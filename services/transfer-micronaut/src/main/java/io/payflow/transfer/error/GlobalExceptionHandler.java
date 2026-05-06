package io.payflow.transfer.error;

import io.micronaut.context.annotation.Produces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.payflow.domain.port.OptimisticLockException;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.NoSuchElementException;

public class GlobalExceptionHandler {

    @Produces
    @Singleton
    public static class NotFoundHandler implements ExceptionHandler<NoSuchElementException, HttpResponse<?>> {

        @Override
        public HttpResponse<?> handle(HttpRequest request, NoSuchElementException ex) {
            return HttpResponse.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));
        }
    }

    @Produces
    @Singleton
    public static class OptimisticLockHandler implements ExceptionHandler<OptimisticLockException, HttpResponse<?>> {

        @Override
        public HttpResponse<?> handle(HttpRequest request, OptimisticLockException ex) {
            return HttpResponse.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "CONCURRENCY_CONFLICT", "message", ex.getMessage()));
        }
    }

    @Produces
    @Singleton
    public static class IllegalArgumentHandler implements ExceptionHandler<IllegalArgumentException, HttpResponse<?>> {

        @Override
        public HttpResponse<?> handle(HttpRequest request, IllegalArgumentException ex) {
            return HttpResponse.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "BAD_REQUEST", "message", ex.getMessage()));
        }
    }
}
