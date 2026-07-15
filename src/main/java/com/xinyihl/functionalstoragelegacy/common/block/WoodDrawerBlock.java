package com.xinyihl.functionalstoragelegacy.common.block;

import com.xinyihl.functionalstoragelegacy.common.block.base.DrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.storage.DrawerLayout;
import com.xinyihl.functionalstoragelegacy.common.tile.WoodDrawerTile;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Standard wooden drawer block.
 * Each variant is defined by a wood type and drawer type (1/2/4 slots).
 */
public class WoodDrawerBlock extends DrawerBlock {

    private final DrawerLayout drawerLayout;
    private final DrawerWoodType woodType;

    public WoodDrawerBlock(DrawerWoodType woodType, DrawerLayout drawerLayout) {
        super(Material.WOOD);
        this.woodType = woodType;
        this.drawerLayout = drawerLayout;
        this.setRegistryName(woodType.getId() + "_" + drawerLayout.getSlotCount());
        this.setTranslationKey("functionalstoragelegacy." + woodType.getId() + "_" + drawerLayout.getSlotCount());
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(@Nonnull World world, @Nonnull IBlockState state) {
        return new WoodDrawerTile(drawerLayout, woodType);
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

    public DrawerWoodType getWoodType() {
        return woodType;
    }
}
