package com.xinyihl.functionalstoragelegacy.api.storage;

import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Immutable item snapshot composed of a defensively copied type template and
 * a long amount. A present template is always normalized to a count of one.
 * A zero amount may retain a template to represent a locked filter; negative
 * amounts are clamped to zero. An absent resource is normalized to
 * {@link #empty()}.
 */
public final class BigItemStack implements StorageSnapshot<BigItemStack, ItemStorageKey> {

    private static final BigItemStack EMPTY = new BigItemStack();

    private final ItemStack template;
    @Nullable
    private final ItemStorageKey key;
    private final long amount;

    private BigItemStack() {
        this.template = ItemStack.EMPTY;
        this.key = null;
        this.amount = 0L;
    }

    /**
     * Creates a snapshot. The supplied stack is copied immediately and is
     * never retained by reference.
     *
     * @param template item type, metadata, capabilities, and NBT to represent
     * @param amount represented amount; negative values are clamped to zero
     */
    public BigItemStack(@Nullable ItemStack template, long amount) {
        if (template == null || template.isEmpty()) {
            this.template = ItemStack.EMPTY;
            this.key = null;
            this.amount = 0L;
            return;
        }
        this.template = template.copy();
        this.template.setCount(1);
        this.key = new ItemStorageKey(this.template);
        this.amount = Math.max(0L, amount);
    }

    /**
     * @return the shared immutable empty snapshot
     */
    @Nonnull
    public static BigItemStack empty() {
        return EMPTY;
    }

    /**
     * Returns a fresh copy of the normalized template. Mutating the returned
     * stack cannot alter this snapshot.
     *
     * @return a count-one template, or {@link ItemStack#EMPTY}
     */
    @Nonnull
    public ItemStack getTemplate() {
        return template.isEmpty() ? ItemStack.EMPTY : template.copy();
    }

    /** @return immutable exact item key, or {@code null} when unconfigured */
    @Nullable
    @Override
    public ItemStorageKey getKey() {
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
     * Creates the same item snapshot with a different amount. The template is
     * copied again; a non-positive amount produces a typed zero snapshot when
     * this snapshot has a retained template.
     *
     * @param newAmount new represented amount
     * @return an immutable snapshot with the requested amount
     */
    @Nonnull
    @Override
    public BigItemStack withAmount(long newAmount) {
        return template.isEmpty() ? empty() : new BigItemStack(template, newAmount);
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
     * Compares item, metadata, and stack NBT while ignoring count.
     *
     * @param other other snapshot
     * @return {@code true} when both snapshots carry the same item template
     */
    @Override
    public boolean isSameType(@Nullable BigItemStack other) {
        return StorageSnapshot.super.isSameType(other);
    }

    /**
     * Compares item, metadata, and stack NBT while ignoring count.
     *
     * @param other other stack
     * @return {@code true} when this snapshot and the stack represent the same item type
     */
    public boolean isSameType(@Nullable ItemStack other) {
        return hasTemplate() && other != null && !other.isEmpty()
                && ItemStack.areItemsEqual(template, other)
                && ItemStack.areItemStackTagsEqual(template, other);
    }

    /**
     * Converts this snapshot to Forge's int-count representation. Amounts
     * above {@link Integer#MAX_VALUE} are saturated at that boundary.
     *
     * @return a fresh mutable Forge stack, or {@link ItemStack#EMPTY}
     */
    @Nonnull
    public ItemStack toItemStack() {
        if (isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = template.copy();
        result.setCount(amount >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount);
        return result;
    }
}
