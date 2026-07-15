package com.xinyihl.functionalstoragelegacy.common.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.storage.data.IAEItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.api.storage.TransferResult;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;

public class DrawerMEItemHandlerTest {

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    public void injectionUsesRoutedTransactionAndReturnsExactRemainder() {
        Item item = new Item();
        RecordingHandler storage = new RecordingHandler(item, 7L, 0L);
        DrawerMEItemHandler adapter = new DrawerMEItemHandler(storage, null);
        IAEItemStack input = aeStack(new ItemStack(item), 10L);

        IAEItemStack simulatedRemainder = adapter.injectItems(
                input, Actionable.SIMULATE, null);
        assertEquals(3L, simulatedRemainder.getStackSize());
        assertEquals(StorageAction.SIMULATE, storage.lastAction);
        assertEquals(0L, storage.stored);

        IAEItemStack executedRemainder = adapter.injectItems(
                input, Actionable.MODULATE, null);
        assertEquals(3L, executedRemainder.getStackSize());
        assertEquals(StorageAction.EXECUTE, storage.lastAction);
        assertEquals(7L, storage.stored);
    }

    @Test
    public void extractionUsesRoutedTransactionAndReportsProcessedAmount() {
        Item item = new Item();
        RecordingHandler storage = new RecordingHandler(item, 20L, 9L);
        DrawerMEItemHandler adapter = new DrawerMEItemHandler(storage, null);

        IAEItemStack extracted = adapter.extractItems(
                aeStack(new ItemStack(item), 4L), Actionable.MODULATE, null);

        assertEquals(4L, extracted.getStackSize());
        assertEquals(StorageAction.EXECUTE, storage.lastAction);
        assertEquals(5L, storage.stored);
        IAEItemStack simulated = adapter.extractItems(
                aeStack(new ItemStack(item), 10L), Actionable.SIMULATE, null);
        assertEquals(5L, simulated.getStackSize());
        assertEquals(StorageAction.SIMULATE, storage.lastAction);
        assertEquals(5L, storage.stored);
    }

    private static IAEItemStack aeStack(ItemStack definition, long amount) {
        StackInvocation invocation = new StackInvocation(definition, amount);
        return (IAEItemStack) Proxy.newProxyInstance(
                IAEItemStack.class.getClassLoader(),
                new Class<?>[]{IAEItemStack.class}, invocation);
    }

    private static final class StackInvocation implements InvocationHandler {
        private final ItemStack definition;
        private long amount;

        private StackInvocation(ItemStack definition, long amount) {
            this.definition = definition.copy();
            this.amount = amount;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getStackSize":
                    return amount;
                case "getDefinition":
                    return definition.copy();
                case "copy":
                    return aeStack(definition, amount);
                case "setStackSize":
                    amount = (Long) args[0];
                    return proxy;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "TestAEItemStack{" + amount + '}';
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        }
    }

    private static final class RecordingHandler implements IBigItemHandler {
        private final Item item;
        private final long capacity;
        private long stored;
        private StorageAction lastAction;

        private RecordingHandler(Item item, long capacity, long stored) {
            this.item = item;
            this.capacity = capacity;
            this.stored = stored;
        }

        @Override
        public int getSlotCount() {
            return 1;
        }

        @Nonnull
        @Override
        public BigItemStack getSlotSnapshot(int slot) {
            return slot == 0 && stored > 0L
                    ? new BigItemStack(new ItemStack(item), stored)
                    : BigItemStack.empty();
        }

        @Override
        public long getSlotCapacity(int slot) {
            return slot == 0 ? capacity : 0L;
        }

        @Nonnull
        @Override
        public TransferResult<BigItemStack> insertIntoSlot(
                int slot, @Nonnull BigItemStack request, @Nonnull StorageAction action) {
            lastAction = action;
            long inserted = slot == 0 ? Math.min(request.getAmount(), capacity - stored) : 0L;
            if (action == StorageAction.EXECUTE) {
                stored += inserted;
            }
            return new TransferResult<>(request.getAmount(), inserted == 0L
                    ? BigItemStack.empty() : request.withAmount(inserted), action);
        }

        @Nonnull
        @Override
        public TransferResult<BigItemStack> extractFromSlot(
                int slot, long amount, @Nonnull StorageAction action) {
            lastAction = action;
            long requested = Math.max(0L, amount);
            long extracted = slot == 0 ? Math.min(requested, stored) : 0L;
            if (action == StorageAction.EXECUTE) {
                stored -= extracted;
            }
            return new TransferResult<>(requested, extracted == 0L
                    ? BigItemStack.empty()
                    : new BigItemStack(new ItemStack(item), extracted), action);
        }
    }
}
