package io.payflow.account;

import io.payflow.account.api.dto.AccountResponse;
import io.payflow.account.api.dto.CreateAccountRequest;
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
class IdempotencyIntegrationTest {

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
    void duplicateRequest_withSameIdempotencyKey_returnsSameAccountId() {
        String idempotencyKey = UUID.randomUUID().toString();
        var request = new CreateAccountRequest("owner-idempotent", "100.00", "BRL");

        String firstAccountId = webTestClient.post().uri("/accounts")
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .returnResult(AccountResponse.class)
                .getResponseBody()
                .blockFirst()
                .accountId();

        String secondAccountId = webTestClient.post().uri("/accounts")
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .returnResult(AccountResponse.class)
                .getResponseBody()
                .blockFirst()
                .accountId();

        assertThat(secondAccountId).isEqualTo(firstAccountId);
    }

    @Test
    void missingIdempotencyKey_onPostAccounts_returns400() {
        var request = new CreateAccountRequest("owner-no-key", "100.00", "BRL");

        webTestClient.post().uri("/accounts")
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }
}
