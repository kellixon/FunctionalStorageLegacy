package com.xinyihl.functionalstoragelegacy.common.inventory.controller;

import com.xinyihl.functionalstoragelegacy.api.storage.BigFluidStack;
import com.xinyihl.functionalstoragelegacy.api.storage.FluidStorageKey;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigFluidHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.IStorageHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageKey;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageRoutingPolicy;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

/** Default exact-key fluid routing policy. */
public final class FluidStorageRoutingPolicy
        implements StorageRoutingPolicy<BigFluidStack, FluidStorageKey> {

    @Override
    public FluidStorageKey getExactKey(@Nonnull BigFluidStack snapshot) {
        return snapshot.getKey();
    }

    @Nonnull
    @Override
    public Collection<? extends StorageKey> getCompatibleAliases(
            @Nonnull BigFluidStack snapshot) {
        return Collections.emptyList();
    }

    @Override
    public boolean isEmptySlotEligible(
            @Nonnull IStorageHandler<BigFluidStack, FluidStorageKey> handler,
            int index, @Nonnull BigFluidStack request) {
        return true;
    }

    @Override
    public int getCandidatePriority(
            @Nonnull IStorageHandler<BigFluidStack, FluidStorageKey> handler,
            int index,
            @Nonnull BigFluidStack current,
            @Nonnull BigFluidStack request) {
        return current.hasTemplate() ? (handler.isLocked() ? 0 : 1) : -1;
    }
}
