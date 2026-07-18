package com.xinyihl.functionalstoragelegacy.api.storage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

/**
 * Resource-specific routing contract used by generic indexed handlers.
 * Priorities are ordered from lower to higher; a negative priority excludes a
 * candidate. Compatibility aliases are stable value keys such as ore IDs.
 */
public interface StorageRoutingPolicy<
        S extends StorageSnapshot<S, K>, K extends StorageKey> {

    /** @return the exact key used by the primary index, or null when unconfigured */
    @Nullable
    default K getExactKey(@Nonnull S snapshot) {
        return snapshot.getKey();
    }

    /** @return stable compatibility alias keys used by secondary indexes */
    @Nonnull
    default Collection<? extends StorageKey> getCompatibleAliases(@Nonnull S snapshot) {
        return Collections.emptyList();
    }

    /** @return whether an unconfigured index may accept this request */
    boolean isEmptySlotEligible(
            @Nonnull IStorageHandler<S, K> handler, int index, @Nonnull S request);

    /**
     * Returns the candidate priority for the current snapshot and request.
     * Lower values are attempted first; a negative value means ineligible.
     */
    int getCandidatePriority(
            @Nonnull IStorageHandler<S, K> handler,
            int index,
            @Nonnull S current,
            @Nonnull S request);
}
