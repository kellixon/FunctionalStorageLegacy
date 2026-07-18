package com.xinyihl.functionalstoragelegacy.common.inventory.controller;

import com.xinyihl.functionalstoragelegacy.api.storage.IStorageHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageChange;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageChangeDispatcher;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageKey;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageRoutingPolicy;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageSnapshot;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageSubscription;
import com.xinyihl.functionalstoragelegacy.api.storage.TransferResult;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Verifies that the controller index has no item/fluid runtime dependency. */
public class ControllerStorageIndexTest {

    @Test
    public void typedZeroAndAmountCrossingsUpdateOnlyOccupiedMembership() {
        TestPolicy policy = new TestPolicy();
        TestKey alpha = new TestKey("alpha", "metal");
        TestKey beta = new TestKey("beta", "gem");
        TestHandler child = new TestHandler(new TestSnapshot(alpha, 0L));
        ControllerStorageIndex<TestSnapshot, TestKey> index = index(policy);
        index.setHandlers(Collections.singletonList(child));

        assertEquals(Collections.singleton(0), index.getIndicesForKey(alpha));
        assertTrue(index.getEmptyIndices().isEmpty());
        assertTrue(index.getOccupiedIndices().isEmpty());
        int aliasCalls = policy.aliasCalls;

        child.replace(0, new TestSnapshot(alpha, 5L));
        assertEquals(Collections.singletonList(0), index.getOccupiedIndices());
        assertTrue(index.getEmptyIndices().isEmpty());
        assertEquals(aliasCalls, policy.aliasCalls);

        child.replace(0, new TestSnapshot(alpha, 0L));
        assertTrue(index.getOccupiedIndices().isEmpty());
        assertTrue(index.getEmptyIndices().isEmpty());
        assertEquals(Collections.singleton(0), index.getIndicesForKey(alpha));
        assertEquals(aliasCalls, policy.aliasCalls);

        child.replace(0, new TestSnapshot(beta, 2L));
        assertTrue(index.getIndicesForKey(alpha).isEmpty());
        assertEquals(Collections.singleton(0), index.getIndicesForKey(beta));
        assertEquals(Collections.singletonList(0), index.getOccupiedIndices());
        assertTrue(policy.aliasCalls > aliasCalls);

        child.replace(0, TestSnapshot.empty());
        assertEquals(Collections.singletonList(0), index.getEmptyIndices());
        assertTrue(index.getOccupiedIndices().isEmpty());
        assertTrue(index.getIndicesForKey(beta).isEmpty());
    }

    @Test
    public void stableSetHandlersDoesNotSnapshotResubscribeOrPublish() {
        TestHandler child = new TestHandler(
                new TestSnapshot(new TestKey("alpha", "metal"), 1L),
                TestSnapshot.empty());
        ControllerStorageIndex<TestSnapshot, TestKey> index = index(new TestPolicy());
        List<StorageChange<TestSnapshot, TestKey>> changes = new ArrayList<>();
        index.subscribe(changes::add);
        index.setHandlers(Arrays.asList(child, child));
        changes.clear();
        int reads = child.snapshotReads;
        int subscriptions = child.subscribeCalls;

        assertFalse(index.setHandlers(Arrays.asList(child, child)));

        assertEquals(reads, child.snapshotReads);
        assertEquals(subscriptions, child.subscribeCalls);
        assertTrue(changes.isEmpty());
        assertEquals(1, index.getHandlers().size());
        assertEquals(2, index.getStorageCount());
        assertEquals(1, index.getGlobalIndex(child, 1));
    }

    @Test
    public void reentrantChildMutationKeepsCandidateMembershipSnapshotStable() {
        TestKey alpha = new TestKey("alpha", "metal");
        TestKey beta = new TestKey("beta", "gem");
        TestHandler child = new TestHandler(
                new TestSnapshot(alpha, 1L), TestSnapshot.empty());
        ControllerStorageIndex<TestSnapshot, TestKey> index = index(new TestPolicy());
        index.setHandlers(Collections.singletonList(child));
        ControllerStorageIndex.CandidateSnapshot<TestSnapshot, TestKey> candidates =
                index.snapshotCandidates(new TestSnapshot(alpha, 1L));

        final boolean[] reentered = {false};
        index.subscribe(change -> {
            if (!reentered[0]) {
                reentered[0] = true;
                child.replace(1, new TestSnapshot(beta, 3L));
            }
        });
        child.replace(0, new TestSnapshot(alpha, 2L));

        assertTrue(reentered[0]);
        assertEquals(Collections.singleton(0), index.getIndicesForKey(alpha));
        assertEquals(Collections.singleton(1), index.getIndicesForKey(beta));
        assertEquals(Arrays.asList(0, 1), index.getOccupiedIndices());
        assertTrue(index.getEmptyIndices().isEmpty());
        assertEquals(1, candidates.getExact().size());
        assertEquals(1, candidates.getEmpty().size());
        assertEquals(1, candidates.getEmpty().get(0).getGlobalIndex());
    }

