package com.xinyihl.functionalstoragelegacy.common.item.upgrade;

import com.xinyihl.functionalstoragelegacy.api.storage.BigFluidStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigFluidHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.common.tile.FluidDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import com.xinyihl.functionalstoragelegacy.misc.Configurations;
import com.xinyihl.functionalstoragelegacy.misc.RegistrationHandler;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.List;

public class WaterGenerationUpgradeItem extends UtilityUpgradeItem {

    private final WaterGenerationTire tier;

    public WaterGenerationUpgradeItem(WaterGenerationTire tier) {
        super(UtilityUpgradeItem.UtilityAction.NONE);
        this.tier = tier;
        setCreativeTab(RegistrationHandler.CREATIVE_TAB);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(@Nonnull ItemStack stack, World worldIn, @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);

        int amountPerTick = tier.getGenerationRate();
        String rate = String.format("%d mB/t", amountPerTick);
        tooltip.add(TextFormatting.YELLOW + I18n.format("item.functionalstoragelegacy.upgrade.water_generation.amount") + TextFormatting.WHITE + rate);
    }

    @Override
    public void onTick(ControllableDrawerTile tile, ItemStack upgradeStack, int generatedAmount) {
        if (tile.getWorld().isRemote) return;

        handleWaterGeneration(tile, upgradeStack, generatedAmount);
    }

    private void handleWaterGeneration(ControllableDrawerTile tile, ItemStack upgradeStack, int generatedAmount) {
        if (!(tile instanceof FluidDrawerTile)) {
            return;
        }

        FluidDrawerTile fluidTile = (FluidDrawerTile) tile;
        IBigFluidHandler fluidHandler = fluidTile.getFluidHandler();
        if (fluidHandler == null) {
            return;
        }

        Fluid water = FluidRegistry.WATER;
        if (water == null) {
            return;
        }

        int amountPerTick = tier.getGenerationRate();
        fluidHandler.fillRouted(new BigFluidStack(new FluidStack(water, 1), amountPerTick), StorageAction.EXECUTE);
    }

    public enum WaterGenerationTire {
        T1(1, Configurations.GENERATION.WATER_GENERATION_T1), T2(2, Configurations.GENERATION.WATER_GENERATION_T2), T3(3, Configurations.GENERATION.WATER_GENERATION_T3), T4(4, Configurations.GENERATION.WATER_GENERATION_T4);

        private final int tier;
        private final int generationRate;

        WaterGenerationTire(int tier, int generationRate) {
            this.tier = tier;
            this.generationRate = generationRate;
        }

        public int getTier() {
            return this.tier;
        }

        public int getGenerationRate() {
            return this.generationRate;
        }
    }
}
