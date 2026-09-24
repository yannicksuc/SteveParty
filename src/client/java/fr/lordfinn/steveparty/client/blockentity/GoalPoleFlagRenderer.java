package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBlock;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleFlags;
import fr.lordfinn.steveparty.client.flag.FlagSlide;
import fr.lordfinn.steveparty.client.flag.FlagPalettes;
import fr.lordfinn.steveparty.client.flag.FlagWind;
import fr.lordfinn.steveparty.client.flag.ShaderPacks;
import fr.lordfinn.steveparty.items.custom.FlagItem;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The goal pole's flag, drawn as a strip of thin columns that ripple in the wind ({@link FlagWind}): an idle flutter
 * all the time and a gust from time to time.
 * <p>
 * Why a block entity renderer: the pole already has a block entity on the client, the flag is only a dozen quads, and
 * a vertex wave needs no model files, no animation library and no texture frames. It is client only: nothing ticks,
 * nothing is sent, the time comes from the world time. The flag's shape (placed or not, facing) stays in the block
 * state; the baked models only keep the pole itself.
 * <p>
 * Cost: nothing is allocated per frame (scratch arrays and vectors are reused, render thread only); nearby flags use
 * 12 columns, farther ones 6 then 3, and past {@link #RENDER_DISTANCE} blocks the flag is not drawn.
 * <p>
 * A dyed flag is drawn level by level in the colours of its dye's wool ({@link FlagPalettes}); an undyed one uses the
 * classic red texture.
 * <p>
 * Brightness: the flag is drawn in the cutout <em>block</em> layer, shaded like a block face (the old baked flag): the
 * shade of each column comes from the direction it faces in the world, so the waves show as a light ripple around
 * the brightness of the old flag. The entity layers used before add vanilla's entity lighting (at most ~74 % on a
 * vertical cloth, 50 % facing east/west), which made every flag darker than the old model, wool or banners.
 * <p>
 * When its goal is met, the flag slides down the pole ({@link FlagSlide}) to rest at the bottom, stacked on the flags
 * below ({@link GoalPoleFlags}); a ring stays on its own segment, with a thin rope down to the flag, so that everyone
 * sees where it belongs (shears and dye work there). Only the drawing moves: the flag stays on its segment.
 */
public class GoalPoleFlagRenderer implements BlockEntityRenderer<GoalPoleBlockEntity> {
    /** The flag's sprites in the block atlas: the original red one, and a greyscale one tinted with a dye's colour. */
    public static final Identifier SPRITE = Steveparty.id("block/goal_pole_flag");
    /** The notch of a segment with its own goal: the pole's white metal, tinted. */
    private static final Identifier NOTCH_SPRITE = Steveparty.id("block/goal_pole");
    private static final int NOTCH = 0xFFC83C, NOTCH_MET = 0x5EE05A;
    /** The ring the flag hangs from (on its own segment), and the rope down to the flag while it has slid. */
    private static final int RING = 0x8A8A94, ROPE = 0x8C7458;
    // Around the top of the flag (the flag is tied to it), just under the ball on a top segment
    private static final float RING_BOTTOM = 12.4f, RING_TOP = 13.6f, ROPE_X = 6.3f, ROPE_WIDTH = 0.7f;
    private static final double SOUND_DISTANCE_SQ = 24 * 24;
    /** Slide state per flag (one per flag, not per frame). */
    private static final Map<GoalPoleBlockEntity, FlagSlide> SLIDES = new WeakHashMap<>();
    private static final BlockPos.Mutable SCRATCH = new BlockPos.Mutable();
    /** White masks of the flag's shading levels (darkest first): a dyed flag tints each with its wool colour. */
    private static final Identifier[] LEVEL_SPRITES = new Identifier[FlagPalettes.LEVELS];

    static {
        for (int level = 0; level < FlagPalettes.LEVELS; level++) LEVEL_SPRITES[level] = Steveparty.id("block/goal_pole_flag_level_" + level);
    }
    /** Vanilla's block face shading: sides facing north/south are drawn at 80 %, east/west at 60 % (up 100 %). */
    private static final float SHADE_Z = 0.8f, SHADE_X = 0.6f;
    private static final int RENDER_DISTANCE = 128;
    private static final int COLUMNS_NEAR = 12, COLUMNS_MID = 6, COLUMNS_FAR = 3;
    private static final double NEAR_SQ = 24 * 24, MID_SQ = 56 * 56;

