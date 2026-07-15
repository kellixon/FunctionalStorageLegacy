package com.xinyihl.functionalstoragelegacy.common.integration.ae2;

import appeng.api.AEApi;
import appeng.api.storage.IStorageMonitorableAccessor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.capabilities.Capabilities;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigFluidHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.common.tile.FluidDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.compact.CompactingDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.controller.DrawerControllerTile;
import net.minecraftforge.common.capabilities.Capability;

/**
 * Helper class that directly references AE2 classes.
 * Only loaded when AE2 is present.
 */
public class AE2CapabilityHelper {

    public static boolean isStorageAccessor(Capability<?> capability) {
        return capability == Capabilities.STORAGE_MONITORABLE_ACCESSOR;
    }

    @SuppressWarnings("unchecked")
    public static <T> T castAccessor(IStorageMonitorableAccessor accessor) {
        return (T) Capabilities.STORAGE_MONITORABLE_ACCESSOR.cast(accessor);
    }

    public static Object createAccessor(ControllableDrawerTile tile) {
        IItemStorageChannel itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        IFluidStorageChannel fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);

        if (tile instanceof DrawerControllerTile) {
            DrawerControllerTile controller = (DrawerControllerTile) tile;
            return new DrawerStorageAccessor(
                    new DrawerMEMonitor<>(new DrawerMEItemHandler(controller.getItemHandler(), itemChannel), itemChannel),
                    new DrawerMEMonitor<>(new DrawerMEFluidHandler(controller.getFluidHandler(), fluidChannel), fluidChannel)
            );
        }

        if (tile instanceof FluidDrawerTile) {
            IBigFluidHandler fluidHandler = ((FluidDrawerTile) tile).getFluidHandler();
            return new DrawerStorageAccessor(
                    null,
                    new DrawerMEMonitor<>(new DrawerMEFluidHandler(fluidHandler, fluidChannel), fluidChannel)
            );
        }

        if (tile instanceof CompactingDrawerTile) {
            return new DrawerStorageAccessor(
                    new DrawerMEMonitor<>(new CompactingMEItemHandler(
                            tile.getItemHandler(), itemChannel), itemChannel),
                    null
            );
        }

        IBigItemHandler itemHandler = tile.getItemHandler();
        return itemHandler == null ? null : new DrawerStorageAccessor(
                new DrawerMEMonitor<>(new DrawerMEItemHandler(itemHandler, itemChannel), itemChannel),
                null
        );
    }
}
