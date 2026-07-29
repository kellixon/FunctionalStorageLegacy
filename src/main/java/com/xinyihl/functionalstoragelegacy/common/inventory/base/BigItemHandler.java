package com.xinyihl.functionalstoragelegacy.common.inventory.base;

import com.xinyihl.functionalstoragelegacy.api.storage.*;
import com.xinyihl.functionalstoragelegacy.util.ItemUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Base implementation for a fixed number of large-capacity item slots.
 * Mutable item stacks never escape this class; every state transition replaces
 * an immutable internal slot value and public reads return detached snapshots.
 */
public abstract class BigItemHandler implements IBigItemHandler {

    private static final String STORAGE_V2 = "StorageV2";
    private static final String ITEMS = "Items";
    private static final String INDEX = "Index";
    private static final String STACK = "Stack";
    private static final String AMOUNT = "Amount";

    private final SlotState[] states;
    private final StorageChangeDispatcher<BigItemStack, ItemStorageKey> changeDispatcher = new StorageChangeDispatcher<>();

    protected BigItemHandler(int slots) {
        this.states = new SlotState[Math.max(0, slots)];
        Arrays.fill(states, SlotState.EMPTY);
    }

    private static boolean sameStates(SlotState[] left, SlotState[] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int slot = 0; slot < left.length; slot++) {
            if (left[slot].amount != right[slot].amount || !ItemUtil.areItemStacksEqual(left[slot].template, right[slot].template)) {
                return false;
            }
        }
        return true;
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

    private static long saturatedAdd(long left, long right) {
        return left >= Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static TransferResult<BigItemStack, ItemStorageKey> emptyResult(long requested, StorageAction action) {
        return new TransferResult<>(requested, BigItemStack.empty(), action);
    }

    private static TransferResult<BigItemStack, ItemStorageKey> processedResult(BigItemStack request, long processed, StorageAction action) {
        return new TransferResult<>(request.getAmount(), processed == 0L ? BigItemStack.empty() : request.withAmount(processed), action);
    }

    @Override
    public final int getStorageCount() {
        return states.length;
    }

    @Nonnull
    @Override
    public final BigItemStack getSnapshot(int slot) {
        if (!isValidSlot(slot)) {
            return BigItemStack.empty();
        }
        SlotState state = states[slot];
        if (state.template.isEmpty()) {
            return BigItemStack.empty();
        }
        long visibleAmount = isCreative() ? Long.MAX_VALUE : state.amount;
        return new BigItemStack(state.template, visibleAmount);
    }

    @Override
    public final long getCapacity(int slot) {
        if (!isValidSlot(slot)) {
            return 0L;
        }
        return capacityFor(states[slot].template);
    }

    @Nonnull
    @Override
    public final TransferResult<BigItemStack, ItemStorageKey> insert(int slot, @Nonnull BigItemStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L || !isOperationEnabled() || !isValidSlot(slot)) {
            return emptyResult(requested, action);
        }

        ItemStack incoming = request.getTemplate();
        SlotState current = states[slot];
        boolean unconfigured = current.template.isEmpty();
        if ((unconfigured && isLocked()) || (!unconfigured && !ItemUtil.areItemStacksCompatible(current.template, incoming, allowsEquivalentItems()))) {
            return emptyResult(requested, action);
        }

        if (isCreative()) {
            if (action == StorageAction.EXECUTE && unconfigured) {
                BigItemStack before = getSnapshot(slot);
                states[slot] = new SlotState(incoming, Long.MAX_VALUE);
                onChange(StorageChange.delta(slot, before, getSnapshot(slot)));
            }
            return processedResult(request, requested, action);
        }

        ItemStack capacityTemplate = unconfigured ? incoming : current.template;
        long capacity = capacityFor(capacityTemplate);
        long insertable = current.amount >= capacity ? 0L : capacity - current.amount;
        long inserted = Math.min(requested, insertable);
        long processed = voidsOverflow() ? requested : inserted;

        if (action == StorageAction.EXECUTE && inserted > 0L) {
            BigItemStack before = getSnapshot(slot);
            states[slot] = new SlotState(unconfigured ? incoming : current.template, saturatedAdd(current.amount, inserted));
            onChange(StorageChange.delta(slot, before, getSnapshot(slot)));
        }
        return processedResult(request, processed, action);
    }

    @Nonnull
    @Override
    public final TransferResult<BigItemStack, ItemStorageKey> extract(int slot, long amount, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = Math.max(0L, amount);
        if (requested == 0L || !isOperationEnabled() || !isValidSlot(slot)) {
            return emptyResult(requested, action);
        }

        SlotState current = states[slot];
        if (current.template.isEmpty()) {
            return emptyResult(requested, action);
        }

        long extracted = isCreative() ? requested : Math.min(requested, current.amount);
        if (extracted == 0L) {
            return emptyResult(requested, action);
        }

        if (action == StorageAction.EXECUTE && !isCreative()) {
            BigItemStack before = getSnapshot(slot);
            long remaining = current.amount - extracted;
            ItemStack retainedTemplate = remaining == 0L && !isLocked() ? ItemStack.EMPTY : current.template;
            states[slot] = new SlotState(retainedTemplate, remaining);
            onChange(StorageChange.delta(slot, before, getSnapshot(slot)));
        }
        return new TransferResult<>(requested, new BigItemStack(current.template, extracted), action);
    }

