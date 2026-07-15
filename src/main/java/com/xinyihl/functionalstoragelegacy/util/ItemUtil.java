package com.xinyihl.functionalstoragelegacy.util;

import com.xinyihl.functionalstoragelegacy.api.upgrade.IStorageUpgrade;
import com.xinyihl.functionalstoragelegacy.common.item.upgrade.DrawerUpgradeBehavior;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nonnull;

public class ItemUtil {

    /**
     * Check if two ItemStacks are the same item with same metadata and NBT (ignoring count).
     */
    public static boolean areItemStacksEqual(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        return a.getItem() == b.getItem() && a.getMetadata() == b.getMetadata() && ItemStack.areItemStackTagsEqual(a, b);
    }

    public static boolean areItemStacksCompatible(ItemStack template, ItemStack stack, boolean allowOreDictionary) {
        if (areItemStacksEqual(template, stack)) {
            return true;
        }
        return allowOreDictionary && sharesOreDictionary(template, stack);
    }

    public static boolean sharesOreDictionary(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }

        int[] firstIds = OreDictionary.getOreIDs(a);
        int[] secondIds = OreDictionary.getOreIDs(b);
        if (firstIds.length == 0 || secondIds.length == 0) {
            return false;
        }

        for (int firstId : firstIds) {
            for (int secondId : secondIds) {
                if (firstId == secondId) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isStorageUpgradeItem(@Nonnull ItemStack stack) {
        if (!(stack.getItem() instanceof IStorageUpgrade)) {
            return false;
        }
        return !(stack.getItem() instanceof DrawerUpgradeBehavior)
                || ((DrawerUpgradeBehavior) stack.getItem()).getSlotType()
                == DrawerUpgradeBehavior.SlotType.STORAGE;
    }

    public static boolean isUtilityUpgradeItem(@Nonnull ItemStack stack) {
        return stack.getItem() instanceof DrawerUpgradeBehavior
                && ((DrawerUpgradeBehavior) stack.getItem()).getSlotType()
                == DrawerUpgradeBehavior.SlotType.UTILITY;
    }

    /**
     * Returns whether {@code candidate} declares a higher automatic replacement priority than
     * the installed storage upgrade. Upgrade implementations without the internal drawer
     * behavior contract are treated as having no replacement priority.
     */
    public static boolean hasHigherUpgradeReplacementPriority(
            @Nonnull ItemStack candidate, @Nonnull ItemStack installed) {
        if (!isStorageUpgradeItem(candidate) || !isStorageUpgradeItem(installed)) {
            return false;
        }
        int candidatePriority = getUpgradeReplacementPriority(candidate);
        return candidatePriority != Integer.MIN_VALUE
                && candidatePriority > getUpgradeReplacementPriority(installed);
    }

    private static int getUpgradeReplacementPriority(@Nonnull ItemStack stack) {
        return stack.getItem() instanceof DrawerUpgradeBehavior
                ? ((DrawerUpgradeBehavior) stack.getItem()).getReplacementPriority(stack)
                : Integer.MIN_VALUE;
    }
}
