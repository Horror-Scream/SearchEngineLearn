package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchRequest;
import searchengine.dto.search.SearchResponse;
import searchengine.models.Lemma;
import searchengine.models.Page;
import searchengine.models.SiteEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final IndexingService indexingService;
    private final QueryProcessor queryProcessor;
    private final IndexRepository indexRepository;

    @Override
    public SearchResponse search(SearchRequest request) {
        if (indexingService.isCurrentlyIndexing()) {
            return new SearchResponse(false, "Индексация сайтов выполняется, попробуйте позже");
        }

        List<String> lemmas = queryProcessor.processQuery(request.getQuery());
        if (lemmas.isEmpty()) {
            return new SearchResponse(false, "Поисковый запрос не содержит значимых слов");
        }

        log.info("Поисковый запрос: '{}', обработанные леммы: {}", request.getQuery(), lemmas);

        try {
            SearchResponse response = new SearchResponse(true);
            List<SearchResponse.SearchResult> results;

            if (request.getSite() != null && !request.getSite().isEmpty()) {
                Optional<SiteEntity> siteOpt = siteRepository.findByUrl(request.getSite());
                if (siteOpt.isEmpty()) {
                    return new SearchResponse(false, "Не найдено в индексе");
                }
                SiteEntity site = siteOpt.get();
                results = searchOnSite(site, lemmas, request.getQuery(), request.getOffset(), request.getLimit());
                log.info("Найдено {} результатов на сайте {}", results.size(), site.getName());
            } else {
                // Поиск по всем сайтам
                results = searchAllSites(lemmas, request.getQuery(), request.getOffset(), request.getLimit());
                log.info("Всего найдено {} результатов по всем сайтам", results.size());
            }

            response.setData(results);
            response.setCount(countTotalResults(lemmas, request.getSite()));
            return response;
        } catch (Exception e) {
            log.error("Ошибка выполнения поиска для запроса: {}", request.getQuery(), e);
            throw new RuntimeException("Ошибка выполнения поиска: " + e.getMessage(), e);
        }
    }
    private List<SearchResponse.SearchResult> searchOnSite(SiteEntity site, List<String> lemmas, String originalQuery, int offset, int limit) {
        List<Lemma> siteLemmas = lemmaRepository.findByLemmasAndSite(lemmas, site.getId());
        if (siteLemmas.size() < lemmas.size()) {
            return Collections.emptyList(); // Не все леммы найдены → нет результатов
        }

        Set<Integer> pageIds = new HashSet<>();
        boolean first = true;

        for (Lemma lemma : siteLemmas) {
            Set<Integer> lemmaPageIds = lemmaRepository.findPageIdsByLemmaId(lemma.getId());
            if (first) {
                pageIds.addAll(lemmaPageIds);
                first = false;
            } else {
                pageIds.retainAll(lemmaPageIds);
            }
            if (pageIds.isEmpty()) {
                break;
            }
        }

        if (pageIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Page> pages = pageRepository.findByIdIn(new ArrayList<>(pageIds));
        return buildSearchResults(pages, site, originalQuery);
    }
    private List<SearchResponse.SearchResult> searchAllSites(List<String> lemmas, String originalQuery, int offset, int limit) {

        List<Lemma> allLemmas = lemmaRepository.findByLemmas(lemmas);
        if (allLemmas.size() < lemmas.size()) {
            return Collections.emptyList();
        }

        Map<Integer, List<Lemma>> lemmasBySite = allLemmas.stream()
                .collect(Collectors.groupingBy(lemma -> lemma.getSite().getId()));

        List<SearchResponse.SearchResult> results = new ArrayList<>();

        for (Map.Entry<Integer, List<Lemma>> entry : lemmasBySite.entrySet()) {
            int siteId = entry.getKey();
            List<Lemma> siteLemmas = entry.getValue();

            if (siteLemmas.size() < lemmas.size()) {
                continue;
            }

            Set<Integer> pageIds = new HashSet<>();
            boolean first = true;

            for (Lemma lemma : siteLemmas) {
                Set<Integer> lemmaPageIds = lemmaRepository.findPageIdsByLemmaId(lemma.getId());
                if (first) {
                    pageIds.addAll(lemmaPageIds);
                    first = false;
                } else {
                    pageIds.retainAll(lemmaPageIds); // Пересечение
                }
                if (pageIds.isEmpty()) {
                    break;
                }
            }

            if (pageIds.isEmpty()) {
                continue;
            }

            List<Page> pages = pageRepository.findByIdIn(new ArrayList<>(pageIds));
            SiteEntity site = siteRepository.findById(siteId).orElse(null);
            if (site == null) continue;

            for (Page page : pages) {
                float absoluteRelevance = calculateAbsoluteRelevance(page.getId());
                String title = extractTitle(page.getContent());
                String snippet = buildSnippet(page.getContent(), originalQuery);

                results.add(new SearchResponse.SearchResult(
                        site.getUrl(),
                        site.getName(),
                        page.getPath(),
                        title,
                        snippet,
                        absoluteRelevance
                ));
            }
        }

        results.sort((a, b) -> Float.compare(b.getRelevance(), a.getRelevance()));

        if (!results.isEmpty()) {
            float maxRelevance = results.get(0).getRelevance(); // Максимальная (первый элемент после сортировки)
            for (SearchResponse.SearchResult result : results) {
                result.setRelevance(result.getRelevance() / maxRelevance);
            }
        }

        int fromIndex = Math.min(offset, results.size());
        int toIndex = Math.min(offset + limit, results.size());
        if (fromIndex >= toIndex) {
            return Collections.emptyList();
        }
        return results.subList(fromIndex, toIndex);
    }
    private int countTotalResults(List<String> lemmas, String siteUrl) {
        if (siteUrl != null && !siteUrl.isEmpty()) {
            Optional<SiteEntity> siteOpt = siteRepository.findByUrl(siteUrl);
            if (siteOpt.isEmpty()) return 0;

            List<Lemma> siteLemmas = lemmaRepository.findByLemmasAndSite(lemmas, siteOpt.get().getId());
            if (siteLemmas.size() < lemmas.size()) return 0;

            Set<Integer> pageIds = siteLemmas.stream()
                    .map(lemma -> lemmaRepository.findPageIdsByLemmaId(lemma.getId()))
                    .reduce((a, b) -> {
                        a.retainAll(b);
                        return a;
                    })
                    .orElse(Collections.emptySet());
            return pageIds.size();
        } else {
            List<Lemma> allLemmas = lemmaRepository.findByLemmas(lemmas);
            if (allLemmas.size() < lemmas.size()) return 0;

            Set<Integer> pageIds = allLemmas.stream()
                    .map(lemma -> lemmaRepository.findPageIdsByLemmaId(lemma.getId()))
                    .reduce((a, b) -> {
                        a.retainAll(b);
                        return a;
                    })
                    .orElse(Collections.emptySet());
            return pageIds.size();
        }
    }

    private List<SearchResponse.SearchResult> buildSearchResults(List<Page> pages, SiteEntity site, String query) {
        List<SearchResponse.SearchResult> results = new ArrayList<>();

        for (Page page : pages) {
            String title = extractTitle(page.getContent());
            String snippet = buildSnippet(page.getContent(), query);
            float relevance = calculateRelevance(page, query);

            results.add(new SearchResponse.SearchResult(
                    site.getUrl(),
                    site.getName(),
                    page.getPath(),
                    title,
                    snippet,
                    relevance
            ));
        }

        results.sort((a, b) -> Float.compare(b.getRelevance(), a.getRelevance()));
        return results;
    }

    private String extractTitle(String content) {
        if (content == null || content.isEmpty()) return "Без названия";

        int titleStart = content.toLowerCase().indexOf("<title>");
        int titleEnd = content.toLowerCase().indexOf("</title>");

        if (titleStart != -1 && titleEnd != -1) {
            return content.substring(titleStart + 7, titleEnd).trim();
        }
        return "Без названия";
    }

    private String buildSnippet(String content, String query) {
        if (content == null || content.isEmpty()) return "";

        String text = content.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();

        List<String> keywords = Arrays.asList(query.toLowerCase().split("\\s+"));

        int firstIndex = -1;
        String foundKeyword = null;

        for (String keyword : keywords) {
            int index = text.toLowerCase().indexOf(keyword);
            if (index != -1 && (firstIndex == -1 || index < firstIndex)) {
                firstIndex = index;
                foundKeyword = keyword;
            }
        }

        if (firstIndex == -1) {

            return text.substring(0, Math.min(200, text.length())) + "...";
        }


        int start = Math.max(0, firstIndex - 50);
        int end = Math.min(text.length(), firstIndex + (foundKeyword != null ? foundKeyword.length() : 0) + 100);

        String context = text.substring(start, end);

        for (String keyword : keywords) {
            context = context.replaceAll("(?i)" + Pattern.quote(keyword), "<b>$0</b>");
        }

        if (start > 0) context = "..." + context;
        if (end < text.length()) context = context + "...";

        return context;
    }

    private float calculateAbsoluteRelevance(int pageId) {
        return (float) indexRepository.findByPageId(pageId).stream()
                .mapToDouble(searchIndex -> searchIndex.getRank())
                .sum();
    }

    private float calculateRelevance(Page page, String query) {

        String content = page.getContent().toLowerCase();
        String[] keywords = query.toLowerCase().split("\\s+");

        float matches = 0;
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                matches += content.split(keyword).length - 1;
            }
        }

        float relevance = matches / (content.length() / 1000f + 1);
        return Math.min(relevance, 1.0f); // Максимальная релевантность 1.0
    }
}