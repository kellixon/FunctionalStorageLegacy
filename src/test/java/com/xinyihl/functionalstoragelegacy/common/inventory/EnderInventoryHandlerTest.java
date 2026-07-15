package com.xinyihl.functionalstoragelegacy.common.inventory;

import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnderInventoryHandlerTest {

    private static final AtomicInteger NEXT_ITEM_ID = new AtomicInteger(26000);

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    public void runtimeUnlockClearsRetainedFilterAndAcceptsAnotherType() {
        EnderInventoryHandler handler = new EnderInventoryHandler() {
        };
        Item original = new Item();
        Item replacement = new Item();
        handler.insertIntoSlot(
                0,
                new BigItemStack(new ItemStack(original), 4L),
                StorageAction.EXECUTE);
        handler.setLocked(true);
        handler.extractFromSlot(0, 4L, StorageAction.EXECUTE);
        assertTrue(handler.getSlotSnapshot(0).hasTemplate());
        handler.setUpdate();

        handler.setLocked(false);

        assertFalse(handler.isLocked());
        assertFalse(handler.getSlotSnapshot(0).hasTemplate());
        assertTrue(handler.needUpdate());
        NBTTagCompound serialized = handler.serializeNBT();
        assertEquals(0, serialized.getCompoundTag("StorageV2")
                .getTagList("Items", 10).tagCount());
        assertEquals(3L, handler.insertIntoSlot(
                0,
                new BigItemStack(new ItemStack(replacement), 3L),
                StorageAction.EXECUTE).getProcessedAmount());
        assertTrue(handler.getSlotSnapshot(0).isSameType(new ItemStack(replacement)));
    }

    @Test
    public void lockedEmptyFilterSurvivesFullNbtRoundTrip() {
        Item stored = registeredItem("ender_locked_filter");
        EnderInventoryHandler source = new EnderInventoryHandler() {
        };
        source.insertIntoSlot(
                0,
                new BigItemStack(new ItemStack(stored), 2L),
                StorageAction.EXECUTE);
        source.setLocked(true);
        source.extractFromSlot(0, 2L, StorageAction.EXECUTE);

        NBTTagCompound serialized = source.serializeNBTFull();
        assertTrue(serialized.getBoolean("Locked"));
        assertEquals(0L, serialized.getCompoundTag("StorageV2")
                .getTagList("Items", 10).getCompoundTagAt(0).getLong("Amount"));

        EnderInventoryHandler restored = new EnderInventoryHandler() {
        };
        restored.deserializeNBTFull(serialized);

        assertTrue(restored.isLocked());
        assertTrue(restored.getSlotSnapshot(0).hasTemplate());
        assertEquals(0L, restored.getSlotSnapshot(0).getAmount());
        assertTrue(restored.getSlotSnapshot(0).isSameType(new ItemStack(stored)));
    }

    private static Item registeredItem(String path) {
        int id = NEXT_ITEM_ID.getAndIncrement();
        Item item = new Item().setRegistryName(new ResourceLocation(
                "functionalstoragelegacy_test", path + id));
        @SuppressWarnings("unchecked")
        ForgeRegistry<Item> registry = (ForgeRegistry<Item>) ForgeRegistries.ITEMS;
        boolean wasFrozen = registry.isLocked();
        if (wasFrozen) {
            registry.unfreeze();
        }
        try {
            registry.register(item);
            return item;
        } finally {
            if (wasFrozen) {
                registry.freeze();
            }
        }
    }
}
