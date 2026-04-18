package io.payflow.transfer.kafka.command;

public record DebitCommand(
        String commandType,
        String transferId,
        String sourceAccountId,
        String amount,
        String currency
) {
    public static DebitCommand of(String transferId, String sourceAccountId, String amount, String currency) {
        return new DebitCommand("DEBIT", transferId, sourceAccountId, amount, currency);
    }
}
