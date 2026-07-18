package com.xinyihl.functionalstoragelegacy.common.integration.ae2;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.ITickingMonitor;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageChange;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageKey;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageSnapshot;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageSubscription;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Event-driven AE2 monitor for drawer storage.
 *
 * <p>The wrapped generic handler is scanned exactly once when a change source
 * is available.  Subsequent DELTA events update exact {@link BigInteger}
 * totals; only saturated, observable differences are sent to AE2.  A typed,
 * active subscription is mandatory: ticks never rebuild or poll inventory
 * state.</p>
 */
public class DrawerMEMonitor<T extends IAEStack<T>>
        implements IMEMonitor<T>, ITickingMonitor, AutoCloseable {

    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final IMEInventoryHandler<T> handler;
    private final IStorageChannel<T> channel;
    private final AE2StorageChangeSource<T> source;

    /** Exact (unsaturated) amount by immutable generic storage key. */
    private final Map<StorageKey, BigInteger> totals = new HashMap<>();
    /** Latest typed snapshot for each key, retained long enough for removals. */
    private final Map<StorageKey, Object> templates = new HashMap<>();
    /** Saturated amount most recently published to AE2. */
    private final Map<StorageKey, Long> published = new HashMap<>();
    private final Set<StorageKey> dirtyKeys = new LinkedHashSet<>();
    private final Map<IMEMonitorHandlerReceiver<T>, Object> listeners =
            new LinkedHashMap<>();

    private StorageSubscription subscription = StorageSubscription.CLOSED;
    private IActionSource mySource;
    private boolean closed;

    public DrawerMEMonitor(IMEInventoryHandler<T> handler,
                           IStorageChannel<T> channel) {
        this.handler = handler;
        this.channel = channel;
        if (!(handler instanceof AE2StorageChangeSource)) {
            throw new IllegalArgumentException(
                    "DrawerMEMonitor requires a typed storage change source");
        }
        this.source = castSource(handler);
        scanSource(false);
        StorageSubscription created = source.subscribe(this::acceptChange);
        if (created == null || created.isClosed()) {
            if (created != null) {
                created.close();
            }
            throw new IllegalStateException(
                    "DrawerMEMonitor requires an active storage subscription");
        }
        subscription = created;
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> AE2StorageChangeSource<T> castSource(
            IMEInventoryHandler<T> handler) {
        return (AE2StorageChangeSource<T>) handler;
    }

    // ---- ITickingMonitor ----

    @Override
    public TickRateModulation onTick() {
        if (closed) {
            return TickRateModulation.SLOWER;
        }
        return flushDirty()
                ? TickRateModulation.URGENT
                : TickRateModulation.SLOWER;
    }

    @Override
    public void setActionSource(IActionSource source) {
        this.mySource = source;
    }

    // ---- IMEInventory ----

    @Override
    public T injectItems(T input, Actionable type, IActionSource src) {
        T result = handler.injectItems(input, type, src);
        if (type == Actionable.MODULATE && !closed) {
            /* Generic handlers publish synchronously; publish before return. */
            flushDirty();
        }
        return result;
    }

    @Override
    public T extractItems(T request, Actionable mode, IActionSource src) {
        T result = handler.extractItems(request, mode, src);
        if (mode == Actionable.MODULATE && !closed) {
            flushDirty();
        }
        return result;
    }

    @Override
    public IItemList<T> getAvailableItems(IItemList<T> out) {
        for (Map.Entry<StorageKey, BigInteger> entry : totals.entrySet()) {
            long amount = saturate(entry.getValue());
            if (amount <= 0L) {
                continue;
            }
            Object snapshot = templates.get(entry.getKey());
            if (snapshot == null) {
                continue;
            }
            T stack = source.createStack(snapshot, amount);
            AE2StorageListHelper.addStorageSaturated(out, stack);
        }
        return out;
    }

    @Override
    public IItemList<T> getStorageList() {
        IItemList<T> list = channel.createList();
        return getAvailableItems(list);
    }

    @Override
    public IStorageChannel<T> getChannel() {
        return channel;
    }

    // ---- IMEInventoryHandler delegated ----

    @Override
    public AccessRestriction getAccess() {
        return handler.getAccess();
    }

    @Override
    public boolean isPrioritized(T input) {
        return handler.isPrioritized(input);
    }

    @Override
    public boolean canAccept(T input) {
        return handler.canAccept(input);
    }

    @Override
    public int getPriority() {
        return handler.getPriority();
    }

    @Override
    public int getSlot() {
        return handler.getSlot();
    }

    @Override
    public boolean validForPass(int pass) {
        return handler.validForPass(pass);
    }

    // ---- IBaseMonitor ----

    @Override
    public synchronized void addListener(IMEMonitorHandlerReceiver<T> listener,
                                          Object verificationToken) {
        if (!closed && listener != null) {
            listeners.put(listener, verificationToken);
        }
    }

    @Override
    public synchronized void removeListener(IMEMonitorHandlerReceiver<T> listener) {
        listeners.remove(listener);
    }

    /** Idempotently releases the source subscription and AE2 receivers. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        StorageSubscription current = subscription;
        subscription = StorageSubscription.CLOSED;
        listeners.clear();
        dirtyKeys.clear();
        if (current != null) {
            current.close();
        }
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    // ---- Typed change processing ----

    private void acceptChange(Object rawChange) {
        if (closed || !(rawChange instanceof StorageChange)) {
            return;
        }
        @SuppressWarnings("rawtypes")
        StorageChange change = (StorageChange) rawChange;
        if (change.isReset()) {
            applyReset();
            return;
        }
        @SuppressWarnings("rawtypes")
        List entries = change.getEntries();
        for (Object rawEntry : entries) {
            if (!(rawEntry instanceof StorageChange.Entry)) {
                continue;
            }
            StorageChange.Entry entry = (StorageChange.Entry) rawEntry;
            applySnapshot(entry.getBefore(), -1);
            applySnapshot(entry.getAfter(), 1);
        }
    }

    private void applyReset() {
        Set<StorageKey> oldKeys = new LinkedHashSet<>(totals.keySet());
        totals.clear();
        scanSource(true);
        oldKeys.addAll(totals.keySet());
        dirtyKeys.addAll(oldKeys);
    }

    @SuppressWarnings("unchecked")
    private void applySnapshot(Object rawSnapshot, int sign) {
        if (!(rawSnapshot instanceof StorageSnapshot)) {
            return;
        }
        StorageSnapshot<?, ?> snapshot = (StorageSnapshot<?, ?>) rawSnapshot;
        StorageKey key = snapshot.getKey();
        if (key == null) {
            return;
        }
        if (snapshot.hasTemplate()) {
            templates.put(key, rawSnapshot);
        }
        BigInteger amount = BigInteger.valueOf(Math.max(0L, snapshot.getAmount()));
        BigInteger current = totals.get(key);
        if (current == null) {
            current = BigInteger.ZERO;
        }
        BigInteger updated = sign < 0 ? current.subtract(amount) : current.add(amount);
        /* A malformed/out-of-order event must not expose a negative total. */
        if (updated.signum() < 0) {
            updated = BigInteger.ZERO;
        }
        if (updated.signum() == 0) {
            totals.remove(key);
        } else {
            totals.put(key, updated);
        }
        dirtyKeys.add(key);
    }

    private void scanSource(boolean markDirty) {
        totals.clear();
        int count = Math.max(0, source.getStorageCount());
        for (int index = 0; index < count; index++) {
            Object snapshot = source.getSnapshot(index);
            if (!(snapshot instanceof StorageSnapshot)) {
                continue;
            }
            StorageSnapshot<?, ?> typed = (StorageSnapshot<?, ?>) snapshot;
            StorageKey key = typed.getKey();
            if (key == null) {
                continue;
            }
            templates.put(key, snapshot);
            BigInteger amount = BigInteger.valueOf(Math.max(0L, typed.getAmount()));
            BigInteger previous = totals.get(key);
            totals.put(key, (previous == null ? BigInteger.ZERO : previous).add(amount));
            if (markDirty) {
                dirtyKeys.add(key);
            }
        }
        if (!markDirty) {
            published.clear();
            for (Map.Entry<StorageKey, BigInteger> entry : totals.entrySet()) {
                long amount = saturate(entry.getValue());
                if (amount > 0L) {
                    published.put(entry.getKey(), amount);
                }
            }
        }
    }

    private boolean flushDirty() {
        Set<StorageKey> keys;
        synchronized (this) {
            if (dirtyKeys.isEmpty() || closed) {
                return false;
            }
            keys = new LinkedHashSet<>(dirtyKeys);
            dirtyKeys.clear();
        }

        List<T> changes = new ArrayList<>();
        for (StorageKey key : keys) {
            long before = published.containsKey(key) ? published.get(key) : 0L;
            BigInteger exact = totals.get(key);
            long after = saturate(exact == null ? BigInteger.ZERO : exact);
            if (before != after) {
                Object snapshot = templates.get(key);
                if (snapshot != null) {
                    long delta;
                    if (after > before) {
                        delta = after - before;
                    } else {
                        delta = before - after == Long.MIN_VALUE
                                ? Long.MIN_VALUE : -(before - after);
                    }
                    T change = source.createStack(snapshot, delta);
                    if (change != null && delta != 0L) {
                        changes.add(change);
                    }
                }
            }
            if (after == 0L) {
                published.remove(key);
                /* Keep no stale template once a removal has been published. */
                if (!totals.containsKey(key)) {
                    templates.remove(key);
                }
            } else {
                published.put(key, after);
            }
        }
        if (changes.isEmpty()) {
            return false;
        }
        postDifference(changes);
        return true;
    }

    private void postDifference(Iterable<T> changes) {
        List<Map.Entry<IMEMonitorHandlerReceiver<T>, Object>> snapshot;
        synchronized (this) {
            if (closed || listeners.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<>(listeners.entrySet());
        }
        for (Map.Entry<IMEMonitorHandlerReceiver<T>, Object> entry : snapshot) {
            IMEMonitorHandlerReceiver<T> receiver = entry.getKey();
            if (receiver.isValid(entry.getValue())) {
                receiver.postChange(this, changes, mySource);
            } else {
                synchronized (this) {
                    listeners.remove(receiver);
                }
            }
        }
    }

    private static long saturate(BigInteger amount) {
        if (amount == null || amount.signum() <= 0) {
            return 0L;
        }
        return amount.compareTo(LONG_MAX) > 0 ? Long.MAX_VALUE : amount.longValue();
    }
}
