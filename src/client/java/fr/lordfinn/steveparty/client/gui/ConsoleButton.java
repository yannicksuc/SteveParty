package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.client.gui.cartridge.ButtonGlyphs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * A button of the controllers' consoles, as their mock-ups draw it (build_controllers_screens_v2.py {@code button}): its
 * colour and its icon say what it does (▶ green: play, ■ red: stop, ✔ gold: ready, greyed: can't), its 8-row label
 * centred with its icon. Hovered, it brightens and gets a white outline; held, its bevels swap.
 */
public class ConsoleButton extends PressableWidget {
    /**
     * Its colours, on the mod's button ({@link PartyGui#button}: a 1 px outline with its corners cut, a 1 px light bevel
     * on the top and left, a 1 px dark one on the bottom and right, swapped when pressed): outline, light, body, dark.
     */
    public enum Kind {
        NEUTRAL(PartyGui.BUTTON),
        GOLD(new PartyGui.Theme(0xFF8A4A00, 0xFFFFE36A, 0xFFFFC600, 0xFFC98600)),
        GREEN(PartyGui.BUTTON_PRIMARY),
        RED(PartyGui.FLAG_RED),
        /** Out of use: the mod's disabled button, flat. */
        OFF(PartyGui.BUTTON_DISABLED),
        /** On a dark screen: the screen's colours, a light label. */
        SCREEN(new PartyGui.Theme(0xFF0D0A18, 0xFF3D3A66, 0xFF262350, 0xFF1D1A46)),
        /** On the mini-game page's paper: plain, green (the main action), teal, greyed (flat). */
        PAPER(new PartyGui.Theme(0xFF7E9192, 0xFFFFFFFF, 0xFFD6EBEC, 0xFFA9BFC0)),
        PAPER_GREEN(new PartyGui.Theme(0xFF005A40, 0xFF8FF5D0, 0xFF00C792, 0xFF008A66)),
        PAPER_TEAL(new PartyGui.Theme(0xFF004A50, 0xFF8FF0F6, 0xFF00B3BD, 0xFF008C95)),
        PAPER_OFF(new PartyGui.Theme(0xFF8AA3A6, 0xFFDDE9EA, 0xFFDDE9EA, 0xFFDDE9EA));

        final PartyGui.Theme theme;

        Kind(PartyGui.Theme theme) {
            this.theme = theme;
        }

        boolean paper() {
            return this == PAPER || this == PAPER_GREEN || this == PAPER_TEAL || this == PAPER_OFF;
        }
    }

    /** Drawn over the button (an icon of its own: the « Remove » cross). */
    @FunctionalInterface
    public interface Decoration {
        void draw(DrawContext context, ConsoleButton button);
    }

    private @Nullable Decoration decoration;

    public ConsoleButton decoration(Decoration decoration) {
        this.decoration = decoration;
        return this;
    }

    /** One of the mod's button icons in place of its label ({@link ButtonIcons}), in the label's colour, still when pressed. */
    private ButtonIcons.@Nullable Icon symbol;

    public ConsoleButton symbol(ButtonIcons.Icon symbol) {
        this.symbol = symbol;
        return this;
    }

    /** The icon before the label, from the mod's button icons. */
    public enum Icon {
        PLAY(ButtonIcons.Icon.PLAY), STOP(ButtonIcons.Icon.STOP), CHECK(ButtonIcons.Icon.CHECK);

        final ButtonIcons.Icon icon;

        Icon(ButtonIcons.Icon icon) {
            this.icon = icon;
        }
    }

    /**
     * An icon's box (a symbol's) and the room from it to the label: its 8 px ink (rows 1-8 of the box) 4 px from the
     * label's first letter, resting on the label's baseline.
     */
    private static final int ICON_BOX = 10, ICON_GAP = 3;

    private final Runnable onPress;
    private Kind kind;
    private @Nullable Icon icon;

    public ConsoleButton(int x, int y, int width, int height, Text message, Kind kind, @Nullable Icon icon, Runnable onPress) {
        super(x, y, width, height, message);
        this.kind = kind;
        this.icon = icon;
        this.onPress = onPress;
    }

    @Override
    public void onPress() {
        onPress.run();
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        // Greyed when it can't be pressed (the screen's buttons stay dark, their label dimmed)
        Kind shown = active || kind == Kind.SCREEN ? kind : kind.paper() ? Kind.PAPER_OFF : Kind.OFF;
        boolean highlighted = active && (isHovered() || isFocused());
        boolean held = highlighted && isHovered() && MinecraftClient.getInstance().mouse.wasLeftButtonClicked();
        // As PartyButton: hovered brighter in a white frame (teal on paper), held pushed in; what is on it never moves
        PartyGui.button(context, getX(), getY(), width, height, highlighted ? shown.theme.brighter() : shown.theme, held);
        if (highlighted) context.drawBorder(getX(), getY(), width, height, shown.paper() ? 0xFF008C95 : 0xFFFFFFFF);
        if (decoration != null) {
            decoration.draw(context, this);
            return;
        }
        boolean light = shown == Kind.GREEN || shown == Kind.RED || shown == Kind.PAPER_GREEN || shown == Kind.PAPER_TEAL;
        int ink = shown == Kind.SCREEN ? (active ? 0xFFE0EEF3 : 0xFF5E5C88) : light ? 0xFFFFFFFF
                : shown == Kind.PAPER ? 0xFF1E3A40 : shown == Kind.PAPER_OFF ? 0xFF8AA3A6 : shown == Kind.OFF ? PartyGui.DISABLED_INK
                : shown == Kind.NEUTRAL ? 0xFF2E2E2E : shown.theme.outline();
        // White ink on a coloured face, and the screen's light labels, with the font's shadow
        boolean shadow = light || shown == Kind.SCREEN;
        if (symbol != null) {
            // A button under 18 px: its − / + from the small set (ButtonGlyphs), the full icons need room around them
            if (Math.min(width, height) < 18 && symbol == ButtonIcons.Icon.MINUS)
                ButtonGlyphs.minus(context, getX(), getY(), width, height, ink);
            else if (Math.min(width, height) < 18 && symbol == ButtonIcons.Icon.PLUS)
                ButtonGlyphs.plus(context, getX(), getY(), width, height, ink);
            else ButtonIcons.draw(context, symbol, getX(), getY(), width, height, ink, light);
            return;
        }

        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        Text label = getMessage();
        // The icon's box then the label, as one group centred on its ink
        int iconWidth = icon == null ? 0 : ICON_BOX + ICON_GAP;
        int tx = getX() + (width - iconWidth - font.getWidth(label) + 1) / 2;
        // Too long for the button: from its left, inside its outline, the label scrolling
        tx = Math.max(tx, getX() + 2);
        int ty = getY() + (height - 7) / 2;
        if (icon != null) {
            ButtonIcons.draw(context, icon.icon, tx, ty - 2, ICON_BOX, ICON_BOX, ink, light);
            tx += iconWidth;
        }
        UiText.line(context, font, label, tx, ty, getX() + width - 1 - tx, ink, shadow);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
