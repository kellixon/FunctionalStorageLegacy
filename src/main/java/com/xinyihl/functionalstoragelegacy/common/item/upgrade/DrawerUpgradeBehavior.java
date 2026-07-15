package com.xinyihl.functionalstoragelegacy.common.item.upgrade;

import com.xinyihl.functionalstoragelegacy.api.upgrade.IStorageUpgrade;
import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

/** Internal installation and active-behavior contract for the mod's own upgrades. */
public interface DrawerUpgradeBehavior extends IStorageUpgrade {

    enum SlotType {
        STORAGE,
        UTILITY
    }

    SlotType getSlotType();

    default boolean canInstallInto(ControllableDrawerTile tile, @Nonnull ItemStack stack) {
        return true;
    }

    default void onInstalledTick(ControllableDrawerTile tile, @Nonnull ItemStack stack, int slot) {
    }

    default boolean providesRedstoneSignal(@Nonnull ItemStack stack) {
        return false;
    }

    /** Higher values may automatically replace lower values in storage upgrade slots. */
    default int getReplacementPriority(@Nonnull ItemStack stack) {
        return Integer.MIN_VALUE;
    }
}
