package io.payflow.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.payflow.domain.event.MoneyCreditedEvent;
import io.payflow.domain.event.MoneyDebitReversedEvent;
import io.payflow.domain.event.MoneyDebitedEvent;
import io.payflow.domain.event.TransferCreditFailedEvent;
import io.payflow.domain.event.TransferDebitFailedEvent;
import io.payflow.domain.model.AccountId;
import io.payflow.domain.model.Money;
import io.payflow.domain.model.TransferId;
import io.payflow.infrastructure.eventstore.PayFlowJacksonModule;
import io.payflow.transfer.api.dto.InitiateTransferRequest;
import io.payflow.transfer.api.dto.TransferResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TransferSagaIntegrationTest {

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

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Value("${payflow.kafka.topics.account-events}")
    String accountEventsTopic;

    private final ObjectMapper objectMapper = PayFlowJacksonModule.createObjectMapper();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Test
    void happyPath_debitAndCredit_transferCompletes() throws Exception {
        String sourceAccountId = UUID.randomUUID().toString();
        String targetAccountId = UUID.randomUUID().toString();

        TransferResponse transfer = initiateTransfer(sourceAccountId, targetAccountId, "100.00", "BRL");
        String transferId = transfer.transferId();

        await().atMost(10, TimeUnit.SECONDS)
               .until(() -> getStatus(transferId).equals("INITIATED"));

        publishMoneyDebitedEvent(transferId, sourceAccountId, "100.00", "BRL");

        await().atMost(10, TimeUnit.SECONDS)
               .until(() -> getStatus(transferId).equals("DEBITING"));

        publishMoneyCreditedEvent(transferId, targetAccountId, "100.00", "BRL");

        await().atMost(10, TimeUnit.SECONDS)
               .until(() -> getStatus(transferId).equals("COMPLETED"));

        assertThat(getStatus(transferId)).isEqualTo("COMPLETED");
    }

    @Test
    void debitFailure_transferFails() throws Exception {
        String sourceAccountId = UUID.randomUUID().toString();
        String targetAccountId = UUID.randomUUID().toString();

        TransferResponse transfer = initiateTransfer(sourceAccountId, targetAccountId, "100.00", "BRL");
        String transferId = transfer.transferId();

        await().atMost(10, TimeUnit.SECONDS)
               .until(() -> getStatus(transferId).equals("INITIATED"));

        publishDebitFailedEvent(transferId, "INSUFFICIENT_FUNDS");

        await().atMost(10, TimeUnit.SECONDS)
               .until(() -> getStatus(transferId).equals("FAILED"));

        assertThat(getStatus(transferId)).isEqualTo("FAILED");
    }

    @Test
    void creditFailure_triggersReversal_transferReversed() throws Exception {
        String sourceAccountId = UUID.randomUUID().toString();
        String targetAccountId = UUID.randomUUID().toString();

        TransferResponse transfer = initiateTransfer(sourceAccountId, targetAccountId, "100.00", "BRL");
        String transferId = transfer.transferId();

        await().atMost(10, TimeUnit.SECONDS)
               .until(() -> getStatus(transferId).equals("INITIATED"));

        publishMoneyDebitedEvent(transferId, sourceAccountId, "100.00", "BRL");

        await().atMost(10, TimeUnit.SECONDS)
               .until(() -> getStatus(transferId).equals("DEBITING"));

        publishCreditFailedEvent(transferId, "TARGET_ACCOUNT_BLOCKED");

        await().atMost(10, TimeUnit.SECONDS)
               .until(() -> getStatus(transferId).equals("REVERSING"));

        publishMoneyDebitReversedEvent(transferId, sourceAccountId, "100.00", "BRL");

        await().atMost(10, TimeUnit.SECONDS)
               .until(() -> getStatus(transferId).equals("REVERSED"));

        assertThat(getStatus(transferId)).isEqualTo("REVERSED");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TransferResponse initiateTransfer(String sourceId, String targetId, String amount, String currency) {
        var request = new InitiateTransferRequest(sourceId, targetId, amount, currency);
        return webTestClient.post().uri("/transfers")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .returnResult(TransferResponse.class)
                .getResponseBody()
                .blockFirst();
    }

    private String getStatus(String transferId) {
        return webTestClient.get().uri("/transfers/" + transferId)
                .exchange()
                .expectStatus().isOk()
                .returnResult(TransferResponse.class)
                .getResponseBody()
                .blockFirst()
                .status();
    }

    private void publishMoneyDebitedEvent(String transferId, String accountId, String amount, String currency) throws Exception {
        var event = new MoneyDebitedEvent(
                AccountId.of(accountId),
                TransferId.of(transferId),
                Money.of(amount, currency),
                Instant.now());
        kafkaTemplate.send(accountEventsTopic, accountId, serialize(event, "MoneyDebitedEvent"));
    }

    private void publishMoneyCreditedEvent(String transferId, String accountId, String amount, String currency) throws Exception {
        var event = new MoneyCreditedEvent(
                AccountId.of(accountId),
                TransferId.of(transferId),
                Money.of(amount, currency),
                Instant.now());
        kafkaTemplate.send(accountEventsTopic, accountId, serialize(event, "MoneyCreditedEvent"));
    }

    private void publishDebitFailedEvent(String transferId, String reason) throws Exception {
        var event = new TransferDebitFailedEvent(
                TransferId.of(transferId),
                reason,
                Instant.now());
        kafkaTemplate.send(accountEventsTopic, transferId, serialize(event, "TransferDebitFailedEvent"));
    }

    private void publishCreditFailedEvent(String transferId, String reason) throws Exception {
        var event = new TransferCreditFailedEvent(
                TransferId.of(transferId),
                reason,
                Instant.now());
        kafkaTemplate.send(accountEventsTopic, transferId, serialize(event, "TransferCreditFailedEvent"));
    }

    private void publishMoneyDebitReversedEvent(String transferId, String accountId, String amount, String currency) throws Exception {
        var event = new MoneyDebitReversedEvent(
                AccountId.of(accountId),
                TransferId.of(transferId),
                Money.of(amount, currency),
                Instant.now());
        kafkaTemplate.send(accountEventsTopic, accountId, serialize(event, "MoneyDebitReversedEvent"));
    }

    private String serialize(Object event, String eventType) throws Exception {
        var node = objectMapper.valueToTree(event);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("_eventType", eventType);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("_eventVersion", 1);
        return objectMapper.writeValueAsString(node);
    }
}
