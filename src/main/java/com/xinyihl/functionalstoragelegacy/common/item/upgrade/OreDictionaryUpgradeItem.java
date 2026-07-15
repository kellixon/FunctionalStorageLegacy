package com.xinyihl.functionalstoragelegacy.common.item.upgrade;

import com.xinyihl.functionalstoragelegacy.api.upgrade.StorageFeature;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class OreDictionaryUpgradeItem extends UtilityUpgradeItem {

    public OreDictionaryUpgradeItem() {
        super(UtilityAction.NONE);
    }

    @Override
    public void applyUpgrade(@Nonnull ItemStack stack, @Nonnull UpgradeState.Builder builder) {
        super.applyUpgrade(stack, builder);
        builder.addFeature(StorageFeature.EQUIVALENT_ITEMS);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(@Nonnull ItemStack stack, @Nullable World worldIn, @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        tooltip.add(TextFormatting.YELLOW + new TextComponentTranslation("item.functionalstoragelegacy.ore_dictionary_upgrade.desc").getUnformattedText());
    }
}
