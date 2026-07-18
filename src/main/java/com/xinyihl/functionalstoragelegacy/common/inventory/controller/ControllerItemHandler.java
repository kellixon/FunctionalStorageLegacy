package com.xinyihl.functionalstoragelegacy.common.inventory.controller;

import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.IStorageHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.ItemStorageKey;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageChange;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageKey;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageRoutingPolicy;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageSubscription;
import com.xinyihl.functionalstoragelegacy.api.storage.TransferResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Item-specific routing facade over the generic controller index. */
public final class ControllerItemHandler implements IBigItemHandler {

    private final StorageRoutingPolicy<BigItemStack, ItemStorageKey> policy;
    private final ControllerStorageIndex<BigItemStack, ItemStorageKey> index;

    public ControllerItemHandler() {
        this(new ItemStorageRoutingPolicy());
    }

    public ControllerItemHandler(
            @Nonnull StorageRoutingPolicy<BigItemStack, ItemStorageKey> policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.index = new ControllerStorageIndex<>(BigItemStack.empty(), policy);
    }

    @Override
    public int getStorageCount() {
        return index.getStorageCount();
    }

    @Nonnull
    @Override
    public BigItemStack getSnapshot(int slot) {
        return index.getSnapshot(slot);
    }

    @Nonnull
    public BigItemStack getIndexedSnapshot(int globalIndex) {
        return index.getIndexedSnapshot(globalIndex);
    }

    @Override
    public long getCapacity(int slot) {
        return index.getCapacity(slot);
    }

    @Nonnull
    @Override
    public TransferResult<BigItemStack, ItemStorageKey> insert(
            int slot, @Nonnull BigItemStack request, @Nonnull StorageAction action) {
        return index.insert(slot, request, action);
    }

    @Nonnull
    @Override
    public TransferResult<BigItemStack, ItemStorageKey> extract(
            int slot, long amount, @Nonnull StorageAction action) {
        return index.extract(slot, amount, action);
    }

    @Nonnull
    @Override
    public TransferResult<BigItemStack, ItemStorageKey> insertRouted(
            @Nonnull BigItemStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = amountOf(request);
        if (requested == 0L) {
            return emptyResult(0L, action);
        }

        ControllerStorageIndex.CandidateSnapshot<BigItemStack, ItemStorageKey> snapshot =
                index.snapshotCandidates(request);
        List<Candidate> candidates = new ArrayList<>();
        for (ControllerStorageIndex.IndexedStorage<BigItemStack, ItemStorageKey> candidate
                : snapshot.getExact()) {
            addConfiguredCandidate(candidates, candidate, request, false);
        }

        BigItemStack probe = request.withAmount(1L);
        for (ControllerStorageIndex.IndexedStorage<BigItemStack, ItemStorageKey> candidate
                : snapshot.getAliases()) {
            IBigItemHandler child = itemHandler(candidate);
            TransferResult<BigItemStack, ItemStorageKey> simulated = child.insert(
                    candidate.getLocalIndex(), probe, StorageAction.SIMULATE);
            if (simulated != null && simulated.getProcessedAmount() > 0L) {
                addConfiguredCandidate(candidates, candidate, request, true);
            }
        }

        for (ControllerStorageIndex.IndexedStorage<BigItemStack, ItemStorageKey> candidate
                : snapshot.getEmpty()) {
            IBigItemHandler child = itemHandler(candidate);
            if (!child.isLocked() && policy.isEmptySlotEligible(
                    child, candidate.getLocalIndex(), request)) {
                candidates.add(new Candidate(candidate, 2));
            }
        }
        Collections.sort(candidates, Comparator
                .comparingInt((Candidate candidate) -> candidate.priority)
                .thenComparingInt(candidate -> candidate.storage.getGlobalIndex()));

        long processed = 0L;
        // Candidate membership is detached from the live generic index.
        for (Candidate candidate : new ArrayList<>(candidates)) {
            if (processed >= requested) {
                break;
            }
            long remaining = requested - processed;
            TransferResult<BigItemStack, ItemStorageKey> inserted = itemHandler(
                    candidate.storage).insert(candidate.storage.getLocalIndex(),
                    request.withAmount(remaining), action);
            processed = saturatedAdd(processed, bounded(inserted, remaining));
        }
        return aggregate(request, processed, action);
    }

