package com.xinyihl.functionalstoragelegacy.common.inventory;

import com.xinyihl.functionalstoragelegacy.api.storage.StorageChange;
import com.xinyihl.functionalstoragelegacy.common.inventory.base.BigInventoryHandler;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Objects;

/**
 * Ender inventory handler - extends BigInventoryHandler with a frequency UUID for cross-dimensional sharing.
 */
public abstract class EnderInventoryHandler extends BigInventoryHandler {

    private String frequency = "";
    private boolean locked = false;
    private boolean voidsOverflow = false;
    private boolean isCreative = false;
    private double multiplier = 64D * 4D;

    public EnderInventoryHandler() {
        super(1); // Ender drawer has one shared slot.
    }

    @Override
    public double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        if (Double.compare(this.multiplier, multiplier) == 0) {
            return;
        }
        this.multiplier = multiplier;
        onChange(StorageChange.reset());
    }

    @Override
    public boolean voidsOverflow() {
        return voidsOverflow;
    }

    public void setVoidsOverflow(boolean voidsOverflow) {
        if (this.voidsOverflow == voidsOverflow) {
            return;
        }
        this.voidsOverflow = voidsOverflow;
        onChange(StorageChange.reset());
    }

    @Override
    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        if (this.locked == locked) {
            return;
        }
        this.locked = locked;
        // This inherited entry clears empty filters silently and emits one RESET.
        applyLockConfiguration(locked);
    }

    @Override
    public boolean isCreative() {
        return isCreative;
    }

    public void setCreative(boolean isCreative) {
        if (this.isCreative == isCreative) {
            return;
        }
        this.isCreative = isCreative;
        onChange(StorageChange.reset());
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        if (Objects.equals(this.frequency, frequency)) {
            return;
        }
        this.frequency = frequency;
        onChange(StorageChange.reset());
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
        NBTTagCompound beforeStorage = serializeNBT();
        String nextFrequency = nbt == null ? "" : nbt.getString("Frequency");
        boolean nextLocked = nbt != null && nbt.getBoolean("Locked");
        boolean nextVoidsOverflow = nbt != null && nbt.getBoolean("VoidOverflow");
        boolean nextCreative = nbt != null && nbt.getBoolean("IsCreative");
        double nextMultiplier = nbt == null ? 1D : nbt.getDouble("Multiplier");
        if (nextMultiplier == 0D) {
            nextMultiplier = 1D;
        }

        boolean configurationChanged = !Objects.equals(frequency, nextFrequency) || locked != nextLocked || voidsOverflow != nextVoidsOverflow || isCreative != nextCreative || Double.compare(multiplier, nextMultiplier) != 0;
        // Set all virtual properties before loading slots so unlock/creative
        // filtering is applied against the final configuration.
        frequency = nextFrequency;
        locked = nextLocked;
        voidsOverflow = nextVoidsOverflow;
        isCreative = nextCreative;
        multiplier = nextMultiplier;
        deserializeNBT(nbt);

        boolean storageChanged = !beforeStorage.equals(serializeNBT());
        if (configurationChanged && !storageChanged) {
            onChange(StorageChange.reset());
        }
    }
}
