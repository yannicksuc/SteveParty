package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.client.gui.ToolHud.Plate;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.minigame.MiniGameFormat;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.minigame.MiniGameText;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads.RoundCard;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads.StartVote;
import fr.lordfinn.steveparty.utils.Argb;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The mini-game round the player is in, at the top of the screen in place of the party's HUD: a framed card with its
 * name, what to do (the first sentence of its description), his team (its colour) and how it is played (free for all,
 * 2 against 2...). While its description is told before « Go! », a chip under it shows who is ready
 * (« [Y] Ready 1/3 »).
 */
public final class MiniGameRoundHud {
    private static final int WIDTH = 190, PAD = 6, TOP = 4, CHIP = 15, GAP = 3, OBJECTIVE_LINES = 2;
    private static final float FADE_RATE = 0.35f;

    private static @Nullable RoundCard card;
    /** The last card shown: drawn while it fades out. */
    private static @Nullable RoundCard drawn;
    private static @Nullable StartVote vote;
    private static float alpha;
    private static double lastFrame;
    /** Where the card was drawn (x, y, width, height), height 0 when it is not. */
    private static final int[] BOUNDS = new int[4];

    private MiniGameRoundHud() {
    }

    public static void initialize() {
        HudRenderCallback.EVENT.register(MiniGameRoundHud::render);
    }

    public static void show(RoundCard shown) {
        card = shown.show() ? shown : null;
        if (card != null) drawn = card;
        else vote = null;
    }

    public static void vote(StartVote shown) {
        vote = shown.show() ? shown : null;
    }

    public static void clear() {
        card = null;
        drawn = null;
        vote = null;
        alpha = 0;
    }

    /** @return true while the player is in a mini-game's round: the party's HUD gives way to the card. */
    public static boolean isShown() {
        return card != null;
    }

    /** @return true while the player may say he is ready before his round starts (the key sends it). */
    public static boolean canVote() {
        return vote != null && vote.voter();
    }

    /** @return true while the chip of the start's vote is on screen (the practice chip waits under it). */
    public static boolean isVoteShown() {
        return vote != null;
    }

    /** Under the card (centre x, top y) when it is on screen, null otherwise. */
    static float @Nullable [] below() {
        if (card == null || BOUNDS[3] <= 0) return null;
        int bottom = BOUNDS[1] + BOUNDS[3] + GAP + (vote != null ? CHIP + GAP : 0);
        return new float[]{BOUNDS[0] + BOUNDS[2] / 2f, bottom};
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        double now = PartyHud.now();
        float delta = (float) Math.clamp(now - lastFrame, 0, 5);
        lastFrame = now;
        alpha = HudDraw.approach(alpha, card != null ? 1 : 0, FADE_RATE, delta);
        if (alpha <= 0.02f || drawn == null) {
            BOUNDS[3] = 0;
            if (card == null) drawn = null;
            return;
        }
        if (client.options.hudHidden || client.getDebugHud().shouldShowDebugHud()) return;
        draw(context, drawn, card != null ? vote : null, alpha);
    }

