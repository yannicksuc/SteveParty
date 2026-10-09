package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType;
import fr.lordfinn.steveparty.hud.HudShapes.Form;
import fr.lordfinn.steveparty.utils.Easing;
import fr.lordfinn.steveparty.client.gui.paint.Ramp;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static fr.lordfinn.steveparty.hud.HudShapes.GAP;
import static fr.lordfinn.steveparty.hud.HudShapes.PAD;

/**
 * The party's notice, at the action bar's place by default (just over the hotbar and the held item's name): what is
 * happening now, on a white plate (« Tom : lance le dé ! »), then its number in a gold pill (« 1/4 », « 4 pas »; red for
 * an absent player's countdown) and « Rejoue ! » in a green one during a replay turn. On my turn the plate turns gold
 * (« À toi : lance le dé ! »). A new notice cross-fades, a new number pops.
 */
final class NoticeHud {
    private static final int H = 15;
    private static final int MAX_TEXT = 260;
    private static final float CROSSFADE_TICKS = 6;
    private static final float BADGE_POP_TICKS = 7;
    private static final Ramp GREEN = Ramp.of(0x0e3a12, 0xc6f5ae, 0x6ccb52, 0x45a03a);
    private static final Ramp RED = Ramp.of(0x4a0808, 0xffb7ae, 0xe8413c, 0xb02e26);

    /** A notice, laid out. */
    private static final class Line {
        Identifier icon;
        OrderedText text;
        int plateWidth;
        boolean gold;
        String badge;
        int badgeWidth;
        boolean warn;
        Text replay;
        int replayWidth;
        int width;
    }

    private PartyHudModel model;
    private int roomWidth = -1;
    private final Line line = new Line();
    private final Line previous = new Line();
    private int lastActionKey;
    private double changedAt = -1000, badgeAt = -1000;
    private String lastBadge;

    /** The frame hugs the plates: their outline (a pixel round the shapes) and drop shadow (two rows under them). */
    private static final int EDGE = PAD - 1;

    int width() {
        return line.width - 2 * EDGE;
    }

    int height() {
        return 1 + H + 1 + 2;
    }

    void update(PartyHudModel model, int room, double now) {
        if (model == this.model && room == roomWidth) return;
        boolean newModel = model != this.model;
        if (newModel && this.model != null && model.actionKey != lastActionKey) {
            copy(line, previous);
            changedAt = now;
        } else if (newModel && model.badge != null && !model.badge.equals(lastBadge)) {
            badgeAt = now;
        }
        this.model = model;
        this.roomWidth = room;
        lastActionKey = model.actionKey;
        lastBadge = model.badge;
        layout(line, Math.min(MAX_TEXT, room - 60));
    }

    private void layout(Line line, int room) {
        TextRenderer font = HudDraw.font();
        line.icon = model.actionIcon;
        line.gold = model.yourTurn;
        line.badge = model.badge;
        line.warn = model.warn;
        line.badgeWidth = line.badge == null ? 0 : font.getWidth(line.badge) - 1 + 10;
        line.replay = model.replay && model.stepType == PartyStepType.TOKEN_TURN ? Text.translatable("hud.steveparty.party.replay") : null;
        line.replayWidth = line.replay == null ? 0 : font.getWidth(line.replay) - 1 + 10;
        int fixed = 4 + 9 + 4 + 5 + (line.badge != null ? line.badgeWidth + GAP + 1 : 0) + (line.replay != null ? line.replayWidth + GAP + 1 : 0);
        line.text = HudDraw.fit(model.actionText, Math.max(30, room - fixed));
        line.plateWidth = 4 + 9 + 4 + Math.max(0, font.getWidth(line.text) - 1) + 5;
        line.width = 2 * PAD + line.plateWidth + (line.badge != null ? GAP + 1 + line.badgeWidth : 0) + (line.replay != null ? GAP + 1 + line.replayWidth : 0);
    }

    private static void copy(Line from, Line to) {
        to.icon = from.icon;
        to.text = from.text;
        to.plateWidth = from.plateWidth;
        to.gold = from.gold;
        to.badge = from.badge;
        to.badgeWidth = from.badgeWidth;
        to.warn = from.warn;
        to.replay = from.replay;
        to.replayWidth = from.replayWidth;
        to.width = from.width;
    }

    /** Draws the notice with its top-left corner at (0, 0) of the current matrices, centred in its width. */
    void draw(DrawContext context, float alpha, double now) {
        if (model == null || alpha <= 0.02f) return;
        context.getMatrices().push();
        context.getMatrices().translate(-EDGE, -EDGE, 0);
        drawLines(context, alpha, now);
        context.getMatrices().pop();
    }

    private void drawLines(DrawContext context, float alpha, double now) {
        float t = (float) ((now - changedAt) / CROSSFADE_TICKS);
        if (t < 1 && previous.text != null) {
            float in = Easing.easeOutCubic(t);
            drawLine(context, previous, (line.width - previous.width) / 2, -Math.round(in * 4), alpha * (1 - in), now);
            drawLine(context, line, 0, Math.round((1 - in) * 4), alpha * in, now);
        } else {
            drawLine(context, line, 0, 0, alpha, now);
        }
    }

    private void drawLine(DrawContext context, Line line, int x, int y, float alpha, double now) {
        if (line.text == null || alpha <= 0.02f) return;
        HudPaint.draw(context, HudPaint.shape(Form.CUT1, line.plateWidth, H, line.gold ? HudPaint.PLATE_GOLD : HudPaint.PLATE,
                HudPaint.SHADOW | HudPaint.OUTLINE), x, y, alpha);
        HudDraw.icon(context, line.icon, x + PAD + 4, y + PAD + 3, alpha);
        HudDraw.text(context, line.text, x + PAD + 17, y + PAD + 4, HudPaint.TEXT_DARK, alpha);
        int cx = x + line.plateWidth + GAP + 1;
        if (line.badge != null) {
            Ramp ramp = line.warn ? RED : HudPaint.GOLD;
            MatrixStack matrices = context.getMatrices();
            float pop = line == this.line ? (float) ((now - badgeAt) / BADGE_POP_TICKS) : 1;
            float scale = pop < 1 ? 1 + 0.4f * (1 - Easing.easeOutBack(pop)) : 1;
            matrices.push();
            float mx = cx + PAD + line.badgeWidth / 2f, my = y + PAD + H / 2f;
            matrices.translate(mx, my, 0);
            matrices.scale(scale, scale, 1);
            matrices.translate(-mx, -my, 0);
            HudPaint.draw(context, HudPaint.shape(Form.PILL, line.badgeWidth, H, ramp, HudPaint.SHADOW | HudPaint.OUTLINE | HudPaint.BAND), cx, y, alpha);
            HudDraw.text(context, line.badge, cx + PAD + 5, y + PAD + 4, line.warn ? 0xFFFFFFFF : ramp.outline(), alpha);
            matrices.pop();
            cx += line.badgeWidth + GAP + 1;
        }
        if (line.replay != null) {
            HudPaint.draw(context, HudPaint.shape(Form.PILL, line.replayWidth, H, GREEN, HudPaint.SHADOW | HudPaint.OUTLINE | HudPaint.BAND), cx, y, alpha);
            HudDraw.text(context, line.replay, cx + PAD + 5, y + PAD + 4, GREEN.outline(), alpha);
        }
    }
}
