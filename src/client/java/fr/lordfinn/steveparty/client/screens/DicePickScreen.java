package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.PartyGui;
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
 * reroll, the token to swap with. Painted with the mod's GUI kit ({@link PartyGui}): a light panel under a gold title
 * plate, bevelled buttons (a grid of face buttons, or a list of rows of icon and label), a sunken time bar after which
 * the server takes the default option.
 * <p>
 * The dice of a Choice Double / Triple Dice are picked one after the other, shown as one section per die: the faces
 * already picked (gold), the die to pick now (its face buttons), the dice still to come. Closing the picker answers
 * nothing: hitting the die shows it again.
 */
public class DicePickScreen extends Screen {
    private static final int CELL = 22, GAP = 2, COLUMNS = 6;
    private static final int ROW_WIDTH = 176, ROW_HEIGHT = 22;
    private static final int PAD = 10, TOP = 16, SECTION_GAP = 5, LABEL_GAP = 8, BAR_HEIGHT = 5;
    private static final int BAR_FILL = 0xFFFFC52E, BAR_LOW = 0xFFE0703A, BAR_EMPTY = 0xFF8B8B8B;
    /** Clicks and keys are ignored this long after the picker opens: the click (or held key) that answered the
     *  previous picker of the same roll (the next die of a Double / Triple Dice) does not answer this one. */
    private static final long INPUT_DELAY_MS = 250;

    /** A prompt received while the player was in another screen (chat, inventory): shown once it is closed. */
    private static DicePromptPayload waiting;
    private static long waitingSince;

    private final DicePromptPayload prompt;
    private final long receivedAt;
    private final long shownAt = Util.getMeasuringTimeMs();
    private int panelX, panelY, panelWidth, panelHeight;
    /** Where the sections start, the width of their labels, and the top of the section being picked. */
    private int contentX, contentY, labelWidth, currentY;

    public DicePickScreen(DicePromptPayload prompt) {
        this(prompt, Util.getMeasuringTimeMs());
    }

    private DicePickScreen(DicePromptPayload prompt, long receivedAt) {
        super(prompt.title());
        this.prompt = prompt;
        this.receivedAt = receivedAt;
    }

    public int promptId() {
        return prompt.id();
    }

    private List<DicePrompts.Option> options() {
        return prompt.options();
    }

    /** One section per die (a Choice Double / Triple Dice). */
    private boolean sectioned() {
        return prompt.steps() > 1 && !prompt.list();
    }

    private int step() {
        return prompt.picked().size();
    }

    private Text sectionLabel(int index) {
        return Text.translatable("gui.steveparty.dice_prompt.die", index + 1);
    }

    private int optionsHeight() {
        int count = options().size();
        if (prompt.list()) return count * (ROW_HEIGHT + GAP) - GAP;
        int rows = (count + COLUMNS - 1) / COLUMNS;
        return rows * (CELL + GAP) - GAP;
    }

    @Override
    protected void init() {
        int count = options().size();
        labelWidth = 0;
        if (sectioned()) {
            for (int i = 0; i < prompt.steps(); i++) labelWidth = Math.max(labelWidth, textRenderer.getWidth(sectionLabel(i)));
            labelWidth += LABEL_GAP;
        }
        int optionsWidth = prompt.list() ? ROW_WIDTH : Math.min(COLUMNS, count) * (CELL + GAP) - GAP;
        int contentWidth = labelWidth + optionsWidth;
        int contentHeight = optionsHeight();
        if (sectioned()) contentHeight += (prompt.steps() - 1) * (CELL + SECTION_GAP);
        int titleWidth = textRenderer.getWidth(title) + 20;
        panelWidth = Math.max(contentWidth, titleWidth + 12) + 2 * PAD;
        panelHeight = TOP + contentHeight + 8 + BAR_HEIGHT + PAD;
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        contentX = panelX + (panelWidth - contentWidth) / 2;
        contentY = panelY + TOP;
        currentY = contentY + (sectioned() ? step() * (CELL + SECTION_GAP) : 0);
    }

    /** @return {x, y, width, height} of an option. */
    private int[] bounds(int index) {
        int left = contentX + labelWidth;
        if (prompt.list()) return new int[]{left, currentY + index * (ROW_HEIGHT + GAP), ROW_WIDTH, ROW_HEIGHT};
        int row = index / COLUMNS, column = index % COLUMNS;
        return new int[]{left + column * (CELL + GAP), currentY + row * (CELL + GAP), CELL, CELL};
    }

