package com.xinyihl.functionalstoragelegacy.common.tile;

import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.ItemStorageKey;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageChange;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageSubscription;
import com.xinyihl.functionalstoragelegacy.common.inventory.ArmoryCabinetInventoryHandler;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * TileEntity for the armory cabinet.
 * Stores unstackable items (armor, weapons, tools, discs, etc.).
 * Does not extend ControllableDrawerTile since it has no upgrades/controller support.
 */
public class ArmoryCabinetTile extends TileEntity implements ITickable {

    private final ArmoryCabinetInventoryHandler handler;
    private StorageSubscription subscription = StorageSubscription.CLOSED;
    private boolean pendingUpdatePacket;
    private boolean readInProgress;
    private boolean readHadSubscription;

    public ArmoryCabinetTile() {
        this.handler = new ArmoryCabinetInventoryHandler() {
        };
        subscribeHandler();
    }

    public IBigItemHandler getStorage() {
        return handler;
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound compound) {
        compound = super.writeToNBT(compound);
        writeStorage(compound);
        return compound;
    }

    @Override
    public void readFromNBT(@Nonnull NBTTagCompound compound) {
        beginRead();
        super.readFromNBT(compound);
        handler.deserializeNBT(compound);
        finishRead();
    }

    public boolean isEverythingEmpty() {
        for (int i = 0; i < handler.getStorageCount(); i++) {
            if (handler.getSnapshot(i).hasTemplate()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Save tile data to NBT for item storage.
     */
    public NBTTagCompound saveTileToNBT() {
        return handler.serializeNBT();
    }

    /**
     * Load tile data from item NBT.
     */
    public void loadTileFromNBT(NBTTagCompound nbt) {
        beginRead();
        handler.deserializeNBT(nbt);
        finishRead();
        markDirty();
    }

    private void writeStorage(NBTTagCompound nbt) {
        nbt.setTag("StorageV2", handler.serializeNBT().getCompoundTag("StorageV2"));
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(handler);
        }
        return super.getCapability(capability, facing);
    }

    @Nonnull
    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 1, getUpdateTag());
    }

    @Override
    public void onDataPacket(@Nonnull NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public void update() {
        if (!pendingUpdatePacket) {
            return;
        }
        pendingUpdatePacket = false;
        if (world == null || !world.isRemote) {
            sendUpdatePacket();
        }
    }

    public void sendUpdatePacket() {
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (subscription == null || subscription.isClosed()) {
            subscribeHandler();
        }
    }

    @Override
    public void invalidate() {
        closeSubscription();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        closeSubscription();
        super.onChunkUnload();
    }

    private void subscribeHandler() {
        closeSubscription();
        subscription = handler.subscribe(change -> {
            markDirty();
            pendingUpdatePacket = true;
        });
    }

    private void closeSubscription() {
        StorageSubscription current = subscription;
        subscription = StorageSubscription.CLOSED;
        if (current != null) {
            current.close();
        }
    }

    private void beginRead() {
        if (readInProgress) {
            return;
        }
        readInProgress = true;
        readHadSubscription = world != null && !world.isRemote
                && subscription != null && !subscription.isClosed();
        closeSubscription();
    }

    private void finishRead() {
        boolean notifyReset = readInProgress && readHadSubscription;
        readInProgress = false;
        readHadSubscription = false;
        subscribeHandler();
        if (notifyReset) {
            handler.onChange(StorageChange.<BigItemStack, ItemStorageKey>reset());
        }
    }
}
