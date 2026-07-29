package com.xinyihl.functionalstoragelegacy.common.inventory.capability;

import com.xinyihl.functionalstoragelegacy.api.upgrade.StorageFeature;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeAttribute;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeState;
import com.xinyihl.functionalstoragelegacy.common.inventory.base.BigItemHandler;
import com.xinyihl.functionalstoragelegacy.common.storage.DrawerLayout;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;

public class DrawerStackItemHandler extends BigItemHandler {

    private final ItemStack drawerStack;
    private final DrawerLayout drawerLayout;
    private final UpgradeState upgradeState;
    private final boolean locked;

    public DrawerStackItemHandler(@Nonnull ItemStack drawerStack, DrawerLayout drawerLayout) {
        super(drawerLayout.getSlotCount());
        this.drawerStack = drawerStack;
        this.drawerLayout = drawerLayout;
        NBTTagCompound tileData = DrawerStackDataHelper.getTileData(drawerStack);
        this.upgradeState = DrawerStackDataHelper.readUpgradeState(tileData, 4, 3);
        this.locked = DrawerStackDataHelper.isLocked(tileData);
        if (tileData != null) {
            deserializeNBT(tileData);
        }
        subscribe(change -> persistStorage());
    }

    @Override
    public double getMultiplier() {
        return upgradeState.calculate(UpgradeAttribute.ITEM_CAPACITY, drawerLayout.getBaseCapacity());
    }

    @Override
    protected boolean allowsEquivalentItems() {
        return upgradeState.hasFeature(StorageFeature.EQUIVALENT_ITEMS);
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

    private void persistStorage() {
        NBTTagCompound tileData = DrawerStackDataHelper.getOrCreateTileData(drawerStack);
        tileData.setTag("StorageV2", serializeNBT().getCompoundTag("StorageV2"));
    }
}
