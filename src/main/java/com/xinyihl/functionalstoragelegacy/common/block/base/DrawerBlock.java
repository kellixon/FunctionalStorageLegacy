package com.xinyihl.functionalstoragelegacy.common.block.base;

import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageAction;
import com.xinyihl.functionalstoragelegacy.api.storage.TransferResult;
import com.xinyihl.functionalstoragelegacy.common.block.DrawerAttachment;
import com.xinyihl.functionalstoragelegacy.common.block.DrawerFaceLayout;
import com.xinyihl.functionalstoragelegacy.common.inventory.CompactingInventoryHandler;
import com.xinyihl.functionalstoragelegacy.common.storage.DrawerLayout;
import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.controller.DrawerControllerTile;
import com.xinyihl.functionalstoragelegacy.misc.Configurations;
import com.xinyihl.functionalstoragelegacy.misc.RegistrationHandler;
import com.xinyihl.functionalstoragelegacy.util.HitBoxesUtil;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
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
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

/**
 * Abstract base class for all drawer blocks.
 * Handles facing, locked state, click interactions, and drop saving.
 */
public abstract class DrawerBlock extends Block {

    public static final PropertyEnum<DrawerAttachment> ATTACHMENT = PropertyEnum.create("attachment", DrawerAttachment.class);
    public static final PropertyDirection HORIZONTAL_FACING = PropertyDirection.create("horizontal_facing", EnumFacing.Plane.HORIZONTAL);
    public static final EnumMap<DrawerFaceLayout, EnumMap<DrawerAttachment, EnumMap<EnumFacing, List<AxisAlignedBB>>>> CACHED_HIT_BOXES = new EnumMap<>(DrawerFaceLayout.class);
    public static final EnumFacing[] HORIZONTAL_VALUES = new EnumFacing[]{EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST};
    private static final long LARGE_DROP_WARNING_THRESHOLD = 6400L;

    public DrawerBlock(Material material) {
        super(material);
        this.setDefaultState(this.blockState.getBaseState().withProperty(ATTACHMENT, DrawerAttachment.WALL).withProperty(HORIZONTAL_FACING, EnumFacing.NORTH));
        this.setHardness(2.5F);
        this.setResistance(8.0F);

        this.useNeighborBrightness = true;
        this.setLightOpacity(255);

        this.setCreativeTab(RegistrationHandler.CREATIVE_TAB);
    }

    public static DrawerAttachment getAttachment(IBlockState state) {
        return state.getValue(ATTACHMENT);
    }

    public static EnumFacing getHorizontalFacing(IBlockState state) {
        return state.getValue(HORIZONTAL_FACING);
    }

    public static EnumFacing getFrontFacing(IBlockState state) {
        DrawerAttachment attachment = DrawerBlock.getAttachment(state);
        if (attachment == DrawerAttachment.FLOOR) {
            return EnumFacing.UP;
        }
        if (attachment == DrawerAttachment.CEILING) {
            return EnumFacing.DOWN;
        }
        return DrawerBlock.getHorizontalFacing(state);
    }

    private static long getDroppedItemCount(ControllableDrawerTile tile) {
        long total = countHandlerItems(tile.getItemHandler());
        total = saturatedAdd(total, countUpgrades(tile.getStorageUpgrades()));
        return saturatedAdd(total, countUpgrades(tile.getUtilityUpgrades()));
    }

    private static long countHandlerItems(@Nullable IBigItemHandler handler) {
        if (handler == null) return 0L;
        if (handler instanceof CompactingInventoryHandler) {
            CompactingInventoryHandler compacting = (CompactingInventoryHandler) handler;
            long remaining = compacting.getStoredBaseAmount();
            long total = 0L;
            for (CompactingInventoryHandler.Tier tier : compacting.getTiers()) {
                if (!tier.hasTemplate()) continue;
                long amount = remaining / tier.getBaseUnits();
                total = saturatedAdd(total, amount);
                remaining %= tier.getBaseUnits();
            }
            return total;
        }
        long total = 0L;
        for (int slot = 0; slot < handler.getStorageCount(); slot++) {
            total = saturatedAdd(total, handler.getSnapshot(slot).getAmount());
        }
        return total;
    }

