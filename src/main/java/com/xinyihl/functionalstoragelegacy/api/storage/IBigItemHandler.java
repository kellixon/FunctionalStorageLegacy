package com.xinyihl.functionalstoragelegacy.api.storage;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Forge item capability bridge for a generic long-capacity storage handler.
 * Business state is exposed only through {@link IStorageHandler}; the methods
 * below adapt that state to Forge's int-count API and retain item routing
 * semantics needed by the capability.
 */
public interface IBigItemHandler extends IItemHandler,
        IStorageHandler<BigItemStack, ItemStorageKey> {

    /** Adapts the generic index count to Forge. */
    @Override
    default int getSlots() {
        return Math.max(0, getStorageCount());
    }

    /** Adapts a long snapshot to a fresh Forge stack. */
    @Nonnull
    @Override
    default ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= getSlots()) {
            return ItemStack.EMPTY;
        }
        BigItemStack snapshot = getSnapshot(slot);
        return snapshot == null ? ItemStack.EMPTY : snapshot.toItemStack();
    }

    /** Bridges Forge insertion to the generic indexed transaction. */
    @Nonnull
    @Override
    default ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (slot < 0 || slot >= getSlots() || stack == null
                || stack.isEmpty() || stack.getCount() <= 0) {
            return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }
        BigItemStack request = new BigItemStack(stack, stack.getCount());
        TransferResult<BigItemStack, ItemStorageKey> result = insert(
                slot, request, StorageAction.fromSimulation(simulate));
        long processed = result == null ? 0L
                : Math.min(request.getAmount(), Math.max(0L, result.getProcessedAmount()));
        long remaining = request.getAmount() - processed;
        if (remaining == 0L) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        remainder.setCount((int) remaining);
        return remainder;
    }

    /** Bridges Forge extraction to the generic indexed transaction. */
    @Nonnull
    @Override
    default ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= getSlots() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        BigItemStack stored = getSnapshot(slot);
        if (stored == null || stored.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack template = stored.getTemplate();
        long requested = Math.min((long) amount, Math.max(0, template.getMaxStackSize()));
        if (requested <= 0L) {
            return ItemStack.EMPTY;
        }
        TransferResult<BigItemStack, ItemStorageKey> result = extract(
                slot, requested, StorageAction.fromSimulation(simulate));
        if (result == null || result.getProcessed().isEmpty()) {
            return ItemStack.EMPTY;
        }
        long processed = Math.min(requested, Math.max(0L, result.getProcessedAmount()));
        if (processed == 0L) {
            return ItemStack.EMPTY;
        }
        return result.getProcessed().withAmount(processed).toItemStack();
    }

    /** Adapts long capacity to Forge's saturated int limit. */
    @Override
    default int getSlotLimit(int slot) {
        if (slot < 0 || slot >= getSlots()) {
            return 0;
        }
        long capacity = Math.max(0L, getCapacity(slot));
        return capacity >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) capacity;
    }

    /** Checks insertion validity through a side-effect-free generic simulation. */
    @Override
    default boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        if (slot < 0 || slot >= getSlots() || stack == null || stack.isEmpty()) {
            return false;
        }
        TransferResult<BigItemStack, ItemStorageKey> result = insert(
                slot, new BigItemStack(stack, 1L), StorageAction.SIMULATE);
        return result != null && result.getProcessedAmount() > 0L;
    }

    /**
     * Routes insertion through matching configured indices and then empty
     * indices. The generic index methods are the only state operations used.
     */
    @Nonnull
    default TransferResult<BigItemStack, ItemStorageKey> insertRouted(
            @Nonnull BigItemStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request == null || request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L) {
            return new TransferResult<>(0L, BigItemStack.empty(), action);
        }
        long processedTotal = 0L;
        BigItemStack compatibilityProbe = request.withAmount(1L);
        int count = Math.max(0, getStorageCount());
        for (int pass = 0; pass < 3 && processedTotal < requested; pass++) {
            for (int index = 0; index < count && processedTotal < requested; index++) {
                BigItemStack current = getSnapshot(index);
                boolean hasTemplate = current != null && current.hasTemplate();
                boolean exact = hasTemplate && current.isSameType(request);
                if (pass == 0 && !exact) {
                    continue;
                }
                if (pass == 1) {
                    if (!hasTemplate || exact) {
                        continue;
                    }
                    TransferResult<BigItemStack, ItemStorageKey> probe = insert(
                            index, compatibilityProbe, StorageAction.SIMULATE);
                    if (probe == null || probe.getProcessedAmount() <= 0L) {
                        continue;
                    }
                }
                if (pass == 2 && hasTemplate) {
                    continue;
                }
                long remaining = requested - processedTotal;
                TransferResult<BigItemStack, ItemStorageKey> result = insert(
                        index, request.withAmount(remaining), action);
                long processed = result == null ? 0L
                        : Math.min(remaining, Math.max(0L, result.getProcessedAmount()));
                processedTotal = processedTotal > Long.MAX_VALUE - processed
                        ? Long.MAX_VALUE : processedTotal + processed;
            }
        }
        return new TransferResult<>(requested, request.withAmount(processedTotal), action);
    }

    /** Routes type-sensitive extraction through matching generic indices. */
    @Nonnull
    default TransferResult<BigItemStack, ItemStorageKey> extractRouted(
            @Nonnull BigItemStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request == null || request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L) {
            return new TransferResult<>(0L, BigItemStack.empty(), action);
        }
        long processedTotal = 0L;
        int count = Math.max(0, getStorageCount());
        for (int index = 0; index < count && processedTotal < requested; index++) {
            BigItemStack current = getSnapshot(index);
            if (current == null || current.isEmpty() || !current.isSameType(request)) {
                continue;
            }
            long remaining = requested - processedTotal;
            TransferResult<BigItemStack, ItemStorageKey> result = extract(
                    index, remaining, action);
            long processed = result == null ? 0L
                    : Math.min(remaining, Math.max(0L, result.getProcessedAmount()));
            processedTotal = processedTotal > Long.MAX_VALUE - processed
                    ? Long.MAX_VALUE : processedTotal + processed;
        }
        return new TransferResult<>(requested, request.withAmount(processedTotal), action);
    }
}