    static void draw(DrawContext context, RoundCard shown, @Nullable StartVote voteShown, float fade) {
        TextRenderer font = HudDraw.font();
        int screenWidth = context.getScaledWindowWidth();
        int width = Math.min(WIDTH, screenWidth - 16);
        int inner = width - 2 * PAD;
        Text title = shown.title().isEmpty() ? Text.translatable("hud.steveparty.minigame.in.untitled")
                : Text.literal(shown.title());
        List<OrderedText> objective = shown.objective().isEmpty() ? List.of()
                : UiText.wrap(font, MiniGameText.parse(shown.objective(), Style.EMPTY), inner);
        if (objective.size() > OBJECTIVE_LINES) objective = objective.subList(0, OBJECTIVE_LINES);
        Text type = typeText(shown);
        Text team = teamText(shown);
        boolean bottomRow = type != null || team != null;
        int height = PAD + 9 + (objective.isEmpty() ? 0 : 3 + UiText.LINE_H * objective.size()) + (bottomRow ? 5 + 11 : 0) + PAD - 1;
        // Under the boss bars and clear of the status effects, as the party HUD
        int x = (screenWidth - width) / 2, y = Math.round(PartyHud.keepClear(x, TOP, width, height, screenWidth, context.getScaledWindowHeight()));
        BOUNDS[0] = x;
        BOUNDS[1] = y;
        BOUNDS[2] = width;
        BOUNDS[3] = height;

        HudDraw.plate(context, Plate.TEAL, x, y, width, height, fade);
        // The name, after the mini-game icon, centred together
        int titleWidth = Math.min(font.getWidth(title.copy().styled(style -> style.withBold(true))), inner - HudDraw.ICON - 4);
        int left = x + (width - (HudDraw.ICON + 4 + titleWidth)) / 2;
        HudDraw.icon(context, HudDraw.ICON_MINI_GAME, left, y + PAD - 1, fade);
        HudDraw.text(context, title.copy().styled(style -> style.withBold(true)), left + HudDraw.ICON + 4, y + PAD, titleWidth, HudDraw.TEXT, fade);
        int top = y + PAD + 9;
        if (!objective.isEmpty()) {
            top += 3;
            for (OrderedText line : objective) {
                HudDraw.centered(context, line, x + PAD, top, inner, HudDraw.TEXT_SOFT, fade);
                top += UiText.LINE_H;
            }
        }
        if (bottomRow) {
            top += 5;
            // His team on the left (its colour), how it is played on the right
            int right = x + width - PAD;
            if (type != null) {
                int typeWidth = Math.min(font.getWidth(type), inner / 2);
                HudDraw.text(context, type, right - typeWidth, top + 2, typeWidth, HudDraw.TEXT_SOFT, fade);
                right -= typeWidth + 6;
            }
            if (team != null) {
                int color = shown.team() >= 0 ? Argb.opaque(MiniGamePipeRole.ofTeam(shown.team()).color()) : 0xFFA0A0A0;
                HudDraw.fill(context, x + PAD, top + 1, x + PAD + 9, top + 10, HudDraw.OUTLINE, fade);
                HudDraw.fill(context, x + PAD + 1, top + 2, x + PAD + 8, top + 9, color, fade);
                HudDraw.text(context, team, x + PAD + 13, top + 2, right - (x + PAD + 13), HudDraw.TEXT, fade);
            }
        }
        if (voteShown != null) drawVote(context, voteShown, x + width / 2f, y + height + GAP, fade);
    }

    /** « [Y] Ready 1/3 » (green once the player is), « Ready 1/3 » for those who only watch. */
    private static void drawVote(DrawContext context, StartVote shown, float centre, int y, float fade) {
        TextRenderer font = HudDraw.font();
        Text label = !shown.voter() ? Text.translatable("hud.steveparty.minigame.start.watching", shown.ready(), shown.total())
                : Text.translatable(shown.mine() ? "hud.steveparty.minigame.start.ready" : "hud.steveparty.minigame.start",
                MiniGamePracticeHud.keyText(), shown.ready(), shown.total());
        int textWidth = Math.min(font.getWidth(label), context.getScaledWindowWidth() - 40);
        int width = textWidth + 14;
        int x = Math.round(centre - width / 2f);
        HudDraw.plate(context, shown.mine() ? Plate.GREEN : Plate.ORANGE, x, y, width, CHIP, fade);
        HudDraw.text(context, label, x + 7, y + 4, textWidth, HudDraw.TEXT, fade);
    }

    /** « Free for all », « All together », « 2 against 2 »; null when unknown. */
    static @Nullable Text typeText(RoundCard shown) {
        if (shown.kind() < 0 || shown.kind() >= MiniGameFormat.Kind.values().length) return null;
        MiniGameFormat.Kind kind = MiniGameFormat.Kind.values()[shown.kind()];
        if (kind != MiniGameFormat.Kind.TEAMS || shown.sizes().size() < 2) return Text.translatable("format.steveparty." + kind.key());
        MutableText text = Text.literal(String.valueOf(shown.sizes().getFirst()));
        for (int i = 1; i < shown.sizes().size(); i++) text = Text.translatable("format.steveparty.vs", text, String.valueOf(shown.sizes().get(i)));
        return text;
    }

    /** His team (« Team A »), « Spectator » for the audience; null without teams. */
    static @Nullable Text teamText(RoundCard shown) {
        if (shown.team() == RoundCard.SPECTATOR) return Text.translatable("hud.steveparty.minigame.spectator");
        if (shown.team() < 0 || shown.team() > 3) return null;
        return MiniGamePipeRole.ofTeam(shown.team()).text();
    }
}
