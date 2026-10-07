package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.board.BoardLinks;
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
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The paint the Tile Linker Brush leaves on the tiles it strokes, seen by its holder (all client side, a few quads): a
 * blot on each tile reached, in the colour of the brush's level (the redstone wire of that power), and a band from
 * tile to tile along the stroke in the colour of what it did: green, a link made; red, a link erased; white, nothing.
 * While the stroke is held, arrows flow along each of its links the way they lead, in the same colours, and the
 * links already leaving the aimed tile flow in white. Under the crosshair, every frame, the brush also lays a wet
 * ribbon of its colour exactly where the player looks, on any block: instant feedback of the stroke. The paint fades
 * away once the button is released.
 */
final class BrushTrail {
    private static final int FADE_TICKS = 40;
    private static final int MAX_MARKS = 512;
    private static final float WET_ALPHA = 0.8f;
    private static final double BAND = 0.3, BLOT = 0.55, LIFT = 0.03;
    static final int LINKED = 0x4CFF4C, ERASED = 0xFF4040, NOTHING = 0xF0F0F0;
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

    /** What reaching a tile did: the colour of its band and arrows. */
    private record Mark(@Nullable BlockPos fromTile, BlockPos toTile, Vec3d from, Vec3d to, int outcome, Stroke stroke) {
    }

    /** A point of the freehand ribbon: where the crosshair met a block, on which face; {@code joined} to the previous. */
    private record Dab(Vec3d at, Direction face, boolean joined, Stroke stroke) {
    }

    private static final int MAX_DABS = 4096;
    private static final double RIBBON = 0.16, DAB_SPACING = 0.06, MAX_JOIN = 1.2, SURFACE_LIFT = 0.012;

