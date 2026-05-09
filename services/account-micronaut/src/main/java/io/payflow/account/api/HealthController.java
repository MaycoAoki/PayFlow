package io.payflow.account.api;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
@Controller("/health")
public class HealthController {

    @Get("/liveness")
    public HttpResponse<Map<String, String>> liveness() {
        return HttpResponse.ok(Map.of("status", "UP"));
    }

    @Get("/readiness")
    public HttpResponse<Map<String, String>> readiness() {
        return HttpResponse.ok(Map.of("status", "UP"));
    }
}
