package com.xinyihl.functionalstoragelegacy.api.storage;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Immutable outcome of a storage request. The processed snapshot describes
 * what the caller may insert, remove, or otherwise consume. For void and
 * creative storage this amount need not equal a physical state change.
 * Snapshot instances are retained because the {@link StorageSnapshot}
 * contract requires them to be read-only.
 *
 * @param <S> concrete snapshot type
 */
public final class TransferResult<S extends StorageSnapshot> {

    private final long requestedAmount;
    private final S processed;
    private final StorageAction action;

    /**
     * Creates a validated result.
     *
     * @param requestedAmount original non-negative request amount
     * @param processed immutable processed resource snapshot
     * @param action operation mode used to obtain the result
     * @throws NullPointerException if {@code processed} or {@code action} is null
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

    /**
     * @return the original request amount
     */
    public long getRequestedAmount() {
        return requestedAmount;
    }

    /**
     * @return the immutable processed resource snapshot
     */
    @Nonnull
    public S getProcessed() {
        return processed;
    }

    /**
     * @return the processed amount
     */
    public long getProcessedAmount() {
        return processed.getAmount();
    }

    /**
     * @return the operation mode used for the request
     */
    @Nonnull
    public StorageAction getAction() {
        return action;
    }

    /**
     * @return unprocessed amount; subtraction cannot underflow after validation
     */
    public long getRemainingAmount() {
        return requestedAmount - processed.getAmount();
    }

    /**
     * @return {@code true} when the entire request was processed, including a zero request
     */
    public boolean isComplete() {
        return getRemainingAmount() == 0L;
    }
}
