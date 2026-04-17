package io.payflow.domain.model;

import java.util.UUID;

/**
 * Typed UUID wrapper for Account aggregate identity.
 */
public record AccountId(UUID value) {

    public AccountId {
        if (value == null) {
            throw new IllegalArgumentException("AccountId value must not be null");
        }
    }

    public static AccountId of(UUID value) {
        return new AccountId(value);
    }

    public static AccountId of(String value) {
        return new AccountId(UUID.fromString(value));
    }

    public static AccountId generate() {
        return new AccountId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