    // Flag geometry in model pixels, for a pole facing north (the block state turns it): the cloth hangs from the
    // west side of the pole, in the plane z = 8, from y = 2.5 to 13.5, and is 12 pixels long (texture u 0..12, v 0..11)
    private static final float ATTACH_X = 6.5f, PLANE_Z = 8f, TOP_Y = 13.5f, BOTTOM_Y = 2.5f, LENGTH = 12f;
    private static final float POLE_CENTER = 8f;
    private static final float U_MAX = 12f / 16f, V_MAX = 11f / 16f;

    // Scratch data (render thread only)
    private static final float[] XS = new float[COLUMNS_NEAR + 1];
    private static final float[] ZS = new float[COLUMNS_NEAR + 1];
    private static final float[] DROOP = new float[COLUMNS_NEAR + 1];
    private static final float[] NX = new float[COLUMNS_NEAR + 1];
    private static final float[] NZ = new float[COLUMNS_NEAR + 1];
    private static final float[] SHADES = new float[COLUMNS_NEAR + 1];
    private static final Vector3f POSITION = new Vector3f();
    private static final Vector3f NORMAL = new Vector3f();

    private final BlockEntityRenderDispatcher dispatcher;

    public GoalPoleFlagRenderer(BlockEntityRendererFactory.Context context) {
        this.dispatcher = context.getRenderDispatcher();
    }

    @Override
    public void render(GoalPoleBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = entity.getCachedState();
        if (!state.contains(GoalPoleBlock.FLAG)) return;
        World world = entity.getWorld();
        if (world == null) return;
        // The top of the pole: the progress above the ball, the lit ball, the details with a wrench
        if (state.get(GoalPoleBlock.TOP)) GoalPoleTopDisplay.render(entity, tickDelta, matrices, vertexConsumers, dispatcher);
        // A goal per segment: a notch on each segment, gold, green while its goal is met
        if (entity.isPerSegment()) drawBand(vertexConsumers.getBuffer(RenderLayer.getCutout()), matrices.peek(),
                entity.isGoalMet() ? NOTCH_MET : NOTCH, light, 6.1f, 9.9f, 7.4f, 8.6f);
        if (!state.get(GoalPoleBlock.FLAG)) return;

        BlockPos pos = entity.getPos();
        Vec3d camera = dispatcher.camera != null ? dispatcher.camera.getPos() : Vec3d.ZERO;
        double distanceSq = camera.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        int columns = distanceSq < NEAR_SQ ? COLUMNS_NEAR : distanceSq < MID_SQ ? COLUMNS_MID : COLUMNS_FAR;

        // Seconds of world time (wrapped every ~33 h so that a double keeps sub-frame precision)
        double seconds = ((world.getTime() % 2_400_000L) + tickDelta) / 20.0;
        long seed = FlagWind.seed(pos.getX(), pos.getY(), pos.getZ());
        float phase = FlagWind.phase(seed);
        float speed = FlagWind.speed(seed);
        float gust = FlagWind.gust(pos.getX(), pos.getZ(), seconds);
        computeCloth(columns, seconds, phase, speed, gust);

        // Goal met: the flag slides down to the bottom of the pole (on the flags below), and back up afterwards
        boolean met = entity.isGoalMet();
        float wanted = met ? GoalPoleFlags.restingDrop(world, pos, SCRATCH) : 0f;
        FlagSlide slide = SLIDES.computeIfAbsent(entity, e -> new FlagSlide());
        double changeSecond = (entity.getGoalMetTick() % 2_400_000L) / 20.0;
        float stagger = ((seed >>> 40) & 0xFF) / 255f * 0.3f;
        if (slide.update(wanted, met, changeSecond, stagger, seconds) && distanceSq < SOUND_DISTANCE_SQ) {
            // A reel: the flag runs down (lower) or up (higher) its rope
            world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE,
                    SoundCategory.BLOCKS, 0.8f, slide.isDown() ? 0.75f : 1.15f, false);
        }
        float drop = slide.offset(seconds);
        int flagLight = light;
        if (drop <= -8f) {
            // Lit like the block the flag is in now
            SCRATCH.set(pos.getX(), pos.getY() + MathHelper.floor((GoalPoleFlags.FLAG_BOTTOM + GoalPoleFlags.FLAG_HEIGHT / 2 + drop) / 16f), pos.getZ());
            flagLight = WorldRenderer.getLightmapCoordinates(world, SCRATCH);
        }

