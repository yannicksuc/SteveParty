package fr.lordfinn.steveparty.client.screens.partycontroller;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData.Phase;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLiveData;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

import static fr.lordfinn.steveparty.client.screens.partycontroller.DashboardStyle.*;

/** The Players tab: the tokens in the turn order with their rank, player, stars and coins (it scrolls). */
public final class PlayersPage {
    private final DashboardPainter paint;
    private int scroll;

    public PlayersPage(DashboardPainter paint) {
        this.paint = paint;
    }

    /** Back to the first row (the tab is opened again). */
    public void resetScroll() {
        scroll = 0;
    }

    /** The wheel: a row at a time. */
    public void scroll(double verticalAmount) {
        scroll -= (int) Math.signum(verticalAmount);
    }

    public void draw(DrawContext context, PartyDashboardData data, int mx, int my) {
        List<PartyLiveData.Standing> players = data.players();
        paint.header(context, Text.translatable(data.phase() == Phase.SETUP ? KEY + "players.setup" : KEY + "players.party", players.size()), false);
        if (players.isEmpty()) {
            paint.line(context, Text.translatable(KEY + "check.tokens.none"), CX, ROWS_Y + 4, CW, INK_RED);
            return;
        }
        boolean ranked = data.phase() != Phase.SETUP && players.stream().anyMatch(p -> p.stars() != 0 || p.coins() != 0);
        int[] ranks = PartyLiveData.ranks(players);
        int maxScroll = Math.max(0, players.size() - PLAYER_ROWS);
        scroll = Math.clamp(scroll, 0, maxScroll);
        int rowWidth = CW - (maxScroll > 0 ? 6 : 0);
        for (int i = scroll; i < Math.min(players.size(), scroll + PLAYER_ROWS); i++) {
            paint.playerRow(context, players.get(i), i, ranks[i], ROWS_Y + (i - scroll) * ROW, rowWidth, i == data.currentPlayer(), ranked, mx, my);
        }
        if (maxScroll > 0)
            DashboardPainter.scrollbar(context, CX + CW - 3, ROWS_Y, PLAYER_ROWS * ROW - 2, scroll, maxScroll, PLAYER_ROWS, players.size());
    }
}
