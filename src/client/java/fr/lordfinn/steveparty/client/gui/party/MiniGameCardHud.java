package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.client.gui.FormatChips;
import fr.lordfinn.steveparty.client.gui.MiniGamePageTooltipComponent;
import fr.lordfinn.steveparty.client.gui.ToolHud.Plate;
import fr.lordfinn.steveparty.client.minigame.MiniGamePageClient;
import fr.lordfinn.steveparty.minigame.MiniGameFormat;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGameText;
import fr.lordfinn.steveparty.utils.Argb;
import fr.lordfinn.steveparty.utils.Easing;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The card of the mini-game the party controller drew: its picture, its title, how it will be played and the start of
 * its description, in the middle of the screen from the end of the roulette to the departure. The countdown before
 * the departure pops on it. It slides in, and fades out when the players leave (or after a while, when a party bell
 * holds the departure).
 */
public final class MiniGameCardHud {
    /** Sizes of the picture, the biggest that fits the screen is used. */
    private static final int[][] PICTURE_SIZES = {{240, 135}, {192, 108}, {160, 90}, {128, 72}};
    private static final int PAD = 6;
    private static final int MAX_DESCRIPTION_LINES = 2;
    /** Room kept for the turn bar above and the hotbar and its messages below. */
    private static final int ROOM_ABOVE = 40, ROOM_BELOW = 64;
    private static final int BADGE = 26, BADGE_ROOM = 10;
    private static final float IN_TICKS = 7, OUT_TICKS = 10, COUNT_POP_TICKS = 8;
    /** Without news from the server, the card leaves by itself. */
    private static final float STAY_TICKS = 20 * 12, STAY_COUNTDOWN_TICKS = 20 * 3;

    private static @Nullable MiniGamePageData data;
    /** The index of the page's format played, -1 when not known. */
    private static int format = -1;
    private static int countdown;
    private static double shownAt, leaveAt, hidingAt = -1, countdownAt;

    private MiniGameCardHud() {
    }

    public static void initialize() {
        HudRenderCallback.EVENT.register(MiniGameCardHud::render);
    }

    /** Shows the card of a mini-game, or updates it (its countdown). */
    public static void show(MiniGamePageData page, int formatIndex, int seconds) {
        double now = PartyHud.now();
        if (data == null || hidingAt >= 0) shownAt = now;
        hidingAt = -1;
        data = page;
        format = formatIndex;
        if (seconds != countdown) countdownAt = now;
        countdown = seconds;
        leaveAt = now + (seconds > 0 ? STAY_COUNTDOWN_TICKS : STAY_TICKS);
    }

    /** The card fades out. */
    public static void hide() {
        if (data != null && hidingAt < 0) hidingAt = PartyHud.now();
    }

    public static void clear() {
        data = null;
        hidingAt = -1;
        countdown = 0;
    }

