package fr.lordfinn.steveparty.client.model;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import fr.lordfinn.steveparty.blocks.custom.signs.AbstractStencilSignBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Connected texture for a part (post or side bars) of a plastic fence, like {@link ConnectedPlasticModel}: fences
 * of the same colour make one plastic piece at most 2 blocks high (stacked posts) and 4 blocks long (a fence line),
 * so that the plastic border only runs round each piece instead of crossing the bars at every block. Where pieces
 * start is worked out by {@link PieceLayout}.
 * <p>
 * The fence models texture their faces with the block grid (uvlock): each quad keeps its UVs and only takes the
 * sprite of its face's connections.
 */
public class ConnectedPlasticFenceModel implements BakedModel {
    private static final PieceLayout.Lookup LOOKUP = ConnectedPlasticFenceModel::fenceAt;
    private static final PieceLayout.Joiner JOINER = ConnectedPlasticFenceModel::joined;

    /** Material of the quads (the renderer's default), set once when baked; null without a renderer (nothing draws then). */
    @Nullable
    private final RenderMaterial material;
    private final BakedModel base;
    /** Sprite of each mask of {@link ConnectedPlasticModel#UP} .. {@link ConnectedPlasticModel#LEFT} (0 to 15). */
    private final Sprite[] sprites;

    public ConnectedPlasticFenceModel(BakedModel base, Sprite[] sprites) {
        this.base = base;
        this.sprites = sprites;
        Renderer renderer = RendererAccess.INSTANCE.getRenderer();
        this.material = renderer == null ? null : renderer.materialFinder().find();
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
        if (material == null) return;
        QuadEmitter emitter = context.getEmitter();
        int[] masks = new int[6];
        if (fenceAt(world, pos).isOf(state.getBlock())) {
            PieceLayout layout = PieceLayout.begin(world, pos, LOOKUP, JOINER);
            try {
                for (Direction face : Direction.values()) masks[face.ordinal()] = mask(world, layout, pos, face);
            } finally {
                layout.end();
            }
        }
        for (Direction cull : CULL_FACES) {
            if (cull != null && context.isFaceCulled(cull)) continue;
            for (BakedQuad quad : base.getQuads(state, cull, randomSupplier.get())) {
                emitter.fromVanilla(quad, material, cull);
                Sprite from = quad.getSprite();
                Sprite to = sprites[masks[quad.getFace().ordinal()]];
                if (to != null && to != from) {
                    for (int i = 0; i < 4; i++) {
                        emitter.uv(i, remap(emitter.u(i), from.getMinU(), from.getMaxU(), to.getMinU(), to.getMaxU()),
                                remap(emitter.v(i), from.getMinV(), from.getMaxV(), to.getMinV(), to.getMaxV()));
                    }
                }
                emitter.emit();
            }
        }
    }

    private static final Direction[] CULL_FACES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST, null};

    /** Which edges of this face's texture go on into the same piece (see {@link ConnectedPlasticModel#UP}). */
    private static int mask(BlockRenderView world, PieceLayout layout, BlockPos pos, Direction face) {
        Direction up = ConnectedPlasticModel.textureUp(face);
        Direction left = ConnectedPlasticModel.textureLeft(face);
        int mask = 0;
        if (connected(world, layout, pos, up)) mask |= ConnectedPlasticModel.UP;
        if (connected(world, layout, pos, left.getOpposite())) mask |= ConnectedPlasticModel.RIGHT;
        if (connected(world, layout, pos, up.getOpposite())) mask |= ConnectedPlasticModel.DOWN;
        if (connected(world, layout, pos, left)) mask |= ConnectedPlasticModel.LEFT;
        return mask;
    }

    /** @return whether the fence at {@code pos} and its neighbour on {@code dir} are one piece. */
    private static boolean connected(BlockRenderView world, PieceLayout layout, BlockPos pos, Direction dir) {
        return joined(fenceAt(world, pos), fenceAt(world, pos.offset(dir)), dir) && layout.samePiece(pos, dir);
    }

    /**
     * @return the fence at {@code pos}; for a sign standing on a fence, the post of that fence it draws through its
     * block (a lone post), so that the post goes on from the fence below without a seam
     */
    private static BlockState fenceAt(BlockRenderView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof AbstractStencilSignBlock sign && sign.hangsOnPosts()
                && state.get(AbstractStencilSignBlock.MOUNT) == AbstractStencilSignBlock.Mount.POST) {
            BlockState below = world.getBlockState(pos.down());
            if (below.getBlock() instanceof FenceBlock) return below.getBlock().getDefaultState();
        }
        return state;
    }

    /** Same fence, stacked, or linked by its bars. */
    private static boolean joined(BlockState here, BlockState there, Direction dir) {
        if (!there.isOf(here.getBlock())) return false;
        if (dir.getAxis().isVertical()) return true;
        BooleanProperty side = side(dir);
        return here.contains(side) && here.get(side);
    }

    private static BooleanProperty side(Direction dir) {
        return switch (dir) {
            case NORTH -> FenceBlock.NORTH;
            case SOUTH -> FenceBlock.SOUTH;
            case WEST -> FenceBlock.WEST;
            default -> FenceBlock.EAST;
        };
    }

    private static float remap(float value, float fromMin, float fromMax, float toMin, float toMax) {
        return toMin + (value - fromMin) / (fromMax - fromMin) * (toMax - toMin);
    }

    // Everything else (items, particles, fallback) uses the standalone texture

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
