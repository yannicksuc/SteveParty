package fr.lordfinn.steveparty.client.screens.partycontroller;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData.Phase;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLiveData;
import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.paint.Ramp;
import fr.lordfinn.steveparty.client.gui.party.HudPaint;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.Page;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static fr.lordfinn.steveparty.client.screens.partycontroller.DashboardStyle.*;
import static fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.PROGRAM_COLUMNS;
import static fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.PROGRAM_X;

/**
 * The dashboard's two timelines, rows of chips one per step: the running party's (Status) and what the program will play
 * (Program). Each keeps its scroll (wheel or drag, one chip at a time); the running party's follows the current step
 * until it is scrolled.
 */
public final class DashboardTimeline {
    /** Status: the running party's timeline (its round labels, then its 20 px chips). */
    public static final int STEPS_LABELS_Y = CY + 15, STEPS_CHIP = 20, STEPS_LEFT = CX + 1, STEPS_WIDTH = 10 * 22 - 2;
    /** Program: the timeline under the cards (16 px chips on the cards' columns). */
    public static final int PROGRAM_LABELS_Y = CY + 72, PROGRAM_CHIP = 16, PROGRAM_LEFT = PROGRAM_X;
    private static final int PROGRAM_WIDTH = PROGRAM_COLUMNS * 18 - 2;
    /** The rows of round numbers above the chips of a timeline, the gap between two chips. */
    private static final int TIMELINE_LABELS = 10, TIMELINE_GAP = 2;
    /** The running party's timeline keeps this many past steps on the left of the current one. */
    private static final int TIMELINE_PAST = 2;

    private static final Identifier ICON_DICE = Steveparty.id("party_hud/dice"), ICON_GEAR = Steveparty.id("party_hud/gear"),
            ICON_STEPS = Steveparty.id("party_hud/steps"), ICON_CLOCK = Steveparty.id("party_hud/clock"),
            ICON_CROWN = Steveparty.id("party_hud/crown"), ICON_MARKER = Steveparty.id("party_hud/marker");

    private final Dashboard dashboard;
    private final DashboardPainter paint;
    /** The first step shown of each timeline; the running party's follows the current step until it is scrolled. */
    private int stepsScroll, programScroll;
    private boolean stepsScrolled;
    private int stepsFollowed = -1;
    private double drag;
    /** The step of a timeline under the mouse (drawn this frame), null for none. */
    private @Nullable Text hoveredStep;

    public DashboardTimeline(Dashboard dashboard, DashboardPainter paint) {
        this.dashboard = dashboard;
        this.paint = paint;
    }

    /** The running party's steps (Status): the current one stays near the left until the timeline is scrolled. */
    public void drawSteps(DrawContext context, PartyDashboardData data, int mx, int my) {
        PartyDashboardData.Timeline steps = data.steps();
        int current = steps.offset() + steps.current();
        if (current != stepsFollowed) {
            stepsFollowed = current;
            stepsScrolled = false;
        }
        if (!stepsScrolled) stepsScroll = Math.max(0, steps.current() - TIMELINE_PAST);
        stepsScroll = draw(context, data, steps, STEPS_LEFT, STEPS_LABELS_Y, STEPS_WIDTH, STEPS_CHIP, stepsScroll, data.round(), mx, my);
    }

    /** Under the cards, on their columns: what the program will play, its loops unrolled (Program). */
    public void drawProgram(DrawContext context, PartyDashboardData data, int mx, int my) {
        programScroll = draw(context, data, data.program(), PROGRAM_LEFT, PROGRAM_LABELS_Y, PROGRAM_WIDTH, PROGRAM_CHIP, programScroll, 1, mx, my);
    }

    /** The step under the mouse drawn this frame (forgotten once taken), null for none. */
    public @Nullable Text takeHoveredStep() {
        Text step = hoveredStep;
        hoveredStep = null;
        return step;
    }

