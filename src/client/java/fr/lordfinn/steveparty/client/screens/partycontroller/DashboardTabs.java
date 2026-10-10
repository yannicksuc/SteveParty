package fr.lordfinn.steveparty.client.screens.partycontroller;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData.Blocker;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData.Phase;
import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.PartyButton;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.Page;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static fr.lordfinn.steveparty.client.screens.partycontroller.DashboardStyle.*;
import static fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.*;

/**
 * The dashboard's tabs above its panel: their layout (each its name and 5 px each side, 2 px apart; all of the same
 * width, their names scrolling in them, when a wordy language makes them too long), their buttons, the tabs at rest behind the panel,
 * their names (red, or orange, on a tab that blocks the party, or warns).
 */
public final class DashboardTabs {
    private static final int TAB_PAD = 5, TAB_GAP = 2, TAB_LABEL_Y = 7;

    /** What needs the player's attention on a tab (its label turns red, or orange): an error or a warning, and why. */
    public record Badge(boolean error, Text reason) {}

    private final Dashboard dashboard;
    private final DashboardPainter paint;
    /** Left edge (from the panel's) and width of each tab. */
    private final int[] tabLeft = new int[Page.values().length], tabWide = new int[Page.values().length];

    public DashboardTabs(Dashboard dashboard, DashboardPainter paint) {
        this.dashboard = dashboard;
        this.paint = paint;
    }

    public static String key(Page page) {
        return page.name().toLowerCase(Locale.ROOT);
    }

    public static Text name(Page tab) {
        return Text.translatable(KEY + "tab." + key(tab));
    }

    public int left(Page tab) {
        return tabLeft[tab.ordinal()];
    }

    public int width(Page tab) {
        return tabWide[tab.ordinal()];
    }

    private void layout() {
        Page[] tabs = Page.values();
        int room = WIDTH - 2 * BEZEL - TAB_GAP * (tabs.length - 1), total = 0;
        for (Page tab : tabs) total += dashboard.font().getWidth(name(tab)) - 1 + 2 * TAB_PAD;
        int left = BEZEL;
        for (Page tab : tabs) {
            int index = tab.ordinal();
            // Names too long for the row (a wordy language): tabs of the same width, their names scrolling
            tabWide[index] = total > room ? room / tabs.length : dashboard.font().getWidth(name(tab)) - 1 + 2 * TAB_PAD;
            tabLeft[index] = left;
            left += tabWide[index] + TAB_GAP;
        }
    }

    /** Lays the tabs out and adds their buttons (they only handle clicks, focus and tooltip: drawn with the panel). */
    public void addButtons() {
        layout();
        int x = dashboard.left(), y = dashboard.top();
        for (Page tab : Page.values()) {
            int index = tab.ordinal();
            PartyButton button = new PartyButton(x + tabLeft[index], y - TABS_HEIGHT, tabWide[index], TABS_HEIGHT, name(tab), b -> dashboard.showPage(tab)) {
                @Override
                protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                    // Drawn with the panel (drawBackground): the button only handles clicks, focus and tooltip
                }
            };
            button.setTooltip(Tooltip.of(tooltip(tab)));
            dashboard.add(button);
        }
    }

    private Text tooltip(Page tab) {
        Text description = Text.translatable(KEY + "tab." + key(tab) + ".tooltip");
        Badge badge = badge(tab);
        if (badge == null) return description;
        return Text.empty().append(badge.reason.copy().formatted(badge.error ? Formatting.RED : Formatting.YELLOW)).append("\n")
                .append(description.copy().formatted(Formatting.GRAY));
    }

    /** What needs the player's attention on a tab, null for nothing. */
    private @Nullable Badge badge(Page tab) {
        PartyDashboardData data = dashboard.data();
        if (data == null) return null;
        switch (tab) {
            case STATE -> {
                if (data.phase() == Phase.RUNNING) return null;
                Blocker blocker = data.launchBlocker();
                if (blocker == Blocker.NO_BOARD || blocker == Blocker.NO_START || blocker == Blocker.NO_TOKEN)
                    return new Badge(true, StatusPage.blockerText(blocker));
                if (data.board().errors() + data.board().warnings() > 0)
                    return new Badge(false, Text.translatable(KEY + "badge.board", data.board().errors() + data.board().warnings()));
                return null;
            }
            case PLAYERS -> {
                if (data.phase() != Phase.SETUP && data.players().stream().anyMatch(player -> !player.online()))
                    return new Badge(false, Text.translatable(KEY + "badge.offline"));
                if (data.phase() == Phase.SETUP && data.players().isEmpty())
                    return new Badge(true, StatusPage.blockerText(Blocker.NO_TOKEN));
                return null;
            }
            case PROGRAM -> {
                return ProgramPage.catalogueBadge(data);
            }
            default -> {
                return null;
            }
        }
    }

    /** The tabs at rest, behind the panel (their foot under its top edge). */
    public void drawIdle(DrawContext context, int mouseX, int mouseY) {
        int x = dashboard.left(), y = dashboard.top();
        for (Page tab : Page.values()) {
            if (tab == dashboard.page()) continue;
            int index = tab.ordinal(), tx = x + tabLeft[index], tw = tabWide[index], ty = y - TABS_HEIGHT;
            boolean hovered = HitArea.contains(mouseX, mouseY, tx, ty, tw, TABS_HEIGHT);
            ConsolePaint.box(context, tx, ty, tw, TABS_HEIGHT + 3, hovered ? TAB_IDLE_HOVER : TAB_IDLE, 2, 2);
        }
    }

    /** The tabs' names: dark on the tabs at rest, light in the selected one; red (orange) on a tab that blocks (warns). */
    public void drawLabels(DrawContext context) {
        int x = dashboard.left(), y = dashboard.top();
        for (Page tab : Page.values()) {
            int index = tab.ordinal(), tw = tabWide[index];
            int tx = x + tabLeft[index], ty = y - TABS_HEIGHT + TAB_LABEL_Y;
            Badge badge = badge(tab);
            // Centred in the tab; too long (tabs of the same width), it scrolls between its paddings
            Text label = name(tab);
            int w = dashboard.font().getWidth(label), room = tw - 2 * TAB_PAD + 2;
            int lx = w <= room ? tx + (tw - w + 1) / 2 : tx + TAB_PAD - 1, lw = Math.min(w, room);
            if (tab == dashboard.page()) {
                UiText.line(context, dashboard.font(), label, lx, ty, lw, badge == null ? WHITE : badge.error ? INK_RED : INK_WARN, true);
            } else {
                ConsolePaint.darkText(context, dashboard.font(), label.asOrderedText(), lx, ty, lw, badge == null ? TAB_INK : badge.error ? TAB_RED : TAB_WARN, WHITE);
            }
        }
    }
}
