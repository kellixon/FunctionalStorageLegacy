package com.xinyihl.functionalstoragelegacy.client.render;

import com.xinyihl.functionalstoragelegacy.api.storage.BigFluidStack;
import com.xinyihl.functionalstoragelegacy.api.storage.BigItemStack;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigFluidHandler;
import com.xinyihl.functionalstoragelegacy.api.storage.IBigItemHandler;
import com.xinyihl.functionalstoragelegacy.api.upgrade.IStorageUpgrade;
import com.xinyihl.functionalstoragelegacy.api.upgrade.StorageFeature;
import com.xinyihl.functionalstoragelegacy.api.upgrade.UpgradeState;
import com.xinyihl.functionalstoragelegacy.common.block.DrawerAttachment;
import com.xinyihl.functionalstoragelegacy.common.storage.DrawerLayout;
import com.xinyihl.functionalstoragelegacy.common.block.base.DrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.item.ConfigurationToolItem;
import com.xinyihl.functionalstoragelegacy.common.tile.EnderDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.FluidDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.base.ControllableDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.compact.CompactingDrawerTile;
import com.xinyihl.functionalstoragelegacy.common.tile.compact.SimpleCompactingDrawerTile;
import com.xinyihl.functionalstoragelegacy.misc.Configurations;
import com.xinyihl.functionalstoragelegacy.util.NumberUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;

/**
 * TileEntity Special Renderer for drawing item/fluid icons and counts on drawer faces.
 * Handles all drawer types: standard (X_1/X_2/X_4), compacting, simple compacting, ender, and fluid.
 */
public class DrawerRenderer extends TileEntitySpecialRenderer<ControllableDrawerTile> {

    private static final ResourceLocation INDICATOR_TEXTURE = new ResourceLocation("functionalstoragelegacy", "textures/blocks/indicator.png");

    private static final ResourceLocation LOCK_TEXTURE = new ResourceLocation("functionalstoragelegacy", "textures/blocks/lock.png");

    private static final float Z_OFFSET_STACK = 0.97F;
    private static final float Z_OFFSET_COUNT = 0.975F;
    private static final float Z_OFFSET_FLUID = 0.96F;
    private static final float Z_OFFSET_INDICATOR = 1.002F;
    private static final float Z_OFFSET_UPGRADE = 1.005F;
    private static final float Z_OFFSET_LOCK = 1.01F;

