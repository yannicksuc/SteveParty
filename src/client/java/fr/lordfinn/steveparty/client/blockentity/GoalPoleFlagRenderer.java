package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBlock;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBlockEntity;
import fr.lordfinn.steveparty.client.flag.FlagWind;
import fr.lordfinn.steveparty.items.custom.FlagItem;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

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
 * A dyed flag uses a greyscale copy of the texture multiplied by its colour; an undyed one the original red texture.
 */
public class GoalPoleFlagRenderer implements BlockEntityRenderer<GoalPoleBlockEntity> {
    public static final Identifier TEXTURE = Steveparty.id("textures/block/goal_pole_flag.png");
    /** Greyscale version of the flag, multiplied by the colour of a dyed flag. */
    public static final Identifier DYEABLE_TEXTURE = Steveparty.id("textures/block/goal_pole_flag_dyeable.png");
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
        if (!state.contains(GoalPoleBlock.FLAG) || !state.get(GoalPoleBlock.FLAG)) return;
        World world = entity.getWorld();
        if (world == null) return;

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

        matrices.push();
        matrices.translate(0.5f, 0f, 0.5f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facingDegrees(state)));
        matrices.translate(-0.5f, 0f, -0.5f);
        // The whole flag swings a little around the pole during gusts
        float swing = FlagWind.swing(seconds, phase, gust);
        if (swing != 0f) {
            matrices.translate(POLE_CENTER / 16f, 0f, POLE_CENTER / 16f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(swing));
            matrices.translate(-POLE_CENTER / 16f, 0f, -POLE_CENTER / 16f);
        }
        MatrixStack.Entry entry = matrices.peek();
        // Dyed: the greyscale flag multiplied by its colour. Undyed: the original red texture, untouched
        int flagColor = entity.getFlagColor();
        boolean dyed = flagColor != FlagItem.NO_COLOR;
        int color = dyed ? 0xFF000000 | flagColor : 0xFFFFFFFF;
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(dyed ? DYEABLE_TEXTURE : TEXTURE));
        for (int i = 0; i < columns; i++) {
            float u0 = U_MAX * i / columns, u1 = U_MAX * (i + 1) / columns;
            // Front (north side, normal -z at rest), counter-clockwise as seen from the north
            vertex(buffer, entry, i, TOP_Y, u0, 0f, light, color, 1f);
            vertex(buffer, entry, i, BOTTOM_Y, u0, V_MAX, light, color, 1f);
            vertex(buffer, entry, i + 1, BOTTOM_Y, u1, V_MAX, light, color, 1f);
            vertex(buffer, entry, i + 1, TOP_Y, u1, 0f, light, color, 1f);
            // Back (south side): same texels, reversed winding and normal
            vertex(buffer, entry, i, TOP_Y, u0, 0f, light, color, -1f);
            vertex(buffer, entry, i + 1, TOP_Y, u1, 0f, light, color, -1f);
            vertex(buffer, entry, i + 1, BOTTOM_Y, u1, V_MAX, light, color, -1f);
            vertex(buffer, entry, i, BOTTOM_Y, u0, V_MAX, light, color, -1f);
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

    @Override
    public int getRenderDistance() {
        return RENDER_DISTANCE;
    }
}