    /** @return true if the mouse is over the timeline of the page shown. */
    public boolean isOver(double mouseX, double mouseY) {
        PartyDashboardData data = dashboard.data();
        if (data == null) return false;
        int mx = (int) mouseX - dashboard.left(), my = (int) mouseY - dashboard.top();
        if (dashboard.page() == Page.STATE && data.phase() == Phase.RUNNING)
            return HitArea.contains(mx, my, STEPS_LEFT - 6, STEPS_LABELS_Y, STEPS_WIDTH + 12, TIMELINE_LABELS + STEPS_CHIP);
        return dashboard.page() == Page.PROGRAM && HitArea.contains(mx, my, PROGRAM_LEFT - 6, PROGRAM_LABELS_Y, PROGRAM_WIDTH + 12, TIMELINE_LABELS + PROGRAM_CHIP);
    }

    /** The timeline of the page shown, by {@code steps} chips. */
    public void scroll(int steps) {
        if (dashboard.page() == Page.STATE) {
            stepsScroll += steps;
            stepsScrolled = true;
        } else {
            programScroll += steps;
        }
    }

    /** Dragged like a strip: one chip at a time. */
    public void drag(double deltaX) {
        drag += deltaX;
        int pitch = (dashboard.page() == Page.STATE ? STEPS_CHIP : PROGRAM_CHIP) + TIMELINE_GAP;
        while (Math.abs(drag) >= pitch) {
            scroll(drag > 0 ? -1 : 1);
            drag -= Math.signum(drag) * pitch;
        }
    }

    private static @Nullable Identifier icon(PartyDashboardData.StepKind kind) {
        return switch (kind) {
            case START_ROLLS -> ICON_DICE;
            case PREPARING -> ICON_GEAR;
            case TURN, TURNS -> ICON_STEPS;
            case MINI_GAME -> null;
            case EVENT -> ICON_CLOCK;
            case END -> ICON_CROWN;
            case OTHER -> ICON_MARKER;
        };
    }

    /** « Round 2 · Mini-game », « Round 3 · Steve's turn »: what a step of a timeline is, in one line. */
    private static Text stepText(PartyDashboardData data, PartyDashboardData.TimelineStep step, boolean current) {
        PartyLiveData.Standing player = step.player() >= 0 && step.player() < data.players().size() ? data.players().get(step.player()) : null;
        Text what = switch (step.kind()) {
            case START_ROLLS -> Text.translatable(KEY + "action.start_rolls");
            case PREPARING -> Text.translatable("hud.steveparty.party.preparing");
            case TURN -> player == null ? Text.translatable(KEY + "timeline.turn.unknown") : Text.translatable(KEY + "timeline.turn", player.tokenName());
            case TURNS -> Text.translatable(KEY + "timeline.turns");
            case MINI_GAME -> Text.translatable(KEY + "timeline.mini_game");
            case EVENT -> step.value() > 0 ? Text.translatable(KEY + "timeline.event.channel", step.value()) : Text.translatable(KEY + "timeline.event");
            case END -> Text.translatable(KEY + "timeline.end");
            case OTHER -> Text.translatable(KEY + "timeline.other");
        };
        Text line = step.round() > 0 ? Text.translatable(KEY + "timeline.in_round", step.round(), what) : what;
        return current ? Text.translatable(KEY + "timeline.current", line) : line;
    }