    private int optionAt(double mouseX, double mouseY) {
        for (int i = 0; i < options().size(); i++) {
            int[] b = bounds(i);
            if (HitArea.contains(mouseX, mouseY, b[0], b[1], b[2], b[3])) return i;
        }
        return -1;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // No blur: the dice and the board stay readable behind the picker
        context.fill(0, 0, width, height, 0x50000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        PartyGui.panel(context, panelX, panelY, panelWidth, panelHeight, PartyGui.PANEL);
        PartyGui.titlePlate(context, textRenderer, panelX + panelWidth / 2, panelY - 11, 0, title, PartyGui.BUTTON_SELECTED);

        int hovered = optionAt(mouseX, mouseY);
        Text tooltip = null;
        if (sectioned()) {
            for (int i = 0; i < prompt.steps(); i++) {
                int y = i <= step() ? contentY + i * (CELL + SECTION_GAP)
                        : currentY + optionsHeight() + SECTION_GAP + (i - step() - 1) * (CELL + SECTION_GAP);
                int color = i == step() ? PartyGui.TEXT_DARK : PartyGui.TEXT_SOFT;
                context.drawText(textRenderer, sectionLabel(i), contentX, y + (CELL - 8) / 2, color, false);
                int x = contentX + labelWidth;
                if (i < step()) {
                    // A face already picked
                    DicePrompts.Option done = prompt.picked().get(i);
                    PartyGui.button(context, x, y, CELL, CELL, PartyGui.BUTTON_SELECTED, false);
                    drawIcon(context, done, x, y);
                    PartyGui.statusIcon(context, x + CELL + 4, y + (CELL - 7) / 2, true);
                    if (HitArea.contains(mouseX, mouseY, x, y, CELL, CELL)) tooltip = done.label();
                } else if (i > step()) {
                    // A die still to come
                    PartyGui.button(context, x, y, CELL, CELL, PartyGui.BUTTON_DISABLED, false);
                    context.drawCenteredTextWithShadow(textRenderer, Text.literal("?"), x + CELL / 2, y + 7, 0xFFFFFFFF);
                }
            }
        }

        for (int i = 0; i < options().size(); i++) {
            DicePrompts.Option option = options().get(i);
            int[] b = bounds(i);
            boolean isDefault = i == prompt.defaultIndex() && prompt.list();
            PartyGui.Theme theme = isDefault ? PartyGui.BUTTON_SELECTED : PartyGui.BUTTON;
            if (i == hovered) theme = theme.brighter();
            PartyGui.button(context, b[0], b[1], b[2], b[3], theme, false);
            if (prompt.list()) {
                if (!option.icon().isEmpty()) context.drawItem(option.icon(), b[0] + 3, b[1] + 3);
                context.drawText(textRenderer, textRenderer.trimToWidth(option.label(), ROW_WIDTH - 30).getString(),
                        b[0] + 24, b[1] + 7, PartyGui.TEXT_DARK, false);
            } else {
                drawIcon(context, option, b[0], b[1]);
                if (i == hovered) tooltip = option.label();
            }
        }

        // Time left
        float left = prompt.timeoutTicks() <= 0 ? 0f
                : 1f - MathHelper.clamp((Util.getMeasuringTimeMs() - receivedAt) / (prompt.timeoutTicks() * 50f), 0f, 1f);
        int barX = panelX + PAD, barY = panelY + panelHeight - PAD - BAR_HEIGHT + 2, barWidth = panelWidth - 2 * PAD;
        PartyGui.inset(context, barX, barY, barWidth, BAR_HEIGHT, BAR_EMPTY, false, false);
        context.fill(barX + 1, barY + 1, barX + 1 + Math.round((barWidth - 1) * left), barY + BAR_HEIGHT, left < 0.25f ? BAR_LOW : BAR_FILL);

        if (tooltip != null) context.drawTooltip(textRenderer, tooltip, mouseX, mouseY);
    }

    /** The option's icon in a cell (its label when it has none). */
    private void drawIcon(DrawContext context, DicePrompts.Option option, int x, int y) {
        if (!option.icon().isEmpty()) context.drawItem(option.icon(), x + 3, y + 3);
        else context.drawCenteredTextWithShadow(textRenderer, option.label(), x + CELL / 2, y + 7, 0xFFFFFFFF);
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
        if (Util.getMeasuringTimeMs() - shownAt < INPUT_DELAY_MS) return;
        ClientPlayNetworking.send(new DicePromptAnswerPayload(prompt.id(), index));
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /**
     * A prompt from the server: shown at once, or, if the player is busy in another screen (chat, inventory), as soon
     * as that screen is closed (never dropped: the next die of a Double / Triple Dice would take a face at random once
     * its time is over). An empty one closes its picker.
     */
    public static void onPayload(MinecraftClient client, DicePromptPayload payload) {
        if (payload.isClose()) {
            if (client.currentScreen instanceof DicePickScreen screen && screen.promptId() == payload.id()) client.setScreen(null);
            if (waiting != null && waiting.id() == payload.id()) waiting = null;
            return;
        }
        if (client.world == null) return;
        waiting = payload;
        waitingSince = Util.getMeasuringTimeMs();
        showWaiting(client);
    }

    /** Every client tick: the prompt received during another screen is shown once the player is free. */
    public static void showWaiting(MinecraftClient client) {
        if (waiting == null) return;
        if (client.world == null || Util.getMeasuringTimeMs() - waitingSince > waiting.timeoutTicks() * 50L) {
            waiting = null; // its time is over: the server answered it
            return;
        }
        if (client.currentScreen != null && !(client.currentScreen instanceof DicePickScreen)) return;
        client.setScreen(new DicePickScreen(waiting, waitingSince));
        waiting = null;
    }
}
