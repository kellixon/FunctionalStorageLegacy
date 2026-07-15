package com.xinyihl.functionalstoragelegacy.common.inventory;

import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.api.storage.TransferResult;
import com.xinyihl.functionalstoragelegacy.util.ItemUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Large-capacity storage whose visible slots are lossless representations of
 * one shared amount in the lowest-tier unit. Tier definitions and slot reads
 * are immutable snapshots; simulations never configure or mutate storage.
 */
public abstract class CompactingInventoryHandler implements IBigItemHandler {

    private static final String STORAGE_V2 = "StorageV2";
    private static final String BASE_AMOUNT = "BaseAmount";
    private static final String TIERS = "Tiers";
    private static final String INDEX = "Index";
    private static final String STACK = "Stack";
    private static final String BASE_UNITS = "BaseUnits";

    private final Tier[] tiers;
    private long baseAmount;
    private boolean configured;

    protected CompactingInventoryHandler(int slots) {
        tiers = new Tier[Math.max(0, slots)];
        clearConfiguration();
    }

    @Override
    public final int getSlotCount() {
        return tiers.length;
    }

    @Nonnull
    @Override
    public final BigItemStack getSlotSnapshot(int slot) {
        if (!isValidSlot(slot) || !tiers[slot].hasTemplate()) {
            return BigItemStack.empty();
        }
        long amount = isCreative() ? Long.MAX_VALUE : baseAmount / tiers[slot].baseUnits;
        return new BigItemStack(tiers[slot].template, amount);
    }

    @Override
    public final long getSlotCapacity(int slot) {
        if (!isValidSlot(slot) || !tiers[slot].hasTemplate()) {
            return 0L;
        }
        if (hasMaxStorage() || isCreative()) {
            return Long.MAX_VALUE;
        }
        return getTotalBaseCapacity() / tiers[slot].baseUnits;
    }

    @Nonnull
    @Override
    public final TransferResult<BigItemStack> insertIntoSlot(
            int slot, @Nonnull BigItemStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request == null || request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L || !isOperationEnabled() || !isValidSlot(slot)) {
            return emptyResult(requested, action);
        }

        Tier tier = tiers[slot];
        if (!tier.hasTemplate()
                || !ItemUtil.areItemStacksCompatible(
                        tier.template, request.getTemplate(), allowsEquivalentItems())) {
            return emptyResult(requested, action);
        }

        if (isCreative()) {
            return processedResult(request, requested, action);
        }

        long capacity = getTotalBaseCapacity();
        long freeBaseUnits = baseAmount >= capacity ? 0L : capacity - baseAmount;
        long inserted = Math.min(requested, freeBaseUnits / tier.baseUnits);
        long processed = voidsOverflow() ? requested : inserted;

