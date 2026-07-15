package com.xinyihl.functionalstoragelegacy.common.block.compact;

import com.xinyihl.functionalstoragelegacy.common.block.DrawerFaceLayout;
import com.xinyihl.functionalstoragelegacy.common.block.base.DrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.tile.compact.SimpleCompactingDrawerTile;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Block for the simple compacting drawer (2-slot compression).
 * Uses the same hit shapes as X_2 drawers.
 */
public class SimpleCompactingDrawerBlock extends DrawerBlock {

    public SimpleCompactingDrawerBlock() {
        super(Material.ROCK);
        this.setRegistryName("simple_compacting_drawer");
        this.setTranslationKey("functionalstoragelegacy.simple_compacting_drawer");
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(@Nonnull World world, @Nonnull IBlockState state) {
        return new SimpleCompactingDrawerTile();
    }

    @Override
    protected DrawerFaceLayout getFaceLayout() {
        return DrawerFaceLayout.X_2;
    }
}
