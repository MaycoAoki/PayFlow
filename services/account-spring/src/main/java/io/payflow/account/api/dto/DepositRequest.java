package io.payflow.account.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DepositRequest(
        @NotNull @Positive BigDecimal amount,
        @NotBlank String currency
) {
    public DepositRequest(String amount, String currency) {
        this(new BigDecimal(amount), currency);
    }
}
