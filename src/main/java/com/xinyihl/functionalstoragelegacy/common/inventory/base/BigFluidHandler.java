package com.xinyihl.functionalstoragelegacy.common.inventory.base;

import com.xinyihl.functionalstoragelegacy.api.storage.BigFluidStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigFluidHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.api.storage.TransferResult;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Fixed-size large fluid storage backed by independent immutable tank state.
 * Forge's mutable {@link FluidStack#amount} is used only on copied type
 * templates at the API and NBT boundaries; the authoritative amount is a
 * {@code long}. Instances follow their owning tile's threading model and are
 * not independently thread-safe.
 */
public abstract class BigFluidHandler implements IBigFluidHandler {

    private static final String STORAGE_V2 = "StorageV2";
    private static final String TANKS = "Tanks";
    private static final String INDEX = "Index";
    private static final String FLUID = "Fluid";
    private static final String AMOUNT = "Amount";
    private static final String FILTER = "Filter";

    private final TankState[] states;

    protected BigFluidHandler(int tankCount) {
        states = new TankState[Math.max(0, tankCount)];
        clearStates();
    }

    @Override
    public final int getTankCount() {
        return states.length;
    }

    @Nonnull
    @Override
    public final BigFluidStack getTankSnapshot(int tank) {
        if (!isValidTank(tank)) {
            return BigFluidStack.empty();
        }
        TankState state = states[tank];
        if (state.template != null && state.amount > 0L) {
            return new BigFluidStack(
                    state.template, isCreative() ? Long.MAX_VALUE : state.amount);
        }
        return state.filter == null
                ? BigFluidStack.empty() : new BigFluidStack(state.filter, 0L);
    }

    @Override
    public final long getTankCapacity(int tank) {
        return isValidTank(tank) ? capacityPerTank() : 0L;
    }

    @Override
    public final boolean supportsFill(int tank) {
        return isOperationEnabled() && isValidTank(tank);
    }

    @Override
    public final boolean supportsDrain(int tank) {
        return isOperationEnabled() && isValidTank(tank);
    }

    @Override
    public final boolean supportsFluid(int tank, @Nonnull BigFluidStack fluid) {
        if (!isOperationEnabled() || !isValidTank(tank)
                || fluid == null || !fluid.hasTemplate()) {
            return false;
        }
        FluidStack candidate = fluid.getTemplate();
        TankState state = states[tank];
        FluidStack configured = state.template != null && state.amount > 0L
                ? state.template : state.filter;
        return configured == null || configured.isFluidEqual(candidate);
    }

    @Nonnull
    @Override
    public final TransferResult<BigFluidStack> fillTank(
            int tank, @Nonnull BigFluidStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request == null || request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L || !isOperationEnabled() || !isValidTank(tank)) {
            return emptyResult(requested, action);
        }

        TankState current = states[tank];
        FluidStack incoming = request.getTemplate();
        FluidStack configured = current.template != null && current.amount > 0L
                ? current.template : current.filter;
        if (configured != null && !configured.isFluidEqual(incoming)) {
            return emptyResult(requested, action);
        }

        FluidStack selectedTemplate = current.template == null ? incoming : current.template;
        FluidStack selectedFilter = current.filter;
        if (isLocked() && selectedFilter == null) {
            selectedFilter = configured == null ? incoming : configured;
        }

        if (isCreative()) {
            if (action == StorageAction.EXECUTE
                    && (current.template == null
                    || current.amount != Long.MAX_VALUE
                    || !sameFluid(current.filter, selectedFilter))) {
                states[tank] = new TankState(selectedTemplate, Long.MAX_VALUE, selectedFilter);
                onChange();
            }
            return processedResult(request, requested, action);
        }

        long capacity = capacityPerTank();
        long insertable = current.amount >= capacity ? 0L : capacity - current.amount;
        long inserted = Math.min(requested, insertable);
        long processed = voidsOverflow() ? requested : inserted;

