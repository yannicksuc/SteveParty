package fr.lordfinn.steveparty.client.gui;

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
    public enum Kind {
        NEUTRAL(ConsolePaint.Ramp.of(0x000000, 0xffffff, 0xe2e2e2, 0x8a8a8a)),
        GOLD(ConsolePaint.Ramp.of(0x8a4a00, 0xffe36a, 0xffc600, 0xffaa00)),
        GREEN(ConsolePaint.Ramp.of(0x08270a, 0xa6ef8a, 0x46ae2e, 0x1f6a14)),
        RED(ConsolePaint.Ramp.of(0x33030a, 0xff8f8f, 0xd9283b, 0x8e1022)),
        OFF(ConsolePaint.Ramp.of(0x3a3a3a, 0xd0d0d0, 0xa8a8a8, 0x808080)),
        /** On a dark screen: the screen's colours, a light label. */
        SCREEN(ConsolePaint.Ramp.of(0x0d0a18, 0x3d3a66, 0x262350, 0x0d0a18)),
        /** On the mini-game page's paper: plain, green (the main action), teal, greyed. */
        PAPER(ConsolePaint.Ramp.of(0x7e9192, 0xffffff, 0xd6ebec, 0xc7dbdc)),
        PAPER_GREEN(ConsolePaint.Ramp.of(0x005a40, 0x8ff5d0, 0x00c792, 0x00ac82)),
        PAPER_TEAL(ConsolePaint.Ramp.of(0x004a50, 0x8ff0f6, 0x00b3bd, 0x008c95)),
        PAPER_OFF(ConsolePaint.Ramp.of(0x8aa3a6, 0xf0f6f6, 0xdde9ea, 0xc9d7d8));

        final ConsolePaint.Ramp ramp;

        Kind(ConsolePaint.Ramp ramp) {
            this.ramp = ramp;
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

    /** The icons before the label: 7 rows, as the mock-ups' font draws them. */
    public enum Icon {
        PLAY("#...", "##..", "###.", "####", "###.", "##..", "#..."),
        STOP(".....", "#####", "#####", "#####", "#####", "#####", "....."),
        CHECK(".....", "....#", "...#.", "#..#.", ".##..", "..#..", ".....");

        final String[] rows;

        Icon(String... rows) {
            this.rows = rows;
        }

        int width() {
            return rows[0].length();
        }
    }

    private final Runnable onPress;
    private Kind kind;
    private @Nullable Icon icon;

    public void setKind(Kind kind, @Nullable Icon icon) {
        this.kind = kind;
        this.icon = icon;
    }

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
        ConsolePaint.Ramp ramp = shown.ramp;
        if (held) ramp = new ConsolePaint.Ramp(ramp.outline(), ramp.shadow(), ramp.body(), ramp.hi());
        ConsolePaint.box(context, getX(), getY(), width, height, ramp, 1, 1);
        // The highlight follows the button's cut corners
        if (highlighted) ConsolePaint.highlight(context, getX(), getY(), width, height, 1, shown.paper() ? 0xFF008C95 : 0xFFFFFFFF,
                held ? 0 : shown.paper() ? 0x30FFFFFF : 0x22FFFFFF);
        if (decoration != null) {
            decoration.draw(context, this);
            return;
        }

        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        Text label = getMessage();
        int iconWidth = icon == null ? 0 : icon.width() + 5;
        int tx = getX() + (width - iconWidth - font.getWidth(label) + 1) / 2;
        // Light labels with their dark shadow on the green and red buttons, dark ones with a light shadow on the others
        boolean light = shown == Kind.GREEN || shown == Kind.RED || shown == Kind.SCREEN || shown == Kind.PAPER_GREEN || shown == Kind.PAPER_TEAL;
        boolean ink = shown == Kind.PAPER || shown == Kind.PAPER_OFF;
        int ty = getY() + (ink ? (height - 7) / 2 + (height - 7) % 2 : (height - 8) / 2) + (held ? 1 : 0);
        if (ink) {
            // Ink on paper: no shadow
            context.drawText(font, label, tx, ty, shown == Kind.PAPER_OFF ? 0xFF8AA3A6 : 0xFF1E3A40, false);
            return;
        }
        int colour = shown == Kind.SCREEN ? (active ? 0xFFE0EEF3 : 0xFF5E5C88) : light ? 0xFFFFFFFF : shown == Kind.OFF ? 0xFF5E5E5E : ramp.outline();
        int shade = light ? (colour & 0xFCFCFC) >> 2 | 0xFF000000 : shown == Kind.OFF ? 0xFFC8C8C8 : shown.ramp.hi();
        if (icon != null) {
            for (int row = 0; row < icon.rows.length; row++) {
                for (int col = 0; col < icon.width(); col++) {
                    if (icon.rows[row].charAt(col) != '#') continue;
                    PartyGui.pixel(context, tx + col + 1, ty + row + 1, shade);
                }
            }
            for (int row = 0; row < icon.rows.length; row++) {
                for (int col = 0; col < icon.width(); col++) {
                    if (icon.rows[row].charAt(col) == '#') PartyGui.pixel(context, tx + col, ty + row, colour);
                }
            }
            tx += iconWidth;
        }
        if (light) {
            context.drawText(font, label, tx, ty, colour, true);
        } else {
            context.drawText(font, label, tx + 1, ty + 1, shade, false);
            context.drawText(font, label, tx, ty, colour, false);
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
