package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.client.gui.ToolHud.Plate;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The standings, Mario Party style: one plate per player, best first, each with its rank (« 1er » on gold, « 2e » on
 * silver, « 3e » on bronze), its face framed with its token's colour (a crown for the first), the token's name and its
 * player's name, its stars and coins (the party's items, with their icons) and the power-ups its player holds. The
 * player whose turn it is gets a gold plate, slid a little to the right.
 * <p>
 * Animated: plates glide to their new place when the ranks change, « +N » / « -N » rises from the stars or the coins
 * when they change, a power-up pops when one more is held. Too many players for the room: compact rows (no player name, power-ups
 * smaller, on the same line).
 */
final class StandingsHud {
    private static final int ROW = 24;
    private static final int ROW_COMPACT = 18;
    private static final int ROW_GAP = 2;
    private static final int RANK = 18;
    private static final int FACE = 16;
    private static final int NAME_MAX = 56;
    private static final int POWER_UPS_SHOWN = 3;
    private static final float CURRENT_SHIFT = 4;
    private static final float FLOATER_TICKS = 26;
    private static final float POP_TICKS = 8;

    private PartyHudModel model;
    private int roomHeight = -1;
    private boolean compact;
    private int rowHeight = ROW;
    private int width;
    private int height;
    private int nameColumn;
    private int rightColumn;
    private int rankColumn = RANK;
    /** The rows in the order of the model's players, and the order they are shown in (best first). */
    private final List<Row> rows = new ArrayList<>();
    private final Map<UUID, Row> rowsByToken = new HashMap<>();
    private double lastFrame;

    private static final class Row {
        UUID token;
        PartyHudModel.Player player;
        OrderedText name;
        OrderedText owner;
        int ownerColor;
        Text rankText;
        int rankWidth;
        final Counter stars = new Counter();
        final Counter coins = new Counter();
        String more;
        Plate rankPlate;
        int targetY;
        float shownY = Float.NaN;
        float shownShift;
        boolean current;
        // animations
        boolean firstUpdate = true;
        Map<String, Integer> lastCounts = new HashMap<>();
        double[] powerUpPopAt = new double[POWER_UPS_SHOWN];
    }

    /** A number with its item: pops, and lets a « +N » / « -N » rise, when it changes. */
    private static final class Counter {
        String text = "0";
        int width;
        int last = Integer.MIN_VALUE;
        String floater;
        int floaterColor;
        double floaterAt = -1000;
        double popAt = -1000;

        void update(int value, double now, TextRenderer font) {
            text = Integer.toString(value);
            width = font.getWidth(text);
            if (last != Integer.MIN_VALUE && value != last) {
                int gain = value - last;
                floater = (gain > 0 ? "+" : "") + gain;
                floaterColor = gain > 0 ? 0xFF2E9E2E : HudDraw.TEXT_WARN;
                floaterAt = now;
                popAt = now;
            }
            last = value;
        }

        /** Width of the item and the number. */
        int fullWidth() {
            return COUNTER_ICON + 1 + width;
        }
    }

