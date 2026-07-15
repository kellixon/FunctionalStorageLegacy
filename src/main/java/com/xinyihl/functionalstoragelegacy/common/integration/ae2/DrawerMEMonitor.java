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

import java.util.*;

/**
 * Generic IMEMonitor implementation wrapping an IMEInventoryHandler.
 * Implements ITickingMonitor so AE2 storage bus can poll for external changes
 * (e.g. hopper, pipe, player interaction) via periodic onTick() calls.
 * Pattern follows AE2's ItemHandlerAdapter + InventoryCache approach.
 */
public class DrawerMEMonitor<T extends IAEStack<T>> implements IMEMonitor<T>, ITickingMonitor {

    private final IMEInventoryHandler<T> handler;
    private final IStorageChannel<T> channel;
    private final Map<IMEMonitorHandlerReceiver<T>, Object> listeners = new HashMap<>();
    private IActionSource mySource;
    private IItemList<T> cachedList;

    public DrawerMEMonitor(IMEInventoryHandler<T> handler, IStorageChannel<T> channel) {
        this.handler = handler;
        this.channel = channel;
        this.cachedList = channel.createList();
        handler.getAvailableItems(this.cachedList);
    }

    // ---- ITickingMonitor ----

    @Override
    public TickRateModulation onTick() {
        return refreshCacheAndPostDifference()
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
        if (type == Actionable.MODULATE) {
            refreshCacheAndPostDifference();
        }
        return result;
    }

    @Override
    public T extractItems(T request, Actionable mode, IActionSource src) {
        T result = handler.extractItems(request, mode, src);
        if (mode == Actionable.MODULATE) {
            refreshCacheAndPostDifference();
        }
        return result;
    }

    @Override
    public IItemList<T> getAvailableItems(IItemList<T> out) {
        return handler.getAvailableItems(out);
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
    public void addListener(IMEMonitorHandlerReceiver<T> l, Object verificationToken) {
        listeners.put(l, verificationToken);
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver<T> l) {
        listeners.remove(l);
    }

    // ---- Internal ----

    private boolean refreshCacheAndPostDifference() {
        IItemList<T> currentList = channel.createList();
        handler.getAvailableItems(currentList);
        List<T> changes = new ArrayList<>();

        for (T cached : cachedList) {
            T current = currentList.findPrecise(cached);
            long before = nonNegativeSize(cached);
            long after = nonNegativeSize(current);
            addDifference(changes, current == null ? cached : current, after - before);
        }
        for (T current : currentList) {
            if (cachedList.findPrecise(current) == null) {
                addDifference(changes, current, nonNegativeSize(current));
            }
        }

        cachedList = currentList;
        if (changes.isEmpty()) {
            return false;
        }
        postDifference(changes);
        return true;
    }

    private static <T extends IAEStack<T>> long nonNegativeSize(T stack) {
        return stack == null ? 0L : Math.max(0L, stack.getStackSize());
    }

    private static <T extends IAEStack<T>> void addDifference(
            List<T> changes, T template, long amount) {
        if (template == null || amount == 0L) {
            return;
        }
        T difference = template.copy();
        difference.setStackSize(amount);
        changes.add(difference);
    }

    private void postDifference(Iterable<T> changes) {
        Iterator<Map.Entry<IMEMonitorHandlerReceiver<T>, Object>> it = listeners.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<IMEMonitorHandlerReceiver<T>, Object> entry = it.next();
            IMEMonitorHandlerReceiver<T> receiver = entry.getKey();
            if (receiver.isValid(entry.getValue())) {
                receiver.postChange(this, changes, mySource);
            } else {
                it.remove();
            }
        }
    }
}