    private static final List<Mark> MARKS = new ArrayList<>();
    private static final List<Dab> DABS = new ArrayList<>();
    private static @Nullable Dab lastDab;
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
            DABS.clear();
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
                lastDab = null;
                pitch = player.getPitch();
                yaw = player.getYaw();
            }
            sweep(world, player);
        } else if (current != null) {
            current.ended = now;
            current = null;
        }
        if (!MARKS.isEmpty()) MARKS.removeIf(mark -> mark.stroke.ended >= 0 && now - mark.stroke.ended > FADE_TICKS);
        if (!DABS.isEmpty()) DABS.removeIf(dab -> dab.stroke.ended >= 0 && now - dab.stroke.ended > FADE_TICKS);
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
        MARKS.add(new Mark(last, tile.toImmutable(), lastAt == null ? at : lastAt, at, outcome(world, player.getActiveItem(), last, tile), current));
        if (MARKS.size() > MAX_MARKS) MARKS.removeFirst();
        last = tile.toImmutable();
        lastAt = at;
    }

    /** Whether the held stroke went from {@code from} to {@code to} (its own arrows show it). */
    private static boolean inStroke(BlockPos from, BlockPos to) {
        for (Mark mark : MARKS) if (mark.stroke == current && from.equals(mark.fromTile) && to.equals(mark.toTile)) return true;
        return false;
    }

    /** What going from {@code from} to {@code to} does (as the server will): erase their link, make one, or nothing. */
    static int outcome(ClientWorld world, ItemStack brush, @Nullable BlockPos from, BlockPos to) {
        if (from == null || from.equals(to)) return NOTHING;
        if (BrushOverlay.linked(world, brush, from, to)) return ERASED;
        return BoardLinks.container(world, from) != null && BoardLinks.container(world, to) instanceof BoardSpaceBlockEntity
                ? LINKED : NOTHING;
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.matrixStack();
        if (client.world == null || matrices == null) return;
        if (current != null && client.player != null) paintUnderCrosshair(client, context);
        if (MARKS.isEmpty() && DABS.isEmpty()) return;
        float now = client.world.getTime() + context.tickCounter().getTickDelta(true);
        Vec3d cam = context.camera().getPos();
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getDebugQuads());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        ribbon(consumer, matrix, cam, now);
        for (Mark mark : MARKS) {
            float alpha = WET_ALPHA;
            if (mark.stroke.ended >= 0) alpha *= 1 - MathHelper.clamp((now - mark.stroke.ended) / FADE_TICKS, 0, 1);
            if (alpha < 0.02f) continue;
            int band = ((int) (alpha * 0.85f * 255) << 24) | mark.outcome;
            int color = ((int) (alpha * 255) << 24) | mark.stroke.rgb;
            Vec3d a = mark.from.subtract(cam), b = mark.to.subtract(cam);
            double dx = b.x - a.x, dz = b.z - a.z, length = Math.sqrt(dx * dx + dz * dz);
            if (length > 1.0E-3) {
                double sx = -dz / length * BAND / 2, sz = dx / length * BAND / 2;
                quad(consumer, matrix, a.x - sx, a.y, a.z - sz, a.x + sx, a.y, a.z + sz,
                        b.x + sx, b.y, b.z + sz, b.x - sx, b.y, b.z - sz, band);
            }
            // The blot a little above the band: no flicker where they overlap
            double r = BLOT / 2, y = b.y + 0.004;
            quad(consumer, matrix, b.x - r, y, b.z - r, b.x + r, y, b.z - r, b.x + r, y, b.z + r, b.x - r, y, b.z + r, color);
        }
        consumers.draw(RenderLayer.getDebugQuads());
        if (current != null) arrows(context, client, consumers, now);
    }

    /** Every frame: a dab where the crosshair meets a block (the brush's reach), spaced a little apart. */
    private static void paintUnderCrosshair(MinecraftClient client, WorldRenderContext context) {
        float tickDelta = context.tickCounter().getTickDelta(true);
        Vec3d eye = context.camera().getPos();
        Vec3d end = eye.add(client.player.getRotationVec(tickDelta).multiply(BrushAim.REACH));
        HitResult hit = client.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, client.player));
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            lastDab = null; // looking at the sky: the ribbon breaks
            return;
        }
        Direction face = blockHit.getSide();
        Vec3d at = hit.getPos().add(Vec3d.of(face.getVector()).multiply(SURFACE_LIFT));
        if (lastDab != null && lastDab.face == face && lastDab.at.squaredDistanceTo(at) < DAB_SPACING * DAB_SPACING) return;
        boolean joined = lastDab != null && lastDab.face == face && lastDab.at.squaredDistanceTo(at) < MAX_JOIN * MAX_JOIN;
        lastDab = new Dab(at, face, joined, current);
        DABS.add(lastDab);
        if (DABS.size() > MAX_DABS) DABS.removeFirst();
    }

    /** The freehand ribbon: a square dab on each point, a band on its face to the previous one. */
    private static void ribbon(VertexConsumer consumer, Matrix4f matrix, Vec3d cam, float now) {
        Dab previous = null;
        for (Dab dab : DABS) {
            // Opaque while wet: the dabs overlap, a see-through paint would come out blotchy
            float alpha = 1f;
            if (dab.stroke.ended >= 0) alpha *= 1 - MathHelper.clamp((now - dab.stroke.ended) / FADE_TICKS, 0, 1);
            if (alpha < 0.02f) {
                previous = dab;
                continue;
            }
            int color = ((int) (alpha * 255) << 24) | dab.stroke.rgb;
            Vec3d p = dab.at.subtract(cam);
            Vec3d u = tangent(dab.face, true).multiply(RIBBON / 2), v = tangent(dab.face, false).multiply(RIBBON / 2);
            quad(consumer, matrix, p.subtract(u).subtract(v), p.add(u).subtract(v), p.add(u).add(v), p.subtract(u).add(v), color);
            if (dab.joined && previous != null && previous.stroke == dab.stroke) {
                Vec3d q = previous.at.subtract(cam);
                Vec3d along = p.subtract(q);
                Vec3d side = along.crossProduct(Vec3d.of(dab.face.getVector()));
                if (side.lengthSquared() > 1.0E-8) {
                    side = side.normalize().multiply(RIBBON / 2);
                    quad(consumer, matrix, q.subtract(side), q.add(side), p.add(side), p.subtract(side), color);
                }
            }
            previous = dab;
        }
    }

    /** One of the two axes lying on a face. */
    private static Vec3d tangent(Direction face, boolean first) {
        return switch (face.getAxis()) {
            case Y -> first ? new Vec3d(1, 0, 0) : new Vec3d(0, 0, 1);
            case X -> first ? new Vec3d(0, 1, 0) : new Vec3d(0, 0, 1);
            case Z -> first ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        };
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color) {
        quad(consumer, matrix, a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, d.x, d.y, d.z, color);
    }

    /** The live preview of the held stroke: arrows flowing along its links, and along those leaving the aimed tile. */
    private static void arrows(WorldRenderContext context, MinecraftClient client, VertexConsumerProvider.Immediate consumers, float now) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;
        MatrixStack matrices = context.matrixStack();
        double phase = now / 20.0 * 2.5;
        Vec3d up = new Vec3d(0, 0.12, 0);
        for (Mark mark : MARKS) {
            if (mark.stroke != current || mark.from.equals(mark.to)) continue;
            WorldDraw.path(matrices, consumers, context.camera(), mark.from.add(up), mark.to.add(up), 0xF0000000 | mark.outcome,
                    0.45, 0.45, phase, 0.3, 0);
        }
        BlockPos aimed = BrushAim.aimed(player, client.world, context.tickCounter().getTickDelta(true));
        CartridgeContainerBlockEntity container = aimed == null ? null : BoardLinks.container(client.world, aimed);
        if (container != null) {
            ItemStack brush = player.getActiveItem();
            Vec3d from = BoardSpaces.standPos(client.world, aimed).add(0, LIFT, 0).add(up);
            for (BlockPos to : BoardLinks.links(container, BoardLinks.slotOf(container, TileLinkerBrush.level(brush)))) {
                if (inStroke(aimed, to)) continue;
                WorldDraw.path(matrices, consumers, context.camera(), from, BoardSpaces.standPos(client.world, to).add(0, LIFT, 0).add(up),
                        0xC0000000 | NOTHING, 0.4, 0.45, phase, 0.3, 0);
            }
        }
        consumers.draw();
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
