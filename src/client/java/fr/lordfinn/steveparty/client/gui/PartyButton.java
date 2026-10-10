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
            boolean shadow = style == Style.PRIMARY && active;
            // Too long for the button: it scrolls slowly in it, whole in a tooltip under the mouse (UiText)
            if (textRenderer.getWidth(message) <= room) UiText.centered(context, textRenderer, message, x, centerY - 4, w, textColor, shadow);
            else UiText.line(context, textRenderer, message, x + 3, centerY - 4, room, textColor, shadow);
        }
    }


    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
