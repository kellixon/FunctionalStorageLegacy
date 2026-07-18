package com.xinyihl.functionalstoragelegacy.common.inventory;

import com.xinyihl.functionalstoragelegacy.api.storage.*;
import com.xinyihl.functionalstoragelegacy.common.inventory.base.BigInventoryHandler;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.registries.ForgeRegistry;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class BigInventoryHandlerTest {

    private static final AtomicInteger NEXT_ITEM_ID = new AtomicInteger(30000);

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    public void normalStorageUsesRealSlotsAndSimulationHasNoSideEffects() {
        TestHandler handler = new TestHandler(1, 1D);
        Item item = new Item();
        BigItemStack request = new BigItemStack(new ItemStack(item), 70L);
        String beforeNbt = handler.serializeNBT().toString();

        TransferResult<BigItemStack, ItemStorageKey> simulated = handler.insert(
                0, request, StorageAction.SIMULATE);

        assertEquals(64L, simulated.getProcessedAmount());
        assertFalse(handler.getSnapshot(0).hasTemplate());
        assertEquals(beforeNbt, handler.serializeNBT().toString());
        assertEquals(0, handler.changes);
        assertEquals(1, handler.getStorageCount());
        assertEquals(1, handler.getSlots());

        TransferResult<BigItemStack, ItemStorageKey> executed = handler.insert(
                0, request, StorageAction.EXECUTE);
        assertEquals(64L, executed.getProcessedAmount());
        assertEquals(6L, executed.getRemainingAmount());
        assertEquals(64L, handler.getSnapshot(0).getAmount());
        assertEquals(1, handler.changes);

        ItemStack detached = handler.getSnapshot(0).getTemplate();
        detached.setCount(32);
        assertEquals(1, handler.getSnapshot(0).getTemplate().getCount());
        assertEquals(0L, handler.getCapacity(1));
        assertEquals(0L, handler.insert(
                1, request, StorageAction.EXECUTE).getProcessedAmount());
    }

    @Test
    public void lockedStorageRetainsAndEnforcesItsZeroAmountFilter() {
        TestHandler handler = new TestHandler(1, 1D);
        handler.locked = true;
        Item accepted = new Item();
        Item rejected = new Item();

        assertEquals(0L, handler.insert(
                0, new BigItemStack(new ItemStack(accepted), 4L), StorageAction.EXECUTE)
                .getProcessedAmount());
        assertFalse(handler.getSnapshot(0).hasTemplate());

        assertTrue(handler.setSlotFilter(0, new ItemStack(accepted)));
        handler.changes = 0;
        handler.insert(
                0, new BigItemStack(new ItemStack(accepted), 4L), StorageAction.EXECUTE);
        handler.extract(0, 4L, StorageAction.EXECUTE);

        BigItemStack retained = handler.getSnapshot(0);
        assertTrue(retained.isEmpty());
        assertTrue(retained.hasTemplate());
        assertTrue(retained.isSameType(new ItemStack(accepted)));
        assertEquals(0L, handler.insert(
                0, new BigItemStack(new ItemStack(rejected), 1L), StorageAction.EXECUTE)
                .getProcessedAmount());
    }

    @Test
    public void voidConsumesOnlyCompatibleOverflowWithoutAddingAPseudoSlot() {
        TestHandler handler = new TestHandler(1, 1D);
        handler.voidOverflow = true;
        Item accepted = new Item();
        Item rejected = new Item();

        TransferResult<BigItemStack, ItemStorageKey> inserted = handler.insert(
                0, new BigItemStack(new ItemStack(accepted), 100L), StorageAction.EXECUTE);
        assertEquals(100L, inserted.getProcessedAmount());
        assertEquals(64L, handler.getSnapshot(0).getAmount());
        assertEquals(1, handler.getStorageCount());
        assertEquals(0L, handler.insert(
                0, new BigItemStack(new ItemStack(rejected), 20L), StorageAction.EXECUTE)
                .getProcessedAmount());

        TestHandler simulated = new TestHandler(1, 1D);
        simulated.voidOverflow = true;
        String beforeNbt = simulated.serializeNBT().toString();
        assertEquals(100L, simulated.insert(
                0, new BigItemStack(new ItemStack(accepted), 100L), StorageAction.SIMULATE)
                .getProcessedAmount());
        assertFalse(simulated.getSnapshot(0).hasTemplate());
        assertEquals(beforeNbt, simulated.serializeNBT().toString());
        assertEquals(0, simulated.changes);
    }

    @Test
    public void creativeAndCapacityEdgesSaturateWithoutSimulationMutation() {
        Item item = new Item();
        TestHandler creative = new TestHandler(1, 1D);
        creative.creative = true;
        BigItemStack request = new BigItemStack(new ItemStack(item), Long.MAX_VALUE);

        assertEquals(Long.MAX_VALUE, creative.insert(
                0, request, StorageAction.SIMULATE).getProcessedAmount());
        assertFalse(creative.getSnapshot(0).hasTemplate());
        assertEquals(0, creative.changes);

        creative.insert(0, request, StorageAction.EXECUTE);
        assertEquals(Long.MAX_VALUE, creative.getSnapshot(0).getAmount());
        assertEquals(Long.MAX_VALUE, creative.getCapacity(0));
        int changes = creative.changes;
        assertEquals(Long.MAX_VALUE, creative.extract(
                0, Long.MAX_VALUE, StorageAction.EXECUTE).getProcessedAmount());
        assertEquals(changes, creative.changes);

        TestHandler infinite = new TestHandler(1, Double.POSITIVE_INFINITY);
        assertEquals(Long.MAX_VALUE, infinite.getCapacity(0));
        TestHandler notANumber = new TestHandler(1, Double.NaN);
        assertEquals(0L, notANumber.getCapacity(0));
    }

    @Test
    public void creativeInsertPreservesExistingFiniteItemAmountWithoutEvent() {
        Item item = new Item();
        TestHandler handler = new TestHandler(1, 1D);
        BigItemStack request = new BigItemStack(new ItemStack(item), 7L);
        handler.insert(0, request, StorageAction.EXECUTE);
        int changes = handler.changes;

        handler.creative = true;
        assertEquals(7L, handler.insert(
                0, request, StorageAction.EXECUTE).getProcessedAmount());
        assertEquals(Long.MAX_VALUE, handler.getSnapshot(0).getAmount());
        assertEquals(changes, handler.changes);

        handler.creative = false;
        assertEquals(7L, handler.getSnapshot(0).getAmount());
        assertEquals(changes, handler.changes);
    }

    @Test
    public void equivalentItemsShareAConfiguredFilter() {
        Item first = registeredItem("equivalent_first");
        Item second = registeredItem("equivalent_second");
        String oreName = "functionalStorageLegacyEquivalentTest";
        OreDictionary.registerOre(oreName, new ItemStack(first));
        OreDictionary.registerOre(oreName, new ItemStack(second));

        TestHandler handler = new TestHandler(1, 1D);
        handler.equivalent = true;
        assertTrue(handler.setSlotFilter(0, new ItemStack(first)));
        TransferResult<BigItemStack, ItemStorageKey> result = handler.insert(
                0, new BigItemStack(new ItemStack(second), 8L), StorageAction.EXECUTE);

        assertEquals(8L, result.getProcessedAmount());
        assertEquals(8L, handler.getSnapshot(0).getAmount());
        assertTrue(handler.getSnapshot(0).isSameType(new ItemStack(first)));
    }

    @Test
    public void routedInsertionPrefersExactThenEquivalentThenEmptyWithoutSimulationMutation() {
        Item requestItem = registeredItem("routed_exact");
        Item equivalentItem = registeredItem("routed_equivalent");
        String oreName = "functionalStorageLegacyRoutedEquivalentTest"
                + NEXT_ITEM_ID.get();
        OreDictionary.registerOre(oreName, new ItemStack(requestItem));
        OreDictionary.registerOre(oreName, new ItemStack(equivalentItem));

        TestHandler handler = new TestHandler(3, 1D);
        handler.equivalent = true;
        assertTrue(handler.setSlotFilter(0, new ItemStack(equivalentItem)));
        assertTrue(handler.setSlotFilter(1, new ItemStack(requestItem)));
        handler.changes = 0;
        BigItemStack request = new BigItemStack(new ItemStack(requestItem), 140L);
        String beforeNbt = handler.serializeNBT().toString();

        TransferResult<BigItemStack, ItemStorageKey> simulated = handler.insertRouted(
                request, StorageAction.SIMULATE);

        assertEquals(140L, simulated.getProcessedAmount());
        assertEquals(beforeNbt, handler.serializeNBT().toString());
        assertEquals(0, handler.changes);
        assertEquals(0L, handler.getSnapshot(0).getAmount());
        assertEquals(0L, handler.getSnapshot(1).getAmount());
        assertFalse(handler.getSnapshot(2).hasTemplate());

        TransferResult<BigItemStack, ItemStorageKey> executed = handler.insertRouted(
                request, StorageAction.EXECUTE);

        assertEquals(140L, executed.getProcessedAmount());
        assertEquals(64L, handler.getSnapshot(1).getAmount());
        assertTrue(handler.getSnapshot(1).isSameType(new ItemStack(requestItem)));
        assertEquals(64L, handler.getSnapshot(0).getAmount());
        assertTrue(handler.getSnapshot(0).isSameType(new ItemStack(equivalentItem)));
        assertEquals(12L, handler.getSnapshot(2).getAmount());
        assertTrue(handler.getSnapshot(2).isSameType(new ItemStack(requestItem)));
    }

    @Test
    public void runtimeUnlockClearsOnlyZeroAmountFiltersAndNotifiesOnce() {
        Item emptyFilter = new Item();
        Item populated = new Item();
        Item replacement = new Item();
        TestHandler handler = new TestHandler(2, 1D);
        handler.locked = true;
        handler.setSlotFilter(0, new ItemStack(emptyFilter));
        handler.setSlotFilter(1, new ItemStack(populated));
        handler.insert(
                1, new BigItemStack(new ItemStack(populated), 7L), StorageAction.EXECUTE);
        handler.changes = 0;

        handler.locked = false;
        handler.setLockFilters(false);

        assertFalse(handler.getSnapshot(0).hasTemplate());
        assertEquals(7L, handler.getSnapshot(1).getAmount());
        assertTrue(handler.getSnapshot(1).isSameType(new ItemStack(populated)));
        assertEquals(1, handler.changes);
        assertEquals(1, handler.serializeNBT().getCompoundTag("StorageV2")
                .getTagList("Items", 10).tagCount());
        assertEquals(1L, handler.insert(
                0,
                new BigItemStack(new ItemStack(replacement), 1L),
                StorageAction.EXECUTE).getProcessedAmount());
    }

    @Test
    public void legacyNbtIsIgnoredAndMissingV2ClearsState() {
        TestHandler handler = new TestHandler(1, 1D);
        handler.insert(
                0, new BigItemStack(new ItemStack(new Item()), 3L), StorageAction.EXECUTE);
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setTag("BigItems", new NBTTagCompound());

        handler.deserializeNBT(legacy);

        assertFalse(handler.getSnapshot(0).hasTemplate());
        assertEquals(0L, handler.getSnapshot(0).getAmount());
    }

    @Test
    public void storageV2RoundTripsTagsFiltersAndLongAmounts() {
        Item item = registeredItem("big_round_trip");
        ItemStack tagged = new ItemStack(item, 1, 3);
        tagged.setTagCompound(new NBTTagCompound());
        tagged.getTagCompound().setString("marker", "kept");
        long storedAmount = (long) Integer.MAX_VALUE + 123L;
        TestHandler source = new TestHandler(2, Double.POSITIVE_INFINITY);
        source.locked = true;
        assertTrue(source.setSlotFilter(0, tagged));
        assertTrue(source.setSlotFilter(1, tagged));
        source.insert(
                0, new BigItemStack(tagged, storedAmount), StorageAction.EXECUTE);

        NBTTagCompound serialized = source.serializeNBT();
        TestHandler restored = new TestHandler(2, Double.POSITIVE_INFINITY);
        restored.locked = true;
        restored.deserializeNBT(serialized);

        assertEquals(2, serialized.getCompoundTag("StorageV2")
                .getTagList("Items", 10).tagCount());
        assertEquals(storedAmount, restored.getSnapshot(0).getAmount());
        assertEquals(3, restored.getSnapshot(0).getTemplate().getMetadata());
        assertEquals("kept", restored.getSnapshot(0).getTemplate()
                .getTagCompound().getString("marker"));
        assertTrue(restored.getSnapshot(1).hasTemplate());
        assertTrue(restored.getSnapshot(1).isEmpty());
        assertEquals("kept", restored.getSnapshot(1).getTemplate()
                .getTagCompound().getString("marker"));
    }

    @Test
    public void eventsCarryExactSingleAndBatchTransitionsAndCanBeReleased() {
        Item first = new Item();
        Item second = new Item();
        TestHandler handler = new TestHandler(2, 2D);
        List<StorageChange<BigItemStack, ItemStorageKey>> events = new ArrayList<>();
        StorageSubscription subscription = handler.subscribe(events::add);

        handler.insert(
                0, new BigItemStack(new ItemStack(first), 5L), StorageAction.SIMULATE);
        assertTrue(events.isEmpty());
        handler.insert(
                0, new BigItemStack(new ItemStack(first), 5L), StorageAction.EXECUTE);
        assertEquals(1, events.size());
        assertSingleItemDelta(events.get(0), 0, 0L, 5L, false, true);

        handler.locked = true;
        handler.setSlotFilter(1, new ItemStack(second));
        events.clear();
        handler.locked = false;
        handler.setLockFilters(false);
        assertEquals(1, events.size());
        assertEquals(1, events.get(0).getEntries().size());
        assertEquals(1, events.get(0).getEntries().get(0).getIndex());
        assertTrue(events.get(0).getEntries().get(0).getBefore().hasTemplate());
        assertEquals(0L, events.get(0).getEntries().get(0).getBefore().getAmount());
        assertFalse(events.get(0).getEntries().get(0).getAfter().hasTemplate());

        handler.locked = true;
        handler.setSlotFilter(1, new ItemStack(first));
        events.clear();
        handler.setSlotFilter(1, new ItemStack(second));
        assertEquals(1, events.size());
        assertEquals(1, events.get(0).getEntries().size());
        assertTrue(events.get(0).getEntries().get(0).getBefore()
                .isSameType(new BigItemStack(new ItemStack(first), 0L)));
        assertTrue(events.get(0).getEntries().get(0).getAfter()
                .isSameType(new BigItemStack(new ItemStack(second), 0L)));

        subscription.close();
        assertTrue(subscription.isClosed());
        handler.extract(0, 1L, StorageAction.EXECUTE);
        assertEquals(1, events.size());
    }

    @Test
    public void configurationAndOnlineDeserializePublishOnlyResetsWhenChanged() {
        Item item = registeredItem("event_reset");
        TestHandler source = new TestHandler(1, 1D);
        source.insert(
                0, new BigItemStack(new ItemStack(item), 3L), StorageAction.EXECUTE);
        NBTTagCompound populated = source.serializeNBT();

        TestHandler handler = new TestHandler(1, 1D);
        handler.deserializeNBT(populated);
        List<StorageChange<BigItemStack, ItemStorageKey>> events = new ArrayList<>();
        handler.subscribe(events::add);
        handler.deserializeNBT(populated);
        assertTrue(events.isEmpty());
        handler.deserializeNBT(new NBTTagCompound());
        assertEquals(1, events.size());
        assertTrue(events.get(0).isReset());
        handler.onChange(StorageChange.reset());
        handler.locked = true;
        handler.applyLockConfiguration(true);
        assertEquals(3, events.size());
        assertTrue(events.get(1).isReset());
        assertTrue(events.get(2).isReset());
    }

    @Test
    public void pureVoidAndRepeatedCreativeOperationsDoNotPublishFalseDeltas() {
        Item item = new Item();
        TestHandler voiding = new TestHandler(1, 1D);
        voiding.voidOverflow = true;
        List<StorageChange<BigItemStack, ItemStorageKey>> voidEvents = new ArrayList<>();
        voiding.subscribe(voidEvents::add);
        voiding.insert(
                0, new BigItemStack(new ItemStack(item), 64L), StorageAction.EXECUTE);
        voiding.insert(
                0, new BigItemStack(new ItemStack(item), 20L), StorageAction.EXECUTE);
        assertEquals(1, voidEvents.size());

        TestHandler creative = new TestHandler(1, 1D);
        creative.creative = true;
        List<StorageChange<BigItemStack, ItemStorageKey>> creativeEvents = new ArrayList<>();
        creative.subscribe(creativeEvents::add);
        BigItemStack request = new BigItemStack(new ItemStack(item), 4L);
        creative.insert(0, request, StorageAction.EXECUTE);
        creative.insert(0, request, StorageAction.EXECUTE);
        creative.extract(0, 4L, StorageAction.EXECUTE);
        assertEquals(1, creativeEvents.size());
    }

    private static void assertSingleItemDelta(
            StorageChange<BigItemStack, ItemStorageKey> change,
            int index,
            long beforeAmount,
            long afterAmount,
            boolean beforeTemplate,
            boolean afterTemplate) {
        assertTrue(change.isDelta());
        assertEquals(1, change.getEntries().size());
        StorageChange.Entry<BigItemStack, ItemStorageKey> entry = change.getEntries().get(0);
        assertEquals(index, entry.getIndex());
        assertEquals(beforeAmount, entry.getBefore().getAmount());
        assertEquals(afterAmount, entry.getAfter().getAmount());
        assertEquals(beforeTemplate, entry.getBefore().hasTemplate());
        assertEquals(afterTemplate, entry.getAfter().hasTemplate());
    }

    private static Item registeredItem(String path) {
        int id = NEXT_ITEM_ID.getAndIncrement();
        ResourceLocation name = new ResourceLocation(
                "functionalstoragelegacy_test", path + id);
        Item item = new Item().setRegistryName(name);
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

    private static final class TestHandler extends BigInventoryHandler {
        private final double multiplier;
        private int changes;
        private boolean locked;
        private boolean voidOverflow;
        private boolean creative;
        private boolean equivalent;

        private TestHandler(int slots, double multiplier) {
            super(slots);
            this.multiplier = multiplier;
            subscribe(change -> changes++);
        }

        @Override
        public double getMultiplier() {
            return multiplier;
        }

        @Override
        public boolean isLocked() {
            return locked;
        }

        @Override
        public boolean voidsOverflow() {
            return voidOverflow;
        }

        @Override
        public boolean isCreative() {
            return creative;
        }

        @Override
        protected boolean allowsEquivalentItems() {
            return equivalent;
        }
    }
}
