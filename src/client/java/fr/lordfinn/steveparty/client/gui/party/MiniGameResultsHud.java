package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.client.gui.HudDepth;
import fr.lordfinn.steveparty.client.gui.ToolHud.Plate;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.minigame.MiniGameResults;
import fr.lordfinn.steveparty.utils.Argb;
import fr.lordfinn.steveparty.utils.Easing;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The end of a party's mini-game, in the style of the mini-game card ({@link MiniGameCardHud}): the results card, in
 * the middle of the screen for a few seconds, a line per player (per team in a team mini-game) with its place, and
 * what the party paid for it (the coins and the stars, with their items). The results of a mini-game played out of a
 * party, or of a party's practice round, say that nothing was paid.
 */
public final class MiniGameResultsHud {
    private static final int WIDTH = 210, PAD = 7, ROW = 14, CHIP = 24;
    private static final int ROOM_ABOVE = 40, ROOM_BELOW = 64, PARTY_END_TOP = 10;
    private static final float IN_TICKS = 7, OUT_TICKS = 10, STAY_TICKS = 20 * 7, PARTY_END_STAY_TICKS = 20 * 12;
    private static final int[] PLACE_COLORS = {0xFFFFC52E, 0xFFC9D3DA, 0xFFC9793F, 0xFF9C7FD6};

    private static @Nullable MiniGameResults results;
    private static double shownAt;

    private MiniGameResultsHud() {
    }

    public static void initialize() {
        HudRenderCallback.EVENT.register(MiniGameResultsHud::render);
    }

    /** Shows the results of the mini-game that just ended. */
    public static void show(MiniGameResults shown) {
        results = shown;
        shownAt = PartyHud.now();
    }

    public static void clear() {
        results = null;
    }

    /** @return true while the results card is on screen. */
    public static boolean isShown() {
        return results != null;
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (MinecraftClient.getInstance().options.hudHidden) return;
        if (results == null) return;
        double now = PartyHud.now();
        float age = (float) (now - shownAt);
        float in = Easing.easeOutCubic(age / IN_TICKS);
        float stay = results.kind().partyEnd() ? PARTY_END_STAY_TICKS : STAY_TICKS;
        float out = age < stay ? 1 : 1 - Easing.clamp01((age - stay) / OUT_TICKS);
        if (out <= 0) {
            results = null;
            return;
        }
        float alpha = Math.min(in, out);
        if (alpha < 0.08f) return;
        drawResults(context, results, alpha, (1 - in) * -10 + (1 - out) * -6);
    }

