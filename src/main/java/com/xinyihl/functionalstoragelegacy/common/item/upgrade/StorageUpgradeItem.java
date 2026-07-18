package com.xinyihl.functionalstoragelegacy.common.item.upgrade;

import com.xinyihl.functionalstoragelegacy.api.upgrade.StorageFeature;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeAttribute;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeModifier;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeState;
import com.xinyihl.functionalstoragelegacy.misc.Configurations;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Item for storage upgrades that increase drawer capacity.
 * Has different tiers (IRON/COPPER/GOLD/DIAMOND/NETHERITE/MAX).
 * Each tier contributes modifiers keyed by {@link UpgradeAttribute}.
 */
public class StorageUpgradeItem extends UpgradeItem {

    private final StorageTier tier;

    public StorageUpgradeItem(StorageTier tier) {
        super(SlotType.STORAGE);
        this.tier = tier;
    }

    public StorageTier getTier() {
        return tier;
    }

    @Override
    public int getReplacementPriority(@Nonnull ItemStack stack) {
        return tier.ordinal();
    }

    public boolean isMaxStorageUpgrade() {
        return tier == StorageTier.MAX;
    }

    public Map<UpgradeAttribute, UpgradeModifier> getModifiers() {
        switch (tier) {
            case IRON: {
                Map<UpgradeAttribute, UpgradeModifier> map = new EnumMap<>(UpgradeAttribute.class);
                map.put(UpgradeAttribute.ITEM_CAPACITY, UpgradeModifier.setBase(1));
                map.put(UpgradeAttribute.FLUID_CAPACITY, UpgradeModifier.setBase(1));
                return map;
            }
            case COPPER:
            case GOLD:
            case DIAMOND:
            case NETHERITE: {
                float mult = getItemStorageMultiplier(tier);
                Map<UpgradeAttribute, UpgradeModifier> map = new EnumMap<>(UpgradeAttribute.class);
                map.put(UpgradeAttribute.ITEM_CAPACITY, UpgradeModifier.multiply(mult));
                map.put(UpgradeAttribute.FLUID_CAPACITY, UpgradeModifier.multiply(mult / Configurations.STORAGE.fluidDivisor));
                map.put(UpgradeAttribute.CONTROLLER_RANGE, UpgradeModifier.addBase(mult / Configurations.STORAGE.rangeDivisor));
                return map;
            }
            case MAX:
            default:
                return Collections.emptyMap();
        }
    }

    @Override
    public void applyUpgrade(@Nonnull ItemStack stack, @Nonnull UpgradeState.Builder builder) {
        super.applyUpgrade(stack, builder);
        if (tier == StorageTier.MAX) {
            builder.addFeature(StorageFeature.MAX_CAPACITY);
        } else {
            builder.addModifiers(getModifiers());
        }
    }


    public float getItemStorageMultiplier(StorageUpgradeItem.StorageTier tier) {
        switch (tier) {
            case COPPER:
                return Configurations.STORAGE.copperMultiplier;
            case GOLD:
                return Configurations.STORAGE.goldMultiplier;
            case DIAMOND:
                return Configurations.STORAGE.diamondMultiplier;
            case NETHERITE:
                return Configurations.STORAGE.netheriteMultiplier;
            case IRON:
            case MAX:
            default:
                return 1.0f;
        }
    }

    @Override
    public boolean hasEffect(@Nonnull ItemStack stack) {
        return tier == StorageTier.NETHERITE || tier == StorageTier.MAX;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(@Nonnull ItemStack stack, @Nullable World worldIn, @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        if (tier == StorageTier.IRON) {
            tooltip.add(TextFormatting.GRAY + new TextComponentTranslation("item.functionalstoragelegacy.iron_downgrade.desc").getUnformattedText());
        } else if (tier == StorageTier.MAX) {
            tooltip.add(TextFormatting.GOLD + new TextComponentTranslation("item.functionalstoragelegacy.max_storage_upgrade.desc").getUnformattedText());
        } else {
            tooltip.add(TextFormatting.YELLOW + new TextComponentTranslation("item.functionalstoragelegacy.storage_upgrade.multiplier", TextFormatting.WHITE + "" + getItemStorageMultiplier(tier) + "x").getUnformattedText());
        }
    }

    /**
     * Storage upgrade tiers with their capacity modifiers.
     * Each tier provides a map of {@link UpgradeAttribute} to {@link UpgradeModifier}
     * describing how the tier affects different aspects of a drawer.
     */
    public enum StorageTier {
        IRON("iron"), COPPER("copper"), GOLD("gold"), DIAMOND("diamond"), NETHERITE("netherite"), MAX("max");

        private final String name;

        StorageTier(String name) {
            this.name = name;
        }

        public boolean isHigherThan(StorageTier other) {
            return ordinal() > other.ordinal();
        }

        public String getName() {
            return name;
        }
    }
}
