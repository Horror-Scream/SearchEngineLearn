package searchengine.dto.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import searchengine.dto.statistics.ResultResponse;
import searchengine.exceptions.BadRequestException;
import searchengine.exceptions.InternalServerErrorException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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
