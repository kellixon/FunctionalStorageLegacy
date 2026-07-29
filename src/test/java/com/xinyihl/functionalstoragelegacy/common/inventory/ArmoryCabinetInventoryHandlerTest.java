package com.xinyihl.functionalstoragelegacy.common.inventory;

import com.xinyihl.functionalstoragelegacy.api.storage.*;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ArmoryCabinetInventoryHandlerTest {

    private static final AtomicInteger NEXT_ITEM_ID = new AtomicInteger(27000);

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    public void coreTransactionsUseCapacityOneAndNeverRouteStrictSlots() {
        TestHandler handler = new TestHandler(2);
        Item armoryItem = new Item().setMaxStackSize(1);
        ItemStack source = new ItemStack(armoryItem);
        source.setTagCompound(new NBTTagCompound());
        source.getTagCompound().setString("owner", "request");
        BigItemStack request = new BigItemStack(source, 4L);

        TransferResult<BigItemStack, ItemStorageKey> invalid = handler.insert(
                2, request, StorageAction.EXECUTE);
        assertEquals(0L, invalid.getProcessedAmount());
        assertEquals(0L, handler.getCapacity(2));

        TransferResult<BigItemStack, ItemStorageKey> simulated = handler.insert(
                0, request, StorageAction.SIMULATE);
        assertEquals(1L, simulated.getProcessedAmount());
        assertTrue(handler.getSnapshot(0).isEmpty());
        assertEquals(0, handler.changes);

        handler.insert(0, request, StorageAction.EXECUTE);
        source.getTagCompound().setString("owner", "mutated");
        assertEquals(1L, handler.getCapacity(0));
        assertEquals(1L, handler.getSnapshot(0).getAmount());
        assertEquals("request", handler.getSnapshot(0).getTemplate()
                .getTagCompound().getString("owner"));

        assertEquals(0L, handler.insert(
                0,
                new BigItemStack(new ItemStack(new Item().setMaxStackSize(1)), 1L),
                StorageAction.EXECUTE).getProcessedAmount());
        assertTrue(handler.getSnapshot(1).isEmpty());
        assertEquals(0L, handler.insert(
                1,
                new BigItemStack(new ItemStack(new Item().setMaxStackSize(64)), 1L),
                StorageAction.EXECUTE).getProcessedAmount());

        assertEquals(1L, handler.extract(
                0, Long.MAX_VALUE, StorageAction.SIMULATE).getProcessedAmount());
        assertEquals(1L, handler.getSnapshot(0).getAmount());
        assertEquals(1, handler.changes);
        assertEquals(1L, handler.extract(
                0, Long.MAX_VALUE, StorageAction.EXECUTE).getProcessedAmount());
        assertTrue(handler.getSnapshot(0).isEmpty());
        assertEquals(2, handler.changes);
    }

    @Test
    public void insertionRoutesThroughVirtualKeySlotsAndReadsAreDefensiveCopies() {
        TestHandler handler = new TestHandler(2);
        Item firstItem = new Item().setMaxStackSize(1);
        Item secondItem = new Item().setMaxStackSize(1);
        ItemStack first = new ItemStack(firstItem);
        first.setTagCompound(new NBTTagCompound());
        first.getTagCompound().setString("owner", "stored");

        assertTrue(handler.insertItem(0, first, false).isEmpty());
        assertTrue(handler.getStackInSlot(0).isEmpty());
        ItemStack returned = handler.getStackInSlot(1);
        returned.getTagCompound().setString("owner", "mutated");
        returned.setCount(0);
        assertEquals("stored", handler.getStackInSlot(1)
                .getTagCompound().getString("owner"));

        ItemStack rejected = handler.insertItem(0, new ItemStack(secondItem), false);
        assertTrue(rejected.isEmpty());
        assertEquals(2, handler.getSlots());
        assertFalse(handler.getStackInSlot(0).isEmpty());
        assertFalse(handler.getStackInSlot(1).isEmpty());
        assertFalse(handler.insertItem(1, new ItemStack(secondItem), true).isEmpty());
        assertFalse(handler.insertItem(-1, new ItemStack(secondItem), false).isEmpty());
        assertEquals(0, handler.getSlotLimit(-1));
        assertEquals(2, handler.changes);
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

    @Test
    public void armoryPublishesExactSlotDeltasResetAndRelease() {
        Item first = registeredItem("armory_event_first").setMaxStackSize(1);
        Item second = registeredItem("armory_event_second").setMaxStackSize(1);
        TestHandler handler = new TestHandler(1);
        List<StorageChange<BigItemStack, ItemStorageKey>> events = new ArrayList<>();
        StorageSubscription subscription = handler.subscribe(events::add);

        handler.insert(
                0, new BigItemStack(new ItemStack(first), 1L), StorageAction.SIMULATE);
        assertTrue(events.isEmpty());
        handler.insert(
                0, new BigItemStack(new ItemStack(first), 1L), StorageAction.EXECUTE);
        assertEquals(1, events.size());
        StorageChange.Entry<BigItemStack, ItemStorageKey> inserted =
                events.get(0).getEntries().get(0);
        assertEquals(0, inserted.getIndex());
        assertFalse(inserted.getBefore().hasTemplate());
        assertTrue(inserted.getAfter().isSameType(new BigItemStack(new ItemStack(first), 1L)));

        handler.extract(0, 1L, StorageAction.EXECUTE);
        handler.insert(
                0, new BigItemStack(new ItemStack(second), 1L), StorageAction.EXECUTE);
        assertEquals(3, events.size());
        assertTrue(events.get(1).getEntries().get(0).getBefore().isSameType(
                new BigItemStack(new ItemStack(first), 1L)));
        assertTrue(events.get(2).getEntries().get(0).getAfter().isSameType(
                new BigItemStack(new ItemStack(second), 1L)));

        NBTTagCompound same = handler.serializeNBT();
        events.clear();
        handler.deserializeNBT(same);
        assertTrue(events.isEmpty());
        handler.deserializeNBT(new NBTTagCompound());
        assertEquals(1, events.size());
        assertTrue(events.get(0).isReset());

        subscription.close();
        handler.insert(
                0, new BigItemStack(new ItemStack(first), 1L), StorageAction.EXECUTE);
        assertEquals(1, events.size());
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
            subscribe(change -> changes++);
        }
    }
}
