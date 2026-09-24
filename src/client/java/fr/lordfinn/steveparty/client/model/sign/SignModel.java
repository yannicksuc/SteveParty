package fr.lordfinn.steveparty.client.model.sign;

import fr.lordfinn.steveparty.blocks.custom.signs.AbstractStencilSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.SignShapes;
import fr.lordfinn.steveparty.blocks.custom.signs.StencilCanvasBlockEntity;
import fr.lordfinn.steveparty.components.ModComponents;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Chunk-mesh model of a 16-way stencil sign. Vanilla block models can only turn by 90°: this one emits the quads
 * of its JSON model(s) turned by the sign's {@code rotation} (22.5° steps, same turn the traffic sign always had),
 * and re-textured with the sign's material (see {@link MaterialSprites}). Items get the same model, unturned, made
 * of the material written on the stack.
 * <p>
 * The JSON models are plain Blockbench models: they use oak / stone textures as placeholders, swapped for the
 * material's own textures here (see the subclasses).
 */
public abstract class SignModel implements BakedModel {
    protected final BakedModel base;
    private static RenderMaterial material;

    protected SignModel(BakedModel base) {
        this.base = base;
    }

    /** What to draw: material and plate colour (null when not set), stencil shape (cut-out panel). */
    public record Look(@Nullable Identifier material, @Nullable DyeColor plateColor, @Nullable byte[] shape) {
        static final Look NONE = new Look(null, null, null);
    }

    /** Emits the sign's quads through {@code out}. {@code state} is the block's, or the item's block state. */
    protected abstract void emit(Output out, BlockState state, Look look, long seed, Supplier<Random> random);

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
        RenderMaterial material = material();
        if (material == null) return;
        Look look = world.getBlockEntityRenderData(pos) instanceof StencilCanvasBlockEntity.RenderData data
                ? new Look(data.material(), data.plateColor(), data.shape()) : Look.NONE;
        Quaternionf turn = state.contains(AbstractStencilSignBlock.ROTATION)
                ? new Quaternionf().rotationY((float) Math.toRadians(SignShapes.angleDegrees(state.get(AbstractStencilSignBlock.ROTATION))))
                : null;
        emit(new Output(context.getEmitter(), material, turn), state, look, pos.asLong(), randomSupplier);
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<Random> randomSupplier, RenderContext context) {
        RenderMaterial material = material();
        if (material == null || !(stack.getItem() instanceof BlockItem blockItem)) return;
        Block block = blockItem.getBlock();
        BlockState state = stack.getOrDefault(DataComponentTypes.BLOCK_STATE, BlockStateComponent.DEFAULT).applyToState(block.getDefaultState());
        Look look = new Look(stack.get(ModComponents.SIGN_MATERIAL), stack.get(DataComponentTypes.BASE_COLOR), null);
        emit(new Output(context.getEmitter(), material, null), state, look, 0L, randomSupplier);
    }

    private static @Nullable RenderMaterial material() {
        if (material == null) {
            Renderer renderer = RendererAccess.INSTANCE.getRenderer();
            if (renderer == null) return null;
            // Flat light like the traffic sign always had: ambient occlusion is wrong on turned faces
            material = renderer.materialFinder().blendMode(BlendMode.CUTOUT).ambientOcclusion(TriState.FALSE).find();
        }
        return material;
    }

    /** Writes turned, re-textured quads. */
    protected static final class Output {
        private final QuadEmitter emitter;
        private final RenderMaterial material;
        private final @Nullable Quaternionf turn;
        private final Vector3f vector = new Vector3f();

        Output(QuadEmitter emitter, RenderMaterial material, @Nullable Quaternionf turn) {
            this.emitter = emitter;
            this.material = material;
            this.turn = turn;
        }

        /**
         * Emits every quad of {@code model}.
         *
         * @param retexture sprite to draw instead of each sprite of the model (identity: none)
         * @param tint      ARGB colour of the tinted faces ({@code tintindex} in the JSON), 0 to leave them
         */
        void model(BakedModel model, BlockState state, Supplier<Random> random, Function<Sprite, Sprite> retexture, int tint) {
            for (Direction face : Direction.values()) quads(model.getQuads(state, face, random.get()), retexture, tint);
            quads(model.getQuads(state, null, random.get()), retexture, tint);
        }

        private void quads(List<BakedQuad> quads, Function<Sprite, Sprite> retexture, int tint) {
            for (BakedQuad quad : quads) {
                emitter.fromVanilla(quad, material, null);
                Sprite from = quad.getSprite();
                Sprite to = retexture.apply(from);
                if (to != null && to != from) {
                    for (int i = 0; i < 4; i++) {
                        emitter.uv(i, remap(emitter.u(i), from.getMinU(), from.getMaxU(), to.getMinU(), to.getMaxU()),
                                remap(emitter.v(i), from.getMinV(), from.getMaxV(), to.getMinV(), to.getMaxV()));
                    }
                }
                if (tint != 0 && quad.hasColor()) {
                    for (int i = 0; i < 4; i++) emitter.color(i, tint);
                }
                emitter.colorIndex(-1);
                emit();
            }
        }

        /**
         * Emits a quad given in model pixels: corners top left, bottom left, bottom right, top right as seen from
         * outside the face, textured with the (u0, v0)-(u1, v1) part of {@code sprite} (fractions of the sprite).
         */
        void quad(Vector3f topLeft, Vector3f bottomLeft, Vector3f bottomRight, Vector3f topRight, Vector3f normal,
                  Sprite sprite, float u0, float v0, float u1, float v1) {
            emitter.material(material);
            emitter.cullFace(null);
            emitter.nominalFace(null);
            emitter.colorIndex(-1);
            emitter.tag(0);
            corner(0, topLeft, sprite, u0, v0, normal);
            corner(1, bottomLeft, sprite, u0, v1, normal);
            corner(2, bottomRight, sprite, u1, v1, normal);
            corner(3, topRight, sprite, u1, v0, normal);
            emit();
        }

        private void corner(int i, Vector3f pixel, Sprite sprite, float u, float v, Vector3f normal) {
            emitter.pos(i, pixel.x / 16F, pixel.y / 16F, pixel.z / 16F);
            emitter.uv(i, sprite.getMinU() + (sprite.getMaxU() - sprite.getMinU()) * u,
                    sprite.getMinV() + (sprite.getMaxV() - sprite.getMinV()) * v);
            emitter.color(i, -1);
            emitter.normal(i, normal.x, normal.y, normal.z);
            emitter.lightmap(i, 0);
        }

        /** Turns the quad being emitted around the block's vertical axis, then emits it. */
        private void emit() {
            if (turn != null) {
                for (int i = 0; i < 4; i++) {
                    emitter.copyPos(i, vector);
                    vector.sub(0.5F, 0, 0.5F);
                    turn.transform(vector);
                    emitter.pos(i, vector.x + 0.5F, vector.y, vector.z + 0.5F);
                    if (emitter.hasNormal(i)) {
                        emitter.copyNormal(i, vector);
                        turn.transform(vector);
                        emitter.normal(i, vector.x, vector.y, vector.z);
                    }
                }
            }
            emitter.emit();
        }

        private static float remap(float value, float fromMin, float fromMax, float toMin, float toMax) {
            return toMin + (value - fromMin) / (fromMax - fromMin) * (toMax - toMin);
        }
    }

    // Everything else (particles, item transforms, vanilla fallback) comes from the JSON model

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
