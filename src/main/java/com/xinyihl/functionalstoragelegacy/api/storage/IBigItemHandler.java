package com.xinyihl.functionalstoragelegacy.api.storage;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Long-capacity item handler whose implementers provide only slot snapshots,
 * capacities, and explicit transactions. Forge's int-based API and routed
 * operations are supplied as side-effect-safe defaults. Like Forge handlers,
 * instances are not implicitly thread-safe.
 */
public interface IBigItemHandler extends IItemHandler, IStorageHandler {

    /**
     * @return number of real storage slots; virtual overflow slots are forbidden
     */
    int getSlotCount();

    /**
     * Returns a detached snapshot. Implementations must return an empty snapshot
     * for an invalid index and must not expose mutable internal state.
     *
     * @param slot slot index
     * @return immutable slot snapshot
     */
    @Nonnull
    BigItemStack getSlotSnapshot(int slot);

    /**
     * Returns the long slot capacity. Implementations must return zero for an
     * invalid index and must saturate capacity calculations at {@link Long#MAX_VALUE}.
     *
     * @param slot slot index
     * @return non-negative slot capacity
     */
    long getSlotCapacity(int slot);

    /**
     * Inserts into exactly one slot. Empty requests, non-positive amounts, and
     * invalid indices must return a zero-processed result. Simulation must not
     * mutate contents, filters, NBT, or notification state.
     *
     * @param slot target slot
     * @param request immutable requested item and amount
     * @param action operation mode
     * @return validated result whose request amount equals {@code request.getAmount()}
     */
    @Nonnull
    TransferResult<BigItemStack> insertIntoSlot(
            int slot, @Nonnull BigItemStack request, @Nonnull StorageAction action);

    /**
     * Extracts from exactly one slot. Non-positive amounts and invalid indices
     * must return a zero-processed result. Simulation must be side-effect free.
     *
     * @param slot source slot
     * @param amount maximum amount to extract
     * @param action operation mode
     * @return validated result whose request amount is {@code max(0, amount)}
     */
    @Nonnull
    TransferResult<BigItemStack> extractFromSlot(
            int slot, long amount, @Nonnull StorageAction action);

    /**
     * Adapts the real slot count to Forge.
     *
     * @return a non-negative slot count
     */
    @Override
    default int getSlots() {
        return Math.max(0, getSlotCount());
    }

