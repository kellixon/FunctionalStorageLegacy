package com.xinyihl.functionalstoragelegacy.common.inventory;

import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.api.storage.TransferResult;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CompactingInventoryHandlerTest {

    private static final AtomicInteger NEXT_ITEM_ID = new AtomicInteger(31000);

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    public void insertionAndExtractionConserveSharedBaseUnits() {
        Item compact = new Item();
        Item base = new Item();
        TestHandler handler = configuredHandler(1D, compact, base);
        handler.changes = 0;

        TransferResult<BigItemStack> simulated = handler.insertIntoSlot(
                0, new BigItemStack(new ItemStack(compact), 2L), StorageAction.SIMULATE);
        assertEquals(2L, simulated.getProcessedAmount());
        assertEquals(0L, handler.getStoredBaseAmount());
        assertEquals(0, handler.changes);

        handler.insertIntoSlot(
                0, new BigItemStack(new ItemStack(compact), 2L), StorageAction.EXECUTE);
        assertEquals(18L, handler.getStoredBaseAmount());
        assertEquals(2L, handler.getSlotSnapshot(0).getAmount());
        assertEquals(18L, handler.getSlotSnapshot(1).getAmount());

        handler.extractFromSlot(1, 5L, StorageAction.EXECUTE);
        assertEquals(13L, handler.getStoredBaseAmount());
        assertEquals(1L, handler.getSlotSnapshot(0).getAmount());
        assertEquals(13L, handler.getSlotSnapshot(1).getAmount());
    }

    @Test
    public void simulationCannotConfigureTiersOrWriteNotificationState() {
        TestHandler handler = new TestHandler(2, 1D);
        Item item = new Item();
        String beforeNbt = handler.serializeNBT().toString();

        TransferResult<BigItemStack> result = handler.insertIntoSlot(
                0, new BigItemStack(new ItemStack(item), 4L), StorageAction.SIMULATE);

        assertEquals(0L, result.getProcessedAmount());
        assertFalse(handler.isConfigured());
        assertFalse(handler.getSlotSnapshot(0).hasTemplate());
        assertEquals(beforeNbt, handler.serializeNBT().toString());
        assertEquals(0, handler.changes);
    }

    @Test
    public void tierDefinitionsAndReturnedListsAreDetachedAndImmutable() {
        TestHandler handler = new TestHandler(2, 1D);
        Item item = new Item();
        ItemStack source = new ItemStack(item, 16);
        CompactingInventoryHandler.Tier tier = new CompactingInventoryHandler.Tier(source, 9L);
        handler.configureTiers(Arrays.asList(tier));

        source.setCount(3);
        ItemStack returned = handler.getTiers().get(0).getTemplate();
        returned.setCount(22);
        assertEquals(1, handler.getTiers().get(0).getTemplate().getCount());
        assertEquals(9L, handler.getTiers().get(0).getBaseUnits());
        try {
            handler.getTiers().add(CompactingInventoryHandler.Tier.empty());
            throw new AssertionError("tier list must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void lockedStorageRetainsConfigurationAtZero() {
        Item compact = new Item();
        Item base = new Item();
        TestHandler handler = configuredHandler(1D, compact, base);
        handler.locked = true;

        handler.insertIntoSlot(
                1, new BigItemStack(new ItemStack(base), 5L), StorageAction.EXECUTE);
        handler.extractFromSlot(1, 5L, StorageAction.EXECUTE);

        assertEquals(0L, handler.getStoredBaseAmount());
        assertTrue(handler.isConfigured());
        assertTrue(handler.getSlotSnapshot(0).hasTemplate());
        assertTrue(handler.getSlotSnapshot(0).isEmpty());
    }

    @Test
    public void voidUsesTierConversionAndHasNoVirtualSlot() {
        Item compact = new Item();
        Item base = new Item();
        TestHandler handler = configuredHandler(1D / 64D, compact, base);
        handler.voidOverflow = true;
        handler.changes = 0;

        TransferResult<BigItemStack> result = handler.insertIntoSlot(
                0, new BigItemStack(new ItemStack(compact), 2L), StorageAction.EXECUTE);

        assertEquals(2L, result.getProcessedAmount());
        assertEquals(9L, handler.getStoredBaseAmount());
        assertEquals(1L, handler.getSlotSnapshot(0).getAmount());
        assertEquals(2, handler.getSlotCount());
        assertEquals(2, handler.getSlots());
        assertEquals(0L, handler.insertIntoSlot(
                0, new BigItemStack(new ItemStack(new Item()), 1L), StorageAction.EXECUTE)
                .getProcessedAmount());
    }

    @Test
    public void creativeAndCapacityEdgesAreSaturated() {
        Item compact = new Item();
        Item base = new Item();
        TestHandler creative = configuredHandler(1D, compact, base);
        creative.creative = true;
        creative.changes = 0;

        assertEquals(Long.MAX_VALUE, creative.getSlotCapacity(0));
        assertEquals(Long.MAX_VALUE, creative.getSlotSnapshot(0).getAmount());
        assertEquals(Long.MAX_VALUE, creative.insertIntoSlot(
                0,
                new BigItemStack(new ItemStack(compact), Long.MAX_VALUE),
                StorageAction.SIMULATE).getProcessedAmount());
        assertEquals(0, creative.changes);
        assertEquals(Long.MAX_VALUE, creative.extractFromSlot(
                1, Long.MAX_VALUE, StorageAction.EXECUTE).getProcessedAmount());
        assertEquals(0, creative.changes);

        TestHandler infinite = configuredHandler(Double.POSITIVE_INFINITY, compact, base);
        assertEquals(Long.MAX_VALUE, infinite.getTotalBaseCapacity());
        TestHandler notANumber = configuredHandler(Double.NaN, compact, base);
        assertEquals(0L, notANumber.getTotalBaseCapacity());
    }

    @Test
    public void runtimeUnlockClearsOnlyEmptyTiersAndNotifiesOnce() {
        Item oldType = new Item();
        Item newType = new Item();
        TestHandler empty = new TestHandler(1, 1D);
        empty.locked = true;
        empty.configureTiers(Arrays.asList(
                new CompactingInventoryHandler.Tier(new ItemStack(oldType), 1L)));
        empty.changes = 0;

        empty.locked = false;
        empty.setLockFilters(false);

        assertFalse(empty.isConfigured());
        assertEquals(1, empty.changes);
        assertEquals(0, empty.serializeNBT().getCompoundTag("StorageV2")
                .getTagList("Tiers", 10).tagCount());
        empty.configureTiers(Arrays.asList(
                new CompactingInventoryHandler.Tier(new ItemStack(newType), 1L)));
        assertEquals(2L, empty.insertIntoSlot(
                0,
                new BigItemStack(new ItemStack(newType), 2L),
                StorageAction.EXECUTE).getProcessedAmount());

        TestHandler populated = configuredHandler(1D, oldType, newType);
        populated.insertIntoSlot(
                1, new BigItemStack(new ItemStack(newType), 5L), StorageAction.EXECUTE);
        populated.locked = false;
        populated.changes = 0;
        populated.setLockFilters(false);
        assertTrue(populated.isConfigured());
        assertEquals(5L, populated.getStoredBaseAmount());
        assertEquals(0, populated.changes);
    }

    @Test
    public void creativeEmptyTiersSurviveUnlockAndNbtRoundTrip() {
        Item stored = registeredItem("creative_empty_tier");
        TestHandler source = new TestHandler(1, 1D);
        source.creative = true;
        source.locked = true;
        source.configureTiers(Arrays.asList(
                new CompactingInventoryHandler.Tier(new ItemStack(stored), 1L)));
        source.changes = 0;

        source.locked = false;
        source.setLockFilters(false);

        assertTrue(source.isConfigured());
        assertEquals(0L, source.getStoredBaseAmount());
        assertEquals(0, source.changes);
        NBTTagCompound serialized = source.serializeNBT();
        assertEquals(1, serialized.getCompoundTag("StorageV2")
                .getTagList("Tiers", 10).tagCount());

        TestHandler restored = new TestHandler(1, 1D);
        restored.creative = true;
        restored.deserializeNBT(serialized);
        assertTrue(restored.isConfigured());
        assertEquals(0L, restored.getStoredBaseAmount());
        assertTrue(restored.getSlotSnapshot(0).hasTemplate());
        assertTrue(restored.getSlotSnapshot(0).isSameType(new ItemStack(stored)));
    }

    @Test
    public void unlockedEmptyExtractionClearsTiersAndLegacyNbtIsIgnored() {
        Item compact = new Item();
        Item base = new Item();
        TestHandler handler = configuredHandler(1D, compact, base);
        handler.insertIntoSlot(
                1, new BigItemStack(new ItemStack(base), 3L), StorageAction.EXECUTE);
        handler.extractFromSlot(1, 3L, StorageAction.EXECUTE);
        assertFalse(handler.isConfigured());

        handler.configureTiers(Arrays.asList(
                new CompactingInventoryHandler.Tier(new ItemStack(compact), 9L),
                new CompactingInventoryHandler.Tier(new ItemStack(base), 1L)));
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setLong("TotalBase", 99L);
        handler.deserializeNBT(legacy);
        assertFalse(handler.isConfigured());
        assertEquals(0L, handler.getStoredBaseAmount());
    }

    @Test
    public void storageV2RoundTripsBaseAmountTierUnitsAndTags() {
        Item compact = registeredItem("compact_round_trip");
        Item base = registeredItem("base_round_trip");
        ItemStack compactTemplate = new ItemStack(compact, 1, 4);
        compactTemplate.setTagCompound(new NBTTagCompound());
        compactTemplate.getTagCompound().setString("tier", "compact");
        ItemStack baseTemplate = new ItemStack(base, 1, 2);
        baseTemplate.setTagCompound(new NBTTagCompound());
        baseTemplate.getTagCompound().setString("tier", "base");
        long storedAmount = (long) Integer.MAX_VALUE + 77L;
        TestHandler source = new TestHandler(2, Double.POSITIVE_INFINITY);
        source.configureTiers(Arrays.asList(
                new CompactingInventoryHandler.Tier(compactTemplate, 9L),
                new CompactingInventoryHandler.Tier(baseTemplate, 1L)));
        source.insertIntoSlot(
                1, new BigItemStack(baseTemplate, storedAmount), StorageAction.EXECUTE);

        NBTTagCompound serialized = source.serializeNBT();
        TestHandler restored = new TestHandler(2, Double.POSITIVE_INFINITY);
        restored.deserializeNBT(serialized);

        assertEquals(storedAmount, serialized.getCompoundTag("StorageV2")
                .getLong("BaseAmount"));
        assertEquals(2, serialized.getCompoundTag("StorageV2")
                .getTagList("Tiers", 10).tagCount());
        assertTrue(restored.isConfigured());
        assertEquals(storedAmount, restored.getStoredBaseAmount());
        assertEquals(9L, restored.getTiers().get(0).getBaseUnits());
        assertEquals(1L, restored.getTiers().get(1).getBaseUnits());
        assertEquals(4, restored.getTiers().get(0).getTemplate().getMetadata());
        assertEquals("compact", restored.getTiers().get(0).getTemplate()
                .getTagCompound().getString("tier"));
        assertEquals(2, restored.getTiers().get(1).getTemplate().getMetadata());
        assertEquals("base", restored.getTiers().get(1).getTemplate()
                .getTagCompound().getString("tier"));
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

    private static TestHandler configuredHandler(
            double multiplier, Item compact, Item base) {
        TestHandler handler = new TestHandler(2, multiplier);
        handler.configureTiers(Arrays.asList(
                new CompactingInventoryHandler.Tier(new ItemStack(compact), 9L),
                new CompactingInventoryHandler.Tier(new ItemStack(base), 1L)));
        return handler;
    }

    private static final class TestHandler extends CompactingInventoryHandler {
        private final double multiplier;
        private int changes;
        private boolean locked;
        private boolean voidOverflow;
        private boolean creative;

        private TestHandler(int slots, double multiplier) {
            super(slots);
            this.multiplier = multiplier;
        }

        @Override
        public void onChange() {
            changes++;
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
    }
}
