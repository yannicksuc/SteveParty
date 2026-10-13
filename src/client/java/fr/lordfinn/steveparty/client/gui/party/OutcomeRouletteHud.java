package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.client.board.WorldDraw;
import fr.lordfinn.steveparty.client.gui.FloatingInfo;
import fr.lordfinn.steveparty.client.gui.HudDepth;
import fr.lordfinn.steveparty.client.gui.ToolHud.Plate;
import fr.lordfinn.steveparty.hud.OutcomeRoulette;
import fr.lordfinn.steveparty.hud.OutcomeRoulette.Phase;
import fr.lordfinn.steveparty.payloads.custom.OutcomeRoulettePayload;
import fr.lordfinn.steveparty.payloads.custom.OutcomeRoulettePayload.Line;
import fr.lordfinn.steveparty.utils.Easing;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The outcome roulette shown ({@link OutcomeRoulette}, sent by {@link OutcomeRoulettePayload}): its clock and sounds,
 * for every player of the party, and its HUD strip. The roulette itself is a panel floating in the world over what
 * triggered it ({@code OutcomeRoulettePanel}), so that the show under it stays in sight; only when that panel is out of
 * a player's sight (behind them, off the screen, too far) does its card on the left of the screen ({@link FloatingInfo})
 * stand in for it, with every line like the panel.
 * <p>
 * The lines are read first (a low « dun-dun » when it opens); then the light runs down them, a tick at each step,
 * higher while fast, lower as it slows down; it stops on the result with a bell (and a sting for a loss, a jingle for a
 * mild one). Timed from the payload alone: every client lights the same line at the same moment, and one joining in
 * the middle picks it up where it is. It can't be skipped.
 */
public final class OutcomeRouletteHud {
    /** The result stays this long once the roulette is over, while what it says happens; then it fades. */
    public static final double LINGER_TICKS = 30, OUT_TICKS = 10;
    /** The result flashes this long when the light stops on it. */
    public static final double FLASH_TICKS = 14;
    /**
     * The card standing in for the panel (card pixels, see {@link FloatingInfo}): its narrowest, a row's height and the
     * gap under it, an icon, where a row's sentence starts, the reading bar's room.
     */
    private static final int MIN_WIDTH = 140, ROW_H = 11, ROW_GAP = 3, ICON = 9, ROW_TEXT_X = 2 + ICON + 3, BAR_H = 2;
    private static final float IN_TICKS = 6, LINE_STAGGER = 3;
    private static final int TRACK = WorldDraw.onPlate(0x60000000);
    /** The panel's colours ({@code OutcomeRoulettePanel}): opaque rows on the plate. */
    private static final int ROW = 0xFFFFFFFF, ROW_EDGE = 0xFF003640, CAPTION = 0xFF2C6E78,
            LIT = 0xFFFFD23F, LIT_EDGE = 0xFF7A4A00, TITLE_MINE = 0xFF9A6200, WARN = 0xFFA02A1E, GOOD = 0xFF1F6E2A, SOFT = 0xFF6B6B6B;
    /** The roulette's card ({@link FloatingInfo}). */
    public static final Object CARD = new Object();

    private static @Nullable OutcomeRoulettePayload shown;
    /** When it started, on the HUD's clock ({@link PartyHud#now}), and its step times. */
    private static double startedAt;
    private static double[] times = new double[0];
    /** The steps already ticked (-1: the second note of the reveal heard), and whether the stop was heard. */
    private static int heardSteps;
    private static boolean heardStop;
    /** When the strip appeared (it slides in), -1 while it is not shown. */
    private static double stripAt = -1;

    private OutcomeRouletteHud() {
    }

    public static void initialize() {
        HudRenderCallback.EVENT.register(OutcomeRouletteHud::render);
    }

    /** A roulette starts (or goes on: a player joining in the middle), or the one shown is stopped. */
    public static void onPayload(OutcomeRoulettePayload payload) {
        if (payload.isStop()) {
            if (shown != null && shown.id() == payload.id()) shown = null;
            return;
        }
        boolean same = shown != null && shown.id() == payload.id();
        shown = payload;
        startedAt = PartyHud.now() - payload.elapsed();
        times = OutcomeRoulette.stepTimes(payload.steps());
        if (same) return;
        double age = payload.elapsed();
        // Joining in the middle: what was already heard stays unheard
        heardSteps = age < OutcomeRoulette.REVEAL_TICKS ? 0 : OutcomeRoulette.stepsTaken(times, age - OutcomeRoulette.REVEAL_TICKS);
        heardStop = age >= OutcomeRoulette.REVEAL_TICKS + OutcomeRoulette.SPIN_TICKS;
        stripAt = -1;
        if (age < 10) {
            play(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.6f, 1.0f);
            play(SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO.value(), 0.5f, 0.6f);
        }
    }

    public static void clear() {
        shown = null;
    }

