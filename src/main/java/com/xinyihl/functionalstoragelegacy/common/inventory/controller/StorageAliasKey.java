package com.xinyihl.functionalstoragelegacy.common.inventory.controller;

import com.xinyihl.functionalstoragelegacy.api.storage.StorageKey;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Small immutable key used for secondary compatibility indexes.  Alias keys
 * are deliberately kept separate from exact item/fluid keys so an alias can
 * never accidentally become an exact storage identity.
 */
public final class StorageAliasKey implements StorageKey {

    private final String namespace;
    private final int value;
    private final int hashCode;

    public StorageAliasKey(@Nonnull String namespace, int value) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.value = value;
        this.hashCode = 31 * namespace.hashCode() + value;
    }

    /** Creates the alias used by Forge's ore dictionary ids. */
    @Nonnull
    public static StorageAliasKey ore(int oreId) {
        return new StorageAliasKey("ore", oreId);
    }

    @Nonnull
    public String getNamespace() {
        return namespace;
    }

    public int getValue() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof StorageAliasKey)) {
            return false;
        }
        StorageAliasKey other = (StorageAliasKey) object;
        return value == other.value && namespace.equals(other.namespace);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return namespace + ':' + value;
    }
}
