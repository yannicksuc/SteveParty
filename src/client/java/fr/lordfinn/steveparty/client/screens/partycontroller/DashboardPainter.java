package fr.lordfinn.steveparty.client.screens.partycontroller;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLiveData;
import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.GuiItems;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.client.gui.paint.Ramp;
import fr.lordfinn.steveparty.client.utils.SkinUtils;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.client.screens.partycontroller.DashboardStyle.*;

/**
 * The dashboard's drawing kit, shared by its pages: header rows, light and dark text, one-line texts that may not fit
 * (scrolling in their box, see {@link UiText}), player rows, rank discs, heads, ghost items, tooltips. Coordinates are the
 * panel's (the pages are drawn from its corner).
 */
public final class DashboardPainter {
    private final Dashboard dashboard;

    public DashboardPainter(Dashboard dashboard) {
        this.dashboard = dashboard;
    }

    private TextRenderer font() {
        return dashboard.font();
    }

    /**
     * A header row (12 px): its title (or a pill) on the left, « Read only » before the « i » for who may not change
     * what the tab sets.
     */
    public void header(DrawContext context, Text title, boolean readOnly) {
        int room = INFO_X - 4 - CX;
        if (readOnly) room -= readOnly(context, CY + 2) + 6;
        line(context, title, CX, CY + 2, room, WHITE);
    }

    /** « Read only » in red, ending 4 px before the « i », on the row at {@code ty} (at most half of it). @return its width */
    public int readOnly(DrawContext context, int ty) {
        Text locked = Text.translatable(KEY + "read_only");
        int lw = Math.min(font().getWidth(locked) - 1, (INFO_X - 4 - CX) / 2);
        UiText.line(context, font(), locked, INFO_X - 4 - lw, ty, lw + 1, INK_RED, true);
        return lw;
    }

    /** A gold pill on the header row (up to 4 px before the « i »), its label dark (the round). */
    public void headerPill(DrawContext context, Text label) {
        int w = Math.min(font().getWidth(label) - 1 + 10, INFO_X - 4 - CX);
        ConsolePaint.pill(context, CX, CY, w, ROW_H, FRAME, true);
        ConsolePaint.darkText(context, font(), label.asOrderedText(), CX + 5, CY + 2, w - 9, GOLD_DARK, GOLD_LIGHT);
    }

    /** Dark text with a light shadow (the mock-ups' {@code dark}). */
    public void dark(DrawContext context, OrderedText text, int tx, int ty, int colour, int shade) {
        ConsolePaint.darkText(context, font(), text, tx, ty, colour, shade);
    }

    /** Light text in a box of its own width: for short symbols whose place follows them (« ‹ », « +3 »). */
    public void light(DrawContext context, Text text, int tx, int ty, int colour) {
        UiText.line(context, font(), text, tx, ty, font().getWidth(text), colour, true);
    }

    /** Light text in a box {@code width} wide: too long, it scrolls in it. */
    public void light(DrawContext context, Text text, int tx, int ty, int width, int colour) {
        UiText.line(context, font(), text, tx, ty, width, colour, true);
    }

    /** A number centred (light) in a field {@code width} wide (too wide, it scrolls in it). */
    public void centred(DrawContext context, String value, int fieldX, int width, int ty, int colour) {
        int w = font().getWidth(value);
        if (w <= width) light(context, Text.literal(value), fieldX + (width - w + 1) / 2, ty, w, colour);
        else light(context, Text.literal(value), fieldX, ty, width, colour);
    }

    /** « [star] 3  [coin] 12 » at the right of a row (56 px): each number up to the next icon, or the row's end. */
    public void counts(DrawContext context, int stars, int coins, int cx, int cy) {
        smallItem(context, dashboard.currency(PartyCurrency.STAR), cx, cy, 8);
        light(context, Text.literal(Integer.toString(stars)), cx + 10, cy, 16, WHITE);
        smallItem(context, dashboard.currency(PartyCurrency.COIN), cx + 26, cy, 8);
        light(context, Text.literal(Integer.toString(coins)), cx + 36, cy, 20, WHITE);
    }

    private static void smallItem(DrawContext context, ItemStack stack, int ix, int iy, int size) {
        if (!stack.isEmpty()) GuiItems.scaled(context, stack, ix, iy, size / 16f);
    }

    /** A faded item (an empty slot's, a ghost card): under the slot's colour, its count with it. */
    public void ghost(DrawContext context, ItemStack stack, int gx, int gy, boolean count) {
        PartyGui.ghostItem(context, stack, gx, gy, count ? font() : null, GHOST_VEIL);
    }

    /** A scroll bar on the screen: a dark track, a lighter thumb. */
    public static void scrollbar(DrawContext context, int sx, int sy, int height, int scroll, int maxScroll, int shown, int total) {
        context.fill(sx, sy, sx + 3, sy + height, SLOT_BODY);
        int thumb = Math.max(8, height * shown / Math.max(shown, total));
        int ty = sy + (maxScroll == 0 ? 0 : (height - thumb) * scroll / maxScroll);
        context.fill(sx, ty, sx + 3, ty + thumb, SLOT_LOW);
    }

    // ------------------------------------------------------------------ one-line texts that may not fit

    /**
     * {@code text} (light, shadowed) on one line in a box {@code width} wide, its last column of shadow left out: too
     * long, it scrolls in it (its whole text in a tooltip under the mouse).
     */
    public void line(DrawContext context, Text text, int tx, int ty, int width, int colour) {
        UiText.line(context, font(), text, tx, ty, width + 1, colour, true);
    }