    /**
     * Installs or clears a retained slot filter without exposing mutable slot
     * state. A populated slot can only keep its existing compatible type.
     *
     * @param slot   target slot
     * @param filter desired item type, or an empty stack to clear an empty slot
     * @return whether the requested filter is valid for the current contents
     */
    public final boolean setSlotFilter(int slot, @Nonnull ItemStack filter) {
        if (!isValidSlot(slot)) {
            return false;
        }
        SlotState current = states[slot];
        ItemStack normalized = normalize(filter);
        if (current.amount > 0L && (normalized.isEmpty() || !ItemUtil.areItemStacksCompatible(current.template, normalized, allowsEquivalentItems()))) {
            return false;
        }
        ItemStack replacement = current.amount > 0L ? current.template : normalized;
        if (ItemUtil.areItemStacksEqual(current.template, replacement)) {
            return true;
        }
        BigItemStack before = getSnapshot(slot);
        states[slot] = new SlotState(replacement, current.amount);
        onChange(StorageChange.delta(slot, before, getSnapshot(slot)));
        return true;
    }

    /**
     * Synchronizes retained zero-amount filters with an externally owned lock
     * flag. Unlocking clears only empty filters; populated slots are untouched.
     * One change notification is emitted when any filter is removed.
     *
     * @param locked desired lock state
     */
    public final void setLockFilters(boolean locked) {
        if (locked) {
            return;
        }
        List<StorageChange.Entry<BigItemStack, ItemStorageKey>> entries = new ArrayList<>();
        for (int slot = 0; slot < states.length; slot++) {
            SlotState current = states[slot];
            if (current.amount == 0L && !current.template.isEmpty()) {
                BigItemStack before = getSnapshot(slot);
                states[slot] = SlotState.EMPTY;
                entries.add(new StorageChange.Entry<>(slot, before, getSnapshot(slot)));
            }
        }
        if (!entries.isEmpty()) {
            onChange(StorageChange.delta(entries));
        }
    }

    /**
     * Applies filter retention for a user-visible lock transition and emits
     * exactly one full-resynchronization event. Callers must invoke this only
     * after the external lock flag actually changes.
     */
    public final void applyLockConfiguration(boolean locked) {
        if (!locked) {
            clearEmptyFiltersSilently();
        }
        onChange(StorageChange.reset());
    }

    /**
     * Serializes only the 2.0 storage schema. Filters are retained even when
     * their stored amount is zero.
     */
    @Nonnull
    public final NBTTagCompound serializeNBT() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound storage = new NBTTagCompound();
        NBTTagList items = new NBTTagList();
        for (int slot = 0; slot < states.length; slot++) {
            SlotState state = states[slot];
            if (state.template.isEmpty()) {
                continue;
            }
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger(INDEX, slot);
            entry.setTag(STACK, state.template.writeToNBT(new NBTTagCompound()));
            entry.setLong(AMOUNT, state.amount);
            items.appendTag(entry);
        }
        storage.setTag(ITEMS, items);
        root.setTag(STORAGE_V2, storage);
        return root;
    }

    /**
     * Replaces all contents from the 2.0 schema. Missing {@code StorageV2}
     * means empty storage; legacy item keys are deliberately ignored.
     */
    public final void deserializeNBT(@Nonnull NBTTagCompound root) {
        SlotState[] before = states.clone();
        Arrays.fill(states, SlotState.EMPTY);
        if (root.hasKey(STORAGE_V2, Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound storage = root.getCompoundTag(STORAGE_V2);
            NBTTagList items = storage.getTagList(ITEMS, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < items.tagCount(); i++) {
                NBTTagCompound entry = items.getCompoundTagAt(i);
                int slot = entry.getInteger(INDEX);
                if (!isValidSlot(slot) || !entry.hasKey(STACK, Constants.NBT.TAG_COMPOUND)) {
                    continue;
                }
                ItemStack template = normalize(new ItemStack(entry.getCompoundTag(STACK)));
                if (template.isEmpty()) {
                    continue;
                }
                long amount = Math.max(0L, entry.getLong(AMOUNT));
                if (amount > 0L || isLocked()) {
                    states[slot] = new SlotState(template, amount);
                }
            }
        }
        if (changeDispatcher.hasSubscribers() && !sameStates(before, states)) {
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

    /**
     * @return whether ore-dictionary-equivalent items share a configured slot
     */
    protected boolean allowsEquivalentItems() {
        return false;
    }

    /**
     * @return whether finite capacity is replaced with {@link Long#MAX_VALUE}
     */
    protected boolean hasMaxStorage() {
        return false;
    }

    /**
     * @return whether this handler's owning container currently allows transactions
     */
    protected boolean isOperationEnabled() {
        return true;
    }

    private long capacityFor(ItemStack template) {
        if (hasMaxStorage() || isCreative()) {
            return Long.MAX_VALUE;
        }
        double multiplier = getMultiplier();
        if (Double.isNaN(multiplier) || multiplier <= 0D) {
            return 0L;
        }
        int maxStackSize = template.isEmpty() ? 64 : Math.max(0, template.getMaxStackSize());
        double capacity = multiplier * maxStackSize;
        if (Double.isInfinite(capacity) || capacity >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return capacity <= 0D ? 0L : (long) Math.floor(capacity);
    }

    private boolean isValidSlot(int slot) {
        return slot >= 0 && slot < states.length;
    }

    private void clearEmptyFiltersSilently() {
        for (int slot = 0; slot < states.length; slot++) {
            SlotState current = states[slot];
            if (current.amount == 0L && !current.template.isEmpty()) {
                states[slot] = SlotState.EMPTY;
            }
        }
    }

    private static final class SlotState {
        private static final SlotState EMPTY = new SlotState(ItemStack.EMPTY, 0L);

        private final ItemStack template;
        private final long amount;

        private SlotState(ItemStack template, long amount) {
            this.template = normalize(Objects.requireNonNull(template, "template"));
            this.amount = Math.max(0L, amount);
        }
    }
}
