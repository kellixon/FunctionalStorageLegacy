package com.xinyihl.functionalstoragelegacy.common.inventory.controller;

import com.xinyihl.functionalstoragelegacy.api.storage.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

/**
 * Fluid-specific routing facade over the generic controller index.
 */
public final class ControllerFluidHandler implements IBigFluidHandler {

    private final StorageRoutingPolicy<BigFluidStack, FluidStorageKey> policy;
    private final ControllerStorageIndex<BigFluidStack, FluidStorageKey> index;

    public ControllerFluidHandler() {
        this(new FluidStorageRoutingPolicy());
    }

    public ControllerFluidHandler(@Nonnull StorageRoutingPolicy<BigFluidStack, FluidStorageKey> policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.index = new ControllerStorageIndex<>(BigFluidStack.empty(), policy);
    }

    private static IBigFluidHandler fluidHandler(ControllerStorageIndex.IndexedStorage<BigFluidStack, FluidStorageKey> storage) {
        return (IBigFluidHandler) storage.getHandler();
    }

    private static long amountOf(@Nullable BigFluidStack request) {
        return request == null || request.isEmpty() ? 0L : request.getAmount();
    }

    private static long bounded(@Nullable TransferResult<BigFluidStack, FluidStorageKey> result, long remaining) {
        return result == null ? 0L : Math.min(remaining, Math.max(0L, result.getProcessedAmount()));
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static TransferResult<BigFluidStack, FluidStorageKey> aggregate(BigFluidStack request, long processed, StorageAction action) {
        long amount = Math.min(request.getAmount(), Math.max(0L, processed));
        return new TransferResult<>(request.getAmount(), amount == 0L ? BigFluidStack.empty() : request.withAmount(amount), action);
    }

    private static TransferResult<BigFluidStack, FluidStorageKey> emptyResult(long requested, StorageAction action) {
        return new TransferResult<>(requested, BigFluidStack.empty(), action);
    }

    @Override
    public int getStorageCount() {
        return index.getStorageCount();
    }

    @Nonnull
    @Override
    public BigFluidStack getSnapshot(int tank) {
        return index.getSnapshot(tank);
    }

    @Nonnull
    public BigFluidStack getIndexedSnapshot(int globalIndex) {
        return index.getIndexedSnapshot(globalIndex);
    }

    @Override
    public long getCapacity(int tank) {
        return index.getCapacity(tank);
    }

    @Override
    public boolean supportsFill(int tank) {
        ControllerStorageIndex.IndexedStorage<BigFluidStack, FluidStorageKey> storage = index.getIndexedStorage(tank);
        return storage != null && fluidHandler(storage).supportsFill(storage.getLocalIndex());
    }

    @Override
    public boolean supportsDrain(int tank) {
        ControllerStorageIndex.IndexedStorage<BigFluidStack, FluidStorageKey> storage = index.getIndexedStorage(tank);
        return storage != null && fluidHandler(storage).supportsDrain(storage.getLocalIndex());
    }

    @Override
    public boolean supportsFluid(int tank, @Nonnull BigFluidStack fluid) {
        ControllerStorageIndex.IndexedStorage<BigFluidStack, FluidStorageKey> storage = index.getIndexedStorage(tank);
        return storage != null && fluidHandler(storage).supportsFluid(storage.getLocalIndex(), fluid);
    }

    @Nonnull
    @Override
    public TransferResult<BigFluidStack, FluidStorageKey> insert(int tank, @Nonnull BigFluidStack request, @Nonnull StorageAction action) {
        return index.insert(tank, request, action);
    }

    @Nonnull
    @Override
    public TransferResult<BigFluidStack, FluidStorageKey> extract(int tank, long amount, @Nonnull StorageAction action) {
        return index.extract(tank, amount, action);
    }

    @Nonnull
    @Override
    public TransferResult<BigFluidStack, FluidStorageKey> fillRouted(@Nonnull BigFluidStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = amountOf(request);
        if (requested == 0L) {
            return emptyResult(0L, action);
        }
        ControllerStorageIndex.CandidateSnapshot<BigFluidStack, FluidStorageKey> snapshot = index.snapshotCandidates(request);
        List<Candidate> candidates = new ArrayList<>();
        addConfigured(candidates, snapshot.getExact(), request);
        addConfigured(candidates, snapshot.getAliases(), request);
        for (ControllerStorageIndex.IndexedStorage<BigFluidStack, FluidStorageKey> candidate : snapshot.getEmpty()) {
            IBigFluidHandler child = fluidHandler(candidate);
            int local = candidate.getLocalIndex();
            if (child.supportsFill(local) && child.supportsFluid(local, request) && policy.isEmptySlotEligible(child, local, request)) {
                candidates.add(new Candidate(candidate, 2));
            }
        }
        candidates.sort(Comparator.comparingInt((Candidate candidate) -> candidate.priority).thenComparingInt(candidate -> candidate.storage.getGlobalIndex()));

        long processed = 0L;
        for (Candidate candidate : new ArrayList<>(candidates)) {
            if (processed >= requested) {
                break;
            }
            long remaining = requested - processed;
            TransferResult<BigFluidStack, FluidStorageKey> filled = fluidHandler(candidate.storage).insert(candidate.storage.getLocalIndex(), request.withAmount(remaining), action);
            processed = saturatedAdd(processed, bounded(filled, remaining));
        }
        return aggregate(request, processed, action);
    }

    @Nonnull
    @Override
    public TransferResult<BigFluidStack, FluidStorageKey> drainRouted(@Nonnull BigFluidStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = amountOf(request);
        if (requested == 0L) {
            return emptyResult(0L, action);
        }
        List<ControllerStorageIndex.IndexedStorage<BigFluidStack, FluidStorageKey>> candidates = index.snapshotCandidates(request).getExact();
        long processed = 0L;
        for (ControllerStorageIndex.IndexedStorage<BigFluidStack, FluidStorageKey> candidate : candidates) {
            if (processed >= requested) {
                break;
            }
            BigFluidStack current = candidate.getSnapshot();
            IBigFluidHandler child = fluidHandler(candidate);
            int local = candidate.getLocalIndex();
            if (current.getAmount() <= 0L || !current.isSameType(request) || !child.supportsDrain(local) || !child.supportsFluid(local, request)) {
                continue;
            }
            long remaining = requested - processed;
            TransferResult<BigFluidStack, FluidStorageKey> drained = child.extract(local, remaining, action);
            processed = saturatedAdd(processed, bounded(drained, remaining));
        }
        return aggregate(request, processed, action);
    }

    @Nonnull
    @Override
    public TransferResult<BigFluidStack, FluidStorageKey> drainRouted(long amount, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = Math.max(0L, amount);
        if (requested == 0L) {
            return emptyResult(0L, action);
        }
        // occupied is already an ordered amount>0 snapshot; typed-zero filters
        // never enter this selection pass.
        List<Integer> occupied = index.getOccupiedIndices();
        for (Integer global : occupied) {
            ControllerStorageIndex.IndexedStorage<BigFluidStack, FluidStorageKey> candidate = index.getIndexedStorage(global);
            if (candidate == null) {
                continue;
            }
            BigFluidStack current = candidate.getSnapshot();
            IBigFluidHandler child = fluidHandler(candidate);
            int local = candidate.getLocalIndex();
            if (current.getAmount() > 0L && child.supportsDrain(local) && child.supportsFluid(local, current)) {
                return drainRouted(current.withAmount(requested), action);
            }
        }
        return emptyResult(requested, action);
    }

    public void closeSubscriptions() {
        index.closeSubscriptions();
    }

    @Nonnull
    public List<IBigFluidHandler> getHandlers() {
        List<IBigFluidHandler> result = new ArrayList<>();
        for (IStorageHandler<BigFluidStack, FluidStorageKey> handler : index.getHandlers()) {
            result.add((IBigFluidHandler) handler);
        }
        return Collections.unmodifiableList(result);
    }

    public void setHandlers(@Nonnull List<? extends IBigFluidHandler> handlers) {
        index.setHandlers(handlers);
    }

    @Nonnull
    public List<Integer> getOccupiedIndices() {
        return index.getOccupiedIndices();
    }

    @Nonnull
    public List<Integer> getOccupiedTanks() {
        return getOccupiedIndices();
    }

    @Nonnull
    public List<Integer> getEmptyIndices() {
        return index.getEmptyIndices();
    }

    @Nonnull
    public List<Integer> getEmptyTanks() {
        return getEmptyIndices();
    }

    @Nonnull
    public Set<Integer> getIndicesForKey(@Nullable StorageKey key) {
        return index.getIndicesForKey(key);
    }

    @Nonnull
    public Set<Integer> getExactIndices(@Nullable StorageKey key) {
        return getIndicesForKey(key);
    }

    @Nonnull
    public Set<Integer> getIndicesForAlias(@Nullable StorageKey alias) {
        return index.getIndicesForAlias(alias);
    }

    @Nonnull
    public Set<Integer> getAliasIndices(@Nullable StorageKey alias) {
        return getIndicesForAlias(alias);
    }

    @Nonnull
    public Set<Integer> getCandidateIndices(@Nullable StorageKey key) {
        return index.getCandidateIndices(key);
    }

    @Nonnull
    public Set<Integer> getCandidateIndices(@Nullable BigFluidStack request) {
        return index.getCandidateIndices(request);
    }

    @Nullable
    public ControllerStorageIndex.IndexedStorage<BigFluidStack, FluidStorageKey> getIndexedTank(int globalIndex) {
        return index.getIndexedStorage(globalIndex);
    }

    @Nonnull
    public List<ControllerStorageIndex.IndexedStorage<BigFluidStack, FluidStorageKey>> getIndexedTanks() {
        return index.getIndexedStorages();
    }

    public int getGlobalIndex(@Nonnull IBigFluidHandler handler, int localIndex) {
        return index.getGlobalIndex(handler, localIndex);
    }

    @Nonnull
    public ControllerStorageIndex<BigFluidStack, FluidStorageKey> getIndex() {
        return index;
    }

    @Override
    public void onChange(@Nonnull StorageChange<BigFluidStack, FluidStorageKey> change) {
        index.onChange(change);
    }

    @Nonnull
    @Override
    public StorageSubscription subscribe(@Nonnull Consumer<? super StorageChange<BigFluidStack, FluidStorageKey>> listener) {
        return index.subscribe(listener);
    }

    private void addConfigured(List<Candidate> result, List<ControllerStorageIndex.IndexedStorage<BigFluidStack, FluidStorageKey>> supplied, BigFluidStack request) {
        for (ControllerStorageIndex.IndexedStorage<BigFluidStack, FluidStorageKey> candidate : supplied) {
            IBigFluidHandler child = fluidHandler(candidate);
            int local = candidate.getLocalIndex();
            if (!child.supportsFill(local) || !child.supportsFluid(local, request)) {
                continue;
            }
            int priority = policy.getCandidatePriority(child, local, candidate.getSnapshot(), request);
            if (priority >= 0) {
                result.add(new Candidate(candidate, priority));
            }
        }
    }

    private static final class Candidate {
        private final ControllerStorageIndex.IndexedStorage<BigFluidStack, FluidStorageKey> storage;
        private final int priority;

        private Candidate(ControllerStorageIndex.IndexedStorage<BigFluidStack, FluidStorageKey> storage, int priority) {
            this.storage = storage;
            this.priority = priority;
        }
    }
}
