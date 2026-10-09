package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType;
import fr.lordfinn.steveparty.client.utils.SkinUtils;
import fr.lordfinn.steveparty.hud.HudShapes.Form;
import fr.lordfinn.steveparty.hud.TurnStripLayout;
import fr.lordfinn.steveparty.hud.TurnStripLayout.El;
import fr.lordfinn.steveparty.hud.TurnStripLayout.Kind;
import fr.lordfinn.steveparty.hud.TurnStripLayout.Type;
import fr.lordfinn.steveparty.utils.Argb;
import fr.lordfinn.steveparty.utils.Easing;
import fr.lordfinn.steveparty.client.gui.paint.Ramp;
import fr.lordfinn.steveparty.client.gui.paint.PaintedTextures.Tex;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static fr.lordfinn.steveparty.hud.HudShapes.PAD;

/**
 * The turn bar, at the top of the screen by default (the approved mock-up « 7E v4 », see {@link TurnStripLayout}): no
 * panel, one strip on one axis, the step being played big with its halo and the marker over it, the next one medium,
 * the following ones small, the next mini-game always visible, « +N » for the rounds left; under the strip, the yellow
 * bubbles of the player of this client (« toi », « à toi ! » pulsing on their turn, « toi dans 5 »). What is
 * happening now is the notice's ({@link NoticeHud}), at the action bar's place.
 * <p>
 * When the step changes, one choreography of {@value #CHANGE_TICKS} ticks: the step that was played shrinks and slides
 * left, the strip slides one place, the next step grows into the big chip (overshooting a little) and lands, new steps
 * come in on the right by growing, the bubbles and « +N » land with it. Heads never fade (only plates and texts do):
 * they slide and scale. Another layout (a new width, new data) glides there the same way. The layout is worked out
 * when the model or the room changes, never per frame.
 */
final class TurnBarHud {
    /**
     * Its height before any layout (the strip, the marker over it, a row of bubbles under it): the attached notice's
     * place while the bar is not shown.
     */
    static final int HEIGHT = 64;
    private static final float CHANGE_TICKS = 8;
    private static final float GLIDE_TICKS = 6;

    // ---------------------------------------------------------------- layout (on change only)
    private PartyHudModel model;
    private int roomWidth = -1;
    private TurnStripLayout.Result after = new TurnStripLayout.Result(List.of(), 0, -1, -1, false, 0);
    private TurnStripLayout.Result before = after;
    private double changedAt = -1000;
    private boolean stepChange;
    private String leaving, coming;
    private int lastStepIndex = Integer.MIN_VALUE;
    /** What the bar draws ({@link TurnStripLayout.Result#drawn}): its frame hugs it; eased when it changes. */
    private int[] drawn = {0, 0, 120, HEIGHT - 1};
    private float shownLeft = -1, shownTop, shownRight, shownBottom;
    private double lastFrame;
    private final List<String> keys = new ArrayList<>();

    int width() {
        return shownLeft < 0 ? drawn[2] - drawn[0] + 1 : Math.round(shownRight - shownLeft) + 1;
    }

    int height() {
        return shownLeft < 0 ? drawn[3] - drawn[1] + 1 : Math.round(shownBottom - shownTop) + 1;
    }

    /** Takes a new model or new room (the unscaled width the bar may use): lays out again only then. */
    void update(PartyHudModel model, int room, double now) {
        if (model == this.model && room == roomWidth) return;
        boolean newModel = model != this.model;
        this.model = model;
        this.roomWidth = room;
        Set<Integer> mine = new HashSet<>();
        for (int i = 0; i < model.players.size(); i++) if (model.players.get(i).mine) mine.add(i);
        TurnStripLayout.Result result = TurnStripLayout.layout(new TurnStripLayout.Input(steps(model), model.rounds, mine,
                names(model), Math.max(80, room - 12)), ClientHudTexts.INSTANCE);
        boolean first = after.elements().isEmpty();
        // A layout arriving during a change: the change is over (its end is where this one starts)
        before = first ? result : after;
        after = result;
        stepChange = newModel && lastStepIndex != Integer.MIN_VALUE && model.stepIndex != lastStepIndex && !first;
        if (newModel) lastStepIndex = model.stepIndex;
        leaving = coming = null;
        if (stepChange && !before.elements().isEmpty() && !after.elements().isEmpty()) {
            leaving = before.elements().getFirst().key;
            coming = after.elements().getFirst().key;
            if (leaving.equals(coming)) leaving = null;
        }
        changedAt = first ? -1000 : now;
        drawn = result.drawn();
        if (first || shownLeft < 0) {
            shownLeft = drawn[0];
            shownTop = drawn[1];
            shownRight = drawn[2];
            shownBottom = drawn[3];
        }
        keys.clear();
        LinkedHashSet<String> all = new LinkedHashSet<>();
        for (El el : before.elements()) all.add(el.key);
        for (El el : after.elements()) all.add(el.key);
        keys.addAll(all);
    }

