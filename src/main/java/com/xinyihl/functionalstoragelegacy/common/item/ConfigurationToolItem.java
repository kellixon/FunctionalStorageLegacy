package com.xinyihl.functionalstoragelegacy.common.item;

import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import com.xinyihl.functionalstoragelegacy.misc.RegistrationHandler;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Configuration tool item for drawer blocks.
 * Right-click a drawer to apply the current action.
 * Shift+right-click air to cycle between actions.
 */
public class ConfigurationToolItem extends Item {

    public ConfigurationToolItem() {
        this.setMaxStackSize(1);
        this.setCreativeTab(RegistrationHandler.CREATIVE_TAB);
    }

    public static ConfigurationAction getAction(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey("ConfigAction")) {
            int ordinal = stack.getTagCompound().getInteger("ConfigAction");
            ConfigurationAction[] values = ConfigurationAction.values();
            if (ordinal >= 0 && ordinal < values.length) return values[ordinal];
        }
        return ConfigurationAction.LOCKING;
    }

    public static void setAction(ItemStack stack, ConfigurationAction action) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger("ConfigAction", action.ordinal());
    }

    @Nonnull
    @Override
    public EnumActionResult onItemUse(@Nonnull EntityPlayer player, World worldIn, @Nonnull BlockPos pos, @Nonnull EnumHand hand, @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (worldIn.isRemote) return EnumActionResult.SUCCESS;

        ItemStack stack = player.getHeldItem(hand);
        TileEntity te = worldIn.getTileEntity(pos);
        ConfigurationAction action = getAction(stack);

        if (te instanceof ControllableDrawerTile) {
            ControllableDrawerTile drawerTile = (ControllableDrawerTile) te;
            if (action == ConfigurationAction.LOCKING) {
                drawerTile.toggleLocking();
            } else {
                drawerTile.toggleOption(action);
                if (action.getMax() > 1) {
                    player.sendStatusMessage(new TextComponentTranslation("configurationtool.configmode.indicator.mode_" + drawerTile.getDrawerOptions().getAdvancedValue(action)), true);
                }
            }
            return EnumActionResult.SUCCESS;
        }
        return EnumActionResult.PASS;
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World worldIn, EntityPlayer playerIn, @Nonnull EnumHand handIn) {
        ItemStack stack = playerIn.getHeldItem(handIn);
        if (playerIn.isSneaking()) {
            ConfigurationAction action = getAction(stack);
            ConfigurationAction[] values = ConfigurationAction.values();
            ConfigurationAction newAction = values[(action.ordinal() + 1) % values.length];
            setAction(stack, newAction);

            if (!worldIn.isRemote) {
                playerIn.sendStatusMessage(new TextComponentTranslation("configurationtool.configmode.swapped").appendText(" ").appendSibling(new TextComponentTranslation("configurationtool.configmode." + newAction.name().toLowerCase(Locale.ROOT))), true);
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        return super.onItemRightClick(worldIn, playerIn, handIn);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(@Nonnull ItemStack stack, @Nullable World worldIn, @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        ConfigurationAction action = getAction(stack);
        tooltip.add(TextFormatting.YELLOW + new TextComponentTranslation("configurationtool.configmode").getUnformattedText() + " " + TextFormatting.WHITE + new TextComponentTranslation("configurationtool.configmode." + action.name().toLowerCase(Locale.ROOT)).getUnformattedText());
        tooltip.add(TextFormatting.GRAY + "");
        tooltip.addAll(Arrays.asList(new TextComponentTranslation("configurationtool.use").getUnformattedText().split("\\\\n")));
    }

    /**
     * Available configuration actions.
     */
    public enum ConfigurationAction {
        LOCKING(1), TOGGLE_NUMBERS(1), TOGGLE_RENDER(1), TOGGLE_UPGRADES(1), INDICATOR(3);

        private final int max;

        ConfigurationAction(int max) {
            this.max = max;
        }

        public int getMax() {
            return max;
        }
    }
}
