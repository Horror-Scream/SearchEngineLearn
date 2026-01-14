package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.search.SearchRequest;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.ResultResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.exceptions.BadRequestException;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;

    @GetMapping("/statistics")
    public StatisticsResponse statistics() {
        return statisticsService.getStatistics();
    }

    @GetMapping("/startIndexing")
    public ResultResponse startIndexing() {
        if (!indexingService.startIndexing()) {
            throw new BadRequestException("Индексация уже запущена");
        }
        return new ResultResponse(true);
    }

    @GetMapping("/stopIndexing")
    public ResultResponse stopIndexing() {
        if (!indexingService.stopIndexing()) {
            throw new BadRequestException("Индексация не запущена");
        }
        return new ResultResponse(true);
    }

    @PostMapping("/indexPage")
    public ResultResponse indexPage(@RequestParam String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new BadRequestException("Не указан URL страницы");
        }

        String result = indexingService.indexSinglePage(url);
        if (result != null) {
            throw new BadRequestException(result);
        }
        return new ResultResponse(true);
    }

    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam String query,
            @RequestParam(required = false) String site,
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = "20") int limit) {

        if (query == null || query.trim().isEmpty()) {
            throw new BadRequestException("Задан пустой поисковый запрос");
        }

        SearchRequest searchRequest = new SearchRequest(query, site, offset, limit);
        SearchResponse response = searchService.search(searchRequest);

        if (!response.isResult()) {
            throw new BadRequestException(response.getError());
        }
        return response;
    }
}