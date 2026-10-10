package fr.lordfinn.steveparty.client.screens.partycontroller;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyBank;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData.Blocker;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData.Phase;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLiveData;
import fr.lordfinn.steveparty.client.gui.ConsoleButton;
import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.Page;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static fr.lordfinn.steveparty.client.screens.partycontroller.DashboardStyle.*;
import static fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.*;

/**
 * The Status tab: before a party, its three steps (board, players, mini-games: only the first one left is open) and
 * « Start the party »; during a party, the round, its timeline, what is happening now, the step and the leader, and
 * « Stop the party »; once over, the standings and « Start a new party ». « Follow » on every phase.
 */
public final class StatusPage {
    /** The three lines under the running party's timeline. */
    private static final int LINES_Y = CY + 50, LINE = 13;
    /** Before a party: the steps. */
    private static final int CHECK_Y = CY + 16, CHECK_ROW = 14;
    /** How long the « Stop the party » button waits for its confirmation (a second click). */
    private static final long STOP_CONFIRM_MS = 4000;

    /** A note: its state, its text, its hint (tooltip) and the tab that sets it. */
    private record Check(int state, Text text, Text hint, @Nullable Page target) {
        static final int OK = 0, WARN = 1, ERROR = 2, INFO = 3;
    }

    private final Dashboard dashboard;
    private final DashboardPainter paint;
    private final DashboardTimeline timeline;
    /** Until when a second click on « Stop the party » stops it (0: not asked). */
    private long stopConfirmUntil;

    public StatusPage(Dashboard dashboard, DashboardPainter paint, DashboardTimeline timeline) {
        this.dashboard = dashboard;
        this.paint = paint;
        this.timeline = timeline;
    }

    public static Text blockerText(Blocker blocker) {
        return Text.translatable(KEY + "blocker." + blocker.name().toLowerCase(Locale.ROOT));
    }

    /** @return true when the stop confirmation has just run out (the buttons are then built again) */
    public boolean tick() {
        boolean confirmOver = stopConfirmUntil != 0 && Util.getMeasuringTimeMs() > stopConfirmUntil;
        if (confirmOver) stopConfirmUntil = 0;
        return confirmOver;
    }

    // ------------------------------------------------------------------ buttons

    public void addButtons(PartyDashboardData data) {
        int x = dashboard.left(), y = dashboard.top();
        // Follow the party: its HUDs on screen (at the bottom right during a party, else at the bottom left)
        Text followText = Text.translatable(KEY + (data.following() ? "follow.on" : "follow.off"));
        int followWidth = dashboard.font().getWidth(followText) - 1 + 20;
        int followX = data.phase() == Phase.RUNNING ? CX + CW - followWidth : CX;
        ConsoleButton follow = dashboard.add(new ConsoleButton(x + followX, y + BUTTON_Y, followWidth, BTN_H, followText,
                data.following() ? ConsoleButton.Kind.GOLD : ConsoleButton.Kind.SCREEN, null, () -> dashboard.click(BUTTON_FOLLOW)));
        follow.setTooltip(Tooltip.of(Text.translatable(KEY + "follow.tooltip")));

        if (data.phase() == Phase.RUNNING) {
            addStopButton(data);
            return;
        }
        stopConfirmUntil = 0;
        // The main action (the board is checked again every two seconds while the dashboard is open, and at the launch)
        Blocker blocker = data.launchBlocker();
        Text launchText = Text.translatable(KEY + (data.phase() == Phase.ENDED ? "launch.again" : "launch"));
        int launchWidth = dashboard.font().getWidth(launchText) - 1 + 20;
        ConsoleButton launch = dashboard.add(new ConsoleButton(x + CX + CW - launchWidth, y + BUTTON_Y, launchWidth, BTN_H, launchText,
                ConsoleButton.Kind.GREEN, null, () -> {
            dashboard.click(BUTTON_LAUNCH);
            dashboard.close();
        }));
        launch.active = blocker == Blocker.NONE;
        launch.setTooltip(Tooltip.of(blocker == Blocker.NONE
                ? Text.translatable(KEY + "launch.tooltip")
                : Text.empty().append(Text.translatable(KEY + "launch.blocked").formatted(Formatting.RED)).append("\n")
                        .append(blockerText(blocker).copy().formatted(Formatting.GRAY))));
    }

