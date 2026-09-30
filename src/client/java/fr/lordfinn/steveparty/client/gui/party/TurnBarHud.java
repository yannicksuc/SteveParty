package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.client.gui.ToolHud.Plate;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * The turn bar, at the top of the screen by default: one plate with
 * <ul>
 * <li>a first row: the round (« Tour 3/10 »), then every player in the turn order as a face framed with its token's
 * colour; the player whose turn it is gets a gold plate with its name, and a small gold marker bobbing under it;
 * the players who already played this round are greyed;</li>
 * <li>a second row: what is happening now (« À toi de lancer le dé ! », « Déplacement » and the steps left...), with
 * the Rejoue tag during a replay turn and the number (steps left, roll, seconds) in a gold box.</li>
 * </ul>
 * Animated: the gold plate slides from a player to the next and pops, the action line cross-fades when it changes, the
 * number pops when it changes. The layout is worked out when the model or the room changes, never per frame.
 */
final class TurnBarHud {
    private static final int PAD = 4;
    private static final int ROW_A = 18;
    private static final int ROW_B = 13;
    private static final int ROW_GAP = 2;
    static final int HEIGHT = PAD + ROW_A + ROW_GAP + ROW_B + PAD;
    private static final int CHIP = 18;
    private static final int CHIP_GAP = 2;
    private static final int FACE = 16;
    private static final int MIN_WIDTH = 120;
    private static final int MAX_WIDTH = 360;
    private static final int NAME_MAX = 72;
    private static final float TURN_POP_TICKS = 9;
    private static final float CROSSFADE_TICKS = 6;
    private static final float BADGE_POP_TICKS = 7;

    // ---------------------------------------------------------------- layout (on change only)
    private PartyHudModel model;
    private int layoutRevision = -1;
    private int roomWidth = -1;
    private Text roundText = Text.empty();
    private int roundWidth;
    private int[] chipX = new int[0];
    private int[] chipW = new int[0];
    private OrderedText currentName;
    private int width = MIN_WIDTH;
    private final ActionLine line = new ActionLine();
    private final ActionLine previousLine = new ActionLine();

    // ---------------------------------------------------------------- animation state
    private float[] shownX = new float[0];
    private float[] shownW = new float[0];
    private float shownWidth = -1;
    private int lastCurrent = -2;
    private double turnChangedAt = -1000;
    private int lastActionKey;
    private double actionChangedAt = -1000;
    private String lastBadge;
    private double badgeChangedAt = -1000;
    private double lastFrame;

    /** One action line, laid out. */
    private static final class ActionLine {
        Identifier icon;
        OrderedText text;
        int textWidth;
        String badge;
        int badgeWidth;
        boolean replay;
        OrderedText replayText;
        int replayWidth;
        boolean warn;
        int width;

        void copyFrom(ActionLine other) {
            icon = other.icon;
            text = other.text;
            textWidth = other.textWidth;
            badge = other.badge;
            badgeWidth = other.badgeWidth;
            replay = other.replay;
            replayText = other.replayText;
            replayWidth = other.replayWidth;
            warn = other.warn;
            width = other.width;
        }
    }

    /** Width of the whole bar, unscaled (valid after {@link #update}). */
    int width() {
        return width;
    }

    int height() {
        return HEIGHT;
    }

    /**
     * Takes a new model or new room (the unscaled width the bar may use): lays out again only if one of them
     * changed, and starts the animations of what changed.
     */
    void update(PartyHudModel model, int room, double now) {
        if (model == this.model && room == roomWidth) return;
        boolean newModel = model != this.model;
        this.model = model;
        this.roomWidth = room;
        layout(room);

        if (newModel) {
            if (model.current != lastCurrent) {
                if (lastCurrent != -2) turnChangedAt = now;
                lastCurrent = model.current;
            }
            if (model.actionKey != lastActionKey) {
                if (layoutRevision >= 0) {
                    actionChangedAt = now;
                    badgeChangedAt = -1000;
                }
                lastActionKey = model.actionKey;
            } else if (model.badge != null && !model.badge.equals(lastBadge)) {
                badgeChangedAt = now;
            }
            lastBadge = model.badge;
        }
        layoutRevision++;
    }

