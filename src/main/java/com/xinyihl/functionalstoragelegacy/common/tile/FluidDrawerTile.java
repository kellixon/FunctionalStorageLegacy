package com.xinyihl.functionalstoragelegacy.common.tile;

import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.BigFluidStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigFluidHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.api.storage.TransferResult;
import com.xinyihl.functionalstoragelegacy.api.upgrade.StorageFeature;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeAttribute;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeState;
import com.xinyihl.functionalstoragelegacy.common.inventory.base.BigFluidHandler;
import com.xinyihl.functionalstoragelegacy.common.storage.DrawerLayout;
import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * TileEntity for fluid drawers.
 * Holds a BigFluidHandler for large-capacity fluid storage.
 */
public class FluidDrawerTile extends ControllableDrawerTile {

    private BigFluidHandler fluidHandler;
    private DrawerLayout drawerLayout;

    public FluidDrawerTile() {
        this(DrawerLayout.X_1);
    }

    public FluidDrawerTile(DrawerLayout drawerLayout) {
        super();
        this.drawerLayout = drawerLayout;
        this.fluidHandler = createFluidHandler();
    }

    private BigFluidHandler createFluidHandler() {
        return new BigFluidHandler(drawerLayout.getSlotCount()) {
            @Override
            public void onChange() {
                FluidDrawerTile.this.markDirty();
                FluidDrawerTile.this.sendUpdatePacket();
            }

            @Override
            public double getMultiplier() {
                return FluidDrawerTile.this.getFluidMultiplier(
                        FluidDrawerTile.this.drawerLayout.getBaseCapacity());
            }

            @Override
            protected boolean hasMaxStorage() {
                return FluidDrawerTile.this.hasMaxStorageUpgrade();
            }

            @Override
            public boolean voidsOverflow() {
                return FluidDrawerTile.this.voidsOverflow();
            }

            @Override
            public boolean isLocked() {
                return FluidDrawerTile.this.isLocked();
            }

            @Override
            public boolean isCreative() {
                return FluidDrawerTile.this.isCreative();
            }
        };
    }

    @Override
    public boolean onSlotActivated(EntityPlayer player, EnumHand hand, EnumFacing facing,
                                   float hitX, float hitY, float hitZ, int slot) {
        ItemStack heldStack = player.getHeldItem(hand);

        if (!world.isRemote && slot >= 0 && slot < fluidHandler.getTankCount() && !heldStack.isEmpty()) {
            boolean fluidInteraction = FluidUtil.interactWithFluidHandler(player, hand, getSingleTankHandler(slot));
            if (fluidInteraction) {
                return true;
            }
        }

        return super.onSlotActivated(player, hand, facing, hitX, hitY, hitZ, slot);
    }

    @Override
    public void onClicked(EntityPlayer player, int slot) {
        if (!world.isRemote && slot >= 0 && slot < fluidHandler.getTankCount()) {
            ItemStack heldStack = player.getHeldItem(EnumHand.MAIN_HAND);
            if (!heldStack.isEmpty()) {
                FluidUtil.interactWithFluidHandler(player, EnumHand.MAIN_HAND, getSingleTankHandler(slot));
            }
        }
    }

