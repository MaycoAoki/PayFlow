package io.payflow.account;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.payflow.account.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void serverError_includesCauseMessage() {
        var request = HttpRequest.GET("/test");
        var cause = new RuntimeException("root cause details");
        var ex = new RuntimeException("outer message", cause);

        HttpResponse<?> resp = handler.handle(request, ex);

        assertThat(resp.getStatus().getCode()).isEqualTo(500);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody(Map.class).orElseThrow();
        assertThat(body.get("message").toString()).contains("root cause details");
    }
}
