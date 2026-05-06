package io.payflow.account.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.payflow.domain.port.EventStore;
import io.payflow.infrastructure.eventstore.EventTypeRegistry;
import io.payflow.infrastructure.eventstore.PayFlowJacksonModule;
import io.payflow.infrastructure.eventstore.PostgresEventStore;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

@Factory
public class InfrastructureConfig {

    @Singleton
    @Bean
    public ObjectMapper objectMapper() {
        return PayFlowJacksonModule.createObjectMapper();
    }

    @Singleton
    @Bean
    public EventTypeRegistry eventTypeRegistry() {
        return EventTypeRegistry.defaultRegistry();
    }

    @Singleton
    @Bean
    public EventStore eventStore(DataSource dataSource, ObjectMapper objectMapper,
                                  EventTypeRegistry eventTypeRegistry) {
        return new PostgresEventStore(dataSource, objectMapper, eventTypeRegistry);
    }
}
