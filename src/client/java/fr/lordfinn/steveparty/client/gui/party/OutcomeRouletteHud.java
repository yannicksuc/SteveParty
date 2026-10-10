package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.client.gui.HudDepth;
import fr.lordfinn.steveparty.client.gui.ToolHud.Plate;
import fr.lordfinn.steveparty.client.gui.paint.Ramp;
import fr.lordfinn.steveparty.hud.HudShapes.Form;
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

import static fr.lordfinn.steveparty.hud.HudShapes.PAD;

/**
 * The outcome roulette shown ({@link OutcomeRoulette}, sent by {@link OutcomeRoulettePayload}): its clock and sounds,
 * for every player of the party, and its HUD strip. The roulette itself is a panel floating in the world over what
 * triggered it ({@code OutcomeRoulettePanel}), so that the show under it stays in sight; only when that panel is out of
 * a player's sight (behind them, off the screen, too far) does a compact strip on the left of the screen stand in for
 * it: who triggered it, then the line the light is on, then the result.
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
    /** How the strip stands in for the panel: its width at most, its line's height. */
    private static final int STRIP_WIDTH = 210, STRIP_PAD = 6, LINE_H = 13, ICON = 10;
    private static final float IN_TICKS = 6;
    private static final Ramp FLASH = new Ramp(HudPaint.GOLD.outline(), 0xFFFFFFFF, 0xFFFFFFFF, HudPaint.GOLD.hi());
    /** The panel was drawn on screen this recently (nanoseconds): no strip. */
    private static final long PANEL_SEEN_NANOS = 150_000_000L;

    private static @Nullable OutcomeRoulettePayload shown;
    /** When it started, on the HUD's clock ({@link PartyHud#now}), and its step times. */
    private static double startedAt;
    private static double[] times = new double[0];
    /** The steps already ticked (-1: the second note of the reveal heard), and whether the stop was heard. */
    private static int heardSteps;
    private static boolean heardStop;
    private static long panelSeenAt;
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

    /** The panel was drawn on screen this frame: the strip stays away. */
    public static void panelSeen() {
        panelSeenAt = System.nanoTime();
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
        boolean panel = roulette.anchor().isPresent() && System.nanoTime() - panelSeenAt < PANEL_SEEN_NANOS;
        if (client.options.hudHidden || panel) {
            stripAt = -1;
            return;
        }
        double now = PartyHud.now();
        if (stripAt < 0) stripAt = now;
        float in = Easing.easeOutCubic(Easing.clamp01((float) ((now - stripAt) / IN_TICKS)));
        float alpha = Math.min(alpha(), in);
        if (alpha < 0.05f) return;
        drawStrip(context, roulette, age, alpha, (1 - in) * -12);
    }

    /**
     * On the left of the screen, half way down: who triggered it, then the line the light is on (gold), « ? » while
     * the lines are read, the result once it stopped (it flashes).
     */
    private static void drawStrip(DrawContext context, OutcomeRoulettePayload roulette, double age, float alpha, float slide) {
        TextRenderer font = HudDraw.font();
        int width = Math.min(STRIP_WIDTH, context.getScaledWindowWidth() / 2 - 8);
        int inner = width - 2 * STRIP_PAD;
        int height = STRIP_PAD + 11 + LINE_H + STRIP_PAD;
        int x = 6, y = context.getScaledWindowHeight() / 2 - height / 2;
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(slide, 0, 0);
        HudDraw.plate(context, Plate.TEAL, x, y, width, height, alpha);
        HudDraw.text(context, roulette.title(), x + STRIP_PAD, y + STRIP_PAD, inner, mine(roulette) ? 0xFF9A6200 : HudDraw.TEXT, alpha);
        int lit = OutcomeRoulette.litLine(roulette.lines().size(), times, age);
        double stoppedFor = age - (OutcomeRoulette.REVEAL_TICKS + OutcomeRoulette.SPIN_TICKS);
        int lineY = y + STRIP_PAD + 11;
        if (lit < 0) {
            drawLine(context, font, null, roulette.caption(), x + STRIP_PAD, lineY, inner, false, -1, alpha);
        } else {
            Line line = roulette.lines().get(lit);
            drawLine(context, font, line, line.text(), x + STRIP_PAD, lineY, inner, true, stoppedFor, alpha);
        }
        matrices.pop();
    }

    private static void drawLine(DrawContext context, TextRenderer font, @Nullable Line line, Text text, int x, int y, int width,
                                 boolean lit, double stoppedFor, float alpha) {
        boolean flash = stoppedFor >= 0 && stoppedFor < FLASH_TICKS && ((int) (stoppedFor / 2.5)) % 2 == 0;
        Ramp ramp = flash ? FLASH : lit ? HudPaint.PLATE_GOLD : HudPaint.PLATE;
        int height = LINE_H;
        if (lit && stoppedFor >= 0) HudPaint.draw(context, HudPaint.halo(Form.CUT1, width, height), x - PAD, y - PAD, alpha);
        HudPaint.draw(context, HudPaint.shape(Form.CUT1, width, height, ramp, HudPaint.SHADOW | HudPaint.OUTLINE), x - PAD, y - PAD, alpha);
        int textX = x + 4, right = x + width - 4;
        if (line != null && !line.icon().isEmpty() && alpha > 0.3f) {
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(x + 3, y + (height - ICON) / 2f, 0);
            matrices.scale(ICON / 16f, ICON / 16f, 1);
            HudDepth.item(context, () -> context.drawItem(line.icon(), 0, 0));
            matrices.pop();
            textX = x + 3 + ICON + 4;
        }
        if (line == null) HudDraw.centered(context, text, x + 4, y + 3, width - 8, HudPaint.TEXT_DARK, alpha);
        else HudDraw.text(context, text, textX, y + 3, right - textX, HudPaint.TEXT_DARK, alpha);
    }

    /** The phase of the roulette shown. */
    public static Phase phase() {
        return OutcomeRoulette.phase(age());
    }
}
