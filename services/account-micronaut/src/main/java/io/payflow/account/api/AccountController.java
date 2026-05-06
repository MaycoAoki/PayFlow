package io.payflow.account.api;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import io.payflow.account.api.dto.AccountResponse;
import io.payflow.account.api.dto.CreateAccountRequest;
import io.payflow.account.api.dto.DepositRequest;
import io.payflow.account.api.dto.EventSummary;
import io.payflow.account.service.AccountService;
import jakarta.validation.Valid;

import java.util.List;

@Controller("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Post
    @Status(HttpStatus.CREATED)
    public AccountResponse createAccount(@Body @Valid CreateAccountRequest req,
                                          @Header("Idempotency-Key") String idempotencyKey) {
        return accountService.createAccount(req, idempotencyKey);
    }

    @Get("/{id}")
    public AccountResponse getAccount(@PathVariable String id) {
        return accountService.findAccount(id);
    }

    @Get("/{id}/events")
    public List<EventSummary> getAccountEvents(@PathVariable String id) {
        return accountService.listEvents(id);
    }

    @Post("/{id}/deposits")
    @Status(HttpStatus.ACCEPTED)
    public HttpResponse<Void> deposit(@PathVariable String id,
                                       @Body @Valid DepositRequest req,
                                       @Header("Idempotency-Key") String idempotencyKey) {
        accountService.deposit(id, req, idempotencyKey);
        return HttpResponse.accepted();
    }
}
