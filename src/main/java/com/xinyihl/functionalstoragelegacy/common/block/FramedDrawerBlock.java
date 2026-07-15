package com.xinyihl.functionalstoragelegacy.common.block;

import com.xinyihl.functionalstoragelegacy.common.block.base.DrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.storage.DrawerLayout;
import com.xinyihl.functionalstoragelegacy.common.storage.FramedDrawerStyle;
import com.xinyihl.functionalstoragelegacy.common.tile.FramedDrawerTile;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** A normal item drawer whose exterior, front, and divider textures are configurable. */
public class FramedDrawerBlock extends DrawerBlock {

    public static final IUnlistedProperty<FramedDrawerStyle> STYLE = new StyleProperty();

    private final DrawerLayout drawerLayout;

    public FramedDrawerBlock(DrawerLayout drawerLayout) {
        super(Material.WOOD);
        this.drawerLayout = drawerLayout;
        String name = "framed_" + drawerLayout.getSlotCount();
        setRegistryName(name);
        setTranslationKey("functionalstoragelegacy." + name);
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(@Nonnull World world, @Nonnull IBlockState state) {
        return new FramedDrawerTile(drawerLayout);
    }

    @Nonnull
    @Override
    public IBlockState getExtendedState(@Nonnull IBlockState state,
                                        @Nonnull IBlockAccess world,
                                        @Nonnull BlockPos pos) {
        if (!(state instanceof IExtendedBlockState)) {
            return state;
        }
        TileEntity tile = world.getTileEntity(pos);
        FramedDrawerStyle style = tile instanceof FramedDrawerTile
                ? ((FramedDrawerTile) tile).getStyle() : FramedDrawerStyle.EMPTY;
        return ((IExtendedBlockState) state).withProperty(STYLE, style);
    }

    @Nonnull
    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this,
                new IProperty[]{ATTACHMENT, HORIZONTAL_FACING},
                new IUnlistedProperty[]{STYLE});
    }

    @Nullable
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

    @Override
    public DrawerLayout getDrawerLayout() {
        return drawerLayout;
    }

    private static final class StyleProperty implements IUnlistedProperty<FramedDrawerStyle> {
        @Override
        public String getName() {
            return "framed_style";
        }

        @Override
        public boolean isValid(FramedDrawerStyle value) {
            return value != null;
        }

        @Override
        public Class<FramedDrawerStyle> getType() {
            return FramedDrawerStyle.class;
        }

        @Override
        public String valueToString(FramedDrawerStyle value) {
            return value == null ? "" : value.getCacheKey();
        }
    }
}
