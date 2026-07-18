package com.xinyihl.functionalstoragelegacy.common.integration.ae2;

import appeng.api.storage.data.IAEStack;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageChange;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageSubscription;

import java.util.function.Consumer;

/**
 * Internal bridge from a generic drawer handler to the AE2 monitor.  Keeping
 * this bridge next to the AE2 adapters means the monitor never has to infer a
 * resource key from an AE2 stack (which would lose exact NBT identity).
 */
interface AE2StorageChangeSource<T extends IAEStack<T>> {

    int getStorageCount();

    Object getSnapshot(int index);

    T createStack(Object snapshot, long amount);

    StorageSubscription subscribe(Consumer<Object> listener);
}
