package com.xinyihl.functionalstoragelegacy.client.gui;

import com.xinyihl.functionalstoragelegacy.Tags;
import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigFluidHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.common.container.ContainerDrawer;
import com.xinyihl.functionalstoragelegacy.common.storage.DrawerLayout;
import com.xinyihl.functionalstoragelegacy.common.tile.EnderDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.FluidDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.WoodDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.compact.CompactingDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.compact.SimpleCompactingDrawerTile;
import com.xinyihl.functionalstoragelegacy.util.ItemUtil;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.lang3.tuple.Pair;

import java.util.function.Function;

public class GuiDrawer extends GuiContainer {

    private static final ResourceLocation BACKGROUND = new ResourceLocation(Tags.MOD_ID, "textures/gui/background.png");

    private static final ResourceLocation SLOT = new ResourceLocation(Tags.MOD_ID, "textures/gui/slot.png");

    private static final int INFO_PANEL_X = 120;
    private static final int INFO_PANEL_Y = 8;

    private final ContainerDrawer container;
    private DrawerInfoGuiAddon itemInfoAddon;
    private FluidDrawerInfoGuiAddon fluidInfoAddon;

    public GuiDrawer(ContainerDrawer container) {
        super(container);
        this.container = container;
        this.xSize = 176;
        this.ySize = 166;
        initInfoAddon();
    }

    private void initInfoAddon() {
        ControllableDrawerTile tile = container.getTile();

        if (tile instanceof WoodDrawerTile) {
            WoodDrawerTile woodDrawerTile = (WoodDrawerTile) tile;
            DrawerLayout layout = woodDrawerTile.getDrawerLayout();
            IBigItemHandler handler = woodDrawerTile.getItemHandler();
            ResourceLocation frontTexture = new ResourceLocation(Tags.MOD_ID, "textures/blocks/" + woodDrawerTile.getWoodType().getId() + "_front_" + layout.getSlotCount() + ".png");
            itemInfoAddon = new DrawerInfoGuiAddon(INFO_PANEL_X, INFO_PANEL_Y, frontTexture, layout.getSlotCount(), DrawerGuiLayout.slotPositions(layout), handler::getSnapshot, handler::getCapacity);
        } else if (tile instanceof CompactingDrawerTile) {
            CompactingDrawerTile compactingTile = (CompactingDrawerTile) tile;
            IBigItemHandler handler = compactingTile.getItemHandler();
            Function<Integer, Pair<Integer, Integer>> positions = getCompactingPositions(handler.getStorageCount());
            ResourceLocation frontTexture;
            if (tile instanceof SimpleCompactingDrawerTile) {
                frontTexture = new ResourceLocation(Tags.MOD_ID, "textures/blocks/simple_compacting_drawer_front.png");
            } else {
                frontTexture = new ResourceLocation(Tags.MOD_ID, "textures/blocks/compacting_drawer_front.png");
            }
            itemInfoAddon = new DrawerInfoGuiAddon(INFO_PANEL_X, INFO_PANEL_Y, frontTexture, handler.getStorageCount(), positions, handler::getSnapshot, handler::getCapacity);
        } else if (tile instanceof FluidDrawerTile) {
            FluidDrawerTile fluidTile = (FluidDrawerTile) tile;
            DrawerLayout layout = fluidTile.getDrawerLayout();
            IBigFluidHandler handler = fluidTile.getFluidHandler();
            String suffix = layout.getSlotCount() == 1 ? "" : "_" + layout.getSlotCount();
            ResourceLocation frontTexture = new ResourceLocation(Tags.MOD_ID, "textures/blocks/fluid_front" + suffix + ".png");
            fluidInfoAddon = new FluidDrawerInfoGuiAddon(INFO_PANEL_X, INFO_PANEL_Y, frontTexture, layout.getSlotCount(), DrawerGuiLayout.slotPositions(layout), () -> handler, handler::getCapacity);
        } else if (tile instanceof EnderDrawerTile) {
            EnderDrawerTile enderTile = (EnderDrawerTile) tile;
            ResourceLocation frontTexture = new ResourceLocation(Tags.MOD_ID, "textures/blocks/ender_front.png");
            itemInfoAddon = new DrawerInfoGuiAddon(INFO_PANEL_X, INFO_PANEL_Y, frontTexture, 1, DrawerGuiLayout.slotPositions(DrawerLayout.X_1), i -> enderTile.getItemHandler() == null ? BigItemStack.empty() : enderTile.getItemHandler().getSnapshot(0), i -> {
                if (enderTile.getItemHandler() != null) {
                    return enderTile.getItemHandler().getCapacity(0);
                }
                return 0L;
            });
        }
    }

