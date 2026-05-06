package io.payflow.account;

import io.payflow.account.api.dto.AccountResponse;
import io.payflow.account.api.dto.CreateAccountRequest;
import io.payflow.account.api.dto.DepositRequest;
import io.payflow.account.persistence.AccountProjectionRepository;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test verifying the AccountProjector replay capability.
 *
 * <p>Scenario: after account creation and a deposit are processed, the projection row
 * is deleted to simulate a fresh projector start. The consumer group offsets are then
 * reset to the beginning of the topic so the projector re-reads all past events and
 * rebuilds the projection with the correct balance — demonstrating that the projector
 * is stateless and restartable from offset 0.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProjectorReplayIntegrationTest {

    private static final String PROJECTOR_GROUP = "account-projector";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("payflow")
            .withUsername("payflow")
            .withPassword("payflow");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        // Ensure the projector picks up events from the very beginning when the
        // consumer group has no committed offsets (fresh Testcontainers Kafka).
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }

    @Autowired
    WebTestClient webTestClient;

    @Autowired
    AccountProjectionRepository projectionRepo;

    @Autowired
    KafkaAdmin kafkaAdmin;

    @Value("${payflow.kafka.topics.account-events}")
    String accountEventsTopic;

    @Test
    void projector_rebuilds_correct_balance_after_projection_deletion() throws Exception {
        // ── Step 1: Create account via API ─────────────────────────────────────
        String accountId = webTestClient.post().uri("/accounts")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .bodyValue(new CreateAccountRequest("alice", "500.00", "BRL"))
                .exchange()
                .expectStatus().isCreated()
                .returnResult(AccountResponse.class)
                .getResponseBody()
                .blockFirst()
                .accountId();

        assertThat(accountId).isNotBlank();

        // ── Step 2: Deposit ────────────────────────────────────────────────────
        webTestClient.post().uri("/accounts/" + accountId + "/deposits")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .bodyValue(new DepositRequest("200.00", "BRL"))
                .exchange()
                .expectStatus().isAccepted();

        // ── Step 3: Wait for projector to reach balance 700.00 ─────────────────
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> projectionRepo.findById(accountId)
                        .map(p -> p.getBalance().compareTo(new BigDecimal("700.00")) == 0)
                        .orElse(false));

        BigDecimal balanceBeforeTruncate = projectionRepo.findById(accountId).get().getBalance();
        assertThat(balanceBeforeTruncate).isEqualByComparingTo(new BigDecimal("700.00"));

        // ── Step 4: Delete projection — simulate fresh projector start ──────────
        projectionRepo.deleteById(accountId);
        assertThat(projectionRepo.findById(accountId)).isEmpty();

        // ── Step 5: Reset consumer group offsets to 0 (replay from beginning) ──
        // This replicates what an ops team would do with kafka-consumer-groups.sh
        // --reset-offsets --to-earliest before restarting a stateless projector.
        resetConsumerGroupOffsetsToBeginning();

        // ── Step 6: Wait for projector to rebuild the projection ────────────────
        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> projectionRepo.findById(accountId).isPresent());

        BigDecimal rebuiltBalance = projectionRepo.findById(accountId).get().getBalance();
        assertThat(rebuiltBalance).isEqualByComparingTo(balanceBeforeTruncate);
    }

    /**
     * Resets all committed offsets for the {@value #PROJECTOR_GROUP} consumer group
     * to the beginning of each assigned partition, replicating the
     * {@code kafka-consumer-groups.sh --reset-offsets --to-earliest} operation.
     *
     * <p>After the reset the projector will re-consume every event from offset 0
     * on the next poll cycle, rebuilding projections from scratch.
     */
    private void resetConsumerGroupOffsetsToBeginning() throws Exception {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {

            // Discover which partitions the consumer group currently owns offsets for.
            ListConsumerGroupOffsetsResult offsetsResult =
                    admin.listConsumerGroupOffsets(PROJECTOR_GROUP);
            Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> currentOffsets =
                    offsetsResult.partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);

            if (currentOffsets == null || currentOffsets.isEmpty()) {
                return; // Group has not committed any offsets yet — nothing to reset.
            }

            // Fetch the earliest (beginning) offset for each partition.
            Map<TopicPartition, OffsetSpec> earliestRequest = currentOffsets.keySet().stream()
                    .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.earliest()));
            var earliestOffsets = admin.listOffsets(earliestRequest)
                    .all().get(10, TimeUnit.SECONDS);

            // Build reset map: set each partition's committed offset to its earliest.
            Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> resetOffsets =
                    new HashMap<>();
            earliestOffsets.forEach((tp, info) ->
                    resetOffsets.put(tp, new org.apache.kafka.clients.consumer.OffsetAndMetadata(info.offset())));

            admin.alterConsumerGroupOffsets(PROJECTOR_GROUP, resetOffsets)
                    .all().get(10, TimeUnit.SECONDS);
        }
    }
}
