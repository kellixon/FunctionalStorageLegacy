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

    private static Match findMatch(InventoryCrafting inventory) {
        int width = inventory.getWidth();
        int height = inventory.getHeight();
        if (width < 2 || height < 2) {
            return null;
        }
        for (int top = 0; top < height - 1; top++) {
            for (int left = 0; left < width - 1; left++) {
                Match match = matchAt(inventory, left, top);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static Match matchAt(InventoryCrafting inventory, int left, int top) {
        ItemStack exterior = inventory.getStackInRowAndColumn(left, top);
        ItemStack front = inventory.getStackInRowAndColumn(left + 1, top);
        ItemStack drawer = inventory.getStackInRowAndColumn(left, top + 1);
        ItemStack divider = inventory.getStackInRowAndColumn(left + 1, top + 1);
        if (!isBlock(exterior) || !isBlock(front) || !isFramedDrawer(drawer)
                || (!divider.isEmpty() && !isBlock(divider))) {
            return null;
        }
        for (int row = 0; row < inventory.getHeight(); row++) {
            for (int column = 0; column < inventory.getWidth(); column++) {
                boolean inside = column >= left && column <= left + 1
                        && row >= top && row <= top + 1;
                if (!inside && !inventory.getStackInRowAndColumn(column, row).isEmpty()) {
                    return null;
                }
            }
        }
        return new Match(exterior, front, drawer, divider);
    }

    @Override
    public boolean matches(@Nonnull InventoryCrafting inventory, @Nonnull World world) {
        return findMatch(inventory) != null;
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

    @Nonnull
    @Override
    public ItemStack getCraftingResult(@Nonnull InventoryCrafting inventory) {
        Match match = findMatch(inventory);
        if (match == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = match.drawer.copy();
        result.setCount(1);
        new FramedDrawerStyle(
                match.exterior,
                match.front,
                match.divider).applyToDrawerStack(result);
        return result;
    }

    @Override
    public boolean canFit(int width, int height) {
        return width >= 2 && height >= 2;
    }

    private static final class Match {
        private final ItemStack exterior;
        private final ItemStack front;
        private final ItemStack drawer;
        private final ItemStack divider;

        private Match(ItemStack exterior, ItemStack front,
                      ItemStack drawer, ItemStack divider) {
            this.exterior = exterior;
            this.front = front;
            this.drawer = drawer;
            this.divider = divider;
        }
    }
}
