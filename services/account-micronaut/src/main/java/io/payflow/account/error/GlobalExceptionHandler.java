package io.payflow.account.error;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.payflow.domain.port.OptimisticLockException;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.NoSuchElementException;

@Produces
@Singleton
@Requires(classes = {RuntimeException.class, ExceptionHandler.class})
public class GlobalExceptionHandler implements ExceptionHandler<RuntimeException, HttpResponse<?>> {

    @Override
    public HttpResponse<?> handle(HttpRequest request, RuntimeException ex) {
        if (ex instanceof OptimisticLockException) {
            return HttpResponse.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "CONCURRENCY_CONFLICT", "message", ex.getMessage()));
        }
        if (ex instanceof NoSuchElementException) {
            return HttpResponse.notFound(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));
        }
        if (ex instanceof IllegalArgumentException) {
            return HttpResponse.badRequest(Map.of("error", "BAD_REQUEST", "message", ex.getMessage()));
        }
        String message = buildMessageChain(ex);
        return HttpResponse.serverError(Map.of("error", "INTERNAL_ERROR", "message", message));
    }

    private String buildMessageChain(Throwable t) {
        StringBuilder sb = new StringBuilder(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
        Throwable cause = t.getCause();
        if (cause != null) {
            sb.append(" | caused by: ").append(cause.getClass().getSimpleName()).append(": ").append(cause.getMessage());
            Throwable nested = cause.getCause();
            if (nested != null) {
                sb.append(" | caused by: ").append(nested.getClass().getSimpleName()).append(": ").append(nested.getMessage());
            }
        }
        return sb.toString();
    }
}
