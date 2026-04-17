package io.payflow.domain.model;

/**
 * State machine statuses for the Transfer aggregate saga.
 *
 * <p>Valid transitions:
 * <pre>
 *   INITIATED → DEBITING → CREDITING → COMPLETED
 *   DEBITING  → FAILED
 *   CREDITING → REVERSING → REVERSED
 * </pre>
 */
public enum TransferStatus {
    INITIATED,
    DEBITING,
    CREDITING,
    COMPLETED,
    FAILED,
    REVERSING,
    REVERSED
}
