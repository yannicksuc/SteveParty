package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the top of a goal pole shows: the progress ("3 / 5") floating above the ball within {@link #PROGRESS_DISTANCE}
 * blocks, the ball lit with a soft glow while the goal is met, and, with a wrench in hand, the details (the goal of
 * each segment, the players followed and their points) of the pole looked at. Client only, from data the pole and its base already sync.
 */
final class GoalPoleTopDisplay {
    private static final String KEY = "hint.steveparty.goal_pole.";
    private static final double PROGRESS_DISTANCE_SQ = 16 * 16, DETAIL_DISTANCE_SQ = 10 * 10;
    private static final Identifier BALL_SPRITE = Steveparty.id("block/goal_pole_ball");
    private static final Identifier GLOW = Steveparty.id("textures/misc/goal_pole_glow.png");
    /** Ball of the model, in pixels: [5, 13.5, 5] to [11, 19.5, 11]. */
    private static final float BALL_MIN = 5f / 16f, BALL_MAX = 11f / 16f, BALL_BOTTOM = 13.5f / 16f, BALL_TOP = 19.5f / 16f;
    private static final float BALL_CENTER_Y = 16.5f / 16f;
    private static final double LABEL_Y = 1.62;
    private static final float PROGRESS_SCALE = 1f / 26f, DETAIL_SCALE = 1f / 48f;
    /** Looking at the pole down to this many blocks below its top shows the details. */
    private static final int COLUMN_REACH = 64;
    private static final int MET = 0x5EE05A, BACKGROUND = 0x70000000, DETAIL_BACKGROUND = 0x90000000;
    private static final int MAX_SEGMENT_LINES = 8, MAX_PLAYERS = 3;
    private static final Vector3f POSITION = new Vector3f();

    private GoalPoleTopDisplay() {}

    static void render(GoalPoleBlockEntity top, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers,
                       BlockEntityRenderDispatcher dispatcher) {
        World world = top.getWorld();
        if (world == null || dispatcher.camera == null || !top.isLinked()) {
            if (world != null && dispatcher.camera != null && WorldLabels.holdingWrench() && near(top, dispatcher, DETAIL_DISTANCE_SQ)) {
                WorldLabels.draw(matrices, consumers, dispatcher, 0.5, LABEL_Y, 0.5,
                        Text.translatable(KEY + "no_base").formatted(Formatting.GRAY), 0xFFFFFFFF, DETAIL_BACKGROUND, 0);
            }
            return;
        }
        if (top.isGoalMet()) drawBallLight(top, world, tickDelta, matrices, consumers, dispatcher);
        if (!near(top, dispatcher, PROGRESS_DISTANCE_SQ)) return;

        WorldLabels.draw(matrices, consumers, dispatcher, 0.5, LABEL_Y, 0.5, progress(top, world),
                top.isGoalMet() ? 0xFF000000 | MET : 0xFFFFFFFF, BACKGROUND, 0, PROGRESS_SCALE);

        BlockPos pos = top.getPos();
        if (WorldLabels.holdingWrench() && near(top, dispatcher, DETAIL_DISTANCE_SQ)
                && WorldLabels.lookingAtColumn(pos.getX(), pos.getZ(), pos.getY() - COLUMN_REACH, pos.getY())) {
            List<Text> lines = details(top, world);
            // Stacked upwards from just above the progress
            double y = LABEL_Y + 0.33;
            for (int i = 0; i < lines.size(); i++) {
                WorldLabels.draw(matrices, consumers, dispatcher, 0.5, y, 0.5, lines.get(i), 0xFFFFFFFF, DETAIL_BACKGROUND,
                        i - lines.size() + 1, DETAIL_SCALE);
            }
        }
    }

    private static boolean near(GoalPoleBlockEntity pole, BlockEntityRenderDispatcher dispatcher, double distanceSq) {
        BlockPos pos = pole.getPos();
        return dispatcher.camera.getPos().squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5) <= distanceSq;
    }

    /**
     * "3 / 5": the total and the number to reach. A goal per segment aims at the lowest goal not reached yet. Goals
     * that are not a number to reach ("less than 5") show as "3 · less than 5".
     */
    private static Text progress(GoalPoleBlockEntity top, World world) {
        GoalPoleBlockEntity aim = top;
        if (top.isPerSegment()) {
            Long best = null;
            BlockPos.Mutable cursor = top.getPos().mutableCopy();
            while (world.getBlockEntity(cursor) instanceof GoalPoleBlockEntity segment) {
                Long target = segment.displayTarget();
                if (!segment.isGoalMet() && target != null && (best == null || target < best)) {
                    best = target;
                    aim = segment;
                }
                cursor.move(0, -1, 0);
            }
        }
        long total = top.getTotal();
        Long target = aim.displayTarget();
        MutableText text = target != null ? Text.literal(total + " / " + target)
                : Text.literal(total + " · ").append(goal(aim));
        return top.isGoalMet() && aim == top ? Text.literal("✔ ").append(text) : text;
    }

    private static Text goal(GoalPoleBlockEntity segment) {
        return Text.translatable("gui.steveparty.goal_pole.comparator." + segment.getComparator().name().toLowerCase(Locale.ROOT),
                segment.getValue());
    }

    /** With a wrench: the goal (or each segment's, top first), the players followed and their points. */
    private static List<Text> details(GoalPoleBlockEntity top, World world) {
        List<Text> lines = new ArrayList<>();
        BlockPos.Mutable cursor = top.getPos().mutableCopy();
        List<GoalPoleBlockEntity> segments = new ArrayList<>();
        while (world.getBlockEntity(cursor) instanceof GoalPoleBlockEntity segment) {
            segments.add(segment);
            cursor.move(0, -1, 0);
        }
        GoalPoleBaseBlockEntity base = world.getBlockEntity(cursor) instanceof GoalPoleBaseBlockEntity b ? b : null;
        if (top.isPerSegment()) {
            int shown = Math.min(segments.size(), MAX_SEGMENT_LINES);
            for (int i = 0; i < shown; i++) {
                GoalPoleBlockEntity segment = segments.get(i);
                MutableText line = Text.translatable(KEY + "segment", segments.size() - i).formatted(Formatting.GRAY).append(" ")
                        .append(goal(segment).copy().formatted(Formatting.WHITE));
                if (segment.isGoalMet()) line.append(Text.literal(" ✔").formatted(Formatting.GREEN));
                lines.add(line);
            }
        } else {
            lines.add(Text.translatable(KEY + "goal").formatted(Formatting.GRAY).append(" ")
                    .append(goal(top).copy().formatted(Formatting.WHITE)));
        }
        if (base != null) {
            lines.add(Text.translatable(KEY + "players").formatted(Formatting.GRAY).append(" ")
                    .append(Text.literal(base.getSelector()).formatted(Formatting.WHITE)));
            lines.add(points(base));
        }
        return lines;
    }

    /** "Points: LordFinn 2 · Alex 1" (the best few), or "No points yet". */
    private static Text points(GoalPoleBaseBlockEntity base) {
        Map<String, Integer> points = base.getPointsView();
        List<Map.Entry<String, Integer>> best = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : points.entrySet()) {
            if (entry.getValue() != 0) best.add(entry);
        }
        if (best.isEmpty()) return Text.translatable(KEY + "points.none").formatted(Formatting.GRAY);
        best.sort(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
        MutableText list = Text.empty();
        for (int i = 0; i < Math.min(best.size(), MAX_PLAYERS); i++) {
            if (i > 0) list.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY));
            list.append(Text.literal(best.get(i).getKey() + " ").formatted(Formatting.WHITE))
                    .append(Text.literal(String.valueOf(best.get(i).getValue())).formatted(Formatting.YELLOW));
        }
        if (best.size() > MAX_PLAYERS) list.append(Text.literal(" …").formatted(Formatting.GRAY));
        return Text.translatable(KEY + "points").formatted(Formatting.GRAY).append(" ").append(list);
    }

    // ---------------------------------------------------------------- the lit ball

    /**
     * The goal is met: the ball is drawn again at full brightness (no face shading), with a soft glow around it that
     * breathes slowly, and pops for a second when the goal has just been reached.
     */
    private static void drawBallLight(GoalPoleBlockEntity top, World world, float tickDelta, MatrixStack matrices,
                                      VertexConsumerProvider consumers, BlockEntityRenderDispatcher dispatcher) {
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        Sprite sprite = MinecraftClient.getInstance().getBakedModelManager()
                .getAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).getSprite(BALL_SPRITE);
        VertexConsumer cutout = consumers.getBuffer(RenderLayer.getCutout());
        MatrixStack.Entry entry = matrices.peek();
        float e = 0.02f / 16f, min = BALL_MIN - e, max = BALL_MAX + e, bottom = BALL_BOTTOM - e, topY = BALL_TOP + e;
        float su0 = sprite.getFrameU(0f), su1 = sprite.getFrameU(6f / 16f), sv0 = sprite.getFrameV(0f), sv1 = sprite.getFrameV(6f / 16f);
        float tv0 = sprite.getFrameV(6f / 16f), tv1 = sprite.getFrameV(12f / 16f);
        int white = 0xFFFFFFFF;
        GoalPoleFlagRenderer.quad(cutout, entry, max, topY, min, min, topY, min, min, bottom, min, max, bottom, min, su0, sv0, su1, sv1, white, light, 0, 0, -1);
        GoalPoleFlagRenderer.quad(cutout, entry, min, topY, max, max, topY, max, max, bottom, max, min, bottom, max, su0, sv0, su1, sv1, white, light, 0, 0, 1);
        GoalPoleFlagRenderer.quad(cutout, entry, min, topY, min, min, topY, max, min, bottom, max, min, bottom, min, su0, sv0, su1, sv1, white, light, -1, 0, 0);
        GoalPoleFlagRenderer.quad(cutout, entry, max, topY, max, max, topY, min, max, bottom, min, max, bottom, max, su0, sv0, su1, sv1, white, light, 1, 0, 0);
        GoalPoleFlagRenderer.quad(cutout, entry, min, topY, min, max, topY, min, max, topY, max, min, topY, max, su0, tv0, su1, tv1, white, light, 0, 1, 0);

        // The glow: a camera-facing disc, unlit, fading out through its alpha
        double time = (world.getTime() % 2_400_000L) + tickDelta;
        double age = time - (top.getGoalMetTick() % 2_400_000L);
        float breathe = 0.5f + 0.5f * MathHelper.sin((float) (time / 32.0 * Math.PI * 2));
        float pop = age >= 0 && age < 20 ? 1f - (float) age / 20f : 0f;
        float size = 0.62f + 0.06f * breathe + 0.5f * pop;
        int alpha = (int) (255 * Math.min(1f, 0.6f + 0.25f * breathe + 0.4f * pop));
        int color = alpha << 24 | 0xFFFFFF;
        VertexConsumer glow = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(GLOW));
        matrices.push();
        matrices.translate(0.5f, BALL_CENTER_Y, 0.5f);
        matrices.multiply(dispatcher.camera.getRotation());
        Matrix4f pose = matrices.peek().getPositionMatrix();
        glowCorner(glow, pose, -size, -size, 0f, 1f, color);
        glowCorner(glow, pose, size, -size, 1f, 1f, color);
        glowCorner(glow, pose, size, size, 1f, 0f, color);
        glowCorner(glow, pose, -size, size, 0f, 0f, color);
        matrices.pop();
    }

    private static void glowCorner(VertexConsumer buffer, Matrix4f pose, float x, float y, float u, float v, int color) {
        pose.transformPosition(x, y, 0f, POSITION);
        buffer.vertex(POSITION.x, POSITION.y, POSITION.z, color, u, v, OverlayTexture.DEFAULT_UV,
                LightmapTextureManager.MAX_LIGHT_COORDINATE, 0f, 0f, 1f);
    }
}
