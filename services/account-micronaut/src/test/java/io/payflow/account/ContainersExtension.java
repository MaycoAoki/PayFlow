package io.payflow.account;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public class ContainersExtension implements BeforeAllCallback {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("payflow")
            .withUsername("payflow")
            .withPassword("payflow");

    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    private static volatile boolean started = false;

    @Override
    public synchronized void beforeAll(ExtensionContext context) {
        if (!started) {
            if (!POSTGRES.isRunning()) POSTGRES.start();
            if (!KAFKA.isRunning()) KAFKA.start();
            System.setProperty("datasources.default.url", POSTGRES.getJdbcUrl());
            System.setProperty("datasources.default.username", POSTGRES.getUsername());
            System.setProperty("datasources.default.password", POSTGRES.getPassword());
            System.setProperty("datasources.default.driver-class-name", "org.postgresql.Driver");
            System.setProperty("kafka.bootstrap.servers", KAFKA.getBootstrapServers());
            System.setProperty("payflow.kafka.topics.account-events", "payflow.account.events");
            System.setProperty("payflow.idempotency.ttl-hours", "24");
            started = true;
        }
    }
}
