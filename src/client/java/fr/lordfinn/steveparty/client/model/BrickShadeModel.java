package fr.lordfinn.steveparty.client.model;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Bricks of the polished terracotta and concrete: their textures cover {@value #SPAN} x {@value #SPAN} blocks (every
 * brick with its own shade, painted in), and each block shows its own part of one, picked from where it is. Each zone
 * of {@value #SPAN} x {@value #SPAN} blocks draws one of a few such textures at random (from its position), so a wall
 * never repeats; the bricks straddling two zones are the same in all of them, so the zones join up perfectly.
 * The geometry stays the vanilla one (one quad per face, only the texture coordinates are moved): it costs next to
 * nothing and can show no crack.
 */
public class BrickShadeModel implements BakedModel {
    /** Blocks covered by the texture on each side (it is 16 x SPAN pixels wide). */
    public static final int SPAN = 8;
    // Texture right / down of each face, following the vanilla block face UVs (see ConnectedPlasticModel)
    private static final Direction[] TEXTURE_RIGHT = new Direction[6];
    private static final Direction[] TEXTURE_DOWN = new Direction[6];

    static {
        set(Direction.UP, Direction.EAST, Direction.SOUTH);
        set(Direction.DOWN, Direction.EAST, Direction.NORTH);
        set(Direction.NORTH, Direction.WEST, Direction.DOWN);
        set(Direction.SOUTH, Direction.EAST, Direction.DOWN);
        set(Direction.WEST, Direction.SOUTH, Direction.DOWN);
        set(Direction.EAST, Direction.NORTH, Direction.DOWN);
    }

    private static void set(Direction face, Direction right, Direction down) {
        TEXTURE_RIGHT[face.ordinal()] = right;
        TEXTURE_DOWN[face.ordinal()] = down;
    }

    private final BakedModel base;
    /** The textures a zone may draw (the first is the block's own). */
    private final Sprite[] variants;

    public BrickShadeModel(BakedModel base, Sprite[] variants) {
        this.base = base;
        this.variants = variants;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
        context.pushTransform(quad -> showPart(quad, pos));
        base.emitBlockQuads(world, state, pos, randomSupplier, context);
        context.popTransform();
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<Random> randomSupplier, RenderContext context) {
        context.pushTransform(quad -> showPart(quad, BlockPos.ORIGIN));
        base.emitItemQuads(stack, randomSupplier, context);
        context.popTransform();
    }

    /** Moves the texture coordinates of a face onto the part its block shows, in the texture its zone drew. */
    private boolean showPart(MutableQuadView quad, BlockPos pos) {
        Direction face = quad.nominalFace();
        if (face == null) face = quad.lightFace();
        int blockU = dot(pos, TEXTURE_RIGHT[face.ordinal()]), blockV = dot(pos, TEXTURE_DOWN[face.ordinal()]);
        Sprite from = variants[0];
        Sprite to = variants[zoneVariant(Math.floorDiv(blockU, SPAN), Math.floorDiv(blockV, SPAN), face, dot(pos, face))];
        int partU = Math.floorMod(blockU, SPAN), partV = Math.floorMod(blockV, SPAN);
        for (int i = 0; i < 4; i++) {
            float u = (quad.u(i) - from.getMinU()) / (from.getMaxU() - from.getMinU());
            float v = (quad.v(i) - from.getMinV()) / (from.getMaxV() - from.getMinV());
            quad.uv(i, to.getMinU() + (partU + u) / SPAN * (to.getMaxU() - to.getMinU()),
                    to.getMinV() + (partV + v) / SPAN * (to.getMaxV() - to.getMinV()));
        }
        return true;
    }

    /** @return the texture drawn by a zone: random-looking, but always the same for the same zone. */
    private int zoneVariant(int zoneU, int zoneV, Direction face, int depth) {
        long z = zoneU * 0x9E3779B97F4A7C15L ^ zoneV * 0xC2B2AE3D27D4EB4FL ^ (depth * 31L + face.getAxis().ordinal()) * 0x165667B19E3779F9L;
        z = (z ^ (z >>> 29)) * 0xBF58476D1CE4E5B9L;
        z ^= z >>> 32;
        return (int) Math.floorMod(z, (long) variants.length);
    }

    private static int dot(BlockPos pos, Direction direction) {
        return pos.getX() * direction.getOffsetX() + pos.getY() * direction.getOffsetY() + pos.getZ() * direction.getOffsetZ();
    }

    // Everything else (particles, fallback) uses the whole texture

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, Random random) {
        return base.getQuads(state, face, random);
    }

    @Override public boolean useAmbientOcclusion() { return base.useAmbientOcclusion(); }
    @Override public boolean hasDepth() { return base.hasDepth(); }
    @Override public boolean isSideLit() { return base.isSideLit(); }
    @Override public boolean isBuiltin() { return false; }
    @Override public Sprite getParticleSprite() { return base.getParticleSprite(); }
    @Override public ModelTransformation getTransformation() { return base.getTransformation(); }
    @Override public ModelOverrideList getOverrides() { return base.getOverrides(); }
}
