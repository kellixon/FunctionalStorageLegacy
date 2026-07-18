package com.xinyihl.functionalstoragelegacy.common.inventory.capability;

import com.xinyihl.functionalstoragelegacy.api.upgrade.StorageFeature;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeAttribute;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeState;
import com.xinyihl.functionalstoragelegacy.common.inventory.base.BigFluidHandler;
import com.xinyihl.functionalstoragelegacy.common.storage.DrawerLayout;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nonnull;

public class FluidDrawerStackItemHandler extends BigFluidHandler implements IFluidHandlerItem {

    private final ItemStack drawerStack;
    private final DrawerLayout drawerLayout;
    private final UpgradeState upgradeState;
    private final boolean locked;

    public FluidDrawerStackItemHandler(@Nonnull ItemStack drawerStack, DrawerLayout drawerLayout) {
        super(drawerLayout.getSlotCount());
        this.drawerStack = drawerStack;
        this.drawerLayout = drawerLayout;
        NBTTagCompound tileData = DrawerStackDataHelper.getTileData(drawerStack);
        this.upgradeState = DrawerStackDataHelper.readUpgradeState(
                tileData,
                4,
                3
        );
        this.locked = DrawerStackDataHelper.isLocked(tileData);
        deserializeNBT(tileData);
        subscribe(change -> persistStorage());
    }

    @Override
    public double getMultiplier() {
        return upgradeState.calculate(
                UpgradeAttribute.FLUID_CAPACITY, drawerLayout.getBaseCapacity());
    }

    @Override
    protected boolean hasMaxStorage() {
        return upgradeState.hasFeature(StorageFeature.MAX_CAPACITY);
    }

    @Override
    protected boolean isOperationEnabled() {
        return drawerStack.getCount() == 1;
    }

    @Override
    public boolean voidsOverflow() {
        return upgradeState.hasFeature(StorageFeature.VOID_OVERFLOW);
    }

    @Override
    public boolean isLocked() {
        return locked;
    }

    @Override
    public boolean isCreative() {
        return upgradeState.hasFeature(StorageFeature.CREATIVE);
    }

    @Nonnull
    @Override
    public ItemStack getContainer() {
        return drawerStack;
    }

    private void persistStorage() {
        NBTTagCompound tileData = DrawerStackDataHelper.getOrCreateTileData(drawerStack);
        tileData.setTag("StorageV2", serializeNBT().getCompoundTag("StorageV2"));
    }
}
