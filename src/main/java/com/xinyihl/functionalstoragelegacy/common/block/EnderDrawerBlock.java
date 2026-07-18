package com.xinyihl.functionalstoragelegacy.common.block;

import com.xinyihl.functionalstoragelegacy.common.block.base.DrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.item.LinkingToolItem;
import com.xinyihl.functionalstoragelegacy.common.storage.DrawerLayout;
import com.xinyihl.functionalstoragelegacy.common.tile.EnderDrawerTile;
import com.xinyihl.functionalstoragelegacy.misc.RegistrationHandler;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Block for ender drawers.
 * Uses X_1 drawer shape for slot detection (single slot).
 * Cross-dimensional shared storage via frequency.
 */
public class EnderDrawerBlock extends DrawerBlock {

    public EnderDrawerBlock() {
        super(Material.ROCK);
        this.setRegistryName("ender_drawer");
        this.setTranslationKey("functionalstoragelegacy.ender_drawer");
        this.setHardness(22.5F);
        this.setResistance(3000.0F);
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(@Nonnull World world, @Nonnull IBlockState state) {
        return new EnderDrawerTile();
    }

    @Override
    protected DrawerFaceLayout getFaceLayout() {
        return DrawerFaceLayout.X_1;
    }

    @Override
    public DrawerLayout getDrawerLayout() {
        return DrawerLayout.X_1;
    }

    @Override
    public void onBlockClicked(World world, @Nonnull BlockPos pos, @Nonnull EntityPlayer player) {
        super.onBlockClicked(world, pos, player);
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof EnderDrawerTile && !player.world.isRemote) {
            ItemStack stack = player.getHeldItemMainhand();
            if (stack.getItem() == RegistrationHandler.LINKING_TOOL) {
                LinkingToolItem.setEnderFrequency(stack, ((EnderDrawerTile) te).getFrequency());
                player.sendStatusMessage(new TextComponentTranslation("linkingtool.ender.stored").setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.AQUA)), true);
            }
        }
    }
}
