package com.xinyihl.functionalstoragelegacy.common.tile.controller;

import com.xinyihl.functionalstoragelegacy.FunctionalStorageLegacy;
import com.xinyihl.functionalstoragelegacy.api.storage.*;
import com.xinyihl.functionalstoragelegacy.client.render.DrawerOptions;
import com.xinyihl.functionalstoragelegacy.common.block.base.DrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.inventory.controller.ControllerFluidHandler;
import com.xinyihl.functionalstoragelegacy.common.inventory.controller.ControllerItemHandler;
import com.xinyihl.functionalstoragelegacy.common.item.ConfigurationToolItem;
import com.xinyihl.functionalstoragelegacy.common.item.LinkingToolItem;
import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import com.xinyihl.functionalstoragelegacy.misc.Configurations;
import com.xinyihl.functionalstoragelegacy.misc.RegistrationHandler;
import com.xinyihl.functionalstoragelegacy.util.ConnectedDrawers;
import com.xinyihl.functionalstoragelegacy.util.ItemUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;

/**
 * TileEntity for the storage controller.
 * Aggregates connected drawers into a unified item/fluid handler.
 * Uses BFS (ConnectedDrawers) for discovery and ControllerInventoryHandler/ControllerFluidHandler for access.
 */
public class DrawerControllerTile extends ControllableDrawerTile {

    private static final HashMap<UUID, Long> INTERACTION_LOGGER = new HashMap<>();

    private final ConnectedDrawers connectedDrawers;
    private final ControllerItemHandler inventoryHandler;
    private final ControllerFluidHandler fluidHandler;
    protected boolean needRebuild = false;

    public DrawerControllerTile() {
        this.drawerOptions = new DrawerOptions();
        this.storageUpgrades = new ItemStackHandler(getStorageUpgradesAmount()) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return canInsertStorageUpgrade(slot, stack);
            }

            @Override
            protected int getStackLimit(int slot, @Nonnull ItemStack stack) {
                return 1;
            }

