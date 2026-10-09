package fr.lordfinn.steveparty.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * Button in the mod's pixel style ({@link PartyGui}): light grey, green for the main action, gold when selected
 * (toggle groups), dimmed when inactive. Hovered or focused, it brightens and gets a white outline; while the mouse
 * is held on it, it is drawn pressed in. The content is its text, or a custom {@link Content} (symbols, icons).
 */
public class PartyButton extends PressableWidget {
    public enum Style { NORMAL, PRIMARY }

    /** Draws the button's content centered on (centerX, centerY), in the given colour. */
    @FunctionalInterface
    public interface Content {
        void draw(DrawContext context, TextRenderer textRenderer, int centerX, int centerY, int color);
    }

    private final Consumer<PartyButton> onPress;
    private Style style = Style.NORMAL;
    private boolean selected;
    private Content content;

    public PartyButton(int x, int y, int width, int height, Text message, Consumer<PartyButton> onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    public PartyButton style(Style style) {
        this.style = style;
        return this;
    }

    public PartyButton content(Content content) {
        this.content = content;
        return this;
    }

    /** Pixels on the right a too long label leaves free (for a badge on the button's corner). */
    private int reservedRight;

    public PartyButton reserveRight(int pixels) {
        this.reservedRight = pixels;
        return this;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isSelected() {
        return selected;
    }

    @Override
    public void onPress() {
        onPress.accept(this);
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        // focus only shows when it came from the keyboard: a clicked button keeps the focus but must not stay lit
        boolean keyboardFocus = isFocused() && MinecraftClient.getInstance().getNavigationType().isKeyboard();
        boolean highlighted = active && (isHovered() || keyboardFocus);
        boolean held = highlighted && isHovered() && MinecraftClient.getInstance().mouse.wasLeftButtonClicked();
        PartyGui.Theme theme = !active ? PartyGui.BUTTON_DISABLED
                : selected ? PartyGui.BUTTON_SELECTED
                : style == Style.PRIMARY ? PartyGui.BUTTON_PRIMARY
                : PartyGui.BUTTON;
        if (highlighted && !selected) theme = theme.brighter();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        // Selected toggles and held buttons are pushed in (1 px lower, bevels swapped)
        boolean pressedIn = selected || held;
        PartyGui.button(context, x, y, w, h, theme, pressedIn);
        if (highlighted) context.drawBorder(x, y, w, h, 0xFFFFFFFF);

        int textColor = !active ? 0xFF6E6E6E
                : selected ? 0xFF4A2C00
                : style == Style.PRIMARY ? 0xFFFFFFFF
                : 0xFF2E2E2E;
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int centerX = x + w / 2, centerY = y + h / 2 + (pressedIn ? 1 : 0);
        if (content != null) {
            content.draw(context, textRenderer, centerX, centerY, textColor);
        } else {
            Text message = getMessage();
            int room = w - 6 - reservedRight;
            if (textRenderer.getWidth(message) > room) {
                // Too long for the button: cut with « … », scrolling back and forth while hovered
                drawLong(context, textRenderer, message, x + 3, centerY - 4, room, textColor);
                return;
            }
            int textX = centerX - textRenderer.getWidth(message) / 2;
            if (style == Style.PRIMARY && active) {
                context.drawTextWithShadow(textRenderer, message, textX, centerY - 4, textColor);
            } else {
                context.drawText(textRenderer, message, textX, centerY - 4, textColor, false);
            }
        }
    }

    private static final float MARQUEE_SPEED = 28F;
    private static final long MARQUEE_PAUSE_MS = 700;
    private long marqueeStart = -1;

    private void drawLong(DrawContext context, TextRenderer textRenderer, Text message, int left, int top, int room, int color) {
        boolean shadow = style == Style.PRIMARY && active;
        if (!isHovered()) {
            marqueeStart = -1;
            String cut = GuiText.cut(textRenderer, message.getString(), room);
            Text shown = Text.literal(cut).setStyle(message.getStyle());
            context.drawText(textRenderer, shown, left + (room - textRenderer.getWidth(shown)) / 2, top, color, shadow);
            return;
        }
        long now = net.minecraft.util.Util.getMeasuringTimeMs();
        if (marqueeStart < 0) marqueeStart = now;
        int travel = textRenderer.getWidth(message) - room;
        long moveMs = Math.max(1, (long) (travel / MARQUEE_SPEED * 1000F));
        long t = (now - marqueeStart) % (2 * (MARQUEE_PAUSE_MS + moveMs));
        float offset;
        if (t < MARQUEE_PAUSE_MS) offset = 0;
        else if (t < MARQUEE_PAUSE_MS + moveMs) offset = (t - MARQUEE_PAUSE_MS) / (float) moveMs * travel;
        else if (t < 2 * MARQUEE_PAUSE_MS + moveMs) offset = travel;
        else offset = travel - (t - 2 * MARQUEE_PAUSE_MS - moveMs) / (float) moveMs * travel;
        context.enableScissor(left, top - 1, left + room, top + 10);
        context.getMatrices().push();
        context.getMatrices().translate(-offset, 0, 0);
        context.drawText(textRenderer, message, left, top, color, shadow);
        context.getMatrices().pop();
        context.disableScissor();
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
