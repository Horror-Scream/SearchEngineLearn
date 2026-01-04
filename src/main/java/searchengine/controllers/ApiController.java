package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.search.SearchRequest;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.ResultResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;

    @Autowired
    public ApiController(StatisticsService statisticsService,
                         IndexingService indexingService,
                         SearchService searchService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.searchService = searchService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        try {
            return ResponseEntity.ok(statisticsService.getStatistics());
        } catch (Exception e) {
            return handleInternalError(e, "Ошибка получения статистики");
        }
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<ResultResponse> startIndexing() {
        try {
            return indexingService.startIndexing() ?
                    ResponseEntity.ok(new ResultResponse(true)) :
                    ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(new ResultResponse(false, "Индексация уже запущена"));
        } catch (Exception e) {
            return handleInternalError(e, "Ошибка запуска индексации");
        }
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<ResultResponse> stopIndexing() {
        try {
            return indexingService.stopIndexing() ?
                    ResponseEntity.ok(new ResultResponse(true)) :
                    ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(new ResultResponse(false, "Индексация не запущена"));
        } catch (Exception e) {
            return handleInternalError(e, "Ошибка остановки индексации");
        }
    }

    @PostMapping("/indexPage")
    public ResponseEntity<ResultResponse> indexPage(@RequestParam String url) {
        try {
            if (url == null || url.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResultResponse(false, "Не указан URL страницы"));
            }

            String result = indexingService.indexSinglePage(url);
            if (result == null) {
                return ResponseEntity.ok(new ResultResponse(true));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResultResponse(false, result));
            }
        } catch (Exception e) {
            return handleInternalError(e, "Ошибка индексации страницы");
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam String query,
            @RequestParam(required = false) String site,
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = "20") int limit) {

        try {
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResultResponse(false, "Задан пустой поисковый запрос"));
            }

            SearchRequest searchRequest = new SearchRequest(query, site, offset, limit);
            SearchResponse response = searchService.search(searchRequest);

            if (!response.isResult()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(response);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return handleInternalError(e, "Ошибка выполнения поиска");
        }
    }

    private <T> ResponseEntity<T> handleInternalError(Exception e, String message) {
        System.err.println(message + ": " + e.getMessage());
        e.printStackTrace();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body((T) new ResultResponse(false, "Внутренняя ошибка сервера"));
    }
}