    // ------------------------------------------------------------------ what the panel reads

    /** The roulette shown, null when none (or once it has faded out). */
    public static @Nullable OutcomeRoulettePayload current() {
        if (shown != null && age() >= OutcomeRoulette.TOTAL_TICKS + LINGER_TICKS + OUT_TICKS) shown = null;
        return shown;
    }

    /** Ticks since the roulette shown started (smooth). */
    public static double age() {
        return PartyHud.now() - startedAt;
    }

    /** The lit line now, -1 while the lines are read. */
    public static int litLine() {
        return shown == null ? -1 : OutcomeRoulette.litLine(shown.lines().size(), times, age());
    }

    /** How faded in and out it is now: in over a few ticks (not for a player joining in the middle), out at the end. */
    public static float alpha() {
        if (shown == null) return 0;
        double age = age(), end = OutcomeRoulette.TOTAL_TICKS + LINGER_TICKS;
        float in = shown.elapsed() > IN_TICKS ? 1 : Easing.easeOutCubic(Easing.clamp01((float) (age / IN_TICKS)));
        float out = age < end ? 1 : 1 - Easing.clamp01((float) ((age - end) / OUT_TICKS));
        return Math.min(in, out);
    }

    /** It is the player's own roulette. */
    public static boolean mine(OutcomeRoulettePayload roulette) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && roulette.trigger().map(client.player.getUuid()::equals).orElse(false);
    }

    // ------------------------------------------------------------------ sounds

    private static void play(SoundEvent sound, float pitch, float volume) {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume));
    }

    /** The second note of the reveal, the ticks of the light, the stop: heard as they come (every frame). */
    private static void listen(OutcomeRoulettePayload roulette, double age) {
        if (age >= 6 && heardSteps == 0 && !heardStop && age < OutcomeRoulette.REVEAL_TICKS) {
            play(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.5f, 1.0f); // dun... dun
            heardSteps = -1;
        }
        if (age < OutcomeRoulette.REVEAL_TICKS) return;
        if (heardSteps < 0) heardSteps = 0;
        int taken = OutcomeRoulette.stepsTaken(times, age - OutcomeRoulette.REVEAL_TICKS);
        if (taken > heardSteps) {
            heardSteps = taken;
            if (taken < times.length) {
                float progress = taken / (float) Math.max(1, times.length);
                play(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 1.6f - 0.6f * progress, 0.8f);
            }
        }
        if (!heardStop && age >= OutcomeRoulette.REVEAL_TICKS + OutcomeRoulette.SPIN_TICKS) {
            heardStop = true;
            Line result = roulette.lines().get(Math.min(roulette.result(), roulette.lines().size() - 1));
            play(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f, 1.0f);
            switch (result.tone()) {
                case BAD -> play(SoundEvents.ENTITY_WITCH_CELEBRATE, 1.0f, 0.8f);
                case GOOD -> play(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.4f, 0.6f);
                case NEUTRAL -> play(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.0f, 0.8f);
            }
        }
    }

    // ------------------------------------------------------------------ the strip

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        OutcomeRoulettePayload roulette = current();
        if (roulette == null) return;
        double age = age();
        listen(roulette, age);
        MinecraftClient client = MinecraftClient.getInstance();
        boolean panel = roulette.anchor().isPresent() && !FloatingInfo.needsCard(CARD);
        if (client.options.hudHidden || panel) {
            stripAt = -1;
            return;
        }
        double now = PartyHud.now();
        if (stripAt < 0) stripAt = now;
        float in = Easing.easeOutCubic(Easing.clamp01((float) ((now - stripAt) / IN_TICKS)));
        float alpha = Math.min(alpha(), in);
        if (alpha < 0.05f) return;
        drawStrip(context, roulette, age, alpha, (1 - in) * 12f);
    }

    /**
     * Its card ({@link FloatingInfo}), the panel in short: who triggered it, the question, then every outcome (its icon,
     * its sentence, its chance in its tone's colour), the lit one on gold; the result flashes while the others fade, the
     * reading time runs out under them. A sentence too long for the card scrolls.
     */
    private static void drawStrip(DrawContext context, OutcomeRoulettePayload roulette, double age, float alpha, float slide) {
        TextRenderer font = HudDraw.font();
        List<Line> lines = roulette.lines();
        boolean caption = !roulette.caption().getString().isEmpty();
        int widest = Math.max(font.getWidth(roulette.title()) + 2, caption ? font.getWidth(roulette.caption()) : 0);
        for (Line line : lines) widest = Math.max(widest, ROW_TEXT_X + font.getWidth(line.text()) + chanceRoom(font, line) + 4);
        int width = Math.clamp(widest + 2 * FloatingInfo.PAD, MIN_WIDTH, FloatingInfo.MAX_WIDTH);
        int inner = width - 2 * FloatingInfo.PAD;
        int head = 10 + (caption ? 9 : 0) + 3;
        int height = FloatingInfo.PAD + head + lines.size() * (ROW_H + ROW_GAP) + BAR_H + FloatingInfo.PAD;
        int lit = OutcomeRoulette.litLine(lines.size(), times, age);
        double stoppedFor = age - (OutcomeRoulette.REVEAL_TICKS + OutcomeRoulette.SPIN_TICKS);
        boolean joinedLate = roulette.elapsed() > IN_TICKS;
        FloatingInfo.card(context, CARD, width, height, Plate.TEAL, alpha, slide, card -> {
            int x = FloatingInfo.PAD, y = FloatingInfo.PAD;
            Text title = roulette.title().copy().styled(style -> style.withBold(true));
            HudDraw.centered(card, title, x, y, inner, mine(roulette) ? TITLE_MINE : HudDraw.TEXT, alpha);
            if (caption) HudDraw.centered(card, roulette.caption(), x, y + 10, inner, CAPTION, alpha);
            int rowY = y + head;
            for (int i = 0; i < lines.size(); i++) {
                float pop = joinedLate ? 1 : Easing.clamp01((float) ((age - i * LINE_STAGGER) / IN_TICKS));
                if (pop > 0) {
                    boolean result = stoppedFor >= 0 && i == roulette.result();
                    float rowAlpha = alpha * Easing.easeOutCubic(pop) * (stoppedFor >= 0 && !result ? 0.45f : 1);
                    drawRow(card, font, lines.get(i), x, rowY, inner, i == lit, result ? stoppedFor : -1, rowAlpha);
                }
                rowY += ROW_H + ROW_GAP;
            }
            if (age < OutcomeRoulette.REVEAL_TICKS) {
                float left = 1 - Easing.clamp01((float) (age / OutcomeRoulette.REVEAL_TICKS));
                int barWidth = Math.round(inner * left), barX = x + (inner - barWidth) / 2;
                HudDraw.fill(card, x, rowY, x + inner, rowY + 2, TRACK, alpha);
                if (barWidth > 0) HudDraw.fill(card, barX, rowY, barX + barWidth, rowY + 2, 0xFFFFC900, alpha);
            }
        });
    }

    /**
     * One outcome, as on the panel: a white row (gold while lit, white flashing in gold when the light stops on
     * it), its icon, its sentence, its chance. {@code stoppedFor}: ticks since the light stopped on it (-1: not the result).
     */
    private static void drawRow(DrawContext context, TextRenderer font, Line line, int x, int y, int width, boolean lit,
                                double stoppedFor, float alpha) {
        boolean flash = stoppedFor >= 0 && stoppedFor < FLASH_TICKS && ((int) (stoppedFor / 2.5)) % 2 == 0;
        if (flash) {
            HudDraw.fill(context, x - 1, y - 1, x + width + 1, y + ROW_H + 1, 0xFFFFC900, alpha);
            HudDraw.fill(context, x, y, x + width, y + ROW_H, ROW, alpha);
        } else if (lit) {
            HudDraw.fill(context, x - 1, y - 1, x + width + 1, y + ROW_H + 1, LIT_EDGE, alpha);
            HudDraw.fill(context, x, y, x + width, y + ROW_H, LIT, alpha);
        } else {
            HudDraw.fill(context, x - 1, y - 1, x + width + 1, y + ROW_H + 1, ROW_EDGE, alpha);
            HudDraw.fill(context, x, y, x + width, y + ROW_H, ROW, alpha);
        }
        int textY = y + (ROW_H - 8) / 2 + 1;
        if (!line.icon().isEmpty() && alpha > 0.3f) {
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(x + 2, y + (ROW_H - ICON) / 2f, 0);
            matrices.scale(ICON / 16f, ICON / 16f, 1);
            HudDepth.item(context, () -> context.drawItem(line.icon(), 0, 0));
            matrices.pop();
        }
        int textX = x + ROW_TEXT_X, right = x + width - 3;
        if (line.chance() >= 0) {
            String chance = line.chance() + "%";
            int colour = switch (line.tone()) {
                case BAD -> WARN;
                case GOOD -> GOOD;
                case NEUTRAL -> SOFT;
            };
            int chanceWidth = font.getWidth(chance);
            HudDraw.text(context, chance, right - chanceWidth + 1, textY, chanceWidth, colour, alpha);
            right -= chanceWidth + 4;
        }
        HudDraw.text(context, line.text(), textX, textY, right - textX, HudPaint.TEXT_DARK, alpha);
    }

    private static int chanceRoom(TextRenderer font, Line line) {
        return line.chance() < 0 ? 0 : font.getWidth(line.chance() + "%") + 4;
    }

    /** The phase of the roulette shown. */
    public static Phase phase() {
        return OutcomeRoulette.phase(age());
    }
}
