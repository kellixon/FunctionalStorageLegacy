package com.xinyihl.functionalstoragelegacy.common.inventory.controller;

import com.xinyihl.functionalstoragelegacy.api.storage.BigFluidStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigFluidHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.api.storage.TransferResult;
import com.xinyihl.functionalstoragelegacy.common.inventory.base.BigFluidHandler;
import net.minecraft.init.Bootstrap;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.Test;
import org.junit.BeforeClass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ControllerFluidHandlerTest {

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    public void strictTankOperationsDelegateToExactlyOneMappedChild() {
        TestHandler first = new TestHandler(2, 1D);
        TestHandler second = new TestHandler(1, 2D);
        ControllerFluidHandler controller = new ControllerFluidHandler();
        controller.setHandlers(Arrays.asList(first, second));

        TransferResult<BigFluidStack> inserted = controller.fillTank(
                2, water(500L), StorageAction.EXECUTE);
        assertEquals(500L, inserted.getProcessedAmount());
        assertTrue(first.getTankSnapshot(0).isEmpty());
        assertTrue(first.getTankSnapshot(1).isEmpty());
        assertEquals(500L, second.getTankSnapshot(0).getAmount());

        assertEquals(3, controller.getTankCount());
        assertEquals(1_000L, controller.getTankCapacity(0));
        assertEquals(2_000L, controller.getTankCapacity(2));
        assertEquals(0L, controller.getTankCapacity(-1));
        assertEquals(0L, controller.fillTank(
                3, water(20L), StorageAction.EXECUTE).getProcessedAmount());

        TransferResult<BigFluidStack> drained = controller.drainTank(
                2, 200L, StorageAction.EXECUTE);
        assertEquals(200L, drained.getProcessedAmount());
        assertEquals(300L, second.getTankSnapshot(0).getAmount());
        assertTrue(first.getTankSnapshot(0).isEmpty());
    }

    @Test
    public void routedFillPrefersMatchingAndRetainedFiltersBeforeEmptyTanks() {
        TestHandler first = new TestHandler(2, 1D);
        TestHandler second = new TestHandler(1, 1D);
        first.fillTank(1, water(500L), StorageAction.EXECUTE);
        second.fillTank(0, water(100L), StorageAction.EXECUTE);
        second.setLocked(true);
        second.drainTank(0, 100L, StorageAction.EXECUTE);

        ControllerFluidHandler controller = new ControllerFluidHandler();
        controller.setHandlers(Arrays.asList(first, second));
        TransferResult<BigFluidStack> result = controller.fillRouted(
                water(1_800L), StorageAction.EXECUTE);

        assertEquals(1_800L, result.getProcessedAmount());
        assertTrue(result.isComplete());
        assertEquals(300L, first.getTankSnapshot(0).getAmount());
        assertEquals(1_000L, first.getTankSnapshot(1).getAmount());
        assertEquals(1_000L, second.getTankSnapshot(0).getAmount());
    }

    @Test
    public void routedSimulationDoesNotMutateChildren() {
        TestHandler first = new TestHandler(1, 1D);
        TestHandler second = new TestHandler(1, 1D);
        first.fillTank(0, water(250L), StorageAction.EXECUTE);
        first.changes = 0;
        second.changes = 0;
        ControllerFluidHandler controller = new ControllerFluidHandler();
        controller.setHandlers(Arrays.asList(first, second));

        TransferResult<BigFluidStack> result = controller.fillRouted(
                water(1_500L), StorageAction.SIMULATE);

        assertEquals(1_500L, result.getProcessedAmount());
        assertEquals(250L, first.getTankSnapshot(0).getAmount());
        assertTrue(second.getTankSnapshot(0).isEmpty());
        assertEquals(0, first.changes);
        assertEquals(0, second.changes);
    }

    @Test
    public void untypedDrainSelectsFirstFluidAndNeverMixesTypes() {
        TestHandler first = new TestHandler(2, 2D);
        TestHandler second = new TestHandler(1, 2D);
        first.fillTank(0, lava(200L), StorageAction.EXECUTE);
        first.fillTank(1, water(1_000L), StorageAction.EXECUTE);
        second.fillTank(0, lava(400L), StorageAction.EXECUTE);
        ControllerFluidHandler controller = new ControllerFluidHandler();
        controller.setHandlers(Arrays.asList(first, second));

        TransferResult<BigFluidStack> drained = controller.drainRouted(
                500L, StorageAction.EXECUTE);

        assertEquals(500L, drained.getProcessedAmount());
        assertTrue(drained.getProcessed().isSameType(
                new FluidStack(FluidRegistry.LAVA, 1)));
        assertEquals(0L, first.getTankSnapshot(0).getAmount());
        assertEquals(1_000L, first.getTankSnapshot(1).getAmount());
        assertEquals(100L, second.getTankSnapshot(0).getAmount());
    }

    @Test
    public void handlerListRebuildIsAtomicAndDefensive() {
        TestHandler first = new TestHandler(2, 1D);
        TestHandler second = new TestHandler(1, 1D);
        List<IBigFluidHandler> source = new ArrayList<>();
        source.add(first);
        ControllerFluidHandler controller = new ControllerFluidHandler();

        controller.setHandlers(source);
        source.add(second);
        assertEquals(2, controller.getTankCount());
        assertEquals(1, controller.getHandlers().size());

        controller.setHandlers(Arrays.asList(second));
        assertEquals(1, controller.getTankCount());
        assertEquals(1, controller.getHandlers().size());
        assertEquals(second, controller.getHandlers().get(0));
        try {
            controller.getHandlers().add(first);
            fail("Expected immutable handler list");
        } catch (UnsupportedOperationException expected) {
            // Expected defensive view.
        }
    }

    @Test
    public void duplicateHandlerIdentityMapsAndSimulatesOnlyOnce() {
        TestHandler shared = new TestHandler(1, 1D);
        ControllerFluidHandler controller = new ControllerFluidHandler();
        controller.setHandlers(Arrays.asList(shared, shared));

        TransferResult<BigFluidStack> simulated = controller.fillRouted(
                water(1_500L), StorageAction.SIMULATE);

        assertEquals(1, controller.getHandlers().size());
        assertEquals(1, controller.getTankCount());
        assertEquals(1_000L, simulated.getProcessedAmount());
        assertTrue(shared.getTankSnapshot(0).isEmpty());
    }

    @Test
    public void invalidAndZeroRequestsReturnZeroResults() {
        ControllerFluidHandler controller = new ControllerFluidHandler();
        controller.setHandlers(Arrays.asList(new TestHandler(1, 1D)));

        assertEquals(0L, controller.fillTank(
                -1, water(5L), StorageAction.EXECUTE).getProcessedAmount());
        assertEquals(0L, controller.drainTank(
                4, 5L, StorageAction.EXECUTE).getProcessedAmount());
        assertEquals(0L, controller.drainTank(
                0, -5L, StorageAction.EXECUTE).getRequestedAmount());
        assertFalse(controller.supportsFill(-1));
        assertFalse(controller.supportsDrain(2));
    }

    private static BigFluidStack water(long amount) {
        return new BigFluidStack(new FluidStack(FluidRegistry.WATER, 1), amount);
    }

    private static BigFluidStack lava(long amount) {
        return new BigFluidStack(new FluidStack(FluidRegistry.LAVA, 1), amount);
    }

    private static final class TestHandler extends BigFluidHandler {
        private final double multiplier;
        private boolean locked;
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

        private void setLocked(boolean value) {
            setLockFilters(value);
            locked = value;
        }
    }
}
