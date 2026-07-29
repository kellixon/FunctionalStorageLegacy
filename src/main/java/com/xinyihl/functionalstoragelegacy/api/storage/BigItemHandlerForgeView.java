package com.xinyihl.functionalstoragelegacy.api.storage;

import javax.annotation.Nonnull;
import java.util.*;

final class BigItemHandlerForgeView {

    private BigItemHandlerForgeView() {
    }

    static List<Storage> storages(@Nonnull IBigItemHandler handler) {
        Objects.requireNonNull(handler, "handler");
        Map<ItemStorageKey, Storage> byKey = new LinkedHashMap<>();
        int count = Math.max(0, handler.getStorageCount());
        for (int index = 0; index < count; index++) {
            BigItemStack snapshot = handler.getSnapshot(index);
            if (!snapshot.hasTemplate() || snapshot.isEmpty()) {
                continue;
            }
            ItemStorageKey key = snapshot.getKey();
            Storage previous = byKey.get(key);
            if (previous == null) {
                byKey.put(key, new Storage(snapshot, handler.getCapacity(index)));
            } else {
                byKey.put(key, new Storage(previous.snapshot.withAmount(saturatedAdd(previous.snapshot.getAmount(), snapshot.getAmount())), saturatedAdd(previous.capacity, handler.getCapacity(index))));
            }
        }
        return new ArrayList<>(byKey.values());
    }

    static boolean hasEmptyStorage(@Nonnull IBigItemHandler handler) {
        int count = Math.max(0, handler.getStorageCount());
        for (int index = 0; index < count; index++) {
            if (handler.isEmptyStorageAvailable(index)) {
                return true;
            }
        }
        return false;
    }

    static long emptyStorageCapacity(@Nonnull IBigItemHandler handler) {
        long capacity = 0L;
        int count = Math.max(0, handler.getStorageCount());
        for (int index = 0; index < count; index++) {
            if (handler.isEmptyStorageAvailable(index)) {
                capacity = saturatedAdd(capacity, handler.getCapacity(index));
            }
        }
        return capacity;
    }

    static boolean isValidSlot(@Nonnull IBigItemHandler handler, int slot) {
        return slot >= 0 && slot < handler.getSlots();
    }

    static Storage storageAt(int slot, boolean hasEmpty, @Nonnull List<Storage> storages) {
        int index = slot - (hasEmpty ? 1 : 0);
        return index < 0 || index >= storages.size() ? null : storages.get(index);
    }

    static int toForgeLimit(long value) {
        long capacity = Math.max(0L, value);
        return capacity >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) capacity;
    }

    private static long saturatedAdd(long left, long right) {
        long safeLeft = Math.max(0L, left);
        long safeRight = Math.max(0L, right);
        return safeLeft > Long.MAX_VALUE - safeRight ? Long.MAX_VALUE : safeLeft + safeRight;
    }

    static final class Storage {
        final BigItemStack snapshot;
        final long capacity;

        private Storage(@Nonnull BigItemStack snapshot, long capacity) {
            this.snapshot = snapshot;
            this.capacity = Math.max(0L, capacity);
        }
    }
}
