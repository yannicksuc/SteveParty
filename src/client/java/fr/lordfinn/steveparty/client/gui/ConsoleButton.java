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
        GOLD(ConsolePaint.Ramp.of(0x3b2600, 0xfff2a8, 0xffc52e, 0xb5761a)),
        GREEN(ConsolePaint.Ramp.of(0x08270a, 0xa6ef8a, 0x46ae2e, 0x1f6a14)),
        RED(ConsolePaint.Ramp.of(0x33030a, 0xff8f8f, 0xd9283b, 0x8e1022)),
        OFF(ConsolePaint.Ramp.of(0x3a3a3a, 0xd0d0d0, 0xa8a8a8, 0x808080));

        final ConsolePaint.Ramp ramp;

        Kind(ConsolePaint.Ramp ramp) {
            this.ramp = ramp;
        }
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
        Kind shown = active ? kind : Kind.OFF;
        boolean highlighted = active && (isHovered() || isFocused());
        boolean held = highlighted && isHovered() && MinecraftClient.getInstance().mouse.wasLeftButtonClicked();
        ConsolePaint.Ramp ramp = shown.ramp;
        if (held) ramp = new ConsolePaint.Ramp(ramp.outline(), ramp.shadow(), ramp.body(), ramp.hi());
        ConsolePaint.box(context, getX(), getY(), width, height, ramp, 1, 1);
        if (highlighted && !held) context.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0x22FFFFFF);
        if (highlighted) context.drawBorder(getX(), getY(), width, height, 0xFFFFFFFF);

        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        Text label = getMessage();
        int iconWidth = icon == null ? 0 : icon.width() + 5;
        int tx = getX() + (width - iconWidth - font.getWidth(label) + 1) / 2;
        int ty = getY() + (height - 8) / 2 + (held ? 1 : 0);
        // Light labels with their dark shadow on the green and red buttons, dark ones with a light shadow on the others
        boolean light = shown == Kind.GREEN || shown == Kind.RED;
        int colour = light ? 0xFFFFFFFF : shown == Kind.OFF ? 0xFF5E5E5E : ramp.outline();
        int shade = light ? 0xFF3F3F3F : shown == Kind.OFF ? 0xFFC8C8C8 : shown.ramp.hi();
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
