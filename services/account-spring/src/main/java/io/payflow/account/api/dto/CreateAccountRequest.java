package io.payflow.account.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank String ownerId,
        @NotNull @Positive BigDecimal initialBalance,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency
) {
    public CreateAccountRequest(String ownerId, String initialBalance, String currency) {
        this(ownerId, new BigDecimal(initialBalance), currency);
    }
}