    private void layout(int room) {
        TextRenderer font = HudDraw.font();
        int maxContent = MathHelper.clamp(room, MIN_WIDTH, MAX_WIDTH) - 2 * PAD;

        // Row A: round, then the chips
        if (model.round > 0)
            roundText = model.rounds > 0 ? Text.translatable("hud.steveparty.party.round", model.round, model.rounds)
                    : Text.translatable("hud.steveparty.party.round.only", model.round);
        else roundText = Text.translatable("hud.steveparty.party.round.start");
        roundWidth = font.getWidth(roundText) + 10;
        int players = model.players.size();
        int others = roundWidth + 4 + Math.max(0, players - 1) * (CHIP + CHIP_GAP);
        int nameRoom = model.current >= 0 ? MathHelper.clamp(maxContent - others - (FACE + 11), 16, NAME_MAX) : 0;
        if (model.current >= 0) {
            Text name = Text.literal(model.players.get(model.current).name);
            currentName = HudDraw.fit(name, nameRoom);
            nameRoom = Math.min(nameRoom, font.getWidth(currentName));
        }
        if (chipX.length != players) {
            chipX = new int[players];
            chipW = new int[players];
        }
        int x = roundWidth + 4;
        for (int i = 0; i < players; i++) {
            chipX[i] = x;
            chipW[i] = i == model.current ? FACE + 9 + nameRoom : CHIP;
            x += chipW[i] + CHIP_GAP;
        }
        int rowA = x - CHIP_GAP;

        // Row B: [Rejoue] icon text [badge]
        layoutLine(line, maxContent);
        width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, Math.max(rowA, line.width) + 2 * PAD));
    }

    private void layoutLine(ActionLine line, int maxContent) {
        TextRenderer font = HudDraw.font();
        line.icon = model.actionIcon;
        line.replay = model.replay && model.stepType == fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType.TOKEN_TURN;
        line.replayText = Text.translatable("hud.steveparty.party.replay").asOrderedText();
        line.replayWidth = line.replay ? font.getWidth(line.replayText) + HudDraw.ICON + 9 : 0;
        line.badge = model.badge;
        line.badgeWidth = model.badge == null ? 0 : Math.max(ROW_B, font.getWidth(model.badge) + 7);
        line.warn = model.warn;
        int fixed = (line.replay ? line.replayWidth + 4 : 0) + HudDraw.ICON + 3 + (line.badge != null ? line.badgeWidth + 4 : 0);
        line.text = HudDraw.fit(model.actionText, Math.max(20, maxContent - fixed));
        line.textWidth = font.getWidth(line.text);
        line.width = fixed + line.textWidth;
    }

    /** Draws the bar with its top-left corner at (0, 0) of the current matrices. */
    void draw(DrawContext context, float alpha, double now) {
        if (model == null || alpha <= 0.02f) return;
        float delta = (float) MathHelper.clamp(now - lastFrame, 0, 5);
        lastFrame = now;
        animate(delta);

        int w = Math.round(shownWidth);
        HudDraw.plate(context, Plate.TEAL, 0, 0, w, HEIGHT, alpha);

        // Round
        HudDraw.plate(context, Plate.PURPLE, PAD, PAD, roundWidth, ROW_A, alpha);
        HudDraw.text(context, roundText, PAD + 5, PAD + 5, HudDraw.TEXT, alpha);

        // Players: the current one's gold plate slides and pops, the others are faces
        int players = Math.min(model.players.size(), shownX.length);
        for (int i = 0; i < players; i++) {
            if (i == model.current) continue;
            PartyHudModel.Player player = model.players.get(i);
            int x = PAD + Math.round(shownX[i]);
            HudDraw.face(context, player.owner, player.name, player.color, x + 1, PAD + 1, FACE, alpha, player.played);
            if (model.stepType == fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType.START_ROLLS && player.startRoll > 0)
                rollTag(context, player.startRoll, x + CHIP - 6, PAD + ROW_A - 6, alpha);
        }
        if (model.current >= 0 && model.current < players) drawCurrent(context, alpha, now);

        // Action line (cross-fading when it changed)
        float t = (float) ((now - actionChangedAt) / CROSSFADE_TICKS);
        int lineY = PAD + ROW_A + ROW_GAP;
        if (t < 1) {
            float out = 1 - HudDraw.easeOutCubic(t);
            drawLine(context, previousLine, w, lineY - Math.round((1 - out) * 4), alpha * out, now);
            drawLine(context, line, w, lineY + Math.round(out * 4), alpha * HudDraw.easeOutCubic(t), now);
        } else {
            previousLine.copyFrom(line);
            drawLine(context, line, w, lineY, alpha, now);
        }
    }

    private void drawCurrent(DrawContext context, float alpha, double now) {
        PartyHudModel.Player player = model.players.get(model.current);
        float x = PAD + shownX[model.current];
        float w = shownW[model.current];
        MatrixStack matrices = context.getMatrices();
        float pop = (float) ((now - turnChangedAt) / TURN_POP_TICKS);
        float scale = pop < 1 ? 1 + 0.3f * (1 - HudDraw.easeOutBack(pop)) : 1;
        matrices.push();
        matrices.translate(x + w / 2, PAD + ROW_A / 2f, 0);
        matrices.scale(scale, scale, 1);
        matrices.translate(-w / 2, -ROW_A / 2f, 0);
        int width = Math.round(w);
        HudDraw.plate(context, Plate.GOLD, 0, 0, width, ROW_A, alpha);
        HudDraw.face(context, player.owner, player.name, player.color, 2, 1, FACE, alpha, false);
        // The name shows once the plate has room for it
        float room = (w - (FACE + 9)) / Math.max(1, chipW[model.current] - (FACE + 9));
        if (currentName != null && room > 0.6f)
            HudDraw.text(context, currentName, FACE + 4, 5, player.mine ? HudDraw.TEXT_MINE : HudDraw.TEXT, alpha * HudDraw.clamp01((room - 0.6f) / 0.4f));
        matrices.pop();
        // The marker bobs under the plate: whole pixels, a slow pulse
        int bob = Math.round((float) Math.sin(now * Math.PI / 12) * 1.2f);
        context.drawGuiTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, HudDraw.ICON_MARKER,
                Math.round(x + 2 + FACE / 2f - 3.5f), PAD + ROW_A - 1 + bob, 7, 5, HudDraw.white(alpha));
    }

    private void rollTag(DrawContext context, int roll, int x, int y, float alpha) {
        String text = Integer.toString(roll);
        int width = HudDraw.font().getWidth(text) + 4;
        HudDraw.plate(context, Plate.GOLD, x, y, Math.max(8, width), 10, alpha);
        HudDraw.text(context, text, x + Math.max(8, width) / 2 - HudDraw.font().getWidth(text) / 2, y + 1, HudDraw.TEXT, alpha);
    }

    private void drawLine(DrawContext context, ActionLine line, int width, int y, float alpha, double now) {
        if (line.text == null || alpha <= 0.02f) return;
        int x = (width - line.width) / 2;
        if (line.replay) {
            HudDraw.plate(context, Plate.GREEN, x, y, line.replayWidth, ROW_B, alpha);
            HudDraw.icon(context, HudDraw.ICON_REPLAY, x + 3, y + 2, alpha);
            HudDraw.text(context, line.replayText, x + 5 + HudDraw.ICON, y + 3, HudDraw.TEXT, alpha);
            x += line.replayWidth + 4;
        }
        HudDraw.icon(context, line.icon, x, y + 2, alpha);
        x += HudDraw.ICON + 3;
        HudDraw.text(context, line.text, x, y + 3, line.warn ? HudDraw.TEXT_WARN : HudDraw.TEXT, alpha);
        x += line.textWidth + 4;
        if (line.badge != null) {
            MatrixStack matrices = context.getMatrices();
            float pop = line == this.line ? (float) ((now - badgeChangedAt) / BADGE_POP_TICKS) : 1;
            float scale = pop < 1 ? 1 + 0.45f * (1 - HudDraw.easeOutBack(pop)) : 1;
            matrices.push();
            matrices.translate(x + line.badgeWidth / 2f, y + ROW_B / 2f, 0);
            matrices.scale(scale, scale, 1);
            matrices.translate(-line.badgeWidth / 2f, -ROW_B / 2f, 0);
            HudDraw.plate(context, line.warn ? Plate.RED : Plate.GOLD, 0, 0, line.badgeWidth, ROW_B, alpha);
            HudDraw.text(context, line.badge, (line.badgeWidth - HudDraw.font().getWidth(line.badge)) / 2 + 1, 3, HudDraw.TEXT, alpha);
            matrices.pop();
        }
    }

    /** Eases the shown chips and width towards the layout (the gold plate slides, the bar grows or shrinks). */
    private void animate(float delta) {
        int players = chipX.length;
        if (shownX.length != players) {
            shownX = new float[players];
            shownW = new float[players];
            for (int i = 0; i < players; i++) {
                shownX[i] = chipX[i];
                shownW[i] = chipW[i];
            }
        }
        for (int i = 0; i < players; i++) {
            shownX[i] = HudDraw.approach(shownX[i], chipX[i], 0.45f, delta);
            shownW[i] = HudDraw.approach(shownW[i], chipW[i], 0.45f, delta);
        }
        shownWidth = shownWidth < 0 ? width : HudDraw.approach(shownWidth, width, 0.45f, delta);
    }
}
