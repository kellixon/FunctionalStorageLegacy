package com.xinyihl.functionalstoragelegacy.api.storage;

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
import static org.junit.Assert.fail;

/** Exercises the generic core with a third resource type and no game runtime types. */
public class GenericStorageCoreTest {

    @Test
    public void genericHandlerTransfersAndRoutesThirdPartySnapshots() {
        TestHandler handler = new TestHandler(10L, 10L);
        List<StorageChange<TestSnapshot, TestKey>> changes = new ArrayList<>();
        StorageSubscription subscription = handler.subscribe(changes::add);
        TestSnapshot alpha = new TestSnapshot(new TestKey("alpha", "metal"), 7L);

        TransferResult<TestSnapshot, TestKey> inserted = handler.insert(
                0, alpha, StorageAction.EXECUTE);
        assertEquals(7L, inserted.getProcessedAmount());
        assertEquals(7L, handler.getSnapshot(0).getAmount());
        assertEquals(1, changes.size());
        assertEquals(0, changes.get(0).getEntries().get(0).getIndex());
        assertFalse(changes.get(0).getEntries().get(0).getBefore().hasTemplate());
        assertSame(alpha.getKey(), handler.getSnapshot(0).getKey());

        TransferResult<TestSnapshot, TestKey> simulated = handler.extract(
                0, 3L, StorageAction.SIMULATE);
        assertEquals(3L, simulated.getProcessedAmount());
        assertEquals(7L, handler.getSnapshot(0).getAmount());
        assertEquals(1, changes.size());

        TransferResult<TestSnapshot, TestKey> extracted = handler.extract(
                0, 3L, StorageAction.EXECUTE);
        assertEquals(3L, extracted.getProcessedAmount());
        assertEquals(4L, handler.getSnapshot(0).getAmount());
        assertEquals(2, changes.size());
        assertEquals(7L, changes.get(1).getEntries().get(0).getBefore().getAmount());
        assertEquals(4L, changes.get(1).getEntries().get(0).getAfter().getAmount());

        TestRoutingPolicy policy = new TestRoutingPolicy();
        TestSnapshot equivalent = new TestSnapshot(new TestKey("beta", "metal"), 1L);
        assertEquals(alpha.getKey(), policy.getExactKey(alpha));
        assertTrue(policy.getCompatibleAliases(alpha).contains(
                new TestKey("alias:metal", "metal")));
        assertEquals(0, policy.getCandidatePriority(handler, 0, alpha, alpha));
        assertEquals(1, policy.getCandidatePriority(handler, 0, equivalent, alpha));
        assertEquals(2, policy.getCandidatePriority(
                handler, 1, TestSnapshot.empty(), alpha));
        assertTrue(policy.isEmptySlotEligible(handler, 1, alpha));
        assertEquals(1.0D, handler.getMultiplier(), 0.0D);
        assertSame(handler, handler.getStorageIdentity());

        subscription.close();
        subscription.close();
        assertTrue(subscription.isClosed());
    }

    @Test
    public void typedZeroRetainsItsGenericFilterKey() {
        TestKey key = new TestKey("locked", "filter");
        TestSnapshot typedZero = new TestSnapshot(key, 0L);

        assertTrue(typedZero.isEmpty());
        assertTrue(typedZero.hasTemplate());
        assertSame(key, typedZero.getKey());
        assertTrue(typedZero.isSameType(new TestSnapshot(
                new TestKey("locked", "filter"), 9L)));
        assertFalse(TestSnapshot.empty().hasTemplate());
        assertFalse(TestSnapshot.empty().isSameType(typedZero));
    }