    private static final int COUNTER_ICON = 10;
    private static final int COUNTER_GAP = 4;

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    /** Takes a new model or new room (unscaled height it may use): lays out again only then. */
    void update(PartyHudModel model, int room, double now) {
        if (model == this.model && room == roomHeight) return;
        this.model = model;
        this.roomHeight = room;
        TextRenderer font = HudDraw.font();
        int count = model.players.size();
        compact = count * (ROW + ROW_GAP) > room;
        rowHeight = compact ? ROW_COMPACT : ROW;

        // Rows kept by token, so that their animations go on
        List<Row> ordered = new ArrayList<>(count);
        Map<UUID, Row> kept = new HashMap<>();
        for (int i = 0; i < count; i++) {
            PartyHudModel.Player player = model.players.get(i);
            Row row = rowsByToken.getOrDefault(player.token, new Row());
            row.token = player.token;
            row.player = player;
            row.current = i == model.current;
            ordered.add(row);
            kept.put(player.token, row);
        }
        rowsByToken.clear();
        rowsByToken.putAll(kept);
        rows.clear();
        rows.addAll(ordered);

        // Texts and widths
        nameColumn = 0;
        rightColumn = 0;
        rankColumn = RANK;
        for (Row row : rows) {
            PartyHudModel.Player player = row.player;
            row.name = HudDraw.fit(Text.literal(player.name), NAME_MAX);
            Text owner;
            if (player.owner == null) {
                owner = Text.translatable("hud.steveparty.party.anyone");
                row.ownerColor = HudDraw.TEXT_SOFT;
            } else if (!player.online) {
                owner = Text.translatable("hud.steveparty.party.offline", player.ownerName);
                row.ownerColor = HudDraw.TEXT_WARN;
            } else {
                owner = Text.literal(player.ownerName);
                row.ownerColor = player.mine ? HudDraw.TEXT_MINE : HudDraw.TEXT_SOFT;
            }
            row.owner = HudDraw.fit(owner, NAME_MAX);
            nameColumn = Math.max(nameColumn, font.getWidth(row.name));
            if (!compact) nameColumn = Math.max(nameColumn, font.getWidth(row.owner));
            row.rankText = model.hasStandings ? Text.translatable("hud.steveparty.party.rank." + Math.min(player.rank, 9)) : Text.literal("-");
            row.rankWidth = font.getWidth(row.rankText);
            rankColumn = Math.max(rankColumn, row.rankWidth + 7);
            row.rankPlate = !model.hasStandings ? Plate.TEAL : switch (player.rank) {
                case 1 -> Plate.GOLD;
                case 2 -> Plate.TEAL;
                case 3 -> Plate.ORANGE;
                default -> Plate.PURPLE;
            };
            // Stars and coins, animated when they change
            row.stars.update(player.stars, now, font);
            row.coins.update(player.coins, now, font);
            boolean firstUpdate = row.firstUpdate;
            row.firstUpdate = false;
            int shown = Math.min(POWER_UPS_SHOWN, player.powerUps.size());
            row.more = player.powerUps.size() > shown ? "+" + (player.powerUps.size() - shown) : null;
            int itemSize = compact ? 10 : 12;
            int powerUps = shown * itemSize + (row.more != null ? font.getWidth(row.more) + 1 : 0);
            int currencies = row.stars.fullWidth() + COUNTER_GAP + row.coins.fullWidth();
            rightColumn = Math.max(rightColumn, compact ? currencies + (powerUps > 0 ? powerUps + 3 : 0) : Math.max(currencies, powerUps));
            Map<String, Integer> counts = new HashMap<>();
            for (int i = 0; i < player.powerUps.size(); i++) {
                ItemStack stack = player.powerUps.get(i);
                String key = stack.getItem().toString() + stack.getComponentChanges().hashCode();
                counts.put(key, stack.getCount());
                Integer before = row.lastCounts.get(key);
                if (i < POWER_UPS_SHOWN && !firstUpdate && (before == null || stack.getCount() > before))
                    row.powerUpPopAt[i] = now;
            }
            row.lastCounts = counts;
        }
        nameColumn = Math.min(nameColumn, NAME_MAX);

        // Order: best rank first, the turn order between ties
        List<Row> byRank = new ArrayList<>(rows);
        byRank.sort((a, b) -> Integer.compare(a.player.rank, b.player.rank));
        for (int i = 0; i < byRank.size(); i++) {
            Row row = byRank.get(i);
            row.targetY = i * (rowHeight + ROW_GAP);
            if (Float.isNaN(row.shownY)) row.shownY = row.targetY;
        }
        width = 2 + rankColumn + 3 + FACE + 4 + nameColumn + 6 + rightColumn + 5 + (int) CURRENT_SHIFT;
        height = Math.max(0, count * (rowHeight + ROW_GAP) - ROW_GAP);
    }

    void draw(DrawContext context, float alpha, double now) {
        if (model == null || alpha <= 0.02f) return;
        float delta = (float) MathHelper.clamp(now - lastFrame, 0, 5);
        lastFrame = now;
        int rowWidth = width - (int) CURRENT_SHIFT;
        // The current player's row last: drawn over the others while they swap
        for (int pass = 0; pass < 2; pass++) {
            for (Row row : rows) {
                if (row.current != (pass == 1)) continue;
                row.shownY = HudDraw.approach(row.shownY, row.targetY, 0.3f, delta);
                row.shownShift = HudDraw.approach(row.shownShift, row.current ? CURRENT_SHIFT : 0, 0.35f, delta);
                drawRow(context, row, Math.round(row.shownShift), Math.round(row.shownY), rowWidth, alpha, now);
            }
        }
    }

