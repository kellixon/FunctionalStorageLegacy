package com.xinyihl.functionalstoragelegacy.client.gui;

import com.xinyihl.functionalstoragelegacy.api.storage.BigFluidStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigFluidHandler;
import com.xinyihl.functionalstoragelegacy.util.NumberUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Renders fluid drawer info panel within the GUI.
 * Shows stored fluids with amounts in a 48x48 panel using background.png.
 * Ported from FunctionalStorage 1.21's FluidDrawerInfoGuiAddon.
 */
public class FluidDrawerInfoGuiAddon {

    private final int posX;
    private final int posY;
    private final ResourceLocation gui;
    private final int slotAmount;
    private final Function<Integer, Pair<Integer, Integer>> slotPosition;
    private final Supplier<IBigFluidHandler> fluidHandlerSupplier;
    private final Function<Integer, Long> slotMaxAmount;

    public FluidDrawerInfoGuiAddon(int posX, int posY, ResourceLocation gui, int slotAmount, Function<Integer, Pair<Integer, Integer>> slotPosition, Supplier<IBigFluidHandler> fluidHandlerSupplier, Function<Integer, Long> slotMaxAmount) {
        this.posX = posX;
        this.posY = posY;
        this.gui = gui;
        this.slotAmount = slotAmount;
        this.slotPosition = slotPosition;
        this.fluidHandlerSupplier = fluidHandlerSupplier;
        this.slotMaxAmount = slotMaxAmount;
    }

    /**
     * Returns {x, y, width, height} for fluid rendering area in the 48x48 panel.
     */
    public static int[] getSizeForSlots(int currentSlot, int slotAmount) {
        if (slotAmount == 1) {
            return new int[]{9, 9, 30, 30};
        }
        if (slotAmount == 2) {
            if (currentSlot == 0) return new int[]{0, 6, 48, 13};
            if (currentSlot == 1) return new int[]{0, 30, 48, 13};
        }
        if (slotAmount == 4) {
            if (currentSlot == 0) return new int[]{2, 2, 16, 16};
            if (currentSlot == 1) return new int[]{30, 2, 16, 16};
            if (currentSlot == 2) return new int[]{2, 30, 16, 16};
            if (currentSlot == 3) return new int[]{30, 30, 16, 16};
        }
        return new int[]{0, 0, 0, 0};
    }

    /**
     * Returns {x, y, width, height} for hover detection area in the 48x48 panel.
     */
    public static int[] getSizeForHoverSlots(int currentSlot, int slotAmount) {
        if (slotAmount == 1) {
            return new int[]{9, 9, 30, 30};
        }
        if (slotAmount == 2) {
            if (currentSlot == 0) return new int[]{6, 6, 36, 12};
            if (currentSlot == 1) return new int[]{6, 30, 36, 12};
        }
        if (slotAmount == 4) {
            if (currentSlot == 0) return new int[]{6, 6, 12, 12};
            if (currentSlot == 1) return new int[]{30, 6, 12, 12};
            if (currentSlot == 2) return new int[]{6, 30, 12, 12};
            if (currentSlot == 3) return new int[]{30, 30, 12, 12};
        }
        return new int[]{0, 0, 0, 0};
    }

    /**
     * Draw the background panel with fluid rendering and amount text.
     */
    public void drawBackground(GuiScreen screen, int guiX, int guiY) {
        Minecraft mc = Minecraft.getMinecraft();

        // Render fluids behind the background overlay
        for (int i = 0; i < slotAmount; i++) {
            BigFluidStack snapshot = safeSnapshot(i);
            FluidStack fluidStack = snapshot.getTemplate();
            if (fluidStack != null) {
                renderFluid(mc, guiX, guiY, fluidStack, i, slotAmount);
            }
        }
        // Draw front texture as the panel overlay on top of fluids (16x16 block texture scaled to 48x48)
        int size = 48;
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(gui);
        GlStateManager.enableBlend();
        Gui.drawScaledCustomSizeModalRect(guiX + posX, guiY + posY, 0, 0, 16, 16, size, size, 16, 16);
        GlStateManager.disableBlend();

        // Draw amount text
        for (int i = 0; i < slotAmount; i++) {
            BigFluidStack snapshot = safeSnapshot(i);
            if (snapshot.hasTemplate()) {
                int x = guiX + slotPosition.apply(i).getLeft() + posX;
                int y = guiY + slotPosition.apply(i).getRight() + posY;
                String amount = NumberUtils.formatCompactFluid(snapshot.getAmount()) + "/" + NumberUtils.formatCompactFluid(slotMaxAmount.apply(i));
                float scale = 1f;
                GlStateManager.pushMatrix();
                GlStateManager.translate(0, 0, 200);
                GlStateManager.scale(scale, scale, scale);
                int textX = (int) ((x + 17 - mc.fontRenderer.getStringWidth(amount) / 2F) * (1 / scale));
                int textY = (int) ((y + 12) * (1 / scale));
                mc.fontRenderer.drawStringWithShadow(amount, textX, textY, 0xFFFFFF);
                GlStateManager.popMatrix();
            }
        }
    }

