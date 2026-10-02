package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.payloads.custom.DicePromptAnswerPayload;
import fr.lordfinn.steveparty.payloads.custom.DicePromptPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The picker of a dice prompt ({@link DicePrompts}): the face of a Choice die, the result kept of a Lucky die, keep or
 * reroll, the token to swap with. A grid of icons (faces) or a list of rows (icon and label); a bar shows the time
 * left, after which the server takes the highlighted option. Closing it answers nothing: hitting the die shows it
 * again.
 */
public class DicePickScreen extends Screen {
    private static final int CELL = 26, GAP = 3, COLUMNS = 6;
    private static final int ROW_WIDTH = 170, ROW_HEIGHT = 22;
    private static final int PAD = 8, TITLE_HEIGHT = 14, BAR_HEIGHT = 3;
    private static final int PANEL = 0xE0181320, PANEL_BORDER = 0xFF4A2F78, PANEL_LIGHT = 0xFF7A5AA8;
    private static final int CELL_FILL = 0xFF2A1840, CELL_HOVER = 0xFF4A2F78, CELL_DEFAULT = 0xFF3A2A18;
    private static final int CELL_BORDER = 0xFF140A20, CELL_BORDER_HOVER = 0xFFFFFFFF, CELL_BORDER_DEFAULT = 0xFFFFD35A;

    private final DicePromptPayload prompt;
    private final long openedAt = Util.getMeasuringTimeMs();
    private int panelX, panelY, panelWidth, panelHeight;

    public DicePickScreen(DicePromptPayload prompt) {
        super(prompt.title());
        this.prompt = prompt;
    }

    public int promptId() {
        return prompt.id();
    }

    private List<DicePrompts.Option> options() {
        return prompt.options();
    }

    @Override
    protected void init() {
        int count = options().size();
        int contentWidth, contentHeight;
        if (prompt.list()) {
            contentWidth = ROW_WIDTH;
            contentHeight = count * (ROW_HEIGHT + GAP) - GAP;
        } else {
            int columns = Math.min(COLUMNS, count), rows = (count + COLUMNS - 1) / COLUMNS;
            contentWidth = columns * (CELL + GAP) - GAP;
            contentHeight = rows * (CELL + GAP) - GAP;
        }
        panelWidth = Math.max(contentWidth, textRenderer.getWidth(title)) + 2 * PAD;
        panelHeight = PAD + TITLE_HEIGHT + contentHeight + PAD + BAR_HEIGHT + 4;
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
    }

    /** @return {x, y, width, height} of an option. */
    private int[] bounds(int index) {
        int top = panelY + PAD + TITLE_HEIGHT;
        if (prompt.list()) {
            return new int[]{panelX + (panelWidth - ROW_WIDTH) / 2, top + index * (ROW_HEIGHT + GAP), ROW_WIDTH, ROW_HEIGHT};
        }
        int count = options().size();
        int row = index / COLUMNS, column = index % COLUMNS;
        int inRow = Math.min(COLUMNS, count - row * COLUMNS);
        int rowWidth = inRow * (CELL + GAP) - GAP;
        return new int[]{panelX + (panelWidth - rowWidth) / 2 + column * (CELL + GAP), top + row * (CELL + GAP), CELL, CELL};
    }

    private int optionAt(double mouseX, double mouseY) {
        for (int i = 0; i < options().size(); i++) {
            int[] b = bounds(i);
            if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) return i;
        }
        return -1;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // No blur: the die and the board stay readable behind the picker
        context.fill(0, 0, width, height, 0x50000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, PANEL_BORDER);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 1, PANEL_LIGHT);
        context.drawCenteredTextWithShadow(textRenderer, title, panelX + panelWidth / 2, panelY + PAD - 2, 0xFFFFE08C);

        int hovered = optionAt(mouseX, mouseY);
        for (int i = 0; i < options().size(); i++) {
            DicePrompts.Option option = options().get(i);
            int[] b = bounds(i);
            boolean isDefault = i == prompt.defaultIndex() && prompt.list();
            int border = i == hovered ? CELL_BORDER_HOVER : isDefault ? CELL_BORDER_DEFAULT : CELL_BORDER;
            context.fill(b[0] - 1, b[1] - 1, b[0] + b[2] + 1, b[1] + b[3] + 1, border);
            context.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], i == hovered ? CELL_HOVER : isDefault ? CELL_DEFAULT : CELL_FILL);
            if (prompt.list()) {
                if (!option.icon().isEmpty()) context.drawItem(option.icon(), b[0] + 3, b[1] + 3);
                context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(option.label(), ROW_WIDTH - 28).getString(),
                        b[0] + 24, b[1] + 7, 0xFFFFFFFF);
            } else if (!option.icon().isEmpty()) {
                context.drawItem(option.icon(), b[0] + 5, b[1] + 5);
            } else {
                context.drawCenteredTextWithShadow(textRenderer, option.label(), b[0] + b[2] / 2, b[1] + 9, 0xFFFFFFFF);
            }
        }

        // Time left
        float left = prompt.timeoutTicks() <= 0 ? 0f
                : 1f - MathHelper.clamp((Util.getMeasuringTimeMs() - openedAt) / (prompt.timeoutTicks() * 50f), 0f, 1f);
        int barX = panelX + PAD, barY = panelY + panelHeight - PAD + 2, barWidth = panelWidth - 2 * PAD;
        context.fill(barX, barY, barX + barWidth, barY + BAR_HEIGHT, 0xFF505050);
        context.fill(barX, barY, barX + Math.round(barWidth * left), barY + BAR_HEIGHT, left < 0.25f ? 0xFFE0703A : 0xFFFFD35A);

        if (hovered >= 0 && !prompt.list()) context.drawTooltip(textRenderer, options().get(hovered).label(), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = optionAt(mouseX, mouseY);
        if (button == 0 && index >= 0) {
            pick(index);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** The number keys pick the first options. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int index = keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9 ? keyCode - GLFW.GLFW_KEY_1
                : keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode <= GLFW.GLFW_KEY_KP_9 ? keyCode - GLFW.GLFW_KEY_KP_1 : -1;
        if (index >= 0 && index < options().size()) {
            pick(index);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void pick(int index) {
        ClientPlayNetworking.send(new DicePromptAnswerPayload(prompt.id(), index));
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** A prompt from the server: shown unless the player is busy in another screen; an empty one closes its picker. */
    public static void onPayload(MinecraftClient client, DicePromptPayload payload) {
        if (payload.isClose()) {
            if (client.currentScreen instanceof DicePickScreen screen && screen.promptId() == payload.id()) client.setScreen(null);
            return;
        }
        if (client.world == null) return;
        if (client.currentScreen == null || client.currentScreen instanceof DicePickScreen) client.setScreen(new DicePickScreen(payload));
    }
}
