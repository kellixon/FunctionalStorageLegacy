package com.xinyihl.functionalstoragelegacy.client.render;

import com.xinyihl.functionalstoragelegacy.common.item.LinkingToolItem;
import com.xinyihl.functionalstoragelegacy.common.tile.controller.ControllerExtensionTile;
import com.xinyihl.functionalstoragelegacy.common.tile.controller.DrawerControllerTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * TileEntitySpecialRenderer for StorageControllerTile.
 * Renders:
 * 1. Controller search range box (green wireframe + translucent green faces)
 * 2. Connected drawers highlight (white wireframes)
 * 3. Linking tool area selection preview (white wireframe for two-point selection in MULTIPLE mode)
 * <p>
 * All rendering only occurs when the player holds a LinkingToolItem in their main hand
 * and the tool is bound to this controller.
 */
@SideOnly(Side.CLIENT)
public class ControllerRenderer extends TileEntitySpecialRenderer<DrawerControllerTile> {

    @Override
    public void render(@Nonnull DrawerControllerTile te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {

        Minecraft mc = Minecraft.getMinecraft();

        ItemStack mainHand = mc.player.getHeldItemMainhand();
        if (mainHand.isEmpty() || !(mainHand.getItem() instanceof LinkingToolItem)) return;

        // Only render if the tool is bound to THIS controller
        BlockPos controllerPos = LinkingToolItem.getControllerPos(mainHand);
        if (controllerPos == null || !controllerPos.equals(te.getPos())) return;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);

        // Disable texture, enable blending for translucent rendering
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();

        // ---- 1. Area selection preview (MULTIPLE mode, first position set) ----
        LinkingToolItem.LinkingMode mode = LinkingToolItem.getLinkingMode(mainHand);
        if (mode == LinkingToolItem.LinkingMode.MULTIPLE && hasFirstPosition(mainHand)) {
            BlockPos firstPos = getFirstPosition(mainHand);
            RayTraceResult rayTrace = mc.player.rayTrace(8, partialTicks);
            if (rayTrace != null && rayTrace.typeOfHit == RayTraceResult.Type.BLOCK) {
                BlockPos hitPos = rayTrace.getBlockPos();
                AxisAlignedBB selectionBox = new AxisAlignedBB(Math.min(firstPos.getX(), hitPos.getX()), Math.min(firstPos.getY(), hitPos.getY()), Math.min(firstPos.getZ(), hitPos.getZ()), Math.max(firstPos.getX(), hitPos.getX()) + 1, Math.max(firstPos.getY(), hitPos.getY()) + 1, Math.max(firstPos.getZ(), hitPos.getZ()) + 1).offset(-te.getPos().getX(), -te.getPos().getY(), -te.getPos().getZ());
                renderWireframeBox(selectionBox, 1f, 1f, 1f, 1f);
            }
        }

        // ---- 2. Connected drawers highlight (white wireframes) ----
        Set<BlockPos> highlightedBlocks = new HashSet<>();
        List<Long> connectedPositions = te.getConnectedDrawers().getConnectedDrawers();
        for (Long posLong : connectedPositions) {
            highlightedBlocks.add(BlockPos.fromLong(posLong));
        }

        for (Long posLong : te.getConnectedDrawers().getLinkedExtensionPositions()) {
            BlockPos extensionPos = BlockPos.fromLong(posLong);
            if (!(te.getWorld().getTileEntity(extensionPos) instanceof ControllerExtensionTile)) {
                continue;
            }
            highlightedBlocks.add(extensionPos);
        }
        renderMergedBlockWireframes(highlightedBlocks, te.getPos(), 1f, 1f, 1f, 1f);

        // ---- 3. Controller range box (green wireframe + translucent green faces) ----
        double range = te.getControllerRange();
        AxisAlignedBB rangeBox = new AxisAlignedBB(0, 0, 0, 1, 1, 1).grow(range).grow(0.002);
        renderWireframeBox(new AxisAlignedBB(0, 0, 0, 1, 1, 1).grow(0.002), 1f, 0.293f, 0.416f, 1f);
        GlStateManager.enableDepth();
        renderWireframeBox(rangeBox, 0.0f, 1f, 0.0f, 1f);
        renderGridBox(rangeBox, 0.0f, 1f, 0.0f, 0.3f);
        renderFilledBox(rangeBox, 0.0f, 1f, 0.0f, 0.11f);

        // Restore GL state
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    @Override
    public boolean isGlobalRenderer(@Nonnull DrawerControllerTile te) {
        return true;
    }

    // ============================================================
    // Wireframe box rendering
    // ============================================================

    private void renderWireframeBox(AxisAlignedBB box, float r, float g, float b, float a) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        GlStateManager.glLineWidth(2.0f);
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        float x1 = (float) box.minX;
        float y1 = (float) box.minY;
        float z1 = (float) box.minZ;
        float x2 = (float) box.maxX;
        float y2 = (float) box.maxY;
        float z2 = (float) box.maxZ;

        // Bottom face edges
        buffer.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y1, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y1, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y1, z2).color(r, g, b, a).endVertex();
        buffer.pos(x2, y1, z2).color(r, g, b, a).endVertex();
        buffer.pos(x1, y1, z2).color(r, g, b, a).endVertex();
        buffer.pos(x1, y1, z2).color(r, g, b, a).endVertex();
        buffer.pos(x1, y1, z1).color(r, g, b, a).endVertex();

