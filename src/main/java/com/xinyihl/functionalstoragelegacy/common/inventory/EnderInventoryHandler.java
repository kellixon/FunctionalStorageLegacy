package com.xinyihl.functionalstoragelegacy.common.inventory;

import com.xinyihl.functionalstoragelegacy.common.inventory.base.BigInventoryHandler;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Ender inventory handler - extends BigInventoryHandler with a frequency UUID for cross-dimensional sharing.
 */
public abstract class EnderInventoryHandler extends BigInventoryHandler {

    private String frequency = "";
    private boolean locked = false;
    private boolean voidsOverflow = false;
    private boolean isCreative = false;
    private double multiplier = 64D * 4D;
    public boolean needUpdate = true;

    public EnderInventoryHandler() {
        super(1); // Ender drawer has 1 slot, base 32 slot amount
    }

    @Override
    public void onChange() {
        needUpdate = true;
    }

    public boolean needUpdate() {
        return needUpdate;
    }

    public void setUpdate() {
        needUpdate = false;
    }

    @Override
    public double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    @Override
    public boolean voidsOverflow() {
        return voidsOverflow;
    }

    public void setVoidsOverflow(boolean voidsOverflow) {
        this.voidsOverflow = voidsOverflow;
    }

    @Override
    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
        setLockFilters(locked);
    }

    @Override
    public boolean isCreative() {
        return isCreative;
    }

    public void setCreative(boolean isCreative) {
        this.isCreative = isCreative;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public NBTTagCompound serializeNBTFull() {
        NBTTagCompound nbt = serializeNBT();
        nbt.setString("Frequency", frequency);
        nbt.setBoolean("Locked", locked);
        nbt.setBoolean("VoidOverflow", voidsOverflow);
        nbt.setBoolean("IsCreative", isCreative);
        nbt.setDouble("Multiplier", multiplier);
        return nbt;
    }

    public void deserializeNBTFull(NBTTagCompound nbt) {
        frequency = nbt.getString("Frequency");
        setLocked(nbt.getBoolean("Locked"));
        voidsOverflow = nbt.getBoolean("VoidOverflow");
        isCreative = nbt.getBoolean("IsCreative");
        multiplier = nbt.getDouble("Multiplier");
        if (multiplier == 0D) multiplier = 1D;
        deserializeNBT(nbt);
    }
}
