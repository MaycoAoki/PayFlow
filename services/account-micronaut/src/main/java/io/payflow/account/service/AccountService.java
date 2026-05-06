package io.payflow.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.transaction.annotation.Transactional;
import io.payflow.account.api.dto.AccountResponse;
import io.payflow.account.api.dto.CreateAccountRequest;
import io.payflow.account.api.dto.DepositRequest;
import io.payflow.account.api.dto.EventSummary;
import io.payflow.account.kafka.AccountEventPublisher;
import io.payflow.account.persistence.AccountProjection;
import io.payflow.account.persistence.AccountProjectionRepository;
import io.payflow.account.persistence.IdempotencyRecord;
import io.payflow.account.persistence.IdempotencyRepository;
import io.payflow.account.persistence.TransactionHistoryEntry;
import io.payflow.account.persistence.TransactionHistoryRepository;
import io.payflow.domain.event.AccountCreatedEvent;
import io.payflow.domain.event.MoneyDepositedEvent;
import io.payflow.domain.model.AccountId;
import io.payflow.domain.model.Money;
import io.payflow.domain.port.EventStore;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Singleton
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final EventStore eventStore;
    private final AccountProjectionRepository projectionRepo;
    private final TransactionHistoryRepository historyRepo;
    private final IdempotencyRepository idempotencyRepo;
    private final AccountEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public AccountService(EventStore eventStore,
                          AccountProjectionRepository projectionRepo,
                          TransactionHistoryRepository historyRepo,
                          IdempotencyRepository idempotencyRepo,
                          AccountEventPublisher eventPublisher,
                          ObjectMapper objectMapper) {
        this.eventStore = eventStore;
        this.projectionRepo = projectionRepo;
        this.historyRepo = historyRepo;
        this.idempotencyRepo = idempotencyRepo;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest req, String idempotencyKey) {
        Optional<IdempotencyRecord> existing = idempotencyRepo.findById(idempotencyKey);
        if (existing.isPresent() && !existing.get().isExpired()) {
            try {
                return objectMapper.readValue(existing.get().getResponseBody(), AccountResponse.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to deserialize cached response", e);
            }
        }

        AccountId accountId = AccountId.generate();
        Money initialBalance = Money.of(req.initialBalance(), req.currency());

        var event = new AccountCreatedEvent(accountId, req.ownerId(), initialBalance, Instant.now());
        eventStore.append(accountId.toString(), "Account", 0L, List.of(event));

        var projection = new AccountProjection(
                accountId.toString(), req.ownerId(),
                initialBalance.amount(), initialBalance.currency().getCurrencyCode(),
                "ACTIVE", 1L);
        projectionRepo.save(projection);

        historyRepo.save(new TransactionHistoryEntry(
                accountId.toString(), "AccountCreatedEvent",
                initialBalance.amount(), initialBalance.currency().getCurrencyCode(),
                null, event.occurredAt()));

        AccountResponse response = toResponse(projection);
        storeIdempotencyRecord(idempotencyKey, accountId.toString(), response, (short) 201);

        log.info("Account created accountId={} ownerId={}", accountId, req.ownerId());
        eventPublisher.publish(accountId.toString(), event);

        return response;
    }

    @Transactional
    public void deposit(String accountId, DepositRequest req, String idempotencyKey) {
        Optional<IdempotencyRecord> existingIdempotency = idempotencyRepo.findById(idempotencyKey);
        if (existingIdempotency.isPresent() && !existingIdempotency.get().isExpired()) {
            return;
        }

        var projection = projectionRepo.findById(accountId)
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + accountId));

        long expectedVersion = projection.getVersion();
        Money amount = Money.of(req.amount(), req.currency());

        var event = new MoneyDepositedEvent(AccountId.of(accountId), amount, Instant.now());
        eventStore.append(accountId, "Account", expectedVersion, List.of(event));

        projection.applyDeposit(amount.amount());
        projectionRepo.save(projection);

        historyRepo.save(new TransactionHistoryEntry(
                accountId, "MoneyDepositedEvent",
                amount.amount(), amount.currency().getCurrencyCode(),
                null, event.occurredAt()));

        storeIdempotencyRecord(idempotencyKey, accountId, null, (short) 202);
        log.info("Deposit applied accountId={} amount={}", accountId, amount);
        eventPublisher.publish(accountId, event);
    }

    public AccountResponse findAccount(String accountId) {
        return projectionRepo.findById(accountId)
                .map(this::toResponse)
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + accountId));
    }

    public List<EventSummary> listEvents(String accountId) {
        return eventStore.loadEvents(accountId).stream()
                .map(e -> new EventSummary(e.getClass().getSimpleName(), e.occurredAt()))
                .toList();
    }

    private void storeIdempotencyRecord(String idempotencyKey, String aggregateId,
                                         Object responseBody, short statusCode) {
        try {
            String body = responseBody != null ? objectMapper.writeValueAsString(responseBody) : null;
            idempotencyRepo.save(new IdempotencyRecord(idempotencyKey, aggregateId, body, statusCode));
        } catch (JsonProcessingException e) {
            log.warn("Failed to store idempotency record for key={}", idempotencyKey, e);
        }
    }

    private AccountResponse toResponse(AccountProjection p) {
        String balance = p.getBalance().setScale(2, RoundingMode.HALF_UP).toPlainString();
        return new AccountResponse(
                p.getAccountId(), p.getOwnerId(),
                balance, p.getCurrency(), p.getStatus());
    }
}
