package io.payflow.transfer;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.payflow.transfer.api.dto.InitiateTransferRequest;
import io.payflow.transfer.api.dto.TransferResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(ContainersExtension.class)
@MicronautTest(transactional = false)
class TransferControllerIntegrationTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void initiateTransfer_withValidRequest_returns202() {
        var request = new InitiateTransferRequest(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "100.00", "BRL");

        var httpRequest = HttpRequest.POST("/transfers", request)
                .header("Idempotency-Key", UUID.randomUUID().toString());

        var response = client.toBlocking().exchange(httpRequest, TransferResponse.class);

        assertThat(response.status().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
        assertThat(response.body()).isNotNull();
        TransferResponse body = response.body().get();
        assertThat(body.transferId()).isNotBlank();
        assertThat(body.status()).isEqualTo("INITIATED");
        assertThat(body.amount()).isEqualTo("100.00");
        assertThat(body.currency()).isEqualTo("BRL");
    }

    @Test
    void initiateTransfer_withoutIdempotencyKey_returns400() {
        var request = new InitiateTransferRequest(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "100.00", "BRL");

        var httpRequest = HttpRequest.POST("/transfers", request);

        var response = client.toBlocking().onErrorReturn(throwable -> true, null)
                .exchange(httpRequest, TransferResponse.class);

        assertThat(response.status().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void getTransfer_existingTransfer_returns200() {
        String idempotencyKey = UUID.randomUUID().toString();
        var request = new InitiateTransferRequest(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "50.00", "BRL");

        var httpRequest = HttpRequest.POST("/transfers", request)
                .header("Idempotency-Key", idempotencyKey);

        TransferResponse created = client.toBlocking().exchange(httpRequest, TransferResponse.class)
                .body()
                .get();

        var getRequest = HttpRequest.GET("/transfers/" + created.transferId());
        var getResponse = client.toBlocking().exchange(getRequest, TransferResponse.class);

        assertThat(getResponse.status().getCode()).isEqualTo(HttpStatus.OK.getCode());
        TransferResponse fetched = getResponse.body().get();
        assertThat(fetched.transferId()).isEqualTo(created.transferId());
        assertThat(fetched.status()).isEqualTo("INITIATED");
    }

    @Test
    void getTransfer_nonExistent_returns404() {
        var getRequest = HttpRequest.GET("/transfers/" + UUID.randomUUID());

        var response = client.toBlocking().onErrorReturn(throwable -> true, null)
                .exchange(getRequest, TransferResponse.class);

        assertThat(response.status().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void initiateTransfer_withSameIdempotencyKey_returnsSameTransferId() {
        String idempotencyKey = UUID.randomUUID().toString();
        var request = new InitiateTransferRequest(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "75.00", "BRL");

        var httpRequest = HttpRequest.POST("/transfers", request)
                .header("Idempotency-Key", idempotencyKey);

        TransferResponse firstResponse = client.toBlocking().exchange(httpRequest, TransferResponse.class)
                .body()
                .get();

        TransferResponse secondResponse = client.toBlocking().exchange(httpRequest, TransferResponse.class)
                .body()
                .get();

        assertThat(secondResponse.transferId()).isEqualTo(firstResponse.transferId());
    }

    @Test
    void healthLiveness_returns200() {
        var request = HttpRequest.GET("/health/liveness");
        var response = client.toBlocking().exchange(request);

        assertThat(response.status().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    void healthReadiness_returns200() {
        var request = HttpRequest.GET("/health/readiness");
        var response = client.toBlocking().exchange(request);

        assertThat(response.status().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }
}
