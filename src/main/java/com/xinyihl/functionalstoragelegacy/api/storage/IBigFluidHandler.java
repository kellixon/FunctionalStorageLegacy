package com.xinyihl.functionalstoragelegacy.api.storage;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Long-capacity fluid handler whose implementers provide only tank snapshots,
 * capacities, and explicit transactions. Forge's int-based API and routed
 * operations are supplied as side-effect-safe defaults. Like Forge handlers,
 * instances are not implicitly thread-safe.
 */
public interface IBigFluidHandler extends IFluidHandler, IStorageHandler {

    /**
     * @return number of real storage tanks
     */
    int getTankCount();

    /**
     * Returns a detached snapshot. Implementations must return an empty snapshot
     * for an invalid index and must not expose mutable internal state.
     *
     * @param tank tank index
     * @return immutable tank snapshot
     */
    @Nonnull
    BigFluidStack getTankSnapshot(int tank);

    /**
     * Returns long tank capacity. Implementations must return zero for an
     * invalid index and must saturate capacity calculations at {@link Long#MAX_VALUE}.
     *
     * @param tank tank index
     * @return non-negative tank capacity
     */
    long getTankCapacity(int tank);

    /**
     * Fills exactly one tank. Empty requests, non-positive amounts, and invalid
     * indices must return a zero-processed result. Simulation must not mutate
     * contents, filters, NBT, or notification state.
     *
     * @param tank target tank
     * @param request immutable requested fluid and amount
     * @param action operation mode
     * @return validated result whose request amount equals {@code request.getAmount()}
     */
    @Nonnull
    TransferResult<BigFluidStack> fillTank(
            int tank, @Nonnull BigFluidStack request, @Nonnull StorageAction action);

    /**
     * Drains exactly one tank. Non-positive amounts and invalid indices must
     * return a zero-processed result. Simulation must be side-effect free.
     *
     * @param tank source tank
     * @param amount maximum amount to drain
     * @param action operation mode
     * @return validated result whose request amount is {@code max(0, amount)}
     */
    @Nonnull
    TransferResult<BigFluidStack> drainTank(
            int tank, long amount, @Nonnull StorageAction action);

    /**
     * Reports whether a tank can ever be filled. The default allows every
     * valid tank and does not consider current fullness.
     *
     * @param tank tank index
     * @return whether filling is supported
     */
    default boolean supportsFill(int tank) {
        return tank >= 0 && tank < Math.max(0, getTankCount());
    }

    /**
     * Reports whether a tank can ever be drained. The default allows every
     * valid tank and does not consider current contents.
     *
     * @param tank tank index
     * @return whether draining is supported
     */
    default boolean supportsDrain(int tank) {
        return tank >= 0 && tank < Math.max(0, getTankCount());
    }

    /**
     * Reports whether a tank supports a fluid type, ignoring its amount and
     * current contents. The default allows every present fluid template in a
     * valid tank, including a zero-amount retained filter snapshot.
     *
     * @param tank tank index
     * @param fluid fluid type to test
     * @return whether the type is supported
     */
    default boolean supportsFluid(int tank, @Nonnull BigFluidStack fluid) {
        return tank >= 0 && tank < Math.max(0, getTankCount())
                && fluid != null && fluid.hasTemplate();
    }

    /**
     * Builds detached Forge tank properties. Contents and capacities are
     * saturated only at Forge's int boundary; all capability flags delegate to
     * the overridable support methods.
     *
     * @return one property object per real tank
     */
    @Nonnull
    @Override
    default IFluidTankProperties[] getTankProperties() {
        final IBigFluidHandler handler = this;
        int count = Math.max(0, getTankCount());
        IFluidTankProperties[] properties = new IFluidTankProperties[count];
        for (int index = 0; index < count; index++) {
            final int tank = index;
            BigFluidStack current = getTankSnapshot(tank);
            final BigFluidStack snapshot = current == null ? BigFluidStack.empty() : current;
            long longCapacity = Math.max(0L, getTankCapacity(tank));
            final int capacity = longCapacity >= Integer.MAX_VALUE
                    ? Integer.MAX_VALUE : (int) longCapacity;
            properties[index] = new IFluidTankProperties() {
                @Nullable
                @Override
                public FluidStack getContents() {
                    return snapshot.toFluidStack();
                }

                @Override
                public int getCapacity() {
                    return capacity;
                }

                @Override
                public boolean canFill() {
                    return handler.supportsFill(tank);
                }

                @Override
                public boolean canDrain() {
                    return handler.supportsDrain(tank);
                }

                @Override
                public boolean canFillFluidType(FluidStack fluidStack) {
                    return fluidStack != null && handler.supportsFill(tank)
                            && handler.supportsFluid(tank, new BigFluidStack(fluidStack, 1L));
                }

                @Override
                public boolean canDrainFluidType(FluidStack fluidStack) {
                    return fluidStack != null && handler.supportsDrain(tank)
                            && handler.supportsFluid(tank, new BigFluidStack(fluidStack, 1L));
                }
            };
        }
        return properties;
    }

