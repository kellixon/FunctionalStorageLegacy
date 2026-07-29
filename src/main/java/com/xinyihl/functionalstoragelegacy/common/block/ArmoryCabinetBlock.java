package com.xinyihl.functionalstoragelegacy.common.block;

import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.api.storage.TransferResult;
import com.xinyihl.functionalstoragelegacy.common.tile.ArmoryCabinetTile;
import com.xinyihl.functionalstoragelegacy.misc.Configurations;
import com.xinyihl.functionalstoragelegacy.misc.RegistrationHandler;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Block for the armory cabinet.
 * Stores unstackable items. Has a simple facing property, no slot detection.
 * Opens GUI on right-click.
 */
public class ArmoryCabinetBlock extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);

    public ArmoryCabinetBlock() {
        super(Material.IRON);
        this.setRegistryName("armory_cabinet");
        this.setTranslationKey("functionalstoragelegacy.armory_cabinet");
        this.setHardness(5.0F);
        this.setResistance(10.0F);
        this.setCreativeTab(RegistrationHandler.CREATIVE_TAB);
        this.setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    private static void dropStoredItems(World world, BlockPos pos, IBigItemHandler handler) {
        for (int slot = 0; slot < handler.getStorageCount(); slot++) {
            BigItemStack snapshot = handler.getSnapshot(slot);
            if (!snapshot.hasTemplate() || snapshot.getAmount() <= 0L) continue;
            TransferResult<BigItemStack, ?> extracted = handler.extract(slot, snapshot.getAmount(), StorageAction.EXECUTE);
            if (extracted.getProcessedAmount() > 0L && !extracted.getProcessed().isEmpty()) {
                spawnAsEntity(world, pos, extracted.getProcessed().toItemStack());
            }
        }
    }

    @Nonnull
    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Nonnull
    @Override
    public IBlockState getStateFromMeta(int meta) {
        return this.getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Nonnull
    @Override
    public IBlockState getStateForPlacement(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, @Nonnull EnumHand hand) {
        return this.getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public boolean hasTileEntity(@Nonnull IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(@Nonnull World world, @Nonnull IBlockState state) {
        return new ArmoryCabinetTile();
    }

    @Override
    public void onBlockPlacedBy(@Nonnull World worldIn, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityLivingBase placer, @Nonnull ItemStack stack) {
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack);
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey("TileData")) {
            TileEntity te = worldIn.getTileEntity(pos);
            if (te instanceof ArmoryCabinetTile) {
                ((ArmoryCabinetTile) te).loadTileFromNBT(stack.getTagCompound().getCompoundTag("TileData"));
            }
        }
    }

    @Override
    public void getDrops(@Nonnull NonNullList<ItemStack> drops, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, @Nonnull IBlockState state, int fortune) {
        // Don't add default drops - handled by breakBlock
    }

    @Override
    public boolean removedByPlayer(@Nonnull IBlockState state, @Nonnull World world, @Nonnull BlockPos pos, @Nonnull EntityPlayer player, boolean willHarvest) {
        if (willHarvest) return true;
        return super.removedByPlayer(state, world, pos, player, false);
    }

    @Override
    public void harvestBlock(@Nonnull World worldIn, @Nonnull EntityPlayer player, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nullable TileEntity te, @Nonnull ItemStack stack) {
        super.harvestBlock(worldIn, player, pos, state, te, stack);
        worldIn.setBlockToAir(pos);
    }

    @Override
    public void breakBlock(World worldIn, @Nonnull BlockPos pos, @Nonnull IBlockState state) {
        TileEntity te = worldIn.getTileEntity(pos);
        if (te instanceof ArmoryCabinetTile) {
            ArmoryCabinetTile cabinet = (ArmoryCabinetTile) te;
            ItemStack drop = new ItemStack(this);
            if (Configurations.GENERAL.keepContentsOnBreak && !cabinet.isEverythingEmpty()) {
                NBTTagCompound tileData = cabinet.saveTileToNBT();
                if (!drop.hasTagCompound()) drop.setTagCompound(new NBTTagCompound());
                drop.getTagCompound().setTag("TileData", tileData);
            } else if (!Configurations.GENERAL.keepContentsOnBreak) {
                dropStoredItems(worldIn, pos, cabinet.getStorage());
            }
            spawnAsEntity(worldIn, pos, drop);
        }
        super.breakBlock(worldIn, pos, state);
    }

    @Nonnull
    @Override
    public ItemStack getPickBlock(@Nonnull IBlockState state, @Nonnull RayTraceResult target, World world, @Nonnull BlockPos pos, @Nonnull EntityPlayer player) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof ArmoryCabinetTile) {
            ArmoryCabinetTile cabinet = (ArmoryCabinetTile) te;
            ItemStack drop = new ItemStack(this);
            if (!cabinet.isEverythingEmpty()) {
                NBTTagCompound tileData = cabinet.saveTileToNBT();
                if (!drop.hasTagCompound()) drop.setTagCompound(new NBTTagCompound());
                drop.getTagCompound().setTag("TileData", tileData);
            }
            return drop;
        }
        return new ItemStack(this);
    }
}
