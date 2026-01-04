package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import searchengine.models.Lemma;
import searchengine.models.Page;
import searchengine.models.SiteEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LemmaProcessingService {

    @Autowired
    private LemmaProcessor lemmaProcessor;

    @Autowired
    private IndexRepository indexRepository;

    @Autowired
    private LemmaRepository lemmaRepository;

    @Autowired
    private PageRepository pageRepository;

    private final TransactionTemplate transactionTemplate;

    public LemmaProcessingService(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    private static final Map<Integer, Object> siteLocks = new ConcurrentHashMap<>();

    private Object getSiteLock(int siteId) {
        synchronized (LemmaProcessingService.class) {
            return siteLocks.computeIfAbsent(siteId, k -> new Object());
        }
    }

    public void processLemmas(SiteEntity site, String path, String content) {
        synchronized (getSiteLock(site.getId())) {
            transactionTemplate.execute(status -> {
                try {
                    Page page = pageRepository.findBySiteIdAndPath(site.getId(), path)
                            .orElseThrow(() -> new RuntimeException("Сохраненная страница не найдена: " + path));

                    indexRepository.deleteByPageId(page.getId());

                    if (content == null || content.trim().isEmpty()) {
                        return null;
                    }

                    Map<String, Integer> lemmaFrequencies = lemmaProcessor.extractLemmas(content);
                    log.debug("🔤 Найдено {} лемм на странице {}", lemmaFrequencies.size(), path);

                    List<Lemma> lemmas = new ArrayList<>();
                    List<searchengine.models.SearchIndex> indexes = new ArrayList<>();

                    List<String> sortedLemmas = lemmaFrequencies.keySet().stream()
                            .filter(lemma -> lemma.length() >= 3)
                            .sorted()
                            .collect(Collectors.toList());

                    for (String lemmaStr : sortedLemmas) {
                        int frequency = lemmaFrequencies.get(lemmaStr);

                        Lemma lemma = lemmaRepository.findBySiteAndLemma(site.getId(), lemmaStr)
                                .orElseGet(() -> {
                                    Lemma newLemma = new Lemma();
                                    newLemma.setSite(site);
                                    newLemma.setLemma(lemmaStr);
                                    newLemma.setFrequency(0);
                                    return lemmaRepository.save(newLemma);
                                });

                        lemma.setFrequency(lemma.getFrequency() + frequency);
                        lemmas.add(lemma);

                        searchengine.models.SearchIndex index = new searchengine.models.SearchIndex();
                        index.setPage(page);
                        index.setLemma(lemma);
                        index.setRank(frequency);
                        indexes.add(index);
                    }

                    lemmaRepository.saveAll(lemmas);
                    indexRepository.saveAll(indexes);
                    log.trace("Обработка лемм завершена для страницы {}", path);

                } catch (Exception e) {
                    log.error("Ошибка обработки лемм для страницы {}{}", site.getUrl(), path, e);
                    throw new RuntimeException("Ошибка обработки лемм", e);
                }
                return null;
            });
        }
    }
}