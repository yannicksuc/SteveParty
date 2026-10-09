package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileShape;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.board.BrushLinkable;
import fr.lordfinn.steveparty.board.BrushLinks;
import fr.lordfinn.steveparty.board.CartridgeLinks;
import fr.lordfinn.steveparty.board.BrushAim;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.world.RaycastContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The paint the Tile Linker Brush leaves, seen by its holder (all client side): under the crosshair, every frame, a
 * stroke of wet paint in the colour of the brush's level (the redstone wire of that power) exactly where the player
 * looks, on any surface and over the edges between them, with bristle streaks, ragged ends and a few drops. While the
 * stroke is held, arrows flow along each link it makes or erases, the way they lead (green, a link made; red, a link
 * erased), and the links already leaving the aimed tile flow in white. The paint fades away once the button is released.
 * A blob (the look lingering on one spot without board space, as the server finds it: see BrushAim.Blob) leaves a big
 * splat of paint there and an arrow to the cell it links.
 */
final class BrushTrail {
    private static final int FADE_TICKS = 60;
    private static final int MAX_MARKS = 512;
    private static final double LIFT = 0.03;
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

    /**
     * A point of the freehand paint: where the crosshair met a surface, its normal, the paint's width there, its light,
     * how far along the stroke it is (the bristle streaks run along it), and whether it continues the previous point.
     * A drop is a speck of paint flung beside the stroke.
     */
    private record Dab(Vec3d at, Vec3d normal, double width, int light, double along, float shade, boolean joined,
                       boolean drop, Stroke stroke) {
    }

    /**
     * The paint's textures, one per step of drying (0: wet): each step has a few more of its texels gone, the edges
     * first, then here and there (a cut-out layer can't fade: see PaintLayers).
     */
    private static final int DRY_STEPS = 8;
    private static final Identifier[] DAB_TEXTURES = new Identifier[DRY_STEPS], STREAK_TEXTURES = new Identifier[DRY_STEPS];

    static {
        for (int i = 0; i < DRY_STEPS; i++) {
            DAB_TEXTURES[i] = Steveparty.id("textures/misc/brush_dab_" + i + ".png");
            STREAK_TEXTURES[i] = Steveparty.id("textures/misc/brush_streak_" + i + ".png");
        }
    }
    private static final int MAX_DABS = 4096;
    private static final double WIDTH = 0.36, DAB_SPACING = 0.04, MAX_JOIN = 1.2;
    /** Blocks of stroke per repeat of the streak texture. */
    private static final double STREAK_LENGTH = 0.6;
    private static final Random SPLASH = new Random();
    /** How wide a blob's splat is. */
    private static final double BLOB_WIDTH = 1.1;
    private static final BrushAim.Blob BLOB = new BrushAim.Blob();
    /** The cells blobbed or whose ghost was erased in the held stroke: neither again before it ends (as on the server). */
    private static final Set<BlockPos> CELLS = new HashSet<>();

    private static final List<Mark> MARKS = new ArrayList<>();
    private static @Nullable Dab lastDab;
    private static final List<Dab> DABS = new ArrayList<>();
    private static @Nullable ClientWorld trailWorld;
    private static @Nullable Stroke current;
    private static @Nullable BlockPos last;
    private static @Nullable Vec3d lastAt;
    private static float pitch, yaw;

    private BrushTrail() {
    }

