package fr.lordfinn.steveparty.client.gui.party;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.gui.ToolHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;

/**
 * Drawing bits shared by the party HUDs, in the mod's GUI style (the board plates of {@link ToolHud}: light plates cut
 * like the mod's screens, dark text), every colour carrying the HUD's fade (vertex colours for fills and text,
 * the shader colour for textures: see {@link #faded}).
 */
final class HudDraw {
    static final int TEXT = ToolHud.TEXT;
    static final int TEXT_SOFT = 0xFF6B6B6B;
    static final int TEXT_MINE = 0xFF1F6E2A;
    static final int TEXT_WARN = 0xFFA02A1E;
    static final int OUTLINE = 0xFF2B2B2B;

    static final Identifier ICON_DICE = icon("dice");
    static final Identifier ICON_STEPS = icon("steps");
    static final Identifier ICON_SHOP = icon("shop");
    static final Identifier ICON_CLOCK = icon("clock");
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
        int a = MathHelper.clamp(Math.round(ColorHelper.Argb.getAlpha(argb) * alpha), 0, 255);
        return ColorHelper.Argb.withAlpha(a, argb);
    }

    /** White, faded: the tint of sprites. */
    static int white(float alpha) {
        return fade(0xFFFFFFFF, alpha);
    }

    /** Runs texture draws blended and faded to {@code alpha} (1.21.1 textures take no colour: the shader colour). */
    static void faded(float alpha, Runnable draw) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, ColorHelper.Argb.getAlpha(white(alpha)) / 255f);
        draw.run();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    static void plate(DrawContext context, ToolHud.Plate plate, int x, int y, int width, int height, float alpha) {
        faded(alpha, () -> context.drawGuiTexture(plateSprite(plate), x, y, width, height));
    }

    private static Identifier plateSprite(ToolHud.Plate plate) {
        return Steveparty.id("board/plate_" + plate.name().toLowerCase(java.util.Locale.ROOT));
    }

    static void icon(DrawContext context, Identifier icon, int x, int y, float alpha) {
        faded(alpha, () -> context.drawGuiTexture(icon, x, y, ICON, ICON));
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

    /** Light text with the font's shadow (a quarter of its colour, a pixel down and right). */
    static void shadowed(DrawContext context, String text, int x, int y, int color, float alpha) {
        context.drawText(font(), text, x, y, fade(color, alpha), true);
    }

    /** The text, cut with an ellipsis to fit in {@code width} pixels. */
    static OrderedText fit(Text text, int width) {
        TextRenderer font = font();
        if (font.getWidth(text) <= width) return text.asOrderedText();
        StringVisitable cut = font.trimToWidth(text, Math.max(0, width - font.getWidth("…")));
        return Language.getInstance().reorder(StringVisitable.concat(cut, StringVisitable.plain("…")));
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
