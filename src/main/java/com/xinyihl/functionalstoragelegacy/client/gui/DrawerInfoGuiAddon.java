package com.xinyihl.functionalstoragelegacy.client.gui;

import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.util.NumberUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Renders immutable long-capacity item snapshots in the drawer GUI panel.
 */
public class DrawerInfoGuiAddon {

    private final int posX;
    private final int posY;
    private final ResourceLocation gui;
    private final int slotAmount;
    private final Function<Integer, Pair<Integer, Integer>> slotPosition;
    private final Function<Integer, BigItemStack> slotSnapshot;
    private final Function<Integer, Long> slotMaxAmount;

    public DrawerInfoGuiAddon(int posX, int posY, ResourceLocation gui, int slotAmount, Function<Integer, Pair<Integer, Integer>> slotPosition, Function<Integer, BigItemStack> slotSnapshot, Function<Integer, Long> slotMaxAmount) {
        this.posX = posX;
        this.posY = posY;
        this.gui = gui;
        this.slotAmount = slotAmount;
        this.slotPosition = slotPosition;
        this.slotSnapshot = slotSnapshot;
        this.slotMaxAmount = slotMaxAmount;
    }

    public void drawBackground(GuiScreen screen, int guiX, int guiY) {
        Minecraft mc = Minecraft.getMinecraft();
        int size = 48;
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(gui);
        GlStateManager.enableBlend();
        Gui.drawScaledCustomSizeModalRect(guiX + posX, guiY + posY, 0, 0, 16, 16, size, size, 16, 16);
        GlStateManager.disableBlend();

        for (int slot = 0; slot < slotAmount; slot++) {
            BigItemStack snapshot = safeSnapshot(slot);
            if (!snapshot.hasTemplate()) {
                continue;
            }
            ItemStack itemStack = snapshot.getTemplate();
            int x = guiX + slotPosition.apply(slot).getLeft() + posX;
            int y = guiY + slotPosition.apply(slot).getRight() + posY;

            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemAndEffectIntoGUI(itemStack, x, y);
            RenderHelper.disableStandardItemLighting();

            String amount = NumberUtils.formatCompact(snapshot.getAmount()) + "/" + NumberUtils.formatCompact(slotMaxAmount.apply(slot));
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 0, 200);
            int textX = (int) (x + 17 - mc.fontRenderer.getStringWidth(amount) / 2F);
            mc.fontRenderer.drawStringWithShadow(amount, textX, y + 12, 0xFFFFFF);
            GlStateManager.popMatrix();
        }
    }

    public void drawForeground(GuiScreen screen, int guiX, int guiY, int mouseX, int mouseY) {
        for (int slot = 0; slot < slotAmount; slot++) {
            int x = slotPosition.apply(slot).getLeft() + posX + guiX;
            int y = slotPosition.apply(slot).getRight() + posY + guiY;
            if (mouseX <= x || mouseX >= x + 18 || mouseY <= y || mouseY >= y + 18) {
                continue;
            }

            int fx = slotPosition.apply(slot).getLeft() + posX;
            int fy = slotPosition.apply(slot).getRight() + posY;
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.colorMask(true, true, true, false);
            Gui.drawRect(fx - 1, fy - 1, fx + 17, fy + 17, 0x80FFFFFF);
            GlStateManager.colorMask(true, true, true, true);
            GlStateManager.enableLighting();
            GlStateManager.enableDepth();

            BigItemStack snapshot = safeSnapshot(slot);
            List<String> tooltip = new ArrayList<>();
            if (!snapshot.hasTemplate()) {
                tooltip.add("§6" + net.minecraft.client.resources.I18n.format("gui.functionalstoragelegacy.item") + "§f" + net.minecraft.client.resources.I18n.format("gui.functionalstoragelegacy.empty"));
            } else {
                tooltip.add("§6" + net.minecraft.client.resources.I18n.format("gui.functionalstoragelegacy.item") + "§f" + snapshot.getTemplate().getDisplayName());
                String amount = NumberUtils.formatCompact(snapshot.getAmount()) + "/" + NumberUtils.formatCompact(slotMaxAmount.apply(slot));
                tooltip.add("§6" + net.minecraft.client.resources.I18n.format("gui.functionalstoragelegacy.amount") + "§f" + amount);
            }
            tooltip.add("§6" + net.minecraft.client.resources.I18n.format("gui.functionalstoragelegacy.slot") + "§f" + slot);
            screen.drawHoveringText(tooltip, mouseX - guiX, mouseY - guiY);
        }
    }

    private BigItemStack safeSnapshot(int slot) {
        BigItemStack snapshot = slotSnapshot.apply(slot);
        return snapshot == null ? BigItemStack.empty() : snapshot;
    }

    public int getPosX() {
        return posX;
    }

    public int getPosY() {
        return posY;
    }
}