    @Test
    public void closedAndOldGenerationEventsCannotMutateReplacementIndex() {
        TestKey alpha = new TestKey("alpha", "metal");
        TestKey beta = new TestKey("beta", "gem");
        TestKey stale = new TestKey("stale", "dust");
        TestHandler oldChild = new TestHandler(new TestSnapshot(alpha, 1L));
        TestHandler replacement = new TestHandler(new TestSnapshot(beta, 1L));
        ControllerStorageIndex<TestSnapshot, TestKey> index = index(new TestPolicy());
        index.setHandlers(Collections.singletonList(oldChild));
        Consumer<? super StorageChange<TestSnapshot, TestKey>> oldListener =
                oldChild.lastListener;

        index.setHandlers(Collections.singletonList(replacement));
        oldChild.fireStale(oldListener, StorageChange.delta(
                0, new TestSnapshot(alpha, 1L), new TestSnapshot(stale, 4L)));

        assertTrue(index.getIndicesForKey(alpha).isEmpty());
        assertTrue(index.getIndicesForKey(stale).isEmpty());
        assertEquals(Collections.singleton(0), index.getIndicesForKey(beta));
        assertEquals(1, oldChild.closeCalls);

        index.closeSubscriptions();
        index.closeSubscriptions();
        assertEquals(1, replacement.closeCalls);
        replacement.replace(0, new TestSnapshot(stale, 2L));
        assertEquals(Collections.singleton(0), index.getIndicesForKey(beta));

        assertTrue(index.setHandlers(Collections.singletonList(replacement)));
        assertEquals(Collections.singleton(0), index.getIndicesForKey(stale));
        assertEquals(2, replacement.subscribeCalls);
    }

    @Test
    public void samePhysicalIdentityRebindsFacadeWithoutResnapshotOrAggregateEvent() {
        Object sharedIdentity = new Object();
        TestKey alpha = new TestKey("alpha", "metal");
        TestKey beta = new TestKey("beta", "gem");
        TestHandler facadeA = new TestHandler(
                sharedIdentity, new TestSnapshot(alpha, 1L));
        TestHandler facadeB = new TestHandler(
                sharedIdentity, new TestSnapshot(alpha, 1L));
        ControllerStorageIndex<TestSnapshot, TestKey> index = index(new TestPolicy());
        List<StorageChange<TestSnapshot, TestKey>> changes = new ArrayList<>();
        index.subscribe(changes::add);
        index.setHandlers(Collections.singletonList(facadeA));
        changes.clear();
        int readsA = facadeA.snapshotReads;

        assertTrue(index.setHandlers(Collections.singletonList(facadeB)));
        assertEquals(readsA, facadeA.snapshotReads);
        assertEquals(0, facadeB.snapshotReads);
        assertEquals(1, facadeA.closeCalls);
        assertEquals(1, facadeB.subscribeCalls);
        assertTrue(changes.isEmpty());
        assertSame(facadeB, index.getHandlers().get(0));

        facadeB.replace(0, new TestSnapshot(beta, 3L));
        assertEquals(Collections.singleton(0), index.getIndicesForKey(beta));
        assertTrue(index.getIndicesForKey(alpha).isEmpty());

        int readsB = facadeB.snapshotReads;
        int subscriptionsB = facadeB.subscribeCalls;
        assertFalse(index.setHandlers(Collections.singletonList(facadeB)));
        assertEquals(readsB, facadeB.snapshotReads);
        assertEquals(subscriptionsB, facadeB.subscribeCalls);
    }

    private static ControllerStorageIndex<TestSnapshot, TestKey> index(TestPolicy policy) {
        return new ControllerStorageIndex<>(TestSnapshot.empty(), policy);
    }

    private static final class TestKey implements StorageKey {
        private final String id;
        private final String group;