    private static void drawResults(DrawContext context, MiniGameResults shown, float alpha, float slide) {
        TextRenderer font = HudDraw.font();
        int screenWidth = context.getScaledWindowWidth(), screenHeight = context.getScaledWindowHeight();
        int room = screenHeight - ROOM_ABOVE - ROOM_BELOW;
        // The title and the note under the rows wrap: the card grows with them
        List<OrderedText> title = shown.title().isEmpty() ? List.of() : UiText.wrap(font, Text.literal(shown.title()), WIDTH - 2 * PAD);
        Text note = Text.translatable(switch (shown.kind()) {
            case PRACTICE -> "hud.steveparty.minigame.results.practice";
            case PARTY_END_TIE -> "hud.steveparty.party.results.tie";
            default -> "hud.steveparty.minigame.results.test";
        });
        boolean noted = shown.test() || shown.kind() == MiniGameResults.Kind.PARTY_END_TIE;
        List<OrderedText> noteLines = noted ? UiText.wrap(font, note, WIDTH - 2 * PAD) : List.of();
        int head = PAD + 12 + UiText.LINE_H * title.size() + 3;
        int rows = Math.max(1, Math.min(shown.rows().size(), Math.max(1, (room - head - PAD) / ROW)));
        int height = head + rows * ROW + PAD - 2 + (noteLines.isEmpty() ? 0 : 1 + UiText.LINE_H * noteLines.size());
        int x = (screenWidth - WIDTH) / 2;
        // A party's end stays long, while its lines fill the chat: the card at the top, clear of the chat
        int y = shown.kind().partyEnd() ? PARTY_END_TOP : Math.max(4, ROOM_ABOVE + (room - height) / 2);

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0, slide, 0);
        HudDraw.plate(context, Plate.TEAL, x, y, WIDTH, height, alpha);
        Text heading = Text.translatable(shown.kind().partyEnd() ? "hud.steveparty.party.results" : "hud.steveparty.minigame.results")
                .styled(style -> style.withBold(true));
        HudDraw.centered(context, heading, x + PAD, y + PAD, WIDTH - 2 * PAD, HudDraw.TEXT, alpha);
        for (int i = 0; i < title.size(); i++) {
            HudDraw.centered(context, title.get(i), x + PAD, y + PAD + 11 + UiText.LINE_H * i, WIDTH - 2 * PAD, HudDraw.TEXT_SOFT, alpha);
        }
        int top = y + head;
        for (int i = 0; i < rows && i < shown.rows().size(); i++) {
            drawRow(context, font, shown, shown.rows().get(i), x + PAD, top, WIDTH - 2 * PAD, alpha);
            top += ROW;
        }
        for (int i = 0; i < noteLines.size(); i++) {
            HudDraw.centered(context, noteLines.get(i), x + PAD, top + 1 + UiText.LINE_H * i, WIDTH - 2 * PAD,
                    shown.kind().partyEnd() ? HudDraw.TEXT_SOFT : HudDraw.TEXT_WARN, alpha);
        }
        matrices.pop();
    }

    private static void drawRow(DrawContext context, TextRenderer font, MiniGameResults shown, MiniGameResults.Row row,
                                int x, int y, int width, float alpha) {
        // The place, on a chip of its colour; the participants (no place) have none
        int left = x;
        if (row.place() > 0) {
            int color = PLACE_COLORS[Math.min(row.place(), PLACE_COLORS.length) - 1];
            HudDraw.fill(context, x, y, x + CHIP, y + ROW - 2, HudDraw.OUTLINE, alpha);
            HudDraw.fill(context, x + 1, y + 1, x + CHIP - 1, y + ROW - 3, color, alpha);
            Text place = row.place() <= 9 ? Text.translatable("hud.steveparty.party.rank." + row.place()) : Text.literal(String.valueOf(row.place()));
            HudDraw.centered(context, place, x + 1, y + 2, CHIP - 2, HudDraw.TEXT, alpha);
            left += CHIP + 4;
        }
        // What was paid, from the right
        int right = x + width;
        // A party's end: what each has (0 included), not what was won
        boolean totals = shown.kind().partyEnd();
        right = drawGain(context, font, shown.starItem(), row.stars(), totals, right, y, alpha);
        right = drawGain(context, font, shown.coinItem(), row.coins(), totals, right, y, alpha);
        // Who
        MutableText names = Text.empty();
        int color = row.place() == 0 ? HudDraw.TEXT_SOFT : HudDraw.TEXT;
        if (row.team() >= 0) {
            MiniGamePipeRole role = MiniGamePipeRole.ofTeam(row.team());
            names.append(role.text().copy().styled(style -> style.withBold(true))).append(" ");
            HudDraw.fill(context, left, y + 1, left + 3, y + ROW - 3, Argb.opaque(role.color()), alpha);
            left += 5;
        } else if (row.place() == 0) {
            names.append(Text.translatable("hud.steveparty.minigame.results.participant")).append(" ");
        }
        names.append(String.join(", ", row.names()));
        HudDraw.text(context, names, left, y + 2, right - left - 4, color, alpha);
    }

    /** « +10 [item] » (« 10 [item] » for a {@code total}), right-aligned on {@code right}. @return the new right edge */
    private static int drawGain(DrawContext context, TextRenderer font, ItemStack item, int amount, boolean total, int right, int y, float alpha) {
        if (amount <= 0 && !total) return right;
        String text = total ? String.valueOf(amount) : "+" + amount;
        int iconX = right - 10;
        if (alpha > 0.5f && !item.isEmpty()) {
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(iconX, y, 0);
            matrices.scale(10 / 16f, 10 / 16f, 1);
            HudDepth.item(context, () -> context.drawItem(item, 0, 0));
            matrices.pop();
        }
        // The row makes room for it (the names take what is left)
        int textWidth = font.getWidth(text), textX = iconX - 2 - textWidth;
        HudDraw.text(context, text, textX, y + 2, textWidth, total ? HudDraw.TEXT : HudDraw.TEXT_MINE, alpha);
        return textX - 6;
    }
}
