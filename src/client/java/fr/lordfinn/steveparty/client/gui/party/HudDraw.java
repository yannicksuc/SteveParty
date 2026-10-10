package fr.lordfinn.steveparty.client.gui.party;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.gui.GuiText;
import fr.lordfinn.steveparty.client.gui.HudDepth;
import fr.lordfinn.steveparty.client.gui.ToolHud;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.utils.Argb;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

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

    /** White, faded: the tint of sprites. */
    static int white(float alpha) {
        return Argb.fade(0xFFFFFFFF, alpha);
    }

    /**
     * Runs texture draws blended and faded to {@code alpha} (1.21.1 textures take no colour: the shader colour), in
     * front of what the HUD drew before ({@link HudDepth}).
     */
    static void faded(DrawContext context, float alpha, Runnable draw) {
        HudDepth.onTop(context, () -> {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 1f, 1f, ColorHelper.Argb.getAlpha(white(alpha)) / 255f);
            draw.run();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        });
    }

    /** A plain fill (vertex colour: {@code argb} faded), in front of what the HUD drew before. */
    static void fill(DrawContext context, int x0, int y0, int x1, int y1, int argb, float alpha) {
        HudDepth.onTop(context, () -> context.fill(x0, y0, x1, y1, Argb.fade(argb, alpha)));
    }

    static void plate(DrawContext context, ToolHud.Plate plate, int x, int y, int width, int height, float alpha) {
        faded(context, alpha, () -> context.drawGuiTexture(plateSprite(plate), x, y, width, height));
    }

    private static Identifier plateSprite(ToolHud.Plate plate) {
        return Steveparty.id("board/plate_" + plate.name().toLowerCase(Locale.ROOT));
    }

    static void icon(DrawContext context, Identifier icon, int x, int y, float alpha) {
        faded(context, alpha, () -> context.drawGuiTexture(icon, x, y, ICON, ICON));
    }

    /** One line of text in the box ({@code x}, {@code y}, {@code width}) (see {@link UiText#line}), faded. */
    static void text(DrawContext context, Text text, int x, int y, int width, int color, float alpha) {
        HudDepth.onTop(context, () -> UiText.line(context, font(), text, x, y, width, Argb.fade(color, alpha), false));
    }

    static void text(DrawContext context, OrderedText text, int x, int y, int width, int color, float alpha) {
        HudDepth.onTop(context, () -> UiText.line(context, font(), text, x, y, width, Argb.fade(color, alpha), false));
    }

    static void text(DrawContext context, String text, int x, int y, int width, int color, float alpha) {
        HudDepth.onTop(context, () -> UiText.line(context, font(), text, x, y, width, Argb.fade(color, alpha), false));
    }

    /** Same, centered in the box when it fits. */
    static void centered(DrawContext context, String text, int x, int y, int width, int color, float alpha) {
        HudDepth.onTop(context, () -> UiText.centered(context, font(), text, x, y, width, Argb.fade(color, alpha), false));
    }

    /** Light text with the font's shadow (a quarter of its colour, a pixel down and right), in its box. */
    static void shadowed(DrawContext context, String text, int x, int y, int width, int color, float alpha) {
        HudDepth.onTop(context, () -> UiText.line(context, font(), text, x, y, width, Argb.fade(color, alpha), true));
    }

    // Without a box: the callers not given one yet, their text taking its own width (it never scrolls)

    static void text(DrawContext context, Text text, int x, int y, int color, float alpha) {
        text(context, text, x, y, font().getWidth(text), color, alpha);
    }

    static void text(DrawContext context, OrderedText text, int x, int y, int color, float alpha) {
        text(context, text, x, y, font().getWidth(text), color, alpha);
    }

    static void text(DrawContext context, String text, int x, int y, int color, float alpha) {
        text(context, text, x, y, font().getWidth(text), color, alpha);
    }

    static void shadowed(DrawContext context, String text, int x, int y, int color, float alpha) {
        shadowed(context, text, x, y, font().getWidth(text), color, alpha);
    }

    /** The text, cut with an ellipsis to fit in {@code width} pixels. */
    static OrderedText fit(Text text, int width) {
        return GuiText.fit(font(), text, width);
    }

    // ------------------------------------------------------------------ easing

    /** Frame-rate independent smoothing of {@code current} towards {@code target} ({@code rate} per tick). */
    static float approach(float current, float target, float rate, float deltaTicks) {
        if (Math.abs(target - current) < 0.01f) return target;
        return current + (target - current) * (1 - (float) Math.exp(-rate * deltaTicks));
    }
}
