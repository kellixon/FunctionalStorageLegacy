package com.xinyihl.functionalstoragelegacy.common.storage;

import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import java.util.Objects;

/** Immutable material selection for the three visible parts of a framed drawer. */
public final class FramedDrawerStyle {

    public static final String NBT_KEY = "FramedStyle";
    public static final FramedDrawerStyle EMPTY = new FramedDrawerStyle(
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);

    private static final String EXTERIOR = "Exterior";
    private static final String FRONT = "Front";
    private static final String DIVIDER = "Divider";

    private final ItemStack exterior;
    private final ItemStack front;
    private final ItemStack divider;
    private final String cacheKey;

    public FramedDrawerStyle(ItemStack exterior, ItemStack front, ItemStack divider) {
        this.exterior = normalize(exterior);
        this.front = normalize(front);
        this.divider = normalize(divider);
        this.cacheKey = writeToNBT().toString();
    }

    public boolean isConfigured() {
        return !exterior.isEmpty() && !front.isEmpty();
    }

    @Nonnull
    public ItemStack getExterior() {
        return exterior.copy();
    }

    @Nonnull
    public ItemStack getFront() {
        return front.copy();
    }

    /** An omitted divider follows the exterior material, matching the modern implementation. */
    @Nonnull
    public ItemStack getDivider() {
        return (divider.isEmpty() ? exterior : divider).copy();
    }

    public String getCacheKey() {
        return cacheKey;
    }

    @Nonnull
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        writeStack(tag, EXTERIOR, exterior);
        writeStack(tag, FRONT, front);
        writeStack(tag, DIVIDER, divider);
        return tag;
    }

    @Nonnull
    public static FramedDrawerStyle fromNBT(NBTTagCompound tag) {
        if (tag == null || tag.isEmpty()) {
            return EMPTY;
        }
        FramedDrawerStyle style = new FramedDrawerStyle(
                readStack(tag, EXTERIOR),
                readStack(tag, FRONT),
                readStack(tag, DIVIDER));
        return style.isConfigured() ? style : EMPTY;
    }

    @Nonnull
    public static FramedDrawerStyle fromDrawerStack(ItemStack drawer) {
        if (drawer.isEmpty() || !drawer.hasTagCompound()) {
            return EMPTY;
        }
        NBTTagCompound root = drawer.getTagCompound();
        if (!root.hasKey("TileData", Constants.NBT.TAG_COMPOUND)) {
            return EMPTY;
        }
        NBTTagCompound tileData = root.getCompoundTag("TileData");
        if (!tileData.hasKey(NBT_KEY, Constants.NBT.TAG_COMPOUND)) {
            return EMPTY;
        }
        return fromNBT(tileData.getCompoundTag(NBT_KEY));
    }

    public void applyToDrawerStack(ItemStack drawer) {
        if (drawer.isEmpty() || !isConfigured()) {
            return;
        }
        if (!drawer.hasTagCompound()) {
            drawer.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound root = drawer.getTagCompound();
        NBTTagCompound tileData = root.hasKey("TileData", Constants.NBT.TAG_COMPOUND)
                ? root.getCompoundTag("TileData") : new NBTTagCompound();
        tileData.setTag(NBT_KEY, writeToNBT());
        root.setTag("TileData", tileData);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof FramedDrawerStyle)) return false;
        FramedDrawerStyle other = (FramedDrawerStyle) object;
        return ItemStack.areItemStacksEqual(exterior, other.exterior)
                && ItemStack.areItemStacksEqual(front, other.front)
                && ItemStack.areItemStacksEqual(divider, other.divider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cacheKey);
    }

    @Override
    public String toString() {
        return cacheKey;
    }

    private static ItemStack normalize(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ItemBlock)) {
            return ItemStack.EMPTY;
        }
        ItemStack normalized = stack.copy();
        normalized.setCount(1);
        return normalized;
    }

    private static void writeStack(NBTTagCompound parent, String key, ItemStack stack) {
        if (!stack.isEmpty()) {
            parent.setTag(key, stack.writeToNBT(new NBTTagCompound()));
        }
    }

    private static ItemStack readStack(NBTTagCompound parent, String key) {
        if (!parent.hasKey(key, Constants.NBT.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(parent.getCompoundTag(key));
    }
}
