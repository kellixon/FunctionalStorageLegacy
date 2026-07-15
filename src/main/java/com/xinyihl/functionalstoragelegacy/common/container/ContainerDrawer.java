package com.xinyihl.functionalstoragelegacy.common.container;

import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import com.xinyihl.functionalstoragelegacy.util.ItemUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nonnull;

/**
 * Container for drawer upgrade management GUI.
 * Shows storage upgrade slots and utility upgrade slots.
 */
public class ContainerDrawer extends Container {

    private final ControllableDrawerTile tile;

    public ContainerDrawer(InventoryPlayer playerInventory, ControllableDrawerTile tile) {
        this.tile = tile;

        // Storage upgrade slots (top row)
        ItemStackHandler storageUpgrades = tile.getStorageUpgrades();
        for (int i = 0; i < storageUpgrades.getSlots(); i++) {
            addSlotToContainer(new StorageUpgradeSlot(storageUpgrades, i, 8 + i * 18, 20));
        }

        // Utility upgrade slots (second row)
        ItemStackHandler utilityUpgrades = tile.getUtilityUpgrades();
        for (int i = 0; i < utilityUpgrades.getSlots(); i++) {
            addSlotToContainer(new UtilityUpgradeSlot(utilityUpgrades, i, 8 + i * 18, 44));
        }

        // Player inventory (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return playerIn.getDistanceSq(tile.getPos()) <= 64;
    }

    @Nonnull
    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        if (clickTypeIn == ClickType.PICKUP
                && slotId >= 0
                && slotId < tile.getStorageUpgrades().getSlots()) {
            Slot slot = inventorySlots.get(slotId);
            ItemStack heldStack = player.inventory.getItemStack();
            if (slot instanceof StorageUpgradeSlot
                    && slot.getHasStack()
                    && ItemUtil.isStorageUpgradeItem(heldStack)) {
                ItemStack existing = slot.getStack();
                if (ItemUtil.hasHigherUpgradeReplacementPriority(heldStack, existing)
                        && tile.canReplaceStorageUpgrade(slotId, heldStack)) {
                    ItemStack replacement = heldStack.copy();
                    replacement.setCount(1);
                    slot.putStack(replacement);
                    slot.onSlotChanged();

                    heldStack.shrink(1);
                    if (heldStack.isEmpty()) {
                        player.inventory.setItemStack(existing.copy());
                    } else if (!player.inventory.addItemStackToInventory(existing.copy())) {
                        player.dropItem(existing.copy(), false);
                    }

                    detectAndSendChanges();
                    return player.inventory.getItemStack();
                }
            }
        }
        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    @Nonnull
    @Override
    public ItemStack transferStackInSlot(@Nonnull EntityPlayer playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = inventorySlots.get(index);

        if (slot != null && slot.getHasStack()) {
            ItemStack stackInSlot = slot.getStack();
            itemstack = stackInSlot.copy();

            int upgradeSlots = tile.getStorageUpgrades().getSlots() + tile.getUtilityUpgrades().getSlots();

            if (index < upgradeSlots) {
                if (!slot.canTakeStack(playerIn)) {
                    return ItemStack.EMPTY;
                }
                // Transfer from upgrade slot to player inventory
                if (!mergeItemStack(stackInSlot, upgradeSlots, upgradeSlots + 36, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Transfer from player inventory to upgrade slots
                if (!movePlayerUpgradeStack(playerIn, stackInSlot)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stackInSlot.isEmpty()) {
                slot.putStack(ItemStack.EMPTY);
            } else {
                slot.onSlotChanged();
            }
        }
        return itemstack;
    }

    public ControllableDrawerTile getTile() {
        return tile;
    }

    private boolean movePlayerUpgradeStack(EntityPlayer playerIn, ItemStack stackInSlot) {
        boolean movedAny = false;

        if (stackInSlot.isEmpty()) {
            return false;
        }

        if (ItemUtil.isStorageUpgradeItem(stackInSlot)) {
            for (int i = 0; i < tile.getStorageUpgrades().getSlots() && !stackInSlot.isEmpty(); i++) {
                ItemStack existing = tile.getStorageUpgrades().getStackInSlot(i);
                if (!ItemUtil.hasHigherUpgradeReplacementPriority(stackInSlot, existing)
                        || !tile.canReplaceStorageUpgrade(i, stackInSlot)) {
                    continue;
                }

                ItemStack replacement = stackInSlot.copy();
                replacement.setCount(1);
                tile.getStorageUpgrades().setStackInSlot(i, replacement);
                stackInSlot.shrink(1);
                movedAny = true;

                if (!playerIn.inventory.addItemStackToInventory(existing.copy())) {
                    playerIn.dropItem(existing.copy(), false);
                }
            }
        }

        if (ItemUtil.isStorageUpgradeItem(stackInSlot)) {
            for (int i = 0; i < tile.getStorageUpgrades().getSlots() && !stackInSlot.isEmpty(); i++) {
                if (!tile.getStorageUpgrades().getStackInSlot(i).isEmpty() || !tile.canInsertStorageUpgrade(i, stackInSlot)) {
                    continue;
                }
                ItemStack singleUpgrade = stackInSlot.copy();
                singleUpgrade.setCount(1);
                tile.getStorageUpgrades().setStackInSlot(i, singleUpgrade);
                stackInSlot.shrink(1);
                movedAny = true;
            }
            return movedAny;
        }

        if (tile.canInsertUtilityUpgrade(0, stackInSlot)) {
            for (int i = 0; i < tile.getUtilityUpgrades().getSlots() && !stackInSlot.isEmpty(); i++) {
                if (!tile.getUtilityUpgrades().getStackInSlot(i).isEmpty() || !tile.canInsertUtilityUpgrade(i, stackInSlot)) {
                    continue;
                }
                ItemStack singleUpgrade = stackInSlot.copy();
                singleUpgrade.setCount(1);
                tile.getUtilityUpgrades().setStackInSlot(i, singleUpgrade);
                stackInSlot.shrink(1);
                movedAny = true;
            }
        }

        return movedAny;
    }

    private class StorageUpgradeSlot extends SlotItemHandler {
        private final int upgradeSlot;

        StorageUpgradeSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
            this.upgradeSlot = index;
        }

        @Override
        public boolean isItemValid(@Nonnull ItemStack stack) {
            return tile.canInsertStorageUpgrade(upgradeSlot, stack);
        }

        @Override
        public boolean canTakeStack(EntityPlayer playerIn) {
            return tile.canRemoveStorageUpgrade(upgradeSlot);
        }
    }

    private class UtilityUpgradeSlot extends SlotItemHandler {
        private final int upgradeSlot;

        UtilityUpgradeSlot(ItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
            this.upgradeSlot = index;
        }

        @Override
        public boolean isItemValid(@Nonnull ItemStack stack) {
            return tile.canInsertUtilityUpgrade(upgradeSlot, stack);
        }
    }
}
