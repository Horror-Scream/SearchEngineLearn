package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.models.SiteEntity;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.repositories.IndexRepository;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final IndexingService indexingService;

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sitesList.getSites().size());
        total.setIndexing(indexingService.isCurrentlyIndexing());

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        int totalPages = 0;
        int totalLemmas = 0;

        for (Site siteConfig : sitesList.getSites()) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setUrl(siteConfig.getUrl());
            item.setName(siteConfig.getName());

            String normalizedUrl = normalizeUrl(siteConfig.getUrl());
            Optional<SiteEntity> siteEntityOpt = siteRepository.findByUrl(normalizedUrl);

            if (siteEntityOpt.isPresent()) {
                SiteEntity siteEntity = siteEntityOpt.get();
                item.setStatus(siteEntity.getStatus().name());
                item.setError(siteEntity.getLastError() != null ? siteEntity.getLastError() : "");
                item.setStatusTime(siteEntity.getStatusTime().toInstant(ZoneOffset.UTC).toEpochMilli());

                int pagesCount = pageRepository.countBySiteId(siteEntity.getId());
                int lemmasCount = lemmaRepository.findBySiteId(siteEntity.getId()).size();

                item.setPages(pagesCount);
                item.setLemmas(lemmasCount);

                totalPages += pagesCount;
                totalLemmas += lemmasCount;
            } else {
                item.setStatus("NOT_INDEXED");
                item.setError("");
                item.setStatusTime(0);
                item.setPages(0);
                item.setLemmas(0);
            }
            detailed.add(item);
        }

        total.setPages(totalPages);
        total.setLemmas(totalLemmas);

        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);

        StatisticsResponse response = new StatisticsResponse();
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    private String normalizeUrl(String url) {
        return url.endsWith("/") ? url : url + "/";
    }
}