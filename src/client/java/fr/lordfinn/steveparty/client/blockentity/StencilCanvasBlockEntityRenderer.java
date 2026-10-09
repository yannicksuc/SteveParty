package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.EaselSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.AbstractStencilSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.PlasticRoadSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.RockSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.StencilCanvasBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.signs.StencilPaintBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.WoodenPanelBlock;
import fr.lordfinn.steveparty.client.hammer.StencilHammerStrikes;
import fr.lordfinn.steveparty.client.utils.StencilResourceManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import fr.lordfinn.steveparty.stencil.StencilShape;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * Draws the symbol of every stencil block: on its sign faces (see {@link SymbolLayouts}), turned with the sign, or
 * on the face a paint was sprayed on. The block itself is in the chunk mesh (see the stencil sign models), and so is
 * the engraving of the rock signs, dug in the stone.
 * <p>
 * Painted symbols take the dye's sign colour; engraved ones are a dark, see-through print of the shape. Brushing
 * fades them. Sprayed paint lets a little of its wall show through; a plastic plate's symbol stops at its outline.
 * Paint sprayed on a see-through block (glass, star fragments...) is solid and seen from both sides: through the
 * block, from behind, the symbol reads mirrored, like a sticker on a window.
 */
public class StencilCanvasBlockEntityRenderer<T extends StencilCanvasBlockEntity> implements BlockEntityRenderer<T> {
    /** Symbols stay visible as far as signs are usually seen (the default is 64 blocks). */
    private static final int RENDER_DISTANCE = 256;
    private static final int ENGRAVED_COLOR = 0x6A1E1A16;
    /** Opacity of the paint for each brush step (StencilCanvasBlockEntity#MAX_FADE + 1 steps). */
    private static final float[] FADE_ALPHA = {1.0F, 0.78F, 0.58F, 0.4F, 0.24F};
    /** Sprayed paint is a little see-through: the texture of the block it is on shows under it. */
    private static final float SPRAY_ALPHA = 0.86F;
    /** Stencil Hammer stamp: how far (blocks) the pattern spreads from the hit point, cells per quad side, soft edge. */
    private static final float REVEAL_RADIUS = 1.25F;
    private static final int REVEAL_GRID = 8;
    private static final float REVEAL_EDGE = 0.15F;

    public StencilCanvasBlockEntityRenderer(BlockEntityRendererFactory.Context ignoredCtx) {
    }

    /**
     * What drawing a symbol needs, kept on its block entity ({@link StencilCanvasBlockEntity#getRenderCache()}, dropped
     * whenever the symbol changes) instead of being worked out again every frame: the quads, the texture (valid for
     * one {@link StencilResourceManager#generation()}), and the transform of a sign (valid while its block state and
     * the post it rests against stay the same).
     */
    private static final class Cache {
        final BlockState state;
        final List<SymbolLayouts.SymbolQuad> quads;
        final byte[] shape;
        final StencilResourceManager.Kind kind;
        final boolean sign;
        /** Sprayed on a see-through block: opaque, and drawn on both sides. */
        final boolean seeThroughSupport;
        @Nullable Identifier texture;
        int textureGeneration;
        @Nullable BlockState transformPost;
        final Matrix4f transform = new Matrix4f();
        final Matrix3f normalTransform = new Matrix3f();
        boolean hasTransform;

        Cache(BlockState state, List<SymbolLayouts.SymbolQuad> quads, byte[] shape, StencilResourceManager.Kind kind, boolean sign,
              boolean seeThroughSupport) {
            this.state = state;
            this.quads = quads;
            this.shape = shape;
            this.kind = kind;
            this.sign = sign;
            this.seeThroughSupport = seeThroughSupport;
        }
    }

    @Override
    public void render(T entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (!entity.hasShape()) return;
        BlockState state = entity.getCachedState();
        Cache cache = entity.getRenderCache() instanceof Cache cached && cached.state == state ? cached : null;
        if (cache == null) {
            cache = createCache(entity, state);
            entity.setRenderCache(cache);
        }
        // Nothing drawn here (a rock engraving is dug in the chunk mesh, a cut-out panel has no painted face)
        if (cache.quads.isEmpty()) return;
        if (cache.texture == null || cache.textureGeneration != StencilResourceManager.generation()) {
            cache.texture = StencilResourceManager.getTexture(cache.shape, cache.kind);
            cache.textureGeneration = StencilResourceManager.generation();
        }
        if (cache.texture == null) return;

        // A Stencil Hammer strike: the previous symbol stays until the head lands, then the new one spreads from
        // the hit point over it
        StencilHammerStrikes.Reveal reveal = StencilHammerStrikes.reveal(entity.getPos());
        World world = entity.getWorld();
        float since = reveal == null || !(world instanceof ClientWorld clientWorld) ? Float.MAX_VALUE : reveal.sinceImpact(clientWorld, tickDelta);
        if (reveal != null && since < StencilHammerStrikes.REVEAL_TICKS && reveal.oldCache != cache) {
            if (reveal.oldCache instanceof Cache old && old.state == state && old.texture != null && !old.quads.isEmpty()) {
                draw(matrices, vertexConsumers, entity, state, old, reveal.oldColor, reveal.oldFade, reveal.oldGlowing, light, null, 0);
            }
            if (since < 0) return;
            Vector3f hit = reveal.hit.subtract(Vec3d.of(entity.getPos())).toVector3f();
            float progress = since / StencilHammerStrikes.REVEAL_TICKS;
            float radius = REVEAL_RADIUS * progress * (2 - progress); // ease out: a splash spreading, slowing down
            draw(matrices, vertexConsumers, entity, state, cache, entity.getColor(), entity.getFade(), entity.isGlowing(), light, hit, radius);
            return;
        }
        draw(matrices, vertexConsumers, entity, state, cache, entity.getColor(), entity.getFade(), entity.isGlowing(), light, null, 0);
    }