    // ============================================================
    // Standard drawers
    // ============================================================
    @Override
    public void render(@Nonnull ControllableDrawerTile te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        if (te == null || te.getWorld() == null) return;

        // Distance check using client config render range
        double distSq = te.getDistanceSq(Minecraft.getMinecraft().player.posX, Minecraft.getMinecraft().player.posY, Minecraft.getMinecraft().player.posZ);
        double renderRange = Configurations.CLIENT.drawerRenderRange;
        if (distSq > renderRange * renderRange) return;

        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof DrawerBlock)) return;

        DrawerBlock block = (DrawerBlock) state.getBlock();
        DrawerAttachment attachment = DrawerBlock.getAttachment(state);
        EnumFacing horizontalFacing = DrawerBlock.getHorizontalFacing(state);
        EnumFacing frontFacing = DrawerBlock.getFrontFacing(state);
        DrawerOptions options = te.getDrawerOptions();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);

        int ambLight = te.getWorld().getCombinedLight(te.getPos().offset(frontFacing), 0);
        int lu = ambLight % 65536;
        int lv = ambLight / 65536;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float) lu, (float) lv);

        setupFaceTransform(attachment, horizontalFacing);

        if (te instanceof FluidDrawerTile) {
            renderFluidSlots((FluidDrawerTile) te, block.getDrawerLayout(), options);
        } else if (te instanceof SimpleCompactingDrawerTile) {
            renderSimpleCompactingSlots((SimpleCompactingDrawerTile) te, options);
        } else if (te instanceof CompactingDrawerTile) {
            renderCompactingSlots((CompactingDrawerTile) te, options);
        } else if (te instanceof EnderDrawerTile) {
            renderEnderSlot((EnderDrawerTile) te, options);
        } else {
            renderItemSlots(te, block.getDrawerLayout(), options);
        }

        // Render upgrade icons on the face
        renderUpgradesOnFace(te, options, Z_OFFSET_UPGRADE);

        renderLockOnFace(te, Z_OFFSET_LOCK);

        GlStateManager.enableLighting();
        GlStateManager.enableLight(0);
        GlStateManager.enableLight(1);
        GlStateManager.enableColorMaterial();
        GlStateManager.colorMaterial(1032, 5634);
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableNormalize();
        GlStateManager.disableBlend();

        GlStateManager.popMatrix();
    }

    // ============================================================
    // Item  drawers
    // ============================================================
    private void renderItemSlots(ControllableDrawerTile te, DrawerLayout drawerLayout, DrawerOptions options) {
        if (drawerLayout == null) return;
        IBigItemHandler handler = te.getItemHandler();
        if (handler == null) return;

        boolean showRender = options == null || options.isShowItemRender();
        boolean showCount = options == null || options.isShowItemCount();

        switch (drawerLayout) {
            case X_1:
                renderItemSlot(handler, 0, 0.5F, 0.5F, 1.0F, showRender, showCount, 0.3F, options);
                break;
            case X_2:
                renderItemSlot(handler, 0, 0.5F, 0.77F, 0.5F, showRender, showCount, 0.2F, options);
                renderItemSlot(handler, 1, 0.5F, 0.27F, 0.5F, showRender, showCount, 0.2F, options);
                break;
            case X_4:
                renderItemSlot(handler, 0, 0.25F, 0.77F, 0.5F, showRender, showCount, 0.2F, options);
                renderItemSlot(handler, 1, 0.75F, 0.77F, 0.5F, showRender, showCount, 0.2F, options);
                renderItemSlot(handler, 2, 0.25F, 0.27F, 0.5F, showRender, showCount, 0.2F, options);
                renderItemSlot(handler, 3, 0.75F, 0.27F, 0.5F, showRender, showCount, 0.2F, options);
                break;
        }
    }

    private void renderItemSlot(IBigItemHandler handler, int slot, float posX, float posY, float slotScale, boolean showRender, boolean showCount, float textScale, DrawerOptions options) {
        if (slot < 0 || slot >= handler.getSlotCount()) return;
        BigItemStack snapshot = handler.getSlotSnapshot(slot);
        if (snapshot == null || !snapshot.hasTemplate()) return;
        ItemStack stack = snapshot.getTemplate();

        long count = snapshot.getAmount();
        long maxAmount = handler.getSlotCapacity(slot);
        float progress = ratio(count, maxAmount);
        renderIndicatorOnFace(posX, posY, slotScale, progress, options, Z_OFFSET_INDICATOR);
        if (showRender) {
            renderStackOnFace(stack, posX, posY, slotScale, Z_OFFSET_STACK);
        }
        if (showCount) {
            renderCountOnFace(count, posX, posY, slotScale, textScale, Z_OFFSET_COUNT);
        }
    }

    // ============================================================
    // Compacting drawers
    // ============================================================
    private void renderSimpleCompactingSlots(SimpleCompactingDrawerTile te, DrawerOptions options) {
        IBigItemHandler handler = te.getItemHandler();
        if (handler == null) return;

        boolean showRender = options == null || options.isShowItemRender();
        boolean showCount = options == null || options.isShowItemCount();

        renderCompactSlot(handler, 0, 0.5F, 0.77F, showRender, showCount, options);
        renderCompactSlot(handler, 1, 0.5F, 0.27F, showRender, showCount, options);
    }

    private void renderCompactingSlots(CompactingDrawerTile te, DrawerOptions options) {
        IBigItemHandler handler = te.getItemHandler();
        if (handler == null) return;

        boolean showRender = options == null || options.isShowItemRender();
        boolean showCount = options == null || options.isShowItemCount();

        // 3 slots: 0 = top-center, 1 = bottom-left, 2 = bottom-right
        renderCompactSlot(handler, 0, 0.5F, 0.77F, showRender, showCount, options);
        renderCompactSlot(handler, 1, 0.25F, 0.27F, showRender, showCount, options);
        renderCompactSlot(handler, 2, 0.75F, 0.27F, showRender, showCount, options);
    }

    private void renderCompactSlot(IBigItemHandler handler, int slot, float posX, float posY, boolean showRender, boolean showCount, DrawerOptions options) {
        if (slot < 0 || slot >= handler.getSlotCount()) return;
        BigItemStack snapshot = handler.getSlotSnapshot(slot);
        if (snapshot == null || !snapshot.hasTemplate()) return;
        ItemStack stack = snapshot.getTemplate();

        long count = snapshot.getAmount();
        long maxAmount = handler.getSlotCapacity(slot);
        float progress = ratio(count, maxAmount);
        renderIndicatorOnFace(posX, posY, 0.5F, progress, options, Z_OFFSET_INDICATOR);
        if (showRender) {
            renderStackOnFace(stack, posX, posY, 0.5F, 1.001F);
        }
        if (showCount) {
            renderCountOnFace(count, posX, posY, 0.5F, 0.2F, 1.005F);
        }
    }


    // ============================================================
    // Ender drawers
    // ============================================================
    private void renderEnderSlot(EnderDrawerTile te, DrawerOptions options) {
        IBigItemHandler handler = te.getItemHandler();
        if (handler == null || handler.getSlotCount() < 1) return;

        boolean showRender = options == null || options.isShowItemRender();
        boolean showCount = options == null || options.isShowItemCount();

        BigItemStack snapshot = handler.getSlotSnapshot(0);
        if (snapshot == null || !snapshot.hasTemplate()) return;
        ItemStack stack = snapshot.getTemplate();

        long count = snapshot.getAmount();
        if (showRender) {
            renderStackOnFace(stack, 0.5F, 0.5F, 1.0F, 1.001F);
        }
        if (showCount) {
            renderCountOnFace(count, 0.5F, 0.5F, 1.0F, 0.2F, 1.005F);
        }
    }

    // ============================================================
    // Fluid drawers
    // ============================================================
    private void renderFluidSlots(FluidDrawerTile te, DrawerLayout drawerLayout, DrawerOptions options) {
        IBigFluidHandler handler = te.getFluidHandler();
        if (handler == null || drawerLayout == null) return;

        boolean showRender = options == null || options.isShowItemRender();
        boolean showCount = options == null || options.isShowItemCount();

        switch (drawerLayout) {
            case X_1:
                renderSingleFluidSlot(handler, 0, 0.5F, 0.5F, 1.0F, 0.35F, 0.35F, showRender, showCount, 0.3F, options);
                break;
            case X_2:
                renderSingleFluidSlot(handler, 0, 0.49F, 0.77F, 0.5F, 0.80F, 0.30F, showRender, showCount, 0.2F, options);
                renderSingleFluidSlot(handler, 1, 0.49F, 0.27F, 0.5F, 0.80F, 0.30F, showRender, showCount, 0.2F, options);
                break;
            case X_4:
                renderSingleFluidSlot(handler, 0, 0.25F, 0.77F, 0.5F, 0.35F, 0.35F, showRender, showCount, 0.2F, options);
                renderSingleFluidSlot(handler, 1, 0.75F, 0.77F, 0.5F, 0.35F, 0.35F, showRender, showCount, 0.2F, options);
                renderSingleFluidSlot(handler, 2, 0.25F, 0.27F, 0.5F, 0.35F, 0.35F, showRender, showCount, 0.2F, options);
                renderSingleFluidSlot(handler, 3, 0.75F, 0.27F, 0.5F, 0.35F, 0.35F, showRender, showCount, 0.2F, options);
                break;
        }
    }

    private void renderSingleFluidSlot(IBigFluidHandler handler, int slot, float posX, float posY, float slotScale,
                                       float fluidHalfWidth, float fluidHalfHeight,
                                       boolean showRender, boolean showCount, float textScale,
                                       DrawerOptions options) {
        if (slot < 0 || slot >= handler.getTankCount()) return;
        BigFluidStack snapshot = handler.getTankSnapshot(slot);
        if (snapshot == null || !snapshot.hasTemplate()) return;
        FluidStack fluid = snapshot.getTemplate();

        long count = snapshot.getAmount();
        long maxAmount = handler.getTankCapacity(slot);
        float progress = ratio(count, maxAmount);
        renderIndicatorOnFace(posX, posY, slotScale, progress, options, Z_OFFSET_INDICATOR);
        if (showRender) {
            renderFluidOnFace(fluid, posX, posY, slotScale, Z_OFFSET_FLUID, fluidHalfWidth, fluidHalfHeight);
        }
        if (showCount) {
            renderCountOnFace(count, posX, posY, slotScale, textScale, Z_OFFSET_COUNT);
        }
    }

    private static float ratio(long amount, long capacity) {
        return capacity <= 0L ? 0.0F : (float) Math.min(1.0D, amount / (double) capacity);
    }

    // ============================================================
    // Face transformation
    // ============================================================
    private void setupFaceTransform(DrawerAttachment attachment, EnumFacing horizontalFacing) {
        GlStateManager.translate(0.5F, 0.5F, 0.5F);

        int rotY;
        switch (horizontalFacing) {
            case NORTH:
                rotY = 180;
                break;
            case EAST:
                rotY = 90;
                break;
            case WEST:
                rotY = 270;
                break;
            case SOUTH:
            default:
                rotY = 0;
                break;
        }
        GlStateManager.rotate(rotY, 0, 1, 0);

        switch (attachment) {
            case FLOOR:
                GlStateManager.rotate(-90, 1, 0, 0);
                break;
            case CEILING:
                GlStateManager.rotate(90, 1, 0, 0);
                break;
            case WALL:
            default:
                break;
        }
        GlStateManager.translate(-0.5F, -0.5F, -0.5F);
    }

    // ============================================================
    // Core rendering: item stack on face
    // ============================================================
    private void renderStackOnFace(ItemStack stack, float posX, float posY, float slotScale, float zOffset) {
        if (stack.isEmpty()) return;

        float cX = posX * 16.0f;
        float cY = posY * 16.0f;
        float size = (slotScale >= 1.0f) ? 0.5f : 0.25f;

        float offsetX = cX - 8.0f * size;
        float offsetY = 16.0f - cY - 8.0f * size;
        GlStateManager.pushMatrix();

        GlStateManager.translate(0, 1, zOffset);
        GlStateManager.scale(1 / 16f, -1 / 16f, 0.00001f);
        GlStateManager.translate(offsetX, offsetY, 0);
        GlStateManager.scale(size, size, 1);

        GlStateManager.pushMatrix();
        if (size >= 0.5f) {
            GlStateManager.scale(2.6f, 2.6f, 1);
            GlStateManager.rotate(171.6f, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(84.9f, 1.0F, 0.0F, 0.0F);
        } else {
            GlStateManager.scale(1.92f, 1.92f, 1);
            GlStateManager.rotate(169.2f, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(79.0f, 1.0F, 0.0F, 0.0F);
        }
        RenderHelper.enableStandardItemLighting();
        GlStateManager.popMatrix();

        GlStateManager.enableCull();
        GlStateManager.disableRescaleNormal();
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(-1, -1);

        GlStateManager.enableRescaleNormal();
        GlStateManager.disableRescaleNormal();
        GlStateManager.pushAttrib();
        GlStateManager.enableRescaleNormal();
        GlStateManager.popAttrib();

        RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
        try {
            renderItem.renderItemIntoGUI(stack, 0, 0);
        } catch (Exception ignored) {
        }

        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.disablePolygonOffset();

        GlStateManager.popMatrix();
    }

    // ============================================================
    // Core rendering: fluid icon on face
    // ============================================================
    private void renderFluidOnFace(FluidStack fluid, float posX, float posY, float slotScale, float zOffset,
                                   float halfWidth, float halfHeight) {
        if (fluid == null || fluid.getFluid() == null) return;

        TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks()
                .getAtlasSprite(fluid.getFluid().getStill(fluid).toString());
        if (sprite == null) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(posX, posY, zOffset);

        if (slotScale != 1.0F) {
            GlStateManager.scale(slotScale, slotScale, 1.0F);
        }

        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        int color = fluid.getFluid().getColor(fluid);
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float a = ((color >> 24) & 0xFF) / 255.0F;

        GlStateManager.color(r, g, b, a);
        GlStateManager.enableBlend();
        GlStateManager.disableLighting();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(-halfWidth, -halfHeight, 0).tex(sprite.getMinU(), sprite.getMaxV()).endVertex();
        buffer.pos(halfWidth, -halfHeight, 0).tex(sprite.getMaxU(), sprite.getMaxV()).endVertex();
        buffer.pos(halfWidth, halfHeight, 0).tex(sprite.getMaxU(), sprite.getMinV()).endVertex();
        buffer.pos(-halfWidth, halfHeight, 0).tex(sprite.getMinU(), sprite.getMinV()).endVertex();
        tessellator.draw();

        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();
    }

    // ============================================================
    // Core rendering: lock icon on face
    // ============================================================
    private void renderLockOnFace(ControllableDrawerTile te, float zOffset) {
        if (!te.isLocked()) return;

        GlStateManager.pushMatrix();

        float offsetX = 0.5f;
        float offsetY = 15.5f / 16.0f;
        GlStateManager.translate(offsetX, offsetY, zOffset);
        float size = 0.5f / 16.0f;
        GlStateManager.scale(size, size, 1.0f);

        Minecraft.getMinecraft().getTextureManager().bindTexture(LOCK_TEXTURE);

        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableLighting();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX);

        buffer.pos(-1, -1, 0).tex(0, 1).endVertex();
        buffer.pos(1, -1, 0).tex(1, 1).endVertex();
        buffer.pos(1, 1, 0).tex(1, 0).endVertex();
        buffer.pos(-1, 1, 0).tex(0, 0).endVertex();

        tessellator.draw();

        GlStateManager.enableLighting();
        GlStateManager.disableBlend();

        GlStateManager.popMatrix();
    }

    // ============================================================
    // Core rendering: count text on face
    // ============================================================
    private void renderCountOnFace(long count, float posX, float posY, float slotScale, float maxScale, float zOffset) {
        if (count <= 0) return;
        String text = NumberUtils.formatCompact(count);
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;

        float offsetX = posX * 16.0f;
        float cY = posY * 16.0f;
        float size = (slotScale >= 1.0f) ? 1f : 0.25f;

        float offsetY = 16.0f - cY + 4.0f * size; // shift down

        GlStateManager.pushMatrix();

        GlStateManager.translate(0, 1, zOffset);
        GlStateManager.scale(1 / 16f, -1 / 16f, 0.00001f);
        GlStateManager.translate(offsetX, offsetY, 0);

        int textWidth = fontRenderer.getStringWidth(text);
        float scaleX = (16.0f * size) / (float) Math.max(textWidth, 1);
        float actualScale = Math.min(scaleX * 0.8f, maxScale);

        GlStateManager.scale(actualScale, actualScale, 1.0f);

        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(false);

        fontRenderer.drawString(text, -textWidth / 2F, 2F, 0xFFFFFFFF, true);

        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    // ============================================================
    // Core rendering: indicator on face
    // ============================================================
    private void renderIndicatorOnFace(float posX, float posY, float slotScale, float progress, DrawerOptions options, float zOffset) {
        if (options == null) return;
        int indicatorValue = options.getAdvancedValue(ConfigurationToolItem.ConfigurationAction.INDICATOR);
        if (indicatorValue == 0) return;

        GlStateManager.pushMatrix();

        // Position the indicator below the slot center
        float barWidth = slotScale * 0.5f;   // half the slot width
        float barHeight = slotScale * 0.08f;  // thin bar
        float yOffset = slotScale * 0.415f;   // below center

        float x1 = posX - barWidth / 2f;
        float x2 = posX + barWidth / 2f;
        float y1 = posY - yOffset - barHeight;
        float y2 = posY - yOffset;

        Minecraft.getMinecraft().getTextureManager().bindTexture(INDICATOR_TEXTURE);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableLighting();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        // Background bar (top half of texture: V 0.0 ~ 0.5) - render for modes 1 and 2
        if (indicatorValue != 3) {
            buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
            buffer.pos(x1, y1, zOffset).tex(0.0, 0.125).endVertex();
            buffer.pos(x2, y1, zOffset).tex(0.5, 0.125).endVertex();
            buffer.pos(x2, y2, zOffset).tex(0.5, 0.0).endVertex();
            buffer.pos(x1, y2, zOffset).tex(0.0, 0.0).endVertex();
            tessellator.draw();
        }

        // Foreground (progress) bar (bottom half of texture: V 0.5 ~ 1.0) - render for mode 1 always, or modes 2/3 only when full
        if (indicatorValue == 1 || progress >= 1.0f) {
            float progressX2 = x1 + (x2 - x1) * progress;
            float progressU2 = 0.5f * progress;
            buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
            buffer.pos(x1, y1, zOffset + 0.0001f).tex(0.0, 0.625).endVertex();
            buffer.pos(progressX2, y1, zOffset + 0.0001f).tex(progressU2, 0.625).endVertex();
            buffer.pos(progressX2, y2, zOffset + 0.0001f).tex(progressU2, 0.5).endVertex();
            buffer.pos(x1, y2, zOffset + 0.0001f).tex(0.0, 0.5).endVertex();
            tessellator.draw();
        }

        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    // ============================================================
    // Core rendering: upgrade icons on face
    // ============================================================
    private void renderUpgradesOnFace(ControllableDrawerTile te, DrawerOptions options, float zOffset) {
        if (options == null || !options.isActive(ConfigurationToolItem.ConfigurationAction.TOGGLE_UPGRADES)) return;

        float iconScale = 0.002f;

        GlStateManager.pushMatrix();

        // Bottom-left corner position
        float baseX = 0.03f;
        float baseY = 0.03f;
        int iconIndex = 0;

        // Render storage upgrades
        for (int i = 0; i < te.getStorageUpgrades().getSlots(); i++) {
            ItemStack upgradeStack = te.getStorageUpgrades().getStackInSlot(i);
            if (!upgradeStack.isEmpty()) {
                GlStateManager.pushMatrix();
                // Flip Y scale to match GUI expectation and fix winding order (so it's visible from the front)
                GlStateManager.translate(baseX + iconIndex * iconScale * 16, baseY + iconScale * 16, zOffset);
                GlStateManager.scale(iconScale, -iconScale, 0.00001f);

                RenderHelper.enableStandardItemLighting();
                GlStateManager.enableRescaleNormal();
                try {
                    Minecraft.getMinecraft().getRenderItem().renderItemIntoGUI(upgradeStack, 0, 0);
                } catch (Exception ignored) {
                }
                GlStateManager.disableRescaleNormal();

                GlStateManager.popMatrix();
                iconIndex++;
            }
        }

        // Render the actual installed upgrade that contributes void overflow.
        ItemStack voidUpgrade = findUpgradeProvidingFeature(te, StorageFeature.VOID_OVERFLOW);
        if (!voidUpgrade.isEmpty()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(1.0f - baseX - iconScale * 16, baseY + iconScale * 16, zOffset);
            GlStateManager.scale(iconScale, -iconScale, 0.00001f);

            RenderHelper.enableStandardItemLighting();
            GlStateManager.enableRescaleNormal();
            try {
                Minecraft.getMinecraft().getRenderItem().renderItemIntoGUI(voidUpgrade, 0, 0);
            } catch (Exception ignored) {
            }
            GlStateManager.disableRescaleNormal();

            GlStateManager.popMatrix();
        }

        GlStateManager.popMatrix();
    }

    private ItemStack findUpgradeProvidingFeature(
            ControllableDrawerTile tile, StorageFeature feature) {
        for (int i = 0; i < tile.getStorageUpgrades().getSlots(); i++) {
            ItemStack stack = tile.getStorageUpgrades().getStackInSlot(i);
            if (providesFeature(stack, feature)) {
                return stack;
            }
        }
        for (int i = 0; i < tile.getUtilityUpgrades().getSlots(); i++) {
            ItemStack stack = tile.getUtilityUpgrades().getStackInSlot(i);
            if (providesFeature(stack, feature)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private boolean providesFeature(ItemStack stack, StorageFeature feature) {
        if (stack.isEmpty() || !(stack.getItem() instanceof IStorageUpgrade)) {
            return false;
        }
        UpgradeState.Builder builder = UpgradeState.builder();
        ((IStorageUpgrade) stack.getItem()).applyUpgrade(stack, builder);
        return builder.build().hasFeature(feature);
    }
}
