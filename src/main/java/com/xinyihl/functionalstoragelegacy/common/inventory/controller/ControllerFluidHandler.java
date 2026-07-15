package com.xinyihl.functionalstoragelegacy.common.inventory.controller;

import com.xinyihl.functionalstoragelegacy.api.storage.BigFluidStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigFluidHandler;
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
 * Flat aggregate of connected fluid handlers. Tank-addressed operations do one
 * bounds check and delegate to exactly one child tank; global scans occur only
 * in the explicit routed methods. Rebuilding atomically replaces both the
 * immutable child list and its O(1) flattened index.
 */
public final class ControllerFluidHandler implements IBigFluidHandler {

    private List<IBigFluidHandler> handlers = Collections.emptyList();
    private List<HandlerTankMapping> tankMappings = Collections.emptyList();

    @Override
    public int getTankCount() {
        return tankMappings.size();
    }

    @Nonnull
    @Override
    public BigFluidStack getTankSnapshot(int tank) {
        HandlerTankMapping mapping = mappingAt(tank);
        return mapping == null
                ? BigFluidStack.empty()
                : mapping.handler.getTankSnapshot(mapping.localTank);
    }

    @Override
    public long getTankCapacity(int tank) {
        HandlerTankMapping mapping = mappingAt(tank);
        return mapping == null ? 0L : mapping.handler.getTankCapacity(mapping.localTank);
    }

    @Override
    public boolean supportsFill(int tank) {
        HandlerTankMapping mapping = mappingAt(tank);
        return mapping != null && mapping.handler.supportsFill(mapping.localTank);
    }

    @Override
    public boolean supportsDrain(int tank) {
        HandlerTankMapping mapping = mappingAt(tank);
        return mapping != null && mapping.handler.supportsDrain(mapping.localTank);
    }

    @Override
    public boolean supportsFluid(int tank, @Nonnull BigFluidStack fluid) {
        HandlerTankMapping mapping = mappingAt(tank);
        return mapping != null
                && fluid != null
                && mapping.handler.supportsFluid(mapping.localTank, fluid);
    }