        if (action == StorageAction.EXECUTE && inserted > 0L) {
            states[tank] = new TankState(
                    selectedTemplate,
                    saturatedAdd(current.amount, inserted),
                    selectedFilter);
            onChange();
        }
        return processedResult(request, processed, action);
    }

    @Nonnull
    @Override
    public final TransferResult<BigFluidStack> drainTank(
            int tank, long amount, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = Math.max(0L, amount);
        if (requested == 0L || !isOperationEnabled() || !isValidTank(tank)) {
            return emptyResult(requested, action);
        }

        TankState current = states[tank];
        if (current.template == null || current.amount == 0L) {
            return emptyResult(requested, action);
        }

        long drained = isCreative() ? requested : Math.min(requested, current.amount);
        if (drained == 0L) {
            return emptyResult(requested, action);
        }

        if (action == StorageAction.EXECUTE && !isCreative()) {
            long remaining = current.amount - drained;
            FluidStack retainedFilter = isLocked()
                    ? (current.filter == null ? current.template : current.filter)
                    : null;
            states[tank] = remaining == 0L
                    ? new TankState(null, 0L, retainedFilter)
                    : new TankState(current.template, remaining, retainedFilter);
            onChange();
        }
        return new TransferResult<>(
                requested, new BigFluidStack(current.template, drained), action);
    }

    /**
     * Synchronizes retained filters with an externally owned lock flag. Locking
     * captures populated tank types; unlocking clears every retained filter.
     * One change notification is emitted only when filter state actually changes.
     *
     * @param locked desired lock state
     */
    public final void setLockFilters(boolean locked) {
        boolean changed = false;
        for (int tank = 0; tank < states.length; tank++) {
            TankState current = states[tank];
            FluidStack replacement = locked
                    ? (current.template == null ? current.filter : current.template)
                    : null;
            if (!sameFluid(current.filter, replacement)) {
                states[tank] = new TankState(current.template, current.amount, replacement);
                changed = true;
            }
        }
        if (changed) {
            onChange();
        }
    }

    /**
     * Returns a defensive copy of a retained tank filter.
     *
     * @param tank tank index
     * @return normalized filter copy, or {@code null}
     */
    @Nullable
    public final FluidStack getTankFilter(int tank) {
        if (!isValidTank(tank) || states[tank].filter == null) {
            return null;
        }
        return states[tank].filter.copy();
    }

    /**
     * Serializes only the 2.0 schema. Fluid templates, long amounts, and locked
     * filters are stored independently under {@code StorageV2.Tanks}.
     *
     * @return a new detached root compound
     */
    @Nonnull
    public final NBTTagCompound serializeNBT() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound storage = new NBTTagCompound();
        NBTTagList tanks = new NBTTagList();
        for (int tank = 0; tank < states.length; tank++) {
            TankState state = states[tank];
            if ((state.template == null || state.amount == 0L) && state.filter == null) {
                continue;
            }
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger(INDEX, tank);
            if (state.template != null && state.amount > 0L) {
                entry.setTag(FLUID, state.template.writeToNBT(new NBTTagCompound()));
            }
            entry.setLong(AMOUNT, state.amount);
            if (state.filter != null) {
                entry.setTag(FILTER, state.filter.writeToNBT(new NBTTagCompound()));
            }
            tanks.appendTag(entry);
        }
        storage.setTag(TANKS, tanks);
        root.setTag(STORAGE_V2, storage);
        return root;
    }

    /**
     * Replaces all state from the 2.0 schema. Missing {@code StorageV2}
     * deliberately means empty storage; legacy {@code Tank_*} and
     * {@code FluidInv} keys are never read.
     *
     * @param root serialized root, or {@code null} to clear
     */
    public final void deserializeNBT(@Nullable NBTTagCompound root) {
        clearStates();
        if (root == null || !root.hasKey(STORAGE_V2, Constants.NBT.TAG_COMPOUND)) {
            return;
        }
        NBTTagList tanks = root.getCompoundTag(STORAGE_V2)
                .getTagList(TANKS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tanks.tagCount(); i++) {
            NBTTagCompound entry = tanks.getCompoundTagAt(i);
            int tank = entry.getInteger(INDEX);
            if (!isValidTank(tank)) {
                continue;
            }
            long amount = Math.max(0L, entry.getLong(AMOUNT));
            FluidStack template = entry.hasKey(FLUID, Constants.NBT.TAG_COMPOUND)
                    ? normalize(FluidStack.loadFluidStackFromNBT(entry.getCompoundTag(FLUID)))
                    : null;
            if (template == null || amount == 0L) {
                template = null;
                amount = 0L;
            }
            FluidStack filter = entry.hasKey(FILTER, Constants.NBT.TAG_COMPOUND)
                    ? normalize(FluidStack.loadFluidStackFromNBT(entry.getCompoundTag(FILTER)))
                    : null;
            if (!isLocked()) {
                filter = null;
            } else if (template != null
                    && (filter == null || !template.isFluidEqual(filter))) {
                filter = template;
            }
            states[tank] = new TankState(template, amount, filter);
        }
    }

    /** Called once after an executed operation changes observable state. */
    public abstract void onChange();

    /** @return current per-tank capacity in buckets before conversion to mB */
    public abstract double getMultiplier();

    /** @return whether finite capacity is replaced with {@link Long#MAX_VALUE} */
    protected boolean hasMaxStorage() {
        return false;
    }

    /** @return whether this handler's owning container currently allows transactions */
    protected boolean isOperationEnabled() {
        return true;
    }

    private long capacityPerTank() {
        if (hasMaxStorage() || isCreative()) {
            return Long.MAX_VALUE;
        }
        double multiplier = getMultiplier();
        if (Double.isNaN(multiplier) || multiplier <= 0D) {
            return 0L;
        }
        double capacity = multiplier * 1000D;
        if (Double.isInfinite(capacity) || capacity >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return capacity <= 0D ? 0L : (long) Math.floor(capacity);
    }

    private boolean isValidTank(int tank) {
        return tank >= 0 && tank < states.length;
    }

    private void clearStates() {
        for (int i = 0; i < states.length; i++) {
            states[i] = TankState.EMPTY;
        }
    }

    @Nullable
    private static FluidStack normalize(@Nullable FluidStack stack) {
        if (stack == null) {
            return null;
        }
        FluidStack copy = stack.copy();
        copy.amount = 1;
        return copy;
    }

    private static boolean sameFluid(
            @Nullable FluidStack left, @Nullable FluidStack right) {
        return left == null ? right == null : right != null && left.isFluidEqual(right);
    }

    private static long saturatedAdd(long left, long right) {
        return left >= Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static TransferResult<BigFluidStack> emptyResult(
            long requested, StorageAction action) {
        return new TransferResult<>(requested, BigFluidStack.empty(), action);
    }

    private static TransferResult<BigFluidStack> processedResult(
            BigFluidStack request, long processed, StorageAction action) {
        return new TransferResult<>(
                request.getAmount(),
                processed == 0L ? BigFluidStack.empty() : request.withAmount(processed),
                action);
    }

    private static final class TankState {
        private static final TankState EMPTY = new TankState(null, 0L, null);

        @Nullable
        private final FluidStack template;
        private final long amount;
        @Nullable
        private final FluidStack filter;

        private TankState(
                @Nullable FluidStack template,
                long amount,
                @Nullable FluidStack filter) {
            FluidStack normalizedTemplate = normalize(template);
            long normalizedAmount = normalizedTemplate == null ? 0L : Math.max(0L, amount);
            this.template = normalizedAmount == 0L ? null : normalizedTemplate;
            this.amount = normalizedAmount;
            this.filter = normalize(filter);
        }
    }
}
