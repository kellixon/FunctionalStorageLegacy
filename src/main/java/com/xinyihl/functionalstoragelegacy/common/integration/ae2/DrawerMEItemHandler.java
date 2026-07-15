package com.xinyihl.functionalstoragelegacy.common.integration.ae2;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.api.storage.TransferResult;
import net.minecraft.item.ItemStack;

/** AE2 adapter shared by individual drawers and aggregate controller storage. */
public class DrawerMEItemHandler implements IDrawerMEInventoryHandler<IAEItemStack> {

    private final IBigItemHandler handler;
    private final IItemStorageChannel channel;

    public DrawerMEItemHandler(IBigItemHandler handler, IItemStorageChannel channel) {
        this.handler = handler;
        this.channel = channel;
    }

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable type, IActionSource src) {
        BigItemStack request = requestOf(input);
        if (request.isEmpty()) {
            return null;
        }
        TransferResult<BigItemStack> result = handler.insertRouted(request, actionOf(type));
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
        TransferResult<BigItemStack> result = handler.extractRouted(request, actionOf(mode));
        if (result.getProcessedAmount() == 0L) {
            return null;
        }
        IAEItemStack extracted = requestStack.copy();
        extracted.setStackSize(result.getProcessedAmount());
        return extracted;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        for (int slot = 0; slot < handler.getSlotCount(); slot++) {
            BigItemStack snapshot = handler.getSlotSnapshot(slot);
            if (snapshot == null || snapshot.isEmpty()) {
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
        for (int slot = 0; slot < handler.getSlotCount(); slot++) {
            BigItemStack snapshot = handler.getSlotSnapshot(slot);
            if (snapshot != null && snapshot.hasTemplate() && snapshot.isSameType(request)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canAccept(IAEItemStack input) {
        BigItemStack request = requestOf(input);
        return !request.isEmpty()
                && handler.insertRouted(request.withAmount(1L), StorageAction.SIMULATE)
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

    private static BigItemStack requestOf(IAEItemStack stack) {
        if (stack == null || stack.getStackSize() <= 0L) {
            return BigItemStack.empty();
        }
        ItemStack definition = stack.getDefinition();
        return definition == null || definition.isEmpty()
                ? BigItemStack.empty()
                : new BigItemStack(definition, stack.getStackSize());
    }

    private static StorageAction actionOf(Actionable actionable) {
        return actionable == Actionable.SIMULATE ? StorageAction.SIMULATE : StorageAction.EXECUTE;
    }
}
