package com.xinyihl.functionalstoragelegacy.common.inventory.capability;

import com.xinyihl.functionalstoragelegacy.api.upgrade.IStorageUpgrade;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class DrawerStackDataHelper {

    private DrawerStackDataHelper() {
    }

    @Nullable
    static NBTTagCompound getTileData(@Nonnull ItemStack drawerStack) {
        if (!drawerStack.hasTagCompound() || !drawerStack.getTagCompound().hasKey("TileData")) {
            return null;
        }
        return drawerStack.getTagCompound().getCompoundTag("TileData");
    }

    @Nonnull
    static NBTTagCompound getOrCreateTileData(@Nonnull ItemStack drawerStack) {
        if (!drawerStack.hasTagCompound()) {
            drawerStack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound stackTag = drawerStack.getTagCompound();
        if (!stackTag.hasKey("TileData")) {
            stackTag.setTag("TileData", new NBTTagCompound());
        }
        return stackTag.getCompoundTag("TileData");
    }

    @Nonnull
    static UpgradeState readUpgradeState(@Nullable NBTTagCompound tileData, int storageUpgradeSlots, int utilityUpgradeSlots) {
        UpgradeState.Builder builder = UpgradeState.builder();
        if (tileData == null) {
            return builder.build();
        }

        if (tileData.hasKey("StorageUpgrades")) {
            ItemStackHandler storageUpgrades = new ItemStackHandler(storageUpgradeSlots);
            storageUpgrades.deserializeNBT(tileData.getCompoundTag("StorageUpgrades"));
            for (int i = 0; i < storageUpgrades.getSlots(); i++) {
                applyUpgrade(storageUpgrades.getStackInSlot(i), builder);
            }
        }

        if (tileData.hasKey("UtilityUpgrades")) {
            ItemStackHandler utilityUpgrades = new ItemStackHandler(utilityUpgradeSlots);
            utilityUpgrades.deserializeNBT(tileData.getCompoundTag("UtilityUpgrades"));
            for (int i = 0; i < utilityUpgrades.getSlots(); i++) {
                applyUpgrade(utilityUpgrades.getStackInSlot(i), builder);
            }
        }

        return builder.build();
    }

    static boolean isLocked(@Nullable NBTTagCompound tileData) {
        return tileData != null && tileData.getBoolean("Locked");
    }

    private static void applyUpgrade(@Nonnull ItemStack stack, UpgradeState.Builder builder) {
        if (!stack.isEmpty() && stack.getItem() instanceof IStorageUpgrade) {
            ((IStorageUpgrade) stack.getItem()).applyUpgrade(stack, builder);
        }
    }
}
