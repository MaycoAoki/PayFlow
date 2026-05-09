package io.payflow.account;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.payflow.account.api.dto.AccountResponse;
import io.payflow.account.api.dto.CreateAccountRequest;
import io.payflow.account.api.dto.DepositRequest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(ContainersExtension.class)
@MicronautTest(transactional = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountControllerIntegrationTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    @Order(1)
    void createAccount_withValidRequest_returns201() {
        var req = HttpRequest.POST("/accounts", new CreateAccountRequest("owner-1", "100.00", "BRL"))
                .header("Idempotency-Key", UUID.randomUUID().toString());

        HttpResponse<AccountResponse> resp = client.toBlocking().exchange(req, AccountResponse.class);

        assertThat(resp.status().getCode()).isEqualTo(HttpStatus.CREATED.getCode());
        AccountResponse body = resp.body();
        assertThat(body).isNotNull();
        assertThat(body.accountId()).isNotBlank();
        assertThat(body.ownerId()).isEqualTo("owner-1");
        assertThat(body.balance()).isEqualTo("100.00");
        assertThat(body.currency()).isEqualTo("BRL");
        assertThat(body.status()).isEqualTo("ACTIVE");
    }

    @Test
    @Order(2)
    void createAccount_withoutIdempotencyKey_returns400() {
        var req = HttpRequest.POST("/accounts", new CreateAccountRequest("owner-1", "100.00", "BRL"));

        assertThatThrownBy(() -> client.toBlocking().exchange(req, AccountResponse.class))
                .isInstanceOf(HttpClientResponseException.class)
                .satisfies(ex -> assertThat(((HttpClientResponseException) ex).getStatus().getCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST.getCode()));
    }

    @Test
    @Order(3)
    void getAccount_existingAccount_returns200() {
        String key = UUID.randomUUID().toString();
        var createReq = HttpRequest.POST("/accounts", new CreateAccountRequest("owner-2", "200.00", "BRL"))
                .header("Idempotency-Key", key);

        AccountResponse created = client.toBlocking().exchange(createReq, AccountResponse.class).body();
        assertThat(created).isNotNull();

        var getReq = HttpRequest.GET("/accounts/" + created.accountId());
        HttpResponse<AccountResponse> resp = client.toBlocking().exchange(getReq, AccountResponse.class);

        assertThat(resp.status().getCode()).isEqualTo(HttpStatus.OK.getCode());
        AccountResponse fetched = resp.body();
        assertThat(fetched).isNotNull();
        assertThat(fetched.accountId()).isEqualTo(created.accountId());
        assertThat(fetched.balance()).isEqualTo("200.00");
        assertThat(fetched.currency()).isEqualTo("BRL");
    }

    @Test
    @Order(4)
    void getAccount_nonExistent_returns404() {
        var req = HttpRequest.GET("/accounts/" + UUID.randomUUID());

        assertThatThrownBy(() -> client.toBlocking().exchange(req, AccountResponse.class))
                .isInstanceOf(HttpClientResponseException.class)
                .satisfies(ex -> assertThat(((HttpClientResponseException) ex).getStatus().getCode())
                        .isEqualTo(HttpStatus.NOT_FOUND.getCode()));
    }

    @Test
    @Order(5)
    void deposit_toExistingAccount_updatesBalance() {
        String createKey = UUID.randomUUID().toString();
        var createReq = HttpRequest.POST("/accounts", new CreateAccountRequest("owner-3", "100.00", "BRL"))
                .header("Idempotency-Key", createKey);

        AccountResponse created = client.toBlocking().exchange(createReq, AccountResponse.class).body();
        assertThat(created).isNotNull();

        var depositReq = HttpRequest.POST(
                        "/accounts/" + created.accountId() + "/deposits",
                        new DepositRequest("50.00", "BRL"))
                .header("Idempotency-Key", UUID.randomUUID().toString());

        HttpResponse<?> depositResp = client.toBlocking().exchange(depositReq);
        assertThat(depositResp.status().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());

        var getReq = HttpRequest.GET("/accounts/" + created.accountId());
        AccountResponse updated = client.toBlocking().exchange(getReq, AccountResponse.class).body();
        assertThat(updated).isNotNull();
        assertThat(updated.balance()).isEqualTo("150.00");
    }

    @Test
    @Order(6)
    void same_idempotencyKey_createAccount_returnsSameAccount() {
        String key = UUID.randomUUID().toString();
        var req = HttpRequest.POST("/accounts", new CreateAccountRequest("owner-5", "300.00", "BRL"))
                .header("Idempotency-Key", key);

        AccountResponse first = client.toBlocking().exchange(req, AccountResponse.class).body();
        AccountResponse second = client.toBlocking().exchange(req, AccountResponse.class).body();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.accountId()).isEqualTo(second.accountId());
    }

    @Test
    @Order(7)
    void getAccountEvents_returnsEventHistory() {
        String createKey = UUID.randomUUID().toString();
        var createReq = HttpRequest.POST("/accounts", new CreateAccountRequest("owner-4", "100.00", "BRL"))
                .header("Idempotency-Key", createKey);

        AccountResponse created = client.toBlocking().exchange(createReq, AccountResponse.class).body();
        assertThat(created).isNotNull();

        var eventsReq = HttpRequest.GET("/accounts/" + created.accountId() + "/events");
        HttpResponse<String> resp = client.toBlocking().exchange(eventsReq, String.class);

        assertThat(resp.status().getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(resp.body()).contains("AccountCreatedEvent");
        assertThat(resp.body()).contains("occurredAt");
    }

    @Test
    @Order(8)
    void healthLiveness_returns200() {
        var req = HttpRequest.GET("/health/liveness");
        HttpResponse<?> resp = client.toBlocking().exchange(req);
        assertThat(resp.status().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    @Order(9)
    void healthReadiness_returns200() {
        var req = HttpRequest.GET("/health/readiness");
        HttpResponse<?> resp = client.toBlocking().exchange(req);
        assertThat(resp.status().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }
}
