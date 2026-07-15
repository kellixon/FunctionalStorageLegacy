package com.xinyihl.functionalstoragelegacy.common.integration.ae2;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import com.xinyihl.functionalstoragelegacy.api.storage.BigFluidStack;
import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigFluidHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.api.storage.TransferResult;
import com.xinyihl.functionalstoragelegacy.util.ItemUtil;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.annotation.Nonnull;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiPredicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MEAvailableItemsOverflowTest {

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    public void drawerItemsSaturateDuplicateSlotsAndKeepDifferentTypesSeparate() {
        Item first = new Item();
        Item second = new Item();
        SnapshotItemHandler storage = new SnapshotItemHandler(
                item(first, Long.MAX_VALUE),
                item(first, Long.MAX_VALUE),
                item(second, 7L));
        RecordingList<IAEItemStack> out = itemList();

        new DrawerMEItemHandler(storage, itemChannel()).getAvailableItems(out);

        assertEquals(2, out.size());
        assertEquals(Long.MAX_VALUE, itemAmount(out, first));
        assertTrue(itemAmount(out, first) > 0L);
        assertEquals(7L, itemAmount(out, second));
    }

    @Test
    public void drawerItemsNormalizeNonPositiveExistingStorage() {
        Item item = new Item();
        RecordingList<IAEItemStack> out = itemList();
        out.addStorage(aeItemStack(new ItemStack(item), -9L));

        new DrawerMEItemHandler(
                new SnapshotItemHandler(item(item, 5L)), itemChannel())
                .getAvailableItems(out);

        assertEquals(5L, itemAmount(out, item));
    }

    @Test
    public void compactingItemsSaturateAgainstPrepopulatedOutput() {
        Item first = new Item();
        Item second = new Item();
        RecordingList<IAEItemStack> out = itemList();
        out.addStorage(aeItemStack(new ItemStack(first), Long.MAX_VALUE - 3L));
        SnapshotItemHandler storage = new SnapshotItemHandler(
                item(first, 10L), item(second, 4L));

        new CompactingMEItemHandler(storage, itemChannel()).getAvailableItems(out);

        assertEquals(2, out.size());
        assertEquals(Long.MAX_VALUE, itemAmount(out, first));
        assertTrue(itemAmount(out, first) > 0L);
        assertEquals(4L, itemAmount(out, second));
    }

    @Test
    public void fluidsSaturateDuplicateTanksAndKeepDifferentTypesSeparate() {
        Fluid first = FluidRegistry.WATER;
        Fluid second = FluidRegistry.LAVA;
        SnapshotFluidHandler storage = new SnapshotFluidHandler(
                fluid(first, Long.MAX_VALUE),
                fluid(first, Long.MAX_VALUE),
                fluid(second, 11L));
        RecordingList<IAEFluidStack> out = fluidList();

        new DrawerMEFluidHandler(storage, fluidChannel()).getAvailableItems(out);

        assertEquals(2, out.size());
        assertEquals(Long.MAX_VALUE, fluidAmount(out, first));
        assertTrue(fluidAmount(out, first) > 0L);
        assertEquals(11L, fluidAmount(out, second));
    }

    @Test
    public void monitorVoidFullInjectionPostsNoPhysicalDifference() {
        Item item = new Item();
        MonitorItemHandler storage = MonitorItemHandler.voidFull(item, 64L);
        IItemStorageChannel channel = itemChannel();
        DrawerMEMonitor<IAEItemStack> monitor = new DrawerMEMonitor<>(
                new DrawerMEItemHandler(storage, channel), channel);
        RecordingReceiver receiver = new RecordingReceiver();
        monitor.addListener(receiver, receiver);

        IAEItemStack remainder = monitor.injectItems(
                aeItemStack(new ItemStack(item), 10L),
                Actionable.MODULATE,
                null);

        assertNull(remainder);
        assertEquals(64L, storage.stored);
        assertTrue(receiver.amounts.isEmpty());
    }

    @Test
    public void monitorFirstCreativeConfigurationPostsLongMaxDifference() {
        Item item = new Item();
        MonitorItemHandler storage = MonitorItemHandler.creativeEmpty();
        IItemStorageChannel channel = itemChannel();
        DrawerMEMonitor<IAEItemStack> monitor = new DrawerMEMonitor<>(
                new DrawerMEItemHandler(storage, channel), channel);
        RecordingReceiver receiver = new RecordingReceiver();
        monitor.addListener(receiver, receiver);

        assertNull(monitor.injectItems(
                aeItemStack(new ItemStack(item), 1L),
                Actionable.MODULATE,
                null));

        assertEquals(Long.MAX_VALUE, storage.stored);
        assertEquals(Collections.singletonList(Long.MAX_VALUE), receiver.amounts);
    }

    @Test
    public void monitorRepeatedCreativeTransactionsPostNoDifference() {
        Item item = new Item();
        MonitorItemHandler storage = MonitorItemHandler.creativeConfigured(item);
        IItemStorageChannel channel = itemChannel();
        DrawerMEMonitor<IAEItemStack> monitor = new DrawerMEMonitor<>(
                new DrawerMEItemHandler(storage, channel), channel);
        RecordingReceiver receiver = new RecordingReceiver();
        monitor.addListener(receiver, receiver);

        assertNull(monitor.injectItems(
                aeItemStack(new ItemStack(item), 8L),
                Actionable.MODULATE,
                null));
        IAEItemStack extracted = monitor.extractItems(
                aeItemStack(new ItemStack(item), 3L),
                Actionable.MODULATE,
                null);

        assertEquals(3L, extracted.getStackSize());
        assertEquals(Long.MAX_VALUE, storage.stored);
        assertTrue(receiver.amounts.isEmpty());
    }

    @Test
    public void monitorFiniteChangesUsePhysicalAmountsAndSimulationDoesNotRefresh() {
        Item item = new Item();
        MonitorItemHandler storage = MonitorItemHandler.normal(10L);
        IItemStorageChannel channel = itemChannel();
        DrawerMEMonitor<IAEItemStack> monitor = new DrawerMEMonitor<>(
                new DrawerMEItemHandler(storage, channel), channel);
        RecordingReceiver receiver = new RecordingReceiver();
        monitor.addListener(receiver, receiver);
        storage.setPhysical(item, 2L);

        monitor.injectItems(
                aeItemStack(new ItemStack(item), 1L),
                Actionable.SIMULATE,
                null);
        assertTrue(receiver.amounts.isEmpty());

        assertEquals(TickRateModulation.URGENT, monitor.onTick());
        assertEquals(Collections.singletonList(2L), receiver.amounts);
        receiver.amounts.clear();

        monitor.injectItems(
                aeItemStack(new ItemStack(item), 5L),
                Actionable.MODULATE,
                null);
        monitor.extractItems(
                aeItemStack(new ItemStack(item), 3L),
                Actionable.MODULATE,
                null);

        assertEquals(4L, storage.stored);
        assertEquals(Arrays.asList(5L, -3L), receiver.amounts);
    }

    private static BigItemStack item(Item item, long amount) {
        return new BigItemStack(new ItemStack(item), amount);
    }

    private static BigFluidStack fluid(Fluid fluid, long amount) {
        return new BigFluidStack(new FluidStack(fluid, 1), amount);
    }

    private static long itemAmount(RecordingList<IAEItemStack> list, Item item) {
        IAEItemStack found = list.findPrecise(aeItemStack(new ItemStack(item), 1L));
        return found == null ? 0L : found.getStackSize();
    }

    private static long fluidAmount(RecordingList<IAEFluidStack> list, Fluid fluid) {
        IAEFluidStack found = list.findPrecise(
                aeFluidStack(new FluidStack(fluid, 1), 1L));
        return found == null ? 0L : found.getStackSize();
    }

    private static RecordingList<IAEItemStack> itemList() {
        return new RecordingList<>((left, right) -> ItemUtil.areItemStacksEqual(
                left.getDefinition(), right.getDefinition()));
    }

    private static RecordingList<IAEFluidStack> fluidList() {
        return new RecordingList<>((left, right) ->
                left.getFluid() == right.getFluid());
    }

    private static IItemStorageChannel itemChannel() {
        return (IItemStorageChannel) Proxy.newProxyInstance(
                IItemStorageChannel.class.getClassLoader(),
                new Class<?>[]{IItemStorageChannel.class},
                (proxy, method, args) -> {
                    if ("createList".equals(method.getName())) {
                        return itemList();
                    }
                    if ("createStack".equals(method.getName())
                            && args != null && args.length == 1
                            && args[0] instanceof ItemStack) {
                        return aeItemStack((ItemStack) args[0], 1L);
                    }
                    return objectMethod(proxy, method, args, "TestItemChannel");
                });
    }

    private static IFluidStorageChannel fluidChannel() {
        return (IFluidStorageChannel) Proxy.newProxyInstance(
                IFluidStorageChannel.class.getClassLoader(),
                new Class<?>[]{IFluidStorageChannel.class},
                (proxy, method, args) -> {
                    if ("createStack".equals(method.getName())
                            && args != null && args.length == 1
                            && args[0] instanceof FluidStack) {
                        return aeFluidStack((FluidStack) args[0], 1L);
                    }
                    return objectMethod(proxy, method, args, "TestFluidChannel");
                });
    }

    private static IAEItemStack aeItemStack(ItemStack definition, long amount) {
        return (IAEItemStack) Proxy.newProxyInstance(
                IAEItemStack.class.getClassLoader(),
                new Class<?>[]{IAEItemStack.class},
                new ItemStackInvocation(definition, amount));
    }

    private static IAEFluidStack aeFluidStack(FluidStack definition, long amount) {
        return (IAEFluidStack) Proxy.newProxyInstance(
                IAEFluidStack.class.getClassLoader(),
                new Class<?>[]{IAEFluidStack.class},
                new FluidStackInvocation(definition, amount));
    }

    private static Object objectMethod(
            Object proxy, Method method, Object[] args, String description) {
        switch (method.getName()) {
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return proxy == args[0];
            case "toString":
                return description;
            default:
                throw new UnsupportedOperationException(method.getName());
        }
    }

    private static final class ItemStackInvocation implements InvocationHandler {
        private final ItemStack definition;
        private long amount;

        private ItemStackInvocation(ItemStack definition, long amount) {
            this.definition = definition.copy();
            this.amount = amount;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getStackSize":
                    return amount;
                case "setStackSize":
                    amount = (Long) args[0];
                    return proxy;
                case "getDefinition":
                    return definition.copy();
                case "copy":
                    return aeItemStack(definition, amount);
                default:
                    return objectMethod(proxy, method, args,
                            "TestAEItemStack{" + amount + '}');
            }
        }
    }

    private static final class FluidStackInvocation implements InvocationHandler {
        private final FluidStack definition;
        private long amount;

        private FluidStackInvocation(FluidStack definition, long amount) {
            this.definition = definition.copy();
            this.amount = amount;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getStackSize":
                    return amount;
                case "setStackSize":
                    amount = (Long) args[0];
                    return proxy;
                case "getFluidStack":
                    return definition.copy();
                case "getFluid":
                    return definition.getFluid();
                case "copy":
                    return aeFluidStack(definition, amount);
                default:
                    return objectMethod(proxy, method, args,
                            "TestAEFluidStack{" + amount + '}');
            }
        }
    }

    /** Deliberately mirrors AE2 1.12's raw long addition. */
    private static final class RecordingList<T extends IAEStack<T>>
            implements IItemList<T> {
        private final BiPredicate<T, T> sameType;
        private final List<T> entries = new ArrayList<>();

        private RecordingList(BiPredicate<T, T> sameType) {
            this.sameType = sameType;
        }

        @Override
        public void addStorage(T stack) {
            T existing = findPrecise(stack);
            if (existing == null) {
                entries.add(stack.copy());
            } else {
                existing.setStackSize(existing.getStackSize() + stack.getStackSize());
            }
        }

        @Override
        public void add(T stack) {
            addStorage(stack);
        }

        @Override
        public T findPrecise(T stack) {
            if (stack == null) {
                return null;
            }
            for (T existing : entries) {
                if (sameType.test(existing, stack)) {
                    return existing;
                }
            }
            return null;
        }

        @Override
        public Collection<T> findFuzzy(T stack, FuzzyMode fuzzy) {
            return Collections.emptyList();
        }

        @Override
        public boolean isEmpty() {
            return entries.isEmpty();
        }

        @Override
        public void addCrafting(T stack) {
        }

        @Override
        public void addRequestable(T stack) {
        }

        @Override
        public T getFirstItem() {
            return entries.isEmpty() ? null : entries.get(0);
        }

        @Override
        public int size() {
            return entries.size();
        }

        @Override
        public Iterator<T> iterator() {
            return entries.iterator();
        }

        @Override
        public void resetStatus() {
        }
    }

    private static final class SnapshotItemHandler implements IBigItemHandler {
        private final BigItemStack[] snapshots;

        private SnapshotItemHandler(BigItemStack... snapshots) {
            this.snapshots = snapshots;
        }

        @Override
        public int getSlotCount() {
            return snapshots.length;
        }

        @Nonnull
        @Override
        public BigItemStack getSlotSnapshot(int slot) {
            return slot < 0 || slot >= snapshots.length
                    ? BigItemStack.empty() : snapshots[slot];
        }

        @Override
        public long getSlotCapacity(int slot) {
            return slot < 0 || slot >= snapshots.length ? 0L : Long.MAX_VALUE;
        }

        @Nonnull
        @Override
        public TransferResult<BigItemStack> insertIntoSlot(
                int slot, @Nonnull BigItemStack request, @Nonnull StorageAction action) {
            return new TransferResult<>(request.getAmount(), BigItemStack.empty(), action);
        }

        @Nonnull
        @Override
        public TransferResult<BigItemStack> extractFromSlot(
                int slot, long amount, @Nonnull StorageAction action) {
            return new TransferResult<>(Math.max(0L, amount), BigItemStack.empty(), action);
        }
    }

    private static final class SnapshotFluidHandler implements IBigFluidHandler {
        private final BigFluidStack[] snapshots;

        private SnapshotFluidHandler(BigFluidStack... snapshots) {
            this.snapshots = snapshots;
        }

        @Override
        public int getTankCount() {
            return snapshots.length;
        }

        @Nonnull
        @Override
        public BigFluidStack getTankSnapshot(int tank) {
            return tank < 0 || tank >= snapshots.length
                    ? BigFluidStack.empty() : snapshots[tank];
        }

        @Override
        public long getTankCapacity(int tank) {
            return tank < 0 || tank >= snapshots.length ? 0L : Long.MAX_VALUE;
        }

        @Nonnull
        @Override
        public TransferResult<BigFluidStack> fillTank(
                int tank, @Nonnull BigFluidStack request, @Nonnull StorageAction action) {
            return new TransferResult<>(request.getAmount(), BigFluidStack.empty(), action);
        }

        @Nonnull
        @Override
        public TransferResult<BigFluidStack> drainTank(
                int tank, long amount, @Nonnull StorageAction action) {
            return new TransferResult<>(Math.max(0L, amount), BigFluidStack.empty(), action);
        }
    }

    private static final class RecordingReceiver
            implements IMEMonitorHandlerReceiver<IAEItemStack> {
        private final List<Long> amounts = new ArrayList<>();

        @Override
        public boolean isValid(Object verificationToken) {
            return verificationToken == this;
        }

        @Override
        public void postChange(
                IBaseMonitor<IAEItemStack> monitor,
                Iterable<IAEItemStack> change,
                IActionSource source) {
            for (IAEItemStack stack : change) {
                amounts.add(stack.getStackSize());
            }
        }

        @Override
        public void onListUpdate() {
        }
    }

    private static final class MonitorItemHandler implements IBigItemHandler {
        private final long capacity;
        private final boolean voidOverflow;
        private final boolean creative;
        private Item item;
        private long stored;

        private MonitorItemHandler(
                long capacity, boolean voidOverflow, boolean creative,
                Item item, long stored) {
            this.capacity = capacity;
            this.voidOverflow = voidOverflow;
            this.creative = creative;
            this.item = item;
            this.stored = stored;
        }

        private static MonitorItemHandler normal(long capacity) {
            return new MonitorItemHandler(capacity, false, false, null, 0L);
        }

        private static MonitorItemHandler voidFull(Item item, long capacity) {
            return new MonitorItemHandler(capacity, true, false, item, capacity);
        }

        private static MonitorItemHandler creativeEmpty() {
            return new MonitorItemHandler(Long.MAX_VALUE, false, true, null, 0L);
        }

        private static MonitorItemHandler creativeConfigured(Item item) {
            return new MonitorItemHandler(
                    Long.MAX_VALUE, false, true, item, Long.MAX_VALUE);
        }

        private void setPhysical(Item item, long stored) {
            this.item = item;
            this.stored = stored;
        }

        @Override
        public int getSlotCount() {
            return 1;
        }

        @Nonnull
        @Override
        public BigItemStack getSlotSnapshot(int slot) {
            return slot == 0 && item != null && stored > 0L
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
            long requested = request.isEmpty() ? 0L : request.getAmount();
            if (slot != 0 || requested == 0L || !accepts(request)) {
                return new TransferResult<>(requested, BigItemStack.empty(), action);
            }
            if (creative) {
                if (item == null && action == StorageAction.EXECUTE) {
                    item = request.getTemplate().getItem();
                    stored = Long.MAX_VALUE;
                }
                return result(request, requested, action);
            }

            long physical = Math.min(requested, Math.max(0L, capacity - stored));
            if (action == StorageAction.EXECUTE && physical > 0L) {
                if (item == null) {
                    item = request.getTemplate().getItem();
                }
                stored += physical;
            }
            return result(request, voidOverflow ? requested : physical, action);
        }

        @Nonnull
        @Override
        public TransferResult<BigItemStack> extractFromSlot(
                int slot, long amount, @Nonnull StorageAction action) {
            long requested = Math.max(0L, amount);
            if (slot != 0 || item == null || requested == 0L) {
                return new TransferResult<>(requested, BigItemStack.empty(), action);
            }
            long extracted = Math.min(requested, stored);
            BigItemStack result = new BigItemStack(new ItemStack(item), extracted);
            if (!creative && action == StorageAction.EXECUTE) {
                stored -= extracted;
                if (stored == 0L) {
                    item = null;
                }
            }
            return new TransferResult<>(requested, result, action);
        }

        private boolean accepts(BigItemStack request) {
            return item == null || request.isSameType(new ItemStack(item));
        }

        private static TransferResult<BigItemStack> result(
                BigItemStack request, long processed, StorageAction action) {
            return new TransferResult<>(
                    request.getAmount(),
                    processed == 0L ? BigItemStack.empty() : request.withAmount(processed),
                    action);
        }
    }
}
