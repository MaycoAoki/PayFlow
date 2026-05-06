package io.payflow.transfer.filter;

import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

@Filter("/**")
public class IdempotencyFilter implements HttpServerFilter {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        String path = request.getPath();

        if (request.getMethod() != HttpMethod.POST || path.startsWith("/health")) {
            return chain.proceed(request);
        }

        String idempotencyKey = request.getHeaders().get(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Mono.just(HttpResponse.status(HttpStatus.BAD_REQUEST)
                    .body("{\"error\":\"Idempotency-Key header is required\"}"));
        }

        return chain.proceed(request);
    }
}
