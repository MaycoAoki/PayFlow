package io.payflow.account.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.payflow.domain.port.EventStore;
import io.payflow.infrastructure.eventstore.EventTypeRegistry;
import io.payflow.infrastructure.eventstore.PayFlowJacksonModule;
import io.payflow.infrastructure.eventstore.PostgresEventStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class InfrastructureConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return PayFlowJacksonModule.createObjectMapper();
    }

    @Bean
    public EventTypeRegistry eventTypeRegistry() {
        return EventTypeRegistry.defaultRegistry();
    }

    @Bean
    public EventStore eventStore(DataSource dataSource, ObjectMapper objectMapper,
                                  EventTypeRegistry eventTypeRegistry) {
        return new PostgresEventStore(dataSource, objectMapper, eventTypeRegistry);
    }
}
