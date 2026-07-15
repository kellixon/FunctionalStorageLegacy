package com.xinyihl.functionalstoragelegacy.common.inventory.capability;

import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class DrawerStackCapabilityProvider implements ICapabilityProvider {

    @Nullable
    private final IBigItemHandler itemHandler;

    @Nullable
    private final IFluidHandlerItem fluidHandlerItem;

    public DrawerStackCapabilityProvider(@Nullable IBigItemHandler handler) {
        this(handler, null);
    }

    public DrawerStackCapabilityProvider(@Nullable IBigItemHandler itemHandler,
                                         @Nullable IFluidHandlerItem fluidHandlerItem) {
        this.itemHandler = itemHandler;
        this.fluidHandlerItem = fluidHandlerItem;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable net.minecraft.util.EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return itemHandler != null;
        }
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY
                || capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return fluidHandlerItem != null;
        }
        return false;
    }

    @Nullable
    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable net.minecraft.util.EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && itemHandler != null) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(itemHandler);
        }
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY && fluidHandlerItem != null) {
            return CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY.cast(fluidHandlerItem);
        }
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY && fluidHandlerItem != null) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fluidHandlerItem);
        }
        return null;
    }
}
