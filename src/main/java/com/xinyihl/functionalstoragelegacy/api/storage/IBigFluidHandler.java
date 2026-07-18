package com.xinyihl.functionalstoragelegacy.api.storage;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Forge fluid capability bridge for a generic long-capacity storage handler.
 * Indexed state changes use {@link IStorageHandler}; fluid support methods and
 * Forge tank properties remain the fluid-specific capability surface.
 */
public interface IBigFluidHandler extends IFluidHandler,
        IStorageHandler<BigFluidStack, FluidStorageKey> {

    /** Reports whether a generic index currently supports filling. */
    default boolean supportsFill(int index) {
        return index >= 0 && index < Math.max(0, getStorageCount());
    }

    /** Reports whether a generic index currently supports draining. */
    default boolean supportsDrain(int index) {
        return index >= 0 && index < Math.max(0, getStorageCount());
    }

    /** Reports whether a generic index supports a fluid type. */
    default boolean supportsFluid(int index, @Nonnull BigFluidStack fluid) {
        return index >= 0 && index < Math.max(0, getStorageCount())
                && fluid != null && fluid.hasTemplate();
    }

    /** Builds detached Forge tank properties from generic snapshots/capacities. */
    @Nonnull
    @Override
    default IFluidTankProperties[] getTankProperties() {
        final IBigFluidHandler handler = this;
        int count = Math.max(0, getStorageCount());
        IFluidTankProperties[] properties = new IFluidTankProperties[count];
        for (int index = 0; index < count; index++) {
            final int storageIndex = index;
            BigFluidStack current = getSnapshot(storageIndex);
            final BigFluidStack snapshot = current == null ? BigFluidStack.empty() : current;
            long longCapacity = Math.max(0L, getCapacity(storageIndex));
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
                    return handler.supportsFill(storageIndex);
                }

                @Override
                public boolean canDrain() {
                    return handler.supportsDrain(storageIndex);
                }

                @Override
                public boolean canFillFluidType(FluidStack fluidStack) {
                    return fluidStack != null && handler.supportsFill(storageIndex)
                            && handler.supportsFluid(
                            storageIndex, new BigFluidStack(fluidStack, 1L));
                }

                @Override
                public boolean canDrainFluidType(FluidStack fluidStack) {
                    return fluidStack != null && handler.supportsDrain(storageIndex)
                            && handler.supportsFluid(
                            storageIndex, new BigFluidStack(fluidStack, 1L));
                }
            };
        }
        return properties;
    }

    /** Bridges Forge fill to routed generic insertion. */
    @Override
    default int fill(@Nullable FluidStack resource, boolean doFill) {
        if (resource == null || resource.amount <= 0) {
            return 0;
        }
        BigFluidStack request = new BigFluidStack(resource, resource.amount);
        TransferResult<BigFluidStack, FluidStorageKey> result = fillRouted(
                request, doFill ? StorageAction.EXECUTE : StorageAction.SIMULATE);
        long processed = Math.min(request.getAmount(), Math.max(0L, result.getProcessedAmount()));
        return processed >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) processed;
    }

    /** Bridges Forge typed drain to routed generic extraction. */
    @Nullable
    @Override
    default FluidStack drain(@Nullable FluidStack resource, boolean doDrain) {
        if (resource == null || resource.amount <= 0) {
            return null;
        }
        BigFluidStack request = new BigFluidStack(resource, resource.amount);
        TransferResult<BigFluidStack, FluidStorageKey> result = drainRouted(
                request, doDrain ? StorageAction.EXECUTE : StorageAction.SIMULATE);
        long processed = Math.min(request.getAmount(), Math.max(0L, result.getProcessedAmount()));
        return processed == 0L ? null : result.getProcessed().withAmount(processed).toFluidStack();
    }

    /** Bridges Forge untyped drain to the first available fluid type. */
    @Nullable
    @Override
    default FluidStack drain(int maxDrain, boolean doDrain) {
        if (maxDrain <= 0) {
            return null;
        }
        TransferResult<BigFluidStack, FluidStorageKey> result = drainRouted(
                maxDrain, doDrain ? StorageAction.EXECUTE : StorageAction.SIMULATE);
        long processed = Math.min((long) maxDrain, Math.max(0L, result.getProcessedAmount()));
        return processed == 0L ? null : result.getProcessed().withAmount(processed).toFluidStack();
    }

    /** Routes filling through compatible configured indices before empty indices. */
    @Nonnull
    default TransferResult<BigFluidStack, FluidStorageKey> fillRouted(
            @Nonnull BigFluidStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request == null || request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L) {
            return new TransferResult<>(0L, BigFluidStack.empty(), action);
        }
        long processedTotal = 0L;
        int count = Math.max(0, getStorageCount());
        for (int pass = 0; pass < 2 && processedTotal < requested; pass++) {
            for (int index = 0; index < count && processedTotal < requested; index++) {
                if (!supportsFill(index) || !supportsFluid(index, request)) {
                    continue;
                }
                BigFluidStack current = getSnapshot(index);
                boolean hasTemplate = current != null && current.hasTemplate();
                if ((pass == 0 && (!hasTemplate || !current.isSameType(request)))
                        || (pass == 1 && hasTemplate)) {
                    continue;
                }
                long remaining = requested - processedTotal;
                TransferResult<BigFluidStack, FluidStorageKey> result = insert(
                        index, request.withAmount(remaining), action);
                long processed = result == null ? 0L
                        : Math.min(remaining, Math.max(0L, result.getProcessedAmount()));
                processedTotal = processedTotal > Long.MAX_VALUE - processed
                        ? Long.MAX_VALUE : processedTotal + processed;
            }
        }
        return new TransferResult<>(requested, request.withAmount(processedTotal), action);
    }

    /** Routes typed extraction through matching generic indices. */
    @Nonnull
    default TransferResult<BigFluidStack, FluidStorageKey> drainRouted(
            @Nonnull BigFluidStack request, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = request == null || request.isEmpty() ? 0L : request.getAmount();
        if (requested == 0L) {
            return new TransferResult<>(0L, BigFluidStack.empty(), action);
        }
        long processedTotal = 0L;
        int count = Math.max(0, getStorageCount());
        for (int index = 0; index < count && processedTotal < requested; index++) {
            if (!supportsDrain(index) || !supportsFluid(index, request)) {
                continue;
            }
            BigFluidStack current = getSnapshot(index);
            if (current == null || current.isEmpty() || !current.isSameType(request)) {
                continue;
            }
            long remaining = requested - processedTotal;
            TransferResult<BigFluidStack, FluidStorageKey> result = extract(
                    index, remaining, action);
            long processed = result == null ? 0L
                    : Math.min(remaining, Math.max(0L, result.getProcessedAmount()));
            processedTotal = processedTotal > Long.MAX_VALUE - processed
                    ? Long.MAX_VALUE : processedTotal + processed;
        }
        return new TransferResult<>(requested, request.withAmount(processedTotal), action);
    }

    /** Routes untyped extraction by selecting the first available fluid type. */
    @Nonnull
    default TransferResult<BigFluidStack, FluidStorageKey> drainRouted(
            long amount, @Nonnull StorageAction action) {
        Objects.requireNonNull(action, "action");
        long requested = Math.max(0L, amount);
        if (requested == 0L) {
            return new TransferResult<>(0L, BigFluidStack.empty(), action);
        }
        int count = Math.max(0, getStorageCount());
        for (int index = 0; index < count; index++) {
            BigFluidStack current = getSnapshot(index);
            if (current != null && !current.isEmpty()
                    && supportsDrain(index) && supportsFluid(index, current)) {
                return drainRouted(current.withAmount(requested), action);
            }
        }
        return new TransferResult<>(requested, BigFluidStack.empty(), action);
    }
}
