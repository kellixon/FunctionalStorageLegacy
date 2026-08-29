package com.xinyihl.functionalstoragelegacy.mixin.ae2;

import appeng.capabilities.Capabilities;
import appeng.helpers.DualityInterface;
import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets AE2 interfaces use the regular inventory path for Functional Storage drawers.
 *
 * <p>Drawers expose {@link Capabilities#STORAGE_MONITORABLE_ACCESSOR} so that they can
 * be attached to an AE2 storage bus, but they are not AE2 grid hosts themselves. The
 * crafting interface assumes every tile exposing this capability is a grid host and
 * otherwise continues before trying {@code InventoryAdaptor}, which prevents crafted
 * items from being inserted into drawers.</p>
 */
@Pseudo
@Mixin(value = DualityInterface.class, remap = false)
public abstract class DualityInterfaceMixin {

    @Redirect(
            method = "pushPattern",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/tileentity/TileEntity;getCapability(" +
                            "Lnet/minecraftforge/common/capabilities/Capability;" +
                            "Lnet/minecraft/util/EnumFacing;)Ljava/lang/Object;"
            )
    )
    private Object functionalStorageLegacy$useInventoryAdaptor(TileEntity tile, Capability<?> capability, EnumFacing side) {
        if (tile instanceof ControllableDrawerTile && capability == Capabilities.STORAGE_MONITORABLE_ACCESSOR) {
            return null;
        }
        return tile.getCapability(capability, side);
    }
}