    /** The model's steps as the strip shows them: a round of a program not started yet is one capsule. */
    private static List<TurnStripLayout.Step> steps(PartyHudModel model) {
        List<TurnStripLayout.Step> steps = new ArrayList<>();
        boolean beforeRounds = model.stepType == PartyStepType.START_ROLLS || model.stepType == PartyStepType.BASIC_GAME_GENERATOR;
        int lastCapsule = -1;
        for (int i = 0; i < model.strip.size(); i++) {
            PartyHudModel.Step s = model.strip.get(i);
            Kind kind = switch (s.kind) {
                case TURN -> Kind.TURN;
                case TURNS -> Kind.CAPSULE;
                case MINI_GAME -> Kind.MINI_GAME;
                case EVENT -> Kind.EVENT;
                case START_ROLLS -> Kind.START;
                case END -> Kind.END;
                case PREPARING, OTHER -> Kind.PREPARING;
                case ROLL -> null;
            };
            if (kind == null) continue;
            if (i > 0 && beforeRounds && kind != Kind.CAPSULE) continue;
            if (kind == Kind.CAPSULE) {
                if (s.round == lastCapsule) continue;
                lastCapsule = s.round;
            }
            steps.add(new TurnStripLayout.Step(kind, kind == Kind.TURN ? s.player : -1, s.round, s.key));
        }
        return steps;
    }

    private static List<String> names(PartyHudModel model) {
        List<String> names = new ArrayList<>(model.players.size());
        for (PartyHudModel.Player player : model.players) names.add(player.name);
        return names;
    }

    // ---------------------------------------------------------------- drawing

