package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.client.gui.ToolHud;
import fr.lordfinn.steveparty.client.gui.paint.Ramp;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.hud.DiceRevealLayout;
import fr.lordfinn.steveparty.hud.HudShapes.Form;
import fr.lordfinn.steveparty.payloads.custom.DiceRevealPayload;
import fr.lordfinn.steveparty.utils.Easing;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.hud.HudShapes.PAD;

/**
 * The reveal of a throw, at the action bar's place, in the party HUD's look: the roller on a white plate (gold: my own
 * throw), then a pill per die, « ? » while it turns, popping in with its face as it stops (« 3 + 5 + ? »), then « = »
 * and the total in a gold pill; a double / triple flashes in a green / orange pill and tints the matching faces
 * (green / gold). A single die shows its result only. Laid out on a fixed grid ({@link DiceRevealLayout}): nothing
 * jumps from one state to the next. Sent by the server ({@link DiceRevealPayload}) to the players near the dice and to
 * the roller's party; it stays a few seconds after the total, then fades. The vanilla action bar goes above it.
 */
public final class DiceRevealHud {
    private static final int H = DiceRevealLayout.H;
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
    /** Where the row of the current throw starts (see {@link DiceRevealLayout}), for that throw and screen width. */
    private static int anchorThrow = Integer.MIN_VALUE, anchorScreen, anchorX;
    /** The longest plate and total texts (cut with an ellipsis beyond). */
    private static final int MAX_PLATE_TEXT = 120, MAX_TOTAL_TEXT = 160;
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
        TextRenderer font = HudDraw.font();
        String plate = Text.translatable("hud.steveparty.dice_reveal.who", reveal.name()).getString();
        List<DiceFace> faces = reveal.faces();
        int dice = Math.max(reveal.dice(), faces.size());
        List<String> faceTexts = new ArrayList<>();
        for (DiceFace face : faces) faceTexts.add(face.asText().getString());
        String total = reveal.total().map(Text::getString).orElse(null);
        // The roller's plate and the total keep a bounded size: a longer text scrolls in it (UiText)
        DiceRevealLayout.Measure measure = text -> {
            int width = font.getWidth(text);
            if (text.equals(plate)) return Math.min(width, MAX_PLATE_TEXT + 1);
            if (text.equals(total)) return Math.min(width, MAX_TOTAL_TEXT + 1);
            return width;
        };
        String badge = reveal.combo() >= 2 ? badge(reveal.combo()) : null;
        List<DiceRevealLayout.Box> boxes = DiceRevealLayout.layout(measure, plate, dice, faceTexts, total, badge);