            @Nonnull
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (!canRemoveStorageUpgrade(slot)) {
                    return ItemStack.EMPTY;
                }
                return super.extractItem(slot, amount, simulate);
            }

            @Override
            protected void onContentsChanged(int slot) {
                needsUpgradeCache = true;
                needRebuild = true;
                markDirty();
            }
        };
        this.utilityUpgrades = new ItemStackHandler(getUtilityUpgradesAmount()) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return canInsertUtilityUpgrade(slot, stack);
            }

            @Override
            protected int getStackLimit(int slot, @Nonnull ItemStack stack) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int slot) {
                needsUpgradeCache = true;
                markDirty();
            }
        };
        this.connectedDrawers = new ConnectedDrawers(null, this);
        this.inventoryHandler = new ControllerItemHandler();
        this.fluidHandler = new ControllerFluidHandler();
    }

    private static ItemStack remainderOf(ItemStack original, long remaining) {
        if (remaining <= 0L) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = original.copy();
        remainder.setCount((int) Math.min(remaining, original.getCount()));
        return remainder;
    }

    private void refreshHandlers() {
        inventoryHandler.setHandlers(connectedDrawers.getItemHandlers());
        fluidHandler.setHandlers(connectedDrawers.getFluidHandlers());
    }

    /**
     * Refreshes flattened mappings after a stable child facade changes target.
     */
    public void refreshHandlerMappings() {
        refreshHandlers();
    }

    @Override
    public void update() {
        super.update();
        if (world != null && !world.isRemote) {
            if (world.getTotalWorldTime() % 10 == 0) {
                rebuild();
                needRebuild = false;
            }
        }
    }

    private void rebuild() {
        AxisAlignedBB area = new AxisAlignedBB(pos).grow(getControllerRange());
        boolean topologyChanged = false;
        Iterator<Long> iterator = connectedDrawers.getConnectedDrawers().iterator();
        while (iterator.hasNext()) {
            BlockPos drawerPos = BlockPos.fromLong(iterator.next());
            boolean inRange = area.contains(new Vec3d(drawerPos.getX() + 0.5, drawerPos.getY() + 0.5, drawerPos.getZ() + 0.5));
            if (!inRange) {
                iterator.remove();
                topologyChanged = true;
                continue;
            }
            if (!world.isBlockLoaded(drawerPos)) {
                continue;
            }
            TileEntity tile = world.getTileEntity(drawerPos);
            if (!(tile instanceof ControllableDrawerTile)) {
                iterator.remove();
                topologyChanged = true;
            }
        }
        topologyChanged |= connectedDrawers.rebuild();
        refreshHandlers();
        if (topologyChanged) {
            markDirty();
            sendUpdatePacket();
        }
    }

    @Override
    public void setWorld(@Nonnull World worldIn) {
        super.setWorld(worldIn);
        connectedDrawers.setLevel(worldIn);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        rebuild();
    }

    @Override
    public void invalidate() {
        inventoryHandler.closeSubscriptions();
        fluidHandler.closeSubscriptions();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        inventoryHandler.closeSubscriptions();
        fluidHandler.closeSubscriptions();
        super.onChunkUnload();
    }

    @Override
    public boolean onSlotActivated(EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ, int slot) {
        ItemStack heldStack = player.getHeldItem(hand);

        if (heldStack.getItem() instanceof ConfigurationToolItem || heldStack.getItem() == RegistrationHandler.LINKING_TOOL) {
            return false;
        }

        if (player.isSneaking()) {
            player.openGui(FunctionalStorageLegacy.INSTANCE, 0, world, pos.getX(), pos.getY(), pos.getZ());
            return true;
        }

        // Upgrades can be installed by clicking any part or side of the controller.
        if (ItemUtil.isStorageUpgradeItem(heldStack) || ItemUtil.isUtilityUpgradeItem(heldStack)) {
            return super.onSlotActivated(player, hand, facing, hitX, hitY, hitZ, slot);
        }

        // Player item insertion is only available from the controller's front face.
        if (facing != DrawerBlock.getFrontFacing(world.getBlockState(pos))) {
            return true;
        }

        if (!world.isRemote) {
            if (!heldStack.isEmpty()) {
                BigItemStack request = new BigItemStack(heldStack, heldStack.getCount());
                TransferResult<BigItemStack, ItemStorageKey> simulated = inventoryHandler.insertRouted(request, StorageAction.SIMULATE);
                if (simulated.getProcessedAmount() > 0L) {
                    TransferResult<BigItemStack, ItemStorageKey> inserted = inventoryHandler.insertRouted(request, StorageAction.EXECUTE);
                    player.setHeldItem(hand, remainderOf(heldStack, inserted.getRemainingAmount()));
                    INTERACTION_LOGGER.put(player.getUniqueID(), System.currentTimeMillis());
                    return true;
                }
            }

            boolean doubleClick = System.currentTimeMillis() - INTERACTION_LOGGER.getOrDefault(player.getUniqueID(), System.currentTimeMillis()) < 300L;
            if (doubleClick) {
                for (ItemStack inventoryStack : player.inventory.mainInventory) {
                    if (inventoryStack.isEmpty()) {
                        continue;
                    }
                    BigItemStack request = new BigItemStack(inventoryStack, inventoryStack.getCount());
                    TransferResult<BigItemStack, ItemStorageKey> inserted = inventoryHandler.insertMatchingRouted(request, StorageAction.EXECUTE);
                    inventoryStack.setCount((int) inserted.getRemainingAmount());
                }
            }

            INTERACTION_LOGGER.put(player.getUniqueID(), System.currentTimeMillis());
        }

        return false;
    }

    @Override
    public IBigItemHandler getItemHandler() {
        return inventoryHandler;
    }

    @Override
    public IBigFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(inventoryHandler);
        }
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fluidHandler);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public void toggleLocking() {
        super.toggleLocking();
        if (world != null && !world.isRemote) {
            for (Long drawerPos : new ArrayList<>(connectedDrawers.getConnectedDrawers())) {
                TileEntity te = world.getTileEntity(BlockPos.fromLong(drawerPos));
                if (te instanceof DrawerControllerTile) continue;
                if (te instanceof ControllableDrawerTile) {
                    ((ControllableDrawerTile) te).setLocked(this.isLocked());
                }
            }
        }
    }

    @Override
    public void toggleOption(ConfigurationToolItem.ConfigurationAction action) {
        super.toggleOption(action);
        if (world != null && !world.isRemote) {
            for (Long drawerPos : new ArrayList<>(connectedDrawers.getConnectedDrawers())) {
                TileEntity te = world.getTileEntity(BlockPos.fromLong(drawerPos));
                if (te instanceof DrawerControllerTile) continue;
                if (te instanceof ControllableDrawerTile) {
                    ControllableDrawerTile cdt = (ControllableDrawerTile) te;
                    if (action.getMax() == 1) {
                        cdt.getDrawerOptions().setActive(action, this.getDrawerOptions().isActive(action));
                    } else {
                        cdt.getDrawerOptions().setAdvancedValue(action, this.getDrawerOptions().getAdvancedValue(action));
                    }
                    cdt.markDirty();
                    cdt.sendUpdatePacket();
                }
            }
        }
    }

    /**
     * Get the effective controller search range.
     * Base range from config multiplied by range fraction from storage upgrades.
     */
    public double getControllerRange() {
        return Configurations.GENERAL.drawerControllerLinkingRange + getRangeBonus();
    }

    public boolean addConnectedDrawers(LinkingToolItem.ActionMode action, BlockPos... positions) {
        double range = getControllerRange();
        boolean didWork = false;
        AxisAlignedBB area = new AxisAlignedBB(pos).grow(range);

        for (BlockPos position : positions) {
            // Skip controller blocks (don't link controllers to themselves)
            if (world.getBlockState(position).getBlock() == RegistrationHandler.DRAWER_CONTROLLER_BLOCK) {
                continue;
            }

            TileEntity te = world.getTileEntity(position);
            if (te instanceof ControllerExtensionTile) {
                connectedDrawers.removeLinkedExtension(position);

                if (action == LinkingToolItem.ActionMode.ADD) {
                    if (area.contains(new net.minecraft.util.math.Vec3d(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5))) {
                        ((ControllerExtensionTile) te).setControllerPos(this.pos);
                        connectedDrawers.addLinkedExtension(position);
                        didWork = true;
                    }
                } else {
                    ((ControllerExtensionTile) te).clearControllerPos();
                    didWork = true;
                }
                continue;
            }

            if (te instanceof ControllableDrawerTile) {
                if (action == LinkingToolItem.ActionMode.ADD) {
                    if (area.contains(new net.minecraft.util.math.Vec3d(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5))) {
                        ((ControllableDrawerTile) te).setControllerPos(this.pos);
                        long posLong = position.toLong();
                        if (!connectedDrawers.getConnectedDrawers().contains(posLong)) {
                            connectedDrawers.getConnectedDrawers().add(posLong);
                            didWork = true;
                        }
                    }
                } else if (action == LinkingToolItem.ActionMode.REMOVE) {
                    connectedDrawers.getConnectedDrawers().removeIf(l -> l == position.toLong());
                    ((ControllableDrawerTile) te).clearControllerPos();
                    didWork = true;
                }
            }
        }

        connectedDrawers.rebuild();
        refreshHandlers();
        markDirty();
        sendUpdatePacket();
        return didWork;
    }

    @Override
    protected void writeCustomData(NBTTagCompound nbt) {
        nbt.setTag("ConnectedDrawers", connectedDrawers.serializeNBT());
    }

    @Override
    protected void readCustomData(NBTTagCompound nbt) {
        if (nbt.hasKey("ConnectedDrawers")) {
            connectedDrawers.deserializeNBT(nbt.getCompoundTag("ConnectedDrawers"));
        } else {
            connectedDrawers.deserializeNBT(new NBTTagCompound());
        }
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound compound) {
        compound = super.writeToNBT(compound);
        compound.setTag("ConnectedDrawers", connectedDrawers.serializeNBT());
        return compound;
    }

    @Override
    public void readFromNBT(@Nonnull NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("ConnectedDrawers")) {
            connectedDrawers.deserializeNBT(compound.getCompoundTag("ConnectedDrawers"));
        } else {
            connectedDrawers.deserializeNBT(new NBTTagCompound());
        }
    }

    @Override
    public int getUtilityUpgradesAmount() {
        return 0;
    }

    public ConnectedDrawers getConnectedDrawers() {
        return connectedDrawers;
    }

    /**
     * Remove a drawer from the connected list (called when drawer is broken).
     */
    public void removeConnectedDrawer(BlockPos drawerPos) {
        connectedDrawers.getConnectedDrawers().removeIf(l -> l == drawerPos.toLong());
        connectedDrawers.removeLinkedExtension(drawerPos);
        connectedDrawers.rebuild();
        refreshHandlers();
        markDirty();
        sendUpdatePacket();
    }

    @Nonnull
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return TileEntity.INFINITE_EXTENT_AABB;
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return Float.POSITIVE_INFINITY;
    }
}
