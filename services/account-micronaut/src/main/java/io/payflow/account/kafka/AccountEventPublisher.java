package io.payflow.account.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.KafkaKey;
import io.micronaut.configuration.kafka.annotation.Topic;
import io.micronaut.context.annotation.Value;
import io.payflow.domain.event.DomainEvent;
import io.payflow.infrastructure.eventstore.PayFlowJacksonModule;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AccountEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AccountEventPublisher.class);

    private final AccountKafkaProducer producer;
    private final ObjectMapper objectMapper;
    private final String accountEventsTopic;

    public AccountEventPublisher(AccountKafkaProducer producer,
                                  @Value("${payflow.kafka.topics.account-events}") String accountEventsTopic) {
        this.producer = producer;
        this.objectMapper = PayFlowJacksonModule.createObjectMapper();
        this.accountEventsTopic = accountEventsTopic;
    }

    public void publish(String accountId, DomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            producer.send(accountEventsTopic, accountId, payload);
            log.info("Published event eventType={} accountId={}", event.getClass().getSimpleName(), accountId);
        } catch (Exception e) {
            log.error("Failed to publish event eventType={} accountId={}", event.getClass().getSimpleName(), accountId, e);
        }
    }

    @KafkaClient
    public interface AccountKafkaProducer {
        void send(@Topic String topic, @KafkaKey String key, String payload);
    }
}
