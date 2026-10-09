package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.client.gui.ToolHud.Plate;
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

/**
 * The end of a party's mini-game, in the style of the mini-game card ({@link MiniGameCardHud}): the results card, in
 * the middle of the screen for a few seconds, a line per player (per team in a team mini-game) with its place, and
 * what the party paid for it (the coins and the stars, with their items). The results of a mini-game played out of a
 * party, or of a party's practice round, say that nothing was paid; while a mini-game is played out of a party, a chip
 * at the top of the screen says so (« Out of a party: ... »).
 */
public final class MiniGameResultsHud {
    private static final int WIDTH = 210, PAD = 7, ROW = 14, CHIP = 24;
    private static final int ROOM_ABOVE = 40, ROOM_BELOW = 64;
    private static final float IN_TICKS = 7, OUT_TICKS = 10, STAY_TICKS = 20 * 7;
    private static final int[] PLACE_COLORS = {0xFFFFC52E, 0xFFC9D3DA, 0xFFC9793F, 0xFF9C7FD6};

    private static @Nullable MiniGameResults results;
    private static double shownAt;
    /** The title of the mini-game being tested (empty: it has none), null while no test is played. */
    private static @Nullable String testTitle;

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

    /** A test of a mini-game is being played ({@code title}: its page's, empty for none), or is over (null). */
    public static void test(@Nullable String title) {
        testTitle = title;
    }

    /** @return true while the label of a test is on screen. */
    public static boolean isTesting() {
        return testTitle != null;
    }

    public static void clear() {
        results = null;
        testTitle = null;
    }

    /** @return true while the results card is on screen. */
    public static boolean isShown() {
        return results != null;
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (MinecraftClient.getInstance().options.hudHidden) return;
        if (testTitle != null) drawTestLabel(context, testTitle);
        if (results == null) return;
        double now = PartyHud.now();
        float age = (float) (now - shownAt);
        float in = Easing.easeOutCubic(age / IN_TICKS);
        float out = age < STAY_TICKS ? 1 : 1 - Easing.clamp01((age - STAY_TICKS) / OUT_TICKS);
        if (out <= 0) {
            results = null;
            return;
        }
        float alpha = Math.min(in, out);
        if (alpha < 0.08f) return;
        drawResults(context, results, alpha, (1 - in) * -10 + (1 - out) * -6);
    }

    /** « Test of the mini-game: ... », on a chip at the top of the screen. */
    private static void drawTestLabel(DrawContext context, String title) {
        TextRenderer font = HudDraw.font();
        Text name = title.isEmpty() ? Text.translatable("item.steveparty.mini_game_page") : Text.literal(title);
        OrderedText text = HudDraw.fit(Text.translatable("hud.steveparty.minigame.test", name), context.getScaledWindowWidth() - 40);
        int width = font.getWidth(text) + 14, x = (context.getScaledWindowWidth() - width) / 2, y = 6;
        HudDraw.plate(context, Plate.ORANGE, x, y, width, 15, 1);
        HudDraw.text(context, text, x + 7, y + 4, HudDraw.TEXT, 1);
    }