        private TestKey(String id, String group) {
            this.id = Objects.requireNonNull(id, "id");
            this.group = Objects.requireNonNull(group, "group");
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof TestKey)) {
                return false;
            }
            TestKey other = (TestKey) object;
            return id.equals(other.id) && group.equals(other.group);
        }

        @Override
        public int hashCode() {
            return 31 * id.hashCode() + group.hashCode();
        }
    }

    private static final class TestSnapshot
            implements StorageSnapshot<TestSnapshot, TestKey> {
        private static final TestSnapshot EMPTY = new TestSnapshot(null, 0L);

        @Nullable
        private final TestKey key;
        private final long amount;

        private TestSnapshot(@Nullable TestKey key, long amount) {
            this.key = key;
            this.amount = key == null ? 0L : Math.max(0L, amount);
        }

        private static TestSnapshot empty() {
            return EMPTY;
        }

        @Nullable
        @Override
        public TestKey getKey() {
            return key;
        }

        @Override
        public long getAmount() {
            return amount;
        }

        @Nonnull
        @Override
        public TestSnapshot withAmount(long newAmount) {
            return key == null ? empty() : new TestSnapshot(key, newAmount);
        }
    }

    private static final class TestPolicy
            implements StorageRoutingPolicy<TestSnapshot, TestKey> {
        private int aliasCalls;

        @Nonnull
        @Override
        public Collection<? extends StorageKey> getCompatibleAliases(
                @Nonnull TestSnapshot snapshot) {
            aliasCalls++;
            TestKey key = snapshot.getKey();
            return key == null ? Collections.emptyList()
                    : Collections.singletonList(
                    new TestKey("alias:" + key.group, key.group));
        }

        @Override
        public boolean isEmptySlotEligible(
                @Nonnull IStorageHandler<TestSnapshot, TestKey> handler,
                int index,
                @Nonnull TestSnapshot request) {
            return !handler.isLocked();
        }

        @Override
        public int getCandidatePriority(
                @Nonnull IStorageHandler<TestSnapshot, TestKey> handler,
                int index,
                @Nonnull TestSnapshot current,
                @Nonnull TestSnapshot request) {
            return current.hasTemplate() ? 0 : 2;
        }
    }

    private static final class TestHandler
            implements IStorageHandler<TestSnapshot, TestKey> {
        private final TestSnapshot[] snapshots;
        private final long[] capacities;
        private final StorageChangeDispatcher<TestSnapshot, TestKey> dispatcher =
                new StorageChangeDispatcher<>();
        private final Object identity;
        private int snapshotReads;
        private int subscribeCalls;
        private int closeCalls;
        private Consumer<? super StorageChange<TestSnapshot, TestKey>> lastListener;

        private TestHandler(TestSnapshot... snapshots) {
            this(new Object(), snapshots);
        }

        private TestHandler(Object identity, TestSnapshot... snapshots) {
            this.snapshots = snapshots.clone();
            this.capacities = new long[snapshots.length];
            Arrays.fill(this.capacities, 64L);
            this.identity = Objects.requireNonNull(identity, "identity");
        }

        @Override
        public int getStorageCount() {
            return snapshots.length;
        }

        @Nonnull
        @Override
        public TestSnapshot getSnapshot(int index) {
            snapshotReads++;
            return valid(index) ? snapshots[index] : TestSnapshot.empty();
        }

        @Override
        public long getCapacity(int index) {
            return valid(index) ? capacities[index] : 0L;
        }

        @Nonnull
        @Override
        public TransferResult<TestSnapshot, TestKey> insert(
                int index, @Nonnull TestSnapshot request, @Nonnull StorageAction action) {
            long requested = request == null ? 0L : request.getAmount();
            if (!valid(index) || requested == 0L) {
                return new TransferResult<>(requested, TestSnapshot.empty(), action);
            }
            TestSnapshot before = snapshots[index];
            if (before.hasTemplate() && !before.isSameType(request)) {
                return new TransferResult<>(requested, TestSnapshot.empty(), action);
            }
            long accepted = Math.min(requested,
                    Math.max(0L, capacities[index] - before.getAmount()));
            if (accepted > 0L && action == StorageAction.EXECUTE) {
                replace(index, before.hasTemplate()
                        ? before.withAmount(before.getAmount() + accepted)
                        : request.withAmount(accepted));
            }
            return new TransferResult<>(requested, accepted == 0L
                    ? TestSnapshot.empty() : request.withAmount(accepted), action);
        }

        @Nonnull
        @Override
        public TransferResult<TestSnapshot, TestKey> extract(
                int index, long amount, @Nonnull StorageAction action) {
            long requested = Math.max(0L, amount);
            if (!valid(index) || requested == 0L || snapshots[index].isEmpty()) {
                return new TransferResult<>(requested, TestSnapshot.empty(), action);
            }
            TestSnapshot before = snapshots[index];
            long extracted = Math.min(requested, before.getAmount());
            if (action == StorageAction.EXECUTE) {
                replace(index, before.withAmount(before.getAmount() - extracted));
            }
            return new TransferResult<>(requested, before.withAmount(extracted), action);
        }

        @Nonnull
        @Override
        public Object getStorageIdentity() {
            return identity;
        }

        @Override
        public void onChange(@Nonnull StorageChange<TestSnapshot, TestKey> change) {
            dispatcher.dispatch(change);
        }

        @Nonnull
        @Override
        public StorageSubscription subscribe(
                @Nonnull Consumer<? super StorageChange<TestSnapshot, TestKey>> listener) {
            subscribeCalls++;
            lastListener = listener;
            StorageSubscription delegate = dispatcher.subscribe(listener);
            return new StorageSubscription() {
                private boolean closed;

                @Override
                public void close() {
                    if (!closed) {
                        closed = true;
                        closeCalls++;
                        delegate.close();
                    }
                }

                @Override
                public boolean isClosed() {
                    return closed;
                }
            };
        }

        private void replace(int index, TestSnapshot after) {
            TestSnapshot before = snapshots[index];
            snapshots[index] = after;
            onChange(StorageChange.delta(index, before, after));
        }

        private void fireStale(
                Consumer<? super StorageChange<TestSnapshot, TestKey>> listener,
                StorageChange<TestSnapshot, TestKey> change) {
            listener.accept(change);
        }

        private boolean valid(int index) {
            return index >= 0 && index < snapshots.length;
        }
    }
}