    @Nonnull
    @Override
    public TransferResult<BigFluidStack> fillTank(
            int tank, @Nonnull BigFluidStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request == null || request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L) {
            return emptyResult(0L, action);
        }
        HandlerTankMapping mapping = mappingAt(tank);
        return mapping == null
                ? emptyResult(requested, action)
                : mapping.handler.fillTank(mapping.localTank, request, action);
    }

    @Nonnull
    @Override
    public TransferResult<BigFluidStack> drainTank(
            int tank, long amount, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = Math.max(0L, amount);
        if (requested == 0L) {
            return emptyResult(0L, action);
        }
        HandlerTankMapping mapping = mappingAt(tank);
        return mapping == null
                ? emptyResult(requested, action)
                : mapping.handler.drainTank(mapping.localTank, requested, action);
    }

    /**
     * Performs one classification scan, then fills matching configured tanks
     * before unconfigured empty tanks. Mismatched retained filters are skipped.
     */
    @Nonnull
    @Override
    public TransferResult<BigFluidStack> fillRouted(
            @Nonnull BigFluidStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request == null || request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L) {
            return emptyResult(0L, action);
        }

        List<HandlerTankMapping> matching = new ArrayList<>();
        List<HandlerTankMapping> empty = new ArrayList<>();
        for (HandlerTankMapping mapping : tankMappings) {
            if (!mapping.handler.supportsFill(mapping.localTank)
                    || !mapping.handler.supportsFluid(mapping.localTank, request)) {
                continue;
            }
            BigFluidStack current = mapping.handler.getTankSnapshot(mapping.localTank);
            if (current != null && current.hasTemplate()) {
                if (current.isSameType(request)) {
                    matching.add(mapping);
                }
            } else {
                empty.add(mapping);
            }
        }

        long processed = fillCandidates(matching, request, action, 0L);
        processed = fillCandidates(empty, request, action, processed);
        return aggregateResult(request, processed, action);
    }

    /** Routes type-sensitive draining across matching child tanks in flat order. */
    @Nonnull
    @Override
    public TransferResult<BigFluidStack> drainRouted(
            @Nonnull BigFluidStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request == null || request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L) {
            return emptyResult(0L, action);
        }

        long processed = 0L;
        for (HandlerTankMapping mapping : tankMappings) {
            if (processed >= requested
                    || !mapping.handler.supportsDrain(mapping.localTank)
                    || !mapping.handler.supportsFluid(mapping.localTank, request)) {
                continue;
            }
            BigFluidStack current = mapping.handler.getTankSnapshot(mapping.localTank);
            if (current == null || current.isEmpty() || !current.isSameType(request)) {
                continue;
            }
            long remaining = requested - processed;
            TransferResult<BigFluidStack> result = mapping.handler.drainTank(
                    mapping.localTank, remaining, action);
            long drained = result == null ? 0L
                    : Math.min(remaining, Math.max(0L, result.getProcessedAmount()));
            processed = saturatedAdd(processed, drained);
        }
        return aggregateResult(request, processed, action);
    }

    /**
     * Selects the first drainable non-empty fluid in flat tank order, then
     * aggregates only that fluid type across the controller.
     */
    @Nonnull
    @Override
    public TransferResult<BigFluidStack> drainRouted(
            long amount, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = Math.max(0L, amount);
        if (requested == 0L) {
            return emptyResult(0L, action);
        }
        for (HandlerTankMapping mapping : tankMappings) {
            BigFluidStack current = mapping.handler.getTankSnapshot(mapping.localTank);
            if (current != null && !current.isEmpty()
                    && mapping.handler.supportsDrain(mapping.localTank)
                    && mapping.handler.supportsFluid(mapping.localTank, current)) {
                return drainRouted(current.withAmount(requested), action);
            }
        }
        return emptyResult(requested, action);
    }

    /**
     * Atomically replaces children and the flattened index. Later mutations of
     * the caller's list cannot produce stale tank mappings.
     *
     * @param newHandlers desired handlers in routing order
     */
    public void setHandlers(@Nonnull List<? extends IBigFluidHandler> newHandlers) {
        Objects.requireNonNull(newHandlers, "newHandlers");
        List<IBigFluidHandler> handlerCopy = new ArrayList<>(newHandlers.size());
        List<HandlerTankMapping> mappings = new ArrayList<>();
        Set<Object> seen = Collections.newSetFromMap(
                new IdentityHashMap<Object, Boolean>());
        for (IBigFluidHandler handler : newHandlers) {
            if (handler == null || !seen.add(identityOf(handler))) {
                continue;
            }
            handlerCopy.add(handler);
            int tanks = Math.max(0, handler.getTankCount());
            for (int tank = 0; tank < tanks; tank++) {
                mappings.add(new HandlerTankMapping(handler, tank));
            }
        }
        handlers = Collections.unmodifiableList(handlerCopy);
        tankMappings = Collections.unmodifiableList(mappings);
    }

    /** @return immutable snapshot of current child handler order */
    @Nonnull
    public List<IBigFluidHandler> getHandlers() {
        return handlers;
    }

    private static Object identityOf(IBigFluidHandler handler) {
        if (handler instanceof StorageIdentityProvider) {
            Object identity = ((StorageIdentityProvider) handler).getStorageIdentity();
            if (identity != null) {
                return identity;
            }
        }
        return handler;
    }

    private long fillCandidates(
            List<HandlerTankMapping> candidates,
            BigFluidStack request,
            StorageAction action,
            long alreadyProcessed) {
        long processed = alreadyProcessed;
        for (HandlerTankMapping mapping : candidates) {
            if (processed >= request.getAmount()) {
                break;
            }
            long remaining = request.getAmount() - processed;
            TransferResult<BigFluidStack> result = mapping.handler.fillTank(
                    mapping.localTank, request.withAmount(remaining), action);
            long filled = result == null ? 0L
                    : Math.min(remaining, Math.max(0L, result.getProcessedAmount()));
            processed = saturatedAdd(processed, filled);
        }
        return processed;
    }

    private HandlerTankMapping mappingAt(int tank) {
        return tank < 0 || tank >= tankMappings.size() ? null : tankMappings.get(tank);
    }

    private static TransferResult<BigFluidStack> emptyResult(
            long requested, StorageAction action) {
        return new TransferResult<>(requested, BigFluidStack.empty(), action);
    }

    private static TransferResult<BigFluidStack> aggregateResult(
            BigFluidStack request, long processed, StorageAction action) {
        return new TransferResult<>(
                request.getAmount(),
                processed == 0L ? BigFluidStack.empty() : request.withAmount(processed),
                action);
    }

    private static long saturatedAdd(long left, long right) {
        return left >= Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static final class HandlerTankMapping {
        private final IBigFluidHandler handler;
        private final int localTank;

        private HandlerTankMapping(IBigFluidHandler handler, int localTank) {
            this.handler = handler;
            this.localTank = localTank;
        }
    }
}
