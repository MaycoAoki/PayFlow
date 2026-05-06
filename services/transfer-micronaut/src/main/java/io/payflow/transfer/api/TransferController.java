package io.payflow.transfer.api;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import io.payflow.transfer.api.dto.InitiateTransferRequest;
import io.payflow.transfer.api.dto.TransferResponse;
import io.payflow.transfer.service.TransferSagaOrchestrator;
import jakarta.validation.Valid;

@Controller("/transfers")
public class TransferController {

    private final TransferSagaOrchestrator orchestrator;

    public TransferController(TransferSagaOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Post
    @Status(HttpStatus.ACCEPTED)
    public TransferResponse initiate(
            @Body @Valid InitiateTransferRequest req,
            @Header("Idempotency-Key") String idempotencyKey) {
        return orchestrator.initiate(req, idempotencyKey);
    }

    @Get("/{id}")
    public TransferResponse getTransfer(@PathVariable("id") String id) {
        return orchestrator.findTransfer(id);
    }
}