    /**
     * Adapts a long snapshot to a fresh Forge stack, saturating its count at
     * {@link Integer#MAX_VALUE}.
     *
     * @param slot slot index
     * @return detached Forge stack or {@link ItemStack#EMPTY}
     */
    @Nonnull
    @Override
    default ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= getSlots()) {
            return ItemStack.EMPTY;
        }
        BigItemStack snapshot = getSlotSnapshot(slot);
        return snapshot == null ? ItemStack.EMPTY : snapshot.toItemStack();
    }

    /**
     * Bridges Forge insertion to an explicit transaction and returns a fresh
     * remainder without mutating the input stack.
     *
     * @param slot target slot
     * @param stack Forge request
     * @param simulate Forge simulation flag
     * @return detached unprocessed remainder
     */
    @Nonnull
    @Override
    default ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (slot < 0 || slot >= getSlots() || stack == null || stack.isEmpty() || stack.getCount() <= 0) {
            return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }
        BigItemStack request = new BigItemStack(stack, stack.getCount());
        TransferResult<BigItemStack> result = insertIntoSlot(
                slot, request, StorageAction.fromSimulation(simulate));
        long processed = result == null ? 0L : Math.min(request.getAmount(), Math.max(0L, result.getProcessedAmount()));
        long remaining = request.getAmount() - processed;
        if (remaining == 0L) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        remainder.setCount((int) remaining);
        return remainder;
    }

    /**
     * Bridges Forge extraction while enforcing both its requested int amount
     * and the extracted item's maximum stack size.
     *
     * @param slot source slot
     * @param amount Forge maximum amount
     * @param simulate Forge simulation flag
     * @return detached extracted stack
     */
    @Nonnull
    @Override
    default ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= getSlots() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        BigItemStack stored = getSlotSnapshot(slot);
        if (stored == null || stored.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack template = stored.getTemplate();
        long requested = Math.min((long) amount, Math.max(0, template.getMaxStackSize()));
        if (requested <= 0L) {
            return ItemStack.EMPTY;
        }
        TransferResult<BigItemStack> result = extractFromSlot(
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

    /**
     * Adapts long capacity to Forge using a non-negative saturated conversion.
     *
     * @param slot slot index
     * @return capacity clamped to the int range
     */
    @Override
    default int getSlotLimit(int slot) {
        if (slot < 0 || slot >= getSlots()) {
            return 0;
        }
        long capacity = Math.max(0L, getSlotCapacity(slot));
        return capacity >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) capacity;
    }

    /**
     * Checks insertion validity through a one-item simulation. Correct handler
     * implementations therefore keep this Forge query side-effect free.
     *
     * @param slot slot index
     * @param stack item type to test
     * @return whether the slot can currently accept at least one item
     */
    @Override
    default boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        if (slot < 0 || slot >= getSlots() || stack == null || stack.isEmpty()) {
            return false;
        }
        TransferResult<BigItemStack> result = insertIntoSlot(
                slot, new BigItemStack(stack, 1L), StorageAction.SIMULATE);
        return result != null && result.getProcessedAmount() > 0L;
    }

    /**
     * Routes insertion through exact templated slots, compatible templated
     * slots, and unfiltered empty slots in that order. Non-exact compatibility
     * is determined with a side-effect-free one-item simulation. Each slot is
     * visited at most once for insertion and processed amounts are accumulated
     * without long overflow.
     *
     * @param request item type and total amount
     * @param action operation mode
     * @return aggregate transaction result
     */
    @Nonnull
    default TransferResult<BigItemStack> insertRouted(
            @Nonnull BigItemStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request == null || request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L) {
            return new TransferResult<>(0L, BigItemStack.empty(), action);
        }
        long processedTotal = 0L;
        BigItemStack compatibilityProbe = request.withAmount(1L);
        for (int pass = 0; pass < 3 && processedTotal < requested; pass++) {
            int slots = getSlots();
            for (int slot = 0; slot < slots && processedTotal < requested; slot++) {
                BigItemStack current = getSlotSnapshot(slot);
                boolean hasTemplate = current != null && current.hasTemplate();
                boolean exact = hasTemplate && current.isSameType(request);
                if (pass == 0 && !exact) {
                    continue;
                }
                if (pass == 1) {
                    if (!hasTemplate || exact) {
                        continue;
                    }
                    TransferResult<BigItemStack> probe = insertIntoSlot(
                            slot, compatibilityProbe, StorageAction.SIMULATE);
                    if (probe == null || probe.getProcessedAmount() <= 0L) {
                        continue;
                    }
                }
                if (pass == 2 && hasTemplate) {
                    continue;
                }
                long remaining = requested - processedTotal;
                TransferResult<BigItemStack> result = insertIntoSlot(
                        slot, request.withAmount(remaining), action);
                long processed = result == null ? 0L
                        : Math.min(remaining, Math.max(0L, result.getProcessedAmount()));
                processedTotal = processedTotal > Long.MAX_VALUE - processed
                        ? Long.MAX_VALUE : processedTotal + processed;
            }
        }
        return new TransferResult<>(requested, request.withAmount(processedTotal), action);
    }

    /**
     * Routes a type-sensitive extraction across every matching occupied slot.
     * Each slot is visited at most once and processed amounts are accumulated
     * without long overflow.
     *
     * @param request item type and maximum total amount
     * @param action operation mode
     * @return aggregate transaction result
     */
    @Nonnull
    default TransferResult<BigItemStack> extractRouted(
            @Nonnull BigItemStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request == null || request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L) {
            return new TransferResult<>(0L, BigItemStack.empty(), action);
        }
        long processedTotal = 0L;
        int slots = getSlots();
        for (int slot = 0; slot < slots && processedTotal < requested; slot++) {
            BigItemStack current = getSlotSnapshot(slot);
            if (current == null || current.isEmpty() || !current.isSameType(request)) {
                continue;
            }
            long remaining = requested - processedTotal;
            TransferResult<BigItemStack> result = extractFromSlot(slot, remaining, action);
            long processed = result == null ? 0L
                    : Math.min(remaining, Math.max(0L, result.getProcessedAmount()));
            processedTotal = processedTotal > Long.MAX_VALUE - processed
                    ? Long.MAX_VALUE : processedTotal + processed;
        }
        return new TransferResult<>(requested, request.withAmount(processedTotal), action);
    }
}
