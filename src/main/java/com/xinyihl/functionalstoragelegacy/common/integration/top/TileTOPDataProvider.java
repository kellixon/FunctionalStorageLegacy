package com.xinyihl.functionalstoragelegacy.common.integration.top;

import com.xinyihl.functionalstoragelegacy.Tags;
import com.xinyihl.functionalstoragelegacy.api.storage.BigFluidStack;
import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigFluidHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.common.tile.FluidDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.controller.DrawerControllerTile;
import com.xinyihl.functionalstoragelegacy.util.NumberUtils;
import mcjty.theoneprobe.api.*;
import mcjty.theoneprobe.config.Config;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class TileTOPDataProvider implements IProbeInfoProvider {
    private static final int MAX_RENDERED_ENTRIES = 5;

    public TileTOPDataProvider() {
    }

    @Override
    public String getID() {
        return Tags.MOD_ID + ":" + this.getClass().getSimpleName();
    }

    protected String i18n(String key) {
        return "{*tooltip.functionalstoragelegacy." + key + "*}";
    }

    private long safeAmount(long value) {
        return Math.max(0L, value);
    }

    private long safeCapacity(long value) {
        return Math.max(1L, value);
    }

    private ItemStack iconStack(ItemStack source) {
        ItemStack display = source.copy();
        display.setCount(1);
        return display;
    }

    @Override
    public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World world, IBlockState blockState, IProbeHitData data) {
        TileEntity te = world.getTileEntity(data.getPos());
        if (!(te instanceof ControllableDrawerTile)) {
            return;
        }

        List<ItemEntry> items = collectItems(te);
        List<FluidEntry> fluids = collectFluids(te);

        if (items.isEmpty() && fluids.isEmpty()) {
            return;
        }

        renderExtendedItems(probeInfo, items);
        renderExtendedFluids(probeInfo, fluids);
        renderDrawerFlags(probeInfo, (ControllableDrawerTile) te);
    }

    private List<ItemEntry> collectItems(TileEntity te) {
        List<ItemEntry> items = new ArrayList<>();
        if (!(te instanceof ControllableDrawerTile)) {
            return items;
        }
        IBigItemHandler handler = ((ControllableDrawerTile) te).getItemHandler();
        if (handler == null) {
            return items;
        }
        for (int slot = 0; slot < handler.getSlotCount(); slot++) {
            BigItemStack snapshot = handler.getSlotSnapshot(slot);
            if (snapshot != null && !snapshot.isEmpty()) {
                items.add(new ItemEntry(
                        iconStack(snapshot.getTemplate()),
                        safeAmount(snapshot.getAmount()),
                        safeCapacity(handler.getSlotCapacity(slot))));
            }
        }
        return items;
    }

    private List<FluidEntry> collectFluids(TileEntity te) {
        List<FluidEntry> fluids = new ArrayList<>();

        IBigFluidHandler handler = getFluidHandler(te);
        if (handler == null) {
            return fluids;
        }
        for (int tank = 0; tank < handler.getTankCount(); tank++) {
            BigFluidStack snapshot = handler.getTankSnapshot(tank);
            if (snapshot != null && !snapshot.isEmpty()) {
                FluidStack fluid = snapshot.getTemplate();
                fluids.add(new FluidEntry(
                        fluid.getLocalizedName(),
                        safeAmount(snapshot.getAmount()),
                        safeCapacity(handler.getTankCapacity(tank))));
            }
        }

        return fluids;
    }

    private IBigFluidHandler getFluidHandler(TileEntity te) {
        if (te instanceof FluidDrawerTile) {
            return ((FluidDrawerTile) te).getFluidHandler();
        }
        if (te instanceof DrawerControllerTile) {
            return ((DrawerControllerTile) te).getFluidHandler();
        }
        return null;
    }

    private void renderExtendedItems(IProbeInfo probeInfo, List<ItemEntry> items) {
        if (items.isEmpty()) {
            return;
        }

        probeInfo.text(TextStyleClass.LABEL + i18n("stored"));
        IProbeInfo vertical = probeInfo.vertical(probeInfo.defaultLayoutStyle().borderColor(Config.chestContentsBorderColor).spacing(0));
        int rendered = Math.min(items.size(), MAX_RENDERED_ENTRIES);
        for (int i = 0; i < rendered; i++) {
            ItemEntry item = items.get(i);
            vertical.horizontal(probeInfo.defaultLayoutStyle().alignment(ElementAlignment.ALIGN_CENTER))
                    .item(item.stack)
                    .vertical(probeInfo.defaultLayoutStyle().spacing(0))
                    .itemLabel(item.stack)
                    .text(TextStyleClass.INFO + NumberUtils.formatCompact(item.amount) + " / " + NumberUtils.formatCompact(item.capacity));
        }

        if (items.size() > MAX_RENDERED_ENTRIES) {
            vertical.text(TextStyleClass.INFO + "...");
        }
    }

    private void renderExtendedFluids(IProbeInfo probeInfo, List<FluidEntry> fluids) {
        if (fluids.isEmpty()) {
            return;
        }

        probeInfo.text(TextStyleClass.LABEL + i18n("stored"));
        IProbeInfo vertical = probeInfo.vertical(probeInfo.defaultLayoutStyle().borderColor(Config.chestContentsBorderColor).spacing(0));
        int rendered = Math.min(fluids.size(), MAX_RENDERED_ENTRIES);
        for (int i = 0; i < rendered; i++) {
            FluidEntry fluid = fluids.get(i);
            vertical.text(TextStyleClass.INFO + fluid.name + " " + NumberUtils.formatCompactFluid(fluid.amount) + " / " + NumberUtils.formatCompactFluid(fluid.capacity))
                    .progress(fluid.amount, fluid.capacity,
                            probeInfo.defaultProgressStyle()
                                    .numberFormat(NumberFormat.COMPACT)
                                    .suffix(" mB")
                    );
        }

        if (fluids.size() > MAX_RENDERED_ENTRIES) {
            vertical.text(TextStyleClass.INFO + "...");
        }
    }

    private void renderDrawerFlags(IProbeInfo probeInfo, ControllableDrawerTile drawer) {
        List<String> states = new ArrayList<>();
        if (drawer.isLocked()) {
            states.add("Locked");
        }
        if (drawer.voidsOverflow()) {
            states.add("Void");
        }
        if (drawer.isCreative()) {
            states.add("Creative");
        }
        if (!states.isEmpty()) {
            probeInfo.text(TextStyleClass.INFOIMP + String.join(" | ", states));
        }
    }

    private static class ItemEntry {
        private final ItemStack stack;
        private final long amount;
        private final long capacity;

        private ItemEntry(ItemStack stack, long amount, long capacity) {
            this.stack = stack;
            this.amount = amount;
            this.capacity = capacity;
        }
    }

    private static class FluidEntry {
        private final String name;
        private final long amount;
        private final long capacity;

        private FluidEntry(String name, long amount, long capacity) {
            this.name = name;
            this.amount = amount;
            this.capacity = capacity;
        }
    }
}