    private IFluidHandler getSingleTankHandler(final int slot) {
        return new IFluidHandler() {
            @Override
            public IFluidTankProperties[] getTankProperties() {
                if (slot < 0 || slot >= fluidHandler.getTankCount()) {
                    return new IFluidTankProperties[0];
                }
                IFluidTankProperties[] all = fluidHandler.getTankProperties();
                return new IFluidTankProperties[]{all[slot]};
            }

            @Override
            public int fill(FluidStack resource, boolean doFill) {
                if (resource == null || resource.amount <= 0) {
                    return 0;
                }
                TransferResult<BigFluidStack> result = fluidHandler.fillTank(
                        slot,
                        new BigFluidStack(resource, resource.amount),
                        doFill ? StorageAction.EXECUTE : StorageAction.SIMULATE);
                return (int) Math.min(result.getProcessedAmount(), Integer.MAX_VALUE);
            }

            @Override
            public FluidStack drain(FluidStack resource, boolean doDrain) {
                if (resource == null || resource.amount <= 0) {
                    return null;
                }
                BigFluidStack current = fluidHandler.getTankSnapshot(slot);
                if (current.isEmpty() || !current.isSameType(resource)) {
                    return null;
                }
                TransferResult<BigFluidStack> result = fluidHandler.drainTank(
                        slot,
                        resource.amount,
                        doDrain ? StorageAction.EXECUTE : StorageAction.SIMULATE);
                return result.getProcessed().toFluidStack();
            }

            @Override
            public FluidStack drain(int maxDrain, boolean doDrain) {
                if (maxDrain <= 0 || fluidHandler.getTankSnapshot(slot).isEmpty()) {
                    return null;
                }
                TransferResult<BigFluidStack> result = fluidHandler.drainTank(
                        slot,
                        maxDrain,
                        doDrain ? StorageAction.EXECUTE : StorageAction.SIMULATE);
                return result.getProcessed().toFluidStack();
            }
        };
    }

    @Override
    protected void onLockStateChanged(boolean locked) {
        fluidHandler.setLockFilters(locked);
    }

    @Override
    protected void writeCustomData(NBTTagCompound nbt) {
        nbt.setTag("StorageV2", fluidHandler.serializeNBT().getCompoundTag("StorageV2"));
        nbt.setString("DrawerLayout", drawerLayout.getId());
    }

    @Override
    protected void readCustomData(NBTTagCompound nbt) {
        if (nbt.hasKey("DrawerLayout")) {
            drawerLayout = DrawerLayout.fromId(nbt.getString("DrawerLayout"));
        }
        fluidHandler = createFluidHandler();
        fluidHandler.deserializeNBT(nbt);
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound compound) {
        compound = super.writeToNBT(compound);
        compound.setTag("StorageV2", fluidHandler.serializeNBT().getCompoundTag("StorageV2"));
        compound.setString("DrawerLayout", drawerLayout.getId());
        return compound;
    }

    @Override
    public void readFromNBT(@Nonnull NBTTagCompound compound) {
        if (compound.hasKey("DrawerLayout")) {
            drawerLayout = DrawerLayout.fromId(compound.getString("DrawerLayout"));
        }
        fluidHandler = createFluidHandler();
        super.readFromNBT(compound);
        fluidHandler.deserializeNBT(compound);
    }

    @Override
    public IBigItemHandler getItemHandler() {
        return null; // Fluid drawer has no item handler
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fluidHandler);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public boolean isEverythingEmpty() {
        if (!super.isEverythingEmpty()) return false;
        for (int tank = 0; tank < fluidHandler.getTankCount(); tank++) {
            if (!fluidHandler.getTankSnapshot(tank).isEmpty()) return false;
        }
        return true;
    }

    @Override
    protected int calculateRedstoneSignal() {
        double totalCapacity = 0D;
        double totalStored = 0D;
        for (int tank = 0; tank < fluidHandler.getTankCount(); tank++) {
            totalCapacity += fluidHandler.getTankCapacity(tank);
            totalStored += fluidHandler.getTankSnapshot(tank).getAmount();
        }
        if (totalCapacity <= 0D) return 0;
        return (int) Math.min(15D, (totalStored / totalCapacity) * 15D);
    }

    @Override
    protected boolean canApplyUpgradeState(UpgradeState state) {
        if (state.hasFeature(StorageFeature.CREATIVE)
                || state.hasFeature(StorageFeature.MAX_CAPACITY)) {
            return true;
        }
        double calculated = state.calculate(UpgradeAttribute.FLUID_CAPACITY, drawerLayout.getBaseCapacity());
        long capacityPerTank = (long) Math.floor(calculated * 1000D);
        for (int tank = 0; tank < fluidHandler.getTankCount(); tank++) {
            if (fluidHandler.getTankSnapshot(tank).getAmount() > capacityPerTank) {
                return false;
            }
        }
        return true;
    }

    public IBigFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    public DrawerLayout getDrawerLayout() {
        return drawerLayout;
    }
}
