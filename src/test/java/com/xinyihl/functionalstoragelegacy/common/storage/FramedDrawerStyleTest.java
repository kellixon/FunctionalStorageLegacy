package com.xinyihl.functionalstoragelegacy.common.storage;

import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FramedDrawerStyleTest {

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    public void applyingStylePreservesExistingLongTileData() {
        long amount = (long) Integer.MAX_VALUE + 321L;
        ItemStack drawer = new ItemStack(Items.STICK);
        drawer.setTagCompound(new NBTTagCompound());
        NBTTagCompound tileData = new NBTTagCompound();
        NBTTagCompound storage = new NBTTagCompound();
        storage.setLong("SentinelAmount", amount);
        tileData.setTag("StorageV2", storage);
        drawer.getTagCompound().setTag("TileData", tileData);

        FramedDrawerStyle style = new FramedDrawerStyle(
                new ItemStack(Blocks.OBSIDIAN),
                new ItemStack(Blocks.PLANKS, 1, 4),
                new ItemStack(Blocks.QUARTZ_BLOCK));
        style.applyToDrawerStack(drawer);

        FramedDrawerStyle restored = FramedDrawerStyle.fromDrawerStack(drawer);
        assertEquals(style, restored);
        assertEquals(4, restored.getFront().getMetadata());
        assertEquals(amount, drawer.getTagCompound().getCompoundTag("TileData")
                .getCompoundTag("StorageV2").getLong("SentinelAmount"));
        assertTrue(drawer.getTagCompound().getCompoundTag("TileData")
                .hasKey(FramedDrawerStyle.NBT_KEY));
    }
}
