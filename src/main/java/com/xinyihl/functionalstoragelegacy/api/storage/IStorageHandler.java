package com.xinyihl.functionalstoragelegacy.api.storage;

/**
 * Shared optional state exposed by all large storage handlers. Implementations
 * are assumed to be called under the same thread and synchronization rules as
 * their owning Forge tile or capability.
 */
public interface IStorageHandler {

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
}