    /** @return true while the card is on screen (fading out included). */
    public static boolean isShown() {
        return data != null;
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (data == null) return;
        double now = PartyHud.now();
        if (hidingAt < 0 && now > leaveAt) hidingAt = now;
        float in = Easing.easeOutCubic((float) ((now - shownAt) / IN_TICKS));
        float out = hidingAt < 0 ? 1 : 1 - Easing.clamp01((float) ((now - hidingAt) / OUT_TICKS));
        if (out <= 0) {
            clear();
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;
        float alpha = Math.min(in, out);
        if (alpha < 0.08f) return;
        // Slides down into place, leaves upwards
        float slide = (1 - in) * -10 + (1 - out) * -6;
        draw(context, data, format, countdown, alpha, slide, now);
    }

    private static void draw(DrawContext context, MiniGamePageData page, int played, int countdown, float alpha, float slide, double now) {
        TextRenderer font = HudDraw.font();
        int screenWidth = context.getScaledWindowWidth(), screenHeight = context.getScaledWindowHeight();
        boolean hasPicture = page.image() != null;

        // The biggest picture that leaves the card on screen (the smallest one if none does)
        int pictureWidth = PICTURE_SIZES[PICTURE_SIZES.length - 1][0], pictureHeight = 0;
        int width = 0, height = 0;
        List<OrderedText> description = List.of();
        for (int[] size : PICTURE_SIZES) {
            pictureWidth = size[0];
            pictureHeight = hasPicture ? size[1] : 0;
            width = pictureWidth + 2 * PAD;
            description = page.description().isEmpty() ? List.of()
                    : MiniGamePageTooltipComponent.wrap(font, MiniGameText.parse(page.description()), pictureWidth, MAX_DESCRIPTION_LINES);
            int chipsHeight = FormatChips.flow(font, page.formats(),
                    i -> FormatChips.Look.READ_ONLY, pictureWidth, 3).getLast()[1] + 13;
            height = PAD + 12 + (hasPicture ? pictureHeight + 2 + 4 : 0) + chipsHeight + (description.isEmpty() ? 0 : 3 + 10 * description.size()) + PAD;
            if (width <= screenWidth - 16 && height <= screenHeight - ROOM_ABOVE - ROOM_BELOW) break;
        }
        int x = (screenWidth - width) / 2;
        int y = Math.max(4, ROOM_ABOVE + (screenHeight - ROOM_ABOVE - ROOM_BELOW - height) / 2);

        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0, slide, 0);
        HudDraw.plate(context, Plate.TEAL, x, y, width, height, alpha);
        int top = y + PAD;

        // Title
        Text title = Text.literal(page.title()).styled(style -> style.withBold(true));
        // Clear of the countdown badge on the corner
        OrderedText titleLine = HudDraw.fit(title, pictureWidth - 2 * BADGE_ROOM);
        HudDraw.text(context, titleLine, x + (width - font.getWidth(titleLine)) / 2, top + 1, HudDraw.TEXT, alpha);
        top += 12;

        // Picture, in a dark frame
        if (hasPicture) {
            context.fill(x + PAD - 1, top, x + PAD + pictureWidth + 1, top + pictureHeight + 2, Argb.fade(HudDraw.OUTLINE, alpha));
            MiniGamePageClient.Picture picture = MiniGamePageClient.picture(page.image(), pictureWidth, pictureHeight);
            if (picture != null) picture.draw(context, x + PAD, top + 1, pictureWidth, pictureHeight, HudDraw.white(alpha));
            top += pictureHeight + 2 + 4;
        }

        // How it is played: its formats (pawn chips), the one played gold rimmed; once faded in (the chips don't fade)
        java.util.List<MiniGameFormat> formats = page.formats();
        java.util.function.IntFunction<FormatChips.Look> look =
                i -> new FormatChips.Look(false, i == played, false, false, 13);
        java.util.List<int[]> at = FormatChips.flow(font, formats, look, pictureWidth, 3);
        int rowWidth = 0;
        for (int[] chip : at) if (chip[1] == 0) rowWidth = chip[0] + chip[2];
        if (alpha > 0.6f) {
            FormatChips.drawFlow(context, font, formats, look, x + (width - rowWidth) / 2, top, pictureWidth, 3);
        }
        top += at.getLast()[1] + 13;

        if (!description.isEmpty()) {
            top += 3;
            for (OrderedText line : description) {
                HudDraw.text(context, line, x + (width - font.getWidth(line)) / 2, top, HudDraw.TEXT_SOFT, alpha);
                top += 10;
            }
        }

        // Countdown: a gold badge on the corner of the card, popping at each second
        if (countdown > 0) {
            float pop = Easing.easeOutBack((float) ((now - countdownAt) / COUNT_POP_TICKS));
            float scale = 0.6f + 0.4f * pop;
            matrices.push();
            matrices.translate(x + width - 3, y + 3, 0);
            matrices.scale(scale, scale, 1);
            HudDraw.plate(context, Plate.GOLD, -BADGE / 2, -BADGE / 2, BADGE, BADGE, alpha);
            String digit = String.valueOf(countdown);
            matrices.scale(2, 2, 1);
            HudDraw.text(context, digit, -font.getWidth(digit) / 2, -4, HudDraw.TEXT, alpha);
            matrices.pop();
        }
        matrices.pop();
    }
}
