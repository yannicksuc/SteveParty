package fr.lordfinn.steveparty.client.model.sign;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.signs.AbstractStencilSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.SignMaterial;
import fr.lordfinn.steveparty.blocks.custom.signs.SignPosts;
import fr.lordfinn.steveparty.blocks.custom.signs.SignShapes;
import fr.lordfinn.steveparty.blocks.custom.signs.StencilCanvasBlockEntity;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.StencilCanvasComponent;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Chunk-mesh model of a 16-way stencil sign. Vanilla block models can only turn by 90°: this one emits the quads
 * of its JSON model(s) turned by the sign's {@code rotation} (22.5° steps, same turn the traffic sign always had),
 * and re-textured with the sign's material (see {@link MaterialSprites}). Signs standing on a post also draw the
 * post of the fence below them, never turned; signs hung on the side of a post are drawn around that post, one block
 * behind. Items get the same model, unturned, made of the material written on the stack.
 * <p>
 * The JSON models are plain Blockbench models: they use oak / stone textures as placeholders, swapped for the
 * material's own textures here (see the subclasses).
 */
public abstract class SignModel implements BakedModel {
    protected final BakedModel base;
    private static RenderMaterial solid, emissive, unshaded;

    protected SignModel(BakedModel base) {
        this.base = base;
    }

    /**
     * What to draw: material and plate colour, stencil shape / paint / glow / fade (cut-out panel, rock engraving),
     * the post under the sign (null: none) and how far back its board goes to rest against its post (pixels, see
     * {@link AbstractStencilSignBlock#boardShift}).
     */
    public record Look(@Nullable Identifier material, @Nullable DyeColor plateColor, @Nullable byte[] shape,
                       @Nullable DyeColor color, boolean glowing, int fade, @Nullable BlockState post, float boardShift) {
        /** @return {@code out}, moved back so that the board rests against its post. */
        Output board(Output out) {
            return boardShift == 0 ? out : out.with(new Matrix4f().translate(0, 0, boardShift / 16F));
        }
    }