    private void drawRow(DrawContext context, Row row, int x, int y, int rowWidth, float alpha, double now) {
        PartyHudModel.Player player = row.player;
        TextRenderer font = HudDraw.font();
        HudDraw.plate(context, row.current ? Plate.GOLD : Plate.TEAL, x, y, rowWidth, rowHeight, alpha);
        // Rank
        int rankSize = compact ? rowHeight - 4 : RANK;
        int rankWidth = rankColumn - (RANK - rankSize);
        int rankY = y + (rowHeight - rankSize) / 2;
        HudDraw.plate(context, row.rankPlate, x + 2, rankY, rankWidth, rankSize, alpha);
        HudDraw.text(context, row.rankText, x + 2 + (rankWidth - row.rankWidth) / 2 + 1, rankY + (rankSize - 8) / 2 + 1, HudDraw.TEXT, alpha);
        // Face (a crown on the first's)
        int faceSize = compact ? 14 : FACE;
        int faceX = x + 2 + rankColumn + 3;
        int faceY = y + (rowHeight - faceSize) / 2;
        HudDraw.face(context, player.owner, player.name, player.color, faceX, faceY, faceSize, alpha, false);
        if (model.hasStandings && player.rank == 1 && (player.stars > 0 || player.coins > 0) && !compact)
            HudDraw.icon(context, HudDraw.ICON_CROWN, faceX + faceSize - 6, faceY - 4, alpha);
        // Names
        int nameX = faceX + FACE + 4;
        if (compact) {
            HudDraw.text(context, row.name, nameX, y + (rowHeight - 8) / 2, player.mine ? HudDraw.TEXT_MINE : HudDraw.TEXT, alpha);
        } else {
            HudDraw.text(context, row.name, nameX, y + 4, HudDraw.TEXT, alpha);
            HudDraw.text(context, row.owner, nameX, y + 13, row.ownerColor, alpha);
        }
        // Stars and coins (their « +N » rise at the end, above everything)
        int rightX = nameX + nameColumn + 6;
        int countersY = compact ? y + (rowHeight - COUNTER_ICON) / 2 : y + 2;
        MatrixStack matrices = context.getMatrices();
        drawCounter(context, model.starItem, row.stars, rightX, countersY, alpha, now);
        int coinsX = rightX + row.stars.fullWidth() + COUNTER_GAP;
        drawCounter(context, model.coinItem, row.coins, coinsX, countersY, alpha, now);
        // Power-ups
        int shown = Math.min(POWER_UPS_SHOWN, player.powerUps.size());
        int itemSize = compact ? 10 : 12;
        int itemsX = compact ? coinsX + row.coins.fullWidth() + 3 : rightX;
        int itemsY = compact ? y + (rowHeight - itemSize) / 2 : y + rowHeight - itemSize - 1;
        if (alpha > 0.6f) {
            for (int i = 0; i < shown; i++) {
                ItemStack stack = player.powerUps.get(i);
                float itemPop = (float) ((now - row.powerUpPopAt[i]) / POP_TICKS);
                float scale = itemSize / 16f * (itemPop < 1 ? 1 + 0.6f * (1 - HudDraw.easeOutBack(itemPop)) : 1);
                matrices.push();
                matrices.translate(itemsX + i * itemSize + itemSize / 2f, itemsY + itemSize / 2f, 0);
                matrices.scale(scale, scale, 1);
                context.drawItem(stack, -8, -8);
                if (stack.getCount() > 1) context.drawStackOverlay(font, stack, -8, -8);
                matrices.pop();
            }
        }
        if (row.more != null)
            HudDraw.text(context, row.more, itemsX + shown * itemSize + 1, itemsY + (itemSize - 8) / 2 + 1, HudDraw.TEXT_SOFT, alpha);
        drawFloater(context, font, row.stars, rightX, countersY, alpha, now);
        drawFloater(context, font, row.coins, coinsX, countersY, alpha, now);
    }

    /** The currency's item (10 px) and its number, which pops when it changes. */
    private static void drawCounter(DrawContext context, ItemStack icon, Counter counter, int x, int y, float alpha, double now) {
        MatrixStack matrices = context.getMatrices();
        if (alpha > 0.6f && !icon.isEmpty()) {
            matrices.push();
            matrices.translate(x + COUNTER_ICON / 2f, y + COUNTER_ICON / 2f, 0);
            matrices.scale(COUNTER_ICON / 16f, COUNTER_ICON / 16f, 1);
            context.drawItem(icon, -8, -8);
            matrices.pop();
        }
        float numberX = x + COUNTER_ICON + 1;
        float pop = (float) ((now - counter.popAt) / POP_TICKS);
        if (pop < 1) {
            float scale = 1 + 0.5f * (1 - HudDraw.easeOutBack(pop));
            matrices.push();
            // In front of the item (drawn at a depth of its own)
            matrices.translate(numberX + counter.width / 2f, y + 5, 200);
            matrices.scale(scale, scale, 1);
            HudDraw.text(context, counter.text, -counter.width / 2, -4, HudDraw.TEXT, alpha);
            matrices.pop();
        } else {
            HudDraw.text(context, counter.text, Math.round(numberX), y + 1, HudDraw.TEXT, alpha);
        }
    }

    /** The « +N » / « -N » rising from a counter that changed. */
    private static void drawFloater(DrawContext context, TextRenderer font, Counter counter, int x, int y, float alpha, double now) {
        float floater = (float) ((now - counter.floaterAt) / FLOATER_TICKS);
        if (counter.floater == null || floater >= 1) return;
        int fy = Math.round(y - 2 - HudDraw.easeOutCubic(floater) * 10);
        float fade = alpha * (floater < 0.6f ? 1 : 1 - (floater - 0.6f) / 0.4f);
        // Above the items (drawn at a depth of their own) and the next rows
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0, 0, 400);
        context.drawText(font, counter.floater, x + COUNTER_ICON + 1 + counter.width + 2, fy, HudDraw.fade(counter.floaterColor, fade), true);
        matrices.pop();
    }
}
