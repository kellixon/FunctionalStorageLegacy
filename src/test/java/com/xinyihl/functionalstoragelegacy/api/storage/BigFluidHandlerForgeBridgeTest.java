package com.xinyihl.functionalstoragelegacy.api.storage;

import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraft.init.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BigFluidHandlerForgeBridgeTest {

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    public void forgeFillRoutesMatchingAndRetainedTemplatesBeforeEmptyTanks() {
        MinimalFluidHandler handler = new MinimalFluidHandler(5L, 10L, 3L);
        handler.setTank(1, water(4L));
        handler.setTank(2, water(0L));
        FluidStack request = new FluidStack(FluidRegistry.WATER, 12);

        assertEquals(12, handler.fill(request, false));
        assertEquals(Arrays.asList(1, 2, 0), handler.fillOrder);
        assertEquals(0L, handler.getSnapshot(0).getAmount());
        assertEquals(4L, handler.getSnapshot(1).getAmount());
        assertEquals(0L, handler.getSnapshot(2).getAmount());
        assertEquals(0, handler.changeCount);
        assertEquals(StorageAction.SIMULATE, handler.lastFillAction);

        handler.fillOrder.clear();
        assertEquals(12, handler.fill(request, true));
        assertEquals(Arrays.asList(1, 2, 0), handler.fillOrder);
        assertEquals(3L, handler.getSnapshot(0).getAmount());
        assertEquals(10L, handler.getSnapshot(1).getAmount());
        assertEquals(3L, handler.getSnapshot(2).getAmount());
        assertEquals(3, handler.changeCount);
        assertEquals(StorageAction.EXECUTE, handler.lastFillAction);

        assertEquals(0, handler.fill(null, true));
        assertEquals(0, handler.fill(new FluidStack(FluidRegistry.WATER, 0), true));
    }

    @Test
    public void typedForgeDrainAggregatesOnlyMatchingTanksAndConvertsAction() {
        MinimalFluidHandler handler = new MinimalFluidHandler(20L, 20L, 20L);
        handler.setTank(0, water(3L));
        handler.setTank(1, water(4L));
        handler.setTank(2, lava(8L));

        FluidStack simulated = handler.drain(new FluidStack(FluidRegistry.WATER, 6), false);
        assertNotNull(simulated);
        assertTrue(simulated.isFluidEqual(new FluidStack(FluidRegistry.WATER, 1)));
        assertEquals(6, simulated.amount);
        assertEquals(3L, handler.getSnapshot(0).getAmount());
        assertEquals(4L, handler.getSnapshot(1).getAmount());
        assertEquals(0, handler.changeCount);
        assertEquals(StorageAction.SIMULATE, handler.lastDrainAction);

        FluidStack executed = handler.drain(new FluidStack(FluidRegistry.WATER, 6), true);
        assertNotNull(executed);
        assertEquals(6, executed.amount);
        assertEquals(0L, handler.getSnapshot(0).getAmount());
        assertEquals(1L, handler.getSnapshot(1).getAmount());
        assertEquals(8L, handler.getSnapshot(2).getAmount());
        assertEquals(StorageAction.EXECUTE, handler.lastDrainAction);

        assertNull(handler.drain((FluidStack) null, true));
        assertNull(handler.drain(new FluidStack(FluidRegistry.WATER, 0), true));
    }

    @Test
    public void untypedForgeDrainSelectsFirstFluidAndNeverMixesTypes() {
        MinimalFluidHandler handler = new MinimalFluidHandler(20L, 20L, 20L);
        handler.setTank(0, lava(2L));
        handler.setTank(1, water(10L));
        handler.setTank(2, lava(4L));

        FluidStack drained = handler.drain(5, true);

        assertNotNull(drained);
        assertTrue(drained.isFluidEqual(new FluidStack(FluidRegistry.LAVA, 1)));
        assertEquals(5, drained.amount);
        assertEquals(0L, handler.getSnapshot(0).getAmount());
        assertEquals(10L, handler.getSnapshot(1).getAmount());
        assertEquals(1L, handler.getSnapshot(2).getAmount());
        assertNull(handler.drain(0, true));
    }

    @Test
    public void tankPropertiesSaturateCopyAndHideTypedZeroContents() {
        MinimalFluidHandler handler = new MinimalFluidHandler(Long.MAX_VALUE, -4L);
        handler.setTank(0, water(Long.MAX_VALUE));
        handler.setTank(1, lava(0L));

        IFluidTankProperties[] properties = handler.getTankProperties();

        assertEquals(2, properties.length);
        assertEquals(Integer.MAX_VALUE, properties[0].getCapacity());
        FluidStack contents = properties[0].getContents();
        assertNotNull(contents);
        assertEquals(Integer.MAX_VALUE, contents.amount);
        contents.amount = 1;
        assertEquals(Integer.MAX_VALUE, properties[0].getContents().amount);
        assertEquals(0, properties[1].getCapacity());
        assertNull(properties[1].getContents());
        assertTrue(properties[0].canFill());
        assertTrue(properties[0].canDrain());
        assertTrue(properties[1].canFillFluidType(new FluidStack(FluidRegistry.WATER, 0)));
        assertTrue(properties[1].canDrainFluidType(new FluidStack(FluidRegistry.LAVA, 0)));
        assertTrue(handler.supportsFluid(1, lava(0L)));
    }

    @Test
    public void routedLongOperationsSaturateAndSimulationDoesNotMutate() {
        MinimalFluidHandler handler = new MinimalFluidHandler(Long.MAX_VALUE, Long.MAX_VALUE);
        handler.setTank(0, water(Long.MAX_VALUE - 2L));

        TransferResult<BigFluidStack, FluidStorageKey> result = handler.fillRouted(
                water(Long.MAX_VALUE), StorageAction.SIMULATE);

        assertEquals(Long.MAX_VALUE, result.getProcessedAmount());
        assertTrue(result.isComplete());
        assertEquals(Long.MAX_VALUE - 2L, handler.getSnapshot(0).getAmount());
        assertEquals(0L, handler.getSnapshot(1).getAmount());
        assertEquals(0, handler.changeCount);
    }

    @Test
    public void coreMethodsReturnZeroResultsForIllegalInput() {
        MinimalFluidHandler handler = new MinimalFluidHandler(5L);
        BigFluidStack request = water(4L);

        TransferResult<BigFluidStack, FluidStorageKey> invalidFill = handler.insert(
                -1, request, StorageAction.EXECUTE);
        assertEquals(4L, invalidFill.getRequestedAmount());
        assertEquals(0L, invalidFill.getProcessedAmount());
        assertEquals(0L, handler.extract(2, 4L, StorageAction.EXECUTE).getProcessedAmount());
        assertEquals(0L, handler.extract(0, -4L, StorageAction.EXECUTE).getRequestedAmount());
        assertTrue(handler.getSnapshot(-1).isEmpty());
        assertEquals(0L, handler.getCapacity(1));
    }

    private static BigFluidStack water(long amount) {
        return new BigFluidStack(new FluidStack(FluidRegistry.WATER, 1), amount);
    }

    private static BigFluidStack lava(long amount) {
        return new BigFluidStack(new FluidStack(FluidRegistry.LAVA, 1), amount);
    }

    private static final class MinimalFluidHandler implements IBigFluidHandler {
        private final BigFluidStack[] tanks;
        private final long[] capacities;
        private final List<Integer> fillOrder = new ArrayList<>();
        private int changeCount;
        private StorageAction lastFillAction;
        private StorageAction lastDrainAction;

        private MinimalFluidHandler(long... capacities) {
            this.capacities = capacities.clone();
            this.tanks = new BigFluidStack[capacities.length];
            Arrays.fill(this.tanks, BigFluidStack.empty());
        }

        @Override
        public int getStorageCount() {
            return tanks.length;
        }

        @Nonnull
        @Override
        public BigFluidStack getSnapshot(int tank) {
            return valid(tank) ? tanks[tank] : BigFluidStack.empty();
        }

        @Override
        public long getCapacity(int tank) {
            return valid(tank) ? Math.max(0L, capacities[tank]) : 0L;
        }

        @Nonnull
        @Override
        public TransferResult<BigFluidStack, FluidStorageKey> insert(
                int tank, @Nonnull BigFluidStack request, @Nonnull StorageAction action) {
            long requested = request == null || request.isEmpty() ? 0L : request.getAmount();
            lastFillAction = action;
            if (requested == 0L || !valid(tank)) {
                return new TransferResult<>(requested, BigFluidStack.empty(), action);
            }
            fillOrder.add(tank);
            BigFluidStack current = tanks[tank];
            if (current.hasTemplate() && !current.isSameType(request)) {
                return new TransferResult<>(requested, BigFluidStack.empty(), action);
            }
            long capacity = getCapacity(tank);
            long space = current.getAmount() >= capacity ? 0L : capacity - current.getAmount();
            long accepted = Math.min(requested, space);
            if (accepted > 0L && action == StorageAction.EXECUTE) {
                long newAmount = current.getAmount() + accepted;
                tanks[tank] = current.hasTemplate()
                        ? current.withAmount(newAmount) : request.withAmount(newAmount);
                changeCount++;
            }
            BigFluidStack processed = accepted == 0L
                    ? BigFluidStack.empty() : request.withAmount(accepted);
            return new TransferResult<>(requested, processed, action);
        }

        @Nonnull
        @Override
        public TransferResult<BigFluidStack, FluidStorageKey> extract(
                int tank, long amount, @Nonnull StorageAction action) {
            long requested = Math.max(0L, amount);
            lastDrainAction = action;
            if (requested == 0L || !valid(tank) || tanks[tank].isEmpty()) {
                return new TransferResult<>(requested, BigFluidStack.empty(), action);
            }
            BigFluidStack current = tanks[tank];
            long drained = Math.min(requested, current.getAmount());
            if (drained > 0L && action == StorageAction.EXECUTE) {
                tanks[tank] = current.withAmount(current.getAmount() - drained);
                changeCount++;
            }
            return new TransferResult<>(requested, current.withAmount(drained), action);
        }

        private boolean valid(int tank) {
            return tank >= 0 && tank < tanks.length;
        }

        private void setTank(int tank, BigFluidStack snapshot) {
            tanks[tank] = snapshot;
        }
    }
}
