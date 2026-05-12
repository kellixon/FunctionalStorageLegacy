package com.xinyihl.functionalstoragelegacy.common.inventory;

import com.xinyihl.functionalstoragelegacy.api.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.api.ILockable;
import com.xinyihl.functionalstoragelegacy.util.ItemUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Compacting inventory handler. Maintains items at multiple compression tiers.
 * For example: Iron Nugget <-> Iron Ingot <-> Iron Block.
 * Inserting at any tier auto-converts amounts across all tiers.
 */
public abstract class CompactingInventoryHandler implements IBigItemHandler, ILockable {

    private final List<Result> results;
    private final int slots;
    private boolean setup = false;
    // Total stored in base item units
    private long totalInBase = 0;

    public CompactingInventoryHandler(int slots) {
        this.slots = slots;
        this.results = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            this.results.add(new Result(ItemStack.EMPTY, 1));
        }
    }

    private boolean areItemStacksCompatible(ItemStack a, ItemStack b) {
        return ItemUtil.areItemStacksCompatible(a, b, allowsEquivalentItems());
    }

    @Override
    public int getSlots() {
        if (isVoid()) return slots + 1;
        return slots;
    }

    public boolean canDoubleClickSlot(int slot) {
        if (slot >= slots) return false;
        Result result = results.get(slot);
        return isLocked() || !result.getStack().isEmpty();
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot >= slots) return ItemStack.EMPTY;
        Result result = results.get(slot);
        if (result.getStack().isEmpty()) return ItemStack.EMPTY;
        long totalInBase = getTotalInBase();
        long amountAtTier = totalInBase / result.getNeeded();
        if (isCreative() && amountAtTier > 0) amountAtTier = Integer.MAX_VALUE;
        ItemStack out = result.getStack().copy();
        out.setCount((int) Math.min(amountAtTier, Integer.MAX_VALUE));
        return out;
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        long remaining = insertItemLong(slot, stack, stack.getCount(), simulate);
        if (remaining <= 0) return ItemStack.EMPTY;
        if (remaining >= stack.getCount()) return stack;
        ItemStack rem = stack.copy();
        rem.setCount((int) remaining);
        return rem;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot < 0 || slot >= slots) return ItemStack.EMPTY;
        Result result = results.get(slot);
        if (result.getStack().isEmpty()) return ItemStack.EMPTY;
        ItemStack out = result.getStack().copy();
        int maxExtract = Math.min(amount, result.getStack().getMaxStackSize());
        long extracted = extractItemLong(slot, maxExtract, simulate);
        if (extracted <= 0) return ItemStack.EMPTY;
        out.setCount((int) Math.min(extracted, Integer.MAX_VALUE));
        return out;
    }

    @Override
    public int getSlotLimit(int slot) {
        return (int) Math.min(getLongSlotLimit(slot), Integer.MAX_VALUE);
    }

    public long getLongSlotLimit(int slot) {
        if (isCreative()) return Long.MAX_VALUE;
        if (slot >= slots) return Long.MAX_VALUE;
        double stackSize = 1;
        Result result = results.get(slot);
        if (!result.getStack().isEmpty()) {
            stackSize = result.getStack().getMaxStackSize() / 64D;
        }
        return (long) Math.floor(getTotalCapacity() * stackSize / result.getNeeded());
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        if (slot >= slots) return false;
        Result result = results.get(slot);
        if (result.getStack().isEmpty()) return !isLocked();
        return areItemStacksCompatible(result.getStack(), stack);
    }

    @Override
    public long insertItemLong(int slot, @Nonnull ItemStack stack, long amount, boolean simulate) {
        if (stack.isEmpty() || amount <= 0) return amount;
        if (isVoid() && slot == slots && isVoidValid(stack)) return 0;

        if (slot < slots) {
            Result result = results.get(slot);
            if (!result.getStack().isEmpty() && areItemStacksCompatible(result.getStack(), stack)) {
                long baseEquiv = amount * result.getNeeded();
                long maxBase = getLongSlotLimit(getBaseSlot()) * getBaseResult().getNeeded();
                long currentBase = getTotalInBase();
                long canInsertBase = Math.min(baseEquiv, maxBase - currentBase);
                long insertedItems = canInsertBase / result.getNeeded();

                if (insertedItems <= 0) {
                    if (isVoid()) return 0;
                    return amount;
                }

                if (!simulate && !isCreative()) {
                    setTotalInBase(currentBase + insertedItems * result.getNeeded());
                    onChange();
                }

                long leftover = amount - insertedItems;
                if (leftover > 0 && isVoid()) return 0;
                return Math.max(0, leftover);
            }
        }
        return amount;
    }

    @Override
    public long extractItemLong(int slot, long amount, boolean simulate) {
        if (amount <= 0 || slot >= slots || slot < 0) return 0;
        Result result = results.get(slot);
        if (result.getStack().isEmpty()) return 0;

        long totalBase = getTotalInBase();
        long available = isCreative() ? amount : totalBase / result.getNeeded();
        long extracting = Math.min(amount, available);
        if (extracting <= 0) return 0;

        if (!simulate && !isCreative()) {
            setTotalInBase(totalBase - extracting * result.getNeeded());
            if (totalInBase <= 0 && !isLocked()) {
                resetConfiguration();
            }
            onChange();
        }
        return extracting;
    }

    @Override
    public long getStoredAmount(int slot) {
        if (slot < 0 || slot >= slots) return 0;
        Result result = results.get(slot);
        if (result.getStack().isEmpty()) return 0;
        return isCreative() && getTotalInBase() > 0 ? Long.MAX_VALUE : getTotalInBase() / result.getNeeded();
    }

    @Nonnull
    @Override
    public ItemStack getStoredType(int slot) {
        if (slot >= 0 && slot < slots) {
            return results.get(slot).getStack();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int getRealSlotCount() {
        return slots;
    }

    private boolean isVoidValid(ItemStack stack) {
        for (Result result : results) {
            if (areItemStacksCompatible(result.getStack(), stack)) return true;
        }
        return false;
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setBoolean("Setup", setup);
        nbt.setLong("TotalBase", getTotalInBase());
        for (int i = 0; i < results.size(); i++) {
            NBTTagCompound entry = new NBTTagCompound();
            NBTTagCompound stackTag = new NBTTagCompound();
            if (!results.get(i).getStack().isEmpty()) {
                results.get(i).getStack().writeToNBT(stackTag);
            }
            entry.setTag("Stack", stackTag);
            entry.setInteger("Needed", results.get(i).getNeeded());
            nbt.setTag("Result_" + i, entry);
        }
        return nbt;
    }

    public void deserializeNBT(NBTTagCompound nbt) {
        setup = nbt.getBoolean("Setup");
        for (int i = 0; i < results.size(); i++) {
            String key = "Result_" + i;
            if (nbt.hasKey(key)) {
                NBTTagCompound entry = nbt.getCompoundTag(key);
                NBTTagCompound stackTag = entry.getCompoundTag("Stack");
                ItemStack stack = stackTag.getKeySet().isEmpty() ? ItemStack.EMPTY : new ItemStack(stackTag);
                results.get(i).setStack(stack);
                results.get(i).setNeeded(entry.getInteger("Needed"));
            }
        }
        setTotalInBase(nbt.getLong("TotalBase"));
    }

    // Abstract methods
    public abstract void onChange();

    public abstract float getMultiplier();

    public abstract boolean isVoid();

    public abstract boolean isLocked();

    public abstract boolean isCreative();

    // Total capacity in base items
    public long getTotalCapacity() {
        if (hasMaxStorage()) {
            return Long.MAX_VALUE;
        }
        int nonEmptyCount = 0;
        for (Result r : results) {
            if (!r.getStack().isEmpty()) nonEmptyCount++;
        }
        int exponent = nonEmptyCount - 1;
        if (exponent < 0) {
            return (long) (64.0 * getMultiplier() / 9.0);
        }
        long pow = 1;
        for (int j = 0; j < exponent; j++) {
            pow *= 9;
        }
        return (long) (64 * pow * getMultiplier());
    }

    protected boolean allowsEquivalentItems() {
        return false;
    }

    protected boolean hasMaxStorage() {
        return false;
    }

    public long getTotalInBase() {
        return totalInBase;
    }

    public void setTotalInBase(long totalInBase) {
        this.totalInBase = Math.max(0, totalInBase);
    }

    public List<Result> getResults() {
        return results;
    }

    /**
     * Set the compacting results (highest tier first).
     * e.g., [Iron Block(need=81), Iron Ingot(need=9), Iron Nugget(need=1)]
     */
    public void setResults(List<Result> newResults) {
        results.clear();
        results.addAll(newResults);
        setup = true;
    }

    public boolean isSetup() {
        return setup;
    }

    private void resetConfiguration() {
        setTotalInBase(0);
        for (Result result : results) {
            result.setStack(ItemStack.EMPTY);
            result.setNeeded(1);
        }
        setup = false;
    }

    private int getBaseSlot() {
        return results.size() - 1;
    }

    private Result getBaseResult() {
        return results.get(getBaseSlot());
    }

    /**
     * Represents a compacting tier.
     */
    public static class Result {
        private ItemStack stack;
        private int needed;

        public Result(ItemStack stack, int needed) {
            this.stack = stack.copy();
            this.needed = Math.max(1, needed);
        }

        public ItemStack getStack() {
            return stack;
        }

        public void setStack(ItemStack stack) {
            this.stack = stack.copy();
        }

        public int getNeeded() {
            return needed;
        }

        public void setNeeded(int needed) {
            this.needed = Math.max(1, needed);
        }

        public Result copy() {
            return new Result(stack.copy(), needed);
        }
    }
}