    private static long countUpgrades(ItemStackHandler upgrades) {
        long total = 0L;
        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            total = saturatedAdd(total, upgrades.getStackInSlot(slot).getCount());
        }
        return total;
    }

    private static long saturatedAdd(long left, long right) {
        return left >= Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static void dropUpgrades(World world, BlockPos pos, ItemStackHandler upgrades) {
        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            ItemStack stack = upgrades.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                spawnAsEntity(world, pos, stack.copy());
                upgrades.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    private static void dropStoredItems(World world, BlockPos pos, @Nullable IBigItemHandler handler) {
        if (handler == null) return;
        for (int slot = 0; slot < handler.getStorageCount(); slot++) {
            while (true) {
                BigItemStack snapshot = handler.getSnapshot(slot);
                if (snapshot.getAmount() <= 0L || !snapshot.hasTemplate()) break;
                int amount = Math.max(1, snapshot.getTemplate().getMaxStackSize());
                TransferResult<BigItemStack, ?> extracted = handler.extract(slot, amount, StorageAction.EXECUTE);
                if (extracted.getProcessedAmount() <= 0L || extracted.getProcessed().isEmpty()) break;
                spawnAsEntity(world, pos, extracted.getProcessed().toItemStack());
            }
        }
    }

    /**
     * Copy data from an ItemStack to a placed tile entity.
     */
    protected void copyFromStack(ItemStack stack, ControllableDrawerTile tile) {
        if (stack.hasTagCompound()) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag.hasKey("TileData")) {
                tile.loadTileFromNBT(tag.getCompoundTag("TileData"));
            }
            if (tag.hasKey("Locked")) {
                tile.setLocked(tag.getBoolean("Locked"));
            }
            tile.sendUpdatePacket();
        }
    }

    @Override
    public boolean hasTileEntity(@Nonnull IBlockState state) {
        return true;
    }

    @Override
    public void onBlockClicked(World worldIn, @Nonnull BlockPos pos, @Nonnull EntityPlayer playerIn) {
        if (worldIn.isRemote) return;
        TileEntity te = worldIn.getTileEntity(pos);
        if (te instanceof ControllableDrawerTile) {
            ControllableDrawerTile drawerTile = (ControllableDrawerTile) te;
            int slot = getHitSlot(worldIn.getBlockState(pos), worldIn, pos, playerIn);
            if (slot != -1) {
                drawerTile.onClicked(playerIn, slot);
            }
        }
    }

    @Override
    public void onBlockPlacedBy(@Nonnull World worldIn, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityLivingBase placer, @Nonnull ItemStack stack) {
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack);
        TileEntity te = worldIn.getTileEntity(pos);
        if (te instanceof ControllableDrawerTile) {
            ControllableDrawerTile drawerTile = (ControllableDrawerTile) te;
            copyFromStack(stack, drawerTile);
        }
    }

    @Override
    public void harvestBlock(@Nonnull World worldIn, @Nonnull EntityPlayer player, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nullable TileEntity te, @Nonnull ItemStack stack) {
        super.harvestBlock(worldIn, player, pos, state, te, stack);
        worldIn.setBlockToAir(pos);
    }

    @Override
    public void getDrops(@Nonnull NonNullList<ItemStack> drops, IBlockAccess world, @Nonnull BlockPos pos, @Nonnull IBlockState state, int fortune) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof ControllableDrawerTile) {
            drops.add(Configurations.GENERAL.keepContentsOnBreak
                    ? createStackWithTileData((ControllableDrawerTile) te)
                    : new ItemStack(this));
        }
    }

    @Override
    public boolean removedByPlayer(@Nonnull IBlockState state, @Nonnull World world, @Nonnull BlockPos pos, @Nonnull EntityPlayer player, boolean willHarvest) {
        if (!world.isRemote && !Configurations.GENERAL.keepContentsOnBreak) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof ControllableDrawerTile && getDroppedItemCount((ControllableDrawerTile) te) > LARGE_DROP_WARNING_THRESHOLD) {
                ControllableDrawerTile drawer = (ControllableDrawerTile) te;
                if (!drawer.consumeLargeDropBreakConfirmation()) {
                    drawer.markLargeDropBreakConfirmed();
                    player.sendMessage(new TextComponentTranslation("drawer.break.large_drop_warning")
                            .setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.RED)));
                    return false;
                }
            }
        }
        if (willHarvest) return true; // Delay removal for getDrops
        return super.removedByPlayer(state, world, pos, player, false);
    }

    @Override
    public void breakBlock(World worldIn, @Nonnull BlockPos pos, @Nonnull IBlockState state) {
        TileEntity te = worldIn.getTileEntity(pos);
        if (te instanceof ControllableDrawerTile) {
            ControllableDrawerTile drawerTile = (ControllableDrawerTile) te;
            if (!worldIn.isRemote && !Configurations.GENERAL.keepContentsOnBreak) {
                dropUpgrades(worldIn, pos, drawerTile.getStorageUpgrades());
                dropUpgrades(worldIn, pos, drawerTile.getUtilityUpgrades());
                dropStoredItems(worldIn, pos, drawerTile.getItemHandler());
            }
            if (drawerTile.getControllerPos() != null) {
                TileEntity controllerTE = worldIn.getTileEntity(drawerTile.getControllerPos());
                if (controllerTE instanceof DrawerControllerTile) {
                    ((DrawerControllerTile) controllerTE).removeConnectedDrawer(pos);
                }
            }
        }
        super.breakBlock(worldIn, pos, state);
    }

    @Nonnull
    @Override
    public ItemStack getPickBlock(@Nonnull IBlockState state, @Nonnull RayTraceResult target, World world, @Nonnull BlockPos pos, @Nonnull EntityPlayer player) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof ControllableDrawerTile) {
            return createStackWithTileData((ControllableDrawerTile) te);
        }
        return new ItemStack(this);
    }

    @Override
    public boolean isOpaqueCube(@Nonnull IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(@Nonnull IBlockState state) {
        return false;
    }

    @Override
    public boolean canProvidePower(@Nonnull IBlockState state) {
        return true;
    }

    @Override
    public int getStrongPower(IBlockState state, IBlockAccess worldIn, BlockPos pos, EnumFacing side) {
        return (side == EnumFacing.UP) ? getWeakPower(state, worldIn, pos, side) : 0;
    }

    @Override
    public int getWeakPower(@Nonnull IBlockState state, IBlockAccess blockAccess, @Nonnull BlockPos pos, @Nonnull EnumFacing side) {
        if (!canProvidePower(state)) {
            return 0;
        }
        TileEntity te = blockAccess.getTileEntity(pos);
        if (te instanceof ControllableDrawerTile) {
            return ((ControllableDrawerTile) te).getRedstoneSignal(side);
        }
        return 0;
    }

    @Nonnull
    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, ATTACHMENT, HORIZONTAL_FACING);
    }

    @Nonnull
    @Override
    public IBlockState getStateFromMeta(int meta) {
        int safeMeta = Math.max(0, Math.min(meta, 11));
        DrawerAttachment attachment = DrawerAttachment.byIndex(safeMeta / 4);
        EnumFacing horizontalFacing = HORIZONTAL_VALUES[safeMeta % 4];
        return this.getDefaultState().withProperty(ATTACHMENT, attachment).withProperty(HORIZONTAL_FACING, horizontalFacing);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(ATTACHMENT).getIndex() * 4 + HitBoxesUtil.getHorizontalFacingIndex(state.getValue(HORIZONTAL_FACING));
    }

    @Nonnull
    @Override
    public IBlockState getStateForPlacement(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ, int meta, @Nonnull EntityLivingBase placer, @Nonnull EnumHand hand) {
        EnumFacing placementFacing = HitBoxesUtil.getPlacementFacingFromRay(placer);
        DrawerAttachment attachment = HitBoxesUtil.getAttachmentForPlacement(placementFacing);
        EnumFacing horizontalFacing = HitBoxesUtil.getHorizontalFacingForPlacement(attachment, placementFacing, placer);
        return this.getDefaultState().withProperty(ATTACHMENT, attachment).withProperty(HORIZONTAL_FACING, horizontalFacing);
    }

    @Override
    public boolean onBlockActivated(World worldIn, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityPlayer playerIn, @Nonnull EnumHand hand, @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (worldIn.isRemote) return true;
        TileEntity te = worldIn.getTileEntity(pos);
        if (te instanceof ControllableDrawerTile) {
            ControllableDrawerTile drawerTile = (ControllableDrawerTile) te;
            int slot = getHitSlot(state, worldIn, pos, playerIn);
            return drawerTile.onSlotActivated(playerIn, hand, facing, hitX, hitY, hitZ, slot);
        }
        return false;
    }

    /**
     * Create an ItemStack with tile entity data saved.
     */
    public ItemStack createStackWithTileData(ControllableDrawerTile tile) {
        ItemStack stack = new ItemStack(this);
        if (tile.isLocked() || !tile.isEverythingEmpty()) {
            NBTTagCompound tileTag = tile.saveTileToNBT();
            if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
            stack.getTagCompound().setTag("TileData", tileTag);
        }
        if (tile.isLocked()) {
            if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
            stack.getTagCompound().setBoolean("Locked", true);
        }
        return stack;
    }

    /**
     * Determine which slot was clicked based on ray trace hit position on the front face.
     * Returns -1 if no specific slot was hit (e.g. clicked on the side).
     */
    public int getHitSlot(IBlockState state, World world, BlockPos pos, EntityPlayer player) {
        RayTraceResult hitResult = rayTraceFrontFace(state, world, pos, player);
        if (hitResult == null || hitResult.hitVec == null) {
            return -1;
        }
        Vec3d localHitVec = hitResult.hitVec.subtract(pos.getX(), pos.getY(), pos.getZ());
        int slotIndex = 0;
        for (AxisAlignedBB hitBox : getHitBoxes(state)) {
            if (HitBoxesUtil.containsHitVec(hitBox, localHitVec)) {
                return slotIndex;
            }
            slotIndex++;
        }
        return -1;
    }

    @Nullable
    protected RayTraceResult rayTraceFrontFace(IBlockState state, World world, BlockPos pos, EntityPlayer player) {
        Vec3d start = player.getPositionEyes(1.0F);
        Vec3d look = player.getLookVec();
        double reach = HitBoxesUtil.getPlayerReachDistance(player);
        Vec3d end = start.add(look.x * reach, look.y * reach, look.z * reach);
        RayTraceResult hitResult = this.collisionRayTrace(state, world, pos, start, end);
        if (hitResult == null || hitResult.typeOfHit != RayTraceResult.Type.BLOCK) {
            return null;
        }
        return hitResult.sideHit == getFrontFacing(state) ? hitResult : null;
    }

    public Collection<AxisAlignedBB> getHitBoxes(IBlockState state) {
        DrawerFaceLayout layout = getFaceLayout();
        if (layout == null) {
            return Collections.emptyList();
        }
        DrawerAttachment attachment = getAttachment(state);
        EnumFacing horizontalFacing = getHorizontalFacing(state);
        EnumMap<DrawerAttachment, EnumMap<EnumFacing, List<AxisAlignedBB>>> attachmentCache = CACHED_HIT_BOXES.get(layout);
        if (attachmentCache == null) {
            attachmentCache = new EnumMap<>(DrawerAttachment.class);
            CACHED_HIT_BOXES.put(layout, attachmentCache);
        }
        EnumMap<EnumFacing, List<AxisAlignedBB>> facingCache = attachmentCache.get(attachment);
        if (facingCache == null) {
            facingCache = new EnumMap<>(EnumFacing.class);
            attachmentCache.put(attachment, facingCache);
        }
        List<AxisAlignedBB> cachedShapes = facingCache.get(horizontalFacing);
        if (cachedShapes == null) {
            cachedShapes = HitBoxesUtil.buildHitBoxes(layout, attachment, horizontalFacing);
            facingCache.put(horizontalFacing, cachedShapes);
        }
        return cachedShapes;
    }

    @Nullable
    protected DrawerFaceLayout getFaceLayout() {
        return null;
    }

    public DrawerLayout getDrawerLayout() {
        return null;
    }
}
