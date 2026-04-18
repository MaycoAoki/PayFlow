package io.payflow.transfer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.payflow.domain.event.TransferCompletedEvent;
import io.payflow.domain.event.TransferCreditFailedEvent;
import io.payflow.domain.event.TransferCreditedEvent;
import io.payflow.domain.event.TransferDebitFailedEvent;
import io.payflow.domain.event.TransferDebitedEvent;
import io.payflow.domain.event.TransferInitiatedEvent;
import io.payflow.domain.event.TransferReversedEvent;
import io.payflow.domain.model.AccountId;
import io.payflow.domain.model.Money;
import io.payflow.domain.model.TransferId;
import io.payflow.domain.model.TransferStatus;
import io.payflow.domain.port.EventStore;
import io.payflow.transfer.api.dto.InitiateTransferRequest;
import io.payflow.transfer.api.dto.TransferResponse;
import io.payflow.transfer.kafka.TransferCommandPublisher;
import io.payflow.transfer.persistence.IdempotencyRecord;
import io.payflow.transfer.persistence.IdempotencyRepository;
import io.payflow.transfer.persistence.TransferProjection;
import io.payflow.transfer.persistence.TransferProjectionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class TransferSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TransferSagaOrchestrator.class);

    private final EventStore eventStore;
    private final TransferProjectionRepository projectionRepo;
    private final IdempotencyRepository idempotencyRepo;
    private final TransferCommandPublisher commandPublisher;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public TransferSagaOrchestrator(EventStore eventStore,
                                     TransferProjectionRepository projectionRepo,
                                     IdempotencyRepository idempotencyRepo,
                                     TransferCommandPublisher commandPublisher,
                                     ObjectMapper objectMapper,
                                     MeterRegistry meterRegistry) {
        this.eventStore = eventStore;
        this.projectionRepo = projectionRepo;
        this.idempotencyRepo = idempotencyRepo;
        this.commandPublisher = commandPublisher;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    public Mono<TransferResponse> initiate(InitiateTransferRequest req, String idempotencyKey) {
        return Mono.fromCallable(() -> doInitiate(req, idempotencyKey))
                   .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    protected TransferResponse doInitiate(InitiateTransferRequest req, String idempotencyKey) {
        var existing = idempotencyRepo.findById(idempotencyKey);
        if (existing.isPresent() && !existing.get().isExpired()) {
            try {
                return objectMapper.readValue(existing.get().getResponseBody(), TransferResponse.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to deserialize cached response", e);
            }
        }

        TransferId transferId = TransferId.generate();
        AccountId sourceId = AccountId.of(req.sourceAccountId());
        AccountId targetId = AccountId.of(req.targetAccountId());
        Money amount = Money.of(req.amount(), req.currency());

        var event = new TransferInitiatedEvent(transferId, sourceId, targetId, amount, Instant.now());
        eventStore.append(transferId.toString(), "Transfer", 0L, List.of(event));

        var projection = new TransferProjection(
                transferId.toString(), req.sourceAccountId(), req.targetAccountId(),
                amount.amount(), amount.currency().getCurrencyCode());
        projectionRepo.save(projection);

        TransferResponse response = toResponse(projection);
        storeIdempotencyRecord(idempotencyKey, transferId.toString(), response);

        meterRegistry.counter("payflow.transfers.initiated.total").increment();
        log.info("Transfer initiated transferId={} from={} to={} amount={}", transferId, sourceId, targetId, amount);

        commandPublisher.publishDebitCommand(
                transferId.toString(), req.sourceAccountId(),
                amount.amount().toPlainString(), amount.currency().getCurrencyCode());

        return response;
    }

    public Mono<TransferResponse> findTransfer(String transferId) {
        return Mono.fromCallable(() -> projectionRepo.findById(transferId)
                        .orElseThrow(() -> new NoSuchElementException("Transfer not found: " + transferId)))
                   .subscribeOn(Schedulers.boundedElastic())
                   .map(this::toResponse);
    }

    @Transactional
    public void onMoneyDebited(String transferId, String sourceAccountId, String amount, String currency) {
        var projection = projectionRepo.findById(transferId).orElse(null);
        if (projection == null || !TransferStatus.INITIATED.name().equals(projection.getStatus())) return;

        long expectedVersion = eventStore.loadEvents(transferId).size();
        var event = new TransferDebitedEvent(
                TransferId.of(transferId), AccountId.of(sourceAccountId),
                Money.of(amount, currency), Instant.now());
        eventStore.append(transferId, "Transfer", expectedVersion, List.of(event));

        projection.updateStatus(TransferStatus.DEBITING.name());
        projectionRepo.save(projection);

        log.info("Transfer debited transferId={}", transferId);
        commandPublisher.publishCreditCommand(
                transferId, projection.getTargetAccountId(), amount, currency);
    }

    @Transactional
    public void onDebitFailed(String transferId, String reason) {
        var projection = projectionRepo.findById(transferId).orElse(null);
        if (projection == null || !TransferStatus.INITIATED.name().equals(projection.getStatus())) return;

        long expectedVersion = eventStore.loadEvents(transferId).size();
        var event = new TransferDebitFailedEvent(TransferId.of(transferId), reason, Instant.now());
        eventStore.append(transferId, "Transfer", expectedVersion, List.of(event));

        projection.updateStatus(TransferStatus.FAILED.name());
        projectionRepo.save(projection);

        meterRegistry.counter("payflow.transfers.failed.total", "reason", reason).increment();
        log.info("Transfer debit failed transferId={} reason={}", transferId, reason);
    }

    @Transactional
    public void onMoneyCredited(String transferId, String targetAccountId, String amount, String currency) {
        var projection = projectionRepo.findById(transferId).orElse(null);
        if (projection == null || !TransferStatus.DEBITING.name().equals(projection.getStatus())) return;

        long expectedVersion = eventStore.loadEvents(transferId).size();
        var creditedEvent = new TransferCreditedEvent(
                TransferId.of(transferId), AccountId.of(targetAccountId),
                Money.of(amount, currency), Instant.now());
        var completedEvent = new TransferCompletedEvent(TransferId.of(transferId), Instant.now());
        eventStore.append(transferId, "Transfer", expectedVersion, List.of(creditedEvent, completedEvent));

        projection.updateStatus(TransferStatus.COMPLETED.name());
        projectionRepo.save(projection);

        meterRegistry.counter("payflow.transfers.completed.total").increment();
        log.info("Transfer completed transferId={}", transferId);
    }

    @Transactional
    public void onCreditFailed(String transferId, String reason) {
        var projection = projectionRepo.findById(transferId).orElse(null);
        if (projection == null || !TransferStatus.DEBITING.name().equals(projection.getStatus())) return;

        long expectedVersion = eventStore.loadEvents(transferId).size();
        var event = new TransferCreditFailedEvent(TransferId.of(transferId), reason, Instant.now());
        eventStore.append(transferId, "Transfer", expectedVersion, List.of(event));

        projection.updateStatus(TransferStatus.REVERSING.name());
        projectionRepo.save(projection);

        log.info("Transfer credit failed transferId={} reason={} — initiating reversal", transferId, reason);
        commandPublisher.publishReverseDebitCommand(
                transferId, projection.getSourceAccountId(),
                projection.getAmount().toPlainString(), projection.getCurrency());
    }

    @Transactional
    public void onDebitReversed(String transferId) {
        var projection = projectionRepo.findById(transferId).orElse(null);
        if (projection == null || !TransferStatus.REVERSING.name().equals(projection.getStatus())) return;

        long expectedVersion = eventStore.loadEvents(transferId).size();
        var event = new TransferReversedEvent(TransferId.of(transferId), Instant.now());
        eventStore.append(transferId, "Transfer", expectedVersion, List.of(event));

        projection.updateStatus(TransferStatus.REVERSED.name());
        projectionRepo.save(projection);

        meterRegistry.counter("payflow.transfers.reversed.total").increment();
        log.info("Transfer reversed transferId={}", transferId);
    }

    private void storeIdempotencyRecord(String idempotencyKey, String aggregateId, Object response) {
        try {
            String body = objectMapper.writeValueAsString(response);
            idempotencyRepo.save(new IdempotencyRecord(idempotencyKey, aggregateId, body, (short) 202));
        } catch (JsonProcessingException e) {
            log.warn("Failed to store idempotency record for key={}", idempotencyKey, e);
        }
    }

    private TransferResponse toResponse(TransferProjection p) {
        String amount = p.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();
        return new TransferResponse(
                p.getTransferId(), p.getSourceAccountId(), p.getTargetAccountId(),
                amount, p.getCurrency(), p.getStatus(), p.getCreatedAt());
    }
}