    private Function<Integer, Pair<Integer, Integer>> getCompactingPositions(int slotCount) {
        if (slotCount == 2) {
            return DrawerGuiLayout.slotPositions(DrawerLayout.X_2);
        }
        // 3 slots: top-center (slot 0) + bottom-left (slot 1) + bottom-right (slot 2)
        return i -> {
            if (i == 0) return Pair.of(16, 4);    // top-center (highest tier)
            if (i == 1) return Pair.of(4, 28);    // bottom-left (mid tier)
            return Pair.of(28, 28);                // bottom-right (lowest tier)
        };
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        int x = (this.width - this.xSize) / 2;
        int y = (this.height - this.ySize) / 2;
        mc.getTextureManager().bindTexture(BACKGROUND);
        this.drawTexturedModalRect(x, y, 0, 0, this.xSize, this.ySize);

        // Storage upgrade slot backgrounds
        int storageSlots = container.getTile().getStorageUpgrades().getSlots();
        for (int i = 0; i < storageSlots; i++) {
            int sx = x + 7 + i * 18;
            int sy = y + 19;
            this.drawTexturedModalRect(sx, sy, 238, 0, 18, 18);
        }

        // Utility upgrade slot backgrounds
        int utilitySlots = container.getTile().getUtilityUpgrades().getSlots();
        for (int i = 0; i < utilitySlots; i++) {
            int sx = x + 7 + i * 18;
            int sy = y + 43;
            this.drawTexturedModalRect(sx, sy, 238, 0, 18, 18);
        }

        // Draw info panel
        if (itemInfoAddon != null) {
            itemInfoAddon.drawBackground(this, x, y);
        }
        if (fluidInfoAddon != null) {
            fluidInfoAddon.drawBackground(this, x, y);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        drawUpgradeOverlays();

        String title = container.getTile().getDisplayName() != null ? container.getTile().getDisplayName().getUnformattedText() : "Drawer";
        this.fontRenderer.drawString(title, 8, 6, 4210752);
        this.fontRenderer.drawString(I18n.format("container.inventory"), 8, this.ySize - 96 + 2, 4210752);

        // Draw info panel tooltips
        int guiX = (this.width - this.xSize) / 2;
        int guiY = (this.height - this.ySize) / 2;
        if (itemInfoAddon != null) {
            itemInfoAddon.drawForeground(this, guiX, guiY, mouseX, mouseY);
        }
        if (fluidInfoAddon != null) {
            fluidInfoAddon.drawForeground(this, guiX, guiY, mouseX, mouseY);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    private void drawUpgradeOverlays() {
        int storageSlots = container.getTile().getStorageUpgrades().getSlots();
        int utilitySlots = container.getTile().getUtilityUpgrades().getSlots();
        ItemStack previewUpgradeStack = getUpgradePreviewStack();
        boolean storageUpgrade = ItemUtil.isStorageUpgradeItem(previewUpgradeStack);
        boolean utilityUpgrade = ItemUtil.isUtilityUpgradeItem(previewUpgradeStack);

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        for (int i = 0; i < storageSlots; i++) {
            int sx = 7 + i * 18;
            int sy = 19;

            if (storageUpgrade) {
                ItemStack existing = container.getTile().getStorageUpgrades().getStackInSlot(i);
                if (ItemUtil.hasHigherUpgradeReplacementPriority(previewUpgradeStack, existing)) {
                    int color = container.getTile().canReplaceStorageUpgrade(i, previewUpgradeStack) ? 0x5500AA00 : 0x55AA0000;
                    drawGradientRect(sx, sy, sx + 18, sy + 18, color, color);
                    continue;
                }
            }

            if (!container.getTile().canRemoveStorageUpgrade(i)) {
                drawGradientRect(sx, sy, sx + 18, sy + 18, 0x66000000, 0x66000000);
            }
        }

        for (int i = 0; i < utilitySlots; i++) {
            int sx = 7 + i * 18;
            int sy = 43;
            ItemStack existing = container.getTile().getUtilityUpgrades().getStackInSlot(i);

            if (utilityUpgrade && !existing.isEmpty()) {
                int color = container.getTile().canInsertUtilityUpgrade(i, previewUpgradeStack) ? 0x5500AA00 : 0x55AA0000;
                drawGradientRect(sx, sy, sx + 18, sy + 18, color, color);
            }
        }

        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
    }

    private ItemStack getUpgradePreviewStack() {
        ItemStack carried = mc.player == null ? ItemStack.EMPTY : mc.player.inventory.getItemStack();
        if (!carried.isEmpty()) {
            return carried;
        }

        Slot hovered = getSlotUnderMouse();
        if (hovered == null || !hovered.getHasStack()) {
            return ItemStack.EMPTY;
        }

        int upgradeSlots = container.getTile().getStorageUpgrades().getSlots() + container.getTile().getUtilityUpgrades().getSlots();
        if (hovered.slotNumber < upgradeSlots) {
            return ItemStack.EMPTY;
        }

        return hovered.getStack();
    }
}
