package io.payflow.transfer.api;

import io.payflow.transfer.api.dto.InitiateTransferRequest;
import io.payflow.transfer.api.dto.TransferResponse;
import io.payflow.transfer.service.TransferSagaOrchestrator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferSagaOrchestrator orchestrator;

    public TransferController(TransferSagaOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<TransferResponse> initiate(
            @RequestBody @Valid InitiateTransferRequest req,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return orchestrator.initiate(req, idempotencyKey);
    }

    @GetMapping("/{id}")
    public Mono<TransferResponse> getTransfer(@PathVariable("id") String id) {
        return orchestrator.findTransfer(id);
    }
}
