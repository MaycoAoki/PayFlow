package io.payflow.account.api;

import io.payflow.account.api.dto.AccountResponse;
import io.payflow.account.api.dto.CreateAccountRequest;
import io.payflow.account.api.dto.DepositRequest;
import io.payflow.account.api.dto.EventSummary;
import io.payflow.account.service.AccountService;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AccountResponse> createAccount(
            @RequestBody @Valid CreateAccountRequest req,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return accountService.createAccount(req, idempotencyKey);
    }

    @GetMapping("/{id}")
    public Mono<AccountResponse> getAccount(@PathVariable("id") String id) {
        return accountService.findAccount(id);
    }

    @GetMapping("/{id}/events")
    public Flux<EventSummary> getAccountEvents(@PathVariable("id") String id) {
        return accountService.listEvents(id);
    }

    @PostMapping("/{id}/deposits")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<Void> deposit(
            @PathVariable("id") String id,
            @RequestBody @Valid DepositRequest req,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return accountService.deposit(id, req, idempotencyKey);
    }
}