    @Nonnull
    @Override
    public TransferResult<BigItemStack, ItemStorageKey> extractRouted(
            @Nonnull BigItemStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = amountOf(request);
        if (requested == 0L) {
            return emptyResult(0L, action);
        }
        List<ControllerStorageIndex.IndexedStorage<BigItemStack, ItemStorageKey>> candidates =
                index.snapshotCandidates(request).getExact();
        long processed = 0L;
        for (ControllerStorageIndex.IndexedStorage<BigItemStack, ItemStorageKey> candidate
                : candidates) {
            if (processed >= requested) {
                break;
            }
            BigItemStack current = candidate.getSnapshot();
            if (current.getAmount() <= 0L || !current.isSameType(request)) {
                continue;
            }
            long remaining = requested - processed;
            TransferResult<BigItemStack, ItemStorageKey> extracted = itemHandler(candidate).extract(
                    candidate.getLocalIndex(), remaining, action);
            processed = saturatedAdd(processed, bounded(extracted, remaining));
        }
        return aggregate(request, processed, action);
    }

    public void setHandlers(@Nonnull List<? extends IBigItemHandler> handlers) {
        index.setHandlers(handlers);
    }

    public void closeSubscriptions() {
        index.closeSubscriptions();
    }

    @Nonnull
    public List<IBigItemHandler> getHandlers() {
        List<IBigItemHandler> result = new ArrayList<>();
        for (IStorageHandler<BigItemStack, ItemStorageKey> handler : index.getHandlers()) {
            result.add((IBigItemHandler) handler);
        }
        return Collections.unmodifiableList(result);
    }

    @Nonnull
    public List<Integer> getOccupiedIndices() {
        return index.getOccupiedIndices();
    }

    @Nonnull
    public List<Integer> getOccupiedSlots() {
        return getOccupiedIndices();
    }

    @Nonnull
    public List<Integer> getEmptyIndices() {
        return index.getEmptyIndices();
    }

    @Nonnull
    public List<Integer> getEmptySlots() {
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
    public Set<Integer> getCandidateIndices(@Nullable BigItemStack request) {
        return index.getCandidateIndices(request);
    }

    @Nullable
    public ControllerStorageIndex.IndexedStorage<BigItemStack, ItemStorageKey>
    getIndexedSlot(int globalIndex) {
        return index.getIndexedStorage(globalIndex);
    }

    @Nonnull
    public List<ControllerStorageIndex.IndexedStorage<BigItemStack, ItemStorageKey>>
    getIndexedSlots() {
        return index.getIndexedStorages();
    }

    public int getGlobalIndex(@Nonnull IBigItemHandler handler, int localIndex) {
        return index.getGlobalIndex(handler, localIndex);
    }

    @Nonnull
    public ControllerStorageIndex<BigItemStack, ItemStorageKey> getIndex() {
        return index;
    }

    @Override
    public void onChange(@Nonnull StorageChange<BigItemStack, ItemStorageKey> change) {
        index.onChange(change);
    }

    @Nonnull
    @Override
    public StorageSubscription subscribe(
            @Nonnull Consumer<? super StorageChange<BigItemStack, ItemStorageKey>> listener) {
        return index.subscribe(listener);
    }

    private void addConfiguredCandidate(
            List<Candidate> result,
            ControllerStorageIndex.IndexedStorage<BigItemStack, ItemStorageKey> candidate,
            BigItemStack request,
            boolean aliasConfirmed) {
        IBigItemHandler child = itemHandler(candidate);
        int priority = policy.getCandidatePriority(
                child, candidate.getLocalIndex(), candidate.getSnapshot(), request);
        if (priority < 0) {
            if (!aliasConfirmed) {
                return;
            }
            priority = child.isLocked() ? 0 : 1;
        }
        result.add(new Candidate(candidate, priority));
    }

    private static IBigItemHandler itemHandler(
            ControllerStorageIndex.IndexedStorage<BigItemStack, ItemStorageKey> storage) {
        return (IBigItemHandler) storage.getHandler();
    }

    private static long amountOf(@Nullable BigItemStack request) {
        return request == null || request.isEmpty() ? 0L : request.getAmount();
    }

    private static long bounded(
            @Nullable TransferResult<BigItemStack, ItemStorageKey> result, long remaining) {
        return result == null ? 0L
                : Math.min(remaining, Math.max(0L, result.getProcessedAmount()));
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static TransferResult<BigItemStack, ItemStorageKey> aggregate(
            BigItemStack request, long processed, StorageAction action) {
        long amount = Math.min(request.getAmount(), Math.max(0L, processed));
        return new TransferResult<>(request.getAmount(), amount == 0L
                ? BigItemStack.empty() : request.withAmount(amount), action);
    }

    private static TransferResult<BigItemStack, ItemStorageKey> emptyResult(
            long requested, StorageAction action) {
        return new TransferResult<>(requested, BigItemStack.empty(), action);
    }

    private static final class Candidate {
        private final ControllerStorageIndex.IndexedStorage<BigItemStack, ItemStorageKey> storage;
        private final int priority;

        private Candidate(
                ControllerStorageIndex.IndexedStorage<BigItemStack, ItemStorageKey> storage,
                int priority) {
            this.storage = storage;
            this.priority = priority;
        }
    }
}