        if (action == StorageAction.EXECUTE && inserted > 0L) {
            baseAmount = saturatedAdd(baseAmount, inserted * tier.baseUnits);
            onChange();
        }
        return processedResult(request, processed, action);
    }

    @Nonnull
    @Override
    public final TransferResult<BigItemStack> extractFromSlot(
            int slot, long amount, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = Math.max(0L, amount);
        if (requested == 0L || !isOperationEnabled()
                || !isValidSlot(slot) || !tiers[slot].hasTemplate()) {
            return emptyResult(requested, action);
        }

        Tier tier = tiers[slot];
        long available = isCreative() ? requested : baseAmount / tier.baseUnits;
        long extracted = Math.min(requested, available);
        if (extracted == 0L) {
            return emptyResult(requested, action);
        }

        if (action == StorageAction.EXECUTE && !isCreative()) {
            baseAmount -= extracted * tier.baseUnits;
            if (baseAmount == 0L && !isLocked()) {
                clearConfiguration();
            }
            onChange();
        }
        return new TransferResult<>(
                requested, new BigItemStack(tier.template, extracted), action);
    }

    /**
     * Replaces compression tiers with detached immutable definitions. Missing
     * entries are padded with empty tiers and extra entries are ignored.
     * Existing base units are preserved when a valid configuration is refreshed.
     */
    public final void configureTiers(@Nonnull List<Tier> newTiers) {
        Objects.requireNonNull(newTiers, "newTiers");
        Tier[] replacement = new Tier[tiers.length];
        boolean hasTemplate = false;
        for (int slot = 0; slot < replacement.length; slot++) {
            Tier tier = slot < newTiers.size() && newTiers.get(slot) != null
                    ? newTiers.get(slot) : Tier.empty();
            replacement[slot] = new Tier(tier.template, tier.baseUnits);
            hasTemplate |= replacement[slot].hasTemplate();
        }

        boolean changed = configured != hasTemplate;
        for (int slot = 0; slot < tiers.length; slot++) {
            changed |= !tiers[slot].sameDefinition(replacement[slot]);
            tiers[slot] = replacement[slot];
        }
        configured = hasTemplate;
        if (!configured && baseAmount != 0L) {
            baseAmount = 0L;
            changed = true;
        }
        if (changed) {
            onChange();
        }
    }

    /** @return an unmodifiable list of detached immutable tier definitions */
    @Nonnull
    public final List<Tier> getTiers() {
        List<Tier> snapshots = new ArrayList<>(tiers.length);
        for (Tier tier : tiers) {
            snapshots.add(new Tier(tier.template, tier.baseUnits));
        }
        return Collections.unmodifiableList(snapshots);
    }

    /** @return whether at least one compression tier has a retained template */
    public final boolean isConfigured() {
        return configured;
    }

    /** @return exact stored amount in lowest-tier units */
    public final long getStoredBaseAmount() {
        return baseAmount;
    }

    /**
     * @return maximum shared amount in lowest-tier units, saturated at long max
     */
    public final long getTotalBaseCapacity() {
        if (hasMaxStorage() || isCreative()) {
            return Long.MAX_VALUE;
        }
        return getTotalBaseCapacity(getMultiplier());
    }

    /**
     * Calculates base-unit capacity for a prospective multiplier without
     * mutating upgrades or contents.
     */
    public final long getTotalBaseCapacity(double multiplier) {
        if (!configured) {
            return 0L;
        }
        long largestUnits = 1L;
        Tier baseTier = null;
        for (Tier tier : tiers) {
            if (!tier.hasTemplate()) {
                continue;
            }
            largestUnits = Math.max(largestUnits, tier.baseUnits);
            if (baseTier == null || tier.baseUnits < baseTier.baseUnits) {
                baseTier = tier;
            }
        }
        if (baseTier == null) {
            return 0L;
        }

        if (Double.isNaN(multiplier) || multiplier <= 0D) {
            return 0L;
        }
        double capacity = multiplier
                * Math.max(0, baseTier.template.getMaxStackSize())
                * (double) largestUnits;
        if (Double.isInfinite(capacity) || capacity >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return capacity <= 0D ? 0L : (long) Math.floor(capacity);
    }

    /** @return whether the configured slot participates in double-click insertion */
    public final boolean canDoubleClickSlot(int slot) {
        return isValidSlot(slot) && (isLocked() || tiers[slot].hasTemplate());
    }

    /**
     * Synchronizes empty tier retention with an externally owned lock flag.
     * Unlocking an empty handler clears its configured tiers; stored base units
     * and their tier definitions are never discarded.
     *
     * @param locked desired lock state
     */
    public final void setLockFilters(boolean locked) {
        if (!locked && !isCreative() && baseAmount == 0L && configured) {
            clearConfiguration();
            onChange();
        }
    }

    /** Serializes only {@code StorageV2.BaseAmount/Tiers}. */
    @Nonnull
    public final NBTTagCompound serializeNBT() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound storage = new NBTTagCompound();
        storage.setLong(BASE_AMOUNT, baseAmount);
        NBTTagList tierList = new NBTTagList();
        for (int slot = 0; slot < tiers.length; slot++) {
            Tier tier = tiers[slot];
            if (!tier.hasTemplate()) {
                continue;
            }
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger(INDEX, slot);
            entry.setTag(STACK, tier.template.writeToNBT(new NBTTagCompound()));
            entry.setLong(BASE_UNITS, tier.baseUnits);
            tierList.appendTag(entry);
        }
        storage.setTag(TIERS, tierList);
        root.setTag(STORAGE_V2, storage);
        return root;
    }

    /**
     * Replaces configuration and contents from the 2.0 schema. Missing
     * {@code StorageV2} means empty storage and legacy compacting keys are ignored.
     */
    public final void deserializeNBT(@Nonnull NBTTagCompound root) {
        clearConfiguration();
        if (root == null || !root.hasKey(STORAGE_V2, Constants.NBT.TAG_COMPOUND)) {
            return;
        }
        NBTTagCompound storage = root.getCompoundTag(STORAGE_V2);
        NBTTagList tierList = storage.getTagList(TIERS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tierList.tagCount(); i++) {
            NBTTagCompound entry = tierList.getCompoundTagAt(i);
            int slot = entry.getInteger(INDEX);
            if (!isValidSlot(slot) || !entry.hasKey(STACK, Constants.NBT.TAG_COMPOUND)) {
                continue;
            }
            ItemStack template = new ItemStack(entry.getCompoundTag(STACK));
            if (template.isEmpty()) {
                continue;
            }
            tiers[slot] = new Tier(template, Math.max(1L, entry.getLong(BASE_UNITS)));
            configured = true;
        }
        baseAmount = configured ? Math.max(0L, storage.getLong(BASE_AMOUNT)) : 0L;
        if (baseAmount == 0L && !isLocked() && !isCreative()) {
            clearConfiguration();
        }
    }

    /** Called once after an executed operation changes observable state. */
    public abstract void onChange();

    /** @return the compacting storage multiplier */
    public abstract double getMultiplier();

    protected boolean allowsEquivalentItems() {
        return false;
    }

    protected boolean hasMaxStorage() {
        return false;
    }

    /** @return whether this handler's owning container currently allows transactions */
    protected boolean isOperationEnabled() {
        return true;
    }

    private boolean isValidSlot(int slot) {
        return slot >= 0 && slot < tiers.length;
    }

    private void clearConfiguration() {
        for (int slot = 0; slot < tiers.length; slot++) {
            tiers[slot] = Tier.empty();
        }
        baseAmount = 0L;
        configured = false;
    }

    private static long saturatedAdd(long left, long right) {
        return left >= Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static TransferResult<BigItemStack> emptyResult(
            long requested, StorageAction action) {
        return new TransferResult<>(requested, BigItemStack.empty(), action);
    }

    private static TransferResult<BigItemStack> processedResult(
            BigItemStack request, long processed, StorageAction action) {
        return new TransferResult<>(
                request.getAmount(),
                processed == 0L ? BigItemStack.empty() : request.withAmount(processed),
                action);
    }

    /** Immutable definition of one visible compression tier. */
    public static final class Tier {
        private static final Tier EMPTY = new Tier(ItemStack.EMPTY, 1L);

        private final ItemStack template;
        private final long baseUnits;

        public Tier(@Nonnull ItemStack template, long baseUnits) {
            this.template = normalize(template);
            this.baseUnits = Math.max(1L, baseUnits);
        }

        @Nonnull
        public static Tier empty() {
            return EMPTY;
        }

        @Nonnull
        public ItemStack getTemplate() {
            return template.isEmpty() ? ItemStack.EMPTY : template.copy();
        }

        public long getBaseUnits() {
            return baseUnits;
        }

        public boolean hasTemplate() {
            return !template.isEmpty();
        }

        private boolean sameDefinition(Tier other) {
            return baseUnits == other.baseUnits
                    && ItemUtil.areItemStacksEqual(template, other.template);
        }

        @Nonnull
        private static ItemStack normalize(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack copy = stack.copy();
            copy.setCount(1);
            return copy;
        }
    }
}