    /**
     * A timeline: a row of chips, one per step (the head of the player whose turn it is on his colour, the mini-game's
     * gamepad, else what the step is), the current one framed in gold, the past ones dimmed; above the first chip of
     * each round its name (« Round 4 », « R4 » when short of room), the round {@code highlight} in gold. Shown from
     * {@code scroll}; « +N » after the last step sent when the party has more.
     *
     * @return the scroll, kept within the timeline
     */
    private int draw(DrawContext context, PartyDashboardData data, PartyDashboardData.Timeline timeline, int left, int top, int width,
                     int chip, int scroll, int highlight, int mx, int my) {
        List<PartyDashboardData.TimelineStep> steps = timeline.steps();
        int pitch = chip + TIMELINE_GAP, shown = (width + TIMELINE_GAP) / pitch;
        int total = steps.size() + (timeline.more() > 0 ? 2 : 0);
        scroll = Math.clamp(scroll, 0, Math.max(0, total - shown));
        int chipY = top + TIMELINE_LABELS;
        // More on the left
        if (scroll > 0 || timeline.offset() > 0) paint.light(context, Text.literal("‹"), left - 5, chipY + (chip - 8) / 2, INK_SOFT);
        // More on the right: steps (or the « +N » after them) past the last chip shown
        if (scroll + shown < steps.size() + (timeline.more() > 0 ? 1 : 0))
            paint.light(context, Text.literal("›"), left + shown * pitch - TIMELINE_GAP + 2, chipY + (chip - 8) / 2, INK_SOFT);
        for (int slot = 0; slot < shown; slot++) {
            int index = scroll + slot, cx = left + slot * pitch;
            if (index >= steps.size()) {
                // « +N »: the steps the party has after those sent
                if (index == steps.size() && timeline.more() > 0)
                    paint.light(context, Text.literal("+" + timeline.more()), cx + 2, chipY + (chip - 8) / 2, INK_SOFT);
                break;
            }
            PartyDashboardData.TimelineStep step = steps.get(index);
            boolean current = index == timeline.current(), past = timeline.current() >= 0 && index < timeline.current();
            boolean hovered = HitArea.contains(mx, my, cx, chipY, chip, chip);
            // The round it begins (and, on the first chip shown, the round it is in)
            boolean begins = step.round() > 0 && (index == 0 || steps.get(index - 1).round() != step.round());
            if (step.round() > 0 && (begins || slot == 0)) {
                int room = pitch - TIMELINE_GAP;
                for (int next = index + 1; next < steps.size() && steps.get(next).round() == step.round() && next - scroll < shown; next++) room += pitch;
                Text label = Text.translatable(KEY + "timeline.round", step.round());
                if (dashboard.font().getWidth(label) - 1 > room - 2) label = Text.translatable(KEY + "timeline.round.short", step.round());
                paint.light(context, label, cx + 1, top, past ? INK_DIM : step.round() == highlight ? INK_GOLD : INK_SOFT);
            }
            PartyLiveData.Standing player = step.player() >= 0 && step.player() < data.players().size() ? data.players().get(step.player()) : null;
            Ramp ramp = step.kind() == PartyDashboardData.StepKind.MINI_GAME ? CHIP_GAME
                    : step.kind() == PartyDashboardData.StepKind.EVENT ? CHIP_EVENT
                    : player != null ? HudPaint.playerRamp(player.color(), step.player()) : NEUTRAL;
            ConsolePaint.box(context, cx, chipY, chip, chip, ramp, 1, 1);
            if (player != null && step.kind() == PartyDashboardData.StepKind.TURN) {
                DashboardPainter.head(context, player, cx + (chip - 8) / 2, chipY + (chip - 8) / 2);
            } else if (step.kind() == PartyDashboardData.StepKind.MINI_GAME) {
                ConsolePaint.gamepad(context, cx + (chip - 10) / 2, chipY + (chip - 8) / 2);
            } else {
                Identifier icon = icon(step.kind());
                if (icon != null) {
                    RenderSystem.enableBlend();
                    context.drawGuiTexture(icon, cx + (chip - 9) / 2, chipY + (chip - 9) / 2, 9, 9);
                    RenderSystem.disableBlend();
                }
            }
            if (past) context.fill(cx, chipY, cx + chip, chipY + chip, 0x8C000000 | (SCREEN & 0xFFFFFF));
            if (hovered && !past) context.fill(cx + 1, chipY + 1, cx + chip - 1, chipY + chip - 1, 0x30FFFFFF);
            if (current) halo(context, cx, chipY, chip);
            if (hovered) hoveredStep = stepText(data, step, current);
        }
        return scroll;
    }

    /** The current step's frame: one pale gold pixel all round the chip (its cut corners filled). */
    private static void halo(DrawContext context, int hx, int hy, int size) {
        int c = 0xFFFFF87E;
        context.fill(hx, hy - 1, hx + size, hy, c);
        context.fill(hx, hy + size, hx + size, hy + size + 1, c);
        context.fill(hx - 1, hy, hx, hy + size, c);
        context.fill(hx + size, hy, hx + size + 1, hy + size, c);
        PartyGui.pixel(context, hx, hy, c);
        PartyGui.pixel(context, hx + size - 1, hy, c);
        PartyGui.pixel(context, hx, hy + size - 1, c);
        PartyGui.pixel(context, hx + size - 1, hy + size - 1, c);
    }
}
