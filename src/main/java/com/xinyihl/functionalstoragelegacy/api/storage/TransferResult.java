package com.xinyihl.functionalstoragelegacy.api.storage;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Immutable outcome of a storage request. The processed snapshot describes
 * what the caller may insert, remove, or otherwise consume. For void and
 * creative storage this amount need not equal a physical state change.
 *
 * @param <S> concrete snapshot type
 * @param <K> immutable resource key type
 */
public final class TransferResult<S extends StorageSnapshot<S, K>, K extends StorageKey> {

    private final long requestedAmount;
    private final S processed;
    private final StorageAction action;

    /**
     * Creates a validated result.
     *
     * @throws NullPointerException     if {@code processed} or {@code action} is null
     * @throws IllegalArgumentException if amounts are negative, processed exceeds
     *                                  requested, or emptiness contradicts the amount
     */
    public TransferResult(long requestedAmount, @Nonnull S processed, @Nonnull StorageAction action) {
        this.processed = Objects.requireNonNull(processed, "processed");
        this.action = Objects.requireNonNull(action, "action");
        long processedAmount = processed.getAmount();
        if (requestedAmount < 0L) {
            throw new IllegalArgumentException("requestedAmount must be non-negative");
        }
        if (processedAmount < 0L || processedAmount > requestedAmount) {
            throw new IllegalArgumentException("processed amount must be between zero and requestedAmount");
        }
        if (processed.isEmpty() != (processedAmount == 0L)) {
            throw new IllegalArgumentException("processed emptiness must match its amount");
        }
        this.requestedAmount = requestedAmount;
    }

    public long getRequestedAmount() {
        return requestedAmount;
    }

    @Nonnull
    public S getProcessed() {
        return processed;
    }

    public long getProcessedAmount() {
        return processed.getAmount();
    }

    @Nonnull
    public StorageAction getAction() {
        return action;
    }

    public long getRemainingAmount() {
        return requestedAmount - processed.getAmount();
    }

    public boolean isComplete() {
        return getRemainingAmount() == 0L;
    }
}
