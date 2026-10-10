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
import fr.lordfinn.steveparty.client.gui.paint.Ramp;
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
 * The Status tab: before a party, the checklist of what it needs (each line leading to the tab where it is fixed) and
 * « Start the party »; during a party, the round, its timeline, what is happening now, the step and the leader, and
 * « Stop the party »; once over, the standings and « Start a new party ». « Follow » on every phase.
 */
public final class StatusPage {
    /** The three lines under the running party's timeline. */
    private static final int LINES_Y = CY + 50, LINE = 13;
    /** Before a party: the checklist. */
    private static final int CHECK_Y = CY + 16, CHECK_ROW = 14;
    /** How long the « Stop the party » button waits for its confirmation (a second click). */
    private static final long STOP_CONFIRM_MS = 4000;

    /** A line of the checklist: its state, its text, its hint (tooltip) and the tab that fixes it. */
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
            case SETUP -> {
                paint.header(context, Text.translatable(KEY + "state.setup"), false);
                List<Check> checks = checklist(data);
                for (int i = 0; i < checks.size(); i++) {
                    Check check = checks.get(i);
                    int ly = CHECK_Y + i * CHECK_ROW;
                    boolean hovered = check.target() != null && HitArea.contains(mx, my, CX, ly, CW, ROW_H);
                    if (hovered) context.fill(CX - 2, ly, CX + CW + 2, ly + ROW_H, 0x22FFFFFF);
                    Ramp disc = switch (check.state()) {
                        case Check.OK -> OK;
                        case Check.ERROR -> ERROR;
                        case Check.WARN -> WARN;
                        default -> NEUTRAL;
                    };
                    ConsolePaint.disc(context, CX, ly + 2, 8, disc);
                    int color = switch (check.state()) {
                        case Check.OK -> INK;
                        case Check.ERROR -> INK_RED;
                        case Check.WARN -> INK_WARN;
                        default -> INK_SOFT;
                    };
                    paint.fitted(context, check.text(), CX + 12, ly + 2, CW - 12, color, hovered);
                }
            }
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

    // ------------------------------------------------------------------ the checklist