    /**
     * Stops the running party (bottom left): a first click asks for a confirmation, a second one within
     * {@link #STOP_CONFIRM_MS} stops it (checked again by the server).
     */
    private void addStopButton(PartyDashboardData data) {
        boolean confirming = stopConfirmUntil != 0;
        Text stopText = Text.translatable(KEY + "stop");
        Text confirmText = Text.translatable(KEY + "stop.confirm");
        // As wide as its widest label: it does not move when asking
        int stopWidth = Math.max(dashboard.font().getWidth(stopText), dashboard.font().getWidth(confirmText)) - 1 + 20;
        ConsoleButton stop = dashboard.add(new ConsoleButton(dashboard.left() + CX, dashboard.top() + BUTTON_Y, stopWidth, BTN_H,
                confirming ? confirmText : stopText, confirming ? ConsoleButton.Kind.GOLD : ConsoleButton.Kind.RED, null, () -> {
            if (stopConfirmUntil != 0) {
                stopConfirmUntil = 0;
                dashboard.click(BUTTON_STOP);
            } else {
                stopConfirmUntil = Util.getMeasuringTimeMs() + STOP_CONFIRM_MS;
            }
            dashboard.rebuild();
        }));
        stop.active = data.canEdit();
        stop.setTooltip(Tooltip.of(!data.canEdit() ? Text.translatable(KEY + "locked")
                : Text.translatable(KEY + (confirming ? "stop.confirm.tooltip" : "stop.tooltip"))));
    }

    // ------------------------------------------------------------------ drawing

    public void draw(DrawContext context, PartyDashboardData data, int mx, int my) {
        switch (data.phase()) {
            case SETUP -> drawSetup(context, data, mx, my);
            case RUNNING -> {
                paint.headerPill(context, data.round() == 0 ? Text.translatable("hud.steveparty.party.round.start")
                        : Text.translatable(KEY + "state.round", data.round(), data.rounds()));
                timeline.drawSteps(context, data, mx, my);
                // What is happening now, then the step and the leader
                paint.fitted(context, data.action(), CX, LINES_Y, CW, WHITE, HitArea.contains(mx, my, CX, LINES_Y - 1, CW, 10));
                if (!data.actionDetail().getString().isEmpty())
                    paint.fitted(context, data.actionDetail(), CX, LINES_Y + LINE, CW, INK_SOFT, HitArea.contains(mx, my, CX, LINES_Y + LINE - 1, CW, 10));
                MutableText step = Text.translatable(KEY + "state.step", data.stepIndex() + 1, data.stepCount());
                PartyLiveData.Standing leader = DashboardPainter.leader(data);
                if (leader != null) step.append(" · ").append(Text.translatable(KEY + "state.leader", leader.tokenName()));
                paint.fitted(context, step, CX, LINES_Y + 2 * LINE, CW, INK_SOFT, HitArea.contains(mx, my, CX, LINES_Y + 2 * LINE - 1, CW, 10));
            }
            case ENDED -> {
                List<PartyLiveData.Standing> players = data.players();
                int[] ranks = PartyLiveData.ranks(players);
                List<Integer> order = DashboardPainter.byRank(ranks);
                // The winner in the title, the standings under it
                paint.header(context, players.isEmpty() ? Text.translatable(KEY + "state.ended")
                        : Text.translatable(KEY + "state.winner", players.get(order.getFirst()).tokenName()), false);
                for (int i = 0; i < Math.min(order.size(), PLAYER_ROWS); i++) {
                    int index = order.get(i);
                    paint.playerRow(context, players.get(index), i, ranks[index], ROWS_Y + i * ROW, CW, false, true, mx, my);
                }
            }
        }
    }

    // ------------------------------------------------------------------ before a party: three steps

    /**
     * Before a party: « Ready to play! » or « N steps left », then the three steps (board, players, mini-games), each
     * ticked once done, only the first one left open with what to do; under them, in small, the storage and the rounds
     * (optional). The details of a line are in its tooltip, a click leads to the tab that sets it.
     */
    private void drawSetup(DrawContext context, PartyDashboardData data, int mx, int my) {
        Setup setup = setup(data);
        int left = setup.left();
        paint.header(context, left == 0 ? Text.translatable(KEY + "state.ready")
                : Text.translatable(KEY + (left == 1 ? "state.todo.one" : "state.todo"), left), false);
        for (Row row : setup.rows()) {
            boolean hovered = row.hint() != null && HitArea.contains(mx, my, row.x(), row.y(), row.w(), row.h());
            if (hovered && row.step() != null) context.fill(CX - 2, row.y(), CX + CW + 2, row.y() + row.h(), 0x22FFFFFF);
            if (row.step() == null) {
                UiText.line(context, dashboard.font(), row.text(), row.x(), row.y() + 1, row.w(), row.color(), false);
                continue;
            }
            ConsolePaint.disc(context, CX, row.y() + 2, 8, row.step() == StepState.DONE ? OK : row.step() == StepState.CURRENT ? WARN : NEUTRAL);
            UiText.line(context, dashboard.font(), row.text(), CX + 12, row.y() + 2, CW - 12, row.color(), true);
            if (row.action() != null) {
                if (row.actionLines() == 1) UiText.line(context, dashboard.font(), row.action(), CX + 12, row.y() + ROW_H + 1, CW - 12, INK_SOFT, false);
                else UiText.wrapped(context, dashboard.font(), row.action(), CX + 12, row.y() + ROW_H + 1, CW - 12, INK_SOFT, false);
            }
        }
    }

