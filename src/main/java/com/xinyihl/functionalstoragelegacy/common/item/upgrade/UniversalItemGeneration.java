package com.xinyihl.functionalstoragelegacy.common.item.upgrade;

import com.xinyihl.functionalstoragelegacy.common.inventory.CompactingInventoryHandler;
import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.compact.CompactingDrawerTile;

import com.xinyihl.functionalstoragelegacy.misc.Configurations;
import com.xinyihl.functionalstoragelegacy.misc.RegistrationHandler;
import com.xinyihl.functionalstoragelegacy.util.CompactingUtil;
import com.xinyihl.functionalstoragelegacy.util.TimerUtil;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.List;

public class UniversalItemGeneration extends UtilityUpgradeItem {

    private final GenerationTier tier;
    private static ItemStack TARGET_ITEM_CACHE = null;

    public UniversalItemGeneration(GenerationTier tier) {
        super(UtilityAction.NONE);
        this.setCreativeTab(RegistrationHandler.CREATIVE_TAB);
        this.tier = tier;
    }

    public static void loadTargetItemIfNeeded() {
        if (TARGET_ITEM_CACHE != null) return;

        String config = Configurations.GENERATION.UNIVERSAL_ITEMS_GENERATION;

        if (config == null || config.trim().isEmpty()) {
            TARGET_ITEM_CACHE = ItemStack.EMPTY;
        } else {
            TARGET_ITEM_CACHE = parseItemStack(config);
        }
    }

    public static ItemStack parseItemStack(String config) {
        String[] parts = config.trim().split(":");

        if (parts.length < 2) {
            return ItemStack.EMPTY;
        }

        String modid = parts[0];
        String itemName = parts[1];
        int meta = 0;
        if (parts.length >= 3) {
            String metaStr = parts[2];
            if (!metaStr.equals("*")) {
                try {
                    meta = Integer.parseInt(metaStr);
                } catch (NumberFormatException e) {

                }
            }
        }

        ResourceLocation location = new ResourceLocation(modid, itemName);
        Item item = ForgeRegistries.ITEMS.getValue(location);
        if (item == null) {
            return ItemStack.EMPTY;
        }

        if (!item.getHasSubtypes()) {
            meta = 0;
        }

        return new ItemStack(item, Configurations.GENERATION.UNIVERSAL_ITEMS_GENERATION_TICK, meta);
    }

    public static ItemStack getItemGeneration() {
        loadTargetItemIfNeeded();
        return TARGET_ITEM_CACHE;
    }

    @Override
    public void onTick(ControllableDrawerTile tile, ItemStack upgradeStack, int upgradeSlot) {
        if (tile.getWorld().isRemote) {
            return;
        }

        if (getItemGeneration().isEmpty()) {
            return;
        }

        if (!upgradeStack.hasTagCompound()) {
            upgradeStack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound nbt = upgradeStack.getTagCompound();

        TimerUtil.updateAndExecute(nbt, Configurations.GENERATION.UNIVERSAL_ITEMS_GENERATION_TICK, () -> generatItem(tile));
    }

    private boolean generatItem(ControllableDrawerTile tile) {
        IItemHandler handler = tile.getItemHandler();
        if (handler == null) return false;

        ItemStack baseItem = getItemGeneration();
        if (baseItem.isEmpty()) {
            return false;
        }

        ItemStack itemToGenerate = baseItem.copy();
        itemToGenerate.setCount(tier.getGenerationRate());

        if (tile instanceof CompactingDrawerTile) {
            return GenerationTreatment(tile, itemToGenerate);
        }

        ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, itemToGenerate, false);
        return remainder.isEmpty();
    }

    private boolean GenerationTreatment(ControllableDrawerTile tile, ItemStack itemToGenerate) {
        IItemHandler handler = tile.getItemHandler();
        if (handler == null) return false;

        if (itemToGenerate.isEmpty()) return false;

        if (handler instanceof CompactingInventoryHandler) {
            CompactingInventoryHandler compactingHandler = (CompactingInventoryHandler) handler;

            if (!compactingHandler.isSetup()) {
                if (CompactingUtil.CompressionDrawertrEatment(tile, itemToGenerate, compactingHandler)) return false;
            }
        }

        return CompactingUtil.ItemRemainder(tile, handler, itemToGenerate);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);

        loadTargetItemIfNeeded();
        String rateText = String.format("%d/" + " " +Configurations.GENERATION.UNIVERSAL_ITEMS_GENERATION_TICK + "t", tier.getGenerationRate());
        tooltip.add(TextFormatting.YELLOW + I18n.format("item.functionalstoragelegacy.universal_generation_upgrade.rate") + TextFormatting.WHITE + rateText);

        if (TARGET_ITEM_CACHE != null && !TARGET_ITEM_CACHE.isEmpty()) {
            tooltip.add(TextFormatting.GREEN + I18n.format("item.functionalstoragelegacy.universal_generation_upgrade.target") + TextFormatting.WHITE + TARGET_ITEM_CACHE.getDisplayName());
        } else {
            tooltip.add(TextFormatting.RED + I18n.format("item.functionalstoragelegacy.universal_generation_upgrade.no_target"));
        }

        if (flagIn.isAdvanced()) {
            tooltip.add(TextFormatting.DARK_GRAY + "Config: " + Configurations.GENERATION.UNIVERSAL_ITEMS_GENERATION);
        }
    }

    public enum GenerationTier {
        T1(1),
        T2(2),
        T3(3),
        T4(4);

        private final int tier;

        GenerationTier(int tier) {
            this.tier = tier;
        }

        public int getGenerationRate() {
            switch (this) {
                case T2:
                    return Configurations.GENERATION.UNIVERSAL_GENERATION_RATE_T2;
                case T3:
                    return Configurations.GENERATION.UNIVERSAL_GENERATION_RATE_T3;
                case T4:
                    return Configurations.GENERATION.UNIVERSAL_GENERATION_RATE_T4;
                case T1:
                default:
                    return Configurations.GENERATION.UNIVERSAL_GENERATION_RATE_T1;
            }
        }

        public int getTier() {
            return tier;
        }
    }
}