    /** Draws the bar with its top-left corner at (0, 0) of the current matrices. */
    void draw(DrawContext context, float alpha, double now) {
        if (model == null || alpha <= 0.02f) return;
        float t = (float) MathHelper.clamp((now - changedAt) / (stepChange ? CHANGE_TICKS : GLIDE_TICKS), 0, 1);
        // The frame follows what is drawn (the marker coming and going, the bubbles...), gliding
        float delta = (float) MathHelper.clamp(now - lastFrame, 0, 5);
        lastFrame = now;
        shownLeft = HudDraw.approach(shownLeft, drawn[0], 0.45f, delta);
        shownTop = HudDraw.approach(shownTop, drawn[1], 0.45f, delta);
        shownRight = HudDraw.approach(shownRight, drawn[2], 0.45f, delta);
        shownBottom = HudDraw.approach(shownBottom, drawn[3], 0.45f, delta);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(-Math.round(shownLeft), -Math.round(shownTop), 0);
        boolean landed = t >= 0.6f;
        float slide = Easing.smoothstep(MathHelper.clamp((t - 0.1f) / 0.5f, 0, 1));
        // The big chip last: over the others while it grows
        String top = after.elements().isEmpty() ? null : after.elements().getFirst().key;
        for (int pass = 0; pass < 2; pass++) {
            for (String key : keys) {
                if ((pass == 1) != key.equals(top)) continue;
                El a = before.get(key), b = after.get(key);
                if (t >= 1) {
                    if (b != null) drawEl(context, b, b.x, b.y, 1, alpha, now);
                    continue;
                }
                if (key.equals(leaving) && a != null) {
                    // The step that was played: shrinks, slides 12 px left
                    float s = Math.max(0, 1 - t / 0.4f);
                    drawEl(context, a, Math.round(a.x - 12 * Math.min(1, t / 0.4f)), a.y, s, alpha, now);
                } else if (key.equals(coming) && a != null && b != null) {
                    // The next step: slides into the first place and grows into the big chip
                    float grow = t > 0.25f ? Easing.easeOutBack(MathHelper.clamp((t - 0.25f) / 0.45f, 0, 1)) : 0;
                    float scale = MathHelper.lerp(grow, a.h / (float) b.h, 1);
                    drawEl(context, b, Math.round(MathHelper.lerp(slide, a.x, b.x)), b.y, scale, alpha, now);
                } else if (a != null && b != null) {
                    // Still there: slides to its new place, takes its new look when the new step lands
                    El shown = landed ? b : a;
                    drawEl(context, shown, Math.round(MathHelper.lerp(slide, a.x, b.x)), shown.y, 1, alpha, now);
                } else if (a != null) {
                    // Gone: a chip shrinks, a plate fades
                    if (landed) continue;
                    float k = Math.max(0, 1 - t / 0.55f);
                    if (isChip(a)) drawEl(context, a, a.x, a.y, k, alpha, now);
                    else drawEl(context, a, a.x, a.y, 1, alpha * k, now);
                } else if (b != null) {
                    // New: a chip grows, a plate fades in, the bubbles and the marker land with the step
                    if (isChip(b)) {
                        float s = MathHelper.clamp((t - 0.35f) / 0.5f, 0, 1);
                        if (s > 0) drawEl(context, b, b.x, b.y, s, alpha, now);
                    } else if (b.type == Type.BUBBLE || b.type == Type.MARKER) {
                        if (landed) drawEl(context, b, b.x, b.y, 1, alpha, now);
                    } else {
                        drawEl(context, b, b.x, b.y, 1, alpha * MathHelper.clamp((t - 0.35f) / 0.4f, 0, 1), now);
                    }
                }
            }
        }
        matrices.pop();
    }

    /** Heads and pictures that may not fade: they scale. */
    private static boolean isChip(El el) {
        return el.type == Type.PLAYER || el.type == Type.CAPSULE || el.type == Type.STEP_DISC || el.type == Type.BIG_STEP;
    }

    /** One element, its picture's top-left at (x, y), scaled round its centre. */
    private void drawEl(DrawContext context, El el, int x, int y, float scale, float alpha, double now) {
        if (scale <= 0.05f || alpha <= 0.02f) return;
        MatrixStack matrices = context.getMatrices();
        boolean scaled = Math.abs(scale - 1) > 0.001f;
        if (el.key.equals("toi_now")) {
            // « à toi ! » pulses: 1 -> 1.08 -> 1, about a second
            scale *= 1 + 0.04f * (1 - (float) Math.cos(now * Math.PI * 2 / 20));
            scaled = true;
        }
        if (scaled) {
            float cx = x + el.pictureWidth() / 2f, cy = y + el.pictureHeight() / 2f;
            matrices.push();
            matrices.translate(cx, cy, 0);
            matrices.scale(scale, scale, 1);
            matrices.translate(-cx, -cy, 0);
        }
        switch (el.type) {
            case PLAYER -> player(context, el, x, y, alpha);
            case BIG_STEP -> bigStep(context, el, x, y, alpha);
            case STEP_DISC -> stepDisc(context, el.step.kind(), TurnStripLayout.MEDAL, x, y, alpha);
            case CAPSULE -> capsule(context, el, x, y, alpha);
            case ROUND_DISC -> roundDisc(context, el.number, el.w, el.h, x, y, alpha);
            case MORE -> {
                HudPaint.draw(context, HudPaint.shape(Form.PILL, el.w, el.h, HudPaint.NEUTRAL, HudPaint.SHADOW | HudPaint.OUTLINE | HudPaint.BAND), x, y, alpha);
                darkText(context, el.label, x + PAD + (el.w - el.labelWidth - 1) / 2, y + PAD + 3, HudPaint.NEUTRAL.outline(), 0xFFFFFFFF, alpha);
            }
            case PINNED -> {
                HudPaint.draw(context, HudPaint.shape(Form.PILL, el.w, el.h, HudPaint.MINI_GAME, HudPaint.SHADOW | HudPaint.OUTLINE | HudPaint.BAND), x, y, alpha);
                HudPaint.draw(context, HudPaint.shape(Form.PILL, 10, 10, HudPaint.white(HudPaint.MINI_GAME.shadow()), 0), x + 2, y + 2, alpha);
                HudPaint.draw(context, HudPaint.gamepad(), x + 2 + PAD, y + 3 + PAD, alpha);
                HudDraw.shadowed(context, el.label, x + PAD + 15, y + PAD + 3, HudPaint.TEXT, alpha);
            }
            case BUBBLE -> {
                HudPaint.draw(context, HudPaint.bubble(el.w, false), x, y, alpha);
                darkText(context, el.label, x + PAD + 4, y + TurnStripLayout.POINTER + PAD + 2, HudPaint.GOLD.outline(), HudPaint.GOLD.hi(), alpha);
            }
            case MARKER -> {
                int bob = Math.round((float) Math.sin(now * Math.PI / 12) * 1.2f);
                HudDraw.faded(alpha, () -> context.drawGuiTexture(HudDraw.ICON_MARKER, x + PAD, y + PAD + bob,
                        TurnStripLayout.MARKER_W, TurnStripLayout.MARKER_H));
            }
        }
        if (scaled) matrices.pop();
    }