    private enum StepState { DONE, CURRENT, TODO }

    /** A line of the Status tab before a party: a step ({@code step} not null) or a note. */
    private record Row(int x, int y, int w, int h, @Nullable StepState step, Text text, int color, @Nullable Text action,
                       int actionLines, @Nullable Text hint, @Nullable Page target) {
    }

    /** The lines of the Status tab before a party, and the steps left. */
    private record Setup(List<Row> rows, int left) {
    }

    /** A step: its title, done or not, what to do then, its tooltip and the tab that sets it. */
    private record Step(Text title, boolean done, Text summary, Text action, Text hint, @Nullable Page target) {
    }

    private List<Step> steps(PartyDashboardData data) {
        PartyDashboardData.Board board = data.board();
        List<Step> steps = new ArrayList<>();
        // The board
        Text boardTitle = Text.translatable(KEY + "step.board");
        if (board.spaces() == 0) {
            steps.add(new Step(boardTitle, false, Text.empty(), Text.translatable(KEY + "step.board.none"),
                    Text.translatable(KEY + "check.board.none.hint"), null));
        } else if (board.starts() == 0) {
            steps.add(new Step(boardTitle, false, Text.empty(), Text.translatable(KEY + "step.board.no_start", board.spaces()),
                    Text.translatable(KEY + "check.board.no_start.hint"), null));
        } else {
            long problems = board.errors() + board.warnings();
            List<Text> hint = new ArrayList<>();
            for (PartyDashboardData.Issue issue : board.issues())
                hint.add(Text.literal("• ").append(Text.translatable("message.steveparty.board.summary." + issue.key(), issue.count())));
            hint.add(Text.translatable(KEY + "check.board.hint"));
            MutableText summary = Text.translatable(KEY + "step.board.done", board.spaces(), board.starts());
            if (problems > 0) summary.append(Text.translatable(KEY + "step.board.problems", problems).formatted(Formatting.GOLD));
            steps.add(new Step(boardTitle, true, summary, Text.empty(), DashboardPainter.join(hint), null));
        }
        // The tokens on the start tiles
        int tokens = board.startTokens().size();
        int free = Math.max(0, board.starts() - tokens);
        steps.add(new Step(Text.translatable(KEY + "step.players"), tokens > 0,
                free > 0 ? Text.translatable(KEY + "step.players.free", tokens, free) : Text.translatable(KEY + "step.players.done", tokens),
                Text.translatable(KEY + "step.players.todo"), Text.translatable(KEY + "check.tokens.hint"), Page.PLAYERS));
        // The mini-games (a party can be played without: they are then skipped)
        Text gamesTitle = Text.translatable(KEY + "step.games");
        long notPlayable = data.pages().stream().filter(p -> p.playable() == 0).count();
        if (!data.hasCatalogue()) {
            steps.add(new Step(gamesTitle, false, Text.empty(), Text.translatable(KEY + "step.games.none"),
                    Text.translatable(KEY + "check.catalogue.none.hint"), Page.PROGRAM));
        } else if (data.pages().isEmpty()) {
            steps.add(new Step(gamesTitle, false, Text.empty(), Text.translatable(KEY + "step.games.empty"),
                    Text.translatable(KEY + "check.catalogue.empty.hint"), Page.PROGRAM));
        } else if (notPlayable > 0) {
            steps.add(new Step(gamesTitle, false, Text.empty(), Text.translatable(KEY + "step.games.no_pipes", notPlayable),
                    Text.translatable(KEY + "check.catalogue.no_pipes.hint"), Page.PROGRAM));
        } else {
            steps.add(new Step(gamesTitle, true, Text.translatable(KEY + "step.games.done", data.pages().size()), Text.empty(),
                    Text.translatable(KEY + "check.catalogue.ok.hint"), Page.PROGRAM));
        }
        return steps;
    }

