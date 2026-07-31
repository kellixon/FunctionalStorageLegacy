package com.xinyihl.functionalstoragelegacy.api.storage;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;

/**
 * Forge item capability bridge for a generic long-capacity storage handler.
 * Business state is exposed only through {@link IStorageHandler}; the methods
 * below adapt that state to Forge's int-count API and retain item routing
 * semantics needed by the capability.
 */
public interface IBigItemHandler extends IItemHandler, IStorageHandler<BigItemStack, ItemStorageKey> {

    /**
     * Exposes one virtual slot per stored item key and, when available, one
     * leading empty insertion slot. Physical storage positions stay internal.
     */
    @Override
    default int getSlots() {
        return BigItemHandlerForgeView.storages(this).size() + (BigItemHandlerForgeView.hasEmptyStorage(this) ? 1 : 0);
    }

    /**
     * Returns the virtual empty slot or one aggregated item-key view. Empty
     * physical slots and zero-amount filters are never exposed.
     */
    @Nonnull
    @Override
    default ItemStack getStackInSlot(int slot) {
        boolean hasEmpty = BigItemHandlerForgeView.hasEmptyStorage(this);
        if (hasEmpty && slot == 0) {
            return ItemStack.EMPTY;
        }
        BigItemHandlerForgeView.Storage storage = BigItemHandlerForgeView.storageAt(slot, hasEmpty, BigItemHandlerForgeView.storages(this));
        return storage == null ? ItemStack.EMPTY : storage.snapshot.toItemStack();
    }

