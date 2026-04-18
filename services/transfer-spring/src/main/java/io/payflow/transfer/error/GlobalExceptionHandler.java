package io.payflow.transfer.error;

import io.payflow.domain.port.OptimisticLockException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Mono<Map<String, String>> handleNotFound(NoSuchElementException ex) {
        return Mono.just(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));
    }

    @ExceptionHandler(OptimisticLockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Mono<Map<String, String>> handleConcurrencyConflict(OptimisticLockException ex) {
        return Mono.just(Map.of("error", "CONCURRENCY_CONFLICT", "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return Mono.just(Map.of("error", "BAD_REQUEST", "message", ex.getMessage()));
    }
}
