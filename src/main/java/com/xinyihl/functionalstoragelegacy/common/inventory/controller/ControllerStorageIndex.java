package com.xinyihl.functionalstoragelegacy.common.inventory.controller;

import com.xinyihl.functionalstoragelegacy.api.storage.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

/**
 * Generic event-driven index over a flattened list of child storages.
 *
 * <p>This class owns every resource-independent controller concern: physical
 * identity de-duplication, O(1) global/local mappings, exact and compatibility
 * indexes, empty/occupied classification, child subscriptions and generation
 * filtering. Resource facades only decide candidate eligibility and execute
 * their domain-specific route order.</p>
 */
public final class ControllerStorageIndex<S extends StorageSnapshot<S, K>, K extends StorageKey> implements IStorageHandler<S, K> {

    private final Object mutex = new Object();
    private final S emptySnapshot;
    private final StorageRoutingPolicy<S, K> policy;
    private final StorageChangeDispatcher<S, K> dispatcher = new StorageChangeDispatcher<>();

    private volatile IndexState<S, K> state = IndexState.empty();
    private List<StorageSubscription> childSubscriptions = Collections.emptyList();
    private boolean subscriptionsActive = true;
    private long generation;

    public ControllerStorageIndex(@Nonnull S emptySnapshot, @Nonnull StorageRoutingPolicy<S, K> policy) {
        this.emptySnapshot = Objects.requireNonNull(emptySnapshot, "emptySnapshot");
        this.policy = Objects.requireNonNull(policy, "policy");
        if (emptySnapshot.hasTemplate() || !emptySnapshot.isEmpty()) {
            throw new IllegalArgumentException("emptySnapshot must be unconfigured with amount zero");
        }
    }

    public ControllerStorageIndex(@Nonnull StorageRoutingPolicy<S, K> policy, @Nonnull S emptySnapshot) {
        this(emptySnapshot, policy);
    }

    private static <S extends StorageSnapshot<S, K>, K extends StorageKey> boolean sameHandlerObjects(IndexState<S, K> current, List<ChildSpec<S, K>> requested) {
        if (current.children.size() != requested.size()) {
            return false;
        }
        for (int index = 0; index < requested.size(); index++) {
            if (current.children.get(index).handler != requested.get(index).handler) {
                return false;
            }
        }
        return true;
    }

    private static long amountOf(@Nullable StorageSnapshot<?, ?> snapshot) {
        return snapshot == null || snapshot.isEmpty() ? 0L : Math.max(0L, snapshot.getAmount());
    }

    private static boolean sameTopology(IndexState<?, ?> current, List<? extends ChildSpec<?, ?>> requested) {
        if (current.children.size() != requested.size()) {
            return false;
        }
        for (int index = 0; index < requested.size(); index++) {
            ChildSpec<?, ?> before = current.children.get(index);
            ChildSpec<?, ?> after = requested.get(index);
            if (before.identity != after.identity || before.storageCount != after.storageCount) {
                return false;
            }
        }
        return true;
    }

    private static Object identityOf(IStorageHandler<?, ?> handler) {
        return handler.getStorageIdentity();
    }

    private static boolean validLocal(@Nullable int[] indexes, int localIndex) {
        return indexes != null && localIndex >= 0 && localIndex < indexes.length;
    }

    private static void closeAll(List<StorageSubscription> subscriptions) {
        for (StorageSubscription subscription : subscriptions) {
            if (subscription != null) {
                subscription.close();
            }
        }
    }

    private static <T> void addAll(Set<T> target, @Nullable Set<T> source) {
        if (source != null) {
            target.addAll(source);
        }
    }

    private static TreeSet<Integer> copy(@Nullable Set<Integer> source) {
        return source == null ? new TreeSet<>() : new TreeSet<>(source);
    }

