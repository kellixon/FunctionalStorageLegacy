package com.xinyihl.functionalstoragelegacy.common.inventory.base;

import com.xinyihl.functionalstoragelegacy.api.storage.BigFluidStack;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.api.storage.TransferResult;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.Test;
import org.junit.BeforeClass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BigFluidHandlerTest {

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    public void storesAmountsAboveForgeIntBoundary() {
        TestHandler handler = new TestHandler(1, 3_000_000D);
        long amount = (long) Integer.MAX_VALUE + 500_000_000L;

        TransferResult<BigFluidStack> result = handler.fillTank(
                0, water(amount), StorageAction.EXECUTE);

        assertEquals(amount, result.getProcessedAmount());
        assertEquals(amount, handler.getTankSnapshot(0).getAmount());
        assertEquals(3_000_000_000L, handler.getTankCapacity(0));
        assertEquals(Integer.MAX_VALUE, handler.getTankProperties()[0].getCapacity());
        assertEquals(Integer.MAX_VALUE, handler.getTankProperties()[0].getContents().amount);
        assertEquals(1, handler.changes);
    }

    @Test
    public void capacityUsesDoubleAndSaturatesAtLongMax() {
        TestHandler huge = new TestHandler(1, Double.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, huge.getTankCapacity(0));

        TestHandler invalid = new TestHandler(1, Double.NaN);
        assertEquals(0L, invalid.getTankCapacity(0));
        invalid.multiplier = -2D;
        assertEquals(0L, invalid.getTankCapacity(0));

        TestHandler maxUpgrade = new TestHandler(1, 1D);
        maxUpgrade.maxStorage = true;
        assertEquals(Long.MAX_VALUE, maxUpgrade.getTankCapacity(0));
    }

    @Test
    public void simulationDoesNotChangeContentsFiltersNbtOrNotifications() {
        TestHandler handler = new TestHandler(1, 10D);
        NBTTagCompound beforeFill = handler.serializeNBT();

        TransferResult<BigFluidStack> simulatedFill = handler.fillTank(
                0, water(4_000L), StorageAction.SIMULATE);
        assertEquals(4_000L, simulatedFill.getProcessedAmount());
        assertTrue(handler.getTankSnapshot(0).isEmpty());
        assertNull(handler.getTankFilter(0));
        assertEquals(beforeFill, handler.serializeNBT());
        assertEquals(0, handler.changes);

        handler.fillTank(0, water(4_000L), StorageAction.EXECUTE);
        handler.setLocked(true);
        handler.changes = 0;
        NBTTagCompound beforeDrain = handler.serializeNBT();

        TransferResult<BigFluidStack> simulatedDrain = handler.drainTank(
                0, 4_000L, StorageAction.SIMULATE);
        assertEquals(4_000L, simulatedDrain.getProcessedAmount());
        assertEquals(4_000L, handler.getTankSnapshot(0).getAmount());
        assertNotNull(handler.getTankFilter(0));
        assertEquals(beforeDrain, handler.serializeNBT());
        assertEquals(0, handler.changes);
    }

    @Test
    public void lockedTankRetainsFilterAndRejectsOtherFluid() {
        TestHandler handler = new TestHandler(1, 5D);
        handler.fillTank(0, water(2_000L), StorageAction.EXECUTE);
        handler.setLocked(true);

        handler.drainTank(0, 2_000L, StorageAction.EXECUTE);
        BigFluidStack retained = handler.getTankSnapshot(0);
        assertTrue(retained.isEmpty());
        assertTrue(retained.hasTemplate());
        assertTrue(retained.isSameType(new FluidStack(FluidRegistry.WATER, 1)));
        assertEquals(0L, handler.fillTank(
                0, lava(500L), StorageAction.EXECUTE).getProcessedAmount());
        assertEquals(500L, handler.fillTank(
                0, water(500L), StorageAction.EXECUTE).getProcessedAmount());

        handler.drainTank(0, 500L, StorageAction.EXECUTE);
        handler.setLocked(false);
        assertFalse(handler.getTankSnapshot(0).hasTemplate());
        assertEquals(500L, handler.fillTank(
                0, lava(500L), StorageAction.EXECUTE).getProcessedAmount());
    }

    @Test
    public void voidConsumesOnlyCompatibleOverflow() {
        TestHandler handler = new TestHandler(1, 1D);
        handler.voiding = true;

        TransferResult<BigFluidStack> first = handler.fillTank(
                0, water(1_500L), StorageAction.EXECUTE);
        assertEquals(1_500L, first.getProcessedAmount());
        assertEquals(1_000L, handler.getTankSnapshot(0).getAmount());

        int changes = handler.changes;
        TransferResult<BigFluidStack> overflow = handler.fillTank(
                0, water(500L), StorageAction.EXECUTE);
        assertEquals(500L, overflow.getProcessedAmount());
        assertEquals(changes, handler.changes);
        assertEquals(0L, handler.fillTank(
                0, lava(500L), StorageAction.EXECUTE).getProcessedAmount());
    }

    @Test
    public void creativeReportsFullTransactionsWithoutConsumingState() {
        TestHandler handler = new TestHandler(1, 1D);
        handler.creative = true;

        assertEquals(7L, handler.fillTank(
                0, water(7L), StorageAction.SIMULATE).getProcessedAmount());
        assertFalse(handler.getTankSnapshot(0).hasTemplate());
        assertEquals(0, handler.changes);

        handler.fillTank(0, water(7L), StorageAction.EXECUTE);
        assertEquals(Long.MAX_VALUE, handler.getTankSnapshot(0).getAmount());
        assertEquals(Long.MAX_VALUE, handler.getTankCapacity(0));
        int changes = handler.changes;
        TransferResult<BigFluidStack> drained = handler.drainTank(
                0, Long.MAX_VALUE, StorageAction.EXECUTE);
        assertEquals(Long.MAX_VALUE, drained.getProcessedAmount());
        assertEquals(Long.MAX_VALUE, handler.getTankSnapshot(0).getAmount());
        assertEquals(changes, handler.changes);
    }

    @Test
    public void storageV2RoundTripsLongAmountAndFilter() {
        TestHandler source = new TestHandler(2, 5_000_000D);
        long amount = (long) Integer.MAX_VALUE + 123_456L;
        source.fillTank(1, water(amount), StorageAction.EXECUTE);
        source.setLocked(true);

        NBTTagCompound serialized = source.serializeNBT();
        assertTrue(serialized.hasKey("StorageV2", Constants.NBT.TAG_COMPOUND));
        NBTTagList tanks = serialized.getCompoundTag("StorageV2")
                .getTagList("Tanks", Constants.NBT.TAG_COMPOUND);
        assertEquals(1, tanks.tagCount());
        NBTTagCompound entry = tanks.getCompoundTagAt(0);
        assertEquals(1, entry.getInteger("Index"));
        assertEquals(amount, entry.getLong("Amount"));
        assertTrue(entry.hasKey("Fluid", Constants.NBT.TAG_COMPOUND));
        assertTrue(entry.hasKey("Filter", Constants.NBT.TAG_COMPOUND));

        TestHandler restored = new TestHandler(2, 5_000_000D);
        restored.locked = true;
        restored.deserializeNBT(serialized);
        assertEquals(amount, restored.getTankSnapshot(1).getAmount());
        assertTrue(restored.getTankSnapshot(1).isSameType(
                new FluidStack(FluidRegistry.WATER, 1)));
        assertNotNull(restored.getTankFilter(1));
    }

    @Test
    public void missingStorageV2ClearsStateAndIgnoresLegacyFormat() {
        TestHandler handler = new TestHandler(1, 5D);
        handler.fillTank(0, water(1_000L), StorageAction.EXECUTE);

        NBTTagCompound legacy = new NBTTagCompound();
        NBTTagCompound tank = new NBTTagCompound();
        new FluidStack(FluidRegistry.WATER, 1_000).writeToNBT(tank);
        legacy.setTag("Tank_0", tank);
        NBTTagCompound wrapper = new NBTTagCompound();
        wrapper.setTag("FluidInv", legacy);

        handler.deserializeNBT(wrapper);
        assertTrue(handler.getTankSnapshot(0).isEmpty());
        assertFalse(handler.getTankSnapshot(0).hasTemplate());
    }

    @Test
    public void zeroAmountFluidEntryDoesNotBecomeAnImplicitFilter() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound storage = new NBTTagCompound();
        NBTTagList tanks = new NBTTagList();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger("Index", 0);
        entry.setLong("Amount", 0L);
        entry.setTag("Fluid", new FluidStack(FluidRegistry.WATER, 1)
                .writeToNBT(new NBTTagCompound()));
        tanks.appendTag(entry);
        storage.setTag("Tanks", tanks);
        root.setTag("StorageV2", storage);

        TestHandler handler = new TestHandler(1, 5D);
        handler.deserializeNBT(root);
        assertFalse(handler.getTankSnapshot(0).hasTemplate());

        TransferResult<BigFluidStack> filled = handler.fillTank(
                0, lava(500L), StorageAction.EXECUTE);
        assertEquals(500L, filled.getProcessedAmount());
        assertTrue(handler.getTankSnapshot(0).isSameType(
                new FluidStack(FluidRegistry.LAVA, 1)));
    }

    private static BigFluidStack water(long amount) {
        return new BigFluidStack(new FluidStack(FluidRegistry.WATER, 1), amount);
    }

    private static BigFluidStack lava(long amount) {
        return new BigFluidStack(new FluidStack(FluidRegistry.LAVA, 1), amount);
    }

    private static final class TestHandler extends BigFluidHandler {
        private double multiplier;
        private boolean locked;
        private boolean voiding;
        private boolean creative;
        private boolean maxStorage;
        private int changes;

        private TestHandler(int tanks, double multiplier) {
            super(tanks);
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
            return voiding;
        }

        @Override
        public boolean isCreative() {
            return creative;
        }

        @Override
        protected boolean hasMaxStorage() {
            return maxStorage;
        }

        private void setLocked(boolean value) {
            setLockFilters(value);
            locked = value;
        }
    }
}
