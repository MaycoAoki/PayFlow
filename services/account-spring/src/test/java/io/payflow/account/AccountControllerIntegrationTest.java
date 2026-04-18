package io.payflow.account;

import io.payflow.account.api.dto.AccountResponse;
import io.payflow.account.api.dto.CreateAccountRequest;
import io.payflow.account.api.dto.DepositRequest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountControllerIntegrationTest {

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
    @Order(1)
    void createAccount_withValidRequest_returns201() {
        var request = new CreateAccountRequest("owner-1", "100.00", "BRL");

        webTestClient.post().uri("/accounts")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.accountId").isNotEmpty()
                .jsonPath("$.ownerId").isEqualTo("owner-1")
                .jsonPath("$.balance").isEqualTo("100.00")
                .jsonPath("$.currency").isEqualTo("BRL")
                .jsonPath("$.status").isEqualTo("ACTIVE");
    }

    @Test
    @Order(2)
    void createAccount_withoutIdempotencyKey_returns400() {
        var request = new CreateAccountRequest("owner-1", "100.00", "BRL");

        webTestClient.post().uri("/accounts")
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @Order(3)
    void getAccount_existingAccount_returns200() {
        String idempotencyKey = UUID.randomUUID().toString();
        var createRequest = new CreateAccountRequest("owner-2", "200.00", "BRL");

        String accountId = webTestClient.post().uri("/accounts")
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(createRequest)
                .exchange()
                .expectStatus().isCreated()
                .returnResult(AccountResponse.class)
                .getResponseBody()
                .blockFirst()
                .accountId();

        webTestClient.get().uri("/accounts/" + accountId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accountId").isEqualTo(accountId)
                .jsonPath("$.balance").isEqualTo("200.00")
                .jsonPath("$.currency").isEqualTo("BRL");
    }

    @Test
    @Order(4)
    void getAccount_nonExistent_returns404() {
        webTestClient.get().uri("/accounts/" + UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @Order(5)
    void deposit_toExistingAccount_updatesBalance() {
        String createKey = UUID.randomUUID().toString();
        var createRequest = new CreateAccountRequest("owner-3", "100.00", "BRL");

        String accountId = webTestClient.post().uri("/accounts")
                .header("Idempotency-Key", createKey)
                .bodyValue(createRequest)
                .exchange()
                .expectStatus().isCreated()
                .returnResult(AccountResponse.class)
                .getResponseBody()
                .blockFirst()
                .accountId();

        var depositRequest = new DepositRequest("50.00", "BRL");
        webTestClient.post().uri("/accounts/" + accountId + "/deposits")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .bodyValue(depositRequest)
                .exchange()
                .expectStatus().isAccepted();

        webTestClient.get().uri("/accounts/" + accountId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.balance").isEqualTo("150.00");
    }

    @Test
    @Order(6)
    void getAccountEvents_returnsEventHistory() {
        String createKey = UUID.randomUUID().toString();
        var createRequest = new CreateAccountRequest("owner-4", "100.00", "BRL");

        String accountId = webTestClient.post().uri("/accounts")
                .header("Idempotency-Key", createKey)
                .bodyValue(createRequest)
                .exchange()
                .expectStatus().isCreated()
                .returnResult(AccountResponse.class)
                .getResponseBody()
                .blockFirst()
                .accountId();

        webTestClient.get().uri("/accounts/" + accountId + "/events")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].eventType").isEqualTo("AccountCreatedEvent")
                .jsonPath("$[0].occurredAt").isNotEmpty();
    }

    @Test
    @Order(7)
    void healthLiveness_returns200() {
        webTestClient.get().uri("/health/liveness")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @Order(8)
    void healthReadiness_returns200() {
        webTestClient.get().uri("/health/readiness")
                .exchange()
                .expectStatus().isOk();
    }
}