        matrices.push();
        matrices.translate(0.5f, 0f, 0.5f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facingDegrees(state)));
        matrices.translate(-0.5f, 0f, -0.5f);
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getCutout());
        // The ring stays on the flag's segment; the rope runs down to the flag while it is away
        drawBand(buffer, matrices.peek(), RING, light, 6.2f, 9.8f, RING_BOTTOM, RING_TOP);
        if (TOP_Y + drop < RING_BOTTOM) drawRope(buffer, matrices.peek(), light, RING_BOTTOM, TOP_Y + drop);
        matrices.translate(0f, drop / 16f, 0f);
        // The whole flag swings a little around the pole during gusts
        float swing = FlagWind.swing(seconds, phase, gust);
        if (swing != 0f) {
            matrices.translate(POLE_CENTER / 16f, 0f, POLE_CENTER / 16f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(swing));
            matrices.translate(-POLE_CENTER / 16f, 0f, -POLE_CENTER / 16f);
        }
        MatrixStack.Entry entry = matrices.peek();
        // Undyed: the classic red texture. Dyed: one pass per shading level, in the colours of the dye's wool
        int flagColor = entity.getFlagColor();
        computeShades(columns, -facingDegrees(state) + swing);
        var atlas = MinecraftClient.getInstance().getBakedModelManager().getAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
        if (flagColor == FlagItem.NO_COLOR) {
            drawCloth(buffer, entry, atlas.getSprite(SPRITE), columns, 0xFFFFFF, flagLight);
        } else {
            int[] ramp = FlagPalettes.ramp(flagColor);
            for (int level = 0; level < FlagPalettes.LEVELS; level++) {
                drawCloth(buffer, entry, atlas.getSprite(LEVEL_SPRITES[level]), columns, ramp[level], flagLight);
            }
        }
        matrices.pop();
    }

    /**
     * Positions of the cloth columns. Each column is placed one segment away from the previous one, whatever the
     * wave, so the cloth keeps its length instead of stretching when it flaps.
     */
    private static void computeCloth(int columns, double seconds, float phase, float speed, float gust) {
        float segment = LENGTH / columns;
        XS[0] = ATTACH_X;
        ZS[0] = PLANE_Z;
        DROOP[0] = 0f;
        for (int i = 1; i <= columns; i++) {
            float s = (float) i / columns;
            float z = PLANE_Z + FlagWind.offset(s, seconds, phase, speed, gust);
            float dz = z - ZS[i - 1];
            float dx = (float) Math.sqrt(Math.max(segment * segment - dz * dz, segment * segment * 0.25f));
            XS[i] = XS[i - 1] - dx;
            ZS[i] = z;
            DROOP[i] = FlagWind.droop(s, gust);
        }
        // Normals from the neighbouring columns (front side), in the horizontal plane
        for (int i = 0; i <= columns; i++) {
            int a = Math.max(0, i - 1), b = Math.min(columns, i + 1);
            float tx = XS[b] - XS[a], tz = ZS[b] - ZS[a];
            float length = (float) Math.sqrt(tx * tx + tz * tz);
            NX[i] = length > 0 ? -tz / length : 0f;
            NZ[i] = length > 0 ? tx / length : -1f;
        }
    }

    /** A thin band around the rod (a notch, the flag's ring), shaded like block faces; sizes in pixels. */
    private static void drawBand(VertexConsumer buffer, MatrixStack.Entry entry, int color, int light,
                                 float minPx, float maxPx, float bottomPx, float topPx) {
        Sprite sprite = MinecraftClient.getInstance().getBakedModelManager()
                .getAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).getSprite(NOTCH_SPRITE);
        float min = minPx / 16f, max = maxPx / 16f, bottom = bottomPx / 16f, top = topPx / 16f;
        float u0 = sprite.getFrameU(0.1f), u1 = sprite.getFrameU(0.3f), v0 = sprite.getFrameV(0.1f), v1 = sprite.getFrameV(0.2f);
        int z = shaded(color, SHADE_Z), x = shaded(color, SHADE_X), up = shaded(color, 1f);
        // north, south, west, east, top
        quad(buffer, entry, max, top, min, min, top, min, min, bottom, min, max, bottom, min, u0, v0, u1, v1, z, light, 0, 0, -1);
        quad(buffer, entry, min, top, max, max, top, max, max, bottom, max, min, bottom, max, u0, v0, u1, v1, z, light, 0, 0, 1);
        quad(buffer, entry, min, top, min, min, top, max, min, bottom, max, min, bottom, min, u0, v0, u1, v1, x, light, -1, 0, 0);
        quad(buffer, entry, max, top, max, max, top, min, max, bottom, min, max, bottom, max, u0, v0, u1, v1, x, light, 1, 0, 0);
        quad(buffer, entry, min, top, min, max, top, min, max, top, max, min, top, max, u0, v0, u1, v1, up, light, 0, 1, 0);
        quad(buffer, entry, min, bottom, max, max, bottom, max, max, bottom, min, min, bottom, min, u0, v0, u1, v1, z, light, 0, -1, 0);
    }

    /** The rope from the ring down to the top of the slid flag: two thin crossed strips beside the rod (pixels). */
    private static void drawRope(VertexConsumer buffer, MatrixStack.Entry entry, int light, float topPx, float bottomPx) {
        Sprite sprite = MinecraftClient.getInstance().getBakedModelManager()
                .getAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).getSprite(NOTCH_SPRITE);
        float u0 = sprite.getFrameU(0.1f), u1 = sprite.getFrameU(0.12f), v0 = sprite.getFrameV(0.1f), v1 = sprite.getFrameV(0.4f);
        float x = ROPE_X / 16f, z = PLANE_Z / 16f, half = ROPE_WIDTH / 32f, top = topPx / 16f, bottom = bottomPx / 16f;
        int front = shaded(ROPE, SHADE_Z), side = shaded(ROPE, SHADE_X);
        quad(buffer, entry, x - half, top, z, x + half, top, z, x + half, bottom, z, x - half, bottom, z, u0, v0, u1, v1, front, light, 0, 0, -1);
        quad(buffer, entry, x, top, z - half, x, top, z + half, x, bottom, z + half, x, bottom, z - half, u0, v0, u1, v1, side, light, -1, 0, 0);
    }

    static void quad(VertexConsumer buffer, MatrixStack.Entry entry, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, float u0, float v0, float u1, float v1,
                             int color, int light, float nx, float ny, float nz) {
        Matrix4f pose = entry.getPositionMatrix();
        entry.getNormalMatrix().transform(nx, ny, nz, NORMAL).normalize();
        corner(buffer, pose, x0, y0, z0, u0, v0, color, light);
        corner(buffer, pose, x1, y1, z1, u1, v0, color, light);
        corner(buffer, pose, x2, y2, z2, u1, v1, color, light);
        corner(buffer, pose, x3, y3, z3, u0, v1, color, light);
        // And the other winding: the band is seen from outside whatever the order the cutout layer culls
        corner(buffer, pose, x3, y3, z3, u0, v1, color, light);
        corner(buffer, pose, x2, y2, z2, u1, v1, color, light);
        corner(buffer, pose, x1, y1, z1, u1, v0, color, light);
        corner(buffer, pose, x0, y0, z0, u0, v0, color, light);
    }

    private static void corner(VertexConsumer buffer, Matrix4f pose, float x, float y, float z, float u, float v, int color, int light) {
        pose.transformPosition(x, y, z, POSITION);
        buffer.vertex(POSITION.x, POSITION.y, POSITION.z, color, u, v, OverlayTexture.DEFAULT_UV, light, NORMAL.x, NORMAL.y, NORMAL.z);
    }

    /** The cloth, both sides, with one sprite and one colour (times each column's shade). */
    private static void drawCloth(VertexConsumer buffer, MatrixStack.Entry entry, Sprite sprite, int columns, int color, int light) {
        float v0 = sprite.getFrameV(0f), v1 = sprite.getFrameV(V_MAX);
        for (int i = 0; i < columns; i++) {
            float u0 = sprite.getFrameU(U_MAX * i / columns), u1 = sprite.getFrameU(U_MAX * (i + 1) / columns);
            int c0 = shaded(color, SHADES[i]), c1 = shaded(color, SHADES[i + 1]);
            // Front (north side, normal -z at rest), counter-clockwise as seen from the north
            vertex(buffer, entry, i, TOP_Y, u0, v0, light, c0, 1f);
            vertex(buffer, entry, i, BOTTOM_Y, u0, v1, light, c0, 1f);
            vertex(buffer, entry, i + 1, BOTTOM_Y, u1, v1, light, c1, 1f);
            vertex(buffer, entry, i + 1, TOP_Y, u1, v0, light, c1, 1f);
            // Back (south side): same texels, reversed winding and normal
            vertex(buffer, entry, i, TOP_Y, u0, v0, light, c0, -1f);
            vertex(buffer, entry, i + 1, TOP_Y, u1, v0, light, c1, -1f);
            vertex(buffer, entry, i + 1, BOTTOM_Y, u1, v1, light, c1, -1f);
            vertex(buffer, entry, i, BOTTOM_Y, u0, v1, light, c0, -1f);
        }
    }

    /**
     * Block-face shade of each column, from the direction its normal faces in the world (the pole's facing plus the
     * gust swing): 0.8 facing north/south, 0.6 facing east/west, in between for the waves. Both sides of the cloth
     * face opposite ways along the same axis, so they get the same shade.
     */
    private static void computeShades(int columns, float yawDegrees) {
        if (ShaderPacks.inUse()) {
            // Shader packs light the cloth themselves (and drop vanilla's face shading on the blocks around it)
            for (int i = 0; i <= columns; i++) SHADES[i] = 1f;
            return;
        }
        float radians = yawDegrees * MathHelper.RADIANS_PER_DEGREE;
        float cos = MathHelper.cos(radians), sin = MathHelper.sin(radians);
        for (int i = 0; i <= columns; i++) {
            float x = NX[i] * cos + NZ[i] * sin;
            float z = -NX[i] * sin + NZ[i] * cos;
            SHADES[i] = SHADE_X * x * x + SHADE_Z * z * z;
        }
    }

    private static int shaded(int rgb, float shade) {
        int r = (int) ((rgb >> 16 & 0xFF) * shade), g = (int) ((rgb >> 8 & 0xFF) * shade), b = (int) ((rgb & 0xFF) * shade);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static void vertex(VertexConsumer buffer, MatrixStack.Entry entry, int column, float y, float u, float v,
                               int light, int color, float side) {
        Matrix4f pose = entry.getPositionMatrix();
        Matrix3f normalMatrix = entry.getNormalMatrix();
        pose.transformPosition(XS[column] / 16f, (y - DROOP[column]) / 16f, ZS[column] / 16f, POSITION);
        normalMatrix.transform(NX[column] * side, 0f, NZ[column] * side, NORMAL).normalize();
        buffer.vertex(POSITION.x, POSITION.y, POSITION.z, color, u, v, OverlayTexture.DEFAULT_UV, light,
                NORMAL.x, NORMAL.y, NORMAL.z);
    }

    /** Same rotations as the goal_pole block state file. */
    private static float facingDegrees(BlockState state) {
        return switch (state.get(GoalPoleBlock.FACING)) {
            case SOUTH -> 180f;
            case WEST -> 270f;
            case EAST -> 90f;
            default -> 0f;
        };
    }

    /**
     * A flag can slide down several blocks, and the progress floats above the top: those poles are drawn even when
     * their own block is out of view (the render distance still applies).
     */
    @Override
    public boolean rendersOutsideBoundingBox(GoalPoleBlockEntity entity) {
        BlockState state = entity.getCachedState();
        return state.contains(GoalPoleBlock.FLAG) && (state.get(GoalPoleBlock.FLAG) || state.get(GoalPoleBlock.TOP));
    }

    @Override
    public int getRenderDistance() {
        return RENDER_DISTANCE;
    }
}
