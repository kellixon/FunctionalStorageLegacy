package com.xinyihl.functionalstoragelegacy.common.tile;

import com.xinyihl.functionalstoragelegacy.TestCapabilities;
import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.common.inventory.EnderInventoryHandler;
import com.xinyihl.functionalstoragelegacy.common.inventory.controller.ControllerItemHandler;
import com.xinyihl.functionalstoragelegacy.common.tile.controller.DrawerControllerTile;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.CapabilityItemHandler;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class EnderDrawerTileTest {

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
        TestCapabilities.itemHandler();
    }

    @Test
    public void replacingStorageInvalidatesAccessorOnlyForNewIdentity() {
        CountingTile tile = new CountingTile();
        EnderInventoryHandler first = new EnderInventoryHandler() {
        };
        EnderInventoryHandler second = new EnderInventoryHandler() {
        };
        IBigItemHandler facade = tile.getItemHandler();
        assertSame(facade, tile.getCapability(
                CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null));

        tile.replaceStorage(first);
        assertSame(facade, tile.getItemHandler());
        assertEquals(1, tile.invalidations);
        assertEquals(1L, facade.insertIntoSlot(
                0,
                new BigItemStack(new ItemStack(Items.DIAMOND), 1L),
                StorageAction.EXECUTE).getProcessedAmount());
        assertEquals(1L, first.getSlotSnapshot(0).getAmount());

        tile.replaceStorage(first);
        assertEquals(1, tile.invalidations);

        tile.replaceStorage(second);
        assertSame(facade, tile.getItemHandler());
        assertSame(facade, tile.getCapability(
                CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null));
        assertNotSame(first, second);
        assertEquals(2, tile.invalidations);
        assertEquals(2L, facade.insertIntoSlot(
                0,
                new BigItemStack(new ItemStack(Items.EMERALD), 2L),
                StorageAction.EXECUTE).getProcessedAmount());
        assertEquals(2L, second.getSlotSnapshot(0).getAmount());
        assertEquals(1L, first.getSlotSnapshot(0).getAmount());
    }

    @Test
    public void sharedTargetsDeduplicateAndTargetChangesRefreshMappings() {
        DrawerControllerTile controllerTile = new DrawerControllerTile();
        RefreshingTile firstTile = new RefreshingTile(controllerTile);
        RefreshingTile secondTile = new RefreshingTile(controllerTile);
        EnderInventoryHandler shared = new EnderInventoryHandler() {
        };
        EnderInventoryHandler other = new EnderInventoryHandler() {
        };
        firstTile.replaceStorage(shared);
        secondTile.replaceStorage(shared);
        controllerTile.getConnectedDrawers().getItemHandlers()
                .addAll(Arrays.asList(firstTile.getItemHandler(), secondTile.getItemHandler()));
        controllerTile.refreshHandlerMappings();
        ControllerItemHandler controller =
                (ControllerItemHandler) controllerTile.getItemHandler();

        assertNotSame(firstTile.getItemHandler(), secondTile.getItemHandler());
        assertEquals(1, controller.getHandlers().size());
        assertEquals(1, controller.getSlotCount());

        secondTile.replaceStorage(other);
        assertEquals(2, controller.getHandlers().size());
        assertEquals(2, controller.getSlotCount());

        firstTile.replaceStorage(other);
        assertEquals(1, controller.getHandlers().size());
        assertEquals(1, controller.getSlotCount());
        assertTrue(firstTile.refreshRequests > 0);
        assertTrue(secondTile.refreshRequests > 0);
    }

    @Test
    public void syncedInventoryRetainsLockedEmptyTypeAndSharedFlags() {
        EnderInventoryHandler serverHandler = new EnderInventoryHandler() {
        };
        serverHandler.insertIntoSlot(
                0,
                new BigItemStack(new ItemStack(Items.DIAMOND), 2L),
                StorageAction.EXECUTE);
        serverHandler.setLocked(true);
        serverHandler.extractFromSlot(0, 2L, StorageAction.EXECUTE);
        serverHandler.setVoidsOverflow(true);
        serverHandler.setMultiplier(321D);
        serverHandler.setFrequency("shared-test");
        EnderDrawerTile server = new EnderDrawerTile();
        server.replaceStorage(serverHandler);

        NBTTagCompound update = new NBTTagCompound();
        server.writeSyncedInventory(update);
        EnderDrawerTile client = new EnderDrawerTile();
        client.readSyncedInventory(update);

        IBigItemHandler clientHandler = client.getItemHandler();
        assertTrue(clientHandler.isLocked());
        assertTrue(clientHandler.voidsOverflow());
        assertEquals(321L * 64L, clientHandler.getSlotCapacity(0));
        assertTrue(clientHandler.getSlotSnapshot(0).hasTemplate());
        assertEquals(0L, clientHandler.getSlotSnapshot(0).getAmount());
        assertTrue(clientHandler.getSlotSnapshot(0)
                .isSameType(new ItemStack(Items.DIAMOND)));
    }

    private static final class CountingTile extends EnderDrawerTile {
        private int invalidations;

        @Override
        public void invalidateAE2Accessor() {
            invalidations++;
            super.invalidateAE2Accessor();
        }
    }

    private static final class RefreshingTile extends EnderDrawerTile {
        private final DrawerControllerTile controller;
        private int refreshRequests;

        private RefreshingTile(DrawerControllerTile controller) {
            this.controller = controller;
        }

        @Override
        protected void requestControllerHandlerRefresh() {
            refreshRequests++;
            controller.refreshHandlerMappings();
        }
    }
}
