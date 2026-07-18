package com.xinyihl.functionalstoragelegacy.common.integration.ae2;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.IStorageMonitorableAccessor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

import javax.annotation.Nullable;

/**
 * IStorageMonitorableAccessor implementation for drawers.
 * Provides item and/or fluid ME monitors to AE2 storage bus.
 */
public class DrawerStorageAccessor implements IStorageMonitorableAccessor, AutoCloseable {

    private final DrawerMEMonitor<IAEItemStack> itemMonitor;
    private final DrawerMEMonitor<IAEFluidStack> fluidMonitor;
    private boolean closed;

    public DrawerStorageAccessor(@Nullable DrawerMEMonitor<IAEItemStack> itemMonitor,
                                 @Nullable DrawerMEMonitor<IAEFluidStack> fluidMonitor) {
        this.itemMonitor = itemMonitor;
        this.fluidMonitor = fluidMonitor;
    }

    @Override
    public synchronized IStorageMonitorable getInventory(IActionSource src) {
        if (closed) {
            return null;
        }
        return new IStorageMonitorable() {
            @SuppressWarnings("unchecked")
            @Override
            public <T extends IAEStack<T>> IMEMonitor<T> getInventory(IStorageChannel<T> storageChannel) {
                if (itemMonitor != null && storageChannel == itemMonitor.getChannel()) {
                    return (IMEMonitor<T>) itemMonitor;
                }
                if (fluidMonitor != null && storageChannel == fluidMonitor.getChannel()) {
                    return (IMEMonitor<T>) fluidMonitor;
                }
                return null;
            }
        };
    }

    /** Releases both monitor subscriptions; repeated invalidation is harmless. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (itemMonitor != null) {
            itemMonitor.close();
        }
        if (fluidMonitor != null) {
            fluidMonitor.close();
        }
    }

    /** Alias used by capability providers when a tile is invalidated. */
    public void invalidate() {
        close();
    }

    public synchronized boolean isClosed() {
        return closed;
    }
}
