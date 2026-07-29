package com.xinyihl.functionalstoragelegacy.common.inventory.controller;

import com.xinyihl.functionalstoragelegacy.api.storage.*;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ControllerItemHandlerTest {

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    public void flatIndexDelegatesToExactlyOneLocalSlot() {
        List<String> calls = new ArrayList<>();
        SpyHandler first = new SpyHandler("first", calls, 2, 8L);
        SpyHandler second = new SpyHandler("second", calls, 1, 8L);
        ControllerItemHandler controller = new ControllerItemHandler();
        controller.setHandlers(Arrays.asList(first, second));
        BigItemStack request = new BigItemStack(new ItemStack(new Item()), 3L);

        TransferResult<BigItemStack, ItemStorageKey> inserted = controller.insert(
                2, request, StorageAction.EXECUTE);

        assertEquals(3, controller.getStorageCount());
        assertEquals(3L, inserted.getProcessedAmount());
        assertEquals(0, first.insertCalls);
        assertEquals(1, second.insertCalls);
        assertEquals(0, second.lastInsertSlot);
        assertEquals(3L, second.getSnapshot(0).getAmount());

        controller.extract(2, 2L, StorageAction.EXECUTE);
        assertEquals(0, first.extractCalls);
        assertEquals(1, second.extractCalls);
        assertEquals(0, second.lastExtractSlot);
        assertEquals(1L, second.getSnapshot(0).getAmount());
    }

    @Test
    public void invalidAndEmptyOperationsNeverReachAChild() {
        SpyHandler child = new SpyHandler("child", new ArrayList<String>(), 1, 8L);
        ControllerItemHandler controller = new ControllerItemHandler();
        controller.setHandlers(Collections.singletonList(child));
        BigItemStack request = new BigItemStack(new ItemStack(new Item()), 3L);

        assertEquals(0L, controller.insert(
                0, BigItemStack.empty(), StorageAction.EXECUTE).getProcessedAmount());
        assertEquals(0L, controller.insert(
                -1, request, StorageAction.EXECUTE).getProcessedAmount());
        assertEquals(0L, controller.insert(
                1, request, StorageAction.EXECUTE).getProcessedAmount());
        assertEquals(0L, controller.extract(
                0, 0L, StorageAction.EXECUTE).getProcessedAmount());
        assertEquals(0L, controller.extract(
                0, -2L, StorageAction.EXECUTE).getProcessedAmount());

        assertEquals(0, child.insertCalls);
        assertEquals(0, child.extractCalls);
    }

    @Test
    public void routedInsertionUsesLockedMatchThenMatchThenUnlockedEmpty() {
        Item item = new Item();
        List<String> calls = new ArrayList<>();
        SpyHandler empty = new SpyHandler("empty", calls, 1, 10L);
        SpyHandler matching = new SpyHandler("matching", calls, 1, 2L);
        matching.setSlot(0, new BigItemStack(new ItemStack(item), 0L));
        SpyHandler locked = new SpyHandler("locked", calls, 1, 2L);
        locked.locked = true;
        locked.setSlot(0, new BigItemStack(new ItemStack(item), 0L));
        ControllerItemHandler controller = new ControllerItemHandler();
        controller.setHandlers(Arrays.asList(empty, matching, locked));

        TransferResult<BigItemStack, ItemStorageKey> inserted = controller.insertRouted(
                new BigItemStack(new ItemStack(item), 6L), StorageAction.EXECUTE);

        assertEquals(Arrays.asList("locked", "matching", "empty"), calls);
        assertEquals(6L, inserted.getProcessedAmount());
        assertEquals(2L, locked.getSnapshot(0).getAmount());
        assertEquals(2L, matching.getSnapshot(0).getAmount());
        assertEquals(2L, empty.getSnapshot(0).getAmount());
    }

    @Test
    public void matchingOnlyInsertionNeverUsesEmptyStorage() {
        Item existingItem = new Item();
        Item unmatchedItem = new Item();
        List<String> calls = new ArrayList<>();
        SpyHandler empty = new SpyHandler("empty", calls, 1, 8L);
        SpyHandler matching = new SpyHandler("matching", calls, 1, 4L);
        matching.setSlot(0, new BigItemStack(new ItemStack(existingItem), 2L));
        ControllerItemHandler controller = new ControllerItemHandler();
        controller.setHandlers(Arrays.asList(empty, matching));

        TransferResult<BigItemStack, ItemStorageKey> matched = controller.insertMatchingRouted(
                new BigItemStack(new ItemStack(existingItem), 3L), StorageAction.EXECUTE);
        TransferResult<BigItemStack, ItemStorageKey> unmatched = controller.insertMatchingRouted(
                new BigItemStack(new ItemStack(unmatchedItem), 3L), StorageAction.EXECUTE);

        assertEquals(2L, matched.getProcessedAmount());
        assertEquals(0L, unmatched.getProcessedAmount());
        assertEquals(Collections.singletonList("matching"), calls);
        assertEquals(4L, matching.getSnapshot(0).getAmount());
        assertTrue(empty.getSnapshot(0).isEmpty());
    }

    @Test
    public void forgeEmptySlotExcludesLockedUnconfiguredChildren() {
        SpyHandler locked = new SpyHandler("locked", new ArrayList<String>(), 1, 8L);
        locked.locked = true;
        ControllerItemHandler controller = new ControllerItemHandler();
        controller.setHandlers(Collections.singletonList(locked));

        assertEquals(0, controller.getSlots());

        SpyHandler unlocked = new SpyHandler("unlocked", new ArrayList<String>(), 1, 8L);
        controller.setHandlers(Collections.singletonList(unlocked));
        assertEquals(1, controller.getSlots());
        assertTrue(controller.getStackInSlot(0).isEmpty());
    }

    @Test
    public void routedSimulationAndExtractionHaveNoCrossSlotMutation() {
        Item item = new Item();
        List<String> calls = new ArrayList<>();
        SpyHandler first = new SpyHandler("first", calls, 1, 4L);
        SpyHandler second = new SpyHandler("second", calls, 1, 4L);
        first.setSlot(0, new BigItemStack(new ItemStack(item), 3L));
        second.setSlot(0, new BigItemStack(new ItemStack(item), 3L));
        ControllerItemHandler controller = new ControllerItemHandler();
        controller.setHandlers(Arrays.asList(first, second));

        TransferResult<BigItemStack, ItemStorageKey> simulation = controller.insertRouted(
                new BigItemStack(new ItemStack(item), 2L), StorageAction.SIMULATE);
        assertEquals(2L, simulation.getProcessedAmount());
        assertEquals(3L, first.getSnapshot(0).getAmount());
        assertEquals(3L, second.getSnapshot(0).getAmount());
        assertTrue(calls.isEmpty());

        TransferResult<BigItemStack, ItemStorageKey> extracted = controller.extractRouted(
                new BigItemStack(new ItemStack(item), 5L), StorageAction.EXECUTE);
        assertEquals(5L, extracted.getProcessedAmount());
        assertEquals(0L, first.getSnapshot(0).getAmount());
        assertEquals(1L, second.getSnapshot(0).getAmount());
    }

    @Test
    public void handlerListRebuildDropsStaleMappingsAndCopiesInput() {
        SpyHandler oldHandler = new SpyHandler("old", new ArrayList<String>(), 2, 8L);
        SpyHandler replacement = new SpyHandler("replacement", new ArrayList<String>(), 1, 8L);
        List<IBigItemHandler> source = new ArrayList<>();
        source.add(oldHandler);
        ControllerItemHandler controller = new ControllerItemHandler();
        controller.setHandlers(source);
        source.clear();
        assertEquals(2, controller.getStorageCount());

        controller.setHandlers(Collections.singletonList(replacement));
        assertEquals(1, controller.getStorageCount());
        assertEquals(0L, controller.getCapacity(1));
        assertTrue(controller.getSnapshot(1).isEmpty());
        try {
            controller.getHandlers().clear();
            throw new AssertionError("handler list must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void duplicateHandlerIdentityMapsAndSimulatesOnlyOnce() {
        SpyHandler shared = new SpyHandler(
                "shared", new ArrayList<String>(), 1, 8L);
        ControllerItemHandler controller = new ControllerItemHandler();
        controller.setHandlers(Arrays.asList(shared, shared));

        TransferResult<BigItemStack, ItemStorageKey> simulated = controller.insertRouted(
                new BigItemStack(new ItemStack(new Item()), 12L),
                StorageAction.SIMULATE);

        assertEquals(1, controller.getHandlers().size());
        assertEquals(1, controller.getStorageCount());
        assertEquals(8L, simulated.getProcessedAmount());
        assertEquals(1, shared.insertCalls);
        assertTrue(shared.getSnapshot(0).isEmpty());
    }

    private static final class SpyHandler implements IBigItemHandler {
        private final String name;
        private final List<String> executeInsertOrder;
        private final BigItemStack[] slots;
        private final long capacity;
        private boolean locked;
        private int insertCalls;
        private int extractCalls;
        private int lastInsertSlot = -1;
        private int lastExtractSlot = -1;

        private SpyHandler(
                String name, List<String> executeInsertOrder, int slotCount, long capacity) {
            this.name = name;
            this.executeInsertOrder = executeInsertOrder;
            this.capacity = capacity;
            this.slots = new BigItemStack[slotCount];
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
            return valid(slot) ? capacity : 0L;
        }

        @Nonnull
        @Override
        public TransferResult<BigItemStack, ItemStorageKey> insert(
                int slot, @Nonnull BigItemStack request, @Nonnull StorageAction action) {
            insertCalls++;
            lastInsertSlot = slot;
            long requested = request == null || request.isEmpty() ? 0L : request.getAmount();
            if (requested == 0L || !valid(slot)) {
                return new TransferResult<>(requested, BigItemStack.empty(), action);
            }
            BigItemStack current = slots[slot];
            if (current.hasTemplate() && !current.isSameType(request)) {
                return new TransferResult<>(requested, BigItemStack.empty(), action);
            }
            long space = current.getAmount() >= capacity ? 0L : capacity - current.getAmount();
            long accepted = Math.min(requested, space);
            if (action == StorageAction.EXECUTE && accepted > 0L) {
                executeInsertOrder.add(name);
                slots[slot] = current.hasTemplate()
                        ? current.withAmount(current.getAmount() + accepted)
                        : request.withAmount(accepted);
            }
            return new TransferResult<>(
                    requested,
                    accepted == 0L ? BigItemStack.empty() : request.withAmount(accepted),
                    action);
        }

        @Nonnull
        @Override
        public TransferResult<BigItemStack, ItemStorageKey> extract(
                int slot, long amount, @Nonnull StorageAction action) {
            extractCalls++;
            lastExtractSlot = slot;
            long requested = Math.max(0L, amount);
            if (requested == 0L || !valid(slot) || slots[slot].isEmpty()) {
                return new TransferResult<>(requested, BigItemStack.empty(), action);
            }
            BigItemStack current = slots[slot];
            long extracted = Math.min(requested, current.getAmount());
            if (action == StorageAction.EXECUTE) {
                slots[slot] = current.withAmount(current.getAmount() - extracted);
            }
            return new TransferResult<>(requested, current.withAmount(extracted), action);
        }

        @Override
        public boolean isLocked() {
            return locked;
        }

        private boolean valid(int slot) {
            return slot >= 0 && slot < slots.length;
        }

        private void setSlot(int slot, BigItemStack stack) {
            slots[slot] = stack;
        }
    }
}
