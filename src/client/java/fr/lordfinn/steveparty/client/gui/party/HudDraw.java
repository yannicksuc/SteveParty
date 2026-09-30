package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.gui.ToolHud;
import fr.lordfinn.steveparty.client.utils.SkinUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;

import java.util.UUID;

/**
 * Drawing bits shared by the party HUDs, in the mod's GUI style (the board plates of {@link ToolHud}: light plates cut
 * like the mod's screens, dark text), every colour carrying the HUD's fade (vertex colours: the draw context is
 * deferred, no global shader colour).
 */
final class HudDraw {
    static final int TEXT = ToolHud.TEXT;
    static final int TEXT_SOFT = 0xFF6B6B6B;
    static final int TEXT_MINE = 0xFF1F6E2A;
    static final int TEXT_WARN = 0xFFA02A1E;
    static final int OUTLINE = 0xFF2B2B2B;
    /** Token colours of the tokens that have none (the plates' colours), by turn order. */
    private static final int[] PALETTE = {0x3F8FE0, 0xE0453A, 0x5DB83A, 0xF2C230, 0xA85CE0, 0xE08A1E, 0x3AC0B8, 0xE05CA8};

    static final Identifier ICON_DICE = icon("dice");
    static final Identifier ICON_STEPS = icon("steps");
    static final Identifier ICON_SHOP = icon("shop");
    static final Identifier ICON_REPLAY = icon("replay");
    static final Identifier ICON_CLOCK = icon("clock");
    static final Identifier ICON_POINTS = icon("flag");
    static final Identifier ICON_MINI_GAME = icon("gamepad");
    static final Identifier ICON_PREPARING = icon("gear");
    static final Identifier ICON_CROWN = icon("crown");
    static final Identifier ICON_MARKER = icon("marker");
    static final int ICON = 9;

    private HudDraw() {
    }

    private static Identifier icon(String name) {
        return Steveparty.id("party_hud/" + name);
    }

    static TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }

    /** A colour with its alpha multiplied by {@code alpha} (0..1). */
    static int fade(int argb, float alpha) {
        int a = MathHelper.clamp(Math.round(ColorHelper.getAlpha(argb) * alpha), 0, 255);
        return ColorHelper.withAlpha(a, argb);
    }

    /** White, faded: the tint of sprites. */
    static int white(float alpha) {
        return fade(0xFFFFFFFF, alpha);
    }

    /** The colour of a token (0xRRGGBB, -1 for none: then one of the palette, by turn order). Opaque ARGB. */
    static int tokenColor(int color, int index) {
        return 0xFF000000 | (color >= 0 ? color : PALETTE[Math.floorMod(index, PALETTE.length)]);
    }

    static void plate(DrawContext context, ToolHud.Plate plate, int x, int y, int width, int height, float alpha) {
        context.drawGuiTexture(RenderLayer::getGuiTextured, plateSprite(plate), x, y, width, height, white(alpha));
    }

    private static Identifier plateSprite(ToolHud.Plate plate) {
        return Steveparty.id("board/plate_" + plate.name().toLowerCase(java.util.Locale.ROOT));
    }

    static void icon(DrawContext context, Identifier icon, int x, int y, float alpha) {
        context.drawGuiTexture(RenderLayer::getGuiTextured, icon, x, y, ICON, ICON, white(alpha));
    }

    static void text(DrawContext context, Text text, int x, int y, int color, float alpha) {
        context.drawText(font(), text, x, y, fade(color, alpha), false);
    }

    static void text(DrawContext context, OrderedText text, int x, int y, int color, float alpha) {
        context.drawText(font(), text, x, y, fade(color, alpha), false);
    }

    static void text(DrawContext context, String text, int x, int y, int color, float alpha) {
        context.drawText(font(), text, x, y, fade(color, alpha), false);
    }

    /** The text, cut with an ellipsis to fit in {@code width} pixels. */
    static OrderedText fit(Text text, int width) {
        TextRenderer font = font();
        if (font.getWidth(text) <= width) return text.asOrderedText();
        StringVisitable cut = font.trimToWidth(text, Math.max(0, width - font.getWidth("…")));
        return Language.getInstance().reorder(StringVisitable.concat(cut, StringVisitable.plain("…")));
    }

    /**
     * A player's face (hat layer included) framed with a token colour: {@code size} pixels, the face inside a 1 pixel
     * dark outline and a 1 pixel colour ring. A token without owner shows the initial of its name on its colour.
     */
    static void face(DrawContext context, UUID owner, String name, int color, int x, int y, int size, float alpha, boolean dim) {
        context.fill(x, y, x + size, y + size, fade(OUTLINE, alpha));
        context.fill(x + 1, y + 1, x + size - 1, y + size - 1, fade(dim ? ColorHelper.lerp(0.55f, color, 0xFF6B6B6B) : color, alpha));
        int inner = size - 4;
        int tint = dim ? fade(0xFF8C8C8C, alpha) : white(alpha);
        if (owner != null) {
            Identifier skin = SkinUtils.getPlayerSkin(owner);
            context.drawTexture(RenderLayer::getGuiTextured, skin, x + 2, y + 2, 8, 8, inner, inner, 8, 8, 64, 64, tint);
            context.drawTexture(RenderLayer::getGuiTextured, skin, x + 2, y + 2, 40, 8, inner, inner, 8, 8, 64, 64, tint);
        } else {
            context.fill(x + 2, y + 2, x + size - 2, y + size - 2, fade(dim ? 0xFF6B6B6B : 0xFF3F3F3F, alpha));
            String initial = name.isEmpty() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT);
            TextRenderer font = font();
            context.drawText(font, initial, x + (size - font.getWidth(initial)) / 2 + 1, y + (size - 8) / 2 + 1,
                    fade(0xFFFFFFFF, alpha), false);
        }
    }

    // ------------------------------------------------------------------ easing

    static float clamp01(float t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    static float easeOutCubic(float t) {
        t = clamp01(t);
        float u = 1 - t;
        return 1 - u * u * u;
    }

    /** Overshoots a little before settling: the pops. */
    static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f, c3 = c1 + 1;
        float u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    /** Frame-rate independent smoothing of {@code current} towards {@code target} ({@code rate} per tick). */
    static float approach(float current, float target, float rate, float deltaTicks) {
        if (Math.abs(target - current) < 0.01f) return target;
        return current + (target - current) * (1 - (float) Math.exp(-rate * deltaTicks));
    }
}
