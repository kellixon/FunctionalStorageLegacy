package com.xinyihl.functionalstoragelegacy.common.tile;

import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.common.inventory.CompactingInventoryHandler;
import com.xinyihl.functionalstoragelegacy.common.tile.compact.CompactingDrawerTile;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ItemDrawerLockStateTest {

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    public void woodDrawerUnlockClearsEmptyFilterAndPersistsTheChange() {
        CountingWoodDrawerTile tile = new CountingWoodDrawerTile();
        IBigItemHandler handler = tile.getItemHandler();
        Item original = new Item();
        Item replacement = new Item();
        handler.insert(
                0, new BigItemStack(new ItemStack(original), 4L), StorageAction.EXECUTE);
        tile.setLocked(true);
        handler.extract(0, 4L, StorageAction.EXECUTE);
        assertTrue(handler.getSnapshot(0).hasTemplate());
        assertEquals(1, tile.saveTileToNBT()
                .getCompoundTag("StorageV2").getTagList("Items", 10).tagCount());
        tile.resetNotifications();

        tile.setLocked(false);

        assertFalse(handler.getSnapshot(0).hasTemplate());
        assertEquals(0, tile.saveTileToNBT()
                .getCompoundTag("StorageV2").getTagList("Items", 10).tagCount());
        assertTrue(tile.dirtyCalls > 0);
        assertEquals(0, tile.updateCalls);
        tile.update();
        assertEquals(1, tile.updateCalls);
        tile.update();
        assertEquals(1, tile.updateCalls);
        assertEquals(2L, handler.insert(
                0,
                new BigItemStack(new ItemStack(replacement), 2L),
                StorageAction.EXECUTE).getProcessedAmount());
    }

    @Test
    public void compactingDrawerUnlockClearsOnlyEmptyTierConfiguration() {
        CountingCompactingDrawerTile tile = new CountingCompactingDrawerTile();
        CompactingInventoryHandler handler =
                (CompactingInventoryHandler) tile.getItemHandler();
        Item original = new Item();
        Item replacement = new Item();
        handler.configureTiers(Arrays.asList(
                new CompactingInventoryHandler.Tier(new ItemStack(original), 1L)));
        tile.setLocked(true);
        handler.insert(
                0, new BigItemStack(new ItemStack(original), 3L), StorageAction.EXECUTE);
        handler.extract(0, 3L, StorageAction.EXECUTE);
        assertTrue(handler.isConfigured());
        tile.resetNotifications();

        tile.setLocked(false);

        assertFalse(handler.isConfigured());
        assertEquals(0, tile.saveTileToNBT()
                .getCompoundTag("StorageV2").getTagList("Tiers", 10).tagCount());
        assertTrue(tile.dirtyCalls > 0);
        assertEquals(0, tile.updateCalls);
        tile.update();
        assertEquals(1, tile.updateCalls);
        tile.update();
        assertEquals(1, tile.updateCalls);
        handler.configureTiers(Arrays.asList(
                new CompactingInventoryHandler.Tier(new ItemStack(replacement), 1L)));
        assertEquals(2L, handler.insert(
                0,
                new BigItemStack(new ItemStack(replacement), 2L),
                StorageAction.EXECUTE).getProcessedAmount());
    }

    @Test
    public void woodDrawerRuntimeUnlockPreservesPopulatedStorage() {
        CountingWoodDrawerTile tile = new CountingWoodDrawerTile();
        IBigItemHandler handler = tile.getItemHandler();
        Item stored = new Item();
        handler.insert(
                0, new BigItemStack(new ItemStack(stored), 9L), StorageAction.EXECUTE);
        tile.setLocked(true);

        tile.setLocked(false);

        assertEquals(9L, handler.getSnapshot(0).getAmount());
        assertTrue(handler.getSnapshot(0).isSameType(new ItemStack(stored)));
        assertEquals(1, tile.saveTileToNBT()
                .getCompoundTag("StorageV2").getTagList("Items", 10).tagCount());
    }

    @Test
    public void compactingDrawerRuntimeUnlockPreservesPopulatedStorageAndTiers() {
        CountingCompactingDrawerTile tile = new CountingCompactingDrawerTile();
        CompactingInventoryHandler handler =
                (CompactingInventoryHandler) tile.getItemHandler();
        Item stored = new Item();
        handler.configureTiers(Arrays.asList(
                new CompactingInventoryHandler.Tier(new ItemStack(stored), 1L)));
        tile.setLocked(true);
        handler.insert(
                0, new BigItemStack(new ItemStack(stored), 11L), StorageAction.EXECUTE);

        tile.setLocked(false);

        assertTrue(handler.isConfigured());
        assertEquals(11L, handler.getStoredBaseAmount());
        assertTrue(handler.getSnapshot(0).isSameType(new ItemStack(stored)));
        assertEquals(1, tile.saveTileToNBT()
                .getCompoundTag("StorageV2").getTagList("Tiers", 10).tagCount());
    }

    private static final class CountingWoodDrawerTile extends WoodDrawerTile {
        private int dirtyCalls;
        private int updateCalls;

        @Override
        public void markDirty() {
            dirtyCalls++;
        }

        @Override
        public void sendUpdatePacket() {
            updateCalls++;
        }

        private void resetNotifications() {
            dirtyCalls = 0;
            updateCalls = 0;
        }
    }

    private static final class CountingCompactingDrawerTile extends CompactingDrawerTile {
        private int dirtyCalls;
        private int updateCalls;

        @Override
        public void markDirty() {
            dirtyCalls++;
        }

        @Override
        public void sendUpdatePacket() {
            updateCalls++;
        }

        private void resetNotifications() {
            dirtyCalls = 0;
            updateCalls = 0;
        }
    }
}
