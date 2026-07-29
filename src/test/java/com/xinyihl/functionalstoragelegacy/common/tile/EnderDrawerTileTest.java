package com.xinyihl.functionalstoragelegacy.common.tile;

import com.xinyihl.functionalstoragelegacy.TestCapabilities;
import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.common.inventory.EnderItemHandler;
import com.xinyihl.functionalstoragelegacy.common.inventory.controller.ControllerItemHandler;
import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.controller.DrawerControllerTile;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.CapabilityItemHandler;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.Assert.*;

public class EnderDrawerTileTest {

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
        TestCapabilities.itemHandler();
    }

    @Test
    public void replacingStorageInvalidatesAccessorOnlyForNewIdentity() {
        CountingTile tile = new CountingTile();
        EnderItemHandler first = new EnderItemHandler() {
        };
        EnderItemHandler second = new EnderItemHandler() {
        };
        IBigItemHandler facade = tile.getItemHandler();
        assertSame(facade, tile.getCapability(
                CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null));

        tile.replaceStorage(first);
        assertSame(facade, tile.getItemHandler());
        assertEquals(1, tile.invalidations);
        assertEquals(1L, facade.insert(
                0,
                new BigItemStack(new ItemStack(Items.DIAMOND), 1L),
                StorageAction.EXECUTE).getProcessedAmount());
        assertEquals(1L, first.getSnapshot(0).getAmount());

        tile.replaceStorage(first);
        assertEquals(1, tile.invalidations);

        tile.replaceStorage(second);
        assertSame(facade, tile.getItemHandler());
        assertSame(facade, tile.getCapability(
                CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null));
        assertNotSame(first, second);
        assertEquals(2, tile.invalidations);
        assertEquals(2L, facade.insert(
                0,
                new BigItemStack(new ItemStack(Items.EMERALD), 2L),
                StorageAction.EXECUTE).getProcessedAmount());
        assertEquals(2L, second.getSnapshot(0).getAmount());
        assertEquals(1L, first.getSnapshot(0).getAmount());
    }

    @Test
    public void sharedTargetsDeduplicateAndTargetChangesRefreshMappings() {
        DrawerControllerTile controllerTile = new DrawerControllerTile();
        RefreshingTile firstTile = new RefreshingTile(controllerTile);
        RefreshingTile secondTile = new RefreshingTile(controllerTile);
        EnderItemHandler shared = new EnderItemHandler() {
        };
        EnderItemHandler other = new EnderItemHandler() {
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
        assertEquals(1, controller.getStorageCount());

        secondTile.replaceStorage(other);
        assertEquals(2, controller.getHandlers().size());
        assertEquals(2, controller.getStorageCount());

        firstTile.replaceStorage(other);
        assertEquals(1, controller.getHandlers().size());
        assertEquals(1, controller.getStorageCount());
        assertTrue(firstTile.refreshRequests > 0);
        assertTrue(secondTile.refreshRequests > 0);
    }

    @Test
    public void syncedInventoryRetainsLockedEmptyTypeAndSharedFlags() {
        EnderItemHandler serverHandler = new EnderItemHandler() {
        };
        serverHandler.insert(
                0,
                new BigItemStack(new ItemStack(Items.DIAMOND), 2L),
                StorageAction.EXECUTE);
        serverHandler.setLocked(true);
        serverHandler.extract(0, 2L, StorageAction.EXECUTE);
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
        assertEquals(321L * 64L, clientHandler.getCapacity(0));
        assertTrue(clientHandler.getSnapshot(0).hasTemplate());
        assertEquals(0L, clientHandler.getSnapshot(0).getAmount());
        assertTrue(clientHandler.getSnapshot(0)
                .isSameType(new ItemStack(Items.DIAMOND)));
    }

    @Test
    public void storageReplacementAndUnloadCloseAccessorIdempotently() throws Exception {
        EnderDrawerTile tile = new EnderDrawerTile();
        CountingCloseable replacementAccessor = new CountingCloseable();
        installAccessor(tile, replacementAccessor);
        EnderItemHandler target = new EnderItemHandler() {
        };

        assertTrue(tile.replaceStorage(target));
        assertEquals(1, replacementAccessor.closes);
        assertFalse(tile.replaceStorage(target));
        tile.invalidateAE2Accessor();
        assertEquals(1, replacementAccessor.closes);

        CountingCloseable unloadAccessor = new CountingCloseable();
        installAccessor(tile, unloadAccessor);
        tile.onChunkUnload();
        tile.onChunkUnload();
        assertEquals(1, unloadAccessor.closes);
    }

    @Test
    public void onlineWoodReadClosesOldAccessorAndBindsNewHandler() throws Exception {
        WoodDrawerTile tile = new WoodDrawerTile();
        IBigItemHandler oldHandler = tile.getItemHandler();
        CountingCloseable oldAccessor = new CountingCloseable();
        installAccessor(tile, oldAccessor);
        NBTTagCompound serialized = tile.saveTileToNBT();

        tile.loadTileFromNBT(serialized);

        IBigItemHandler replacement = tile.getItemHandler();
        assertEquals(1, oldAccessor.closes);
        assertNotSame(oldHandler, replacement);
        assertEquals(2L, replacement.insert(
                0,
                new BigItemStack(new ItemStack(Items.DIAMOND), 2L),
                StorageAction.EXECUTE).getProcessedAmount());
        assertEquals(2L, replacement.getSnapshot(0).getAmount());
        assertEquals(0L, oldHandler.getSnapshot(0).getAmount());
    }

    private static void installAccessor(
            ControllableDrawerTile tile, Object accessor) throws Exception {
        Field field = ControllableDrawerTile.class.getDeclaredField("ae2Accessor");
        field.setAccessible(true);
        field.set(tile, accessor);
    }

    private static final class CountingCloseable implements AutoCloseable {
        private int closes;

        @Override
        public void close() {
            closes++;
        }
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
