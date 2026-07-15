package com.xinyihl.functionalstoragelegacy.api.storage;

/**
 * Read-only view of a stored resource and its non-negative long amount.
 * Snapshots exposed by the public API must not share mutable resource state
 * with a handler.
 */
public interface StorageSnapshot {

    /**
     * @return the represented amount, always zero or greater
     */
    long getAmount();

    /**
     * @return {@code true} when this snapshot has no resource or amount
     */
    default boolean isEmpty() {
        return getAmount() == 0L;
    }
}
