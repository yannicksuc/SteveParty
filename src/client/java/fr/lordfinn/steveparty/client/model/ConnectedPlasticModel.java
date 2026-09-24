package fr.lordfinn.steveparty.client.model;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PlasticBlock;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Connected texture for the plastic blocks: touching blocks of the same colour merge into one plastic
 * piece, at most 4 blocks wide on X/Z and 2 blocks high. Pieces start from the edge of the build, and two blocks
 * only merge when they are aligned on the other two axes too, so a piece never outgrows its 4x2x4 box (it can
 * still wrap around another block, hence the inner corner sprites). Where pieces start is worked out by
 * {@link PieceLayout}. A double plastic slab (drawn with this model) is the full block of its colour and merges with
 * those blocks; a half slab never joins a piece.
 * <p>
 * Each face picks one of 47 sprites from which of its 4 edges are connected (bit 1 up, 2 right, 4 down, 8 left,
 * in the texture orientation of vanilla block faces) and which inner corners it must close (bits 16 to 128).
 */
public class ConnectedPlasticModel implements BakedModel {
    /** The full plastic block of each plastic slab's colour: a double slab is that block. */
    private static final Map<Block, Block> FULL_BLOCK_OF_SLAB = new IdentityHashMap<>();

    static {
        for (int i = 0; i < ModBlocks.COLORS.length; i++) FULL_BLOCK_OF_SLAB.put(ModBlocks.PLASTIC_SLABS[i], ModBlocks.PLASTIC_BLOCKS[i]);
    }

    private static final PieceLayout.Lookup LOOKUP = BlockRenderView::getBlockState;
    private static final PieceLayout.Joiner JOINER = (here, there, dir) -> {
        Block piece = pieceOf(here);
        return piece != null && pieceOf(there) == piece;
    };

    // Texture up / left of each face, following the vanilla block face UVs
    private static final Direction[] TEXTURE_UP = new Direction[6];
    private static final Direction[] TEXTURE_LEFT = new Direction[6];

    static {
        set(Direction.UP, Direction.NORTH, Direction.WEST);
        set(Direction.DOWN, Direction.SOUTH, Direction.WEST);
        set(Direction.NORTH, Direction.UP, Direction.EAST);
        set(Direction.SOUTH, Direction.UP, Direction.WEST);
        set(Direction.WEST, Direction.UP, Direction.NORTH);
        set(Direction.EAST, Direction.UP, Direction.SOUTH);
    }

    /** @return the side the top of {@code face}'s texture points to (vanilla block face UVs). */
    public static Direction textureUp(Direction face) {
        return TEXTURE_UP[face.ordinal()];
    }

    /** @return the side the left of {@code face}'s texture points to (vanilla block face UVs). */
    public static Direction textureLeft(Direction face) {
        return TEXTURE_LEFT[face.ordinal()];
    }

    private static void set(Direction face, Direction up, Direction left) {
        TEXTURE_UP[face.ordinal()] = up;
        TEXTURE_LEFT[face.ordinal()] = left;
    }

    private final BakedModel base;
    private final Sprite[] sprites;

    public ConnectedPlasticModel(BakedModel base, Sprite[] sprites) {
        this.base = base;
        this.sprites = sprites;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
        QuadEmitter emitter = context.getEmitter();
        Block piece = pieceOf(state);
        PieceLayout layout = PieceLayout.begin(world, pos, LOOKUP, JOINER);
        try {
            for (Direction face : Direction.values()) {
                if (context.isFaceCulled(face)) continue;
                Direction up = TEXTURE_UP[face.ordinal()];
                Direction left = TEXTURE_LEFT[face.ordinal()];
                Direction right = left.getOpposite();
                Direction down = up.getOpposite();
                boolean u = connected(world, layout, piece, pos, up);
                boolean r = connected(world, layout, piece, pos, right);
                boolean d = connected(world, layout, piece, pos, down);
                boolean l = connected(world, layout, piece, pos, left);
                int mask = (u ? UP : 0) | (r ? RIGHT : 0) | (d ? DOWN : 0) | (l ? LEFT : 0);
                // Inner corners: both sides belong to the piece but the diagonal block does not
                if (u && l && !connected(world, layout, piece, pos.offset(up), left)) mask |= INNER_UP_LEFT;
                if (u && r && !connected(world, layout, piece, pos.offset(up), right)) mask |= INNER_UP_RIGHT;
                if (d && r && !connected(world, layout, piece, pos.offset(down), right)) mask |= INNER_DOWN_RIGHT;
                if (d && l && !connected(world, layout, piece, pos.offset(down), left)) mask |= INNER_DOWN_LEFT;
                emitter.square(face, 0, 0, 1, 1, 0);
                emitter.cullFace(face);
                emitter.spriteBake(sprites[mask], MutableQuadView.BAKE_LOCK_UV);
                emitter.color(-1, -1, -1, -1);
                emitter.emit();
            }
        } finally {
            layout.end();
        }
    }

    /**
     * @return the full plastic block a block counts as in a piece: a plastic block itself, the block of its colour
     * for a double plastic slab, null for anything else (half slabs included)
     */
    @Nullable
    private static Block pieceOf(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof PlasticBlock) return block;
        Block full = FULL_BLOCK_OF_SLAB.get(block);
        return full != null && state.get(SlabBlock.TYPE) == SlabType.DOUBLE ? full : null;
    }

    /** @return whether the block at {@code pos} and its neighbour on {@code dir} are both {@code piece} and one piece. */
    private static boolean connected(BlockRenderView world, PieceLayout layout, @Nullable Block piece, BlockPos pos, Direction dir) {
        // Same colour, wet or dry
        if (piece == null || pieceOf(world.getBlockState(pos)) != piece || pieceOf(world.getBlockState(pos.offset(dir))) != piece) return false;
        return layout.samePiece(pos, dir);
    }

    /** Sprite index of a face: which edges are connected, plus the inner corners (a valid mask has 47 values). */
    public static final int UP = 1, RIGHT = 2, DOWN = 4, LEFT = 8;
    public static final int INNER_UP_LEFT = 16, INNER_UP_RIGHT = 32, INNER_DOWN_RIGHT = 64, INNER_DOWN_LEFT = 128;

    public static boolean isValidMask(int mask) {
        return ((mask & INNER_UP_LEFT) == 0 || (mask & (UP | LEFT)) == (UP | LEFT))
                && ((mask & INNER_UP_RIGHT) == 0 || (mask & (UP | RIGHT)) == (UP | RIGHT))
                && ((mask & INNER_DOWN_RIGHT) == 0 || (mask & (DOWN | RIGHT)) == (DOWN | RIGHT))
                && ((mask & INNER_DOWN_LEFT) == 0 || (mask & (DOWN | LEFT)) == (DOWN | LEFT));
    }

    // Everything else (items, particles, fallback) uses the standalone tile

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
