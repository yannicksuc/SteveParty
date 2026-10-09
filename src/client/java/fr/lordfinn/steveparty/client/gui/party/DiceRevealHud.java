package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.client.gui.ToolHud;
import fr.lordfinn.steveparty.client.gui.paint.Ramp;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.hud.HudShapes.Form;
import fr.lordfinn.steveparty.payloads.custom.DiceRevealPayload;
import fr.lordfinn.steveparty.utils.Easing;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static fr.lordfinn.steveparty.hud.HudShapes.GAP;
import static fr.lordfinn.steveparty.hud.HudShapes.PAD;

/**
 * The reveal of a throw, at the action bar's place, in the party HUD's look: the roller on a white plate (gold: my own
 * throw), then a pill per die, « ? » while it turns, popping in with its face as it stops (« 3 + 5 + ? »), then « = »
 * and the total in a gold pill; a double / triple flashes in a green / gold pill and tints the matching faces. A single
 * die shows its result only. Sent by the server ({@link DiceRevealPayload}) to the players near the dice and to the
 * roller's party; it stays a few seconds after the total, then fades. The vanilla action bar goes above it.
 */
public final class DiceRevealHud {
    private static final int H = 15;
    private static final float POP_TICKS = 7;
    /** How long the whole reveal stays after its total, and when it gives up without one (ticks). */
    private static final double STAY_TICKS = 70, LOST_TICKS = 200, FADE_TICKS = 10;
    /** The bottom of the plates, above the held item's name (as the action bar). */
    private static final int BOTTOM_FROM_SCREEN_BOTTOM = 61;
    private static final Ramp GREEN = Ramp.of(0x0e3a12, 0xc6f5ae, 0x6ccb52, 0x45a03a);
    private static final Ramp RED = Ramp.of(0x4a0808, 0xffb7ae, 0xe8413c, 0xb02e26);
    private static final Ramp PURPLE = Ramp.of(0x2e0a4a, 0xe3c6ff, 0xa35cff, 0x7a38d0);

    private static int throwId = Integer.MIN_VALUE;
    private static @Nullable DiceRevealPayload shown;
    private static final List<Double> faceAt = new ArrayList<>();
    private static double updatedAt = -1000, totalAt = -1000, comboAt = -1000;
    private static int combo;
    /** Top of the plates drawn in the last frames (the action bar goes above it), and frames since. */
    private static int top;
    private static int framesSinceDrawn = Integer.MAX_VALUE;

    private DiceRevealHud() {
    }

    public static void initialize() {
        HudRenderCallback.EVENT.register(DiceRevealHud::render);
    }

    /** The server tells where a reveal is (the whole of it each time). */
    public static void onPayload(DiceRevealPayload payload) {
        double now = PartyHud.now();
        if (payload.throwId() != throwId) {
            throwId = payload.throwId();
            faceAt.clear();
            totalAt = comboAt = -1000;
            combo = 0;
        }
        while (faceAt.size() < payload.faces().size()) faceAt.add(now);
        if (payload.total().isPresent() && (shown == null || shown.total().isEmpty() || shown.throwId() != payload.throwId()))
            totalAt = now;
        if (payload.combo() > combo) {
            combo = payload.combo();
            comboAt = now;
        }
        shown = payload;
        updatedAt = now;
    }

    public static void clear() {
        shown = null;
        throwId = Integer.MIN_VALUE;
    }

    /**
     * How far up a vanilla HUD text whose bottom is {@code bottomFromScreenBottom} pixels above the screen's bottom
     * must go to clear the reveal (0 when none is shown). The previous frame's layout.
     */
    public static int liftFor(DrawContext context, int bottomFromScreenBottom) {
        if (framesSinceDrawn != Integer.MAX_VALUE) framesSinceDrawn++;
        if (framesSinceDrawn > 4) return 0;
        return Math.max(0, context.getScaledWindowHeight() - bottomFromScreenBottom - (top - 2));
    }

    /** A reveal is shown (the held item's name is not written under it). */
    public static boolean isShown() {
        return framesSinceDrawn <= 4;
    }

    // ------------------------------------------------------------------ drawing

