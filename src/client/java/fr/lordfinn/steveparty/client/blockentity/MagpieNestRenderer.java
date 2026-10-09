package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.MagpieNestBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.MagpieNestPile;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * What a Magpie Nest's block model cannot draw: its pile of coins ({@link MagpieNestPile}: one coin cube per thing
 * it holds, coin or not, as high as they go, the first ones where the model had them by hand) and its long twig
 * across the top (turned on three axes in Blockbench, which a block model of this version cannot do). All turned the
 * nest's way.
 * <p>
 * The pile's cubes are worked out once for a nest's coin count and facing (corners and normals, kept by the nest,
 * {@link MagpieNestBlockEntity#renderCache}) and only sent each frame. Its item ({@link #ITEM}): the block model, the
 * twig and the coins placed by hand.
 */
public class MagpieNestRenderer implements BlockEntityRenderer<MagpieNestBlockEntity> {
    private static final SpriteIdentifier TWIGS = new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Steveparty.id("block/magpie_nest"));
    private static final SpriteIdentifier COIN = new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Steveparty.id("block/magpie_nest_coin"));

    /** The coin texture (32 x 32, two texels a pixel): its face, its back, its rim; u0, v0, u1, v1 in the texture. */
    private static final float[] COIN_FACE = {0, 0, 10 / 32F, 10 / 32F}, COIN_BACK = {10 / 32F, 0, 20 / 32F, 10 / 32F},
            COIN_RIM = {0, 10 / 32F, 10 / 32F, 14 / 32F};
    /** The faces of a cube, in the order up, down, north, south, west, east: their corners (bits x, y, z), counter-clockwise from outside. */
    private static final int[][] FACES = {
            {0b010, 0b110, 0b111, 0b011}, // up: (x0,z0) (x0,z1) (x1,z1) (x1,z0)
            {0b100, 0b000, 0b001, 0b101}, // down: (x0,z1) (x0,z0) (x1,z0) (x1,z1)
            {0b011, 0b001, 0b000, 0b010}, // north: (x1,y1) (x1,y0) (x0,y0) (x0,y1)
            {0b110, 0b100, 0b101, 0b111}, // south: (x0,y1) (x0,y0) (x1,y0) (x1,y1)
            {0b010, 0b000, 0b100, 0b110}, // west: (z0,y1) (z0,y0) (z1,y0) (z1,y1)
            {0b111, 0b101, 0b001, 0b011}}; // east: (z1,y1) (z1,y0) (z0,y0) (z0,y1)
    private static final float[][] NORMALS = {{0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    private static final float[][] COIN_UV = {COIN_FACE, COIN_BACK, COIN_RIM, COIN_RIM, COIN_RIM, COIN_RIM};

    /** The long twig of the model (its element 5): from, to, rotation x, y, z about its origin, its faces' uv (pixels). */
    private static final float[] TWIG_FROM = {0, 5, 4}, TWIG_TO = {16, 6, 5}, TWIG_ORIGIN = {8, 5, 5};
    private static final float TWIG_X = -3.70743F, TWIG_Y = 22.20812F, TWIG_Z = -9.72789F;
    private static final float[][] TWIG_UV = {
            {0, 4 / 16F, 1, 5 / 16F}, {0, 11 / 16F, 1, 12 / 16F}, {0, 10 / 16F, 1, 11 / 16F},
            {0, 10 / 16F, 1, 11 / 16F}, {4 / 16F, 10 / 16F, 5 / 16F, 11 / 16F}, {11 / 16F, 10 / 16F, 12 / 16F, 11 / 16F}};
    /** The twig's corners and normals for each facing (by horizontal id). */
    private static final Cubes[] TWIG = new Cubes[4];
    /** The item's coins: the ones placed by hand. */
    private static Cubes itemPile;

    static {
        for (Direction facing : Direction.Type.HORIZONTAL) {
            Matrix4f twig = facingMatrix(facing)
                    .translate(TWIG_ORIGIN[0], TWIG_ORIGIN[1], TWIG_ORIGIN[2])
                    .rotateZ((float) Math.toRadians(TWIG_Z)).rotateY((float) Math.toRadians(TWIG_Y)).rotateX((float) Math.toRadians(TWIG_X))
                    .translate(-TWIG_ORIGIN[0], -TWIG_ORIGIN[1], -TWIG_ORIGIN[2]);
            Cubes cubes = new Cubes(1);
            cubes.put(0, twig, TWIG_FROM, TWIG_TO);
            TWIG[facing.getHorizontal()] = cubes;
        }
    }

    public MagpieNestRenderer(BlockEntityRendererFactory.Context context) {
    }

    /** Corners (block units) and face normals of a few cubes, worked out once. */
    static final class Cubes {
        final int count;
        final float[] corners, normals;

        Cubes(int count) {
            this.count = count;
            this.corners = new float[count * 24];
            this.normals = new float[count * 18];
        }

        /** Cube {@code index}: from {@code from} to {@code to} (pixels), placed by {@code transform} (pixels to pixels). */
        void put(int index, Matrix4f transform, float[] from, float[] to) {
            Vector3f v = new Vector3f();
            for (int c = 0; c < 8; c++) {
                v.set((c & 1) == 0 ? from[0] : to[0], (c & 2) == 0 ? from[1] : to[1], (c & 4) == 0 ? from[2] : to[2]);
                transform.transformPosition(v);
                int o = index * 24 + c * 3;
                corners[o] = v.x / 16;
                corners[o + 1] = v.y / 16;
                corners[o + 2] = v.z / 16;
            }
            for (int f = 0; f < 6; f++) {
                v.set(NORMALS[f][0], NORMALS[f][1], NORMALS[f][2]);
                transform.transformDirection(v).normalize();
                int o = index * 18 + f * 3;
                normals[o] = v.x;
                normals[o + 1] = v.y;
                normals[o + 2] = v.z;
            }
        }

        void draw(MatrixStack.Entry entry, VertexConsumer buffer, Sprite sprite, float[][] uvs, int light, int overlay) {
            Matrix4f pose = entry.getPositionMatrix();
            for (int i = 0; i < count; i++) {
                for (int f = 0; f < 6; f++) {
                    float[] uv = uvs[f];
                    float u0 = sprite.getFrameU(uv[0]), v0 = sprite.getFrameV(uv[1]), u1 = sprite.getFrameU(uv[2]), v1 = sprite.getFrameV(uv[3]);
                    int n = i * 18 + f * 3;
                    float nx = normals[n], ny = normals[n + 1], nz = normals[n + 2];
                    int[] face = FACES[f];
                    vertex(buffer, entry, pose, i, face[0], u0, v0, nx, ny, nz, light, overlay);
                    vertex(buffer, entry, pose, i, face[1], u0, v1, nx, ny, nz, light, overlay);
                    vertex(buffer, entry, pose, i, face[2], u1, v1, nx, ny, nz, light, overlay);
                    vertex(buffer, entry, pose, i, face[3], u1, v0, nx, ny, nz, light, overlay);
                }
            }
        }

        private void vertex(VertexConsumer buffer, MatrixStack.Entry entry, Matrix4f pose, int cube, int corner, float u, float v,
                            float nx, float ny, float nz, int light, int overlay) {
            int o = cube * 24 + corner * 3;
            buffer.vertex(pose, corners[o], corners[o + 1], corners[o + 2]).color(-1).texture(u, v).overlay(overlay).light(light)
                    .normal(entry, nx, ny, nz);
        }
    }

    /** The pile drawn for a nest: its coin count and facing, and its cubes. */
    private record Pile(int count, Direction facing, Cubes cubes) {
    }

    /** Pixels of the north-facing model to pixels of the nest facing {@code facing} (about its middle). */
    private static Matrix4f facingMatrix(Direction facing) {
        return new Matrix4f().translate(8, 0, 8).rotateY((float) Math.toRadians(-MagpieNestPile.yRotation(facing))).translate(-8, 0, -8);
    }

    /** The cubes of the first {@code count} coins of the pile at {@code pos}, facing {@code facing}. */
    static Cubes pile(BlockPos pos, int count, Direction facing) {
        float[] layout = MagpieNestPile.layout(pos, count);
        int drawn = layout.length / MagpieNestPile.STRIDE;
        Cubes cubes = new Cubes(drawn);
        float half = MagpieNestPile.COIN_WIDTH / 2, halfHeight = MagpieNestPile.COIN_HEIGHT / 2;
        float[] from = {-half, -halfHeight, -half}, to = {half, halfHeight, half};
        Matrix4f base = facingMatrix(facing), m = new Matrix4f();
        for (int i = 0; i < drawn; i++) {
            int o = i * MagpieNestPile.STRIDE;
            m.set(base).translate(layout[o], layout[o + 1], layout[o + 2])
                    .rotateX((float) Math.toRadians(layout[o + 4])).rotateZ((float) Math.toRadians(layout[o + 5]))
                    .rotateY((float) Math.toRadians(layout[o + 3]));
            cubes.put(i, m, from, to);
        }
        return cubes;
    }

    @Override
    public void render(MagpieNestBlockEntity nest, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                       int light, int overlay) {
        Direction facing = nest.getFacing();
        int count = MagpieNestPile.drawn(nest.getShownPile());
        Pile pile = nest.renderCache instanceof Pile cached && cached.count == count && cached.facing == facing ? cached : null;
        if (pile == null) {
            pile = new Pile(count, facing, pile(nest.getPos(), count, facing));
            nest.renderCache = pile;
        }
        VertexConsumer buffer = vertexConsumers.getBuffer(TexturedRenderLayers.getEntityCutout());
        MatrixStack.Entry entry = matrices.peek();
        TWIG[facing.getHorizontal()].draw(entry, buffer, TWIGS.getSprite(), TWIG_UV, light, overlay);
        if (count > 0) pile.cubes.draw(entry, buffer, COIN.getSprite(), COIN_UV, light, overlay);
    }

    /** The pile rises above the block. */
    @Override
    public boolean rendersOutsideBoundingBox(MagpieNestBlockEntity nest) {
        return true;
    }

    /** Its item: the block model, the long twig and the coins placed by hand. */
    public static final BuiltinItemRendererRegistry.DynamicItemRenderer ITEM = (stack, mode, matrices, vertexConsumers, light, overlay) -> {
        BlockState state = ModBlocks.MAGPIE_NEST.getDefaultState();
        BlockRenderManager blocks = MinecraftClient.getInstance().getBlockRenderManager();
        blocks.getModelRenderer().render(matrices.peek(), vertexConsumers.getBuffer(TexturedRenderLayers.getEntityCutout()), state,
                blocks.getModel(state), 1, 1, 1, light, overlay);
        if (itemPile == null) itemPile = pile(BlockPos.ORIGIN, MagpieNestPile.HAND_PLACED, Direction.NORTH);
        VertexConsumer buffer = vertexConsumers.getBuffer(TexturedRenderLayers.getEntityCutout());
        TWIG[Direction.NORTH.getHorizontal()].draw(matrices.peek(), buffer, TWIGS.getSprite(), TWIG_UV, light, overlay);
        itemPile.draw(matrices.peek(), buffer, COIN.getSprite(), COIN_UV, light, overlay);
    };
}
