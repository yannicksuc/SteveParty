package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.hud.HudShapes;
import fr.lordfinn.steveparty.hud.HudShapes.Form;
import fr.lordfinn.steveparty.hud.StandingsLayout;
import fr.lordfinn.steveparty.utils.Easing;
import fr.lordfinn.steveparty.client.gui.paint.Ramp;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static fr.lordfinn.steveparty.hud.HudShapes.GAP;
import static fr.lordfinn.steveparty.hud.HudShapes.PAD;

/**
 * The standings, « tableau des scores » (the approved mock-up S5, see {@link StandingsLayout}): the party's star and
 * coin items once, full size, over their columns, then one thin row per player, best first (rank medallion: gold for
 * the first, silver, bronze, then neutral; head; pawn's name; stars and coins right-aligned; bonuses), « toi » at the
 * end of my row, and past eight players the first three, me and my neighbours, the last one.
 * <p>
 * Animated: the rows glide to their new place when the ranks change, a number pops when it changes. Items are never
 * faded (they can't be): they are left out while the HUD fades.
 */
final class StandingsHud {
    private static final float POP_TICKS = 8;
    private static final float GLIDE = 0.3f;
    private static final Ramp[] RANKS = {HudPaint.GOLD, HudPaint.SILVER, HudPaint.BRONZE};

    private PartyHudModel model;
    private StandingsLayout.Layout layout;
    private final Map<UUID, Row> rows = new HashMap<>();
    private final List<Row> shown = new ArrayList<>();
    private final List<StandingsLayout.Row> gaps = new ArrayList<>();
    private double lastFrame;

    /** A player's row: where it goes, where it is, its numbers' pops. */
    private static final class Row {
        StandingsLayout.Entry entry;
        PartyHudModel.Player player;
        String name;
        int y;
        float shownY = Float.NaN;
        int lastStars = Integer.MIN_VALUE, lastCoins = Integer.MIN_VALUE;
        double starsAt = -1000, coinsAt = -1000;
    }

    int width() {
        return layout == null ? 0 : layout.width();
    }

    int height() {
        return layout == null ? 0 : layout.height();
    }

    void update(PartyHudModel model, int room, double now) {
        if (model == this.model) return;
        this.model = model;
        // The players, best first (the turn order between equals)
        List<StandingsLayout.Entry> ranked = new ArrayList<>();
        for (int i = 0; i < model.players.size(); i++) {
            PartyHudModel.Player p = model.players.get(i);
            // No rank before someone holds stars or coins (no medallion: everybody would be « 1st »)
            ranked.add(new StandingsLayout.Entry(i, model.hasStandings ? p.rank : 0, p.name, p.stars, p.coins,
                    Math.min(StandingsLayout.BONUS_SLOTS, p.bonuses.size()), p.mine));
        }
        ranked.sort((a, b) -> Integer.compare(a.rank(), b.rank()));
        layout = StandingsLayout.layout(ranked, ClientHudTexts.INSTANCE);
        Map<UUID, Row> kept = new HashMap<>();
        shown.clear();
        gaps.clear();
        for (StandingsLayout.Row r : layout.rows()) {
            if (r.gap()) {
                gaps.add(r);
                continue;
            }
            PartyHudModel.Player player = model.players.get(r.entry().index());
            Row row = rows.getOrDefault(player.token, new Row());
            row.entry = r.entry();
            row.player = player;
            row.name = ClientHudTexts.INSTANCE.fit(player.name, layout.nameWidth());
            row.y = r.y();
            if (Float.isNaN(row.shownY)) row.shownY = row.y;
            if (row.lastStars != Integer.MIN_VALUE && row.lastStars != player.stars) row.starsAt = now;
            if (row.lastCoins != Integer.MIN_VALUE && row.lastCoins != player.coins) row.coinsAt = now;
            row.lastStars = player.stars;
            row.lastCoins = player.coins;
            kept.put(player.token, row);
            shown.add(row);
        }
        rows.clear();
        rows.putAll(kept);
    }

    void draw(DrawContext context, float alpha, double now) {
        if (model == null || layout == null || alpha <= 0.02f) return;
        float delta = (float) MathHelper.clamp(now - lastFrame, 0, 5);
        lastFrame = now;
        int px = layout.plateX();
        // The header: the items over their columns, « bonus » over its slots
        if (alpha > 0.6f) {
            int top = StandingsLayout.TOP;
            int twoDigits = ClientHudTexts.INSTANCE.width("88");
            icon(context, model.starItem, px + layout.iconX(layout.starColumn(), twoDigits), top);
            icon(context, model.coinItem, px + layout.iconX(layout.coinColumn(), twoDigits), top);
        }
        if (layout.bonuses()) {
            int bx = px + layout.bonusColumn() - 2;
            HudPaint.draw(context, HudPaint.shape(Form.PILL, 34 + 4, 12, HudPaint.NEUTRAL, HudPaint.OUTLINE), bx - PAD, StandingsLayout.TOP + 2 - PAD, alpha);
            TurnBarHud.darkText(context, net.minecraft.text.Text.translatable("hud.steveparty.party.bonus").getString(),
                    px + layout.bonusColumn() + 3, StandingsLayout.TOP + 4, HudPaint.NEUTRAL.outline(), 0xFFFFFFFF, alpha);
        }
        for (StandingsLayout.Row gap : gaps) {
            String n = Integer.toString(gap.hidden());
            int w = ClientHudTexts.INSTANCE.width("… " + n) + 11;
            int gx = px + 12, gy = gap.y();
            HudPaint.draw(context, HudPaint.shape(Form.PILL, w, StandingsLayout.GAP_H, HudPaint.NEUTRAL, HudPaint.OUTLINE), gx - PAD, gy - PAD, alpha);
            HudPaint.draw(context, HudPaint.dots(0xFFFFFFFF), gx + 5, gy + 2, alpha);
            HudPaint.draw(context, HudPaint.dots(HudPaint.NEUTRAL.outline()), gx + 4, gy + 1, alpha);
            HudPaint.small(context, n, gx + 4 + 8, gy + 2, HudPaint.NEUTRAL.outline(), alpha);
        }
        for (Row row : shown) {
            row.shownY = HudDraw.approach(row.shownY, row.y, GLIDE, delta);
            drawRow(context, row, Math.round(row.shownY), alpha, now);
        }
    }

