package com.xinyihl.functionalstoragelegacy.common.integration.ae2;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

/**
 * Saturating writes for AE2 lists whose native aggregation uses raw long addition.
 */
final class AE2StorageListHelper {

    private AE2StorageListHelper() {
    }

    static <T extends IAEStack<T>> void addStorageSaturated(IItemList<T> out, T stack) {
        if (stack == null || stack.getStackSize() <= 0L) {
            return;
        }

        T existing = out.findPrecise(stack);
        long existingSize = existing == null ? 0L : existing.getStackSize();
        if (existing != null && existingSize <= 0L) {
            existing.setStackSize(0L);
            existingSize = 0L;
        }
        if (existingSize == Long.MAX_VALUE) {
            return;
        }

        long contribution = Math.min(stack.getStackSize(), Long.MAX_VALUE - existingSize);
        if (contribution > 0L) {
            T bounded = stack.copy();
            bounded.setStackSize(contribution);
            out.addStorage(bounded);
        }
    }
}
