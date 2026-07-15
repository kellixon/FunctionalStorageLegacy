package com.xinyihl.functionalstoragelegacy.common.item;

import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.common.block.FluidDrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.block.FramedDrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.block.WoodDrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.block.compact.CompactingDrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.block.compact.SimpleCompactingDrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.inventory.capability.CompactingStackItemHandler;
import com.xinyihl.functionalstoragelegacy.common.inventory.capability.DrawerStackCapabilityProvider;
import com.xinyihl.functionalstoragelegacy.common.inventory.capability.DrawerStackItemHandler;
import com.xinyihl.functionalstoragelegacy.common.inventory.capability.FluidDrawerStackItemHandler;
import com.xinyihl.functionalstoragelegacy.common.storage.DrawerLayout;
import com.xinyihl.functionalstoragelegacy.util.NumberUtils;
import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class DrawerItemBlock extends ItemBlock {

    public DrawerItemBlock(Block block) {
        super(block);
    }

    @Override
    public int getItemStackLimit(@Nonnull ItemStack stack) {
        if (stack.hasTagCompound()
                && stack.getTagCompound().hasKey("TileData", Constants.NBT.TAG_COMPOUND)) {
            return 1;
        }
        return super.getItemStackLimit(stack);
    }

    @Nullable
    @Override
    public ICapabilityProvider initCapabilities(@Nonnull ItemStack stack, @Nullable NBTTagCompound nbt) {
        IBigItemHandler itemHandler = createItemHandler(stack);
        IFluidHandlerItem fluidHandler = createFluidItemHandler(stack);
        if (itemHandler == null && fluidHandler == null) {
            return null;
        }
        return new DrawerStackCapabilityProvider(itemHandler, fluidHandler);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(@Nonnull ItemStack stack, @Nullable World worldIn, @Nonnull java.util.List<String> tooltip, @Nonnull ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);

        if (block instanceof FramedDrawerBlock) {
            tooltip.add(TextFormatting.GRAY + new TextComponentTranslation(
                    "frameddrawer.use").getUnformattedText());
        }

        List<String> stored = collectStoredLines(stack);
        if (stored.isEmpty()) return;

        tooltip.add(TextFormatting.YELLOW + new TextComponentTranslation("drawer.tooltip.stored").getUnformattedText());
        for (String line : stored) {
            tooltip.add(TextFormatting.WHITE + line);
        }
    }

    List<String> collectStoredLines(ItemStack stack) {
        List<String> lines = new ArrayList<>();

        if (stack.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) {
            IItemHandler handler = stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            if (handler instanceof IBigItemHandler) {
                return collectStoredItemLines((IBigItemHandler) handler);
            }
        }

        if (!stack.hasTagCompound() || !stack.getTagCompound().hasKey("TileData")) {
            return lines;
        }

        NBTTagCompound tileData = stack.getTagCompound().getCompoundTag("TileData");

        if (tileData.hasKey("StorageV2", Constants.NBT.TAG_COMPOUND)) {
            NBTTagList tanks = tileData.getCompoundTag("StorageV2")
                    .getTagList("Tanks", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < tanks.tagCount(); i++) {
                NBTTagCompound entry = tanks.getCompoundTagAt(i);
                if (!entry.hasKey("Fluid", Constants.NBT.TAG_COMPOUND)) continue;
                long amount = Math.max(0L, entry.getLong("Amount"));
                if (amount == 0L) continue;
                FluidStack fluid = FluidStack.loadFluidStackFromNBT(
                        entry.getCompoundTag("Fluid"));
                if (fluid == null) continue;
                lines.add(fluid.getLocalizedName() + "x" + NumberUtils.formatCompact(amount));
            }
        }

        return lines;
    }

    static List<String> collectStoredItemLines(IBigItemHandler handler) {
        List<String> lines = new ArrayList<>();
        if (handler == null) {
            return lines;
        }
        for (int slot = 0; slot < handler.getSlotCount(); slot++) {
            BigItemStack snapshot = handler.getSlotSnapshot(slot);
            if (snapshot == null || snapshot.isEmpty()) {
                continue;
            }
            ItemStack template = snapshot.getTemplate();
            lines.add(template.getDisplayName()
                    + "x" + NumberUtils.formatCompact(snapshot.getAmount()));
        }
        return lines;
    }

    @Nullable
    private IBigItemHandler createItemHandler(ItemStack stack) {
        if (block instanceof WoodDrawerBlock) {
            DrawerLayout drawerLayout = ((WoodDrawerBlock) block).getDrawerLayout();
            return new DrawerStackItemHandler(stack, drawerLayout);
        }
        if (block instanceof FramedDrawerBlock) {
            DrawerLayout drawerLayout = ((FramedDrawerBlock) block).getDrawerLayout();
            return new DrawerStackItemHandler(stack, drawerLayout);
        }
        if (block instanceof CompactingDrawerBlock) {
            return new CompactingStackItemHandler(stack, 3);
        }
        if (block instanceof SimpleCompactingDrawerBlock) {
            return new CompactingStackItemHandler(stack, 2);
        }
        return null;
    }

    @Nullable
    private IFluidHandlerItem createFluidItemHandler(ItemStack stack) {
        if (block instanceof FluidDrawerBlock) {
            DrawerLayout drawerLayout = ((FluidDrawerBlock) block).getDrawerLayout();
            return new FluidDrawerStackItemHandler(stack, drawerLayout);
        }
        return null;
    }
}
