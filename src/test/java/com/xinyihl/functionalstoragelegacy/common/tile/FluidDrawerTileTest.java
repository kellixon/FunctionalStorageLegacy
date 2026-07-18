package com.xinyihl.functionalstoragelegacy.common.tile;

import com.xinyihl.functionalstoragelegacy.api.storage.BigFluidStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigFluidHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.common.storage.DrawerLayout;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.Test;
import org.junit.BeforeClass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FluidDrawerTileTest {

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    public void layoutAndStorageV2RoundTripTogether() {
        FluidDrawerTile source = new FluidDrawerTile(DrawerLayout.X_2);
        source.getFluidHandler().insert(
                1, water(12_345L), StorageAction.EXECUTE);
        source.setLocked(true);

        NBTTagCompound serialized = source.saveTileToNBT();
        assertEquals(DrawerLayout.X_2.getId(), serialized.getString("DrawerLayout"));
        assertTrue(serialized.hasKey("StorageV2", Constants.NBT.TAG_COMPOUND));
        assertTrue(serialized.getCompoundTag("StorageV2")
                .hasKey("Tanks", Constants.NBT.TAG_LIST));

        FluidDrawerTile restored = new FluidDrawerTile();
        restored.loadTileFromNBT(serialized);
        assertEquals(DrawerLayout.X_2, restored.getDrawerLayout());
        assertEquals(2, restored.getFluidHandler().getStorageCount());
        assertEquals(12_345L, restored.getFluidHandler().getSnapshot(1).getAmount());
        assertTrue(restored.getFluidHandler().getSnapshot(1).isSameType(
                new FluidStack(FluidRegistry.WATER, 1)));

        restored.getFluidHandler().extract(
                1, 12_345L, StorageAction.EXECUTE);
        assertTrue(restored.getFluidHandler().getSnapshot(1).hasTemplate());
    }

    @Test
    public void runtimeUnlockClearsRetainedFluidFilter() {
        FluidDrawerTile tile = new FluidDrawerTile(DrawerLayout.X_1);
        IBigFluidHandler handler = tile.getFluidHandler();
        handler.insert(0, water(2_000L), StorageAction.EXECUTE);
        tile.setLocked(true);
        handler.extract(0, 2_000L, StorageAction.EXECUTE);
        assertTrue(handler.getSnapshot(0).hasTemplate());

        tile.setLocked(false);
        assertFalse(handler.getSnapshot(0).hasTemplate());
        assertEquals(500L, handler.insert(
                0,
                new BigFluidStack(new FluidStack(FluidRegistry.LAVA, 1), 500L),
                StorageAction.EXECUTE).getProcessedAmount());
    }

    @Test
    public void legacyFluidInvIsNotMigratedButNewLayoutStillApplies() {
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setString("DrawerLayout", DrawerLayout.X_4.getId());
        NBTTagCompound oldInventory = new NBTTagCompound();
        NBTTagCompound oldTank = new NBTTagCompound();
        new FluidStack(FluidRegistry.WATER, 1_000).writeToNBT(oldTank);
        oldInventory.setTag("Tank_0", oldTank);
        legacy.setTag("FluidInv", oldInventory);

        FluidDrawerTile restored = new FluidDrawerTile();
        restored.readFromNBT(legacy);
        assertEquals(DrawerLayout.X_4, restored.getDrawerLayout());
        assertEquals(4, restored.getFluidHandler().getStorageCount());
        for (int tank = 0; tank < restored.getFluidHandler().getStorageCount(); tank++) {
            assertTrue(restored.getFluidHandler().getSnapshot(tank).isEmpty());
            assertFalse(restored.getFluidHandler().getSnapshot(tank).hasTemplate());
        }
    }

    private static BigFluidStack water(long amount) {
        return new BigFluidStack(new FluidStack(FluidRegistry.WATER, 1), amount);
    }
}
