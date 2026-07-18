package com.xinyihl.functionalstoragelegacy.common.inventory;

import com.xinyihl.functionalstoragelegacy.api.storage.*;
import com.xinyihl.functionalstoragelegacy.misc.Configurations;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

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
    private final StorageChangeDispatcher<BigItemStack, ItemStorageKey> changeDispatcher = new StorageChangeDispatcher<>();

    public ArmoryCabinetInventoryHandler() {
        this(Configurations.GENERAL.armoryCabinetSize);
    }

    public ArmoryCabinetInventoryHandler(int size) {
        stacks = new ItemStack[Math.max(0, size)];
        Arrays.fill(stacks, ItemStack.EMPTY);
    }

    private static boolean isArmoryItem(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && (stack.getMaxStackSize() == 1 || stack.isItemStackDamageable());
    }

    @Nonnull
    private static ItemStack normalizedCopy(@Nonnull ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    private static TransferResult<BigItemStack, ItemStorageKey> emptyResult(long requested, StorageAction action) {
        return new TransferResult<>(requested, BigItemStack.empty(), action);
    }

    private static boolean sameStacks(ItemStack[] left, ItemStack[] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int slot = 0; slot < left.length; slot++) {
            if (!ItemStack.areItemStacksEqual(left[slot], right[slot])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public final int getStorageCount() {
        return stacks.length;
    }

    @Nonnull
    @Override
    public final BigItemStack getSnapshot(int slot) {
        if (!isValidSlot(slot) || stacks[slot].isEmpty()) {
            return BigItemStack.empty();
        }
        return new BigItemStack(stacks[slot], 1L);
    }

    @Override
    public final long getCapacity(int slot) {
        return isValidSlot(slot) ? 1L : 0L;
    }

    @Nonnull
    @Override
    public final TransferResult<BigItemStack, ItemStorageKey> insert(int slot, @Nonnull BigItemStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L || !isValidSlot(slot) || !stacks[slot].isEmpty()) {
            return emptyResult(requested, action);
        }
        ItemStack template = request.getTemplate();
        if (!isArmoryItem(template)) {
            return emptyResult(requested, action);
        }
        if (action == StorageAction.EXECUTE) {
            BigItemStack before = getSnapshot(slot);
            stacks[slot] = normalizedCopy(template);
            onChange(StorageChange.delta(slot, before, getSnapshot(slot)));
        }
        return new TransferResult<>(requested, request.withAmount(1L), action);
    }

    @Nonnull
    @Override
    public final TransferResult<BigItemStack, ItemStorageKey> extract(int slot, long amount, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = Math.max(0L, amount);
        if (requested == 0L || !isValidSlot(slot) || stacks[slot].isEmpty()) {
            return emptyResult(requested, action);
        }
        BigItemStack processed = new BigItemStack(stacks[slot], 1L);
        if (action == StorageAction.EXECUTE) {
            BigItemStack before = getSnapshot(slot);
            stacks[slot] = ItemStack.EMPTY;
            onChange(StorageChange.delta(slot, before, getSnapshot(slot)));
        }
        return new TransferResult<>(requested, processed, action);
    }

    /**
     * Serializes the armory through the shared {@code StorageV2.Items} schema.
     */
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
        ItemStack[] before = stacks.clone();
        Arrays.fill(stacks, ItemStack.EMPTY);
        if (root != null && root.hasKey(STORAGE_V2, Constants.NBT.TAG_COMPOUND)) {
            NBTTagList items = root.getCompoundTag(STORAGE_V2).getTagList(ITEMS, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < items.tagCount(); i++) {
                NBTTagCompound entry = items.getCompoundTagAt(i);
                int slot = entry.getInteger(INDEX);
                if (!isValidSlot(slot) || entry.getLong(AMOUNT) <= 0L || !entry.hasKey(STACK, Constants.NBT.TAG_COMPOUND)) {
                    continue;
                }
                ItemStack stack = new ItemStack(entry.getCompoundTag(STACK));
                if (!isArmoryItem(stack)) {
                    continue;
                }
                stacks[slot] = normalizedCopy(stack);
            }
        }
        if (changeDispatcher.hasSubscribers() && !sameStacks(before, stacks)) {
            onChange(StorageChange.reset());
        }
    }

    @Override
    public final void onChange(@Nonnull StorageChange<BigItemStack, ItemStorageKey> change) {
        changeDispatcher.dispatch(change);
    }

    @Nonnull
    @Override
    public final StorageSubscription subscribe(@Nonnull Consumer<? super StorageChange<BigItemStack, ItemStorageKey>> listener) {
        return changeDispatcher.subscribe(listener);
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
}
