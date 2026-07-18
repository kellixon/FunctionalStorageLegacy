package com.xinyihl.functionalstoragelegacy.api.storage;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * Generic indexed long-capacity storage. Implementations follow the threading
 * model of their owning tile or capability and are not implicitly thread-safe.
 *
 * @param <S> immutable snapshot self type
 * @param <K> immutable exact resource key type
 */
public interface IStorageHandler<S extends StorageSnapshot<S, K>, K extends StorageKey> {

    /**
     * @return number of real indexed storage positions
     */
    int getStorageCount();

    /**
     * @return detached immutable snapshot, or an unconfigured empty snapshot for an invalid index
     */
    @Nonnull
    S getSnapshot(int index);

    /**
     * @return non-negative long capacity, or zero for an invalid index
     */
    long getCapacity(int index);

    /**
     * Inserts into exactly one index.
     */
    @Nonnull
    TransferResult<S, K> insert(int index, @Nonnull S request, @Nonnull StorageAction action);

    /**
     * Extracts from exactly one index.
     */
    @Nonnull
    TransferResult<S, K> extract(int index, long amount, @Nonnull StorageAction action);

    /**
     * @return whether empty storage retains and enforces a resource filter
     */
    default boolean isLocked() {
        return false;
    }

    /**
     * @return whether compatible overflow is consumed instead of returned
     */
    default boolean voidsOverflow() {
        return false;
    }

    /**
     * @return whether extraction can report resources without consuming storage
     */
    default boolean isCreative() {
        return false;
    }

    /**
     * @return current storage capacity multiplier
     */
    default double getMultiplier() {
        return 1.0D;
    }

    /**
     * @return stable physical storage identity; wrappers should forward their
     * target identity so aggregate handlers can remove duplicates
     */
    @Nonnull
    default Object getStorageIdentity() {
        return this;
    }

    /**
     * Publishes a completed observable change. Stateless compatibility handlers
     * may keep the default no-op; mutable handlers should delegate to a
     * {@link StorageChangeDispatcher}.
     */
    default void onChange(@Nonnull StorageChange<S, K> change) {
    }

    /**
     * Subscribes to observable storage changes. Handlers without an event
     * source return an already-closed subscription.
     */
    @Nonnull
    default StorageSubscription subscribe(@Nonnull Consumer<? super StorageChange<S, K>> listener) {
        return StorageSubscription.CLOSED;
    }
}
