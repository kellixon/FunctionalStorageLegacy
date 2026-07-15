package com.xinyihl.functionalstoragelegacy.common.tile.controller;

import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * TileEntity for controller extension blocks.
 * Delegates to its linked controller's connected drawers.
 * Provides the same capability interface as the controller.
 */
public class ControllerExtensionTile extends ControllableDrawerTile {

    @Nullable
    private DrawerControllerTile getLinkedController() {
        if (world == null) {
            return null;
        }

        if (controllerPos != null) {
            TileEntity te = world.getTileEntity(controllerPos);
            if (te instanceof DrawerControllerTile) {
                return (DrawerControllerTile) te;
            }
        }
        return null;
    }

    @Override
    protected void writeCustomData(NBTTagCompound nbt) {

    }

    @Override
    protected void readCustomData(NBTTagCompound nbt) {

    }

    @Override
    public boolean onSlotActivated(EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ, int slot) {
        DrawerControllerTile controller = getLinkedController();
        if (controller != null) {
            return controller.onSlotActivated(player, hand, facing, hitX, hitY, hitZ, slot);
        }
        return super.onSlotActivated(player, hand, facing, hitX, hitY, hitZ, slot);
    }

    @Override
    public IBigItemHandler getItemHandler() {
        DrawerControllerTile controller = getLinkedController();
        if (controller != null) {
            return controller.getItemHandler();
        }
        return null;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        DrawerControllerTile controller = getLinkedController();
        if (controller != null) {
            return controller.hasCapability(capability, facing);
        }
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        DrawerControllerTile controller = getLinkedController();
        if (controller != null) {
            return controller.getCapability(capability, facing);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public int getStorageUpgradesAmount() {
        return 0;
    }

    @Override
    public int getUtilityUpgradesAmount() {
        return 0;
    }
}
