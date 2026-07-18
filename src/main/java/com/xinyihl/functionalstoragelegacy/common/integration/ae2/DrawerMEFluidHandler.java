package com.xinyihl.functionalstoragelegacy.common.integration.ae2;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;
import com.xinyihl.functionalstoragelegacy.api.storage.BigFluidStack;
import com.xinyihl.functionalstoragelegacy.api.storage.FluidStorageKey;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigFluidHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageSubscription;
import com.xinyihl.functionalstoragelegacy.api.storage.TransferResult;
import com.xinyihl.functionalstoragelegacy.common.inventory.controller.ControllerFluidHandler;
import net.minecraftforge.fluids.FluidStack;

import java.util.function.Consumer;

/** AE2 adapter shared by individual fluid drawers and aggregate controller storage. */
public class DrawerMEFluidHandler implements IDrawerMEInventoryHandler<IAEFluidStack>,
        AE2StorageChangeSource<IAEFluidStack> {

    private final IBigFluidHandler handler;
    private final IFluidStorageChannel channel;

    public DrawerMEFluidHandler(IBigFluidHandler handler, IFluidStorageChannel channel) {
        this.handler = handler;
        this.channel = channel;
    }

    @Override
    public int getStorageCount() {
        return handler.getStorageCount();
    }

    @Override
    public Object getSnapshot(int index) {
        return handler.getSnapshot(index);
    }

    @Override
    public IAEFluidStack createStack(Object rawSnapshot, long amount) {
        if (!(rawSnapshot instanceof BigFluidStack)) {
            return null;
        }
        BigFluidStack snapshot = (BigFluidStack) rawSnapshot;
        if (!snapshot.hasTemplate()) {
            return null;
        }
        IAEFluidStack result = channel.createStack(snapshot.getTemplate());
        if (result != null) {
            result.setStackSize(amount);
        }
        return result;
    }

    @Override
    public StorageSubscription subscribe(Consumer<Object> listener) {
        return handler.subscribe(change -> listener.accept(change));
    }

    @Override
    public IAEFluidStack injectItems(IAEFluidStack input, Actionable type, IActionSource src) {
        BigFluidStack request = requestOf(input);
        if (request.isEmpty()) {
            return null;
        }
        TransferResult<BigFluidStack, FluidStorageKey> result = fillRouted(
                request, actionOf(type));
        long remaining = result.getRemainingAmount();
        if (remaining == 0L) {
            return null;
        }
        if (remaining == request.getAmount()) {
            return input;
        }
        IAEFluidStack remainder = input.copy();
        remainder.setStackSize(remaining);
        return remainder;
    }

    @Override
    public IAEFluidStack extractItems(IAEFluidStack requestStack, Actionable mode,
                                      IActionSource src) {
        BigFluidStack request = requestOf(requestStack);
        if (request.isEmpty()) {
            return null;
        }
        TransferResult<BigFluidStack, FluidStorageKey> result = drainRouted(
                request, actionOf(mode));
        if (result.getProcessedAmount() == 0L) {
            return null;
        }
        IAEFluidStack extracted = requestStack.copy();
        extracted.setStackSize(result.getProcessedAmount());
        return extracted;
    }

    @Override
    public IItemList<IAEFluidStack> getAvailableItems(IItemList<IAEFluidStack> out) {
        for (int tank = 0; tank < handler.getStorageCount(); tank++) {
            BigFluidStack snapshot = handler.getSnapshot(tank);
            if (snapshot == null || snapshot.isEmpty()) {
                continue;
            }
            IAEFluidStack aeStack = channel.createStack(snapshot.getTemplate());
            if (aeStack != null) {
                aeStack.setStackSize(snapshot.getAmount());
                AE2StorageListHelper.addStorageSaturated(out, aeStack);
            }
        }
        return out;
    }

    @Override
    public IStorageChannel<IAEFluidStack> getChannel() {
        return channel;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(IAEFluidStack input) {
        BigFluidStack request = requestOf(input);
        if (request.isEmpty()) {
            return false;
        }
        ControllerFluidHandler controller = controller();
        if (controller != null) {
            for (Integer index : controller.getCandidateIndices(request)) {
                BigFluidStack snapshot = controller.getIndexedSnapshot(index);
                if (snapshot != null && snapshot.hasTemplate()
                        && snapshot.isSameType(request)) {
                    return true;
                }
            }
            return false;
        }
        for (int tank = 0; tank < handler.getStorageCount(); tank++) {
            BigFluidStack snapshot = handler.getSnapshot(tank);
            if (snapshot != null && snapshot.hasTemplate() && snapshot.isSameType(request)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canAccept(IAEFluidStack input) {
        BigFluidStack request = requestOf(input);
        return !request.isEmpty()
                && fillRouted(request.withAmount(1L), StorageAction.SIMULATE)
                .getProcessedAmount() == 1L;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int pass) {
        return true;
    }

    private ControllerFluidHandler controller() {
        return handler instanceof ControllerFluidHandler
                ? (ControllerFluidHandler) handler : null;
    }

    private static BigFluidStack requestOf(IAEFluidStack stack) {
        if (stack == null || stack.getStackSize() <= 0L) {
            return BigFluidStack.empty();
        }
        FluidStack definition = stack.getFluidStack();
        return definition == null
                ? BigFluidStack.empty()
                : new BigFluidStack(definition, stack.getStackSize());
    }

    private static StorageAction actionOf(Actionable actionable) {
        return actionable == Actionable.SIMULATE ? StorageAction.SIMULATE : StorageAction.EXECUTE;
    }

    private TransferResult<BigFluidStack, FluidStorageKey> fillRouted(
            BigFluidStack request, StorageAction action) {
        ControllerFluidHandler controller = controller();
        return controller == null
                ? handler.fillRouted(request, action)
                : controller.fillRouted(request, action);
    }

    private TransferResult<BigFluidStack, FluidStorageKey> drainRouted(
            BigFluidStack request, StorageAction action) {
        ControllerFluidHandler controller = controller();
        return controller == null
                ? handler.drainRouted(request, action)
                : controller.drainRouted(request, action);
    }
}
