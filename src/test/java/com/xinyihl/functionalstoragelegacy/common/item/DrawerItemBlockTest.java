package com.xinyihl.functionalstoragelegacy.common.item;

import com.xinyihl.functionalstoragelegacy.TestCapabilities;
import com.xinyihl.functionalstoragelegacy.api.storage.BigFluidStack;
import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.common.block.DrawerWoodType;
import com.xinyihl.functionalstoragelegacy.common.block.WoodDrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.inventory.CompactingInventoryHandler;
import com.xinyihl.functionalstoragelegacy.common.inventory.base.BigInventoryHandler;
import com.xinyihl.functionalstoragelegacy.common.inventory.capability.CompactingStackItemHandler;
import com.xinyihl.functionalstoragelegacy.common.inventory.capability.DrawerStackCapabilityProvider;
import com.xinyihl.functionalstoragelegacy.common.inventory.capability.DrawerStackItemHandler;
import com.xinyihl.functionalstoragelegacy.common.inventory.capability.FluidDrawerStackItemHandler;
import com.xinyihl.functionalstoragelegacy.common.storage.DrawerLayout;
import com.xinyihl.functionalstoragelegacy.util.NumberUtils;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.registries.ForgeRegistry;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DrawerItemBlockTest {

    private static final AtomicInteger NEXT_ITEM_ID = new AtomicInteger(29000);

    @BeforeClass
    public static void bootstrapCapabilities() {
        Bootstrap.register();
        TestCapabilities.itemHandler();
    }

    @Test
    public void itemTooltipFormattingUsesLongSnapshots() {
        Item storedItem = new Item();
        TestBigHandler handler = new TestBigHandler();
        long amount = 5_000_000_123L;
        handler.insert(
                0, new BigItemStack(new ItemStack(storedItem), amount), StorageAction.EXECUTE);

        List<String> lines = DrawerItemBlock.collectStoredItemLines(handler);

        assertEquals(1, lines.size());
        assertEquals(handler.getSnapshot(0).getTemplate().getDisplayName()
                + "x" + NumberUtils.formatCompact(amount), lines.get(0));
    }

    @Test
    public void stackHandlersReadOnlyRootStorageV2AndPreserveLongAmounts() {
        Item normalItem = registeredItem("normal_stack_capability");
        long normalAmount = (long) Integer.MAX_VALUE + 91L;
        TestBigHandler normalSource = new TestBigHandler();
        normalSource.insert(
                0,
                new BigItemStack(new ItemStack(normalItem), normalAmount),
                StorageAction.EXECUTE);
        ItemStack normalDrawer = drawerStackWithTileData(normalSource.serializeNBT());
        NBTTagCompound normalBeforeConstruction = normalDrawer.getTagCompound().copy();
        DrawerStackItemHandler normalHandler = new DrawerStackItemHandler(
                normalDrawer, DrawerLayout.X_1);
        assertEquals(normalAmount, normalHandler.getSnapshot(0).getAmount());
        assertEquals(normalBeforeConstruction, normalDrawer.getTagCompound());

        Item compactItem = registeredItem("compact_stack_capability");
        long compactAmount = (long) Integer.MAX_VALUE + 37L;
        TestCompactingHandler compactSource = new TestCompactingHandler();
        compactSource.configureTiers(Arrays.asList(
                new CompactingInventoryHandler.Tier(new ItemStack(compactItem), 1L)));
        compactSource.insert(
                0,
                new BigItemStack(new ItemStack(compactItem), compactAmount),
                StorageAction.EXECUTE);
        ItemStack compactDrawerWithData = drawerStackWithTileData(compactSource.serializeNBT());
        NBTTagCompound compactBeforeConstruction = compactDrawerWithData.getTagCompound().copy();
        CompactingStackItemHandler compactHandler = new CompactingStackItemHandler(
                compactDrawerWithData, 1);
        assertEquals(compactAmount, compactHandler.getSnapshot(0).getAmount());
        assertEquals(compactBeforeConstruction, compactDrawerWithData.getTagCompound());

        ItemStack fluidDrawerWithData = drawerStackWithTileData(new NBTTagCompound());
        FluidDrawerStackItemHandler fluidSeeder = new FluidDrawerStackItemHandler(
                fluidDrawerWithData, DrawerLayout.X_1);
        fluidSeeder.insert(0, water(500L), StorageAction.EXECUTE);
        NBTTagCompound fluidBeforeConstruction = fluidDrawerWithData.getTagCompound().copy();
        FluidDrawerStackItemHandler fluidHandler = new FluidDrawerStackItemHandler(
                fluidDrawerWithData, DrawerLayout.X_1);
        assertEquals(500L, fluidHandler.getSnapshot(0).getAmount());
        assertEquals(fluidBeforeConstruction, fluidDrawerWithData.getTagCompound());

        DrawerStackCapabilityProvider provider = new DrawerStackCapabilityProvider(normalHandler);
        assertSame(normalHandler, provider.getCapability(
                CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null));

        ItemStack writableDrawer = drawerStackWithTileData(new NBTTagCompound());
        DrawerStackItemHandler writable = new DrawerStackItemHandler(
                writableDrawer, DrawerLayout.X_1);
        writable.insert(
                0,
                new BigItemStack(new ItemStack(normalItem), 3L),
                StorageAction.EXECUTE);
        NBTTagCompound writtenTileData = writableDrawer.getTagCompound()
                .getCompoundTag("TileData");
        assertTrue(writtenTileData.hasKey("StorageV2"));
        assertTrue(!writtenTileData.hasKey("Inventory"));
    }

    @Test
    public void itemFormIgnoresLegacyInventoryAndCompactingKeys() {
        Item normalItem = registeredItem("nested_normal_storage");
        TestBigHandler normalSource = new TestBigHandler();
        normalSource.insert(
                0,
                new BigItemStack(new ItemStack(normalItem), 17L),
                StorageAction.EXECUTE);
        NBTTagCompound legacy = new NBTTagCompound();
        NBTTagCompound inventory = new NBTTagCompound();
        inventory.setTag("BigItems", new NBTTagCompound());
        inventory.setTag("StorageV2", normalSource.serializeNBT()
                .getCompoundTag("StorageV2"));
        legacy.setTag("Inventory", inventory);

        Item compactItem = registeredItem("nested_compacting_storage");
        TestCompactingHandler compactSource = new TestCompactingHandler();
        compactSource.configureTiers(Arrays.asList(
                new CompactingInventoryHandler.Tier(new ItemStack(compactItem), 1L)));
        compactSource.insert(
                0,
                new BigItemStack(new ItemStack(compactItem), 19L),
                StorageAction.EXECUTE);
        NBTTagCompound compacting = new NBTTagCompound();
        compacting.setLong("TotalBase", 99L);
        compacting.setTag("Result_0", new NBTTagCompound());
        compacting.setTag("StorageV2", compactSource.serializeNBT()
                .getCompoundTag("StorageV2"));
        legacy.setTag("CompactingInv", compacting);

        DrawerStackItemHandler normal = new DrawerStackItemHandler(
                drawerStackWithTileData(legacy), DrawerLayout.X_1);
        CompactingStackItemHandler compact = new CompactingStackItemHandler(
                drawerStackWithTileData(legacy), 1);
        assertTrue(normal.getSnapshot(0).isEmpty());
        assertTrue(compact.getSnapshot(0).isEmpty());

        DrawerItemBlock item = new DrawerItemBlock(
                new WoodDrawerBlock(DrawerWoodType.OAK, DrawerLayout.X_1));
        ItemStack drawerStack = new ItemStack(item);
        drawerStack.setTagCompound(new NBTTagCompound());
        drawerStack.getTagCompound().setTag("TileData", legacy);
        assertTrue(item.collectStoredLines(drawerStack).isEmpty());
    }

    @Test
    public void stackedDrawerCapabilitiesRejectTransactionsWithoutChangingNbt() {
        Item storedItem = registeredItem("stacked_transaction_item");

        ItemStack normalDrawer = drawerStackWithTileData(new NBTTagCompound());
        DrawerStackItemHandler normalSeeder = new DrawerStackItemHandler(
                normalDrawer, DrawerLayout.X_1);
        assertEquals(5L, normalSeeder.insert(
                0,
                new BigItemStack(new ItemStack(storedItem), 5L),
                StorageAction.EXECUTE).getProcessedAmount());
        normalDrawer.setCount(2);
        DrawerStackItemHandler normal = new DrawerStackItemHandler(
                normalDrawer, DrawerLayout.X_1);
        NBTTagCompound normalBefore = normalDrawer.getTagCompound().copy();
        for (StorageAction action : StorageAction.values()) {
            assertEquals(0L, normal.insert(
                    0,
                    new BigItemStack(new ItemStack(storedItem), 2L),
                    action).getProcessedAmount());
            assertEquals(0L, normal.extract(
                    0, 2L, action).getProcessedAmount());
        }
        assertEquals(2, normal.insertItem(
                0, new ItemStack(storedItem, 2), false).getCount());
        assertTrue(normal.extractItem(0, 2, false).isEmpty());
        assertEquals(5L, normal.getSnapshot(0).getAmount());
        assertEquals(normalBefore, normalDrawer.getTagCompound());

        ItemStack compactDrawer = drawerStackWithTileData(new NBTTagCompound());
        CompactingStackItemHandler compactSeeder = new CompactingStackItemHandler(
                compactDrawer, 1);
        compactSeeder.configureTiers(Arrays.asList(
                new CompactingInventoryHandler.Tier(new ItemStack(storedItem), 1L)));
        assertEquals(5L, compactSeeder.insert(
                0,
                new BigItemStack(new ItemStack(storedItem), 5L),
                StorageAction.EXECUTE).getProcessedAmount());
        compactDrawer.setCount(2);
        CompactingStackItemHandler compact = new CompactingStackItemHandler(
                compactDrawer, 1);
        NBTTagCompound compactBefore = compactDrawer.getTagCompound().copy();
        for (StorageAction action : StorageAction.values()) {
            assertEquals(0L, compact.insert(
                    0,
                    new BigItemStack(new ItemStack(storedItem), 2L),
                    action).getProcessedAmount());
            assertEquals(0L, compact.extract(
                    0, 2L, action).getProcessedAmount());
        }
        assertEquals(2, compact.insertItem(
                0, new ItemStack(storedItem, 2), false).getCount());
        assertTrue(compact.extractItem(0, 2, false).isEmpty());
        assertEquals(5L, compact.getSnapshot(0).getAmount());
        assertEquals(compactBefore, compactDrawer.getTagCompound());

        ItemStack fluidDrawer = drawerStackWithTileData(new NBTTagCompound());
        FluidDrawerStackItemHandler fluidSeeder = new FluidDrawerStackItemHandler(
                fluidDrawer, DrawerLayout.X_1);
        assertEquals(500L, fluidSeeder.insert(
                0, water(500L), StorageAction.EXECUTE).getProcessedAmount());
        fluidDrawer.setCount(2);
        FluidDrawerStackItemHandler fluid = new FluidDrawerStackItemHandler(
                fluidDrawer, DrawerLayout.X_1);
        NBTTagCompound fluidBefore = fluidDrawer.getTagCompound().copy();
        for (StorageAction action : StorageAction.values()) {
            assertEquals(0L, fluid.insert(
                    0, water(100L), action).getProcessedAmount());
            assertEquals(0L, fluid.extract(
                    0, 100L, action).getProcessedAmount());
        }
        assertFalse(fluid.supportsFill(0));
        assertFalse(fluid.supportsDrain(0));
        assertFalse(fluid.supportsFluid(0, water(1L)));
        assertEquals(0, fluid.fill(new FluidStack(FluidRegistry.WATER, 100), false));
        assertNull(fluid.drain(100, false));
        assertEquals(500L, fluid.getSnapshot(0).getAmount());
        assertEquals(fluidBefore, fluidDrawer.getTagCompound());
    }

    @Test
    public void persistedDrawerStateForcesSingleStackLimitButEmptyRemainsStackable() {
        DrawerItemBlock drawerItem = new DrawerItemBlock(
                new WoodDrawerBlock(DrawerWoodType.OAK, DrawerLayout.X_1));
        ItemStack drawer = new ItemStack(drawerItem);
        assertTrue(drawerItem.getItemStackLimit(drawer) > 1);

        Item storedItem = registeredItem("persisted_drawer_limit");
        DrawerStackItemHandler handler = new DrawerStackItemHandler(
                drawer, DrawerLayout.X_1);
        assertEquals(3L, handler.insert(
                0,
                new BigItemStack(new ItemStack(storedItem), 3L),
                StorageAction.EXECUTE).getProcessedAmount());

        assertTrue(drawer.getTagCompound().hasKey("TileData"));
        assertEquals(1, drawerItem.getItemStackLimit(drawer));
        assertEquals(1, drawer.getMaxStackSize());
    }

    private static ItemStack drawerStackWithTileData(NBTTagCompound tileData) {
        ItemStack drawer = new ItemStack(new Item());
        drawer.setTagCompound(new NBTTagCompound());
        drawer.getTagCompound().setTag("TileData", tileData);
        return drawer;
    }

    private static BigFluidStack water(long amount) {
        return new BigFluidStack(new FluidStack(FluidRegistry.WATER, 1), amount);
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

    private static final class TestBigHandler extends BigInventoryHandler {
        private TestBigHandler() {
            super(1);
        }

        @Override
        public double getMultiplier() {
            return Double.POSITIVE_INFINITY;
        }
    }

    private static final class TestCompactingHandler extends CompactingInventoryHandler {
        private TestCompactingHandler() {
            super(1);
        }

        @Override
        public double getMultiplier() {
            return Double.POSITIVE_INFINITY;
        }
    }
}
