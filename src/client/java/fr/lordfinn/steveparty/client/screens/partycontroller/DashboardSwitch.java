package fr.lordfinn.steveparty.client.screens.partycontroller;

import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.UiText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;

import static fr.lordfinn.steveparty.client.screens.partycontroller.DashboardStyle.*;

/**
 * A switch of the dashboard (the practice round, Restrict dice, the infinite storage): its state, then the switch
 * (green and to the right when on). A click clicks its handler's button.
 */
final class DashboardSwitch extends PressableWidget {
    private final Dashboard dashboard;
    private final boolean on;
    private final int button;

    DashboardSwitch(Dashboard dashboard, int x, int y, int width, int height, Text message, boolean on, int button) {
        super(x, y, width, height, message);
        this.dashboard = dashboard;
        this.on = on;
        this.button = button;
    }

    @Override
    public void onPress() {
        dashboard.click(button);
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int sx = getX() + width - 24, sy = getY() + 3;
        ConsolePaint.pill(context, sx, sy, 24, 12, on ? SWITCH_ON : SWITCH_OFF, true);
        ConsolePaint.disc(context, sx + (on ? 13 : 1), sy + 1, 10, KNOB);
        if (active && (isHovered() || isFocused())) ConsolePaint.highlight(context, sx - 1, sy - 1, 26, 14, -1, WHITE, 0);
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        // Its state, up to 4 px before the switch
        UiText.line(context, font, getMessage(), getX(), getY() + 5, Math.max(0, width - 27), !active ? INK_DIM : on ? INK_GREEN : INK_SOFT, true);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
