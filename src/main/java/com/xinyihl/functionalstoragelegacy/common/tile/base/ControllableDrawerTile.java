package com.xinyihl.functionalstoragelegacy.common.tile.base;

import com.xinyihl.functionalstoragelegacy.FunctionalStorageLegacy;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigFluidHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.IStorageHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.StorageSubscription;
import com.xinyihl.functionalstoragelegacy.api.upgrade.IStorageUpgrade;
import com.xinyihl.functionalstoragelegacy.api.upgrade.StorageFeature;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeAttribute;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeState;
import com.xinyihl.functionalstoragelegacy.client.render.DrawerOptions;
import com.xinyihl.functionalstoragelegacy.common.item.ConfigurationToolItem;
import com.xinyihl.functionalstoragelegacy.common.item.upgrade.DrawerUpgradeBehavior;
import com.xinyihl.functionalstoragelegacy.common.integration.ae2.AE2Compat;
import com.xinyihl.functionalstoragelegacy.misc.RegistrationHandler;
import com.xinyihl.functionalstoragelegacy.util.ItemUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Abstract base TileEntity for all controllable drawer blocks.
 * Manages storage upgrades, utility upgrades, drawer options, and controller binding.
 */
public abstract class ControllableDrawerTile extends TileEntity implements ITickable {

    protected BlockPos controllerPos;
    protected ItemStackHandler storageUpgrades;
    protected ItemStackHandler utilityUpgrades;
    protected DrawerOptions drawerOptions;
    protected boolean isLocked = false;
    protected boolean needsUpgradeCache = true;
    private UpgradeState cachedUpgradeState = UpgradeState.empty();

    /** The single subscription owned by this tile for its active storage. */
    private StorageSubscription storageSubscription = StorageSubscription.CLOSED;
    private IStorageHandler<?, ?> subscribedStorage;
    private Runnable storageConfigurationReset = () -> { };
    private boolean pendingUpdatePacket;
    private boolean storageReadInProgress;
    private boolean storageReadHadSubscription;

