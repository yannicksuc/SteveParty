package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.board.BrushAim;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The paint the Tile Linker Brush leaves on the tiles it strokes, seen by its holder (all client side, a few quads): a
 * blot on each tile reached and a band from tile to tile along the stroke, in the colour of the brush's level (the
 * redstone wire of that power), pale where the stroke goes over a link again (erasing it). The paint stays wet while
 * the stroke goes on, and fades away once the button is released.
 */
final class BrushTrail {
    private static final int FADE_TICKS = 40;
    private static final int MAX_MARKS = 512;
    private static final float WET_ALPHA = 0.8f;
    private static final double BAND = 0.3, BLOT = 0.55, LIFT = 0.03;
    private static final int ERASED = 0xE8E8E8;
    /** The look is followed between two ticks as the server does: a quick sweep paints every tile it crosses. */
    private static final float SWEEP_STEP = 1.5f;
    private static final int MAX_SWEEP_STEPS = 16;

    private static final class Stroke {
        final int rgb;
        long ended = -1;

        Stroke(int rgb) {
            this.rgb = rgb;
        }
    }

    private record Mark(Vec3d from, Vec3d to, boolean erased, Stroke stroke) {
    }

    private static final List<Mark> MARKS = new ArrayList<>();
    private static @Nullable ClientWorld trailWorld;
    private static @Nullable Stroke current;
    private static @Nullable BlockPos last;
    private static @Nullable Vec3d lastAt;
    private static float pitch, yaw;

    private BrushTrail() {
    }

    static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(BrushTrail::tick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(BrushTrail::render);
    }

    /** Whether the local player is painting: the brush held in use. */
    static boolean painting(@Nullable ClientPlayerEntity player) {
        return player != null && player.isUsingItem() && TileLinkerBrush.isBrush(player.getActiveItem());
    }

    /** The tile the current stroke last reached, or null when not painting. */
    static @Nullable BlockPos lastTile() {
        return current == null ? null : last;
    }

    /** The paint of a brush: the redstone wire of its level (full power for the powered slot). */
    static int color(ItemStack brush) {
        int level = TileLinkerBrush.level(brush);
        return RedstoneWireBlock.getWireColor(level == TileLinkerBrush.POWERED ? 15 : level) & 0xFFFFFF;
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (world != trailWorld) {
            MARKS.clear();
            current = null;
            trailWorld = world;
        }
        if (player == null || world == null) return;
        long now = world.getTime();
        if (painting(player)) {
            if (current == null) {
                current = new Stroke(color(player.getActiveItem()));
                last = null;
                lastAt = null;
                pitch = player.getPitch();
                yaw = player.getYaw();
            }
            sweep(world, player);
        } else if (current != null) {
            current.ended = now;
            current = null;
        }
        if (!MARKS.isEmpty()) MARKS.removeIf(mark -> mark.stroke.ended >= 0 && now - mark.stroke.ended > FADE_TICKS);
    }

    private static void sweep(ClientWorld world, ClientPlayerEntity player) {
        float toPitch = player.getPitch(), toYaw = player.getYaw();
        float turned = Math.max(Math.abs(toPitch - pitch), Math.abs(MathHelper.wrapDegrees(toYaw - yaw)));
        int steps = MathHelper.clamp(MathHelper.ceil(turned / SWEEP_STEP), 1, MAX_SWEEP_STEPS);
        for (int i = 1; i <= steps; i++) {
            float t = (float) i / steps;
            BlockPos aimed = BrushAim.aimed(player, world, MathHelper.lerp(t, pitch, toPitch),
                    yaw + MathHelper.wrapDegrees(toYaw - yaw) * t);
            if (aimed != null && !aimed.equals(last)) reach(world, player, aimed);
        }
        pitch = toPitch;
        yaw = toYaw;
    }

    /** The stroke reaches a tile: a blot on it, and the band from the previous one (pale over a link it erases). */
    private static void reach(ClientWorld world, ClientPlayerEntity player, BlockPos tile) {
        Vec3d at = BoardSpaces.standPos(world, tile).add(0, LIFT, 0);
        boolean erased = last != null && BrushOverlay.linked(world, player.getActiveItem(), last, tile);
        MARKS.add(new Mark(lastAt == null ? at : lastAt, at, erased, current));
        if (MARKS.size() > MAX_MARKS) MARKS.removeFirst();
        last = tile.toImmutable();
        lastAt = at;
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.matrixStack();
        if (MARKS.isEmpty() || client.world == null || matrices == null) return;
        float now = client.world.getTime() + context.tickCounter().getTickDelta(true);
        Vec3d cam = context.camera().getPos();
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getDebugQuads());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        for (Mark mark : MARKS) {
            float alpha = WET_ALPHA;
            if (mark.stroke.ended >= 0) alpha *= 1 - MathHelper.clamp((now - mark.stroke.ended) / FADE_TICKS, 0, 1);
            if (alpha < 0.02f) continue;
            int color = ((int) (alpha * (mark.erased ? 0.6f : 1f) * 255) << 24) | (mark.erased ? ERASED : mark.stroke.rgb);
            Vec3d a = mark.from.subtract(cam), b = mark.to.subtract(cam);
            double dx = b.x - a.x, dz = b.z - a.z, length = Math.sqrt(dx * dx + dz * dz);
            if (length > 1.0E-3) {
                double sx = -dz / length * BAND / 2, sz = dx / length * BAND / 2;
                quad(consumer, matrix, a.x - sx, a.y, a.z - sz, a.x + sx, a.y, a.z + sz,
                        b.x + sx, b.y, b.z + sz, b.x - sx, b.y, b.z - sz, color);
            }
            // The blot a little above the band: no flicker where they overlap
            double r = BLOT / 2, y = b.y + 0.004;
            quad(consumer, matrix, b.x - r, y, b.z - r, b.x + r, y, b.z - r, b.x + r, y, b.z + r, b.x - r, y, b.z + r, color);
        }
        consumers.draw(RenderLayer.getDebugQuads());
    }

    /** A quad (camera space), both sides drawn. */
    private static void quad(VertexConsumer consumer, Matrix4f matrix, double ax, double ay, double az, double bx, double by,
                             double bz, double cx, double cy, double cz, double dx, double dy, double dz, int color) {
        consumer.vertex(matrix, (float) ax, (float) ay, (float) az).color(color);
        consumer.vertex(matrix, (float) bx, (float) by, (float) bz).color(color);
        consumer.vertex(matrix, (float) cx, (float) cy, (float) cz).color(color);
        consumer.vertex(matrix, (float) dx, (float) dy, (float) dz).color(color);
        consumer.vertex(matrix, (float) dx, (float) dy, (float) dz).color(color);
        consumer.vertex(matrix, (float) cx, (float) cy, (float) cz).color(color);
        consumer.vertex(matrix, (float) bx, (float) by, (float) bz).color(color);
        consumer.vertex(matrix, (float) ax, (float) ay, (float) az).color(color);
    }
}