    /** Emits the sign's quads through {@code out}. {@code state} is the block's, or the item's block state. */
    protected abstract void emit(Output out, BlockState state, Look look, long seed, Supplier<Random> random);

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
        if (!materials()) return;
        Direction hung = AbstractStencilSignBlock.hungFacing(state);
        BlockState below = world.getBlockState(pos.down());
        // A hung sign is drawn around the real post behind it: no post of its own
        BlockState post = hung == null && SignPosts.isPost(below) ? below.getBlock().getDefaultState() : null;
        float shift = state.getBlock() instanceof AbstractStencilSignBlock sign ? (float) sign.boardShift(world, pos, state) : 0;
        Look look = world.getBlockEntityRenderData(pos) instanceof StencilCanvasBlockEntity.RenderData data
                ? new Look(data.material(), data.plateColor(), data.shape(), data.color(), data.glowing(), data.fade(), post, shift)
                : new Look(null, null, null, DyeColor.WHITE, false, 0, post, shift);
        Matrix4f turn = new Matrix4f();
        if (hung != null) turn.translate(-hung.getOffsetX(), 0, -hung.getOffsetZ());
        if (state.contains(AbstractStencilSignBlock.ROTATION)) {
            float angle = (float) Math.toRadians(SignShapes.angleDegrees(state.get(AbstractStencilSignBlock.ROTATION)));
            turn.translate(0.5F, 0, 0.5F).rotateY(angle).translate(-0.5F, 0, -0.5F);
        }
        emit(new Output(context.getEmitter(), turn, 0), state, look, pos.asLong(), randomSupplier);
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<Random> randomSupplier, RenderContext context) {
        if (!materials() || !(stack.getItem() instanceof BlockItem blockItem)) return;
        Block block = blockItem.getBlock();
        BlockState state = stack.getOrDefault(DataComponentTypes.BLOCK_STATE, BlockStateComponent.DEFAULT).applyToState(block.getDefaultState());
        StencilCanvasComponent canvas = stack.get(ModComponents.STENCIL_CANVAS);
        Identifier material = stack.get(ModComponents.SIGN_MATERIAL);
        DyeColor plate = stack.get(DataComponentTypes.BASE_COLOR);
        BlockState post = itemPost(block, material, plate);
        float shift = 0;
        if (post != null && block instanceof AbstractStencilSignBlock sign && !Float.isNaN(sign.boardBack())) {
            // Items are drawn facing north: the model unturned
            double reach = SignPosts.reach(SignPosts.postShape(post), 0);
            if (!Double.isNaN(reach)) shift = (float) (8 - reach - sign.boardBack());
        }
        Look look = new Look(material, plate, canvas == null ? null : canvas.shapeArray(),
                canvas == null ? DyeColor.WHITE : canvas.color().orElse(null), canvas != null && canvas.glowing(),
                canvas == null ? 0 : canvas.fade(), post, shift);
        // Items show the fence under the sign, so that one sees what it stands on
        emit(new Output(context.getEmitter(), new Matrix4f(), 1), state, look, 0L, randomSupplier);
    }

    /** Post shown under a sign item: a fence of its wood, or a plastic fence of its colour. */
    private static @Nullable BlockState itemPost(Block sign, @Nullable Identifier material, @Nullable DyeColor plate) {
        if (sign == ModBlocks.PLASTIC_ROAD_SIGN) {
            return ModBlocks.PLASTIC_FENCES[(plate == null ? DyeColor.WHITE : plate).getId()].getDefaultState();
        }
        if (sign != ModBlocks.WOODEN_PANEL && sign != ModBlocks.WOODEN_CUTOUT_PANEL) return null;
        Identifier planks = SignMaterial.WOOD.resolve(material);
        String path = planks.getPath().endsWith("_planks") ? planks.getPath().substring(0, planks.getPath().length() - 7) : planks.getPath();
        return Registries.BLOCK.getOptionalValue(Identifier.of(planks.getNamespace(), path + "_fence"))
                .orElse(Blocks.OAK_FENCE).getDefaultState();
    }

    private static boolean materials() {
        if (solid == null) {
            Renderer renderer = RendererAccess.INSTANCE.getRenderer();
            if (renderer == null) return false;
            // Flat light like the traffic sign always had: ambient occlusion is wrong on turned faces
            solid = renderer.materialFinder().blendMode(BlendMode.CUTOUT).ambientOcclusion(TriState.FALSE).find();
            emissive = renderer.materialFinder().blendMode(BlendMode.CUTOUT).ambientOcclusion(TriState.FALSE).emissive(true).disableDiffuse(true).find();
            unshaded = renderer.materialFinder().blendMode(BlendMode.CUTOUT).ambientOcclusion(TriState.FALSE).disableDiffuse(true).find();
        }
        return true;
    }

    /** How a procedural quad is lit. */
    public enum Light { SHADED, UNSHADED, EMISSIVE }

    /** Writes transformed, re-textured quads (positions in block units, transformed by {@link #matrix}). */
    protected static final class Output {
        private final QuadEmitter emitter;
        private final Matrix4f matrix;
        private final Matrix3f normals;
        private final Vector3f vector = new Vector3f();
        /** How many blocks of fence are drawn below the sign's own block: 0 in the world (the real fence is there), 1 for items. */
        private final float postDrop;

        Output(QuadEmitter emitter, Matrix4f matrix, float postDrop) {
            this.emitter = emitter;
            this.matrix = matrix;
            this.normals = matrix.normal(new Matrix3f());
            this.postDrop = postDrop;
        }

        /** @return an output applying {@code local} (block units) before this output's transform. */
        Output with(Matrix4f local) {
            return new Output(emitter, new Matrix4f(matrix).mul(local), postDrop);
        }

        /**
         * Emits the model of the post (the fence below the sign), as it is, unturned: through the sign's own block,
         * and for items, the fence below it too.
         */
        void post(@Nullable BlockState post, Supplier<Random> random) {
            if (post == null) return;
            BakedModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel(post);
            for (int drop = 0; drop <= postDrop; drop++) {
                new Output(emitter, new Matrix4f().translate(0, -drop, 0), postDrop).model(model, post, random, Function.identity(), 0);
            }
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
                // Rebuilt from its corners alone, like the quads made here: QuadEmitter.fromVanilla also copies what
                // the renderer worked out for the unturned quad (the side it faces, its sprite, lighting flags), and
                // Sodium then left turned faces out when the sign was seen from afar, from the side or the back
                int[] data = quad.getVertexData();
                int stride = data.length / 4;
                Sprite from = quad.getSprite();
                Sprite to = retexture.apply(from);
                boolean swap = to != null && to != from;
                emitter.material(quad.hasShade() ? solid : unshaded);
                emitter.cullFace(null);
                emitter.nominalFace(null);
                emitter.colorIndex(-1);
                emitter.tag(0);
                Direction face = quad.getFace();
                for (int i = 0; i < 4; i++) {
                    int at = i * stride;
                    emitter.pos(i, Float.intBitsToFloat(data[at]), Float.intBitsToFloat(data[at + 1]), Float.intBitsToFloat(data[at + 2]));
                    float u = Float.intBitsToFloat(data[at + 4]), v = Float.intBitsToFloat(data[at + 5]);
                    if (swap) {
                        u = remap(u, from.getMinU(), from.getMaxU(), to.getMinU(), to.getMaxU());
                        v = remap(v, from.getMinV(), from.getMaxV(), to.getMinV(), to.getMaxV());
                    }
                    emitter.uv(i, u, v);
                    emitter.color(i, tint != 0 && quad.hasColor() ? tint : -1);
                    emitter.normal(i, face.getOffsetX(), face.getOffsetY(), face.getOffsetZ());
                    emitter.lightmap(i, 0);
                }
                emit();
            }
        }

        /**
         * Emits a quad given in model pixels: corners top left, bottom left, bottom right, top right as seen from
         * outside the face, textured with the (u0, v0)-(u1, v1) part of {@code sprite} (fractions of the sprite).
         */
        void quad(Vector3f topLeft, Vector3f bottomLeft, Vector3f bottomRight, Vector3f topRight, Vector3f normal,
                  Sprite sprite, float u0, float v0, float u1, float v1) {
            quad(topLeft, bottomLeft, bottomRight, topRight, normal, sprite, u0, v0, u1, v1, -1, Light.SHADED);
        }

        void quad(Vector3f topLeft, Vector3f bottomLeft, Vector3f bottomRight, Vector3f topRight, Vector3f normal,
                  Sprite sprite, float u0, float v0, float u1, float v1, int color, Light light) {
            emitter.material(switch (light) {
                case SHADED -> solid;
                case UNSHADED -> unshaded;
                case EMISSIVE -> emissive;
            });
            emitter.cullFace(null);
            emitter.nominalFace(null);
            emitter.colorIndex(-1);
            emitter.tag(0);
            corner(0, topLeft, sprite, u0, v0, normal, color);
            corner(1, bottomLeft, sprite, u0, v1, normal, color);
            corner(2, bottomRight, sprite, u1, v1, normal, color);
            corner(3, topRight, sprite, u1, v0, normal, color);
            emit();
        }

        private void corner(int i, Vector3f pixel, Sprite sprite, float u, float v, Vector3f normal, int color) {
            emitter.pos(i, pixel.x / 16F, pixel.y / 16F, pixel.z / 16F);
            emitter.uv(i, sprite.getMinU() + (sprite.getMaxU() - sprite.getMinU()) * u,
                    sprite.getMinV() + (sprite.getMaxV() - sprite.getMinV()) * v);
            emitter.color(i, color);
            emitter.normal(i, normal.x, normal.y, normal.z);
            emitter.lightmap(i, 0);
        }

        /** Transforms the quad being emitted, then emits it. */
        private void emit() {
            for (int i = 0; i < 4; i++) {
                emitter.copyPos(i, vector);
                matrix.transformPosition(vector);
                emitter.pos(i, vector.x, vector.y, vector.z);
                if (emitter.hasNormal(i)) {
                    emitter.copyNormal(i, vector);
                    normals.transform(vector).normalize();
                    emitter.normal(i, vector.x, vector.y, vector.z);
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
