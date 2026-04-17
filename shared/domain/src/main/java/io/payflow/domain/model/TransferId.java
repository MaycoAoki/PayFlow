package io.payflow.domain.model;

import java.util.UUID;

/**
 * Typed UUID wrapper for Transfer aggregate identity.
 */
public record TransferId(UUID value) {

    public TransferId {
        if (value == null) {
            throw new IllegalArgumentException("TransferId value must not be null");
        }
    }

    public static TransferId of(UUID value) {
        return new TransferId(value);
    }

    public static TransferId of(String value) {
        return new TransferId(UUID.fromString(value));
    }

    public static TransferId generate() {
        return new TransferId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
