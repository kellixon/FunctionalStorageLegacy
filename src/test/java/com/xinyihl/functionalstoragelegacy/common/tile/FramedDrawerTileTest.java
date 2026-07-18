package com.xinyihl.functionalstoragelegacy.common.tile;

import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.common.inventory.base.BigInventoryHandler;
import com.xinyihl.functionalstoragelegacy.common.item.upgrade.StorageUpgradeItem;
import com.xinyihl.functionalstoragelegacy.common.storage.DrawerLayout;
import com.xinyihl.functionalstoragelegacy.common.storage.FramedDrawerStyle;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FramedDrawerTileTest {

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    public void styleAndLongStorageRoundTripTogether() {
        long amount = (long) Integer.MAX_VALUE + 8_765L;
        BigInventoryHandler seededStorage = new BigInventoryHandler(1) {
            @Override
            public double getMultiplier() {
                return Double.POSITIVE_INFINITY;
            }
        };
        seededStorage.insert(
                0,
                new BigItemStack(new ItemStack(Items.DIAMOND), amount),
                StorageAction.EXECUTE);

        FramedDrawerStyle style = new FramedDrawerStyle(
                new ItemStack(Blocks.STONE),
                new ItemStack(Blocks.PLANKS, 1, 2),
                new ItemStack(Blocks.BRICK_BLOCK));
        NBTTagCompound seed = new NBTTagCompound();
        seed.setString("DrawerLayout", DrawerLayout.X_1.getId());
        seed.setTag("StorageV2", seededStorage.serializeNBT()
                .getCompoundTag("StorageV2"));
        seed.setTag(FramedDrawerStyle.NBT_KEY, style.writeToNBT());

        FramedDrawerTile source = new FramedDrawerTile(DrawerLayout.X_1);
        source.loadTileFromNBT(seed);
        NBTTagCompound serialized = source.saveTileToNBT();

        assertTrue(serialized.hasKey("StorageV2", Constants.NBT.TAG_COMPOUND));
        assertTrue(serialized.hasKey(
                FramedDrawerStyle.NBT_KEY, Constants.NBT.TAG_COMPOUND));
        assertEquals(amount, source.getItemHandler().getSnapshot(0).getAmount());
        assertEquals(style, source.getStyle());

        FramedDrawerTile restored = new FramedDrawerTile();
        restored.loadTileFromNBT(serialized);

        assertEquals(amount, restored.getItemHandler().getSnapshot(0).getAmount());
        assertTrue(restored.getItemHandler().getSnapshot(0)
                .isSameType(new ItemStack(Items.DIAMOND)));
        assertEquals(style, restored.getStyle());
        assertEquals(2, restored.getStyle().getFront().getMetadata());
    }

    @Test
    public void configuredStyleMakesAnOtherwiseEmptyDrawerPersistable() {
        FramedDrawerTile tile = new FramedDrawerTile(DrawerLayout.X_2);
        tile.setStyle(new FramedDrawerStyle(
                new ItemStack(Blocks.COBBLESTONE),
                new ItemStack(Blocks.GLASS),
                ItemStack.EMPTY));

        assertTrue(tile.getStyle().isConfigured());
        assertTrue(ItemStack.areItemStacksEqual(
                tile.getStyle().getExterior(), tile.getStyle().getDivider()));
        assertTrue(!tile.isEverythingEmpty());
    }

    @Test
    public void maxStorageUpgradeUsesLongCapacityAndTransfers() {
        FramedDrawerTile tile = new FramedDrawerTile(DrawerLayout.X_1);
        tile.getStorageUpgrades().setStackInSlot(0, new ItemStack(
                new StorageUpgradeItem(StorageUpgradeItem.StorageTier.MAX)));
        long amount = (long) Integer.MAX_VALUE + 99_999L;

        assertEquals(Long.MAX_VALUE, tile.getItemHandler().getCapacity(0));
        assertEquals(amount, tile.getItemHandler().insert(
                0,
                new BigItemStack(new ItemStack(Items.DIAMOND), amount),
                StorageAction.EXECUTE).getProcessedAmount());
        assertEquals(amount, tile.getItemHandler().getSnapshot(0).getAmount());
    }
}
