package io.payflow.account.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.payflow.domain.event.MoneyDebitedEvent;
import io.payflow.domain.model.AccountId;
import io.payflow.domain.model.Money;
import io.payflow.domain.model.TransferId;
import io.payflow.infrastructure.eventstore.PayFlowJacksonModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureMessageVerifier
@Testcontainers
public abstract class ContractTestBase {

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
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${payflow.kafka.topics.account-events}")
    private String accountEventsTopic;

    public void triggerMoneyDebited() throws Exception {
        ObjectMapper objectMapper = PayFlowJacksonModule.createObjectMapper();
        var accountId = AccountId.of(UUID.randomUUID());
        var transferId = TransferId.of(UUID.randomUUID());
        var amount = Money.of(new BigDecimal("100.00"), "BRL");
        var event = new MoneyDebitedEvent(accountId, transferId, amount, Instant.now());
        // Add envelope fields that the consumer expects for routing
        ObjectNode node = (ObjectNode) objectMapper.valueToTree(event);
        node.put("_eventType", event.getClass().getSimpleName());
        node.put("_eventVersion", event.eventVersion());
        kafkaTemplate.send(accountEventsTopic, accountId.toString(), objectMapper.writeValueAsString(node)).get();
    }
}