    private Setup setup(PartyDashboardData data) {
        List<Step> steps = steps(data);
        List<Row> rows = new ArrayList<>();
        int noteY = BUTTON_Y - 2 - UiText.LINE_H;
        int y = CHECK_Y, left = 0;
        boolean opened = false;
        for (Step step : steps) {
            if (step.done()) {
                MutableText text = step.title().copy().append(Text.literal(" · ").formatted(Formatting.DARK_GRAY)).append(step.summary());
                rows.add(new Row(CX, y, CW, ROW_H, StepState.DONE, text, INK_SOFT, null, 0, step.hint(), step.target()));
                y += CHECK_ROW;
                continue;
            }
            left++;
            if (opened) {
                rows.add(new Row(CX, y, CW, ROW_H, StepState.TODO, step.title(), INK_DIM, null, 0, step.hint(), step.target()));
                y += CHECK_ROW;
                continue;
            }
            // The first step left: open, with what to do (on as many lines as it needs and the room allows)
            opened = true;
            int roomLines = Math.max(1, (noteY - 2 - (y + ROW_H + 1) - (steps.size() - steps.indexOf(step) - 1) * CHECK_ROW) / UiText.LINE_H);
            int wrapped = UiText.wrap(dashboard.font(), step.action(), CW - 12).size();
            // Too long for the room: on one line that scrolls
            int lines = wrapped <= roomLines ? wrapped : 1;
            int h = ROW_H + 1 + lines * UiText.LINE_H;
            rows.add(new Row(CX, y, CW, h, StepState.CURRENT, step.title(), WHITE, step.action(), lines, step.hint(), step.target()));
            y += h + CHECK_ROW - ROW_H;
        }
        // In small under them: what the launch can't do here, else the storage (left) and the rounds (right), optional
        Blocker blocker = data.launchBlocker();
        if (blocker == Blocker.NOT_ALLOWED) {
            rows.add(new Row(CX, noteY, CW, UiText.LINE_H, null, blockerText(blocker), INK_RED, null, 0, null, null));
            return new Setup(rows, left);
        }
        Text rounds = Text.translatable(KEY + "check.rounds", data.roundsSetting());
        int roundsW = Math.min(dashboard.font().getWidth(rounds), CW / 3);
        rows.add(new Row(CX + CW - roundsW, noteY, roundsW, UiText.LINE_H, null, rounds, INK_DIM, null, 0,
                Text.translatable(KEY + "check.rounds.hint"), Page.SETTINGS));
        Check bank = bankNote(data);
        rows.add(new Row(CX, noteY, CW - roundsW - 8, UiText.LINE_H, null, bank.text(), bank.state() == Check.WARN ? INK_WARN : INK_DIM,
                null, 0, bank.hint(), bank.target()));
        return new Setup(rows, left);
    }

    /** The storage of the gains (never blocks the launch: a party can be played without gains), and the currencies. */
    private Check bankNote(PartyDashboardData data) {
        PartyBank.Status bank = data.bank();
        Text bankHint = Text.empty().append(Text.translatable(KEY + "check.bank.hint")).append("\n").append(Text.translatable(KEY + "check.currencies.hint"));
        Text coin = dashboard.currency(PartyCurrency.COIN).getName(), star = dashboard.currency(PartyCurrency.STAR).getName();
        Text skipped = StoragePage.skipped(bank);
        return switch (bank.state()) {
            case NONE -> new Check(Check.WARN, Text.translatable(KEY + "check.bank.none"), bankHint, Page.STORAGE);
            case INFINITE -> new Check(Check.INFO, Text.translatable(KEY + "check.bank.infinite"), bankHint, Page.STORAGE);
            case MISSING -> new Check(Check.WARN, Text.translatable(KEY + "check.bank.missing").append(skipped), bankHint, Page.STORAGE);
            case SHORT -> new Check(Check.WARN, Text.translatable(KEY + "check.bank.short", bank.coins(), coin, bank.stars(), star).append(skipped),
                    bankHint, Page.STORAGE);
            case OK -> new Check(skipped.getString().isEmpty() ? Check.INFO : Check.WARN,
                    Text.translatable(KEY + "check.bank.ok", bank.coins(), coin, bank.stars(), star).append(skipped), bankHint, Page.STORAGE);
        };
    }

    private @Nullable Row rowAt(double mouseX, double mouseY, PartyDashboardData data) {
        double mx = mouseX - dashboard.left(), my = mouseY - dashboard.top();
        for (Row row : setup(data).rows())
            if (row.hint() != null && HitArea.contains(mx, my, row.x(), row.y(), row.w(), row.h())) return row;
        return null;
    }

    /** The tooltip of the line under the mouse (its details, the tab that sets it), null for none. */
    public @Nullable List<Text> tooltip(PartyDashboardData data, int mouseX, int mouseY) {
        if (data.phase() != Phase.SETUP) return null;
        Row row = rowAt(mouseX, mouseY, data);
        if (row == null || row.hint() == null) return null;
        List<Text> lines = new ArrayList<>();
        lines.add(row.hint().copy().formatted(Formatting.GRAY));
        if (row.target() != null)
            lines.add(Text.translatable(KEY + "check.go", DashboardTabs.name(row.target())).formatted(Formatting.YELLOW));
        return lines;
    }

    /** The tab that sets the line clicked, null for none. */
    public @Nullable Page clicked(PartyDashboardData data, double mouseX, double mouseY) {
        if (data.phase() != Phase.SETUP) return null;
        Row row = rowAt(mouseX, mouseY, data);
        return row == null ? null : row.target();
    }
}
