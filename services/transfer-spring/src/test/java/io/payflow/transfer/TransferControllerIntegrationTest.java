package io.payflow.transfer;

import io.payflow.transfer.api.dto.InitiateTransferRequest;
import io.payflow.transfer.api.dto.TransferResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TransferControllerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("payflow")
            .withUsername("payflow")
            .withPassword("payflow");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Autowired
    WebTestClient webTestClient;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Test
    void initiateTransfer_withValidRequest_returns202() {
        var request = new InitiateTransferRequest(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "100.00", "BRL");

        webTestClient.post().uri("/transfers")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.transferId").isNotEmpty()
                .jsonPath("$.status").isEqualTo("INITIATED")
                .jsonPath("$.amount").isEqualTo("100.00")
                .jsonPath("$.currency").isEqualTo("BRL");
    }

    @Test
    void initiateTransfer_withoutIdempotencyKey_returns400() {
        var request = new InitiateTransferRequest(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "100.00", "BRL");

        webTestClient.post().uri("/transfers")
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getTransfer_existingTransfer_returns200() {
        String idempotencyKey = UUID.randomUUID().toString();
        var request = new InitiateTransferRequest(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "50.00", "BRL");

        String transferId = webTestClient.post().uri("/transfers")
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .returnResult(TransferResponse.class)
                .getResponseBody()
                .blockFirst()
                .transferId();

        webTestClient.get().uri("/transfers/" + transferId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.transferId").isEqualTo(transferId)
                .jsonPath("$.status").isEqualTo("INITIATED");
    }

    @Test
    void getTransfer_nonExistent_returns404() {
        webTestClient.get().uri("/transfers/" + UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void initiateTransfer_withSameIdempotencyKey_returnsSameTransferId() {
        String idempotencyKey = UUID.randomUUID().toString();
        var request = new InitiateTransferRequest(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "75.00", "BRL");

        String firstId = webTestClient.post().uri("/transfers")
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .returnResult(TransferResponse.class)
                .getResponseBody()
                .blockFirst()
                .transferId();

        String secondId = webTestClient.post().uri("/transfers")
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .returnResult(TransferResponse.class)
                .getResponseBody()
                .blockFirst()
                .transferId();

        assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    void healthLiveness_returns200() {
        webTestClient.get().uri("/health/liveness")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void healthReadiness_returns200() {
        webTestClient.get().uri("/health/readiness")
                .exchange()
                .expectStatus().isOk();
    }
}
