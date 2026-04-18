package io.payflow.account.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.payflow.domain.event.DomainEvent;
import io.payflow.infrastructure.eventstore.PayFlowJacksonModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AccountEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AccountEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String accountEventsTopic;

    public AccountEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                  @Value("${payflow.kafka.topics.account-events}") String accountEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = PayFlowJacksonModule.createObjectMapper();
        this.accountEventsTopic = accountEventsTopic;
    }

    public void publish(String accountId, DomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(accountEventsTopic, accountId, payload);
            log.info("Published event eventType={} accountId={}", event.getClass().getSimpleName(), accountId);
        } catch (Exception e) {
            log.error("Failed to publish event eventType={} accountId={}", event.getClass().getSimpleName(), accountId, e);
        }
    }
}
