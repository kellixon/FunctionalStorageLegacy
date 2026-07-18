package com.xinyihl.functionalstoragelegacy.api.storage;

import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Immutable fluid snapshot composed of a defensively copied type template and
 * a long amount. A present template is always normalized to one millibucket.
 * A zero amount may retain a template to represent a locked filter; negative
 * amounts are clamped to zero. An absent resource is normalized to
 * {@link #empty()}.
 */
public final class BigFluidStack implements StorageSnapshot<BigFluidStack, FluidStorageKey> {

    private static final BigFluidStack EMPTY = new BigFluidStack();

    @Nullable
    private final FluidStack template;
    @Nullable
    private final FluidStorageKey key;
    private final long amount;

    private BigFluidStack() {
        this.template = null;
        this.key = null;
        this.amount = 0L;
    }

    /**
     * Creates a snapshot. The supplied stack and its NBT are copied
     * immediately and are never retained by reference.
     *
     * @param template fluid type and NBT to represent; {@code null} is empty
     * @param amount   represented amount; negative values are clamped to zero
     */
    public BigFluidStack(@Nullable FluidStack template, long amount) {
        if (template == null) {
            this.template = null;
            this.key = null;
            this.amount = 0L;
            return;
        }
        this.template = template.copy();
        this.template.amount = 1;
        this.key = new FluidStorageKey(this.template);
        this.amount = Math.max(0L, amount);
    }

    /**
     * @return the shared immutable empty snapshot
     */
    @Nonnull
    public static BigFluidStack empty() {
        return EMPTY;
    }

    /**
     * Returns a fresh copy of the normalized template. Mutating the returned
     * stack or its NBT cannot alter this snapshot.
     *
     * @return a one-millibucket template, or {@code null}
     */
    @Nullable
    public FluidStack getTemplate() {
        return template == null ? null : template.copy();
    }

    /**
     * @return immutable exact fluid key, or {@code null} when unconfigured
     */
    @Nullable
    @Override
    public FluidStorageKey getKey() {
        return key;
    }

    /**
     * @return the represented amount
     */
    @Override
    public long getAmount() {
        return amount;
    }

    /**
     * Creates the same fluid snapshot with a different amount. The template is
     * copied again; a non-positive amount produces a typed zero snapshot when
     * this snapshot has a retained template.
     *
     * @param newAmount new represented amount
     * @return an immutable snapshot with the requested amount
     */
    @Nonnull
    @Override
    public BigFluidStack withAmount(long newAmount) {
        return template == null ? empty() : new BigFluidStack(template, newAmount);
    }

    /**
     * Distinguishes an unfiltered empty snapshot from a zero-amount snapshot
     * retaining a locked type.
     *
     * @return whether this snapshot carries a resource template
     */
    @Override
    public boolean hasTemplate() {
        return key != null;
    }

    /**
     * Compares fluid identity and NBT while ignoring amount.
     *
     * @param other other snapshot
     * @return {@code true} when both snapshots carry the same fluid template
     */
    @Override
    public boolean isSameType(@Nullable BigFluidStack other) {
        return StorageSnapshot.super.isSameType(other);
    }

    /**
     * Compares fluid identity and NBT while ignoring amount.
     *
     * @param other other Forge fluid stack
     * @return {@code true} when this snapshot and the stack represent the same fluid type
     */
    public boolean isSameType(@Nullable FluidStack other) {
        return template != null && other != null && template.isFluidEqual(other);
    }

    /**
     * Converts this snapshot to Forge's int-amount representation. Amounts
     * above {@link Integer#MAX_VALUE} are saturated at that boundary.
     *
     * @return a fresh mutable Forge stack, or {@code null}
     */
    @Nullable
    public FluidStack toFluidStack() {
        if (isEmpty()) {
            return null;
        }
        FluidStack result = template.copy();
        result.amount = amount >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
        return result;
    }
}
