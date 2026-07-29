package com.xinyihl.functionalstoragelegacy.common.item;

import com.xinyihl.functionalstoragelegacy.common.inventory.EnderItemHandler;
import com.xinyihl.functionalstoragelegacy.common.tile.EnderDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.controller.DrawerControllerTile;
import com.xinyihl.functionalstoragelegacy.common.world.EnderSavedData;
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
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Linking tool item for connecting drawers to controllers and managing ender drawer frequencies.
 */
public class LinkingToolItem extends Item {

    public LinkingToolItem() {
        this.setMaxStackSize(1);
        this.setCreativeTab(RegistrationHandler.CREATIVE_TAB);
    }

    public static LinkingMode getLinkingMode(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey("LinkingMode")) {
            return LinkingMode.values()[stack.getTagCompound().getInteger("LinkingMode")];
        }
        return LinkingMode.SINGLE;
    }

    public static ActionMode getActionMode(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey("ActionMode")) {
            return ActionMode.values()[stack.getTagCompound().getInteger("ActionMode")];
        }
        return ActionMode.ADD;
    }

    public static void setLinkingMode(ItemStack stack, LinkingMode mode) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger("LinkingMode", mode.ordinal());
    }

    public static void setActionMode(ItemStack stack, ActionMode mode) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger("ActionMode", mode.ordinal());
    }

    @Nullable
    public static BlockPos getControllerPos(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey("ControllerPos")) {
            return BlockPos.fromLong(stack.getTagCompound().getLong("ControllerPos"));
        }
        return null;
    }

    public static void setControllerPos(ItemStack stack, BlockPos pos) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setLong("ControllerPos", pos.toLong());
    }

    @Nullable
    public static String getEnderFrequency(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey("EnderFrequency")) {
            return stack.getTagCompound().getString("EnderFrequency");
        }
        return null;
    }

    public static void setEnderFrequency(ItemStack stack, String frequency) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setString("EnderFrequency", frequency);
    }

    public static List<BlockPos> getBlockPosInAABB(AxisAlignedBB aabb) {
        List<BlockPos> blocks = new ArrayList<>();
        for (double y = aabb.minY; y < aabb.maxY; y++) {
            for (double x = aabb.minX; x < aabb.maxX; x++) {
                for (double z = aabb.minZ; z < aabb.maxZ; z++) {
                    blocks.add(new BlockPos((int) x, (int) y, (int) z));
                }
            }
        }
        return blocks;
    }

    @Override
    public boolean hasEffect(@Nonnull ItemStack stack) {
        return getEnderFrequency(stack) != null;
    }

    @Nonnull
    @Override
    public EnumActionResult onItemUse(@Nonnull EntityPlayer player, World worldIn, @Nonnull BlockPos pos, @Nonnull EnumHand hand, @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (worldIn.isRemote) return EnumActionResult.SUCCESS;

        ItemStack stack = player.getHeldItem(hand);
        TileEntity te = worldIn.getTileEntity(pos);

        // Ender drawer frequency management
        if (te instanceof EnderDrawerTile) {
            String storedFreq = getEnderFrequency(stack);
            if (storedFreq != null) {
                EnderItemHandler inventory = EnderSavedData.getInstance(worldIn).getFrequency(((EnderDrawerTile) te).getFrequency());
                if (inventory.getStackInSlot(0).isEmpty() || (player.isSneaking() && hasEnderSafety(stack))) {
                    ((EnderDrawerTile) te).setFrequency(storedFreq);
                    player.sendStatusMessage(new TextComponentTranslation("linkingtool.ender.changed").setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.AQUA)), true);
                    removeEnderSafety(stack);
                } else {
                    player.sendStatusMessage(new TextComponentTranslation("linkingtool.ender.warning").setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.RED)), true);
                    setEnderSafety(stack, true);
                }
                return EnumActionResult.SUCCESS;
            }
        }

        // Controller linking
        if (te instanceof DrawerControllerTile) {
            setControllerPos(stack, pos);
            // Clear ender frequency
            if (stack.hasTagCompound()) {
                stack.getTagCompound().removeTag("EnderFrequency");
            }
            player.sendStatusMessage(new TextComponentTranslation("linkingtool.controller.configured").setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.GREEN)), true);
            return EnumActionResult.SUCCESS;
        }

        // Drawer linking to controller
        if (te instanceof ControllableDrawerTile) {
            BlockPos controllerPos = getControllerPos(stack);
            if (controllerPos != null) {
                TileEntity controllerTE = worldIn.getTileEntity(controllerPos);
                if (controllerTE instanceof DrawerControllerTile) {
                    DrawerControllerTile controller = (DrawerControllerTile) controllerTE;
                    LinkingMode mode = getLinkingMode(stack);
                    ActionMode action = getActionMode(stack);

                    if (mode == LinkingMode.SINGLE) {
                        if (controller.addConnectedDrawers(action, pos)) {
                            String msgKey = action == ActionMode.ADD ? "linkingtool.single_drawer.linked" : "linkingtool.single_drawer.removed";
                            player.sendStatusMessage(new TextComponentTranslation(msgKey).setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.AQUA)), true);
                        }
                    } else { // MULTIPLE mode
                        if (hasFirstPosition(stack)) {
                            BlockPos firstPos = getFirstPosition(stack);
                            AxisAlignedBB aabb = new AxisAlignedBB(Math.min(firstPos.getX(), pos.getX()), Math.min(firstPos.getY(), pos.getY()), Math.min(firstPos.getZ(), pos.getZ()), Math.max(firstPos.getX(), pos.getX()) + 1, Math.max(firstPos.getY(), pos.getY()) + 1, Math.max(firstPos.getZ(), pos.getZ()) + 1);
                            List<BlockPos> positions = getBlockPosInAABB(aabb);
                            if (controller.addConnectedDrawers(action, positions.toArray(new BlockPos[0]))) {
                                String msgKey = action == ActionMode.ADD ? "linkingtool.multiple_drawer.linked" : "linkingtool.multiple_drawer.removed";
                                player.sendStatusMessage(new TextComponentTranslation(msgKey).setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.AQUA)), true);
                            }
                            clearFirstPosition(stack);
                        } else {
                            setFirstPosition(stack, pos);
                        }
                    }
                    return EnumActionResult.SUCCESS;
                }
            }
        }

        return EnumActionResult.PASS;
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World worldIn, EntityPlayer playerIn, @Nonnull EnumHand handIn) {
        ItemStack stack = playerIn.getHeldItem(handIn);
        if (!stack.isEmpty() && !worldIn.isRemote) {
            if (getEnderFrequency(stack) != null) {
                if (playerIn.isSneaking()) {
                    stack.getTagCompound().removeTag("EnderFrequency");
                    playerIn.sendStatusMessage(new TextComponentTranslation("linkingtool.drawer.clear").setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.GREEN)), true);
                }
            } else {
                if (playerIn.isSneaking()) {
                    LinkingMode mode = getLinkingMode(stack);
                    LinkingMode newMode = mode == LinkingMode.SINGLE ? LinkingMode.MULTIPLE : LinkingMode.SINGLE;
                    setLinkingMode(stack, newMode);
                    clearFirstPosition(stack);
                    playerIn.sendStatusMessage(new TextComponentTranslation("linkingtool.linkingmode.swapped", new TextComponentTranslation("linkingtool.linkingmode." + newMode.name().toLowerCase(Locale.ROOT))).setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.GREEN)), true);
                } else {
                    ActionMode mode = getActionMode(stack);
                    ActionMode newMode = mode == ActionMode.ADD ? ActionMode.REMOVE : ActionMode.ADD;
                    setActionMode(stack, newMode);
                    playerIn.sendStatusMessage(new TextComponentTranslation("linkingtool.linkingaction.swapped", new TextComponentTranslation("linkingtool.linkingaction." + newMode.name().toLowerCase(Locale.ROOT))).setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.GOLD)), true);
                }
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        return super.onItemRightClick(worldIn, playerIn, handIn);
    }

    @Override
    public boolean canDestroyBlockInCreative(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull ItemStack stack, @Nonnull EntityPlayer player) {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(@Nonnull ItemStack stack, @Nullable World worldIn, @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        if (getEnderFrequency(stack) != null) {
            tooltip.add(TextFormatting.GRAY + new TextComponentTranslation("linkingtool.ender.frequency").getUnformattedText() + getEnderFrequency(stack));
            tooltip.add("");
            tooltip.add(TextFormatting.GRAY + new TextComponentTranslation("linkingtool.ender.clear").getUnformattedText());
        } else {
            LinkingMode mode = getLinkingMode(stack);
            ActionMode action = getActionMode(stack);
            tooltip.add(TextFormatting.YELLOW + new TextComponentTranslation("linkingtool.linkingmode").getUnformattedText() + " " + TextFormatting.AQUA + new TextComponentTranslation("linkingtool.linkingmode." + mode.name().toLowerCase(Locale.ROOT)).getUnformattedText());
            tooltip.add(TextFormatting.YELLOW + new TextComponentTranslation("linkingtool.linkingaction").getUnformattedText() + " " + TextFormatting.GOLD + new TextComponentTranslation("linkingtool.linkingaction." + action.name().toLowerCase(Locale.ROOT)).getUnformattedText());
            BlockPos controllerPos = getControllerPos(stack);
            if (controllerPos != null) {
                tooltip.add(TextFormatting.YELLOW + new TextComponentTranslation("linkingtool.controller").getUnformattedText() + " " + TextFormatting.DARK_AQUA + controllerPos.getX() + ", " + controllerPos.getY() + ", " + controllerPos.getZ());
            }
            tooltip.add("");
            tooltip.addAll(Arrays.asList(new TextComponentTranslation("linkingtool.use").getUnformattedText().split("\\\\n")));
        }
    }

    // Helper methods for NBT storage
    private boolean hasFirstPosition(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey("FirstPos");
    }

    private BlockPos getFirstPosition(ItemStack stack) {
        return BlockPos.fromLong(stack.getTagCompound().getLong("FirstPos"));
    }

    private void setFirstPosition(ItemStack stack, BlockPos pos) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setLong("FirstPos", pos.toLong());
    }

    private void clearFirstPosition(ItemStack stack) {
        if (stack.hasTagCompound()) stack.getTagCompound().removeTag("FirstPos");
    }

    private boolean hasEnderSafety(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean("EnderSafety");
    }

    private void setEnderSafety(ItemStack stack, boolean safety) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean("EnderSafety", safety);
    }

    private void removeEnderSafety(ItemStack stack) {
        if (stack.hasTagCompound()) stack.getTagCompound().removeTag("EnderSafety");
    }

    public enum LinkingMode {
        SINGLE, MULTIPLE
    }

    public enum ActionMode {
        ADD, REMOVE
    }
}
