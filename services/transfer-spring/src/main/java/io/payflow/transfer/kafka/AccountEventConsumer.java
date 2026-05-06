package io.payflow.transfer.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.payflow.domain.event.MoneyCreditedEvent;
import io.payflow.domain.event.MoneyDebitReversedEvent;
import io.payflow.domain.event.MoneyDebitedEvent;
import io.payflow.domain.event.TransferCreditFailedEvent;
import io.payflow.domain.event.TransferDebitFailedEvent;
import io.payflow.infrastructure.eventstore.EventTypeRegistry;
import io.payflow.transfer.service.TransferSagaOrchestrator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class AccountEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AccountEventConsumer.class);

    private final TransferSagaOrchestrator orchestrator;
    private final ObjectMapper objectMapper;
    private final EventTypeRegistry eventTypeRegistry;
    private final Tracer tracer;
    private final Propagator propagator;

    public AccountEventConsumer(TransferSagaOrchestrator orchestrator,
                                 ObjectMapper objectMapper,
                                 EventTypeRegistry eventTypeRegistry,
                                 Tracer tracer,
                                 Propagator propagator) {
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
        this.eventTypeRegistry = eventTypeRegistry;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @KafkaListener(topics = "${payflow.kafka.topics.account-events}", groupId = "transfer-service")
    public void onAccountEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        var extractedContext = propagator.extract(record.headers(),
                (carrier, key) -> {
                    var header = carrier.lastHeader(key);
                    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
                });
        var span = tracer.nextSpan(extractedContext)
                .name("kafka.account-event.consume")
                .start();
        try (var ws = tracer.withSpan(span)) {
            try {
                com.fasterxml.jackson.databind.node.ObjectNode node =
                        (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(record.value());
                String eventType = node.path("_eventType").asText();
                node.remove("_eventType");
                node.remove("_eventVersion");

                switch (eventType) {
                    case "MoneyDebitedEvent" -> {
                        var event = objectMapper.treeToValue(node, MoneyDebitedEvent.class);
                        orchestrator.onMoneyDebited(
                                event.transferId().toString(),
                                event.accountId().toString(),
                                event.amount().amount().toPlainString(),
                                event.amount().currency().getCurrencyCode());
                    }
                    case "MoneyCreditedEvent" -> {
                        var event = objectMapper.treeToValue(node, MoneyCreditedEvent.class);
                        orchestrator.onMoneyCredited(
                                event.transferId().toString(),
                                event.accountId().toString(),
                                event.amount().amount().toPlainString(),
                                event.amount().currency().getCurrencyCode());
                    }
                    case "TransferDebitFailedEvent" -> {
                        var event = objectMapper.treeToValue(node, TransferDebitFailedEvent.class);
                        orchestrator.onDebitFailed(
                                event.transferId().toString(),
                                event.reason());
                    }
                    case "TransferCreditFailedEvent" -> {
                        var event = objectMapper.treeToValue(node, TransferCreditFailedEvent.class);
                        orchestrator.onCreditFailed(
                                event.transferId().toString(),
                                event.reason());
                    }
                    case "MoneyDebitReversedEvent" -> {
                        var event = objectMapper.treeToValue(node, MoneyDebitReversedEvent.class);
                        orchestrator.onDebitReversed(event.transferId().toString());
                    }
                    default -> log.debug("Ignoring unhandled event type={}", eventType);
                }

                ack.acknowledge();
            } catch (Exception e) {
                log.error("Failed to process account event offset={} partition={}",
                        record.offset(), record.partition(), e);
                ack.acknowledge();
            }
        } finally {
            span.end();
        }
    }
}