    static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(BrushTrail::tick);
        WorldRenderEvents.LAST.register(BrushTrail::render);
        // Last of the world, after the see-through blocks (stained glass, ice...) even composited apart (Fabulous
        // graphics): the paint writes its depth only after its colour, see paint()
        WorldRenderEvents.LAST.register(BrushTrail::renderPaint);
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
                CELLS.clear();
            }
            BlockPos aimed = sweep(world, player);
            BlockHitResult surface = aimed == null ? BrushAim.surface(player, world) : null;
            if (BLOB.tick(surface == null ? null : surface.getPos())) blob(world, player, surface);
        } else if (current != null) {
            current.ended = now;
            current = null;
        }
        if (!MARKS.isEmpty()) MARKS.removeIf(mark -> mark.stroke.ended >= 0 && now - mark.stroke.ended > FADE_TICKS);
        if (!DABS.isEmpty()) DABS.removeIf(dab -> dab.stroke.ended >= 0 && now - dab.stroke.ended > FADE_TICKS);
    }

    /** Follows the look since the last tick (as the server does); the board space or ghost aimed now, or null. */
    private static @Nullable BlockPos sweep(ClientWorld world, ClientPlayerEntity player) {
        float toPitch = player.getPitch(), toYaw = player.getYaw();
        float turned = Math.max(Math.abs(toPitch - pitch), Math.abs(MathHelper.wrapDegrees(toYaw - yaw)));
        int steps = MathHelper.clamp(MathHelper.ceil(turned / SWEEP_STEP), 1, MAX_SWEEP_STEPS);
        BlockPos aimed = null;
        for (int i = 1; i <= steps; i++) {
            float t = (float) i / steps;
            aimed = BrushAim.aimed(player, world, MathHelper.lerp(t, pitch, toPitch),
                    yaw + MathHelper.wrapDegrees(toYaw - yaw) * t, BrushOverlay.ghosts(), targets(world, player.getActiveItem()));
            if (aimed != null && !aimed.equals(last)) reach(world, player, aimed);
        }
        pitch = toPitch;
        yaw = toYaw;
        return aimed;
    }

    /** What the held stroke aims at besides holders and ghosts: what its last holder links (as the server finds it). */
    static Predicate<BlockPos> targets(ClientWorld world, ItemStack brush) {
        int level = TileLinkerBrush.level(brush);
        return target -> BrushLinks.aims(world, last, level, target);
    }

    /**
     * The stroke reaches a holder of a cartridge (a tile, a router, a Hop Switch...): a blot on it, and the band from
     * the previous one (pale over a link it erases).
     */
    private static void reach(ClientWorld world, ClientPlayerEntity player, BlockPos tile) {
        Vec3d at = BoardSpaces.standPos(world, tile).add(0, LIFT, 0);
        boolean target = !BrushLinks.isHolder(world, tile);
        // A ghost, a chest, a stall... is only a target (reached once): the stroke goes on from the last holder
        if (target && (last == null || inStroke(last, tile) || CELLS.contains(tile))) return;
        int outcome = outcome(world, player.getActiveItem(), last, tile);
        MARKS.add(new Mark(last, tile.toImmutable(), lastAt == null ? at : lastAt, at, outcome, current));
        if (MARKS.size() > MAX_MARKS) MARKS.removeFirst();
        if (target) {
            if (outcome != NOTHING) CELLS.add(tile.toImmutable());
            return;
        }
        last = tile.toImmutable();
        lastAt = at;
    }

    /**
     * A blob made on {@code surface}: a big splat of paint there, and (as the server links it) an arrow from the last
     * tile of the stroke to the cell it links.
     */
    private static void blob(ClientWorld world, ClientPlayerEntity player, BlockHitResult surface) {
        Vec3d normal = Vec3d.of(surface.getSide().getVector());
        Vec3d at = surface.getPos().add(normal.multiply(0.01));
        add(new Dab(at, normal, BLOB_WIDTH, WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(at)), 0, 1f,
                false, true, current));
        lastDab = null;
        if (last == null || BoardLinks.container(world, last) == null) return;
        // A blob plans a board space's cell: only from a board space or router
        BlockPos cell = BrushAim.blobCell(world, surface.getBlockPos());
        if (cell.equals(last) || CELLS.contains(cell) || BrushOverlay.linked(world, player.getActiveItem(), last, cell)) return;
        CELLS.add(cell);
        Vec3d to = BoardSpaces.standPos(world, cell).add(0, LIFT, 0);
        MARKS.add(new Mark(last, cell, lastAt == null ? to : lastAt, to, LINKED, current));
        if (MARKS.size() > MAX_MARKS) MARKS.removeFirst();
    }

    /** Whether the held stroke went from {@code from} to {@code to} (its own arrows show it). */
    private static boolean inStroke(BlockPos from, BlockPos to) {
        for (Mark mark : MARKS) if (mark.stroke == current && from.equals(mark.fromTile) && to.equals(mark.toTile)) return true;
        return false;
    }

    /** What going from {@code from} to {@code to} does (as the server will): erase their link, make one, or nothing. */
    static int outcome(ClientWorld world, ItemStack brush, @Nullable BlockPos from, BlockPos to) {
        if (from == null || from.equals(to) || CELLS.contains(to)) return NOTHING;
        return switch (BrushLinks.outcome(world, from, TileLinkerBrush.level(brush), to)) {
            case ERASE -> ERASED;
            case LINK -> LINKED;
            case NOTHING -> NOTHING;
        };
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
        if (current != null) arrows(context, client, consumers, now);
    }

    private static void renderPaint(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.matrixStack();
        if (client.world == null || matrices == null || DABS.isEmpty()) return;
        float now = client.world.getTime() + context.tickCounter().getTickDelta(true);
        paint(client.getBufferBuilders().getEntityVertexConsumers(), matrices.peek(), context.camera().getPos(), now);
    }

    /** A rub of the brush every so many blocks painted; where the last one was (along its stroke). */
    private static final double SOUND_EVERY = 0.7;
    private static double soundAlong;

    /**
     * Every frame: a point of paint where the crosshair meets a surface (the brush's reach), on a tile's slab where it
     * is drawn (see BoardSpaces#tileHit), a little apart from the previous one. From one surface to another (a floor
     * to a wall, over a step's edge) the paint folds over their edge; now and then a drop is flung beside it.
     */
    private static void paintUnderCrosshair(MinecraftClient client, WorldRenderContext context) {
        float tickDelta = context.tickCounter().getTickDelta(true);
        Vec3d eye = context.camera().getPos();
        Vec3d end = eye.add(client.player.getRotationVec(tickDelta).multiply(BrushAim.REACH));
        HitResult hit = client.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, client.player));
        TileShape.Hit tile = BoardSpaces.tileHit(client.world, eye, end, hit);
        Vec3d at, normal;
        if (tile != null) {
            at = tile.pos();
            normal = tile.normal();
        } else if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            at = hit.getPos();
            normal = Vec3d.of(blockHit.getSide().getVector());
        } else {
            lastDab = null; // looking at the sky: the stroke breaks
            return;
        }
        // Lifted off the surface, more when far (the depth buffer is coarser there): no flicker with it
        at = at.add(normal.multiply(0.006 + 0.0012 * eye.distanceTo(at)));
        double gap = lastDab == null ? Double.MAX_VALUE : lastDab.at.distanceTo(at);
        if (gap < DAB_SPACING) return;
        int light = WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(at));
        boolean joined = gap < MAX_JOIN;
        if (joined && lastDab.normal.dotProduct(normal) < 0.99) {
            // Another surface: the paint goes on over their common edge, folded there (on each side's surface)
            Vec3d edge = fold(lastDab.at, lastDab.normal, at, normal);
            if (edge == null || edge.distanceTo(lastDab.at) > MAX_JOIN || edge.distanceTo(at) > MAX_JOIN) {
                joined = false;
            } else {
                double before = lastDab.along + lastDab.at.distanceTo(edge);
                add(new Dab(edge, lastDab.normal, lastDab.width, lastDab.light, before, lastDab.shade, true, false, current));
                lastDab = new Dab(edge, normal, lastDab.width, light, before, lastDab.shade, true, false, current);
                add(lastDab);
                gap = edge.distanceTo(at);
            }
        }
        double along = joined ? lastDab.along + gap : 0;
        double width = WIDTH * (0.9 + 0.2 * SPLASH.nextDouble());
        lastDab = new Dab(at, normal, width, light, along, 0.9f + 0.12f * SPLASH.nextFloat(), joined, false, current);
        add(lastDab);
        // The bristles on the surface: a soft rub now and then along the stroke
        if (!joined || along - soundAlong >= SOUND_EVERY || along < soundAlong) {
            soundAlong = along;
            client.world.playSound(at.x, at.y, at.z, SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC, SoundCategory.PLAYERS,
                    0.22f, 1.25f + 0.35f * SPLASH.nextFloat(), false);
        }
        if (joined && SPLASH.nextFloat() < 0.1f) {
            Vec3d[] axes = axes(normal);
            double angle = SPLASH.nextDouble() * Math.PI * 2, reach = width * (0.7 + 0.8 * SPLASH.nextDouble());
            Vec3d offset = axes[0].multiply(Math.cos(angle) * reach).add(axes[1].multiply(Math.sin(angle) * reach));
            add(new Dab(at.add(offset), normal, width * (0.18 + 0.2 * SPLASH.nextDouble()), light, 0, 1f, false, true, current));
        }
    }

    /**
     * Where a stroke from {@code a} (on the plane of normal {@code na}) to {@code b} (normal {@code nb}) crosses the
     * line where the two planes meet: the point of that line nearest their middle; null for parallel planes.
     */
    private static @Nullable Vec3d fold(Vec3d a, Vec3d na, Vec3d b, Vec3d nb) {
        Vec3d d = na.crossProduct(nb);
        double length2 = d.lengthSquared();
        if (length2 < 1.0E-6) return null;
        double ha = na.dotProduct(a), hb = nb.dotProduct(b);
        Vec3d onLine = nb.crossProduct(d).multiply(ha).add(d.crossProduct(na).multiply(hb)).multiply(1 / length2);
        Vec3d unit = d.normalize(), middle = a.add(b).multiply(0.5);
        return onLine.add(unit.multiply(middle.subtract(onLine).dotProduct(unit)));
    }

    private static void add(Dab dab) {
        DABS.add(dab);
        if (DABS.size() > MAX_DABS) DABS.removeFirst();
    }

    /** Two unit axes lying on the surface of normal {@code n}. */
    private static Vec3d[] axes(Vec3d n) {
        Vec3d a = n.crossProduct(Math.abs(n.y) < 0.9 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0)).normalize();
        return new Vec3d[]{a, n.crossProduct(a).normalize()};
    }

    /** Whether point {@code i} continues point {@code i - 1} in one run (not a drop, same stroke). */
    private static boolean continues(int i) {
        if (i <= 0 || i >= DABS.size()) return false;
        Dab dab = DABS.get(i);
        return dab.joined && !dab.drop && !DABS.get(i - 1).drop && dab.stroke == DABS.get(i - 1).stroke;
    }

    /**
     * The freehand paint: each run of joined points is one continuous strip (its sides shared from point to point: no
     * square nor gap at the turns) textured with bristle streaks along the stroke, a round ragged dab at both ends, and
     * the drops. Wet, the colour varies a little from point to point; it fades with the stroke.
     */
    private static void paint(VertexConsumerProvider.Immediate consumers, MatrixStack.Entry entry, Vec3d cam, float now) {
        if (DABS.isEmpty()) return;
        // The colour, then its depth alone: blocks drawn after (Sodium's see-through ones) stay under the paint
        for (boolean depth : new boolean[]{false, true}) for (int step = 0; step < DRY_STEPS; step++) {
            RenderLayer streakLayer = depth ? PaintLayers.depth(STREAK_TEXTURES[step]) : PaintLayers.paint(STREAK_TEXTURES[step]);
            VertexConsumer streaks = consumers.getBuffer(streakLayer);
            for (int i = 1; i < DABS.size(); i++) {
                if (continues(i) && dryStep(DABS.get(i), now) == step) {
                    strip(streaks, entry, cam, DABS.get(i - 1), DABS.get(i), side(i - 1), side(i));
                }
            }
            consumers.draw(streakLayer);
            RenderLayer dabLayer = depth ? PaintLayers.depth(DAB_TEXTURES[step]) : PaintLayers.paint(DAB_TEXTURES[step]);
            VertexConsumer dabs = consumers.getBuffer(dabLayer);
            for (int i = 0; i < DABS.size(); i++) {
                Dab dab = DABS.get(i);
                if (!dab.drop && continues(i) && continues(i + 1) || dryStep(dab, now) != step) continue;
                Vec3d direction = dab.drop ? axes(dab.normal)[0] : direction(i);
                dab(dabs, entry, cam, dab, direction, dab.drop ? dab.width : dab.width * 1.15);
            }
            consumers.draw(dabLayer);
        }
    }

    /** How dry {@code dab} is: 0 while its stroke is held, then up to {@link #DRY_STEPS} - 1 as it fades away. */
    private static int dryStep(Dab dab, float now) {
        if (dab.stroke.ended < 0) return 0;
        float dried = MathHelper.clamp((now - dab.stroke.ended) / FADE_TICKS, 0, 0.999f);
        return (int) (dried * DRY_STEPS);
    }

    /** The stroke's direction at point {@code i} (from its neighbours in the same run), on its surface. */
    private static Vec3d direction(int i) {
        Dab dab = DABS.get(i);
        Vec3d from = continues(i) ? DABS.get(i - 1).at : dab.at;
        Vec3d to = continues(i + 1) ? DABS.get(i + 1).at : dab.at;
        Vec3d d = to.subtract(from);
        d = d.subtract(dab.normal.multiply(d.dotProduct(dab.normal)));
        return d.lengthSquared() < 1.0E-10 ? axes(dab.normal)[0] : d.normalize();
    }

    /** Half the paint's width across the stroke at point {@code i}, on its surface. */
    private static Vec3d side(int i) {
        Dab dab = DABS.get(i);
        return direction(i).crossProduct(dab.normal).normalize().multiply(dab.width / 2);
    }

    /** The strip from point {@code a} to point {@code b}, sharing its sides with the next and previous ones. */
    private static void strip(VertexConsumer consumer, MatrixStack.Entry entry, Vec3d cam, Dab a, Dab b, Vec3d sideA,
                              Vec3d sideB) {
        Vec3d pa = a.at.subtract(cam), pb = b.at.subtract(cam);
        float va = (float) (a.along / STREAK_LENGTH), vb = (float) (b.along / STREAK_LENGTH);
        vertex(consumer, entry, pa.add(sideA), 0, va, a);
        vertex(consumer, entry, pa.subtract(sideA), 1, va, a);
        vertex(consumer, entry, pb.subtract(sideB), 1, vb, b);
        vertex(consumer, entry, pb.add(sideB), 0, vb, b);
    }

    /** A round dab on {@code dab}'s surface, {@code size} wide, its bristle grooves along {@code direction}. */
    private static void dab(VertexConsumer consumer, MatrixStack.Entry entry, Vec3d cam, Dab dab, Vec3d direction, double size) {
        Vec3d u = direction.multiply(size / 2), v = direction.crossProduct(dab.normal).normalize().multiply(size / 2);
        Vec3d p = dab.at.subtract(cam);
        vertex(consumer, entry, p.subtract(u).subtract(v), 0, 0, dab);
        vertex(consumer, entry, p.add(u).subtract(v), 1, 0, dab);
        vertex(consumer, entry, p.add(u).add(v), 1, 1, dab);
        vertex(consumer, entry, p.subtract(u).add(v), 0, 1, dab);
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, Vec3d p, float u, float v, Dab dab) {
        int rgb = dab.stroke.rgb;
        float shade = dab.shade;
        consumer.vertex(entry, (float) p.x, (float) p.y, (float) p.z)
                .color(Math.min(1f, ((rgb >> 16) & 0xFF) / 255f * shade), Math.min(1f, ((rgb >> 8) & 0xFF) / 255f * shade),
                        Math.min(1f, (rgb & 0xFF) / 255f * shade), 1f)
                .texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(dab.light)
                .normal(entry, (float) dab.normal.x, (float) dab.normal.y, (float) dab.normal.z);
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
        ItemStack brush = player.getActiveItem();
        BlockPos aimed = BrushAim.aimed(player, client.world, context.tickCounter().getTickDelta(true), BrushOverlay.ghosts(),
                targets(client.world, brush));
        if (aimed != null && BrushLinks.isHolder(client.world, aimed)) {
            Vec3d from = BoardSpaces.standPos(client.world, aimed).add(0, LIFT, 0).add(up);
            for (BrushLinkable kind : BrushLinks.of(client.world, aimed, TileLinkerBrush.level(brush))) {
                for (BlockPos to : kind.targets(client.world)) {
                    // The ghosts' links are drawn by BrushOverlay
                    if (inStroke(aimed, to) || (kind instanceof CartridgeLinks.BoardPaths && BoardLinks.container(client.world, to) == null)) continue;
                    WorldDraw.path(matrices, consumers, context.camera(), from, BoardSpaces.standPos(client.world, to).add(0, LIFT, 0).add(up),
                            0xC0000000 | NOTHING, 0.4, 0.45, phase, 0.3, 0);
                }
            }
        }
        consumers.draw();
    }

}
