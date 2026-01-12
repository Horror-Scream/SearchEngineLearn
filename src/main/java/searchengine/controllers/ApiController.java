package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.search.SearchRequest;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.ResultResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.exceptions.BadRequestException;
import searchengine.exceptions.InternalServerErrorException;
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
        try {
            return statisticsService.getStatistics();
        } catch (Exception e) {
            log.error("Ошибка получения статистики", e);
            throw new InternalServerErrorException("Ошибка получения статистики", e);
        }
    }

    @GetMapping("/startIndexing")
    public ResultResponse startIndexing() {
        try {
            if (indexingService.startIndexing()) {
                return new ResultResponse(true);
            } else {
                throw new BadRequestException("Индексация уже запущена");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка запуска индексации",e);
            throw new InternalServerErrorException("Ошибка запуска индексации");
        }
    }

    @GetMapping("/stopIndexing")
    public ResultResponse stopIndexing() {
        try {
            if (indexingService.stopIndexing()) {
                return new ResultResponse(true);
            } else {
                throw new BadRequestException("Индексация не запущена");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка остановки индексации",e);
            throw new InternalServerErrorException("Ошибка остановки индексации");
        }
    }

    @PostMapping("/indexPage")
    public ResultResponse indexPage(@RequestParam String url) {
        try {
            if (url == null || url.trim().isEmpty()) {
                throw new BadRequestException("Не указан URL страницы");
            }

            String result = indexingService.indexSinglePage(url);
            if (result == null) {
                return new ResultResponse(true);
            } else {
                throw new BadRequestException(result);
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка индексации страницы: {}",url,e);
            throw new InternalServerErrorException("Ошибка индексации страницы",e);
        }
    }

    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam String query,
            @RequestParam(required = false) String site,
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = "20") int limit) {

        try {
            if (query == null || query.trim().isEmpty()) {
                throw new BadRequestException("Задан пустой поисковый запрос");
            }

            SearchRequest searchRequest = new SearchRequest(query, site, offset, limit);
            SearchResponse response = searchService.search(searchRequest);

            if (!response.isResult()) {
                throw new BadRequestException(response.getError());
            }

            return response;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка выполнения поиска: {}", query, e);
            throw new InternalServerErrorException("Ошибка выполнения поиска", e);
        }
    }
    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResultResponse handleBadRequest(BadRequestException e) {
        log.warn("Bad request: {}", e.getMessage());
        return new ResultResponse(false, e.getMessage());
    }

    @ExceptionHandler(InternalServerErrorException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResultResponse handleInternalError(InternalServerErrorException e) {
        log.error("Internal server error: {}", e.getMessage(), e.getCause());
        return new ResultResponse(false, e.getMessage());
    }
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResultResponse handleAllExceptions(Exception e) {
        log.error("Непредвиденная ошибка", e);
        return new ResultResponse(false, "Внутренняя ошибка сервера");
    }
}