        // Top face edges
        buffer.pos(x1, y2, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z2).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z2).color(r, g, b, a).endVertex();
        buffer.pos(x1, y2, z2).color(r, g, b, a).endVertex();
        buffer.pos(x1, y2, z2).color(r, g, b, a).endVertex();
        buffer.pos(x1, y2, z1).color(r, g, b, a).endVertex();

        // Vertical edges
        buffer.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.pos(x1, y2, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y1, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y1, z2).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z2).color(r, g, b, a).endVertex();
        buffer.pos(x1, y1, z2).color(r, g, b, a).endVertex();
        buffer.pos(x1, y2, z2).color(r, g, b, a).endVertex();

        tessellator.draw();
    }

    private void renderMergedBlockWireframes(Collection<BlockPos> blocks, BlockPos origin, float r, float g, float b, float a) {
        if (blocks.isEmpty()) return;

        Map<EdgeKey, Integer> edgeCounts = new HashMap<>();

        for (BlockPos block : blocks) {
            int x = block.getX();
            int y = block.getY();
            int z = block.getZ();

            addEdge(edgeCounts, x, y, z, x + 1, y, z);
            addEdge(edgeCounts, x + 1, y, z, x + 1, y, z + 1);
            addEdge(edgeCounts, x + 1, y, z + 1, x, y, z + 1);
            addEdge(edgeCounts, x, y, z + 1, x, y, z);

            addEdge(edgeCounts, x, y + 1, z, x + 1, y + 1, z);
            addEdge(edgeCounts, x + 1, y + 1, z, x + 1, y + 1, z + 1);
            addEdge(edgeCounts, x + 1, y + 1, z + 1, x, y + 1, z + 1);
            addEdge(edgeCounts, x, y + 1, z + 1, x, y + 1, z);

            addEdge(edgeCounts, x, y, z, x, y + 1, z);
            addEdge(edgeCounts, x + 1, y, z, x + 1, y + 1, z);
            addEdge(edgeCounts, x + 1, y, z + 1, x + 1, y + 1, z + 1);
            addEdge(edgeCounts, x, y, z + 1, x, y + 1, z + 1);
        }

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        GlStateManager.glLineWidth(2.0f);
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        for (Map.Entry<EdgeKey, Integer> entry : edgeCounts.entrySet()) {
            if (entry.getValue() != 1) {
                continue;
            }
            EdgeKey edge = entry.getKey();
            buffer.pos(edge.x1 - origin.getX(), edge.y1 - origin.getY(), edge.z1 - origin.getZ()).color(r, g, b, a).endVertex();
            buffer.pos(edge.x2 - origin.getX(), edge.y2 - origin.getY(), edge.z2 - origin.getZ()).color(r, g, b, a).endVertex();
        }

        tessellator.draw();
    }

    private void addEdge(Map<EdgeKey, Integer> edgeCounts, int x1, int y1, int z1, int x2, int y2, int z2) {
        EdgeKey key = new EdgeKey(x1, y1, z1, x2, y2, z2);
        edgeCounts.compute(key, (k, count) -> count == null ? 1 : count + 1);
    }

    private void renderGridBox(AxisAlignedBB box, float r, float g, float b, float a) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        GlStateManager.glLineWidth(1.1f);
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        float x1 = (float) box.minX;
        float y1 = (float) box.minY;
        float z1 = (float) box.minZ;
        float x2 = (float) box.maxX;
        float y2 = (float) box.maxY;
        float z2 = (float) box.maxZ;

        // Draw grid lines on faces
        for (float x = (float) Math.ceil(x1); x < x2; x += 1.0f) {
            // Top and Bottom (YZ)
            buffer.pos(x, y1, z1).color(r, g, b, a).endVertex();
            buffer.pos(x, y1, z2).color(r, g, b, a).endVertex();
            buffer.pos(x, y2, z1).color(r, g, b, a).endVertex();
            buffer.pos(x, y2, z2).color(r, g, b, a).endVertex();
            // Front and Back (XY)
            buffer.pos(x, y1, z1).color(r, g, b, a).endVertex();
            buffer.pos(x, y2, z1).color(r, g, b, a).endVertex();
            buffer.pos(x, y1, z2).color(r, g, b, a).endVertex();
            buffer.pos(x, y2, z2).color(r, g, b, a).endVertex();
        }

        for (float y = (float) Math.ceil(y1); y < y2; y += 1.0f) {
            // Front and Back (XZ)
            buffer.pos(x1, y, z1).color(r, g, b, a).endVertex();
            buffer.pos(x2, y, z1).color(r, g, b, a).endVertex();
            buffer.pos(x1, y, z2).color(r, g, b, a).endVertex();
            buffer.pos(x2, y, z2).color(r, g, b, a).endVertex();
            // Left and Right (YZ)
            buffer.pos(x1, y, z1).color(r, g, b, a).endVertex();
            buffer.pos(x1, y, z2).color(r, g, b, a).endVertex();
            buffer.pos(x2, y, z1).color(r, g, b, a).endVertex();
            buffer.pos(x2, y, z2).color(r, g, b, a).endVertex();
        }

        for (float z = (float) Math.ceil(z1); z < z2; z += 1.0f) {
            // Top and Bottom (XZ)
            buffer.pos(x1, y1, z).color(r, g, b, a).endVertex();
            buffer.pos(x2, y1, z).color(r, g, b, a).endVertex();
            buffer.pos(x1, y2, z).color(r, g, b, a).endVertex();
            buffer.pos(x2, y2, z).color(r, g, b, a).endVertex();
            // Left and Right (XY)
            buffer.pos(x1, y1, z).color(r, g, b, a).endVertex();
            buffer.pos(x1, y2, z).color(r, g, b, a).endVertex();
            buffer.pos(x2, y1, z).color(r, g, b, a).endVertex();
            buffer.pos(x2, y2, z).color(r, g, b, a).endVertex();
        }

        tessellator.draw();
    }

    private void renderFilledBox(AxisAlignedBB box, float r, float g, float b, float a) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        float x1 = (float) box.minX;
        float y1 = (float) box.minY;
        float z1 = (float) box.minZ;
        float x2 = (float) box.maxX;
        float y2 = (float) box.maxY;
        float z2 = (float) box.maxZ;

        // Front face (z1) - both sides
        buffer.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.pos(x1, y2, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y1, z1).color(r, g, b, a).endVertex();

        buffer.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y1, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z1).color(r, g, b, a).endVertex();
        buffer.pos(x1, y2, z1).color(r, g, b, a).endVertex();

        // Back face (z2) - both sides
        buffer.pos(x1, y1, z2).color(r, g, b, a).endVertex();
        buffer.pos(x2, y1, z2).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z2).color(r, g, b, a).endVertex();
        buffer.pos(x1, y2, z2).color(r, g, b, a).endVertex();

        buffer.pos(x1, y1, z2).color(r, g, b, a).endVertex();
        buffer.pos(x1, y2, z2).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z2).color(r, g, b, a).endVertex();
        buffer.pos(x2, y1, z2).color(r, g, b, a).endVertex();

        // Bottom face (y1) - both sides
        buffer.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y1, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y1, z2).color(r, g, b, a).endVertex();
        buffer.pos(x1, y1, z2).color(r, g, b, a).endVertex();

        buffer.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.pos(x1, y1, z2).color(r, g, b, a).endVertex();
        buffer.pos(x2, y1, z2).color(r, g, b, a).endVertex();
        buffer.pos(x2, y1, z1).color(r, g, b, a).endVertex();

        // Top face (y2) - both sides
        buffer.pos(x1, y2, z1).color(r, g, b, a).endVertex();
        buffer.pos(x1, y2, z2).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z2).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z1).color(r, g, b, a).endVertex();

        buffer.pos(x1, y2, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z2).color(r, g, b, a).endVertex();
        buffer.pos(x1, y2, z2).color(r, g, b, a).endVertex();

        // Left face (x1) - both sides
        buffer.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.pos(x1, y1, z2).color(r, g, b, a).endVertex();
        buffer.pos(x1, y2, z2).color(r, g, b, a).endVertex();
        buffer.pos(x1, y2, z1).color(r, g, b, a).endVertex();

        buffer.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.pos(x1, y2, z1).color(r, g, b, a).endVertex();
        buffer.pos(x1, y2, z2).color(r, g, b, a).endVertex();
        buffer.pos(x1, y1, z2).color(r, g, b, a).endVertex();

        // Right face (x2) - both sides
        buffer.pos(x2, y1, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z2).color(r, g, b, a).endVertex();
        buffer.pos(x2, y1, z2).color(r, g, b, a).endVertex();

        buffer.pos(x2, y1, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y1, z2).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z2).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z1).color(r, g, b, a).endVertex();

        tessellator.draw();
    }

    // ============================================================
    // Filled (translucent) box rendering
    // ============================================================

    private boolean hasFirstPosition(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey("FirstPos");
    }

    // ============================================================
    // LinkingTool NBT helpers (accessing private state)
    // ============================================================

    private BlockPos getFirstPosition(ItemStack stack) {
        return BlockPos.fromLong(stack.getTagCompound().getLong("FirstPos"));
    }

    private static class EdgeKey {
        final int x1;
        final int y1;
        final int z1;
        final int x2;
        final int y2;
        final int z2;

        EdgeKey(int ax, int ay, int az, int bx, int by, int bz) {
            // Normalize order so A->B and B->A map to the same key.
            if (isBefore(ax, ay, az, bx, by, bz)) {
                this.x1 = ax;
                this.y1 = ay;
                this.z1 = az;
                this.x2 = bx;
                this.y2 = by;
                this.z2 = bz;
            } else {
                this.x1 = bx;
                this.y1 = by;
                this.z1 = bz;
                this.x2 = ax;
                this.y2 = ay;
                this.z2 = az;
            }
        }

        private static boolean isBefore(int ax, int ay, int az, int bx, int by, int bz) {
            if (ax != bx) return ax < bx;
            if (ay != by) return ay < by;
            return az <= bz;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof EdgeKey)) return false;
            EdgeKey other = (EdgeKey) obj;
            return x1 == other.x1 && y1 == other.y1 && z1 == other.z1 && x2 == other.x2 && y2 == other.y2 && z2 == other.z2;
        }

        @Override
        public int hashCode() {
            int result = x1;
            result = 31 * result + y1;
            result = 31 * result + z1;
            result = 31 * result + x2;
            result = 31 * result + y2;
            result = 31 * result + z2;
            return result;
        }
    }
}