    private static float alpha(double now) {
        if (shown == null) return 0;
        double end = shown.total().isPresent() ? totalAt + STAY_TICKS : updatedAt + LOST_TICKS;
        if (now >= end + FADE_TICKS) return 0;
        float in = Easing.clamp01((float) ((now - updatedAt) / 4 + (faceAt.isEmpty() ? 0 : 1)));
        return Math.min(in, now <= end ? 1 : 1 - (float) ((now - end) / FADE_TICKS));
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || shown == null) return;
        double now = PartyHud.now();
        float alpha = alpha(now);
        if (alpha <= 0.02f) {
            if (shown.total().isPresent() || now > updatedAt + LOST_TICKS) shown = null;
            return;
        }
        DiceRevealPayload reveal = shown;
        boolean mine = client.player != null && reveal.roller().map(client.player.getUuid()::equals).orElse(false);
        List<Piece> pieces = layout(reveal, mine, now);
        int width = -(GAP + 1);
        for (Piece piece : pieces) width += piece.width + GAP + 1;
        int screenWidth = context.getScaledWindowWidth(), screenHeight = context.getScaledWindowHeight();
        // Its plates' bottom (their shadow included) over the held item's name, or over a tool's HUD (Tile Linker Brush...)
        int y = screenHeight - BOTTOM_FROM_SCREEN_BOTTOM - (H + 3);
        int tool = ToolHud.occupiedTop();
        if (tool >= 0) y = Math.min(y, tool - 2 - (H + 3));
        int x = (screenWidth - width) / 2;
        for (Piece piece : pieces) {
            piece.draw(context, x, y, alpha, now);
            x += piece.width + GAP + 1;
        }
        top = y - 1;
        framesSinceDrawn = 0;
    }

    /** One thing in the row: the plate, a die's pill, a sign, the total, the double (its shape's width, drawn at x). */
    private abstract static class Piece {
        int width;

        abstract void draw(DrawContext context, int x, int y, float alpha, double now);
    }

    private static List<Piece> layout(DiceRevealPayload reveal, boolean mine, double now) {
        List<Piece> pieces = new ArrayList<>();
        Text who = Text.translatable("hud.steveparty.dice_reveal.who", reveal.name());
        pieces.add(new Plate(HudDraw.fit(who, 140), mine));
        List<DiceFace> faces = reveal.faces();
        int dice = Math.max(reveal.dice(), faces.size());
        if (dice > 1) {
            for (int i = 0; i < dice; i++) {
                if (i > 0) pieces.add(new Sign("+"));
                if (i < faces.size()) {
                    DiceFace face = faces.get(i);
                    boolean matching = reveal.combo() >= 2 && face.steps() == reveal.number();
                    Ramp ramp = matching ? (reveal.combo() >= 3 ? HudPaint.GOLD : GREEN) : rampOf(face);
                    pieces.add(new Pill(face.asText().getString(), ramp, i < faceAt.size() ? faceAt.get(i) : now,
                            matching ? comboAt : -1000));
                } else {
                    pieces.add(new Pill("?", HudPaint.EMPTY_SLOT, -1000, -1000));
                }
            }
        }
        Optional<Text> total = reveal.total();
        if (total.isPresent()) {
            if (dice > 1) pieces.add(new Sign("="));
            pieces.add(new Pill(HudDraw.fit(Text.literal(total.get().getString()), 160), HudPaint.GOLD, totalAt, -1000));
        }
        if (reveal.combo() >= 2) {
            Text flash = Text.translatable(reveal.combo() >= 3 ? "hud.steveparty.dice_reveal.triple" : "hud.steveparty.dice_reveal.double");
            pieces.add(new Pill(flash.getString(), reveal.combo() >= 3 ? HudPaint.GOLD : GREEN, comboAt, comboAt));
        }
        return pieces;
    }

    /** The pill of a face, by its kind (the colours of the faces' texts). */
    private static Ramp rampOf(DiceFace face) {
        return switch (face.kind()) {
            case PREMIUM, COIN -> HudPaint.GOLD;
            case CURSED, SWAP -> PURPLE;
            case DEBT -> RED;
            case BLANK -> HudPaint.EMPTY_SLOT;
            case NORMAL -> HudPaint.NEUTRAL;
        };
    }

    /** The roller: the dice icon and « LordFinn : ». */
    private static final class Plate extends Piece {
        final OrderedText text;
        final boolean gold;

        Plate(OrderedText text, boolean gold) {
            this.text = text;
            this.gold = gold;
            this.width = 4 + 9 + 4 + Math.max(0, HudDraw.font().getWidth(text) - 1) + 5;
        }

        @Override
        void draw(DrawContext context, int x, int y, float alpha, double now) {
            HudPaint.draw(context, HudPaint.shape(Form.CUT1, width, H, gold ? HudPaint.PLATE_GOLD : HudPaint.PLATE,
                    HudPaint.SHADOW | HudPaint.OUTLINE), x - PAD, y - PAD, alpha);
            HudDraw.icon(context, HudDraw.ICON_DICE, x + 4, y + 3, alpha);
            HudDraw.text(context, text, x + 17, y + 4, HudPaint.TEXT_DARK, alpha);
        }
    }

    /** « + » between two dice, « = » before the total. */
    private static final class Sign extends Piece {
        final String sign;

        Sign(String sign) {
            this.sign = sign;
            this.width = HudDraw.font().getWidth(sign) - 1;
        }

        @Override
        void draw(DrawContext context, int x, int y, float alpha, double now) {
            HudDraw.shadowed(context, sign, x, y + 4, HudPaint.TEXT, alpha);
        }
    }

    /** A pill: a die's face (« ? » while it turns), the total, the double; it pops in, a double's faces pulse. */
    private static final class Pill extends Piece {
        final @Nullable String text;
        final @Nullable OrderedText ordered;
        final Ramp ramp;
        final double popAt, pulseAt;

        Pill(String text, Ramp ramp, double popAt, double pulseAt) {
            this.text = text;
            this.ordered = null;
            this.ramp = ramp;
            this.popAt = popAt;
            this.pulseAt = pulseAt;
            this.width = Math.max(H, HudDraw.font().getWidth(text) - 1 + 10);
        }

        Pill(OrderedText text, Ramp ramp, double popAt, double pulseAt) {
            this.text = null;
            this.ordered = text;
            this.ramp = ramp;
            this.popAt = popAt;
            this.pulseAt = pulseAt;
            this.width = Math.max(H, HudDraw.font().getWidth(text) - 1 + 10);
        }

        @Override
        void draw(DrawContext context, int x, int y, float alpha, double now) {
            int w = width;
            float pop = (float) ((now - popAt) / POP_TICKS);
            float scale = pop < 1 ? 1 + 0.5f * (1 - Easing.easeOutBack(Easing.clamp01(pop))) : 1;
            if (pop < 0.15f) scale = Math.max(scale, 1.6f - 4 * pop);
            // A double / triple: a beat, three times
            double pulse = now - pulseAt;
            if (pulse >= 0 && pulse < 30) scale += 0.18f * (float) Math.abs(Math.sin(pulse * Math.PI / 10));
            boolean waiting = "?".equals(text);
            int dy = waiting ? Math.round((float) Math.sin(now * 1.3 + x) * 1.2f) : 0;
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            float mx = x + width / 2f, my = y + H / 2f;
            matrices.translate(mx, my + dy, 0);
            matrices.scale(scale, scale, 1);
            matrices.translate(-mx, -my, 0);
            float a = waiting ? alpha * 0.8f : alpha;
            HudPaint.draw(context, HudPaint.shape(Form.PILL, w, H, ramp, HudPaint.SHADOW | HudPaint.OUTLINE | HudPaint.BAND), x - PAD, y - PAD, a);
            int textWidth = (text != null ? HudDraw.font().getWidth(text) : HudDraw.font().getWidth(ordered)) - 1;
            int tx = x + (w - textWidth) / 2;
            int colour = ramp == HudPaint.NEUTRAL || ramp == HudPaint.EMPTY_SLOT ? HudPaint.TEXT_DARK
                    : ramp == HudPaint.GOLD ? ramp.outline() : 0xFFFFFFFF;
            if (text != null) HudDraw.text(context, text, tx, y + 4, colour, a);
            else HudDraw.text(context, ordered, tx, y + 4, colour, a);
            matrices.pop();
        }
    }
}