    /**
     * Draws a symbol; with {@code revealFrom} (block local) only the part within {@code radius} of it, its edge
     * fading, in {@link #REVEAL_GRID}² cells per quad.
     */
    private void draw(MatrixStack matrices, VertexConsumerProvider vertexConsumers, T entity, BlockState state, Cache cache,
                      @Nullable DyeColor color, int fade, boolean glowing, int light, @Nullable Vector3f revealFrom, float radius) {
        int argb = color == null ? ENGRAVED_COLOR : 0xFF000000 | color.getSignColor();
        float alpha = FADE_ALPHA[Math.clamp(fade, 0, FADE_ALPHA.length - 1)]
                * (state.getBlock() instanceof StencilPaintBlock && !cache.seeThroughSupport ? SPRAY_ALPHA : 1.0F);
        argb = ColorHelper.Argb.withAlpha(Math.round((argb >>> 24) * alpha), argb);
        int symbolLight = glowing && color != null ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(cache.texture));

        matrices.push();
        if (cache.sign) {
            // Where the board is drawn: turned, against its post or wall, on its floor or ceiling
            updateTransform(cache, entity, state);
            matrices.peek().getPositionMatrix().mul(cache.transform);
            matrices.peek().getNormalMatrix().mul(cache.normalTransform);
        }
        for (SymbolLayouts.SymbolQuad quad : cache.quads) {
            if (revealFrom == null) drawQuad(matrices.peek(), consumer, quad, argb, symbolLight, false);
            else drawRevealed(matrices.peek(), consumer, quad, cache, argb, symbolLight, revealFrom, radius, false);
            if (!cache.seeThroughSupport) continue;
            // Its back, seen through the block it is sprayed on
            if (revealFrom == null) drawQuad(matrices.peek(), consumer, quad, argb, symbolLight, true);
            else drawRevealed(matrices.peek(), consumer, quad, cache, argb, symbolLight, revealFrom, radius, true);
        }
        matrices.pop();
    }

    /** The part of a quad already stamped: cells within {@code radius} of the hit point, the edge fading in. */
    private static void drawRevealed(MatrixStack.Entry entry, VertexConsumer consumer, SymbolLayouts.SymbolQuad quad, Cache cache,
                                     int argb, int light, Vector3f from, float radius, boolean back) {
        Vector3f center = new Vector3f();
        for (int i = 0; i < REVEAL_GRID; i++) {
            float u0 = (float) i / REVEAL_GRID, u1 = (float) (i + 1) / REVEAL_GRID;
            for (int j = 0; j < REVEAL_GRID; j++) {
                float v0 = (float) j / REVEAL_GRID, v1 = (float) (j + 1) / REVEAL_GRID;
                lerp(quad, (u0 + u1) / 2, (v0 + v1) / 2, center).div(16F);
                if (cache.sign) cache.transform.transformPosition(center);
                float inside = MathHelper.clamp((radius - center.distance(from)) / REVEAL_EDGE, 0, 1);
                if (inside <= 0) continue;
                int cellArgb = ColorHelper.Argb.withAlpha(Math.round((argb >>> 24) * inside), argb);
                Matrix4f matrix = entry.getPositionMatrix();
                Vector3f n = back ? new Vector3f(quad.normal()).negate() : quad.normal();
                // The back face winds the other way round (same corners, so same place on the texture)
                float ua = back ? u1 : u0, ub = back ? u0 : u1;
                vertex(consumer, matrix, entry, lerp(quad, ua, v0, new Vector3f()), ua, v0, cellArgb, light, n);
                vertex(consumer, matrix, entry, lerp(quad, ua, v1, new Vector3f()), ua, v1, cellArgb, light, n);
                vertex(consumer, matrix, entry, lerp(quad, ub, v1, new Vector3f()), ub, v1, cellArgb, light, n);
                vertex(consumer, matrix, entry, lerp(quad, ub, v0, new Vector3f()), ub, v0, cellArgb, light, n);
            }
        }
    }

    /** The point of a quad (in pixels) at texture coordinates (u, v). */
    private static Vector3f lerp(SymbolLayouts.SymbolQuad quad, float u, float v, Vector3f out) {
        Vector3f top = new Vector3f(quad.topLeft()).lerp(quad.topRight(), u);
        Vector3f bottom = new Vector3f(quad.bottomLeft()).lerp(quad.bottomRight(), u);
        return out.set(top).lerp(bottom, v);
    }

    private static Cache createCache(StencilCanvasBlockEntity entity, BlockState state) {
        List<SymbolLayouts.SymbolQuad> quads = List.of();
        boolean sign = false;
        boolean seeThroughSupport = false;
        if (state.getBlock() instanceof StencilPaintBlock) {
            quads = List.of(SymbolLayouts.forPaint(state));
            World world = entity.getWorld();
            BlockPos support = entity.getPos().offset(StencilPaintBlock.getFacing(state).getOpposite());
            seeThroughSupport = world != null && !world.getBlockState(support).isOpaqueFullCube(world, support);
        } else if (state.getBlock() instanceof AbstractStencilSignBlock && !(state.getBlock() instanceof RockSignBlock)) {
            quads = SymbolLayouts.forSign(state);
            sign = true;
        }
        byte[] shape = quads.isEmpty() ? null : entity.getShape();
        if (shape == null) return new Cache(state, List.of(), StencilShape.blank(), StencilResourceManager.Kind.FLAT, false, false);
        if (state.getBlock() instanceof PlasticRoadSignBlock) shape = masked(shape, state.get(PlasticRoadSignBlock.PLATE).mask());
        // Wooden signs keep their painted wood grain; everything else is a plain print
        boolean woodGrain = entity.getColor() != null && (state.getBlock() instanceof EaselSignBlock || state.getBlock() instanceof WoodenPanelBlock);
        return new Cache(state, quads, shape, woodGrain ? StencilResourceManager.Kind.WOOD : StencilResourceManager.Kind.FLAT, sign,
                seeThroughSupport);
    }

    /** A sign is drawn where its model is, which depends on its block state and on the post it rests against. */
    private static void updateTransform(Cache cache, StencilCanvasBlockEntity entity, BlockState state) {
        World world = entity.getWorld();
        BlockPos pos = entity.getPos();
        Direction hung = AbstractStencilSignBlock.hungFacing(state);
        BlockState post = world == null ? null : world.getBlockState(hung != null ? pos.offset(hung.getOpposite()) : pos.down());
        if (cache.hasTransform && cache.transformPost == post) return;
        cache.transform.set(((AbstractStencilSignBlock) state.getBlock()).modelTransform(world, pos, state));
        cache.normalTransform.set(cache.transform);
        cache.transformPost = post;
        cache.hasTransform = true;
    }

    private static byte[] masked(byte[] shape, byte[] mask) {
        for (int i = 0; i < shape.length; i++) shape[i] = (byte) (shape[i] & mask[i]);
        return shape;
    }

    /** Draws a quad given in pixels; {@code back}: its back face (wound the other way, facing the other way). */
    private static void drawQuad(MatrixStack.Entry entry, VertexConsumer consumer, SymbolLayouts.SymbolQuad quad, int argb, int light,
                                 boolean back) {
        Matrix4f matrix = entry.getPositionMatrix();
        if (back) {
            Vector3f n = new Vector3f(quad.normal()).negate();
            vertex(consumer, matrix, entry, quad.topRight(), 1, 0, argb, light, n);
            vertex(consumer, matrix, entry, quad.bottomRight(), 1, 1, argb, light, n);
            vertex(consumer, matrix, entry, quad.bottomLeft(), 0, 1, argb, light, n);
            vertex(consumer, matrix, entry, quad.topLeft(), 0, 0, argb, light, n);
            return;
        }
        Vector3f n = quad.normal();
        vertex(consumer, matrix, entry, quad.topLeft(), 0, 0, argb, light, n);
        vertex(consumer, matrix, entry, quad.bottomLeft(), 0, 1, argb, light, n);
        vertex(consumer, matrix, entry, quad.bottomRight(), 1, 1, argb, light, n);
        vertex(consumer, matrix, entry, quad.topRight(), 1, 0, argb, light, n);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, MatrixStack.Entry entry, Vector3f pixel, float u, float v,
                               int argb, int light, Vector3f normal) {
        consumer.vertex(matrix, pixel.x / 16F, pixel.y / 16F, pixel.z / 16F)
                .color(argb)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, normal.x, normal.y, normal.z);
    }

    /** A hung sign is drawn one block behind its own block, around the post it hangs on. */
    @Override
    public boolean rendersOutsideBoundingBox(T entity) {
        return AbstractStencilSignBlock.hungFacing(entity.getCachedState()) != null;
    }

    @Override
    public int getRenderDistance() {
        return RENDER_DISTANCE;
    }
}
