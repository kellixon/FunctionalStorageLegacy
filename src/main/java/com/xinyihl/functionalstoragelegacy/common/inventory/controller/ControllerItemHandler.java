package com.xinyihl.functionalstoragelegacy.common.inventory.controller;

import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.api.storage.TransferResult;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Flat aggregate of connected item handlers. Slot-addressed operations perform
 * one bounds check and delegate to exactly one child slot; global routing is
 * only performed by the explicit routed methods.
 */
public final class ControllerItemHandler implements IBigItemHandler {

    private List<IBigItemHandler> handlers = Collections.emptyList();
    private List<HandlerSlotMapping> slotMappings = Collections.emptyList();

    @Override
    public int getSlotCount() {
        return slotMappings.size();
    }

    @Nonnull
    @Override
    public BigItemStack getSlotSnapshot(int slot) {
        HandlerSlotMapping mapping = mappingAt(slot);
        return mapping == null
                ? BigItemStack.empty()
                : mapping.handler.getSlotSnapshot(mapping.localSlot);
    }

    @Override
    public long getSlotCapacity(int slot) {
        HandlerSlotMapping mapping = mappingAt(slot);
        return mapping == null ? 0L : mapping.handler.getSlotCapacity(mapping.localSlot);
    }

    @Nonnull
    @Override
    public TransferResult<BigItemStack> insertIntoSlot(
            int slot, @Nonnull BigItemStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request == null || request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L) {
            return new TransferResult<>(0L, BigItemStack.empty(), action);
        }
        HandlerSlotMapping mapping = mappingAt(slot);
        if (mapping == null) {
            return new TransferResult<>(requested, BigItemStack.empty(), action);
        }
        return mapping.handler.insertIntoSlot(mapping.localSlot, request, action);
    }

    @Nonnull
    @Override
    public TransferResult<BigItemStack> extractFromSlot(
            int slot, long amount, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = Math.max(0L, amount);
        if (requested == 0L) {
            return new TransferResult<>(0L, BigItemStack.empty(), action);
        }
        HandlerSlotMapping mapping = mappingAt(slot);
        if (mapping == null) {
            return new TransferResult<>(requested, BigItemStack.empty(), action);
        }
        return mapping.handler.extractFromSlot(mapping.localSlot, amount, action);
    }

    /**
     * Performs one classification scan and then drains candidates in the
     * required order: locked matching slots, unlocked matching slots, and
     * finally unconfigured slots in unlocked handlers.
     */
    @Nonnull
    @Override
    public TransferResult<BigItemStack> insertRouted(
            @Nonnull BigItemStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request == null || request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L) {
            return new TransferResult<>(0L, BigItemStack.empty(), action);
        }

        List<HandlerSlotMapping> lockedMatches = new ArrayList<>();
        List<HandlerSlotMapping> unlockedMatches = new ArrayList<>();
        List<HandlerSlotMapping> unlockedEmpty = new ArrayList<>();
        BigItemStack compatibilityProbe = request.withAmount(1L);

        for (HandlerSlotMapping mapping : slotMappings) {
            BigItemStack current = mapping.handler.getSlotSnapshot(mapping.localSlot);
            boolean hasTemplate = current != null && current.hasTemplate();
            if (!hasTemplate) {
                if (!mapping.handler.isLocked()) {
                    unlockedEmpty.add(mapping);
                }
                continue;
            }

            boolean compatible = current.isSameType(request);
            if (!compatible) {
                TransferResult<BigItemStack> probe = mapping.handler.insertIntoSlot(
                        mapping.localSlot, compatibilityProbe, StorageAction.SIMULATE);
                compatible = probe != null && probe.getProcessedAmount() > 0L;
            }
            if (!compatible) {
                continue;
            }
            if (mapping.handler.isLocked()) {
                lockedMatches.add(mapping);
            } else {
                unlockedMatches.add(mapping);
            }
        }

        long processed = 0L;
        processed = insertCandidates(lockedMatches, request, action, processed);
        processed = insertCandidates(unlockedMatches, request, action, processed);
        processed = insertCandidates(unlockedEmpty, request, action, processed);
        return aggregateResult(request, processed, action);
    }

    /** Routes type-sensitive extraction across matching slots in flat order. */
    @Nonnull
    @Override
    public TransferResult<BigItemStack> extractRouted(
            @Nonnull BigItemStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request == null || request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L) {
            return new TransferResult<>(0L, BigItemStack.empty(), action);
        }

        long processed = 0L;
        for (HandlerSlotMapping mapping : slotMappings) {
            if (processed >= requested) {
                break;
            }
            BigItemStack current = mapping.handler.getSlotSnapshot(mapping.localSlot);
            if (current == null || !current.hasTemplate() || !current.isSameType(request)) {
                continue;
            }
            long remaining = requested - processed;
            TransferResult<BigItemStack> result = mapping.handler.extractFromSlot(
                    mapping.localSlot, remaining, action);
            long extracted = result == null ? 0L
                    : Math.min(remaining, Math.max(0L, result.getProcessedAmount()));
            processed += extracted;
        }
        return aggregateResult(request, processed, action);
    }

    /**
     * Atomically replaces the child list and its flattened O(1) slot index.
     * Later mutations of the caller's list cannot leave stale mappings behind.
     */
    public void setHandlers(@Nonnull List<? extends IBigItemHandler> newHandlers) {
        Objects.requireNonNull(newHandlers, "newHandlers");
        List<IBigItemHandler> handlerCopy = new ArrayList<>(newHandlers.size());
        List<HandlerSlotMapping> mappings = new ArrayList<>();
        Set<Object> seen = Collections.newSetFromMap(
                new IdentityHashMap<Object, Boolean>());
        for (IBigItemHandler handler : newHandlers) {
            if (handler == null || !seen.add(identityOf(handler))) {
                continue;
            }
            handlerCopy.add(handler);
            int slots = Math.max(0, handler.getSlotCount());
            for (int slot = 0; slot < slots; slot++) {
                mappings.add(new HandlerSlotMapping(handler, slot));
            }
        }
        handlers = Collections.unmodifiableList(handlerCopy);
        slotMappings = Collections.unmodifiableList(mappings);
    }

    /** @return an immutable snapshot of the current child handler order */
    @Nonnull
    public List<IBigItemHandler> getHandlers() {
        return handlers;
    }

    private static Object identityOf(IBigItemHandler handler) {
        if (handler instanceof StorageIdentityProvider) {
            Object identity = ((StorageIdentityProvider) handler).getStorageIdentity();
            if (identity != null) {
                return identity;
            }
        }
        return handler;
    }

    private long insertCandidates(
            List<HandlerSlotMapping> candidates,
            BigItemStack request,
            StorageAction action,
            long alreadyProcessed) {
        long processed = alreadyProcessed;
        for (HandlerSlotMapping mapping : candidates) {
            if (processed >= request.getAmount()) {
                break;
            }
            long remaining = request.getAmount() - processed;
            TransferResult<BigItemStack> result = mapping.handler.insertIntoSlot(
                    mapping.localSlot, request.withAmount(remaining), action);
            long inserted = result == null ? 0L
                    : Math.min(remaining, Math.max(0L, result.getProcessedAmount()));
            processed += inserted;
        }
        return processed;
    }

    private HandlerSlotMapping mappingAt(int slot) {
        return slot < 0 || slot >= slotMappings.size() ? null : slotMappings.get(slot);
    }

    private static TransferResult<BigItemStack> aggregateResult(
            BigItemStack request, long processed, StorageAction action) {
        return new TransferResult<>(
                request.getAmount(),
                processed == 0L ? BigItemStack.empty() : request.withAmount(processed),
                action);
    }

    private static final class HandlerSlotMapping {
        private final IBigItemHandler handler;
        private final int localSlot;

        private HandlerSlotMapping(IBigItemHandler handler, int localSlot) {
            this.handler = handler;
            this.localSlot = localSlot;
        }
    }
}
