package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.CrawlerConfig;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.models.*;
import searchengine.repositories.*;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
public class IndexingService {

    private final AtomicBoolean isCurrentlyIndexing = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private SitesList sitesList;
    @Autowired
    private CrawlerConfig crawlerConfig;
    @Autowired
    private SiteCleanupService siteCleanupService;

    private final ForkJoinPool forkJoinPool;
    private final ExecutorService executor;

    public IndexingService() {
        this.forkJoinPool = new ForkJoinPool(
                Runtime.getRuntime().availableProcessors(),
                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                (t, e) -> log.error("Необработанное исключение в пуле потоков", e),
                true
        );
        this.executor = Executors.newSingleThreadExecutor();
    }

    @PreDestroy
    public void shutdown() {
        stopRequested.set(true);
        forkJoinPool.shutdownNow();
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public boolean startIndexing() {
        if (!isCurrentlyIndexing.compareAndSet(false, true)) {
            return false;
        }
        stopRequested.set(false);
        executor.submit(this::executeIndexing);
        return true;
    }

    public boolean stopIndexing() {
        if (!isCurrentlyIndexing.get()) {
            return false;
        }
        stopRequested.set(true);
        log.info("Запрошена остановка индексации");
        return true;
    }

    public boolean isCurrentlyIndexing() {
        return isCurrentlyIndexing.get();
    }

    private void executeIndexing() {
        try {
            log.info("Начинаем полную индексацию сайтов");
            List<SiteEntity> sitesToIndex = new ArrayList<>();

            for (Site siteConfig : sitesList.getSites()) {
                if (stopRequested.get()) {
                    log.info("Индексация остановлена пользователем");
                    break;
                }

                SiteEntity site = prepareSiteForIndexing(siteConfig);
                if (site != null) {
                    sitesToIndex.add(site);
                }
            }

            for (SiteEntity site : sitesToIndex) {
                if (stopRequested.get()) {
                    log.info("Индексация остановлена во время обработки сайтов");
                    break;
                }
                try {
                    log.info("🌐 Начинаем индексацию сайта: {}", site.getName());
                    crawlSite(site);
                    if (!stopRequested.get()) {
                        site.statusTimeUpdate(Status.INDEXED);
                        siteRepository.save(site);
                        log.info("✅ Сайт {} успешно проиндексирован", site.getName());
                    }
                } catch (Exception e) {
                    log.error("💥 Критическая ошибка при индексации сайта {}", site.getName(), e);
                    handleIndexingError(site, "Критическая ошибка: " + e.getMessage());
                }
            }
            log.info("🏁 Полная индексация завершена");
        } catch (Exception e) {
            log.error("Необработанное исключение в процессе индексации", e);
        } finally {
            isCurrentlyIndexing.set(false);
        }
    }

    private SiteEntity prepareSiteForIndexing(Site siteConfig) {
        String normalizedUrl = normalizeUrl(siteConfig.getUrl());
        log.info("🔧 Подготовка сайта: {} (URL: {})", siteConfig.getName(), normalizedUrl);

        SiteEntity site = siteRepository.findByUrl(normalizedUrl)
                .orElseGet(() -> createNewSite(siteConfig, normalizedUrl));

        siteCleanupService.resetSiteData(site);

        return site;
    }

    private SiteEntity createNewSite(Site siteConfig, String normalizedUrl) {
        SiteEntity site = new SiteEntity();
        site.setUrl(normalizedUrl);
        site.setName(siteConfig.getName());
        site.setStatus(Status.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        log.info("Создан новый сайт для индексации: {}", site.getName());
        return siteRepository.save(site);
    }


    private void crawlSite(SiteEntity site) {
        Set<String> visitedPaths = ConcurrentHashMap.newKeySet();
        AtomicInteger counter = new AtomicInteger(0);

        log.info("Старт обхода сайта: {} с корневого пути /", site.getName());
        CrawlTask rootTask = new CrawlTask(site, "/", visitedPaths, counter);
        forkJoinPool.invoke(rootTask);

        log.info("Завершена индексация сайта {}. Всего проиндексировано страниц: {}", site.getName(), counter.get());
    }

    private void randomDelay() {
        try {
            int min = crawlerConfig.getDelayMinMs();
            int max = crawlerConfig.getDelayMaxMs();
            if (min <= max) {
                int delay = min + ThreadLocalRandom.current().nextInt(max - min + 1);
                Thread.sleep(delay);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String normalizeUrl(String url) {
        try {
            URL baseUrl = new URL(url);
            String path = baseUrl.getPath();
            if (path == null || path.isEmpty() || "/".equals(path)) {
                return baseUrl.getProtocol() + "://" + baseUrl.getHost() +
                        (baseUrl.getPort() != -1 ? ":" + baseUrl.getPort() : "") + "/";
            }
            String normalized = baseUrl.toString().replaceAll("/+$", "") + "/";
            return normalized;
        } catch (MalformedURLException e) {
            log.error("Некорректный URL: {}", url, e);
            return url.endsWith("/") ? url : url + "/";
        }
    }

    private void handleIndexingError(SiteEntity site, String errorMessage) {
        site.lastErrorUpdate(Status.FAILED, errorMessage);
        siteRepository.save(site);
        log.error("Сайт {} помечен как FAILED: {}", site.getName(), errorMessage);
    }

    private class CrawlTask extends RecursiveAction {
        private final SiteEntity site;
        private final String path;
        private final Set<String> visitedPaths;
        private final AtomicInteger counter;

        private static final Set<String> EXCLUDED_EXTENSIONS = Set.of(
                ".jpg", ".jpeg", ".png", ".gif", ".svg", ".ico", ".webp", ".bmp", ".tiff",
                ".css", ".js", ".json", ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt",
                ".pptx", ".odt", ".rtf", ".zip", ".rar", ".7z", ".tar", ".gz", ".mp3",
                ".mp4", ".avi", ".mov", ".wmv", ".flv", ".wav", ".ogg", ".webm", ".woff",
                ".woff2", ".ttf", ".eot", ".otf", ".xml", ".rss", ".atom", ".txt", ".csv",
                ".exe", ".dmg", ".apk", ".jar", ".bin", ".iso", ".tar.gz", ".tgz"
        );

        CrawlTask(SiteEntity site, String path, Set<String> visitedPaths, AtomicInteger counter) {
            this.site = site;
            this.path = path;
            this.visitedPaths = visitedPaths;
            this.counter = counter;
        }

        @Override
        protected void compute() {
            // Проверка на остановку
            if (stopRequested.get()) {
                log.debug("Задача прервана по запросу остановки: {}", path);
                return;
            }

            // Нормализация пути
            String cleanPath = path.split("\\?")[0].split("#")[0];
            if (!isHtmlPath(cleanPath)) {
                log.trace("Пропускаем путь (не HTML): {}", cleanPath);
                return;
            }

            // Проверка на посещение
            if (!visitedPaths.add(cleanPath)) {
                log.trace("Путь уже посещен: {}", cleanPath);
                return;
            }

            try {
                randomDelay();
                String fullUrl = resolveFullUrl(site.getUrl(), cleanPath);
                log.debug("Загрузка: {}", fullUrl);

                Connection.Response response = Jsoup.connect(fullUrl)
                        .userAgent(crawlerConfig.getUserAgent())
                        .referrer(crawlerConfig.getReferrer())
                        .timeout(10000)
                        .ignoreHttpErrors(true)
                        .execute();

                int statusCode = response.statusCode();
                String contentType = response.contentType().toLowerCase();

                if (statusCode != 200 || !isHtmlContentType(contentType)) {
                    log.debug("Пропускаем: {} (код: {}, тип: {})", cleanPath, statusCode, contentType);
                    savePage(site, cleanPath, statusCode, "");
                    return;
                }

                Document document = response.parse();
                String content = document.html();

                savePage(site, cleanPath, statusCode, content);
                counter.incrementAndGet();

                LemmaProcessingService self = applicationContext.getBean(LemmaProcessingService.class);
                self.processLemmas(site, cleanPath, content);

                if (!stopRequested.get()) {
                    Set<String> childPaths = extractLinks(document, site.getUrl());
                    List<CrawlTask> subTasks = childPaths.stream()
                            .filter(p -> !visitedPaths.contains(p) && isHtmlPath(p))
                            .map(p -> new CrawlTask(site, p, visitedPaths, counter))
                            .collect(Collectors.toList());

                    if (!subTasks.isEmpty()) {
                        invokeAll(subTasks);
                    }
                }

            } catch (IOException e) {
                log.warn("Ошибка загрузки {}: {}", cleanPath, e.getMessage());
                savePage(site, cleanPath, 0, "");
            } catch (Exception e) {
                log.error("Критическая ошибка при обработке {}", cleanPath, e);
                savePage(site, cleanPath, 0, "");
            }
        }

        private boolean isHtmlPath(String path) {
            if (path == null || path.isEmpty() || path.startsWith("#")) {
                return false;
            }
            String normalized = path.toLowerCase().split("\\?")[0];
            if (normalized.matches(".+\\.[a-z0-9]+$")) {
                for (String ext : EXCLUDED_EXTENSIONS) {
                    if (normalized.endsWith(ext)) {
                        return false;
                    }
                }
            }
            return true;
        }

        private Set<String> extractLinks(Document document, String baseUrl) {
            Set<String> links = ConcurrentHashMap.newKeySet();
            Elements elements = document.select("a[href]");

            for (Element element : elements) {
                String href = element.attr("abs:href").trim();
                if (href.isEmpty()) continue;

                if (!isInternalLink(href, baseUrl)) {
                    continue;
                }

                try {
                    URL url = new URL(href);
                    String urlPath = url.getPath();
                    if (urlPath == null || urlPath.isEmpty()) urlPath = "/";

                    String normalizedPath = urlPath.replaceAll("/+$", "");
                    if (!normalizedPath.startsWith("/")) {
                        normalizedPath = "/" + normalizedPath;
                    }
                    normalizedPath = normalizedPath.split("\\?")[0].split("#")[0];

                    links.add(normalizedPath);
                } catch (MalformedURLException e) {
                    log.debug("Некорректная ссылка: {}", href);
                }
            }
            return links;
        }

        private boolean isInternalLink(String href, String baseUrl) {
            return href.startsWith(baseUrl) ||
                    href.startsWith(baseUrl.replace("https://", "http://")) ||
                    href.startsWith(baseUrl.replace("http://", "https://"));
        }
    }

    private void savePage(SiteEntity site, String path, int code, String content) {
        try {
            Page page = pageRepository.findBySiteIdAndPath(site.getId(), path)
                    .orElseGet(() -> {
                        Page newPage = new Page();
                        newPage.setSite(site);
                        newPage.setPath(path);
                        return newPage;
                    });

            page.setCode(code);
            page.setContent(code == 200 && content != null ? content : "");
            pageRepository.save(page);
            log.trace("Страница сохранена: {}{} (код: {})", site.getUrl(), path, code);
        } catch (Exception e) {
            log.error("Ошибка сохранения страницы {}{}", site.getUrl(), path, e);
        }
    }

    private String resolveFullUrl(String baseUrl, String path) {
        try {
            URL base = new URL(baseUrl);
            URL absolute = new URL(base, path);
            return absolute.toString();
        } catch (MalformedURLException e) {
            log.warn("Ошибка разрешения URL: {}{}", baseUrl, path);
            if (path.startsWith("/")) {
                try {
                    URL url = new URL(baseUrl);
                    return url.getProtocol() + "://" + url.getHost() +
                            (url.getPort() != -1 ? ":" + url.getPort() : "") + path;
                } catch (MalformedURLException ex) {
                    return baseUrl + path;
                }
            }
            return baseUrl + (path.startsWith("/") ? path.substring(1) : path);
        }
    }

    private boolean isHtmlContentType(String contentType) {
        return contentType.contains("text/html") ||
                contentType.contains("application/xhtml+xml");
    }

    private boolean isPageIndexPath(String path) {
        String normalized = path.toLowerCase().split("\\?")[0];
        for (String ext : CrawlTask.EXCLUDED_EXTENSIONS) {
            if (normalized.endsWith(ext)) {
                return false;
            }
        }
        return true;
    }

    @Transactional
    public String indexSinglePage(String url) {
        try {

            if (isCurrentlyIndexing.get()) {
                return "Невозможно проиндексировать отдельную страницу во время полной индексации";
            }

            URL pageUrl = new URL(url);
            String baseUrl = pageUrl.getProtocol() + "://" + pageUrl.getHost() +
                    (pageUrl.getPort() != -1 ? ":" + pageUrl.getPort() : "");

            baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";

            SiteEntity targetSite = null;

            for (Site siteConfig : sitesList.getSites()) {
                String configSiteUrl = normalizeUrl(siteConfig.getUrl());
                if (baseUrl.equals(configSiteUrl) ||
                        baseUrl.equals(configSiteUrl.replace("https://", "http://")) ||
                        baseUrl.equals(configSiteUrl.replace("http://", "https://"))) {

                    Optional<SiteEntity> siteEntityOpt = siteRepository.findByUrl(configSiteUrl);
                    if (!siteEntityOpt.isPresent()) {
                        return "Сайт еще не проиндексирован. Сначала выполните полную индексацию.";
                    }
                    targetSite = siteEntityOpt.get();
                    break;
                }
            }

            if (targetSite == null) {
                return "Данная страница находится за пределами сайтов, указанных в конфигурационном файле";
            }

            String path = pageUrl.getPath();
            if (path == null || path.isEmpty() || path.equals("/")) {
                path = "/";
            } else {
                path = path.replaceAll("/+$", "") + "/";
            }

            if (!isPageIndexPath(path)) {
                return "Страница имеет расширение, которое исключено из индексации";
            }

            indexPageOnly(targetSite, path);
            return null;

        } catch (MalformedURLException e) {
            return "Некорректный URL: " + url;
        } catch (Exception e) {
            log.error("Ошибка индексации страницы {}", url, e);
            return "Внутренняя ошибка при индексации страницы: " + e.getMessage();
        }
    }

    private void indexPageOnly(SiteEntity site, String path) throws IOException {
        try {
            randomDelay();
            String fullUrl = resolveFullUrl(site.getUrl(), path);
            log.debug("Загрузка (одиночная страница): {}", fullUrl);

            Connection.Response response = Jsoup.connect(fullUrl)
                    .userAgent(crawlerConfig.getUserAgent())
                    .referrer(crawlerConfig.getReferrer())
                    .timeout(10000)
                    .ignoreHttpErrors(true)
                    .execute();

            int statusCode = response.statusCode();
            String contentType = response.contentType().toLowerCase();

            if (statusCode != 200 || !isHtmlContentType(contentType)) {
                log.debug("Пропускаем: {} (код: {}, тип: {})", path, statusCode, contentType);
                savePage(site, path, statusCode, "");
                return;
            }

            Document document = response.parse();
            String content = document.html();

            savePage(site, path, statusCode, content);

            LemmaProcessingService self = applicationContext.getBean(LemmaProcessingService.class);
            self.processLemmas(site, path, content);

            log.info("✅ Успешно проиндексирована одиночная страница: {}{}", site.getUrl(), path);

        } catch (IOException e) {
            log.warn("⚠️ Ошибка загрузки {}: {}", path, e.getMessage());
            savePage(site, path, 0, "");
            throw e;
        } catch (Exception e) {
            log.error("💥 Критическая ошибка при обработке {}", path, e);
            savePage(site, path, 0, "");
            throw e;
        }
    }
}