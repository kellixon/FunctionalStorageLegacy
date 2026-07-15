package com.xinyihl.functionalstoragelegacy.common.recipe;

import com.xinyihl.functionalstoragelegacy.common.block.FramedDrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.storage.FramedDrawerStyle;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;

import javax.annotation.Nonnull;

/** 2x2 framed styling recipe: exterior, front, drawer, and optional divider. */
public class FramedDrawerStyleRecipe extends IForgeRegistryEntry.Impl<IRecipe>
        implements IRecipe {

    @Override
    public boolean matches(@Nonnull InventoryCrafting inventory, @Nonnull World world) {
        if (inventory.getSizeInventory() < 3) {
            return false;
        }
        ItemStack exterior = inventory.getStackInSlot(0);
        ItemStack front = inventory.getStackInSlot(1);
        ItemStack drawer = inventory.getStackInSlot(2);
        ItemStack divider = inventory.getSizeInventory() > 3
                ? inventory.getStackInSlot(3) : ItemStack.EMPTY;

        if (!isBlock(exterior) || !isBlock(front) || !isFramedDrawer(drawer)
                || (!divider.isEmpty() && !isBlock(divider))) {
            return false;
        }
        for (int slot = 4; slot < inventory.getSizeInventory(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Nonnull
    @Override
    public ItemStack getCraftingResult(@Nonnull InventoryCrafting inventory) {
        if (inventory.getSizeInventory() < 3) {
            return ItemStack.EMPTY;
        }
        ItemStack result = inventory.getStackInSlot(2).copy();
        result.setCount(1);
        ItemStack divider = inventory.getSizeInventory() > 3
                ? inventory.getStackInSlot(3) : ItemStack.EMPTY;
        new FramedDrawerStyle(
                inventory.getStackInSlot(0),
                inventory.getStackInSlot(1),
                divider).applyToDrawerStack(result);
        return result;
    }

    @Override
    public boolean canFit(int width, int height) {
        return width * height >= 3;
    }

    @Nonnull
    @Override
    public ItemStack getRecipeOutput() {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isDynamic() {
        return true;
    }

    private static boolean isBlock(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemBlock;
    }

    private static boolean isFramedDrawer(ItemStack stack) {
        return isBlock(stack)
                && ((ItemBlock) stack.getItem()).getBlock() instanceof FramedDrawerBlock;
    }
}
