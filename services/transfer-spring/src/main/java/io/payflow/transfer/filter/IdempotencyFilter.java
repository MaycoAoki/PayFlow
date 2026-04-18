package io.payflow.transfer.filter;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class IdempotencyFilter implements WebFilter {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        String path = request.getPath().value();

        if (request.getMethod() != HttpMethod.POST || path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        String idempotencyKey = request.getHeaders().getFirst(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] bytes = "{\"error\":\"Idempotency-Key header is required\"}".getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }

        return chain.filter(exchange);
    }
}
