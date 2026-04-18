package io.payflow.transfer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record InitiateTransferRequest(
        @NotBlank String sourceAccountId,
        @NotBlank String targetAccountId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String currency
) {
    public InitiateTransferRequest(String sourceAccountId, String targetAccountId,
                                    String amount, String currency) {
        this(sourceAccountId, targetAccountId, new BigDecimal(amount), currency);
    }
}
