package com.xinyihl.functionalstoragelegacy.common.inventory.controller;

import javax.annotation.Nonnull;

/**
 * Internal contract for stable forwarding handlers whose physical storage
 * identity can change independently of the facade object.
 */
public interface StorageIdentityProvider {

    /** @return current physical storage identity, compared by object identity */
    @Nonnull
    Object getStorageIdentity();
}
