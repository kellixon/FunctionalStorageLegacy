package com.xinyihl.functionalstoragelegacy.common.tile;

import com.xinyihl.functionalstoragelegacy.common.block.DrawerWoodType;
import com.xinyihl.functionalstoragelegacy.common.storage.DrawerLayout;
import com.xinyihl.functionalstoragelegacy.common.storage.FramedDrawerStyle;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;

/**
 * Standard long-capacity item drawer with an additional persisted framed style.
 */
public class FramedDrawerTile extends WoodDrawerTile {

    private FramedDrawerStyle style = FramedDrawerStyle.EMPTY;

    public FramedDrawerTile() {
        this(DrawerLayout.X_1);
    }

    public FramedDrawerTile(DrawerLayout layout) {
        super(layout, DrawerWoodType.OAK);
    }

    @Nonnull
    public FramedDrawerStyle getStyle() {
        return style;
    }

    public void setStyle(FramedDrawerStyle style) {
        this.style = style == null ? FramedDrawerStyle.EMPTY : style;
        markDirty();
        sendUpdatePacket();
    }

    @Override
    protected void writeCustomData(NBTTagCompound nbt) {
        super.writeCustomData(nbt);
        writeStyle(nbt);
    }

    @Override
    protected void readCustomData(NBTTagCompound nbt) {
        super.readCustomData(nbt);
        readStyle(nbt);
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound compound) {
        compound = super.writeToNBT(compound);
        writeStyle(compound);
        return compound;
    }

    @Override
    public void readFromNBT(@Nonnull NBTTagCompound compound) {
        super.readFromNBT(compound);
        readStyle(compound);
    }

    @Override
    public boolean isEverythingEmpty() {
        return !style.isConfigured() && super.isEverythingEmpty();
    }

    private void writeStyle(NBTTagCompound nbt) {
        if (style.isConfigured()) {
            nbt.setTag(FramedDrawerStyle.NBT_KEY, style.writeToNBT());
        } else {
            nbt.removeTag(FramedDrawerStyle.NBT_KEY);
        }
    }

    private void readStyle(NBTTagCompound nbt) {
        style = nbt.hasKey(FramedDrawerStyle.NBT_KEY, Constants.NBT.TAG_COMPOUND) ? FramedDrawerStyle.fromNBT(nbt.getCompoundTag(FramedDrawerStyle.NBT_KEY)) : FramedDrawerStyle.EMPTY;
    }
}