    @Test
    public void deltaBatchesAndResetKeepDistinctImmutableShapes() {
        TestSnapshot empty = TestSnapshot.empty();
        TestSnapshot alpha = new TestSnapshot(new TestKey("alpha", "a"), 2L);
        TestSnapshot beta = new TestSnapshot(new TestKey("beta", "b"), 3L);
        List<StorageChange.Entry<TestSnapshot, TestKey>> source = new ArrayList<>();
        source.add(new StorageChange.Entry<>(0, empty, alpha));
        source.add(new StorageChange.Entry<>(2, empty, beta));

        StorageChange<TestSnapshot, TestKey> delta = StorageChange.delta(source);
        source.clear();
        assertTrue(delta.isDelta());
        assertEquals(StorageChange.Type.DELTA, delta.getType());
        assertEquals(2, delta.getEntries().size());
        assertEquals(alpha, delta.getEntries().get(0).getAfter());
        try {
            delta.getEntries().add(new StorageChange.Entry<>(3, empty, alpha));
            fail("delta entries must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected immutable view.
        }

        StorageChange<TestSnapshot, TestKey> reset = StorageChange.reset();
        assertTrue(reset.isReset());
        assertTrue(reset.getEntries().isEmpty());

        try {
            StorageChange.delta(Collections.<StorageChange.Entry<TestSnapshot, TestKey>>emptyList());
            fail("empty DELTA must be rejected");
        } catch (IllegalArgumentException expected) {
            // RESET is the only event without indexed entries.
        }
    }

    @Test
    public void dispatcherHasDeterministicReentrantSubscriptionSemantics() {
        StorageChangeDispatcher<TestSnapshot, TestKey> dispatcher =
                new StorageChangeDispatcher<>();
        StorageChange<TestSnapshot, TestKey> delta = StorageChange.delta(
                0, TestSnapshot.empty(),
                new TestSnapshot(new TestKey("alpha", "a"), 1L));
        StorageChange<TestSnapshot, TestKey> reset = StorageChange.reset();
        List<String> calls = new ArrayList<>();
        StorageSubscription[] cancelled = new StorageSubscription[1];
        StorageSubscription[] late = new StorageSubscription[1];

        StorageSubscription first = dispatcher.subscribe(change -> {
            calls.add("first:" + change.getType());
            if (change.isDelta()) {
                late[0] = dispatcher.subscribe(
                        later -> calls.add("late:" + later.getType()));
                cancelled[0].close();
                dispatcher.dispatch(reset);
            }
        });
        cancelled[0] = dispatcher.subscribe(
                change -> calls.add("cancelled:" + change.getType()));
        StorageSubscription steady = dispatcher.subscribe(
                change -> calls.add("steady:" + change.getType()));

        assertTrue(dispatcher.hasSubscribers());
        dispatcher.dispatch(delta);

        assertEquals(Arrays.asList(
                "first:DELTA",
                "steady:DELTA",
                "first:RESET",
                "steady:RESET",
                "late:RESET"), calls);
        assertTrue(cancelled[0].isClosed());

        first.close();
        steady.close();
        late[0].close();
        late[0].close();
        assertFalse(dispatcher.hasSubscribers());
        dispatcher.dispatch(delta);
        assertEquals(5, calls.size());
    }

    @Test
    public void dispatcherContinuesAfterListenerFailureAndRethrowsAfterTheBatch() {
        StorageChangeDispatcher<TestSnapshot, TestKey> dispatcher =
                new StorageChangeDispatcher<>();
        StorageChange<TestSnapshot, TestKey> delta = StorageChange.delta(
                0, TestSnapshot.empty(),
                new TestSnapshot(new TestKey("alpha", "a"), 1L));
        List<String> calls = new ArrayList<>();
        dispatcher.subscribe(change -> {
            calls.add("failed");
            throw new IllegalStateException("listener failure");
        });
        dispatcher.subscribe(change -> calls.add("steady"));

        try {
            dispatcher.dispatch(delta);
            fail("listener failure must be propagated");
        } catch (IllegalStateException expected) {
            assertEquals("listener failure", expected.getMessage());
        }
        assertEquals(Arrays.asList("failed", "steady"), calls);
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

    private static final class TestHandler
            implements IStorageHandler<TestSnapshot, TestKey> {
        private final TestSnapshot[] snapshots;
        private final long[] capacities;
        private final StorageChangeDispatcher<TestSnapshot, TestKey> dispatcher =
                new StorageChangeDispatcher<>();

        private TestHandler(long... capacities) {
            this.capacities = capacities.clone();
            this.snapshots = new TestSnapshot[capacities.length];
            Arrays.fill(this.snapshots, TestSnapshot.empty());
        }

        @Override
        public int getStorageCount() {
            return snapshots.length;
        }

        @Nonnull
        @Override
        public TestSnapshot getSnapshot(int index) {
            return valid(index) ? snapshots[index] : TestSnapshot.empty();
        }

        @Override
        public long getCapacity(int index) {
            return valid(index) ? Math.max(0L, capacities[index]) : 0L;
        }

        @Nonnull
        @Override
        public TransferResult<TestSnapshot, TestKey> insert(
                int index, @Nonnull TestSnapshot request, @Nonnull StorageAction action) {
            long requested = request == null ? 0L : request.getAmount();
            if (!valid(index) || requested == 0L || !request.hasTemplate()) {
                return new TransferResult<>(requested, TestSnapshot.empty(), action);
            }
            TestSnapshot before = snapshots[index];
            if (before.hasTemplate() && !before.isSameType(request)) {
                return new TransferResult<>(requested, TestSnapshot.empty(), action);
            }
            long space = Math.max(0L, getCapacity(index) - before.getAmount());
            long accepted = Math.min(requested, space);
            if (accepted > 0L && action == StorageAction.EXECUTE) {
                long newAmount = before.getAmount() + accepted;
                snapshots[index] = before.hasTemplate()
                        ? before.withAmount(newAmount) : request.withAmount(newAmount);
                onChange(StorageChange.delta(index, before, snapshots[index]));
            }
            return new TransferResult<>(requested,
                    accepted == 0L ? TestSnapshot.empty() : request.withAmount(accepted), action);
        }

        @Nonnull
        @Override
        public TransferResult<TestSnapshot, TestKey> extract(
                int index, long amount, @Nonnull StorageAction action) {
            long requested = Math.max(0L, amount);
            TestSnapshot before = getSnapshot(index);
            if (!valid(index) || requested == 0L || before.isEmpty()) {
                return new TransferResult<>(requested, TestSnapshot.empty(), action);
            }
            long extracted = Math.min(requested, before.getAmount());
            if (action == StorageAction.EXECUTE) {
                long remaining = before.getAmount() - extracted;
                snapshots[index] = remaining == 0L
                        ? TestSnapshot.empty() : before.withAmount(remaining);
                onChange(StorageChange.delta(index, before, snapshots[index]));
            }
            return new TransferResult<>(
                    requested, before.withAmount(extracted), action);
        }

        @Override
        public void onChange(@Nonnull StorageChange<TestSnapshot, TestKey> change) {
            dispatcher.dispatch(change);
        }

        @Nonnull
        @Override
        public StorageSubscription subscribe(
                @Nonnull Consumer<? super StorageChange<TestSnapshot, TestKey>> listener) {
            return dispatcher.subscribe(listener);
        }

        private boolean valid(int index) {
            return index >= 0 && index < snapshots.length;
        }
    }

    private static final class TestRoutingPolicy
            implements StorageRoutingPolicy<TestSnapshot, TestKey> {

        @Nonnull
        @Override
        public Collection<? extends StorageKey> getCompatibleAliases(
                @Nonnull TestSnapshot snapshot) {
            TestKey key = snapshot.getKey();
            return key == null
                    ? Collections.<StorageKey>emptyList()
                    : Collections.singletonList(
                            new TestKey("alias:" + key.group, key.group));
        }

        @Override
        public boolean isEmptySlotEligible(
                @Nonnull IStorageHandler<TestSnapshot, TestKey> handler,
                int index,
                @Nonnull TestSnapshot request) {
            return !handler.isLocked()
                    && handler.getCapacity(index) > 0L
                    && request.hasTemplate();
        }

        @Override
        public int getCandidatePriority(
                @Nonnull IStorageHandler<TestSnapshot, TestKey> handler,
                int index,
                @Nonnull TestSnapshot current,
                @Nonnull TestSnapshot request) {
            if (current.isSameType(request)) {
                return 0;
            }
            if (current.hasTemplate() && request.hasTemplate()
                    && current.getKey().group.equals(request.getKey().group)) {
                return 1;
            }
            return !current.hasTemplate() && isEmptySlotEligible(handler, index, request)
                    ? 2 : -1;
        }
    }
}
