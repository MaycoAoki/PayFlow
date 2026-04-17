package io.payflow.domain.port;

/**
 * Thrown by {@link EventStore#append} when the actual stream version does not match
 * the caller's expected version (optimistic concurrency violation).
 */
public class OptimisticLockException extends RuntimeException {

    private final String aggregateId;
    private final long expectedVersion;
    private final long actualVersion;

    public OptimisticLockException(String aggregateId, long expectedVersion, long actualVersion) {
        super(String.format(
                "Optimistic lock failure for aggregate '%s': expected version %d but was %d",
                aggregateId, expectedVersion, actualVersion));
        this.aggregateId = aggregateId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public String aggregateId() {
        return aggregateId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }

    public long actualVersion() {
        return actualVersion;
    }
}