    private void player(DrawContext context, El el, int x, int y, float alpha) {
        PartyHudModel.Player player = el.step.player() >= 0 && el.step.player() < model.players.size() ? model.players.get(el.step.player()) : null;
        Ramp ramp = player != null ? player.ramp : HudPaint.NEUTRAL;
        HudPaint.draw(context, HudPaint.shape(Form.CHEVRON, el.w, el.h, ramp, HudPaint.SHADOW | HudPaint.OUTLINE | HudPaint.BAND), x, y, alpha);
        if (el.halo) HudPaint.draw(context, HudPaint.halo(Form.CHEVRON, el.w, el.h), x, y, alpha);
        int d = el.h / 4, size = TurnStripLayout.FRAME[el.level];
        int fx = x + PAD + d + 3, fy = y + PAD + (el.h - size) / 2;
        HudPaint.draw(context, HudPaint.shape(Form.CUT1, size, size, HudPaint.white(ramp.outline()), HudPaint.OUTLINE), fx - PAD, fy - PAD, alpha);
        head(context, player, fx + (size - 8) / 2, fy + (size - 8) / 2, alpha);
        if (!el.label.isEmpty()) HudDraw.shadowed(context, el.label, fx + size + 4, y + PAD + (el.h - 8) / 2, HudPaint.TEXT, alpha);
    }

    /** A player's face, 8 x 8 (its skin's face and hat); the initial of its name without player. */
    static void head(DrawContext context, PartyHudModel.Player player, int x, int y, float alpha) {
        UUID owner = player == null ? null : player.owner;
        if (owner != null) {
            Identifier skin = SkinUtils.getPlayerSkin(owner);
            Identifier fallback = DefaultSkinHelper.getSkinTextures(owner).texture();
            HudDraw.faded(alpha, () -> {
                // The default face of the UUID under it: a skin that is not (or no longer) there never leaves a blank
                if (!fallback.equals(skin)) context.drawTexture(fallback, x, y, 8, 8, 8, 8, 8, 8, 64, 64);
                context.drawTexture(skin, x, y, 8, 8, 8, 8, 8, 8, 64, 64);
                context.drawTexture(skin, x, y, 8, 8, 40, 8, 8, 8, 64, 64);
            });
            return;
        }
        context.fill(x, y, x + 8, y + 8, Argb.fade(0xFF3F3F3F, alpha));
        String name = player == null || player.name.isEmpty() ? "?" : player.name;
        String initial = name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
        TextRenderer font = HudDraw.font();
        context.drawText(font, initial, x + (8 - font.getWidth(initial)) / 2 + 1, y, Argb.fade(0xFFFFFFFF, alpha), false);
    }

    private static Ramp kindRamp(Kind kind) {
        return switch (kind) {
            case MINI_GAME -> HudPaint.MINI_GAME;
            case EVENT -> HudPaint.EVENT;
            default -> HudPaint.NEUTRAL;
        };
    }

