package com.xinyihl.functionalstoragelegacy.client.model;

import com.xinyihl.functionalstoragelegacy.common.block.FramedDrawerBlock;
import com.xinyihl.functionalstoragelegacy.common.storage.FramedDrawerStyle;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BakedQuadRetextured;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.property.IExtendedBlockState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Retextures the framed model's marked quads from tile or item style NBT. */
public final class FramedDrawerBakedModel implements IBakedModel {

    private static final String SIDE_MARKER = "functionalstoragelegacy:blocks/framed_side";
    private static final String FRONT_MARKER = "functionalstoragelegacy:blocks/framed_front_";

    private final IBakedModel parent;
    private final FramedDrawerStyle itemStyle;
    private final FramedDrawerBakedModel root;
    private final ItemOverrideList overrides;
    private final Map<String, IBakedModel> itemModels;
    private final Map<String, PartSprites> textureCache;

    public FramedDrawerBakedModel(IBakedModel parent) {
        this.parent = parent;
        this.itemStyle = FramedDrawerStyle.EMPTY;
        this.root = this;
        this.itemModels = new ConcurrentHashMap<>();
        this.textureCache = new ConcurrentHashMap<>();
        this.overrides = new FramedOverrides(this);
    }

    private FramedDrawerBakedModel(IBakedModel parent, FramedDrawerStyle itemStyle,
                                   FramedDrawerBakedModel root) {
        this.parent = parent;
        this.itemStyle = itemStyle;
        this.root = root;
        this.itemModels = root.itemModels;
        this.textureCache = root.textureCache;
        this.overrides = root.overrides;
    }

    @Nonnull
    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state,
                                    @Nullable EnumFacing side, long rand) {
        List<BakedQuad> original = parent.getQuads(state, side, rand);
        FramedDrawerStyle style = styleFrom(state);
        if (!style.isConfigured() || original.isEmpty()) {
            return original;
        }

        PartSprites sprites = textureCache.computeIfAbsent(
                style.getCacheKey(), key -> PartSprites.from(style));
        List<BakedQuad> retextured = new ArrayList<>(original.size());
        for (BakedQuad quad : original) {
            TextureAtlasSprite replacement = replacementFor(quad, sprites);
            retextured.add(replacement == null
                    ? quad : new BakedQuadRetextured(quad, replacement));
        }
        return retextured;
    }

    @Override
    public boolean isAmbientOcclusion() {
        return parent.isAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return parent.isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return parent.isBuiltInRenderer();
    }

    @Nonnull
    @Override
    public TextureAtlasSprite getParticleTexture() {
        if (itemStyle.isConfigured()) {
            PartSprites sprites = textureCache.computeIfAbsent(
                    itemStyle.getCacheKey(), key -> PartSprites.from(itemStyle));
            if (sprites.exterior != null) {
                return sprites.exterior;
            }
        }
        return parent.getParticleTexture();
    }

    @Nonnull
    @Override
    public ItemCameraTransforms getItemCameraTransforms() {
        return parent.getItemCameraTransforms();
    }

    @Nonnull
    @Override
    public ItemOverrideList getOverrides() {
        return overrides;
    }

    private FramedDrawerStyle styleFrom(@Nullable IBlockState state) {
        if (state instanceof IExtendedBlockState) {
            FramedDrawerStyle style = ((IExtendedBlockState) state)
                    .getValue(FramedDrawerBlock.STYLE);
            if (style != null) {
                return style;
            }
        }
        return itemStyle;
    }

    @Nullable
    private static TextureAtlasSprite replacementFor(BakedQuad quad, PartSprites sprites) {
        String icon = quad.getSprite().getIconName();
        if (icon.startsWith(SIDE_MARKER)) {
            return sprites.exterior;
        }
        if (icon.startsWith(FRONT_MARKER)) {
            return isDividerQuad(quad) ? sprites.divider : sprites.front;
        }
        return null;
    }

    /** Divider UVs occupy the 7..9 center strip in the source 16x16 drawer model. */
    private static boolean isDividerQuad(BakedQuad quad) {
        VertexFormat format = quad.getFormat();
        if (!format.hasUvOffset(0)) {
            return false;
        }
        int stride = format.getIntegerSize();
        int uvOffset = format.getUvOffsetById(0) / 4;
        int[] data = quad.getVertexData();
        float minU = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        TextureAtlasSprite sprite = quad.getSprite();
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride + uvOffset;
            float atlasU = Float.intBitsToFloat(data[offset]);
            float atlasV = Float.intBitsToFloat(data[offset + 1]);
            float u = sprite.getUnInterpolatedU(atlasU);
            float v = sprite.getUnInterpolatedV(atlasV);
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }
        return isCenterStrip(minU, maxU) || isCenterStrip(minV, maxV);
    }

    private static boolean isCenterStrip(float min, float max) {
        return Math.abs(min - 7F) < 0.1F && Math.abs(max - 9F) < 0.1F;
    }

    @Nullable
    private static TextureAtlasSprite spriteFor(ItemStack material) {
        if (material.isEmpty() || !(material.getItem() instanceof ItemBlock)) {
            return null;
        }
        Block block = ((ItemBlock) material.getItem()).getBlock();
        try {
            IBlockState state = block.getStateFromMeta(material.getMetadata());
            return Minecraft.getMinecraft().getBlockRendererDispatcher()
                    .getBlockModelShapes().getTexture(state);
        } catch (RuntimeException ignored) {
            return Minecraft.getMinecraft().getBlockRendererDispatcher()
                    .getBlockModelShapes().getTexture(block.getDefaultState());
        }
    }

    private IBakedModel itemModel(FramedDrawerStyle style) {
        if (!style.isConfigured()) {
            return this;
        }
        return itemModels.computeIfAbsent(style.getCacheKey(),
                key -> new FramedDrawerBakedModel(parent, style, root));
    }

    private static final class PartSprites {
        private final TextureAtlasSprite exterior;
        private final TextureAtlasSprite front;
        private final TextureAtlasSprite divider;

        private PartSprites(TextureAtlasSprite exterior, TextureAtlasSprite front,
                            TextureAtlasSprite divider) {
            this.exterior = exterior;
            this.front = front;
            this.divider = divider;
        }

        private static PartSprites from(FramedDrawerStyle style) {
            return new PartSprites(
                    spriteFor(style.getExterior()),
                    spriteFor(style.getFront()),
                    spriteFor(style.getDivider()));
        }
    }

    private static final class FramedOverrides extends ItemOverrideList {
        private final FramedDrawerBakedModel model;

        private FramedOverrides(FramedDrawerBakedModel model) {
            super(Collections.<ItemOverride>emptyList());
            this.model = model;
        }

        @Nonnull
        @Override
        public IBakedModel handleItemState(@Nonnull IBakedModel originalModel,
                                           @Nonnull ItemStack stack,
                                           @Nullable World world,
                                           @Nullable EntityLivingBase entity) {
            return model.itemModel(FramedDrawerStyle.fromDrawerStack(stack));
        }
    }
}
