package com.xinyihl.functionalstoragelegacy.common.inventory;

import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.api.storage.TransferResult;
import com.xinyihl.functionalstoragelegacy.misc.Configurations;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Fixed-slot large item handler for unstackable armory items. Each real slot
 * has capacity one, slot-addressed operations are strict, and mutable stack
 * state never escapes the handler.
 */
public abstract class ArmoryCabinetInventoryHandler implements IBigItemHandler {

    private static final String STORAGE_V2 = "StorageV2";
    private static final String ITEMS = "Items";
    private static final String INDEX = "Index";
    private static final String STACK = "Stack";
    private static final String AMOUNT = "Amount";

    private final ItemStack[] stacks;

    public ArmoryCabinetInventoryHandler() {
        this(Configurations.GENERAL.armoryCabinetSize);
    }

    public ArmoryCabinetInventoryHandler(int size) {
        stacks = new ItemStack[Math.max(0, size)];
        clear();
    }

    public abstract void onChange();

    @Override
    public final int getSlotCount() {
        return stacks.length;
    }

    @Nonnull
    @Override
    public final BigItemStack getSlotSnapshot(int slot) {
        if (!isValidSlot(slot) || stacks[slot].isEmpty()) {
            return BigItemStack.empty();
        }
        return new BigItemStack(stacks[slot], 1L);
    }

    @Override
    public final long getSlotCapacity(int slot) {
        return isValidSlot(slot) ? 1L : 0L;
    }

    @Nonnull
    @Override
    public final TransferResult<BigItemStack> insertIntoSlot(
            int slot, @Nonnull BigItemStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request == null || request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L || !isValidSlot(slot) || !stacks[slot].isEmpty()) {
            return emptyResult(requested, action);
        }
        ItemStack template = request.getTemplate();
        if (!isArmoryItem(template)) {
            return emptyResult(requested, action);
        }
        if (action == StorageAction.EXECUTE) {
            stacks[slot] = normalizedCopy(template);
            onChange();
        }
        return new TransferResult<>(requested, request.withAmount(1L), action);
    }

    @Nonnull
    @Override
    public final TransferResult<BigItemStack> extractFromSlot(
            int slot, long amount, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = Math.max(0L, amount);
        if (requested == 0L || !isValidSlot(slot) || stacks[slot].isEmpty()) {
            return emptyResult(requested, action);
        }
        BigItemStack processed = new BigItemStack(stacks[slot], 1L);
        if (action == StorageAction.EXECUTE) {
            stacks[slot] = ItemStack.EMPTY;
            onChange();
        }
        return new TransferResult<>(requested, processed, action);
    }

    /** Serializes the armory through the shared {@code StorageV2.Items} schema. */
    @Nonnull
    public final NBTTagCompound serializeNBT() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound storage = new NBTTagCompound();
        NBTTagList items = new NBTTagList();
        for (int slot = 0; slot < stacks.length; slot++) {
            if (stacks[slot].isEmpty()) {
                continue;
            }
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger(INDEX, slot);
            entry.setTag(STACK, stacks[slot].writeToNBT(new NBTTagCompound()));
            entry.setLong(AMOUNT, 1L);
            items.appendTag(entry);
        }
        storage.setTag(ITEMS, items);
        root.setTag(STORAGE_V2, storage);
        return root;
    }

    /**
     * Replaces contents from {@code StorageV2.Items}. Missing V2 data means an
     * empty cabinet; legacy {@code Size}, {@code Slot_*}, and wrapper keys are ignored.
     */
    public final void deserializeNBT(@Nullable NBTTagCompound root) {
        clear();
        if (root == null || !root.hasKey(STORAGE_V2, Constants.NBT.TAG_COMPOUND)) {
            return;
        }
        NBTTagList items = root.getCompoundTag(STORAGE_V2)
                .getTagList(ITEMS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound entry = items.getCompoundTagAt(i);
            int slot = entry.getInteger(INDEX);
            if (!isValidSlot(slot) || entry.getLong(AMOUNT) <= 0L
                    || !entry.hasKey(STACK, Constants.NBT.TAG_COMPOUND)) {
                continue;
            }
            ItemStack stack = new ItemStack(entry.getCompoundTag(STACK));
            if (!isArmoryItem(stack)) {
                continue;
            }
            stacks[slot] = normalizedCopy(stack);
        }
    }

    public final int getFilledSlotCount() {
        int count = 0;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private boolean isValidSlot(int slot) {
        return slot >= 0 && slot < stacks.length;
    }

    private static boolean isArmoryItem(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.getMaxStackSize() == 1 || stack.isItemStackDamageable());
    }

    @Nonnull
    private static ItemStack normalizedCopy(@Nonnull ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    private static TransferResult<BigItemStack> emptyResult(
            long requested, StorageAction action) {
        return new TransferResult<>(requested, BigItemStack.empty(), action);
    }

    private void clear() {
        for (int slot = 0; slot < stacks.length; slot++) {
            stacks[slot] = ItemStack.EMPTY;
        }
    }
}