    public ControllableDrawerTile() {
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
                onUpgradeContentsChanged();
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
                onUpgradeContentsChanged();
            }
        };
    }

    @Override
    public void update() {
        flushPendingUpdatePacket();
        if (world == null || world.isRemote) return;

        // Process utility upgrades
        for (int i = 0; i < utilityUpgrades.getSlots(); i++) {
            ItemStack stack = utilityUpgrades.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof DrawerUpgradeBehavior) {
                ((DrawerUpgradeBehavior) stack.getItem()).onInstalledTick(this, stack, i);
            }
        }
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound compound) {
        compound = super.writeToNBT(compound);
        compound.setTag("StorageUpgrades", storageUpgrades.serializeNBT());
        compound.setTag("UtilityUpgrades", utilityUpgrades.serializeNBT());
        compound.setTag("DrawerOptions", drawerOptions.serializeNBT());
        compound.setBoolean("Locked", isLocked);
        if (controllerPos != null) {
            compound.setLong("ControllerPos", controllerPos.toLong());
        }
        return compound;
    }

    @Override
    public void readFromNBT(@Nonnull NBTTagCompound compound) {
        beginStorageRead();
        super.readFromNBT(compound);
        if (compound.hasKey("StorageUpgrades")) {
            storageUpgrades.deserializeNBT(compound.getCompoundTag("StorageUpgrades"));
        }
        if (compound.hasKey("UtilityUpgrades")) {
            utilityUpgrades.deserializeNBT(compound.getCompoundTag("UtilityUpgrades"));
        }
        if (compound.hasKey("DrawerOptions")) {
            drawerOptions.deserializeNBT(compound.getCompoundTag("DrawerOptions"));
        }
        isLocked = compound.getBoolean("Locked");
        if (compound.hasKey("ControllerPos")) {
            controllerPos = BlockPos.fromLong(compound.getLong("ControllerPos"));
        }
        needsUpgradeCache = true;
    }

    /**
     * Save tile data for storing in item NBT.
     */
    public NBTTagCompound saveTileToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setTag("StorageUpgrades", storageUpgrades.serializeNBT());
        nbt.setTag("UtilityUpgrades", utilityUpgrades.serializeNBT());
        nbt.setTag("DrawerOptions", drawerOptions.serializeNBT());
        nbt.setBoolean("Locked", isLocked);
        writeCustomData(nbt);
        return nbt;
    }

    /**
     * Load tile data from item NBT.
     */
    public void loadTileFromNBT(NBTTagCompound nbt) {
        beginStorageRead();
        if (nbt.hasKey("StorageUpgrades")) {
            storageUpgrades.deserializeNBT(nbt.getCompoundTag("StorageUpgrades"));
        }
        if (nbt.hasKey("UtilityUpgrades")) {
            utilityUpgrades.deserializeNBT(nbt.getCompoundTag("UtilityUpgrades"));
        }
        if (nbt.hasKey("DrawerOptions")) {
            drawerOptions.deserializeNBT(nbt.getCompoundTag("DrawerOptions"));
        }
        isLocked = nbt.getBoolean("Locked");
        readCustomData(nbt);
        needsUpgradeCache = true;
        markDirty();
    }

    /**
     * Override in subclasses to save additional data (e.g. inventory contents).
     */
    protected abstract void writeCustomData(NBTTagCompound nbt);

    /**
     * Override in subclasses to load additional data.
     */
    protected abstract void readCustomData(NBTTagCompound nbt);

    /**
     * Handle right-click interaction on a specific slot.
     */
    public boolean onSlotActivated(EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ, int slot) {
        ItemStack heldStack = player.getHeldItem(hand);

        // Skip if using configuration or linking tool
        if (heldStack.getItem() instanceof ConfigurationToolItem
                || heldStack.getItem() == RegistrationHandler.LINKING_TOOL) {
            return false;
        }

        // Try to insert storage upgrade
        if (ItemUtil.isStorageUpgradeItem(heldStack)) {
            for (int i = 0; i < storageUpgrades.getSlots(); i++) {
                if (storageUpgrades.getStackInSlot(i).isEmpty() && canInsertStorageUpgrade(i, heldStack)) {
                    ItemStack toInsert = heldStack.splitStack(1);
                    storageUpgrades.setStackInSlot(i, toInsert);
                    return true;
                }
            }
            // Try upgrading existing
            if (heldStack.getItem() instanceof DrawerUpgradeBehavior) {
                DrawerUpgradeBehavior newUpgrade = (DrawerUpgradeBehavior) heldStack.getItem();
                int newPriority = newUpgrade.getReplacementPriority(heldStack);
                for (int i = 0; i < storageUpgrades.getSlots(); i++) {
                    ItemStack existing = storageUpgrades.getStackInSlot(i);
                    if (newPriority != Integer.MIN_VALUE
                            && existing.getItem() instanceof DrawerUpgradeBehavior) {
                        DrawerUpgradeBehavior existingUpgrade = (DrawerUpgradeBehavior) existing.getItem();
                        if (newPriority > existingUpgrade.getReplacementPriority(existing)
                                && canReplaceStorageUpgrade(i, heldStack)) {
                            // Give back old upgrade
                            if (!player.inventory.addItemStackToInventory(existing.copy())) {
                                player.dropItem(existing.copy(), false);
                            }
                            ItemStack toInsert = heldStack.splitStack(1);
                            storageUpgrades.setStackInSlot(i, toInsert);
                            return true;
                        }
                    }
                }
            }
        }

        // Try to insert utility upgrade
        if (ItemUtil.isUtilityUpgradeItem(heldStack)) {
            for (int i = 0; i < utilityUpgrades.getSlots(); i++) {
                if (utilityUpgrades.getStackInSlot(i).isEmpty() && canInsertUtilityUpgrade(i, heldStack)) {
                    ItemStack toInsert = heldStack.splitStack(1);
                    utilityUpgrades.setStackInSlot(i, toInsert);
                    return true;
                }
            }
        }

        // Open GUI if no slot hit
        if (slot == -1) {
            player.openGui(FunctionalStorageLegacy.INSTANCE, 0, world, pos.getX(), pos.getY(), pos.getZ());
            return true;
        }

        return false;
    }

    /**
     * Handle left-click extraction on a specific slot.
     */
    public void onClicked(EntityPlayer player, int slot) {
        // Override in subclasses
    }

    /**
     * Recalculate storage multiplier and special states from upgrades.
     */
    public void recalculateUpgrades() {
        cachedUpgradeState = calculateUpgradeState(null, ItemStack.EMPTY);
        needsUpgradeCache = false;
    }

    public boolean canInsertStorageUpgrade(int slot, @Nonnull ItemStack stack) {
        if (!ItemUtil.isStorageUpgradeItem(stack) || slot < 0 || slot >= storageUpgrades.getSlots()) {
            return false;
        }
        return !hasIncompatibleUpgrade(stack, slot);
    }

    public boolean canInsertUtilityUpgrade(int slot, @Nonnull ItemStack stack) {
        if (!ItemUtil.isUtilityUpgradeItem(stack) || slot < 0 || slot >= utilityUpgrades.getSlots()) {
            return false;
        }
        DrawerUpgradeBehavior utilityUpgrade = (DrawerUpgradeBehavior) stack.getItem();
        return utilityUpgrade.canInstallInto(this, stack) && !hasIncompatibleUpgrade(stack, null);
    }

    public boolean canRemoveStorageUpgrade(int slot) {
        if (slot < 0 || slot >= storageUpgrades.getSlots()) {
            return false;
        }
        ItemStack existing = storageUpgrades.getStackInSlot(slot);
        if (existing.isEmpty()) {
            return true;
        }
        return canApplyUpgradeState(calculateUpgradeState(slot, ItemStack.EMPTY));
    }

    public boolean canReplaceStorageUpgrade(int slot, @Nonnull ItemStack replacement) {
        if (!ItemUtil.isStorageUpgradeItem(replacement) || slot < 0 || slot >= storageUpgrades.getSlots()) {
            return false;
        }
        if (!storageUpgrades.getStackInSlot(slot).isEmpty() && hasIncompatibleUpgrade(replacement, slot)) {
            return false;
        }
        return canApplyUpgradeState(calculateUpgradeState(slot, replacement));
    }

    protected boolean canApplyUpgradeState(UpgradeState state) {
        return true;
    }

    protected UpgradeState calculateUpgradeState(@Nullable Integer replacedStorageSlot, @Nonnull ItemStack replacementStack) {
        UpgradeState.Builder builder = UpgradeState.builder();

        for (int i = 0; i < storageUpgrades.getSlots(); i++) {
            ItemStack stack = storageUpgrades.getStackInSlot(i);
            if (replacedStorageSlot != null && replacedStorageSlot == i) {
                stack = replacementStack;
            }
            applyUpgrade(builder, stack);
        }

        for (int i = 0; i < utilityUpgrades.getSlots(); i++) {
            applyUpgrade(builder, utilityUpgrades.getStackInSlot(i));
        }

        return builder.build();
    }

    private void applyUpgrade(UpgradeState.Builder builder, @Nonnull ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof IStorageUpgrade) {
            ((IStorageUpgrade) stack.getItem()).applyUpgrade(stack, builder);
        }
    }

    protected boolean hasIncompatibleUpgrade(@Nonnull ItemStack candidate, @Nullable Integer ignoredStorageSlot) {
        for (int i = 0; i < storageUpgrades.getSlots(); i++) {
            if (ignoredStorageSlot != null && ignoredStorageSlot == i) {
                continue;
            }
            ItemStack existing = storageUpgrades.getStackInSlot(i);
            if (isConflictingUpgrade(candidate, existing)) {
                return true;
            }
        }
        for (int i = 0; i < utilityUpgrades.getSlots(); i++) {
            ItemStack existing = utilityUpgrades.getStackInSlot(i);
            if (isConflictingUpgrade(candidate, existing)) {
                return true;
            }
        }
        return false;
    }

    protected boolean isConflictingUpgrade(ItemStack candidate, ItemStack existing) {
        if (candidate.isEmpty() || existing.isEmpty()
                || !(candidate.getItem() instanceof IStorageUpgrade)
                || !(existing.getItem() instanceof IStorageUpgrade)) {
            return false;
        }
        IStorageUpgrade candidateUpgrade = (IStorageUpgrade) candidate.getItem();
        IStorageUpgrade existingUpgrade = (IStorageUpgrade) existing.getItem();
        return candidateUpgrade.conflictsWith(candidate, existing)
                || existingUpgrade.conflictsWith(existing, candidate);
    }

    public double calculateModifier(UpgradeAttribute attribute, double defaultBase) {
        if (needsUpgradeCache) recalculateUpgrades();
        return cachedUpgradeState.calculate(attribute, defaultBase);
    }

    public double getStorageMultiplier(double defaultBase) {
        return calculateModifier(UpgradeAttribute.ITEM_CAPACITY, defaultBase);
    }

    public double getFluidMultiplier(double defaultBase) {
        return calculateModifier(UpgradeAttribute.FLUID_CAPACITY, defaultBase);
    }

    public double getRangeBonus() {
        return calculateModifier(UpgradeAttribute.CONTROLLER_RANGE, 0);
    }

    public boolean hasMaxStorageUpgrade() {
        if (needsUpgradeCache) recalculateUpgrades();
        return cachedUpgradeState.hasFeature(StorageFeature.MAX_CAPACITY);
    }

    public boolean hasOreDictionaryUpgrade() {
        if (needsUpgradeCache) recalculateUpgrades();
        return cachedUpgradeState.hasFeature(StorageFeature.EQUIVALENT_ITEMS);
    }

    public boolean isCreative() {
        if (needsUpgradeCache) recalculateUpgrades();
        return cachedUpgradeState.hasFeature(StorageFeature.CREATIVE);
    }

    public boolean voidsOverflow() {
        if (needsUpgradeCache) recalculateUpgrades();
        return cachedUpgradeState.hasFeature(StorageFeature.VOID_OVERFLOW);
    }

    public boolean isLocked() {
        if (needsUpgradeCache) recalculateUpgrades();
        return isLocked;
    }

    public void setLocked(boolean locked) {
        if (this.isLocked == locked) return;
        this.isLocked = locked;
        onLockStateChanged(locked);
        this.needsUpgradeCache = true;
        markDirty();
        requestUpdatePacket();
    }

    /**
     * Called after the runtime lock flag changes, before persistence and update
     * notifications. Storage-specific subclasses use this to retain or clear
     * filters without duplicating the public lock lifecycle.
     */
    protected void onLockStateChanged(boolean locked) {
    }

    @Override
    public boolean shouldRefresh(@Nonnull World world, @Nonnull BlockPos pos, IBlockState oldState, IBlockState newSate) {
        return oldState.getBlock() != newSate.getBlock();
    }

    public void toggleLocking() {
        setLocked(!isLocked());
    }

    public void toggleOption(ConfigurationToolItem.ConfigurationAction action) {
        if (action.getMax() == 1) {
            drawerOptions.setActive(action, !drawerOptions.isActive(action));
        } else {
            drawerOptions.setAdvancedValue(action, (drawerOptions.getAdvancedValue(action) + 1) % (action.getMax() + 1));
        }
        markDirty();
        requestUpdatePacket();
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public void setControllerPos(BlockPos controllerPos) {
        this.controllerPos = controllerPos;
        markDirty();
    }

    public void clearControllerPos() {
        this.controllerPos = null;
        markDirty();
    }

    public ItemStackHandler getStorageUpgrades() {
        return storageUpgrades;
    }

    public ItemStackHandler getUtilityUpgrades() {
        return utilityUpgrades;
    }

    public DrawerOptions getDrawerOptions() {
        return drawerOptions;
    }

    public int getStorageUpgradesAmount() {
        return 4;
    }

    public int getUtilityUpgradesAmount() {
        return 3;
    }

    public boolean isEverythingEmpty() {
        for (int i = 0; i < storageUpgrades.getSlots(); i++) {
            if (!storageUpgrades.getStackInSlot(i).isEmpty()) return false;
        }
        for (int i = 0; i < utilityUpgrades.getSlots(); i++) {
            if (!utilityUpgrades.getStackInSlot(i).isEmpty()) return false;
        }
        return true;
    }

    public int getRedstoneSignal(EnumFacing side) {
        for (int i = 0; i < utilityUpgrades.getSlots(); i++) {
            ItemStack stack = utilityUpgrades.getStackInSlot(i);
            if (stack.getItem() instanceof DrawerUpgradeBehavior
                    && ((DrawerUpgradeBehavior) stack.getItem()).providesRedstoneSignal(stack)) {
                return calculateRedstoneSignal();
            }
        }
        return 0;
    }

    protected int calculateRedstoneSignal() {
        return 0;
    }

    public void sendUpdatePacket() {
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
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

    // AE2 integration - lazy-initialized accessor (stored as Object to avoid class loading when AE2 absent)
    private Object ae2Accessor;

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @javax.annotation.Nullable EnumFacing facing) {
        if (AE2Compat.isLoaded() && AE2Compat.isStorageAccessorCapability(capability)) return true;
        return super.hasCapability(capability, facing);
    }

    @javax.annotation.Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(@Nonnull Capability<T> capability, @javax.annotation.Nullable EnumFacing facing) {
        if (AE2Compat.isLoaded() && AE2Compat.isStorageAccessorCapability(capability)) {
            if (ae2Accessor == null) {
                ae2Accessor = AE2Compat.createAccessor(this);
            }
            return (T) ae2Accessor;
        }
        return super.getCapability(capability, facing);
    }

    public void invalidateAE2Accessor() {
        Object current = ae2Accessor;
        if (current == null) {
            return;
        }
        try {
            AE2Compat.closeAccessor(current);
        } finally {
            ae2Accessor = null;
        }
    }

    /**
     * Get the item handler for this drawer (for capability).
     */
    @Nullable
    public abstract IBigItemHandler getItemHandler();

    /**
     * Returns the internal large fluid handler without degrading it to Forge's
     * int-only interface. Non-fluid drawers return {@code null}.
     */
    @Nullable
    public IBigFluidHandler getFluidHandler() {
        return null;
    }

    /**
     * Binds this tile to one storage event source. Rebinding always closes the
     * previous registration first; callers may provide the handler's explicit
     * RESET hook for upgrade/configuration changes.
     */
    protected final void bindStorageHandler(
            @Nullable IStorageHandler<?, ?> handler,
            @Nullable Runnable configurationReset) {
        if (subscribedStorage != null && subscribedStorage != handler) {
            /* A deserialized handler is a new physical source.  Any AE2
             * monitor still wrapping the old source must be released before
             * the new binding becomes visible. */
            invalidateAE2Accessor();
        }
        closeStorageSubscription();
        subscribedStorage = handler;
        storageConfigurationReset = configurationReset == null ? () -> { } : configurationReset;
        if (handler != null) {
            storageSubscription = handler.subscribe(change -> onStorageChange());
        } else {
            storageSubscription = StorageSubscription.CLOSED;
        }
    }

    /** Binds an event source without a configuration callback. */
    protected final void bindStorageHandler(@Nullable IStorageHandler<?, ?> handler) {
        bindStorageHandler(handler, null);
    }

    /** Closes the tile-owned storage subscription; safe to call repeatedly. */
    protected final void closeStorageSubscription() {
        StorageSubscription current = storageSubscription;
        storageSubscription = StorageSubscription.CLOSED;
        if (current != null) {
            current.close();
        }
    }

    /**
     * Starts a potentially online storage replacement/read. The old listener
     * is detached before any NBT mutation and the online flag is retained until
     * {@link #finishStorageRead(IStorageHandler, Runnable)}.
     */
    protected final void beginStorageRead() {
        if (storageReadInProgress) {
            return;
        }
        storageReadInProgress = true;
        /* Reads may replace the concrete handler in readCustomData. */
        invalidateAE2Accessor();
        storageReadHadSubscription = world != null && !world.isRemote
                && storageSubscription != null && !storageSubscription.isClosed();
        closeStorageSubscription();
    }

    /**
     * Completes a read/rebind after the replacement handler has been fully
     * deserialized. Online replacement emits one explicit RESET only.
     */
    protected final void finishStorageRead(
            @Nonnull IStorageHandler<?, ?> handler,
            @Nullable Runnable configurationReset) {
        Objects.requireNonNull(handler, "handler");
        boolean notifyReset = storageReadInProgress && storageReadHadSubscription;
        storageReadInProgress = false;
        storageReadHadSubscription = false;
        bindStorageHandler(handler, configurationReset);
        if (notifyReset && configurationReset != null) {
            configurationReset.run();
        }
    }

    /** Re-attaches a closed listener after chunk/tile reload. */
    protected final void rebuildStorageSubscription() {
        if (subscribedStorage != null && (storageSubscription == null
                || storageSubscription.isClosed())) {
            storageSubscription = subscribedStorage.subscribe(change -> onStorageChange());
        }
    }

    /** Emits the handler-owned RESET for an upgrade/configuration transition. */
    protected final void notifyStorageConfigurationChanged() {
        if (!storageReadInProgress) {
            storageConfigurationReset.run();
        }
    }

    /** Marks the tile dirty and schedules at most one packet for a later tick. */
    protected final void onStorageChange() {
        markDirty();
        requestUpdatePacket();
    }

    protected final void requestUpdatePacket() {
        pendingUpdatePacket = true;
    }

    private void flushPendingUpdatePacket() {
        if (!pendingUpdatePacket) {
            return;
        }
        pendingUpdatePacket = false;
        if (world == null || !world.isRemote) {
            sendUpdatePacket();
        }
    }

    private void onUpgradeContentsChanged() {
        needsUpgradeCache = true;
        markDirty();
        notifyStorageConfigurationChanged();
        requestUpdatePacket();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        rebuildStorageSubscription();
    }

    @Override
    public void invalidate() {
        closeStorageSubscription();
        invalidateAE2Accessor();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        closeStorageSubscription();
        invalidateAE2Accessor();
        super.onChunkUnload();
    }

}
