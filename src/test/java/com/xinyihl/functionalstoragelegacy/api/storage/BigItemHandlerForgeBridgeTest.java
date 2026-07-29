package com.xinyihl.functionalstoragelegacy.api.storage;

import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class BigItemHandlerForgeBridgeTest {

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    public void forgeInsertionReturnsRemainderAndConvertsSimulationMode() {
        Item item = new Item();
        MinimalItemHandler handler = new MinimalItemHandler(10L);
        ItemStack input = new ItemStack(item, 16);

        ItemStack simulatedRemainder = handler.insertItem(0, input, true);
        assertEquals(6, simulatedRemainder.getCount());
        assertEquals(16, input.getCount());
        assertEquals(0L, handler.getSnapshot(0).getAmount());
        assertEquals(0, handler.changeCount);
        assertEquals(StorageAction.SIMULATE, handler.lastInsertAction);

        ItemStack executedRemainder = handler.insertItem(0, input, false);
        assertEquals(6, executedRemainder.getCount());
        assertNotSame(input, executedRemainder);
        assertEquals(10L, handler.getSnapshot(0).getAmount());
        assertEquals(1, handler.changeCount);
        assertEquals(StorageAction.EXECUTE, handler.lastInsertAction);

        assertEquals(16, handler.insertItem(-1, input, false).getCount());
        assertEquals(16, handler.insertItem(1, input, false).getCount());
        assertTrue(handler.insertItem(0, ItemStack.EMPTY, false).isEmpty());
    }

    @Test
    public void forgeViewsSaturateLongValuesAtIntBoundary() {
        Item item = new Item();
        MinimalItemHandler handler = new MinimalItemHandler(Long.MAX_VALUE);
        handler.setSlot(0, new BigItemStack(new ItemStack(item), Long.MAX_VALUE));

        assertEquals(1, handler.getSlots());
        assertEquals(Integer.MAX_VALUE, handler.getStackInSlot(0).getCount());
        assertEquals(Integer.MAX_VALUE, handler.getSlotLimit(0));
        assertTrue(handler.getStackInSlot(-1).isEmpty());
        assertEquals(0, handler.getSlotLimit(1));
    }

    @Test
    public void forgeSlotsExposeAggregatedKeysAndOnlyAddAnEmptySlotWhenAvailable() {
        Item first = new Item();
        Item second = new Item();
        MinimalItemHandler handler = new MinimalItemHandler(10L, 20L, 30L);
        handler.setSlot(0, new BigItemStack(new ItemStack(first), 3L));
        handler.setSlot(1, new BigItemStack(new ItemStack(first), 4L));
        handler.setSlot(2, new BigItemStack(new ItemStack(second), 2L));

        assertEquals(2, handler.getSlots());
        assertEquals(7, handler.getStackInSlot(0).getCount());
        assertEquals(2, handler.getStackInSlot(1).getCount());
        assertEquals(30, handler.getSlotLimit(0));
        assertEquals(30, handler.getSlotLimit(1));

        ItemStack extracted = handler.extractItem(0, 5, false);
        assertEquals(5, extracted.getCount());
        assertEquals(0L, handler.getSnapshot(0).getAmount());
        assertEquals(2L, handler.getSnapshot(1).getAmount());

        ItemStack remainder = handler.insertItem(-1, new ItemStack(first, 5), false);
        assertTrue(remainder.isEmpty());
        assertEquals(5L, handler.getSnapshot(0).getAmount());

        MinimalItemHandler withEmpty = new MinimalItemHandler(10L, 20L, 30L);
        withEmpty.setSlot(0, new BigItemStack(new ItemStack(first), 3L));
        withEmpty.setSlot(1, new BigItemStack(new ItemStack(second), 2L));

        assertEquals(3, withEmpty.getSlots());
        assertTrue(withEmpty.getStackInSlot(0).isEmpty());
        assertEquals(3, withEmpty.getStackInSlot(1).getCount());
        assertEquals(2, withEmpty.getStackInSlot(2).getCount());
        assertEquals(30, withEmpty.getSlotLimit(0));
    }

    @Test
    public void forgeExtractionHonorsItemMaxStackAndSimulation() {
        Item limited = new Item().setMaxStackSize(16);
        MinimalItemHandler handler = new MinimalItemHandler(200L);
        handler.setSlot(0, new BigItemStack(new ItemStack(limited), 100L));

        ItemStack simulated = handler.extractItem(0, 100, true);
        assertEquals(16, simulated.getCount());
        assertEquals(100L, handler.getSnapshot(0).getAmount());
        assertEquals(0, handler.changeCount);
        assertEquals(StorageAction.SIMULATE, handler.lastExtractAction);

        ItemStack executed = handler.extractItem(0, 100, false);
        assertEquals(16, executed.getCount());
        assertEquals(84L, handler.getSnapshot(0).getAmount());
        assertEquals(1, handler.changeCount);
        assertEquals(StorageAction.EXECUTE, handler.lastExtractAction);

        assertTrue(handler.extractItem(-1, 1, false).isEmpty());
        assertTrue(handler.extractItem(0, 0, false).isEmpty());
    }

    @Test
    public void itemValidityUsesSideEffectFreeSimulation() {
        Item item = new Item();
        MinimalItemHandler handler = new MinimalItemHandler(4L);

        assertTrue(handler.isItemValid(0, new ItemStack(item)));
        assertEquals(0L, handler.getSnapshot(0).getAmount());
        assertEquals(0, handler.changeCount);
        assertEquals(StorageAction.SIMULATE, handler.lastInsertAction);
        assertFalse(handler.isItemValid(-1, new ItemStack(item)));
        assertFalse(handler.isItemValid(0, ItemStack.EMPTY));
    }

    @Test
    public void routedInsertionPrefersMatchingAndRetainedTemplatesBeforeEmptySlots() {
        Item item = new Item();
        MinimalItemHandler handler = new MinimalItemHandler(5L, 10L, 3L);
        handler.setSlot(1, new BigItemStack(new ItemStack(item), 4L));
        handler.setSlot(2, new BigItemStack(new ItemStack(item), 0L));

        TransferResult<BigItemStack, ItemStorageKey> result = handler.insertRouted(
                new BigItemStack(new ItemStack(item), 12L), StorageAction.EXECUTE);

        assertEquals(Arrays.asList(1, 2, 0), handler.insertOrder);
        assertEquals(12L, result.getProcessedAmount());
        assertTrue(result.isComplete());
        assertEquals(3L, handler.getSnapshot(0).getAmount());
        assertEquals(10L, handler.getSnapshot(1).getAmount());
        assertEquals(3L, handler.getSnapshot(2).getAmount());
    }

    @Test
    public void routedOperationsHandleLongMaxAndDoNotMutateOnSimulation() {
        Item item = new Item();
        MinimalItemHandler handler = new MinimalItemHandler(Long.MAX_VALUE, Long.MAX_VALUE);
        handler.setSlot(0, new BigItemStack(new ItemStack(item), Long.MAX_VALUE - 2L));

        TransferResult<BigItemStack, ItemStorageKey> simulation = handler.insertRouted(
                new BigItemStack(new ItemStack(item), Long.MAX_VALUE), StorageAction.SIMULATE);
        assertEquals(Long.MAX_VALUE, simulation.getProcessedAmount());
        assertTrue(simulation.isComplete());
        assertEquals(Long.MAX_VALUE - 2L, handler.getSnapshot(0).getAmount());
        assertEquals(0L, handler.getSnapshot(1).getAmount());
        assertEquals(0, handler.changeCount);

        handler.setSlot(0, new BigItemStack(new ItemStack(item), 3L));
        handler.setSlot(1, new BigItemStack(new ItemStack(item), 4L));
        TransferResult<BigItemStack, ItemStorageKey> extracted = handler.extractRouted(
                new BigItemStack(new ItemStack(item), 6L), StorageAction.EXECUTE);
        assertEquals(6L, extracted.getProcessedAmount());
        assertEquals(0L, handler.getSnapshot(0).getAmount());
        assertEquals(1L, handler.getSnapshot(1).getAmount());
    }

    @Test
    public void coreMethodsReturnZeroResultsForIllegalInput() {
        MinimalItemHandler handler = new MinimalItemHandler(5L);
        BigItemStack request = new BigItemStack(new ItemStack(new Item()), 4L);

        TransferResult<BigItemStack, ItemStorageKey> invalidInsert = handler.insert(
                -1, request, StorageAction.EXECUTE);
        assertEquals(4L, invalidInsert.getRequestedAmount());
        assertEquals(0L, invalidInsert.getProcessedAmount());
        assertEquals(0L, handler.extract(2, 4L, StorageAction.EXECUTE).getProcessedAmount());
        assertEquals(0L, handler.extract(0, -4L, StorageAction.EXECUTE).getRequestedAmount());
        assertTrue(handler.getSnapshot(-1).isEmpty());
        assertEquals(0L, handler.getCapacity(1));
    }

    private static final class MinimalItemHandler implements IBigItemHandler {
        private final BigItemStack[] slots;
        private final long[] capacities;
        private final List<Integer> insertOrder = new ArrayList<>();
        private int changeCount;
        private StorageAction lastInsertAction;
        private StorageAction lastExtractAction;

        private MinimalItemHandler(long... capacities) {
            this.capacities = capacities.clone();
            this.slots = new BigItemStack[capacities.length];
            Arrays.fill(this.slots, BigItemStack.empty());
        }

        @Override
        public int getStorageCount() {
            return slots.length;
        }

        @Nonnull
        @Override
        public BigItemStack getSnapshot(int slot) {
            return valid(slot) ? slots[slot] : BigItemStack.empty();
        }

        @Override
        public long getCapacity(int slot) {
            return valid(slot) ? Math.max(0L, capacities[slot]) : 0L;
        }

        @Nonnull
        @Override
        public TransferResult<BigItemStack, ItemStorageKey> insert(
                int slot, @Nonnull BigItemStack request, @Nonnull StorageAction action) {
            long requested = request == null || request.isEmpty() ? 0L : request.getAmount();
            lastInsertAction = action;
            if (requested == 0L || !valid(slot)) {
                return new TransferResult<>(requested, BigItemStack.empty(), action);
            }
            insertOrder.add(slot);
            BigItemStack current = slots[slot];
            if (current.hasTemplate() && !current.isSameType(request)) {
                return new TransferResult<>(requested, BigItemStack.empty(), action);
            }
            long capacity = getCapacity(slot);
            long space = current.getAmount() >= capacity ? 0L : capacity - current.getAmount();
            long accepted = Math.min(requested, space);
            if (accepted > 0L && action == StorageAction.EXECUTE) {
                long newAmount = current.getAmount() + accepted;
                slots[slot] = current.hasTemplate()
                        ? current.withAmount(newAmount) : request.withAmount(newAmount);
                changeCount++;
            }
            BigItemStack processed = accepted == 0L
                    ? BigItemStack.empty() : request.withAmount(accepted);
            return new TransferResult<>(requested, processed, action);
        }

        @Nonnull
        @Override
        public TransferResult<BigItemStack, ItemStorageKey> extract(
                int slot, long amount, @Nonnull StorageAction action) {
            long requested = Math.max(0L, amount);
            lastExtractAction = action;
            if (requested == 0L || !valid(slot) || slots[slot].isEmpty()) {
                return new TransferResult<>(requested, BigItemStack.empty(), action);
            }
            BigItemStack current = slots[slot];
            long extracted = Math.min(requested, current.getAmount());
            if (extracted > 0L && action == StorageAction.EXECUTE) {
                slots[slot] = current.withAmount(current.getAmount() - extracted);
                changeCount++;
            }
            return new TransferResult<>(requested, current.withAmount(extracted), action);
        }

        private boolean valid(int slot) {
            return slot >= 0 && slot < slots.length;
        }

        private void setSlot(int slot, BigItemStack snapshot) {
            slots[slot] = snapshot;
        }
    }
}