    /**
     * Bridges Forge insertion directly to routed storage. The supplied slot
     * is only a Forge compatibility argument and never selects a physical
     * drawer.
     */
    @Nonnull
    @Override
    default ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || stack.getCount() <= 0) {
            return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }
        BigItemStack request = new BigItemStack(stack, stack.getCount());
        TransferResult<BigItemStack, ItemStorageKey> result = insertRouted(request, StorageAction.fromSimulation(simulate));
        long remaining = result.getRemainingAmount();
        if (remaining == 0L) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        remainder.setCount((int) Math.min(remaining, stack.getCount()));
        return remainder;
    }

    /**
     * Bridges Forge extraction directly to the routed key represented by the
     * virtual slot. The physical drawer selected by the route is internal.
     */
    @Nonnull
    @Override
    default ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        boolean hasEmpty = BigItemHandlerForgeView.hasEmptyStorage(this);
        BigItemHandlerForgeView.Storage storage = BigItemHandlerForgeView.storageAt(slot, hasEmpty, BigItemHandlerForgeView.storages(this));
        if (storage == null) {
            return ItemStack.EMPTY;
        }
        ItemStack template = storage.snapshot.getTemplate();
        long requested = Math.min((long) amount, Math.max(0, template.getMaxStackSize()));
        if (requested <= 0L) {
            return ItemStack.EMPTY;
        }
        TransferResult<BigItemStack, ItemStorageKey> result = extractRouted(new BigItemStack(template, requested), StorageAction.fromSimulation(simulate));
        if (result.getProcessed().isEmpty()) {
            return ItemStack.EMPTY;
        }
        long processed = Math.min(requested, Math.max(0L, result.getProcessedAmount()));
        if (processed == 0L) {
            return ItemStack.EMPTY;
        }
        return result.getProcessed().withAmount(processed).toItemStack();
    }

    /**
     * Adapts long capacity to Forge's saturated int limit.
     */
    @Override
    default int getSlotLimit(int slot) {
        boolean hasEmpty = BigItemHandlerForgeView.hasEmptyStorage(this);
        List<BigItemHandlerForgeView.Storage> storages = BigItemHandlerForgeView.storages(this);
        if (hasEmpty && slot == 0) {
            return BigItemHandlerForgeView.toForgeLimit(BigItemHandlerForgeView.emptyStorageCapacity(this));
        }
        BigItemHandlerForgeView.Storage storage = BigItemHandlerForgeView.storageAt(slot, hasEmpty, storages);
        if (storage == null) {
            return 0;
        }
        return storage.voidsOverflow ? Integer.MAX_VALUE : BigItemHandlerForgeView.toForgeLimit(storage.capacity);
    }

    /**
     * Checks insertion validity through a side-effect-free generic simulation.
     */
    @Override
    default boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        if (stack.isEmpty() || !BigItemHandlerForgeView.isValidSlot(this, slot)) {
            return false;
        }
        TransferResult<BigItemStack, ItemStorageKey> result = insertRouted(new BigItemStack(stack, 1L), StorageAction.SIMULATE);
        return result.getProcessedAmount() > 0L;
    }

    /**
     * Reports whether one internal index can represent the virtual empty
     * insertion slot. Aggregate handlers may override this to inspect the
     * owning child instead of their own aggregate lock state.
     */
    default boolean isEmptyStorageAvailable(int index) {
        if (index < 0 || index >= Math.max(0, getStorageCount())) {
            return false;
        }
        BigItemStack snapshot = getSnapshot(index);
        return !snapshot.hasTemplate() && getCapacity(index) > 0L && !isLocked();
    }

    /**
     * Routes insertion through matching configured indices and then empty
     * indices. The generic index methods are the only state operations used.
     */
    @Nonnull
    default TransferResult<BigItemStack, ItemStorageKey> insertRouted(@Nonnull BigItemStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L) {
            return new TransferResult<>(0L, BigItemStack.empty(), action);
        }
        long processedTotal = 0L;
        BigItemStack compatibilityProbe = request.withAmount(1L);
        int count = Math.max(0, getStorageCount());
        for (int pass = 0; pass < 3 && processedTotal < requested; pass++) {
            for (int index = 0; index < count && processedTotal < requested; index++) {
                BigItemStack current = getSnapshot(index);
                boolean hasTemplate = current.hasTemplate();
                boolean exact = hasTemplate && current.isSameType(request);
                if (pass == 0 && !exact) {
                    continue;
                }
                if (pass == 1) {
                    if (!hasTemplate || exact) {
                        continue;
                    }
                    TransferResult<BigItemStack, ItemStorageKey> probe = insert(index, compatibilityProbe, StorageAction.SIMULATE);
                    if (probe.getProcessedAmount() <= 0L) {
                        continue;
                    }
                }
                if (pass == 2 && hasTemplate) {
                    continue;
                }
                long remaining = requested - processedTotal;
                TransferResult<BigItemStack, ItemStorageKey> result = insert(index, request.withAmount(remaining), action);
                long processed = Math.min(remaining, Math.max(0L, result.getProcessedAmount()));
                processedTotal = processedTotal > Long.MAX_VALUE - processed ? Long.MAX_VALUE : processedTotal + processed;
            }
        }
        return new TransferResult<>(requested, request.withAmount(processedTotal), action);
    }

    /**
     * Routes type-sensitive extraction through matching generic indices.
     */
    @Nonnull
    default TransferResult<BigItemStack, ItemStorageKey> extractRouted(@Nonnull BigItemStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L) {
            return new TransferResult<>(0L, BigItemStack.empty(), action);
        }
        long processedTotal = 0L;
        int count = Math.max(0, getStorageCount());
        for (int index = 0; index < count && processedTotal < requested; index++) {
            BigItemStack current = getSnapshot(index);
            if (current.isEmpty() || !current.isSameType(request)) {
                continue;
            }
            long remaining = requested - processedTotal;
            TransferResult<BigItemStack, ItemStorageKey> result = extract(index, remaining, action);
            long processed = Math.min(remaining, Math.max(0L, result.getProcessedAmount()));
            processedTotal = processedTotal > Long.MAX_VALUE - processed ? Long.MAX_VALUE : processedTotal + processed;
        }
        return new TransferResult<>(requested, request.withAmount(processedTotal), action);
    }
}