    private static List<Integer> immutableList(Set<Integer> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static Set<Integer> immutableSet(@Nullable Set<Integer> source) {
        return source == null || source.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(new TreeSet<>(source));
    }

    private static <K> void removeFrom(Map<K, NavigableSet<Integer>> map, K key, int index) {
        NavigableSet<Integer> indexes = map.get(key);
        if (indexes == null) {
            return;
        }
        indexes.remove(index);
        if (indexes.isEmpty()) {
            map.remove(key);
        }
    }

    private static <S extends StorageSnapshot<S, K>, K extends StorageKey> List<IndexedStorage<S, K>> views(IndexState<S, K> state, Set<Integer> indexes) {
        List<IndexedStorage<S, K>> result = new ArrayList<>(indexes.size());
        for (Integer index : indexes) {
            Binding<S, K> binding = state.binding(index);
            if (binding != null) {
                result.add(new IndexedStorage<>(binding));
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public int getStorageCount() {
        return state.bindings.size();
    }

    @Nonnull
    @Override
    public S getSnapshot(int index) {
        Binding<S, K> binding = bindingAt(index);
        return binding == null ? emptySnapshot : binding.snapshot;
    }

    @Nonnull
    public S getIndexedSnapshot(int globalIndex) {
        return getSnapshot(globalIndex);
    }

    @Override
    public long getCapacity(int index) {
        Binding<S, K> binding = bindingAt(index);
        return binding == null ? 0L : Math.max(0L, binding.handler.getCapacity(binding.localIndex));
    }

    @Nonnull
    @Override
    public TransferResult<S, K> insert(int index, @Nonnull S request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = amountOf(request);
        if (requested == 0L) {
            return emptyResult(0L, action);
        }
        Binding<S, K> binding = bindingAt(index);
        return binding == null ? emptyResult(requested, action) : binding.handler.insert(binding.localIndex, request, action);
    }

    @Nonnull
    @Override
    public TransferResult<S, K> extract(int index, long amount, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = Math.max(0L, amount);
        if (requested == 0L) {
            return emptyResult(0L, action);
        }
        Binding<S, K> binding = bindingAt(index);
        return binding == null ? emptyResult(requested, action) : binding.handler.extract(binding.localIndex, requested, action);
    }

    /**
     * Replaces child bindings. An active identical identity/order/count topology
     * returns before reading any snapshot, replacing a subscription or emitting
     * an event. Duplicate physical identities retain their first occurrence.
     *
     * @return whether the index was rebuilt
     */
    public boolean setHandlers(@Nonnull List<? extends IStorageHandler<S, K>> requestedHandlers) {
        Objects.requireNonNull(requestedHandlers, "requestedHandlers");
        List<StorageSubscription> subscriptionsToClose;
        StorageChange<S, K> topologyChange = null;
        synchronized (mutex) {
            List<ChildSpec<S, K>> requested = normalizeChildren(requestedHandlers);
            IndexState<S, K> previous = state;
            if (subscriptionsActive && sameTopology(previous, requested)) {
                if (sameHandlerObjects(previous, requested)) {
                    return false;
                }
                // A forwarding facade may be replaced while retaining the
                // same physical identity. Rebind only those representatives;
                // cached snapshots and classification sets remain valid.
                subscriptionsToClose = rebindRepresentatives(previous, requested);
            } else {
                IndexState<S, K> next = buildState(requested);
                topologyChange = topologyChange(previous, next);
                subscriptionsToClose = childSubscriptions;
                final long nextGeneration = ++generation;
                List<StorageSubscription> replacements = new ArrayList<>(requested.size());
                for (ChildSpec<S, K> child : requested) {
                    child.subscriptionGeneration = nextGeneration;
                    final IndexState<S, K> capturedState = next;
                    replacements.add(child.handler.subscribe(change -> onChildChange(capturedState, nextGeneration, child, change)));
                }
                state = next;
                childSubscriptions = Collections.unmodifiableList(replacements);
                subscriptionsActive = true;
                if (!dispatcher.hasSubscribers()) {
                    topologyChange = null;
                }
            }
        }
        closeAll(subscriptionsToClose);
        if (topologyChange != null) {
            onChange(topologyChange);
        }
        return true;
    }

    /**
     * Closes child registrations and invalidates their generation. The method
     * is idempotent; the next setHandlers call performs a fresh indexed bind.
     */
    public void closeSubscriptions() {
        List<StorageSubscription> closing;
        synchronized (mutex) {
            if (!subscriptionsActive) {
                return;
            }
            subscriptionsActive = false;
            generation++;
            closing = childSubscriptions;
            childSubscriptions = Collections.emptyList();
        }
        closeAll(closing);
    }

    public boolean hasActiveSubscriptions() {
        synchronized (mutex) {
            return subscriptionsActive;
        }
    }

    @Nonnull
    public List<IStorageHandler<S, K>> getHandlers() {
        return state.handlers;
    }

    /**
     * amount > 0 only; typed-zero filters are deliberately excluded.
     */
    @Nonnull
    public List<Integer> getOccupiedIndices() {
        synchronized (mutex) {
            return immutableList(state.occupied);
        }
    }

    /**
     * !hasTemplate only; typed-zero filters are deliberately excluded.
     */
    @Nonnull
    public List<Integer> getEmptyIndices() {
        synchronized (mutex) {
            return immutableList(state.empty);
        }
    }

    @Nonnull
    public Set<Integer> getIndicesForKey(@Nullable StorageKey key) {
        synchronized (mutex) {
            return immutableSet(state.exact.get(key));
        }
    }

    @Nonnull
    public Set<Integer> getIndicesForAlias(@Nullable StorageKey alias) {
        synchronized (mutex) {
            return immutableSet(state.aliases.get(alias));
        }
    }

    /**
     * Union for a key that is already known to be exact or an alias.
     */
    @Nonnull
    public Set<Integer> getCandidateIndices(@Nullable StorageKey key) {
        synchronized (mutex) {
            TreeSet<Integer> indexes = new TreeSet<>();
            addAll(indexes, state.exact.get(key));
            addAll(indexes, state.aliases.get(key));
            return Collections.unmodifiableSet(indexes);
        }
    }

    /**
     * Exact plus every policy alias derived from the request.
     */
    @Nonnull
    public Set<Integer> getCandidateIndices(@Nullable S request) {
        if (request == null || !request.hasTemplate()) {
            return Collections.emptySet();
        }
        synchronized (mutex) {
            TreeSet<Integer> indexes = new TreeSet<>();
            addAll(indexes, state.exact.get(exactKey(request)));
            for (StorageKey alias : aliasesFor(request)) {
                addAll(indexes, state.aliases.get(alias));
            }
            return Collections.unmodifiableSet(indexes);
        }
    }

    /**
     * Returns detached ordered candidate membership for one routing operation.
     * The contained binding views remain read-only; later index callbacks do
     * not add or remove entries from these lists.
     */
    @Nonnull
    public CandidateSnapshot<S, K> snapshotCandidates(@Nonnull S request) {
        Objects.requireNonNull(request, "request");
        synchronized (mutex) {
            IndexState<S, K> current = state;
            NavigableSet<Integer> exactIndexes = current.exact.get(exactKey(request));
            TreeSet<Integer> exact = copy(exactIndexes);
            TreeSet<Integer> aliases = new TreeSet<>();
            for (StorageKey alias : aliasesFor(request)) {
                addAll(aliases, current.aliases.get(alias));
            }
            aliases.removeAll(exact);
            return new CandidateSnapshot<>(views(current, exact), views(current, aliases), views(current, current.empty), views(current, current.occupied));
        }
    }

    @Nullable
    public IndexedStorage<S, K> getIndexedStorage(int globalIndex) {
        Binding<S, K> binding = bindingAt(globalIndex);
        return binding == null ? null : new IndexedStorage<>(binding);
    }

    @Nonnull
    public List<IndexedStorage<S, K>> getIndexedStorages() {
        IndexState<S, K> current = state;
        List<IndexedStorage<S, K>> result = new ArrayList<>(current.bindings.size());
        for (Binding<S, K> binding : current.bindings) {
            result.add(new IndexedStorage<>(binding));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * O(1) child/local to global lookup by handler or physical identity.
     */
    public int getGlobalIndex(@Nonnull IStorageHandler<S, K> handler, int localIndex) {
        Objects.requireNonNull(handler, "handler");
        IndexState<S, K> current = state;
        int[] direct = current.reverseByHandler.get(handler);
        if (validLocal(direct, localIndex)) {
            return direct[localIndex];
        }
        int[] physical = current.reverseByIdentity.get(identityOf(handler));
        return validLocal(physical, localIndex) ? physical[localIndex] : -1;
    }

    @Override
    public void onChange(@Nonnull StorageChange<S, K> change) {
        dispatcher.dispatch(Objects.requireNonNull(change, "change"));
    }

    @Nonnull
    @Override
    public StorageSubscription subscribe(@Nonnull Consumer<? super StorageChange<S, K>> listener) {
        return dispatcher.subscribe(listener);
    }

    private void onChildChange(IndexState<S, K> capturedState, long capturedGeneration, ChildSpec<S, K> child, StorageChange<S, K> change) {
        if (change == null) {
            return;
        }
        StorageChange<S, K> translated = null;
        boolean reset = false;
        synchronized (mutex) {
            if (!subscriptionsActive || state != capturedState || child.subscriptionGeneration != capturedGeneration) {
                return;
            }
            if (change.isReset()) {
                rebuildIndexes(capturedState);
                reset = true;
            } else {
                List<StorageChange.Entry<S, K>> entries = new ArrayList<>(change.getEntries().size());
                for (StorageChange.Entry<S, K> entry : change.getEntries()) {
                    int local = entry.getIndex();
                    if (local < 0 || local >= child.bindings.size()) {
                        continue;
                    }
                    Binding<S, K> binding = child.bindings.get(local);
                    updateBinding(capturedState, binding, entry.getAfter());
                    entries.add(new StorageChange.Entry<>(binding.globalIndex, entry.getBefore(), entry.getAfter()));
                }
                if (!entries.isEmpty()) {
                    translated = StorageChange.delta(entries);
                }
            }
        }
        if (reset) {
            onChange(StorageChange.reset());
        } else if (translated != null) {
            onChange(translated);
        }
    }

    private void updateBinding(IndexState<S, K> target, Binding<S, K> binding, S after) {
        S before = binding.snapshot;
        boolean typeChanged = before.hasTemplate() != after.hasTemplate() || !Objects.equals(before.getKey(), after.getKey());
        if (typeChanged) {
            removeIndexes(target, binding);
            binding.snapshot = after;
            addIndexes(target, binding);
            return;
        }

        // Same-key amount changes never touch exact/alias/empty membership.
        // Only occupied membership changes when crossing zero.
        boolean wasOccupied = before.getAmount() > 0L;
        boolean isOccupied = after.getAmount() > 0L;
        binding.snapshot = after;
        if (wasOccupied != isOccupied) {
            if (isOccupied) {
                target.occupied.add(binding.globalIndex);
            } else {
                target.occupied.remove(binding.globalIndex);
            }
        }
    }

    private IndexState<S, K> buildState(List<ChildSpec<S, K>> children) {
        IndexState<S, K> result = new IndexState<>();
        int global = 0;
        for (ChildSpec<S, K> child : children) {
            List<Binding<S, K>> localBindings = new ArrayList<>(child.storageCount);
            int[] reverse = new int[child.storageCount];
            for (int local = 0; local < child.storageCount; local++) {
                Binding<S, K> binding = new Binding<>(global++, child.handler, local, safeSnapshot(child.handler, local));
                localBindings.add(binding);
                reverse[local] = binding.globalIndex;
                result.bindings.add(binding);
                addIndexes(result, binding);
            }
            child.bindings = Collections.unmodifiableList(localBindings);
            result.children.add(child);
            result.handlers.add(child.handler);
            result.reverseByHandler.put(child.handler, reverse);
            result.reverseByIdentity.put(child.identity, reverse);
        }
        return result.freeze();
    }

    private void rebuildIndexes(IndexState<S, K> target) {
        target.exact.clear();
        target.aliases.clear();
        target.empty.clear();
        target.occupied.clear();
        for (Binding<S, K> binding : target.bindings) {
            binding.snapshot = safeSnapshot(binding.handler, binding.localIndex);
            addIndexes(target, binding);
        }
    }

    private void addIndexes(IndexState<S, K> target, Binding<S, K> binding) {
        S snapshot = binding.snapshot;
        binding.key = exactKey(snapshot);
        binding.aliases = aliasSet(snapshot);
        if (!snapshot.hasTemplate()) {
            target.empty.add(binding.globalIndex);
        } else {
            if (binding.key != null) {
                target.exact.computeIfAbsent(binding.key, ignored -> new TreeSet<>()).add(binding.globalIndex);
            }
            for (StorageKey alias : binding.aliases) {
                target.aliases.computeIfAbsent(alias, ignored -> new TreeSet<>()).add(binding.globalIndex);
            }
        }
        if (snapshot.getAmount() > 0L) {
            target.occupied.add(binding.globalIndex);
        }
    }

    private void removeIndexes(IndexState<S, K> target, Binding<S, K> binding) {
        target.empty.remove(binding.globalIndex);
        target.occupied.remove(binding.globalIndex);
        if (binding.key != null) {
            removeFrom(target.exact, binding.key, binding.globalIndex);
        }
        for (StorageKey alias : binding.aliases) {
            removeFrom(target.aliases, alias, binding.globalIndex);
        }
        binding.key = null;
        binding.aliases = Collections.emptySet();
    }

    private K exactKey(S snapshot) {
        if (!snapshot.hasTemplate()) {
            return null;
        }
        K key = policy.getExactKey(snapshot);
        return key == null ? snapshot.getKey() : key;
    }

    private Set<StorageKey> aliasSet(S snapshot) {
        if (!snapshot.hasTemplate()) {
            return Collections.emptySet();
        }
        LinkedHashSet<StorageKey> result = new LinkedHashSet<>();
        Iterable<? extends StorageKey> supplied = policy.getCompatibleAliases(snapshot);
        for (StorageKey alias : supplied) {
            if (alias != null) {
                result.add(alias);
            }
        }
        return result.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(result);
    }

    private List<StorageKey> aliasesFor(S snapshot) {
        return new ArrayList<>(aliasSet(snapshot));
    }

    private List<ChildSpec<S, K>> normalizeChildren(List<? extends IStorageHandler<S, K>> requested) {
        List<ChildSpec<S, K>> result = new ArrayList<>(requested.size());
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        for (IStorageHandler<S, K> handler : requested) {
            if (handler == null) {
                continue;
            }
            Object identity = identityOf(handler);
            if (seen.put(identity, Boolean.TRUE) != null) {
                continue;
            }
            result.add(new ChildSpec<>(handler, identity, Math.max(0, handler.getStorageCount())));
        }
        return result;
    }

    /**
     * Rebinds only representative facade instances whose physical identity is
     * unchanged. No child snapshots, classification policy calls or aggregate
     * events are produced by this path.
     */
    private List<StorageSubscription> rebindRepresentatives(IndexState<S, K> current, List<ChildSpec<S, K>> requested) {
        List<StorageSubscription> oldSubscriptions = new ArrayList<>();
        List<StorageSubscription> replacements = new ArrayList<>(childSubscriptions);
        List<IStorageHandler<S, K>> handlers = new ArrayList<>(current.handlers);
        for (int index = 0; index < requested.size(); index++) {
            ChildSpec<S, K> existing = current.children.get(index);
            ChildSpec<S, K> incoming = requested.get(index);
            if (existing.handler == incoming.handler) {
                continue;
            }

            IStorageHandler<S, K> oldHandler = existing.handler;
            IStorageHandler<S, K> newHandler = incoming.handler;
            existing.handler = newHandler;
            for (Binding<S, K> binding : existing.bindings) {
                binding.handler = newHandler;
            }
            int[] reverse = current.reverseByIdentity.get(existing.identity);
            current.reverseByHandler.remove(oldHandler);
            current.reverseByHandler.put(newHandler, reverse);
            handlers.set(index, newHandler);

            StorageSubscription oldSubscription = replacements.get(index);
            if (oldSubscription != null) {
                oldSubscriptions.add(oldSubscription);
            }
            final IndexState<S, K> capturedState = current;
            final ChildSpec<S, K> capturedChild = existing;
            final long capturedGeneration = ++generation;
            existing.subscriptionGeneration = capturedGeneration;
            replacements.set(index, newHandler.subscribe(change -> onChildChange(capturedState, capturedGeneration, capturedChild, change)));
        }
        current.handlers = Collections.unmodifiableList(handlers);
        childSubscriptions = Collections.unmodifiableList(replacements);
        return oldSubscriptions;
    }

    private StorageChange<S, K> topologyChange(IndexState<S, K> previous, IndexState<S, K> next) {
        int count = Math.max(previous.bindings.size(), next.bindings.size());
        List<StorageChange.Entry<S, K>> entries = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Binding<S, K> oldBinding = previous.binding(index);
            Binding<S, K> newBinding = next.binding(index);
            S before = oldBinding == null ? emptySnapshot : oldBinding.snapshot;
            S after = newBinding == null ? emptySnapshot : newBinding.snapshot;
            if (before.getAmount() != after.getAmount() || !Objects.equals(before.getKey(), after.getKey())) {
                entries.add(new StorageChange.Entry<>(index, before, after));
            }
        }
        return entries.isEmpty() ? StorageChange.reset() : StorageChange.delta(entries);
    }

    private Binding<S, K> bindingAt(int index) {
        IndexState<S, K> current = state;
        return current.binding(index);
    }

    private S safeSnapshot(IStorageHandler<S, K> handler, int localIndex) {
        return handler.getSnapshot(localIndex);
    }

    private TransferResult<S, K> emptyResult(long requested, StorageAction action) {
        return new TransferResult<>(Math.max(0L, requested), emptySnapshot, action);
    }

    /**
     * Immutable candidate-list membership captured for one routing operation.
     */
    public static final class CandidateSnapshot<S extends StorageSnapshot<S, K>, K extends StorageKey> {
        private final List<IndexedStorage<S, K>> exact;
        private final List<IndexedStorage<S, K>> aliases;
        private final List<IndexedStorage<S, K>> empty;
        private final List<IndexedStorage<S, K>> occupied;

        private CandidateSnapshot(List<IndexedStorage<S, K>> exact, List<IndexedStorage<S, K>> aliases, List<IndexedStorage<S, K>> empty, List<IndexedStorage<S, K>> occupied) {
            this.exact = exact;
            this.aliases = aliases;
            this.empty = empty;
            this.occupied = occupied;
        }

        @Nonnull
        public List<IndexedStorage<S, K>> getExact() {
            return exact;
        }

        @Nonnull
        public List<IndexedStorage<S, K>> getAliases() {
            return aliases;
        }

        @Nonnull
        public List<IndexedStorage<S, K>> getEmpty() {
            return empty;
        }

        @Nonnull
        public List<IndexedStorage<S, K>> getOccupied() {
            return occupied;
        }
    }

    /**
     * Read-only view of one O(1) global/local binding.
     */
    public static final class IndexedStorage<S extends StorageSnapshot<S, K>, K extends StorageKey> {
        private final Binding<S, K> binding;

        private IndexedStorage(Binding<S, K> binding) {
            this.binding = binding;
        }

        public int getGlobalIndex() {
            return binding.globalIndex;
        }

        public int getLocalIndex() {
            return binding.localIndex;
        }

        @Nonnull
        public IStorageHandler<S, K> getHandler() {
            return binding.handler;
        }

        @Nonnull
        public S getSnapshot() {
            return binding.snapshot;
        }

        @Nullable
        public K getKey() {
            return binding.key;
        }
    }

    private static final class ChildSpec<S extends StorageSnapshot<S, K>, K extends StorageKey> {
        private final Object identity;
        private final int storageCount;
        private IStorageHandler<S, K> handler;
        private List<Binding<S, K>> bindings = Collections.emptyList();
        private long subscriptionGeneration;

        private ChildSpec(IStorageHandler<S, K> handler, Object identity, int storageCount) {
            this.handler = handler;
            this.identity = identity;
            this.storageCount = storageCount;
        }
    }

    private static final class Binding<S extends StorageSnapshot<S, K>, K extends StorageKey> {
        private final int globalIndex;
        private final int localIndex;
        private volatile IStorageHandler<S, K> handler;
        private volatile S snapshot;
        private K key;
        private Set<StorageKey> aliases = Collections.emptySet();

        private Binding(int globalIndex, IStorageHandler<S, K> handler, int localIndex, S snapshot) {
            this.globalIndex = globalIndex;
            this.handler = handler;
            this.localIndex = localIndex;
            this.snapshot = snapshot;
        }
    }

    private static final class IndexState<S extends StorageSnapshot<S, K>, K extends StorageKey> {
        private final IdentityHashMap<IStorageHandler<S, K>, int[]> reverseByHandler = new IdentityHashMap<>();
        private final IdentityHashMap<Object, int[]> reverseByIdentity = new IdentityHashMap<>();
        private final Map<K, NavigableSet<Integer>> exact = new HashMap<>();
        private final Map<StorageKey, NavigableSet<Integer>> aliases = new HashMap<>();
        private final NavigableSet<Integer> empty = new TreeSet<>();
        private final NavigableSet<Integer> occupied = new TreeSet<>();
        private List<Binding<S, K>> bindings = new ArrayList<>();
        private List<ChildSpec<S, K>> children = new ArrayList<>();
        private List<IStorageHandler<S, K>> handlers = new ArrayList<>();

        private static <S extends StorageSnapshot<S, K>, K extends StorageKey> IndexState<S, K> empty() {
            return new IndexState<S, K>().freeze();
        }

        private IndexState<S, K> freeze() {
            bindings = Collections.unmodifiableList(bindings);
            children = Collections.unmodifiableList(children);
            handlers = Collections.unmodifiableList(handlers);
            return this;
        }

        @Nullable
        private Binding<S, K> binding(int index) {
            return index < 0 || index >= bindings.size() ? null : bindings.get(index);
        }
    }
}
