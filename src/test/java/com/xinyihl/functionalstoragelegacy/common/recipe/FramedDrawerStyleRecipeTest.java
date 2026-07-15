package com.xinyihl.functionalstoragelegacy.common.recipe;

import com.xinyihl.functionalstoragelegacy.common.block.FramedDrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.item.DrawerItemBlock;
import com.xinyihl.functionalstoragelegacy.common.storage.DrawerLayout;
import com.xinyihl.functionalstoragelegacy.common.storage.FramedDrawerStyle;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FramedDrawerStyleRecipeTest {

    @BeforeClass
    public static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    public void recipeCopiesDrawerDataAndAppliesAllThreeMaterials() {
        FramedDrawerBlock block = new FramedDrawerBlock(DrawerLayout.X_4);
        DrawerItemBlock item = new DrawerItemBlock(block);
        ItemStack drawer = new ItemStack(item);
        drawer.setTagCompound(new NBTTagCompound());
        NBTTagCompound tileData = new NBTTagCompound();
        NBTTagCompound storage = new NBTTagCompound();
        long amount = (long) Integer.MAX_VALUE + 42L;
        storage.setLong("SentinelAmount", amount);
        tileData.setTag("StorageV2", storage);
        drawer.getTagCompound().setTag("TileData", tileData);

        InventoryCrafting crafting = new InventoryCrafting(new TestContainer(), 2, 2);
        crafting.setInventorySlotContents(0, new ItemStack(Blocks.OBSIDIAN));
        crafting.setInventorySlotContents(1, new ItemStack(Blocks.PLANKS, 1, 5));
        crafting.setInventorySlotContents(2, drawer);
        crafting.setInventorySlotContents(3, new ItemStack(Blocks.QUARTZ_BLOCK));

        FramedDrawerStyleRecipe recipe = new FramedDrawerStyleRecipe();
        assertTrue(recipe.matches(crafting, null));
        ItemStack result = recipe.getCraftingResult(crafting);
        FramedDrawerStyle style = FramedDrawerStyle.fromDrawerStack(result);

        assertEquals(1, result.getCount());
        assertEquals(5, style.getFront().getMetadata());
        assertTrue(ItemStack.areItemStacksEqual(
                new ItemStack(Blocks.OBSIDIAN), style.getExterior()));
        assertTrue(ItemStack.areItemStacksEqual(
                new ItemStack(Blocks.QUARTZ_BLOCK), style.getDivider()));
        assertEquals(amount, result.getTagCompound().getCompoundTag("TileData")
                .getCompoundTag("StorageV2").getLong("SentinelAmount"));
    }

    private static final class TestContainer extends Container {
        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }
    }
}
