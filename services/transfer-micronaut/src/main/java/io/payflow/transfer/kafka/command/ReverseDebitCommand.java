package io.payflow.transfer.kafka.command;

public record ReverseDebitCommand(
        String commandType,
        String transferId,
        String sourceAccountId,
        String amount,
        String currency
) {
    public static ReverseDebitCommand of(String transferId, String sourceAccountId, String amount, String currency) {
        return new ReverseDebitCommand("REVERSE_DEBIT", transferId, sourceAccountId, amount, currency);
    }
}
