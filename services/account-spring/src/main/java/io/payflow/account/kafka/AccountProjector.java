package io.payflow.account.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.MeterRegistry;
import io.payflow.account.persistence.AccountProjection;
import io.payflow.account.persistence.AccountProjectionRepository;
import io.payflow.account.persistence.TransactionHistoryEntry;
import io.payflow.account.persistence.TransactionHistoryRepository;
import io.payflow.domain.event.AccountCreatedEvent;
import io.payflow.domain.event.MoneyCreditedEvent;
import io.payflow.domain.event.MoneyCreditReversedEvent;
import io.payflow.domain.event.MoneyDebitReversedEvent;
import io.payflow.domain.event.MoneyDebitedEvent;
import io.payflow.domain.event.MoneyDepositedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class AccountProjector {

    private static final Logger log = LoggerFactory.getLogger(AccountProjector.class);

    private final AccountProjectionRepository projectionRepo;
    private final TransactionHistoryRepository historyRepo;
    private final ObjectMapper objectMapper;
    private final AtomicLong lagGauge = new AtomicLong(0);

    public AccountProjector(AccountProjectionRepository projectionRepo,
                             TransactionHistoryRepository historyRepo,
                             ObjectMapper objectMapper,
                             MeterRegistry meterRegistry) {
        this.projectionRepo = projectionRepo;
        this.historyRepo = historyRepo;
        this.objectMapper = objectMapper;
        meterRegistry.gauge("payflow.projector.lag.events", lagGauge, AtomicLong::get);
    }

    @KafkaListener(topics = "${payflow.kafka.topics.account-events}", groupId = "account-projector")
    @Transactional
    public void onAccountEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            lagGauge.set(record.offset());
            ObjectNode node = (ObjectNode) objectMapper.readTree(record.value());
            String eventType = node.path("_eventType").asText();
            node.remove("_eventType");
            node.remove("_eventVersion");

            switch (eventType) {
                case "AccountCreatedEvent" -> {
                    var event = objectMapper.treeToValue(node, AccountCreatedEvent.class);
                    String accountId = event.accountId().toString();
                    // Skip if already recorded — service writes history synchronously;
                    // projector uses occurredAt as idempotency key to avoid double-writes.
                    if (!historyRepo.existsByAccountIdAndOccurredAt(accountId, event.occurredAt())) {
                        projectionRepo.save(new AccountProjection(
                                accountId, event.ownerId(),
                                event.initialBalance().amount(),
                                event.initialBalance().currency().getCurrencyCode(),
                                "ACTIVE", 1L));
                        historyRepo.save(new TransactionHistoryEntry(
                                accountId, "AccountCreatedEvent",
                                event.initialBalance().amount(),
                                event.initialBalance().currency().getCurrencyCode(),
                                null, event.occurredAt()));
                    }
                }
                case "MoneyDepositedEvent" -> {
                    var event = objectMapper.treeToValue(node, MoneyDepositedEvent.class);
                    if (!historyRepo.existsByAccountIdAndOccurredAt(event.accountId().toString(), event.occurredAt())) {
                        projectionRepo.findById(event.accountId().toString()).ifPresent(p -> {
                            p.applyDeposit(event.amount().amount());
                            projectionRepo.save(p);
                            historyRepo.save(new TransactionHistoryEntry(
                                    event.accountId().toString(), "MoneyDepositedEvent",
                                    event.amount().amount(), event.amount().currency().getCurrencyCode(),
                                    null, event.occurredAt()));
                        });
                    }
                }
                case "MoneyDebitedEvent" -> {
                    var event = objectMapper.treeToValue(node, MoneyDebitedEvent.class);
                    if (!historyRepo.existsByAccountIdAndOccurredAt(event.accountId().toString(), event.occurredAt())) {
                        projectionRepo.findById(event.accountId().toString()).ifPresent(p -> {
                            p.applyDebit(event.amount().amount());
                            projectionRepo.save(p);
                            historyRepo.save(new TransactionHistoryEntry(
                                    event.accountId().toString(), "MoneyDebitedEvent",
                                    event.amount().amount(), event.amount().currency().getCurrencyCode(),
                                    event.transferId().toString(), event.occurredAt()));
                        });
                    }
                }
                case "MoneyCreditedEvent" -> {
                    var event = objectMapper.treeToValue(node, MoneyCreditedEvent.class);
                    if (!historyRepo.existsByAccountIdAndOccurredAt(event.accountId().toString(), event.occurredAt())) {
                        projectionRepo.findById(event.accountId().toString()).ifPresent(p -> {
                            p.applyCredit(event.amount().amount());
                            projectionRepo.save(p);
                            historyRepo.save(new TransactionHistoryEntry(
                                    event.accountId().toString(), "MoneyCreditedEvent",
                                    event.amount().amount(), event.amount().currency().getCurrencyCode(),
                                    event.transferId().toString(), event.occurredAt()));
                        });
                    }
                }
                case "MoneyDebitReversedEvent" -> {
                    var event = objectMapper.treeToValue(node, MoneyDebitReversedEvent.class);
                    if (!historyRepo.existsByAccountIdAndOccurredAt(event.accountId().toString(), event.occurredAt())) {
                        projectionRepo.findById(event.accountId().toString()).ifPresent(p -> {
                            p.applyCredit(event.amount().amount());
                            projectionRepo.save(p);
                            historyRepo.save(new TransactionHistoryEntry(
                                    event.accountId().toString(), "MoneyDebitReversedEvent",
                                    event.amount().amount(), event.amount().currency().getCurrencyCode(),
                                    event.transferId().toString(), event.occurredAt()));
                        });
                    }
                }
                case "MoneyCreditReversedEvent" -> {
                    var event = objectMapper.treeToValue(node, MoneyCreditReversedEvent.class);
                    if (!historyRepo.existsByAccountIdAndOccurredAt(event.accountId().toString(), event.occurredAt())) {
                        projectionRepo.findById(event.accountId().toString()).ifPresent(p -> {
                            p.applyDebit(event.amount().amount());
                            projectionRepo.save(p);
                            historyRepo.save(new TransactionHistoryEntry(
                                    event.accountId().toString(), "MoneyCreditReversedEvent",
                                    event.amount().amount(), event.amount().currency().getCurrencyCode(),
                                    event.transferId().toString(), event.occurredAt()));
                        });
                    }
                }
                default -> log.debug("Projector ignoring event type={}", eventType);
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Projector failed on offset={} partition={}", record.offset(), record.partition(), e);
            ack.acknowledge();
        }
    }
}