    /**
     * Draw hover highlight and tooltip on mouse-over.
     */
    public void drawForeground(GuiScreen screen, int guiX, int guiY, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getMinecraft();
        for (int i = 0; i < slotAmount; i++) {
            int[] rect = getSizeForHoverSlots(i, slotAmount);
            int x = rect[0] + posX + guiX;
            int y = rect[1] + posY + guiY;
            if (mouseX > x && mouseX < x + rect[2] && mouseY > y && mouseY < y + rect[3]) {
                int fx = posX + rect[0];
                int fy = posY + rect[1];
                GlStateManager.disableLighting();
                GlStateManager.disableDepth();
                GlStateManager.colorMask(true, true, true, false);
                Gui.drawRect(fx, fy, fx + rect[2], fy + rect[3], 0x80FFFFFF);
                GlStateManager.colorMask(true, true, true, true);
                GlStateManager.enableLighting();
                GlStateManager.enableDepth();

                List<String> tooltip = new ArrayList<>();
                BigFluidStack snapshot = safeSnapshot(i);
                FluidStack over = snapshot.getTemplate();
                if (over == null) {
                    tooltip.add("§6" + net.minecraft.client.resources.I18n.format("gui.functionalstoragelegacy.fluid") + "§f" + net.minecraft.client.resources.I18n.format("gui.functionalstoragelegacy.empty"));
                } else {
                    tooltip.add("§6" + net.minecraft.client.resources.I18n.format("gui.functionalstoragelegacy.fluid") + "§f" + over.getLocalizedName());
                    String amountStr = NumberUtils.formatCompactFluid(snapshot.getAmount()) + "/" + NumberUtils.formatCompactFluid(slotMaxAmount.apply(i));
                    tooltip.add("§6" + net.minecraft.client.resources.I18n.format("gui.functionalstoragelegacy.amount") + "§f" + amountStr);
                }
                tooltip.add("§6" + net.minecraft.client.resources.I18n.format("gui.functionalstoragelegacy.slot") + "§f" + i);

                screen.drawHoveringText(tooltip, mouseX - guiX, mouseY - guiY);
            }
        }
    }

    private void renderFluid(Minecraft mc, int guiX, int guiY, FluidStack fluidStack, int slot, int slotAmount) {
        Fluid fluid = fluidStack.getFluid();
        if (fluid == null) return;
        ResourceLocation still = fluid.getStill(fluidStack);
        if (still == null) return;

        TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite(still.toString());

        int color = fluid.getColor(fluidStack);
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0) a = 1f;

        int[] rect = getSizeForSlots(slot, slotAmount);

        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.color(r, g, b, a);
        GlStateManager.enableBlend();

        for (int x = 0; x < rect[2]; x += 16) {
            for (int y = 0; y < rect[3]; y += 16) {
                int drawWidth = Math.min(16, rect[2] - x);
                int drawHeight = Math.min(16, rect[3] - y);
                drawTexturedRect(posX + guiX + rect[0] + x, posY + guiY + rect[1] + y, drawWidth, drawHeight, sprite);
            }
        }

        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
    }

    private BigFluidStack safeSnapshot(int tank) {
        IBigFluidHandler handler = fluidHandlerSupplier.get();
        if (handler == null || tank < 0 || tank >= handler.getStorageCount()) {
            return BigFluidStack.empty();
        }
        return handler.getSnapshot(tank);
    }

    private void drawTexturedRect(int x, int y, int width, int height, TextureAtlasSprite sprite) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
        float uMin = sprite.getMinU();
        float uMax = sprite.getMaxU();
        float vMin = sprite.getMinV();
        float vMax = sprite.getMaxV();
        // Adjust UV for partial rendering
        float uRange = uMax - uMin;
        float vRange = vMax - vMin;
        uMax = uMin + uRange * (width / 16f);
        vMax = vMin + vRange * (height / 16f);

        buffer.pos(x, y + height, 0).tex(uMin, vMax).endVertex();
        buffer.pos(x + width, y + height, 0).tex(uMax, vMax).endVertex();
        buffer.pos(x + width, y, 0).tex(uMax, vMin).endVertex();
        buffer.pos(x, y, 0).tex(uMin, vMin).endVertex();
        tessellator.draw();
    }
}
