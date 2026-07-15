package com.xinyihl.functionalstoragelegacy.common.inventory;

import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.api.storage.TransferResult;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ArmoryCabinetInventoryHandlerTest {

    private static final AtomicInteger NEXT_ITEM_ID = new AtomicInteger(27000);

    @Test
    public void coreTransactionsUseCapacityOneAndNeverRouteStrictSlots() {
        TestHandler handler = new TestHandler(2);
        Item armoryItem = new Item().setMaxStackSize(1);
        ItemStack source = new ItemStack(armoryItem);
        source.setTagCompound(new NBTTagCompound());
        source.getTagCompound().setString("owner", "request");
        BigItemStack request = new BigItemStack(source, 4L);

        TransferResult<BigItemStack> invalid = handler.insertIntoSlot(
                2, request, StorageAction.EXECUTE);
        assertEquals(0L, invalid.getProcessedAmount());
        assertEquals(0L, handler.getSlotCapacity(2));

        TransferResult<BigItemStack> simulated = handler.insertIntoSlot(
                0, request, StorageAction.SIMULATE);
        assertEquals(1L, simulated.getProcessedAmount());
        assertTrue(handler.getSlotSnapshot(0).isEmpty());
        assertEquals(0, handler.changes);

        handler.insertIntoSlot(0, request, StorageAction.EXECUTE);
        source.getTagCompound().setString("owner", "mutated");
        assertEquals(1L, handler.getSlotCapacity(0));
        assertEquals(1L, handler.getSlotSnapshot(0).getAmount());
        assertEquals("request", handler.getSlotSnapshot(0).getTemplate()
                .getTagCompound().getString("owner"));

        assertEquals(0L, handler.insertIntoSlot(
                0,
                new BigItemStack(new ItemStack(new Item().setMaxStackSize(1)), 1L),
                StorageAction.EXECUTE).getProcessedAmount());
        assertTrue(handler.getSlotSnapshot(1).isEmpty());
        assertEquals(0L, handler.insertIntoSlot(
                1,
                new BigItemStack(new ItemStack(new Item().setMaxStackSize(64)), 1L),
                StorageAction.EXECUTE).getProcessedAmount());

        assertEquals(1L, handler.extractFromSlot(
                0, Long.MAX_VALUE, StorageAction.SIMULATE).getProcessedAmount());
        assertEquals(1L, handler.getSlotSnapshot(0).getAmount());
        assertEquals(1, handler.changes);
        assertEquals(1L, handler.extractFromSlot(
                0, Long.MAX_VALUE, StorageAction.EXECUTE).getProcessedAmount());
        assertTrue(handler.getSlotSnapshot(0).isEmpty());
        assertEquals(2, handler.changes);
    }

    @Test
    public void insertionIsStrictlySlottedAndReadsAreDefensiveCopies() {
        TestHandler handler = new TestHandler(2);
        Item firstItem = new Item().setMaxStackSize(1);
        Item secondItem = new Item().setMaxStackSize(1);
        ItemStack first = new ItemStack(firstItem);
        first.setTagCompound(new NBTTagCompound());
        first.getTagCompound().setString("owner", "stored");

        assertTrue(handler.insertItem(0, first, false).isEmpty());
        ItemStack returned = handler.getStackInSlot(0);
        returned.getTagCompound().setString("owner", "mutated");
        returned.setCount(0);
        assertEquals("stored", handler.getStackInSlot(0)
                .getTagCompound().getString("owner"));

        ItemStack rejected = handler.insertItem(0, new ItemStack(secondItem), false);
        assertFalse(rejected.isEmpty());
        assertTrue(handler.getStackInSlot(1).isEmpty());
        assertTrue(handler.insertItem(1, new ItemStack(secondItem), true).isEmpty());
        assertTrue(handler.getStackInSlot(1).isEmpty());
        assertFalse(handler.insertItem(-1, new ItemStack(secondItem), false).isEmpty());
        assertEquals(0, handler.getSlotLimit(-1));
        assertEquals(1, handler.changes);
    }

    @Test
    public void storageV2RoundTripsAndLegacySlotsAreIgnored() {
        Item item = registeredItem("armory_round_trip").setMaxStackSize(1);
        ItemStack stack = new ItemStack(item, 1, 5);
        stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setString("marker", "kept");
        TestHandler source = new TestHandler(2);
        source.insertItem(1, stack, false);

        NBTTagCompound serialized = source.serializeNBT();
        assertTrue(serialized.hasKey("StorageV2"));
        assertFalse(serialized.hasKey("Size"));
        assertEquals(1, serialized.getCompoundTag("StorageV2")
                .getTagList("Items", 10).tagCount());
        assertEquals(1L, serialized.getCompoundTag("StorageV2")
                .getTagList("Items", 10).getCompoundTagAt(0).getLong("Amount"));

        TestHandler restored = new TestHandler(2);
        restored.deserializeNBT(serialized);
        assertEquals(5, restored.getStackInSlot(1).getMetadata());
        assertEquals("kept", restored.getStackInSlot(1)
                .getTagCompound().getString("marker"));

        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setInteger("Size", 2);
        legacy.setTag("Slot_0", stack.writeToNBT(new NBTTagCompound()));
        restored.deserializeNBT(legacy);
        assertEquals(0, restored.getFilledSlotCount());
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

    private static final class TestHandler extends ArmoryCabinetInventoryHandler {
        private int changes;

        private TestHandler(int size) {
            super(size);
        }

        @Override
        public void onChange() {
            changes++;
        }
    }
}