    private static void drawResults(DrawContext context, MiniGameResults shown, float alpha, float slide) {
        TextRenderer font = HudDraw.font();
        int screenWidth = context.getScaledWindowWidth(), screenHeight = context.getScaledWindowHeight();
        int room = screenHeight - ROOM_ABOVE - ROOM_BELOW;
        boolean hasTitle = !shown.title().isEmpty();
        int head = PAD + 12 + (hasTitle ? 10 : 0) + 3;
        int rows = Math.max(1, Math.min(shown.rows().size(), Math.max(1, (room - head - PAD) / ROW)));
        int height = head + rows * ROW + PAD - 2 + (shown.test() ? 11 : 0);
        int x = (screenWidth - WIDTH) / 2;
        int y = Math.max(4, ROOM_ABOVE + (room - height) / 2);

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0, slide, 0);
        HudDraw.plate(context, Plate.TEAL, x, y, WIDTH, height, alpha);
        Text heading = Text.translatable("hud.steveparty.minigame.results").styled(style -> style.withBold(true));
        HudDraw.text(context, heading, x + (WIDTH - font.getWidth(heading)) / 2, y + PAD, HudDraw.TEXT, alpha);
        if (hasTitle) {
            OrderedText title = HudDraw.fit(Text.literal(shown.title()), WIDTH - 2 * PAD);
            HudDraw.text(context, title, x + (WIDTH - font.getWidth(title)) / 2, y + PAD + 11, HudDraw.TEXT_SOFT, alpha);
        }
        int top = y + head;
        for (int i = 0; i < rows && i < shown.rows().size(); i++) {
            drawRow(context, font, shown, shown.rows().get(i), x + PAD, top, WIDTH - 2 * PAD, alpha);
            top += ROW;
        }
        if (shown.test()) {
            Text note = Text.translatable(shown.kind() == MiniGameResults.Kind.PRACTICE
                    ? "hud.steveparty.minigame.results.practice" : "hud.steveparty.minigame.results.test");
            HudDraw.text(context, note, x + (WIDTH - font.getWidth(note)) / 2, top + 1, HudDraw.TEXT_WARN, alpha);
        }
        matrices.pop();
    }

    private static void drawRow(DrawContext context, TextRenderer font, MiniGameResults shown, MiniGameResults.Row row,
                                int x, int y, int width, float alpha) {
        // The place, on a chip of its colour; the participants (no place) have none
        int left = x;
        if (row.place() > 0) {
            int color = PLACE_COLORS[Math.min(row.place(), PLACE_COLORS.length) - 1];
            context.fill(x, y, x + CHIP, y + ROW - 2, Argb.fade(HudDraw.OUTLINE, alpha));
            context.fill(x + 1, y + 1, x + CHIP - 1, y + ROW - 3, Argb.fade(color, alpha));
            Text place = row.place() <= 9 ? Text.translatable("hud.steveparty.party.rank." + row.place()) : Text.literal(String.valueOf(row.place()));
            HudDraw.text(context, place, x + (CHIP - font.getWidth(place)) / 2, y + 2, HudDraw.TEXT, alpha);
            left += CHIP + 4;
        }
        // What was paid, from the right
        int right = x + width;
        right = drawGain(context, font, shown.starItem(), row.stars(), right, y, alpha);
        right = drawGain(context, font, shown.coinItem(), row.coins(), right, y, alpha);
        // Who
        MutableText names = Text.empty();
        int color = row.place() == 0 ? HudDraw.TEXT_SOFT : HudDraw.TEXT;
        if (row.team() >= 0) {
            MiniGamePipeRole role = MiniGamePipeRole.ofTeam(row.team());
            names.append(role.text().copy().styled(style -> style.withBold(true))).append(" ");
            context.fill(left, y + 1, left + 3, y + ROW - 3, Argb.fade(Argb.opaque(role.color()), alpha));
            left += 5;
        } else if (row.place() == 0) {
            names.append(Text.translatable("hud.steveparty.minigame.results.participant")).append(" ");
        }
        names.append(String.join(", ", row.names()));
        HudDraw.text(context, HudDraw.fit(names, right - left - 4), left, y + 2, color, alpha);
    }

    /** « +10 [item] », right-aligned on {@code right}. @return the new right edge */
    private static int drawGain(DrawContext context, TextRenderer font, ItemStack item, int amount, int right, int y, float alpha) {
        if (amount <= 0) return right;
        String text = "+" + amount;
        int iconX = right - 10;
        if (alpha > 0.5f && !item.isEmpty()) {
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(iconX, y, 0);
            matrices.scale(10 / 16f, 10 / 16f, 1);
            context.drawItem(item, 0, 0);
            matrices.pop();
        }
        int textX = iconX - 2 - font.getWidth(text);
        HudDraw.text(context, text, textX, y + 2, HudDraw.TEXT_MINE, alpha);
        return textX - 6;
    }
}