    private static Tex icon(Kind kind) {
        return switch (kind) {
            case MINI_GAME -> HudPaint.gamepad();
            case EVENT -> HudPaint.bell();
            case START -> HudPaint.die();
            case END -> HudPaint.flag();
            default -> HudPaint.gear();
        };
    }

    /** A step's round medallion: a ring in its colour, white inside, its icon. */
    private static void stepDisc(DrawContext context, Kind kind, int d, int x, int y, float alpha) {
        Ramp ramp = kindRamp(kind);
        HudPaint.draw(context, HudPaint.shape(Form.PILL, d, d, ramp, HudPaint.SHADOW | HudPaint.OUTLINE | HudPaint.BAND), x, y, alpha);
        HudPaint.draw(context, HudPaint.shape(Form.PILL, d - 4, d - 4, HudPaint.white(ramp.shadow()), 0), x + 2, y + 2, alpha);
        Tex icon = icon(kind);
        HudPaint.draw(context, icon, x + PAD + (d - icon.width()) / 2, y + PAD + (d - icon.height()) / 2, alpha);
    }

    private static void bigStep(DrawContext context, El el, int x, int y, float alpha) {
        Ramp ramp = kindRamp(el.step.kind());
        HudPaint.draw(context, HudPaint.shape(Form.PILL, el.w, el.h, ramp, HudPaint.SHADOW | HudPaint.OUTLINE | HudPaint.BAND), x, y, alpha);
        HudPaint.draw(context, HudPaint.halo(Form.PILL, el.w, el.h), x, y, alpha);
        stepDisc(context, el.step.kind(), TurnStripLayout.BIG_MEDAL, x + 2, y + 3, alpha);
        int tx = x + PAD + 3 + TurnStripLayout.BIG_MEDAL + 4, ty = y + PAD + 8;
        if (ramp == HudPaint.NEUTRAL) darkText(context, el.label, tx, ty, HudPaint.TEXT_DARK, 0xFFFFFFFF, alpha);
        else HudDraw.shadowed(context, el.label, tx, ty, HudPaint.TEXT, alpha);
    }

    /** A round whose turn order is not known: a neutral chevron, its number, blank pawns. */
    private static void capsule(DrawContext context, El el, int x, int y, float alpha) {
        HudPaint.draw(context, HudPaint.shape(Form.CHEVRON, el.w, el.h, HudPaint.NEUTRAL, HudPaint.SHADOW | HudPaint.OUTLINE | HudPaint.BAND), x, y, alpha);
        int d = el.h / 4;
        String n = Integer.toString(el.number);
        int w = Math.max(12, 4 * n.length() + 1 + 4);
        w += w % 2;
        roundDisc(context, el.number, w, 12, x + d + 3, y + 3, alpha);
        for (int i = 0; i < TurnStripLayout.CAPSULE_PAWNS; i++)
            HudPaint.draw(context, HudPaint.shape(Form.CUT1, 6, 6, HudPaint.PAWN, HudPaint.OUTLINE), x + d + 18 + i * 7, y + 6, alpha);
    }

    /** The numbered round medallion: neutral, small digits with a white shadow. */
    private static void roundDisc(DrawContext context, int round, int w, int d, int x, int y, float alpha) {
        HudPaint.draw(context, HudPaint.shape(Form.PILL, w, d, HudPaint.NEUTRAL, HudPaint.OUTLINE), x, y, alpha);
        String s = Integer.toString(round);
        int tw = HudPaint.smallWidth(s);
        int x0 = x + PAD + (w - tw - 1) / 2, y0 = y + PAD + (d - 6) / 2;
        HudPaint.small(context, s, x0 + 1, y0 + 1, 0xFFFFFFFF, alpha);
        HudPaint.small(context, s, x0, y0, HudPaint.NEUTRAL.outline(), alpha);
    }

    /** Dark text with a light shadow (the mock-ups' {@code dark_shadowed}). */
    static void darkText(DrawContext context, String text, int x, int y, int color, int shade, float alpha) {
        HudDraw.text(context, text, x + 1, y + 1, shade, alpha);
        HudDraw.text(context, text, x, y, color, alpha);
    }
}