    /**
     * Bridges Forge filling to routed long transactions. Forge's execution
     * boolean is converted to the explicit action without inversion mistakes.
     *
     * @param resource Forge fluid request
     * @param doFill {@code true} to execute, {@code false} to simulate
     * @return processed amount saturated to an int
     */
    @Override
    default int fill(@Nullable FluidStack resource, boolean doFill) {
        if (resource == null || resource.amount <= 0) {
            return 0;
        }
        BigFluidStack request = new BigFluidStack(resource, resource.amount);
        TransferResult<BigFluidStack> result = fillRouted(
                request, doFill ? StorageAction.EXECUTE : StorageAction.SIMULATE);
        long processed = Math.min(request.getAmount(), Math.max(0L, result.getProcessedAmount()));
        return processed >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) processed;
    }

    /**
     * Bridges Forge's type-sensitive drain to a routed long transaction.
     *
     * @param resource requested fluid type and maximum int amount
     * @param doDrain {@code true} to execute, {@code false} to simulate
     * @return detached drained fluid or {@code null}
     */
    @Nullable
    @Override
    default FluidStack drain(@Nullable FluidStack resource, boolean doDrain) {
        if (resource == null || resource.amount <= 0) {
            return null;
        }
        BigFluidStack request = new BigFluidStack(resource, resource.amount);
        TransferResult<BigFluidStack> result = drainRouted(
                request, doDrain ? StorageAction.EXECUTE : StorageAction.SIMULATE);
        long processed = Math.min(request.getAmount(), Math.max(0L, result.getProcessedAmount()));
        return processed == 0L ? null : result.getProcessed().withAmount(processed).toFluidStack();
    }

    /**
     * Bridges Forge's non-type-sensitive drain. The routed operation selects
     * the first drainable non-empty tank type and aggregates only that type.
     *
     * @param maxDrain maximum int amount
     * @param doDrain {@code true} to execute, {@code false} to simulate
     * @return detached drained fluid or {@code null}
     */
    @Nullable
    @Override
    default FluidStack drain(int maxDrain, boolean doDrain) {
        if (maxDrain <= 0) {
            return null;
        }
        TransferResult<BigFluidStack> result = drainRouted(
                maxDrain, doDrain ? StorageAction.EXECUTE : StorageAction.SIMULATE);
        long processed = Math.min((long) maxDrain, Math.max(0L, result.getProcessedAmount()));
        return processed == 0L ? null : result.getProcessed().withAmount(processed).toFluidStack();
    }

    /**
     * Routes filling through compatible templated tanks first, including
     * zero-amount retained filters, and unfiltered empty tanks second.
     * Unsupported tanks are skipped and amounts use saturated addition.
     *
     * @param request fluid type and total amount
     * @param action operation mode
     * @return aggregate transaction result
     */
    @Nonnull
    default TransferResult<BigFluidStack> fillRouted(
            @Nonnull BigFluidStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request == null || request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L) {
            return new TransferResult<>(0L, BigFluidStack.empty(), action);
        }
        long processedTotal = 0L;
        for (int pass = 0; pass < 2 && processedTotal < requested; pass++) {
            int tanks = Math.max(0, getTankCount());
            for (int tank = 0; tank < tanks && processedTotal < requested; tank++) {
                if (!supportsFill(tank) || !supportsFluid(tank, request)) {
                    continue;
                }
                BigFluidStack current = getTankSnapshot(tank);
                boolean hasTemplate = current != null && current.hasTemplate();
                if ((pass == 0 && (!hasTemplate || !current.isSameType(request)))
                        || (pass == 1 && hasTemplate)) {
                    continue;
                }
                long remaining = requested - processedTotal;
                TransferResult<BigFluidStack> result = fillTank(
                        tank, request.withAmount(remaining), action);
                long processed = result == null ? 0L
                        : Math.min(remaining, Math.max(0L, result.getProcessedAmount()));
                processedTotal = processedTotal > Long.MAX_VALUE - processed
                        ? Long.MAX_VALUE : processedTotal + processed;
            }
        }
        return new TransferResult<>(requested, request.withAmount(processedTotal), action);
    }

    /**
     * Routes type-sensitive draining across every matching, supported tank.
     *
     * @param request fluid type and maximum total amount
     * @param action operation mode
     * @return aggregate transaction result
     */
    @Nonnull
    default TransferResult<BigFluidStack> drainRouted(
            @Nonnull BigFluidStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request == null || request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L) {
            return new TransferResult<>(0L, BigFluidStack.empty(), action);
        }
        long processedTotal = 0L;
        int tanks = Math.max(0, getTankCount());
        for (int tank = 0; tank < tanks && processedTotal < requested; tank++) {
            if (!supportsDrain(tank) || !supportsFluid(tank, request)) {
                continue;
            }
            BigFluidStack current = getTankSnapshot(tank);
            if (current == null || current.isEmpty() || !current.isSameType(request)) {
                continue;
            }
            long remaining = requested - processedTotal;
            TransferResult<BigFluidStack> result = drainTank(tank, remaining, action);
            long processed = result == null ? 0L
                    : Math.min(remaining, Math.max(0L, result.getProcessedAmount()));
            processedTotal = processedTotal > Long.MAX_VALUE - processed
                    ? Long.MAX_VALUE : processedTotal + processed;
        }
        return new TransferResult<>(requested, request.withAmount(processedTotal), action);
    }

    /**
     * Routes a non-type-sensitive drain by selecting the first drainable fluid
     * type in tank order, then delegating to type-sensitive routing. A zero or
     * negative amount returns a zero request and never invokes a tank operation.
     *
     * @param amount maximum total amount
     * @param action operation mode
     * @return aggregate transaction result
     */
    @Nonnull
    default TransferResult<BigFluidStack> drainRouted(long amount, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = Math.max(0L, amount);
        if (requested == 0L) {
            return new TransferResult<>(0L, BigFluidStack.empty(), action);
        }
        int tanks = Math.max(0, getTankCount());
        for (int tank = 0; tank < tanks; tank++) {
            BigFluidStack current = getTankSnapshot(tank);
            if (current != null && !current.isEmpty()
                    && supportsDrain(tank) && supportsFluid(tank, current)) {
                return drainRouted(current.withAmount(requested), action);
            }
        }
        return new TransferResult<>(requested, BigFluidStack.empty(), action);
    }
}