        int screenWidth = context.getScaledWindowWidth(), screenHeight = context.getScaledWindowHeight();
        // The row starts where the whole final row is centred, once per throw: nothing before the badge moves after
        if (anchorThrow != reveal.throwId() || anchorScreen != screenWidth) {
            anchorThrow = reveal.throwId();
            anchorScreen = screenWidth;
            int reserved = DiceRevealLayout.reserved(measure, plate, dice, faceTexts, total, List.of(badge(2), badge(3)));
            anchorX = (screenWidth - reserved) / 2;
        }
        int width = DiceRevealLayout.width(boxes);
        int x0 = Math.max(4, Math.min(anchorX, screenWidth - 4 - width));
        // Its plates' bottom (their shadow included) over the held item's name, or over a tool's HUD (Tile Linker Brush...)
        int y = screenHeight - BOTTOM_FROM_SCREEN_BOTTOM - (H + 3);
        int tool = ToolHud.occupiedTop();
        if (tool >= 0) y = Math.min(y, tool - 2 - (H + 3));
        for (DiceRevealLayout.Box box : boxes) {
            int x = x0 + box.x();
            switch (box.kind()) {
                case PLATE -> drawPlate(context, box, x, y, plate, mine, alpha);
                case PLUS -> HudDraw.shadowed(context, "+", x, y + 4, box.width(), HudPaint.TEXT, alpha);
                case EQUALS -> HudDraw.shadowed(context, "=", x, y + 4, box.width(), HudPaint.TEXT, alpha);
                case DIE -> {
                    int i = box.index();
                    if (i < faces.size()) {
                        DiceFace face = faces.get(i);
                        boolean matching = reveal.combo() >= 2 && face.steps() == reveal.number();
                        Ramp ramp = matching ? (reveal.combo() >= 3 ? HudPaint.GOLD : GREEN) : rampOf(face);
                        drawPill(context, box, x, y, faceTexts.get(i), ramp, i < faceAt.size() ? faceAt.get(i) : now,
                                matching ? comboAt : -1000, false, alpha, now);
                    } else {
                        drawPill(context, box, x, y, "?", HudPaint.EMPTY_SLOT, -1000, -1000, true, alpha, now);
                    }
                }
                case TOTAL -> drawPill(context, box, x, y, total, HudPaint.GOLD, totalAt, -1000, false, alpha, now);
                case BADGE -> drawPill(context, box, x, y, badge, reveal.combo() >= 3 ? HudPaint.MINI_GAME : GREEN,
                        comboAt, comboAt, false, alpha, now);
            }
        }
        top = y - 1;
        framesSinceDrawn = 0;
    }

    private static String badge(int combo) {
        return Text.translatable(combo >= 3 ? "hud.steveparty.dice_reveal.triple" : "hud.steveparty.dice_reveal.double").getString();
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
    private static void drawPlate(DrawContext context, DiceRevealLayout.Box box, int x, int y, String text, boolean gold, float alpha) {
        HudPaint.draw(context, HudPaint.shape(Form.CUT1, box.width(), H, gold ? HudPaint.PLATE_GOLD : HudPaint.PLATE,
                HudPaint.SHADOW | HudPaint.OUTLINE), x - PAD, y - PAD, alpha);
        HudDraw.icon(context, HudDraw.ICON_DICE, x + DiceRevealLayout.PLATE_ICON_X, y + 3, alpha);
        // Its room: the plate less the icon and the right margin (the font's trailing pixel in it)
        HudDraw.text(context, text, x + DiceRevealLayout.PLATE_TEXT_X, y + 4,
                box.width() - DiceRevealLayout.PLATE_TEXT_X - DiceRevealLayout.PLATE_RIGHT + 1, HudPaint.TEXT_DARK, alpha);
    }

    /**
     * A pill, its text centred: a die's face (« ? » bobbing while it turns), the total, the badge. It grows in, never
     * past its box (the gaps stay clear); the pills of a double flash lighter on the beat.
     */
    private static void drawPill(DrawContext context, DiceRevealLayout.Box box, int x, int y, String text, Ramp ramp,
                                 double popAt, double pulseAt, boolean waiting, float alpha, double now) {
        int w = box.width();
        boolean dark = ramp == HudPaint.NEUTRAL || ramp == HudPaint.EMPTY_SLOT;
        int colour = dark ? HudPaint.TEXT_DARK : ramp == HudPaint.GOLD ? ramp.outline() : 0xFFFFFFFF;
        float pop = (float) ((now - popAt) / POP_TICKS);
        float scale = pop < 1 ? 0.6f + 0.4f * Easing.easeOutCubic(Easing.clamp01(pop)) : 1;
        double pulse = now - pulseAt;
        // A double / triple: three beats
        if (pulse >= 0 && pulse < 30 && Math.sin(pulse * Math.PI / 5) > 0) {
            ramp = new Ramp(ramp.outline(), ramp.hi(), ramp.hi(), ramp.body()); // lit up, the text dark on it
            colour = ramp.outline();
        }
        int dy = waiting ? Math.round((float) Math.sin(now * 1.3 + x) * 1.2f) : 0;
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        float mx = x + w / 2f, my = y + H / 2f;
        matrices.translate(mx, my + dy, 0);
        matrices.scale(scale, scale, 1);
        matrices.translate(-mx, -my, 0);
        float a = waiting ? alpha * 0.8f : alpha;
        HudPaint.draw(context, HudPaint.shape(Form.PILL, w, H, ramp, HudPaint.SHADOW | HudPaint.OUTLINE | HudPaint.BAND), x - PAD, y - PAD, a);
        // Centred in the pill less 5 pixels on the left and 3 on the right (the font's trailing pixel), as before
        HudDraw.centered(context, text, x + 5, y + 4, w - 8, colour, a);
        matrices.pop();
    }
}
