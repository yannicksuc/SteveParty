package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.client.gui.party.OutcomeRouletteHud;
import fr.lordfinn.steveparty.hud.OutcomeRoulette;
import fr.lordfinn.steveparty.payloads.custom.OutcomeRoulettePayload;
import fr.lordfinn.steveparty.payloads.custom.OutcomeRoulettePayload.Line;
import fr.lordfinn.steveparty.utils.Argb;
import fr.lordfinn.steveparty.utils.Easing;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * The outcome roulette's panel ({@link OutcomeRouletteHud} keeps its clock), floating in the world over what triggered
 * it (the roulette's anchor: the middle of its bottom edge), turned to each player's camera, so that the show under it
 * (the Mistigri and his die) stays in sight:
 * <pre>
 *        LordFinn lands on the Mistigri!
 *              Which sentence?
 *   [ (o) Lose 10 coins              29% ]
 *   [ (*) Lose a star                 7% ]   &lt;- lit: gold
 *   [ (>) Move back 3 spaces         21% ]
 *   ==========                               &lt;- the reading time running out
 * </pre>
 * One whole sentence per line (wrapped on a second row when the panel is too narrow for it), its icon on the left, its
 * chance on the right in its tone's colour. The lines pop in one after the other; the light runs down them and stops
 * on the result, which flashes while the others fade. Seen through the terrain (drawn last, without depth test, each
 * piece over the one before: nothing fights), its scale growing with the distance (up to a cap) to stay readable.
 * Too far, behind the player or off the screen: the HUD's strip stands in for it.
 */
public final class OutcomeRoulettePanel {
    /** Text pixels: the panel's width bounds, its margin, a line's height, the extra height of a wrapped line. */
    private static final int MIN_WIDTH = 170, MAX_WIDTH = 260, PAD = 7, ICON = 10, LINE_H = 13, LINE_GAP = 3, WRAP_EXTRA = 9;
    /** Blocks per text pixel: near, growing with the distance, at most. */
    private static final float SCALE_NEAR = 0.018f, SCALE_PER_BLOCK = 0.0023f, SCALE_MAX = 0.045f;
    /** Beyond this many blocks the HUD's strip takes over. */
    private static final double FAR = 24;
    private static final float POP_TICKS = 6, LINE_STAGGER = 3;
    private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    private static final int TEXT = 0xFFFFFFFF, TEXT_DARK = 0xFF404040, CAPTION = 0xFFCFE8EC, TITLE_MINE = 0xFFFFE27A;
    private static final int ROW = 0xF2FFFFFF, ROW_EDGE = 0xFF003640, FLASH = 0xFFFFFFFF;
    private static final int WARN = 0xFFA02A1E, MINE = 0xFF1F6E2A, SOFT = 0xFF6B6B6B;

    private OutcomeRoulettePanel() {
    }

    public static void initialize() {
        WorldRenderEvents.LAST.register(OutcomeRoulettePanel::render);
    }

    private static void render(WorldRenderContext context) {
        OutcomeRoulettePayload roulette = OutcomeRouletteHud.current();
        if (roulette == null || roulette.anchor().isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.matrixStack();
        if (client.world == null || matrices == null || client.options.hudHidden) return;
        float alpha = OutcomeRouletteHud.alpha();
        if (alpha < 0.05f) return;
        Camera camera = context.camera();
        Vec3d anchor = roulette.anchor().get();
        double distance = camera.getPos().distanceTo(anchor);
        if (distance > FAR) return;
        float scale = MathHelper.clamp((float) distance * SCALE_PER_BLOCK, SCALE_NEAR, SCALE_MAX);

        TextRenderer font = client.textRenderer;
        Layout layout = layout(font, roulette);
        Vec3d middle = anchor.add(0, layout.height * scale / 2, 0);
        if (!onScreen(client, camera, middle)) return;
        OutcomeRouletteHud.panelSeen();

        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        consumers.draw(); // what was batched before stays under it
        Vec3d cam = camera.getPos();
        matrices.push();
        matrices.translate(anchor.x - cam.x, anchor.y - cam.y, anchor.z - cam.z);
        matrices.multiply(camera.getRotation());
        matrices.scale(scale, -scale, scale);
        // Label space: text pixels, y down, its bottom edge on the anchor
        matrices.translate(-layout.width / 2f, -layout.height, 0);
        draw(matrices, consumers, font, roulette, layout, alpha);
        matrices.pop();
        consumers.draw();
    }

    /** The middle of the panel is in front of the camera, within its field of view. */
    private static boolean onScreen(MinecraftClient client, Camera camera, Vec3d middle) {
        Vec3d to = middle.subtract(camera.getPos());
        Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        double ahead = to.dotProduct(look);
        if (ahead <= 0.5) return false;
        Vec3d right = look.crossProduct(new Vec3d(0, 1, 0));
        if (right.lengthSquared() < 1.0E-6) return true; // looking straight up or down at it
        right = right.normalize();
        Vec3d up = right.crossProduct(look).normalize();
        double vertical = Math.tan(Math.toRadians(client.options.getFov().getValue() / 2.0));
        double aspect = client.getWindow().getFramebufferWidth() / (double) Math.max(1, client.getWindow().getFramebufferHeight());
        return Math.abs(to.dotProduct(right)) / ahead < vertical * aspect && Math.abs(to.dotProduct(up)) / ahead < vertical;
    }

    /** The panel's size and each line's rows of text (text pixels). */
    private record Layout(int width, int height, int head, List<List<OrderedText>> texts) {
    }

    private static Layout layout(TextRenderer font, OutcomeRoulettePayload roulette) {
        int widest = Math.max(font.getWidth(roulette.title()) + 2, font.getWidth(roulette.caption()));
        for (Line line : roulette.lines()) widest = Math.max(widest, 3 + ICON + 4 + font.getWidth(line.text()) + chanceRoom(font, line) + 5);
        int width = Math.clamp(widest + 2 * PAD, MIN_WIDTH, MAX_WIDTH);
        int inner = width - 2 * PAD;
        List<List<OrderedText>> texts = new ArrayList<>();
        int height = PAD + head(roulette);
        for (Line line : roulette.lines()) {
            List<OrderedText> wrapped = UiText.wrap(font, line.text(), inner - (3 + ICON + 4) - chanceRoom(font, line) - 5);
            if (wrapped.size() > 2) wrapped = wrapped.subList(0, 2);
            texts.add(wrapped);
            height += LINE_H + (wrapped.size() - 1) * WRAP_EXTRA + LINE_GAP;
        }
        return new Layout(width, height + 4 + PAD, head(roulette), texts);
    }

    private static int head(OutcomeRoulettePayload roulette) {
        return 11 + (roulette.caption().getString().isEmpty() ? 0 : 10) + 4;
    }

    private static int chanceRoom(TextRenderer font, Line line) {
        return line.chance() < 0 ? 0 : font.getWidth(line.chance() + "%") + 6;
    }

    private static void draw(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, TextRenderer font,
                             OutcomeRoulettePayload roulette, Layout layout, float alpha) {
        int width = layout.width, inner = width - 2 * PAD;
        double age = OutcomeRouletteHud.age();
        WorldDraw.plateSeeThrough(matrices, consumers, WorldDraw.Plate.TEAL, 0, 0, width, layout.height);
        Text title = roulette.title().copy().styled(style -> style.withBold(true));
        centered(matrices, consumers, font, title.asOrderedText(), PAD, PAD, inner,
                Argb.fade(OutcomeRouletteHud.mine(roulette) ? TITLE_MINE : TEXT, alpha));
        if (!roulette.caption().getString().isEmpty())
            centered(matrices, consumers, font, roulette.caption().asOrderedText(), PAD, PAD + 11, inner, Argb.fade(CAPTION, alpha));

        int lit = OutcomeRouletteHud.litLine();
        double stoppedFor = age - (OutcomeRoulette.REVEAL_TICKS + OutcomeRoulette.SPIN_TICKS);
        boolean joinedLate = roulette.elapsed() > POP_TICKS;
        int y = PAD + layout.head;
        for (int i = 0; i < roulette.lines().size(); i++) {
            List<OrderedText> text = layout.texts.get(i);
            int height = LINE_H + (text.size() - 1) * WRAP_EXTRA;
            float pop = joinedLate ? 1 : Easing.clamp01((float) ((age - i * LINE_STAGGER) / POP_TICKS));
            if (pop > 0) {
                float lineAlpha = alpha * Easing.easeOutCubic(pop);
                boolean result = stoppedFor >= 0 && i == roulette.result();
                if (stoppedFor >= 0 && !result) lineAlpha *= 0.45f; // the result alone stands out
                drawLine(matrices, consumers, font, roulette.lines().get(i), text, PAD, y, inner, height, i == lit,
                        result ? stoppedFor : -1, pop, lineAlpha);
            }
            y += height + LINE_GAP;
        }
        // The reading time running out, under the lines
        if (age < OutcomeRoulette.REVEAL_TICKS) {
            float left = 1 - Easing.clamp01((float) (age / OutcomeRoulette.REVEAL_TICKS));
            float barWidth = inner * left, barX = PAD + (inner - barWidth) / 2;
            WorldDraw.fillSeeThrough(matrices, consumers, PAD, y + 1, PAD + inner, y + 3, Argb.fade(0x60000000, alpha));
            if (barWidth > 0) WorldDraw.fillSeeThrough(matrices, consumers, barX, y + 1, barX + barWidth, y + 3, Argb.fade(0xFFFFC900, alpha));
        }
    }

    /**
     * One outcome: a white row (a gold plate while lit, white flashes when the light stops on it), its icon, its
     * sentence, its chance. {@code stoppedFor}: ticks since the light stopped on it (-1: not the result, or not yet).
     */
    private static void drawLine(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, TextRenderer font, Line line,
                                 List<OrderedText> text, int x, int y, int width, int height, boolean lit, double stoppedFor,
                                 float pop, float alpha) {
        boolean flash = stoppedFor >= 0 && stoppedFor < OutcomeRouletteHud.FLASH_TICKS && ((int) (stoppedFor / 2.5)) % 2 == 0;
        float scale = 0.85f + 0.15f * Easing.easeOutBack(pop);
        if (stoppedFor >= 0 && stoppedFor < 8) scale *= 1 + 0.06f * (float) Math.sin(stoppedFor / 8 * Math.PI);
        matrices.push();
        matrices.translate(x + width / 2f, y + height / 2f, 0);
        matrices.scale(scale, scale, 1);
        matrices.translate(-(x + width / 2f), -(y + height / 2f), 0);
        if (flash) {
            WorldDraw.fillSeeThrough(matrices, consumers, x - 1, y - 1, x + width + 1, y + height + 1, Argb.fade(0xFFFFC900, alpha));
            WorldDraw.fillSeeThrough(matrices, consumers, x, y, x + width, y + height, Argb.fade(FLASH, alpha));
        } else if (lit) {
            WorldDraw.plateSeeThrough(matrices, consumers, WorldDraw.Plate.GOLD, x - 2, y - 2, x + width + 2, y + height + 2);
        } else {
            WorldDraw.fillSeeThrough(matrices, consumers, x, y + 1, x + width, y + height + 1, Argb.fade(0x50000000, alpha)); // its shadow
            WorldDraw.fillSeeThrough(matrices, consumers, x - 1, y - 1, x + width + 1, y + height, Argb.fade(ROW_EDGE, alpha));
            WorldDraw.fillSeeThrough(matrices, consumers, x, y, x + width, y + height - 1, Argb.fade(ROW, alpha));
        }
        int textY = y + (height - 8 - (text.size() - 1) * WRAP_EXTRA) / 2 + 1;
        if (!line.icon().isEmpty() && alpha > 0.3f) icon(matrices, consumers, line, x + 3, y + (height - ICON) / 2f);
        int textX = x + 3 + ICON + 4, right = x + width - 5;
        if (line.chance() >= 0) {
            String chance = line.chance() + "%";
            int colour = switch (line.tone()) {
                case BAD -> WARN;
                case GOOD -> MINE;
                case NEUTRAL -> SOFT;
            };
            text(matrices, consumers, font, Text.literal(chance).asOrderedText(), right - font.getWidth(chance) + 1, textY, Argb.fade(colour, alpha));
            right -= font.getWidth(chance) + 6;
        }
        for (int row = 0; row < text.size(); row++)
            text(matrices, consumers, font, text.get(row), textX, textY + row * WRAP_EXTRA, Argb.fade(TEXT_DARK, alpha));
        matrices.pop();
    }

    /** An item, {@link #ICON} text pixels wide, its top-left corner at ({@code x}, {@code y}), flat and lit evenly. */
    private static void icon(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, Line line, float x, float y) {
        MinecraftClient client = MinecraftClient.getInstance();
        matrices.push();
        matrices.translate(x + ICON / 2f, y + ICON / 2f, -0.1f);
        // Label space is y down: the item's model is y up
        matrices.scale(ICON, -ICON, 0.01f);
        client.getItemRenderer().renderItem(line.icon(), ModelTransformationMode.GUI, LIGHT, OverlayTexture.DEFAULT_UV,
                matrices, consumers, client.world, 0);
        consumers.draw();
        matrices.pop();
    }

    private static void centered(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, TextRenderer font,
                                 OrderedText text, int x, int y, int width, int argb) {
        text(matrices, consumers, font, text, x + (width - font.getWidth(text)) / 2f, y, argb);
    }

    private static void text(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, TextRenderer font,
                             OrderedText text, float x, float y, int argb) {
        font.draw(text, x, y, argb, false, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, LIGHT);
        consumers.draw();
    }
}
