package com.xinyihl.functionalstoragelegacy.common.integration.ae2;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import com.xinyihl.functionalstoragelegacy.api.storage.*;
import com.xinyihl.functionalstoragelegacy.common.inventory.controller.ControllerItemHandler;
import net.minecraft.item.ItemStack;

import java.util.function.Consumer;

/**
 * Dedicated AE2 view of compacting storage. Visible tier snapshots perform the
 * base-unit conversion in the compacting handler; routed transactions preserve
 * that conversion while exposing every configured tier to AE2.
 */
public class CompactingMEItemHandler implements IDrawerMEInventoryHandler<IAEItemStack>, AE2StorageChangeSource<IAEItemStack> {

    private final IBigItemHandler handler;
    private final IItemStorageChannel channel;

    public CompactingMEItemHandler(IBigItemHandler handler, IItemStorageChannel channel) {
        this.handler = handler;
        this.channel = channel;
    }

    private static BigItemStack requestOf(IAEItemStack stack) {
        if (stack == null || stack.getStackSize() <= 0L) {
            return BigItemStack.empty();
        }
        ItemStack definition = stack.getDefinition();
        return definition == null || definition.isEmpty() ? BigItemStack.empty() : new BigItemStack(definition, stack.getStackSize());
    }

    private static StorageAction actionOf(Actionable actionable) {
        return actionable == Actionable.SIMULATE ? StorageAction.SIMULATE : StorageAction.EXECUTE;
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
    public IAEItemStack createStack(Object rawSnapshot, long amount) {
        if (!(rawSnapshot instanceof BigItemStack)) {
            return null;
        }
        BigItemStack snapshot = (BigItemStack) rawSnapshot;
        if (!snapshot.hasTemplate()) {
            return null;
        }
        IAEItemStack result = channel.createStack(snapshot.getTemplate());
        if (result != null) {
            result.setStackSize(amount);
        }
        return result;
    }

    @Override
    public StorageSubscription subscribe(Consumer<Object> listener) {
        return handler.subscribe(listener);
    }

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable type, IActionSource src) {
        BigItemStack request = requestOf(input);
        if (request.isEmpty()) {
            return null;
        }
        TransferResult<BigItemStack, ItemStorageKey> result = insertRouted(request, actionOf(type));
        long remaining = result.getRemainingAmount();
        if (remaining == 0L) {
            return null;
        }
        if (remaining == request.getAmount()) {
            return input;
        }
        IAEItemStack remainder = input.copy();
        remainder.setStackSize(remaining);
        return remainder;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack requestStack, Actionable mode, IActionSource src) {
        BigItemStack request = requestOf(requestStack);
        if (request.isEmpty()) {
            return null;
        }
        TransferResult<BigItemStack, ItemStorageKey> result = extractRouted(request, actionOf(mode));
        if (result.getProcessedAmount() == 0L) {
            return null;
        }
        IAEItemStack extracted = requestStack.copy();
        extracted.setStackSize(result.getProcessedAmount());
        return extracted;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        for (int slot = 0; slot < handler.getStorageCount(); slot++) {
            BigItemStack snapshot = handler.getSnapshot(slot);
            if (snapshot.isEmpty()) {
                continue;
            }
            IAEItemStack aeStack = channel.createStack(snapshot.getTemplate());
            if (aeStack != null) {
                aeStack.setStackSize(snapshot.getAmount());
                AE2StorageListHelper.addStorageSaturated(out, aeStack);
            }
        }
        return out;
    }

    @Override
    public IStorageChannel<IAEItemStack> getChannel() {
        return channel;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(IAEItemStack input) {
        BigItemStack request = requestOf(input);
        if (request.isEmpty()) {
            return false;
        }
        ControllerItemHandler controller = controller();
        if (controller != null) {
            for (Integer index : controller.getCandidateIndices(request)) {
                BigItemStack snapshot = controller.getIndexedSnapshot(index);
                if (snapshot.hasTemplate() && snapshot.isSameType(request)) {
                    return true;
                }
            }
            return false;
        }
        for (int slot = 0; slot < handler.getStorageCount(); slot++) {
            BigItemStack snapshot = handler.getSnapshot(slot);
            if (snapshot.hasTemplate() && snapshot.isSameType(request)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canAccept(IAEItemStack input) {
        BigItemStack request = requestOf(input);
        return !request.isEmpty() && insertRouted(request.withAmount(1L), StorageAction.SIMULATE).getProcessedAmount() == 1L;
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

    private ControllerItemHandler controller() {
        return handler instanceof ControllerItemHandler ? (ControllerItemHandler) handler : null;
    }

    private TransferResult<BigItemStack, ItemStorageKey> insertRouted(BigItemStack request, StorageAction action) {
        ControllerItemHandler controller = controller();
        return controller == null ? handler.insertRouted(request, action) : controller.insertRouted(request, action);
    }

    private TransferResult<BigItemStack, ItemStorageKey> extractRouted(BigItemStack request, StorageAction action) {
        ControllerItemHandler controller = controller();
        return controller == null ? handler.extractRouted(request, action) : controller.extractRouted(request, action);
    }
}
