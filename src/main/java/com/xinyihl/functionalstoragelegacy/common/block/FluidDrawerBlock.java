package com.xinyihl.functionalstoragelegacy.common.block;

import com.xinyihl.functionalstoragelegacy.common.block.base.DrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.storage.DrawerLayout;
import com.xinyihl.functionalstoragelegacy.common.tile.FluidDrawerTile;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Block for fluid drawers. Supports X_1, X_2, X_4 fluid slot configurations.
 */
public class FluidDrawerBlock extends DrawerBlock {

    private final DrawerLayout drawerLayout;

    public FluidDrawerBlock(DrawerLayout drawerLayout) {
        super(Material.ROCK);
        this.drawerLayout = drawerLayout;
        this.setRegistryName("fluid_" + drawerLayout.getSlotCount());
        this.setTranslationKey("functionalstoragelegacy.fluid_" + drawerLayout.getSlotCount());
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(@Nonnull World world, @Nonnull IBlockState state) {
        return new FluidDrawerTile(drawerLayout);
    }

    @Override
    protected DrawerFaceLayout getFaceLayout() {
        switch (drawerLayout) {
            case X_1:
                return DrawerFaceLayout.X_1;
            case X_2:
                return DrawerFaceLayout.X_2;
            case X_4:
                return DrawerFaceLayout.X_4;
            default:
                return null;
        }
    }

    public DrawerLayout getDrawerLayout() {
        return drawerLayout;
    }

    @Nonnull
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }
}