    private void drawRow(DrawContext context, Row row, int y, float alpha, double now) {
        StandingsLayout.Entry entry = row.entry;
        PartyHudModel.Player player = row.player;
        int px = layout.plateX(), h = StandingsLayout.ROW_H, rx = layout.rankX();
        // The rank medallion, once someone is ranked
        int rank = entry.rank();
        if (layout.ranked() && rank > 0) {
            Ramp rampRank = rank <= 3 ? RANKS[rank - 1] : HudPaint.NEUTRAL;
            int d = StandingsLayout.BADGE;
            HudPaint.draw(context, HudPaint.shape(Form.PILL, d, d, rampRank, HudPaint.OUTLINE | HudPaint.BAND), rx - PAD, y + 1 - PAD, alpha);
            String r = Integer.toString(rank);
            int rw = ClientHudTexts.INSTANCE.width(r);
            TurnBarHud.darkText(context, r, rx + (d - rw - 1) / 2, y + 1 + (d - 8) / 2, rampRank.outline(),
                    rampRank == HudPaint.NEUTRAL ? 0xFFFFFFFF : rampRank.hi(), alpha);
        }
        // The plate (mine: a gold outline round it), the head, the pawn's name
        HudPaint.draw(context, HudPaint.shape(Form.PILL, layout.plateWidth(), h, player.ramp.pastel(), HudPaint.SHADOW | HudPaint.OUTLINE | HudPaint.BAND),
                px - PAD, y - PAD, alpha);
        if (entry.mine()) HudPaint.draw(context, HudPaint.ring(Form.PILL, layout.plateWidth(), h, HudPaint.GOLD.body()), px - PAD, y - PAD, alpha);
        int head = StandingsLayout.HEAD;
        HudPaint.draw(context, HudPaint.shape(Form.CUT1, head, head, HudPaint.white(player.ramp.outline()), HudPaint.OUTLINE),
                px + 3 - PAD, y + (h - head) / 2 - PAD, alpha);
        TurnBarHud.head(context, player, px + 3 + (head - 8) / 2, y + (h - head) / 2 + (head - 8) / 2, alpha);
        int textY = y + (h - 8) / 2;
        TurnBarHud.darkText(context, row.name, px + layout.nameX(), textY, HudPaint.TEXT_DARK, 0xFFFFFFFF, alpha);
        // Stars and coins, right-aligned three-digit columns
        digits(context, player.stars, px + layout.starColumn(), textY, row.starsAt, alpha, now);
        digits(context, player.coins, px + layout.coinColumn(), textY, row.coinsAt, alpha, now);
        // Bonuses
        if (layout.bonuses()) {
            for (int k = 0; k < StandingsLayout.BONUS_SLOTS; k++) {
                int sx = px + layout.bonusColumn() + k * 12, sy = y + (h - StandingsLayout.SLOT) / 2;
                boolean has = k < player.bonuses.size();
                HudPaint.draw(context, HudPaint.shape(Form.PILL, StandingsLayout.SLOT, StandingsLayout.SLOT, has ? HudPaint.NEUTRAL : HudPaint.EMPTY_SLOT,
                        HudPaint.OUTLINE), sx - PAD, sy - PAD, alpha);
                if (has && alpha > 0.6f) smallItem(context, player.bonuses.get(k), sx + 1, sy + 1);
            }
        }
    }

    /** A number right-aligned in a « 888 » column; it pops when it changes. */
    private void digits(DrawContext context, int value, int x, int y, double changedAt, float alpha, double now) {
        String s = Integer.toString(value);
        int w = ClientHudTexts.INSTANCE.width(s);
        int tx = x + layout.digitsWidth() - w;
        float pop = (float) ((now - changedAt) / POP_TICKS);
        if (pop >= 1) {
            TurnBarHud.darkText(context, s, tx, y, HudPaint.TEXT_DARK, 0xFFFFFFFF, alpha);
            return;
        }
        float scale = 1 + 0.5f * (1 - Easing.easeOutBack(pop));
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        float cx = tx + w / 2f, cy = y + 4;
        matrices.translate(cx, cy, 50);
        matrices.scale(scale, scale, 1);
        matrices.translate(-cx, -cy, 0);
        TurnBarHud.darkText(context, s, tx, y, HudPaint.TEXT_DARK, 0xFFFFFFFF, alpha);
        matrices.pop();
    }

    /** An item at its full size, 16 x 16 (any item, block items too). */
    private static void icon(DrawContext context, ItemStack stack, int x, int y) {
        if (!stack.isEmpty()) context.drawItem(stack, x, y);
    }

    /** An item at half size, 8 x 8 (a bonus in its slot). */
    private static void smallItem(DrawContext context, ItemStack stack, int x, int y) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x + 4, y + 4, 0);
        matrices.scale(0.5f, 0.5f, 1);
        context.drawItem(stack, -8, -8);
        matrices.pop();
    }
}
