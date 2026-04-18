package io.payflow.transfer.kafka.command;

public record CreditCommand(
        String commandType,
        String transferId,
        String targetAccountId,
        String amount,
        String currency
) {
    public static CreditCommand of(String transferId, String targetAccountId, String amount, String currency) {
        return new CreditCommand("CREDIT", transferId, targetAccountId, amount, currency);
    }
}
