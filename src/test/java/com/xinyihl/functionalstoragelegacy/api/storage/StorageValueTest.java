package com.xinyihl.functionalstoragelegacy.api.storage;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.Test;
import org.junit.BeforeClass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StorageValueTest {

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    public void itemSnapshotCopiesAndNormalizesItsTemplate() {
        Item item = new Item();
        ItemStack source = new ItemStack(item, 32, 4);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("owner", "source");
        source.setTagCompound(tag);

        BigItemStack snapshot = new BigItemStack(source, Long.MAX_VALUE);
        source.setCount(7);
        source.getTagCompound().setString("owner", "mutated");

        ItemStack firstRead = snapshot.getTemplate();
        assertEquals(1, firstRead.getCount());
        assertEquals(4, firstRead.getMetadata());
        assertEquals("source", firstRead.getTagCompound().getString("owner"));
        firstRead.setCount(12);
        firstRead.getTagCompound().setString("owner", "returned");

        ItemStack secondRead = snapshot.getTemplate();
        assertEquals(1, secondRead.getCount());
        assertEquals("source", secondRead.getTagCompound().getString("owner"));
        assertEquals(Integer.MAX_VALUE, snapshot.toItemStack().getCount());
        assertEquals(Long.MAX_VALUE, snapshot.getAmount());
    }

    @Test
    public void zeroAmountItemCanRetainAFilterWithoutBecomingForgeContents() {
        ItemStack filter = new ItemStack(new Item(), 9);
        BigItemStack snapshot = new BigItemStack(filter, 0L);

        assertTrue(snapshot.isEmpty());
        assertTrue(snapshot.hasTemplate());
        assertEquals(1, snapshot.getTemplate().getCount());
        assertTrue(snapshot.toItemStack().isEmpty());
        assertFalse(BigItemStack.empty().hasTemplate());

        BigItemStack negative = new BigItemStack(filter, -8L);
        assertEquals(0L, negative.getAmount());
        assertTrue(negative.isEmpty());
        assertTrue(negative.hasTemplate());
    }

    @Test
    public void fluidSnapshotCopiesAndNormalizesItsTemplate() {
        FluidStack source = new FluidStack(FluidRegistry.WATER, 750);
        source.tag = new NBTTagCompound();
        source.tag.setString("owner", "source");

        BigFluidStack snapshot = new BigFluidStack(source, Long.MAX_VALUE);
        source.amount = 25;
        source.tag.setString("owner", "mutated");

        FluidStack firstRead = snapshot.getTemplate();
        assertNotNull(firstRead);
        assertEquals(1, firstRead.amount);
        assertEquals("source", firstRead.tag.getString("owner"));
        firstRead.amount = 99;
        firstRead.tag.setString("owner", "returned");

        FluidStack secondRead = snapshot.getTemplate();
        assertNotNull(secondRead);
        assertEquals(1, secondRead.amount);
        assertEquals("source", secondRead.tag.getString("owner"));
        assertEquals(Integer.MAX_VALUE, snapshot.toFluidStack().amount);
        assertEquals(Long.MAX_VALUE, snapshot.getAmount());
    }

    @Test
    public void zeroAmountFluidCanRetainAFilterWithoutBecomingForgeContents() {
        BigFluidStack snapshot = new BigFluidStack(new FluidStack(FluidRegistry.WATER, 50), 0L);

        assertTrue(snapshot.isEmpty());
        assertTrue(snapshot.hasTemplate());
        assertNotNull(snapshot.getTemplate());
        assertEquals(1, snapshot.getTemplate().amount);
        assertNull(snapshot.toFluidStack());
        assertFalse(BigFluidStack.empty().hasTemplate());

        BigFluidStack negative = new BigFluidStack(
                new FluidStack(FluidRegistry.WATER, 50), -8L);
        assertEquals(0L, negative.getAmount());
        assertTrue(negative.isEmpty());
        assertTrue(negative.hasTemplate());
    }

    @Test
    public void transferResultDerivesRemainingWithoutOverflow() {
        BigItemStack processed = new BigItemStack(new ItemStack(new Item()), Long.MAX_VALUE);
        TransferResult<BigItemStack> complete = new TransferResult<>(
                Long.MAX_VALUE, processed, StorageAction.SIMULATE);

        assertEquals(Long.MAX_VALUE, complete.getRequestedAmount());
        assertEquals(Long.MAX_VALUE, complete.getProcessedAmount());
        assertEquals(0L, complete.getRemainingAmount());
        assertTrue(complete.isComplete());
        assertEquals(StorageAction.SIMULATE, complete.getAction());

        TransferResult<BigItemStack> partial = new TransferResult<>(
                Long.MAX_VALUE, processed.withAmount(Long.MAX_VALUE - 1L), StorageAction.EXECUTE);
        assertEquals(1L, partial.getRemainingAmount());
        assertFalse(partial.isComplete());

        TransferResult<BigItemStack> zero = new TransferResult<>(
                0L, BigItemStack.empty(), StorageAction.EXECUTE);
        assertTrue(zero.isComplete());
    }

    @Test
    public void transferResultRejectsInvalidAmounts() {
        assertIllegalArgument(new Runnable() {
            @Override
            public void run() {
                new TransferResult<>(-1L, BigItemStack.empty(), StorageAction.EXECUTE);
            }
        });
        assertIllegalArgument(new Runnable() {
            @Override
            public void run() {
                new TransferResult<>(1L,
                        new BigItemStack(new ItemStack(new Item()), 2L), StorageAction.EXECUTE);
            }
        });
        assertNullPointer(new Runnable() {
            @Override
            public void run() {
                new TransferResult<BigItemStack>(0L, null, StorageAction.EXECUTE);
            }
        });
        assertNullPointer(new Runnable() {
            @Override
            public void run() {
                new TransferResult<>(0L, BigItemStack.empty(), null);
            }
        });
    }

    @Test
    public void storageDefaultsAndActionConversionAreExplicit() {
        IStorageHandler handler = new IStorageHandler() {
        };

        assertFalse(handler.isLocked());
        assertFalse(handler.voidsOverflow());
        assertFalse(handler.isCreative());
        assertEquals(StorageAction.SIMULATE, StorageAction.fromSimulation(true));
        assertEquals(StorageAction.EXECUTE, StorageAction.fromSimulation(false));
        assertTrue(StorageAction.SIMULATE.isSimulation());
        assertFalse(StorageAction.EXECUTE.isSimulation());
    }

    private static void assertIllegalArgument(Runnable operation) {
        try {
            operation.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected invariant rejection.
        }
    }

    private static void assertNullPointer(Runnable operation) {
        try {
            operation.run();
            fail("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected invariant rejection.
        }
    }
}