    /** Same as {@link #line} ({@code hovered} no longer matters: a text too long always scrolls in its box). */
    public void fitted(DrawContext context, Text text, int tx, int ty, int maxWidth, int color, boolean hovered) {
        line(context, text, tx, ty, maxWidth, color);
    }

    // ------------------------------------------------------------------ players

    /** A player's row: its turn, its rank, its head, its token and player, its stars and coins. */
    public void playerRow(DrawContext context, PartyLiveData.Standing player, int index, int rank, int ry, int rowWidth,
                          boolean current, boolean ranked, int mx, int my) {
        boolean hovered = HitArea.contains(mx, my, CX, ry, rowWidth, ROW - 2);
        ConsolePaint.inset(context, CX, ry, rowWidth - 1, 15, current ? 0xFF3D3460 : SLOT_BODY, current ? 0xFFFFC52E : SLOT_EDGE, SLOT_LOW);
        // Its turn, up to its rank disc (or its head)
        light(context, Text.literal(Integer.toString(index + 1)), CX + 4, ry + 4, ranked ? 10 : 26, INK_SOFT);
        if (ranked) rankDisc(context, rank, CX + 14, ry + 2);
        ConsolePaint.box(context, CX + 30, ry + 3, 10, 10, HEAD_FRAME, 1, 1);
        head(context, player, CX + 31, ry + 4);
        int countsX = CX + rowWidth - 56;
        int namesRoom = countsX - (CX + 44) - 4, tokenRoom = Math.min(36, namesRoom / 2), ownerRoom = namesRoom - tokenRoom - 4;
        fitted(context, Text.literal(player.tokenName()), CX + 44, ry + 4, tokenRoom, WHITE, hovered);
        int ownerColor = player.owner().isEmpty() ? INK_SOFT : player.online() ? INK_BLUE : INK_WARN;
        line(context, owner(player), CX + 44 + tokenRoom + 4, ry + 4, ownerRoom, ownerColor);
        counts(context, player.stars(), player.coins(), countsX, ry + 4);
    }

    private static Text owner(PartyLiveData.Standing player) {
        if (player.owner().isEmpty()) return Text.translatable("hud.steveparty.party.anyone");
        if (!player.online()) return Text.translatable("hud.steveparty.party.offline", player.ownerName());
        return Text.literal(player.ownerName());
    }

    /** A token's head, 8 x 8: its player's skin's face, else its colour. */
    public static void head(DrawContext context, PartyLiveData.Standing player, int hx, int hy) {
        if (player.owner().isPresent()) {
            Identifier skin = SkinUtils.getPlayerSkin(player.owner().get());
            RenderSystem.enableBlend();
            context.drawTexture(skin, hx, hy, 8, 8, 8, 8, 8, 8, 64, 64);
            context.drawTexture(skin, hx, hy, 8, 8, 40, 8, 8, 8, 64, 64);
            RenderSystem.disableBlend();
        } else {
            context.fill(hx, hy, hx + 8, hy + 8, player.color() < 0 ? 0xFF8B8B8B : 0xFF000000 | player.color());
        }
    }

    private static Ramp rankRamp(int rank) {
        return switch (rank) {
            case 1 -> RANK_GOLD;
            case 2 -> RANK_SILVER;
            case 3 -> RANK_BRONZE;
            default -> NEUTRAL;
        };
    }

    /** A 12 px disc in the rank's colour, its number dark. */
    public void rankDisc(DrawContext context, int rank, int dx, int dy) {
        Ramp ramp = rankRamp(rank);
        ConsolePaint.disc(context, dx, dy, ROW_H, ramp);
        Text number = Text.literal(Integer.toString(Math.min(rank, 9)));
        dark(context, number.asOrderedText(), dx + (ROW_H - font().getWidth(number) + 1) / 2, dy + 2, ramp.outline(), ramp.hi());
    }

    /** Indexes sorted by rank, the turn order between ties. */
    public static List<Integer> byRank(int[] ranks) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < ranks.length; i++) order.add(i);
        order.sort((a, b) -> Integer.compare(ranks[a], ranks[b]));
        return order;
    }

    /** The player ahead, null before anyone has stars or coins. */
    public static @Nullable PartyLiveData.Standing leader(PartyDashboardData data) {
        List<PartyLiveData.Standing> players = data.players();
        if (players.isEmpty() || players.stream().allMatch(p -> p.stars() == 0 && p.coins() == 0)) return null;
        int[] ranks = PartyLiveData.ranks(players);
        return players.get(byRank(ranks).getFirst());
    }

    // ------------------------------------------------------------------ tooltips

    /** A tooltip of several lines, each wrapped to the dashboard's tooltip width. */
    public void tooltip(DrawContext context, List<Text> lines, int mouseX, int mouseY) {
        // wrapLines also breaks at the line breaks, keeping the styles
        List<OrderedText> wrapped = new ArrayList<>();
        for (Text line : lines) wrapped.addAll(font().wrapLines(line, TOOLTIP_WIDTH));
        context.drawOrderedTooltip(font(), wrapped, mouseX, mouseY);
    }

    /** Joins texts with line breaks (tooltips). */
    public static Text join(List<Text> texts) {
        var joined = Text.empty();
        for (int i = 0; i < texts.size(); i++) {
            if (i > 0) joined.append("\n");
            joined.append(texts.get(i));
        }
        return joined;
    }
}
