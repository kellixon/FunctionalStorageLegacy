package com.xinyihl.functionalstoragelegacy.common.item.upgrade;

import com.xinyihl.functionalstoragelegacy.api.upgrade.StorageFeature;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeState;
import com.xinyihl.functionalstoragelegacy.common.tile.FluidDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import com.xinyihl.functionalstoragelegacy.misc.Configurations;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.wrappers.BlockLiquidWrapper;
import net.minecraftforge.fluids.capability.wrappers.FluidBlockWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Base class for all utility upgrades.
 */
public class UtilityUpgradeItem extends UpgradeItem {

    private static final String TARGET_POS_KEY = "TargetPos";
    private static final String TARGET_FACE_KEY = "TargetFace";
    private static final String TARGET_DIMENSION_KEY = "TargetDimension";
    private static final String TARGET_NAME_KEY = "TargetName";

    private final UtilityAction utilityAction;
    private final Set<Class<? extends ControllableDrawerTile>> insertableDrawerTypes = new LinkedHashSet<>();

    public UtilityUpgradeItem(UtilityAction utilityAction) {
        super(SlotType.UTILITY);
        this.utilityAction = utilityAction;
    }

    /**
     * Get the direction stored on this upgrade's NBT tag.
     */
    public static EnumFacing getDirection(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey("Direction")) {
            return EnumFacing.byIndex(stack.getTagCompound().getInteger("Direction"));
        }
        return EnumFacing.NORTH;
    }

    /**
     * Set the direction on this upgrade's NBT tag.
     */
    public static void setDirection(ItemStack stack, EnumFacing facing) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger("Direction", facing.getIndex());
    }

    public static boolean hasWirelessTarget(ItemStack stack) {
        return stack.hasTagCompound()
                && stack.getTagCompound().hasKey(TARGET_POS_KEY)
                && stack.getTagCompound().hasKey(TARGET_FACE_KEY)
                && stack.getTagCompound().hasKey(TARGET_DIMENSION_KEY);
    }

    public static void setWirelessTarget(ItemStack stack, BlockPos pos, EnumFacing face, int dimension, String name) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setLong(TARGET_POS_KEY, pos.toLong());
        stack.getTagCompound().setInteger(TARGET_FACE_KEY, face.getIndex());
        stack.getTagCompound().setInteger(TARGET_DIMENSION_KEY, dimension);
        stack.getTagCompound().setString(TARGET_NAME_KEY, name);
    }

    public static void clearWirelessTarget(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        tag.removeTag(TARGET_POS_KEY);
        tag.removeTag(TARGET_FACE_KEY);
        tag.removeTag(TARGET_DIMENSION_KEY);
        tag.removeTag(TARGET_NAME_KEY);
    }

    @Nullable
    public static BlockPos getWirelessTargetPos(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(TARGET_POS_KEY)) {
            return BlockPos.fromLong(stack.getTagCompound().getLong(TARGET_POS_KEY));
        }
        return null;
    }

    public static EnumFacing getWirelessTargetFace(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(TARGET_FACE_KEY)) {
            return EnumFacing.byIndex(stack.getTagCompound().getInteger(TARGET_FACE_KEY));
        }
        return EnumFacing.NORTH;
    }

    public static int getWirelessTargetDimension(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(TARGET_DIMENSION_KEY)) {
            return stack.getTagCompound().getInteger(TARGET_DIMENSION_KEY);
        }
        return Integer.MIN_VALUE;
    }

    public static String getWirelessTargetName(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(TARGET_NAME_KEY)) {
            return stack.getTagCompound().getString(TARGET_NAME_KEY);
        }
        return "?";
    }

    public UtilityAction getUtilityAction() {
        return utilityAction;
    }

    @Override
    public void applyUpgrade(@Nonnull ItemStack stack, @Nonnull UpgradeState.Builder builder) {
        super.applyUpgrade(stack, builder);
        if (utilityAction == UtilityAction.VOID) {
            builder.addFeature(StorageFeature.VOID_OVERFLOW);
        }
    }

    @SafeVarargs
    public final void insertableInto(Class<? extends ControllableDrawerTile>... drawerTypes) {
        for (Class<? extends ControllableDrawerTile> drawerType : drawerTypes) {
            if (drawerType != null) {
                insertableDrawerTypes.add(drawerType);
            }
        }
    }

    public boolean canInsertInto(@Nullable ControllableDrawerTile tile) {
        if (tile == null || insertableDrawerTypes.isEmpty()) {
            return true;
        }

        for (Class<? extends ControllableDrawerTile> drawerType : insertableDrawerTypes) {
            if (drawerType.isInstance(tile)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean canInstallInto(ControllableDrawerTile tile, @Nonnull ItemStack stack) {
        return canInsertInto(tile);
    }

    @Override
    public boolean providesRedstoneSignal(@Nonnull ItemStack stack) {
        return utilityAction == UtilityAction.REDSTONE;
    }

    public boolean isWirelessUtility() {
        return utilityAction == UtilityAction.WIRELESS_PULLING
                || utilityAction == UtilityAction.WIRELESS_PUSHING;
    }

    public boolean isDirectionalUtility() {
        return utilityAction == UtilityAction.PULLING
                || utilityAction == UtilityAction.PUSHING
                || utilityAction == UtilityAction.COLLECTOR;
    }

    @Nonnull
    @Override
    public EnumActionResult onItemUse(@Nonnull EntityPlayer player, World worldIn, @Nonnull BlockPos pos, @Nonnull EnumHand hand,
                                      @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!isWirelessUtility() || !player.isSneaking()) {
            return super.onItemUse(player, worldIn, pos, hand, facing, hitX, hitY, hitZ);
        }

        if (worldIn.isRemote) {
            return EnumActionResult.SUCCESS;
        }

        ItemStack stack = player.getHeldItem(hand);
        Block block = worldIn.getBlockState(pos).getBlock();
        String targetName = block.getLocalizedName();
        setWirelessTarget(stack, pos, facing, worldIn.provider.getDimension(), targetName);

        player.sendStatusMessage(new TextComponentTranslation(
                "item.functionalstoragelegacy.upgrade.wireless.bound",
                targetName,
                new TextComponentTranslation("item.functionalstoragelegacy.upgrade.direction." + facing.getName()),
                pos.getX(), pos.getY(), pos.getZ()
        ).setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.GREEN)), true);
        return EnumActionResult.SUCCESS;
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World worldIn, EntityPlayer playerIn, @Nonnull EnumHand handIn) {
        ItemStack stack = playerIn.getHeldItem(handIn);
        if (isWirelessUtility() && playerIn.isSneaking()) {
            if (!worldIn.isRemote) {
                if (hasWirelessTarget(stack)) {
                    clearWirelessTarget(stack);
                    playerIn.sendStatusMessage(new TextComponentTranslation(
                            "item.functionalstoragelegacy.upgrade.wireless.cleared"
                    ).setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.YELLOW)), true);
                }
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }

        if (isDirectionalUtility() && playerIn.isSneaking()) {
            Vec3d lookVec = playerIn.getLookVec();
            EnumFacing next = EnumFacing.getFacingFromVector((float) -lookVec.x, (float) -lookVec.y, (float) -lookVec.z).getOpposite();
            setDirection(stack, next);

            if (!worldIn.isRemote) {
                playerIn.sendStatusMessage(new TextComponentTranslation(
                        "item.functionalstoragelegacy.upgrade.direction.swapped",
                        new TextComponentTranslation("item.functionalstoragelegacy.upgrade.direction." + next.getName())
                ).setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.GREEN)), true);
            }

            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }

        return super.onItemRightClick(worldIn, playerIn, handIn);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(@Nonnull ItemStack stack, World worldIn, @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        if (isDirectionalUtility()) {
            EnumFacing direction = getDirection(stack);
            tooltip.add(TextFormatting.YELLOW
                    + new TextComponentTranslation("item.functionalstoragelegacy.upgrade.direction").getUnformattedText()
                    + " " + TextFormatting.AQUA
                    + new TextComponentTranslation("item.functionalstoragelegacy.upgrade.direction." + direction.getName()).getUnformattedText());
            tooltip.add(TextFormatting.GRAY
                    + new TextComponentTranslation("item.functionalstoragelegacy.upgrade.direction.use").getUnformattedText());
        }
        if (isWirelessUtility()) {
            if (hasWirelessTarget(stack)) {
                EnumFacing targetFace = getWirelessTargetFace(stack);
                BlockPos targetPos = getWirelessTargetPos(stack);
                tooltip.add(TextFormatting.YELLOW
                        + new TextComponentTranslation(
                        "item.functionalstoragelegacy.upgrade.wireless.target",
                        getWirelessTargetName(stack),
                        new TextComponentTranslation("item.functionalstoragelegacy.upgrade.direction." + targetFace.getName())
                ).getUnformattedText());

                if (targetPos != null) {
                    tooltip.add(TextFormatting.GRAY
                            + new TextComponentTranslation(
                            "item.functionalstoragelegacy.upgrade.wireless.target_pos",
                            targetPos.getX(), targetPos.getY(), targetPos.getZ()
                    ).getUnformattedText());
                }
            } else {
                tooltip.add(TextFormatting.RED
                        + new TextComponentTranslation("item.functionalstoragelegacy.upgrade.wireless.no_target").getUnformattedText());
            }

            tooltip.add(TextFormatting.GRAY
                    + new TextComponentTranslation("item.functionalstoragelegacy.upgrade.wireless.use").getUnformattedText());
            tooltip.add(TextFormatting.GRAY
                    + new TextComponentTranslation("item.functionalstoragelegacy.upgrade.wireless.clear").getUnformattedText());
        }
    }

    /**
     * Called every tick for utility upgrades while installed in a drawer.
     */
    public void onTick(ControllableDrawerTile tile, ItemStack upgradeStack, int upgradeSlot) {
        if (tile.getWorld() == null || tile.getWorld().isRemote) return;

        switch (utilityAction) {
            case PULLING:
                if (tile.getWorld().getTotalWorldTime() % Configurations.GENERAL.upgradeTick != 0) return;
                handlePulling(tile, upgradeStack);
                break;
            case PUSHING:
                if (tile.getWorld().getTotalWorldTime() % Configurations.GENERAL.upgradeTick != 0) return;
                handlePushing(tile, upgradeStack);
                break;
            case COLLECTOR:
                if (tile.getWorld().getTotalWorldTime() % Configurations.GENERAL.upgradeTick != 0) return;
                handleCollector(tile, upgradeStack);
                break;
            case WIRELESS_PULLING:
                if (tile.getWorld().getTotalWorldTime() % Configurations.GENERAL.upgradeTick != 0) return;
                handleWirelessPulling(tile, upgradeStack);
                break;
            case WIRELESS_PUSHING:
                if (tile.getWorld().getTotalWorldTime() % Configurations.GENERAL.upgradeTick != 0) return;
                handleWirelessPushing(tile, upgradeStack);
                break;
            default:
                break;
        }
    }

    @Override
    public void onInstalledTick(ControllableDrawerTile tile, @Nonnull ItemStack stack, int slot) {
        onTick(tile, stack, slot);
    }

    private void handlePulling(ControllableDrawerTile tile, ItemStack upgradeStack) {
        EnumFacing dir = getDirection(upgradeStack);
        BlockPos neighborPos = tile.getPos().offset(dir);
        TileEntity neighborTE = tile.getWorld().getTileEntity(neighborPos);
        if (neighborTE == null) return;

        transferPulling(tile, neighborTE, dir.getOpposite());
    }

    private void handleWirelessPulling(ControllableDrawerTile tile, ItemStack upgradeStack) {
        TileEntity targetTE = getWirelessTargetTile(tile, upgradeStack);
        if (targetTE == null) return;

        transferPulling(tile, targetTE, getWirelessTargetFace(upgradeStack));
    }

    private void transferPulling(ControllableDrawerTile tile, TileEntity sourceTile, EnumFacing sourceFace) {

        // Item pulling
        IItemHandler drawerHandler = tile.getItemHandler();
        if (drawerHandler != null && sourceTile.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, sourceFace)) {
            IItemHandler sourceHandler = sourceTile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, sourceFace);
            if (sourceHandler != null) {
                for (int i = 0; i < sourceHandler.getSlots(); i++) {
                    ItemStack extracted = sourceHandler.extractItem(i, Configurations.GENERAL.upgradePullItems, true);
                    if (!extracted.isEmpty()) {
                        ItemStack remainder = ItemHandlerHelper.insertItemStacked(drawerHandler, extracted, true);
                        if (remainder.getCount() < extracted.getCount()) {
                            int toMove = extracted.getCount() - remainder.getCount();
                            ItemStack actualExtracted = sourceHandler.extractItem(i, toMove, false);
                            ItemHandlerHelper.insertItemStacked(drawerHandler, actualExtracted, false);
                            break;
                        }
                    }
                }
            }
        }

        // Fluid pulling
        if (tile instanceof FluidDrawerTile) {
            FluidDrawerTile fluidTile = (FluidDrawerTile) tile;
            if (sourceTile.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, sourceFace)) {
                IFluidHandler sourceFluid = sourceTile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, sourceFace);
                if (sourceFluid != null) {
                    FluidStack drained = sourceFluid.drain(Configurations.GENERAL.upgradePullFluid, false);
                    if (drained != null && drained.amount > 0) {
                        int filled = fluidTile.getFluidHandler().fill(drained, true);
                        if (filled > 0) {
                            sourceFluid.drain(filled, true);
                        }
                    }
                }
            }
        }
    }

    private void handlePushing(ControllableDrawerTile tile, ItemStack upgradeStack) {
        EnumFacing dir = getDirection(upgradeStack);
        BlockPos neighborPos = tile.getPos().offset(dir);
        TileEntity neighborTE = tile.getWorld().getTileEntity(neighborPos);
        if (neighborTE == null) return;

        transferPushing(tile, neighborTE, dir.getOpposite());
    }

    private void handleWirelessPushing(ControllableDrawerTile tile, ItemStack upgradeStack) {
        TileEntity targetTE = getWirelessTargetTile(tile, upgradeStack);
        if (targetTE == null) return;

        transferPushing(tile, targetTE, getWirelessTargetFace(upgradeStack));
    }

    private void transferPushing(ControllableDrawerTile tile, TileEntity destTile, EnumFacing destFace) {

        // Item pushing
        IItemHandler drawerHandler = tile.getItemHandler();
        if (drawerHandler != null && destTile.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, destFace)) {
            IItemHandler destHandler = destTile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, destFace);
            if (destHandler != null) {
                for (int i = 0; i < drawerHandler.getSlots(); i++) {
                    ItemStack available = drawerHandler.extractItem(i, Configurations.GENERAL.upgradePushItems, true);
                    if (!available.isEmpty()) {
                        ItemStack remainder = ItemHandlerHelper.insertItemStacked(destHandler, available, true);
                        if (remainder.getCount() < available.getCount()) {
                            int toMove = available.getCount() - remainder.getCount();
                            ItemStack extracted = drawerHandler.extractItem(i, toMove, false);
                            ItemHandlerHelper.insertItemStacked(destHandler, extracted, false);
                            break;
                        }
                    }
                }
            }
        }

        // Fluid pushing
        if (tile instanceof FluidDrawerTile) {
            FluidDrawerTile fluidTile = (FluidDrawerTile) tile;
            if (destTile.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, destFace)) {
                IFluidHandler destFluid = destTile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, destFace);
                if (destFluid != null) {
                    FluidStack drained = fluidTile.getFluidHandler().drain(Configurations.GENERAL.upgradePushFluid, false);
                    if (drained != null && drained.amount > 0) {
                        int filled = destFluid.fill(drained, true);
                        if (filled > 0) {
                            fluidTile.getFluidHandler().drain(filled, true);
                        }
                    }
                }
            }
        }
    }

    @Nullable
    private TileEntity getWirelessTargetTile(ControllableDrawerTile tile, ItemStack upgradeStack) {
        if (!hasWirelessTarget(upgradeStack) || tile.getWorld() == null) {
            return null;
        }

        if (tile.getWorld().provider.getDimension() != getWirelessTargetDimension(upgradeStack)) {
            return null;
        }

        BlockPos targetPos = getWirelessTargetPos(upgradeStack);
        if (targetPos == null || targetPos.equals(tile.getPos())) {
            return null;
        }

        return tile.getWorld().getTileEntity(targetPos);
    }

    private void handleCollector(ControllableDrawerTile tile, ItemStack upgradeStack) {
        EnumFacing dir = getDirection(upgradeStack);
        BlockPos collectPos = tile.getPos().offset(dir);
        World world = tile.getWorld();

        IItemHandler drawerHandler = tile.getItemHandler();
        if (drawerHandler != null) {
            // Use exact block AABB for collection area (matches 1.21 behavior)
            AxisAlignedBB collectArea = new AxisAlignedBB(collectPos);
            List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, collectArea, EntitySelectors.IS_ALIVE);
            for (EntityItem entity : items) {
                if (entity.isDead) continue;
                ItemStack entityStack = entity.getItem();
                if (entityStack.isEmpty()) continue;

                // Limit items collected per entity to config value (matches 1.21 behavior)
                int maxCollect = Math.min(entityStack.getCount(), Configurations.GENERAL.upgradeCollectorItems);
                ItemStack toInsert = entityStack.copy();
                toInsert.setCount(maxCollect);

                ItemStack remainder = ItemHandlerHelper.insertItemStacked(drawerHandler, toInsert, false);
                int inserted = maxCollect - (remainder.isEmpty() ? 0 : remainder.getCount());
                if (inserted > 0) {
                    entityStack.shrink(inserted);
                    if (entityStack.isEmpty()) {
                        entity.setDead();
                    } else {
                        entity.setItem(entityStack);
                    }
                    // Stop after processing one entity (matches 1.21 behavior)
                    return;
                }
            }
        }

        if (tile instanceof FluidDrawerTile && world.getTotalWorldTime() % (Configurations.GENERAL.upgradeTick * 3L) == 0) {
            handleCollectorFluids((FluidDrawerTile) tile, collectPos);
        }
    }

    private void handleCollectorFluids(FluidDrawerTile tile, BlockPos collectPos) {
        World world = tile.getWorld();
        Block block = world.getBlockState(collectPos).getBlock();

        IFluidHandler sourceFluid;
        if (block instanceof IFluidBlock) {
            sourceFluid = new FluidBlockWrapper((IFluidBlock) block, world, collectPos);
        } else if (block instanceof BlockLiquid) {
            sourceFluid = new BlockLiquidWrapper((BlockLiquid) block, world, collectPos);
        } else {
            return;
        }

        int requested = Math.max(Configurations.GENERAL.upgradeCollectorFluid, Fluid.BUCKET_VOLUME);
        FluidStack drained = sourceFluid.drain(requested, false);
        if (drained == null || drained.amount <= 0) {
            return;
        }

        int accepted = tile.getFluidHandler().fill(drained.copy(), false);
        if (accepted == drained.amount) {
            tile.getFluidHandler().fill(drained.copy(), true);
            sourceFluid.drain(drained.amount, true);
        }
    }

    public enum UtilityAction {
        NONE,
        PULLING,
        PUSHING,
        COLLECTOR,
        VOID,
        REDSTONE,
        WIRELESS_PULLING,
        WIRELESS_PUSHING
    }
}