    private List<Check> checklist(PartyDashboardData data) {
        List<Check> checks = new ArrayList<>();
        PartyDashboardData.Board board = data.board();
        // The board
        if (board.spaces() == 0) {
            checks.add(new Check(Check.ERROR, Text.translatable(KEY + "check.board.none"), Text.translatable(KEY + "check.board.none.hint"), null));
        } else if (board.starts() == 0) {
            checks.add(new Check(Check.ERROR, Text.translatable(KEY + "check.board.no_start", board.spaces()), Text.translatable(KEY + "check.board.no_start.hint"), null));
        } else {
            long problems = board.errors() + board.warnings();
            List<Text> hint = new ArrayList<>();
            for (PartyDashboardData.Issue issue : board.issues())
                hint.add(Text.literal("• ").append(Text.translatable("message.steveparty.board.summary." + issue.key(), issue.count())));
            hint.add(Text.translatable(KEY + "check.board.hint"));
            checks.add(new Check(problems > 0 ? Check.WARN : Check.OK,
                    problems > 0 ? Text.translatable(KEY + "check.board.warnings", board.spaces(), board.starts(), problems)
                            : Text.translatable(KEY + "check.board.ok", board.spaces(), board.starts()),
                    DashboardPainter.join(hint), null));
        }
        // The tokens on the start tiles
        int tokens = board.startTokens().size();
        int free = Math.max(0, board.starts() - tokens);
        checks.add(tokens == 0
                ? new Check(Check.ERROR, Text.translatable(KEY + "check.tokens.none"), Text.translatable(KEY + "check.tokens.hint"), Page.PLAYERS)
                : new Check(Check.OK, free > 0 ? Text.translatable(KEY + "check.tokens.free", tokens, free) : Text.translatable(KEY + "check.tokens.ok", tokens),
                Text.translatable(KEY + "check.tokens.hint"), Page.PLAYERS));
        // The mini-games
        long notPlayable = data.pages().stream().filter(p -> p.playable() == 0).count();
        if (!data.hasCatalogue()) {
            checks.add(new Check(Check.WARN, Text.translatable(KEY + "check.catalogue.none"), Text.translatable(KEY + "check.catalogue.none.hint"), Page.PROGRAM));
        } else if (data.pages().isEmpty()) {
            checks.add(new Check(Check.WARN, Text.translatable(KEY + "check.catalogue.empty"), Text.translatable(KEY + "check.catalogue.empty.hint"), Page.PROGRAM));
        } else if (notPlayable > 0) {
            checks.add(new Check(Check.WARN, Text.translatable(KEY + "check.catalogue.no_pipes", data.pages().size(), notPlayable), Text.translatable(KEY + "check.catalogue.no_pipes.hint"), Page.PROGRAM));
        } else {
            checks.add(new Check(Check.OK, Text.translatable(KEY + "check.catalogue.ok", data.pages().size()), Text.translatable(KEY + "check.catalogue.ok.hint"), Page.PROGRAM));
        }
        // The bank of the gains (never blocks the launch: a party can be played without gains), and the currencies
        PartyBank.Status bank = data.bank();
        Text bankHint = Text.empty().append(Text.translatable(KEY + "check.bank.hint")).append("\n").append(Text.translatable(KEY + "check.currencies.hint"));
        Text coin = dashboard.currency(PartyCurrency.COIN).getName(), star = dashboard.currency(PartyCurrency.STAR).getName();
        Text skipped = StoragePage.skipped(bank);
        checks.add(switch (bank.state()) {
            case NONE -> new Check(Check.WARN, Text.translatable(KEY + "check.bank.none"), bankHint, Page.STORAGE);
            case INFINITE -> new Check(Check.INFO, Text.translatable(KEY + "check.bank.infinite"), bankHint, Page.STORAGE);
            case MISSING -> new Check(Check.WARN, Text.translatable(KEY + "check.bank.missing").append(skipped), bankHint, Page.STORAGE);
            case SHORT -> new Check(Check.WARN, Text.translatable(KEY + "check.bank.short", bank.coins(), coin, bank.stars(), star).append(skipped),
                    bankHint, Page.STORAGE);
            case OK -> new Check(skipped.getString().isEmpty() ? Check.INFO : Check.WARN,
                    Text.translatable(KEY + "check.bank.ok", bank.coins(), coin, bank.stars(), star).append(skipped), bankHint, Page.STORAGE);
        });
        checks.add(new Check(Check.INFO, Text.translatable(KEY + "check.rounds", data.roundsSetting()), Text.translatable(KEY + "check.rounds.hint"), Page.SETTINGS));
        return checks;
    }

    private int checkAt(double mouseX, double mouseY, PartyDashboardData data) {
        double mx = mouseX - dashboard.left(), my = mouseY - dashboard.top();
        if (mx < CX || mx >= CX + CW) return -1;
        int index = (int) Math.floor((my - CHECK_Y) / CHECK_ROW);
        int count = checklist(data).size();
        return my >= CHECK_Y && index >= 0 && index < count && my < CHECK_Y + index * CHECK_ROW + ROW_H ? index : -1;
    }

    /** The tooltip of the checklist's line under the mouse (its hint, the tab that fixes it), null for none. */
    public @Nullable List<Text> tooltip(PartyDashboardData data, int mouseX, int mouseY) {
        if (data.phase() != Phase.SETUP) return null;
        int index = checkAt(mouseX, mouseY, data);
        if (index < 0) return null;
        Check check = checklist(data).get(index);
        List<Text> lines = new ArrayList<>();
        lines.add(check.hint().copy().formatted(Formatting.GRAY));
        if (check.target() != null)
            lines.add(Text.translatable(KEY + "check.go", DashboardTabs.name(check.target())).formatted(Formatting.YELLOW));
        return lines;
    }

    /** The tab that fixes the checklist's line clicked, null for none. */
    public @Nullable Page clicked(PartyDashboardData data, double mouseX, double mouseY) {
        if (data.phase() != Phase.SETUP) return null;
        int index = checkAt(mouseX, mouseY, data);
        return index < 0 ? null : checklist(data).get(index).target();
    }
}
