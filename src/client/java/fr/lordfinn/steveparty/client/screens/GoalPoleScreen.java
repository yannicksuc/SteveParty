package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBlockEntity;
import fr.lordfinn.steveparty.payloads.custom.GoalPolePayload;
import fr.lordfinn.steveparty.screen_handlers.custom.GoalPoleScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import static fr.lordfinn.steveparty.sounds.ModSounds.CLOSE_TILE_GUI_SOUND_EVENT;
import static fr.lordfinn.steveparty.sounds.ModSounds.OPEN_TILE_GUI_SOUND_EVENT;

public class GoalPoleScreen extends HandledScreen<GoalPoleScreenHandler> {
    private static final Identifier TEXTURE = Steveparty.id("textures/gui/book.png");

    private TextFieldWidget valueField;
    private ButtonWidget comparatorButton;
    private ButtonWidget cancelButton;
    private ButtonWidget validateButton;

    private GoalPoleBlockEntity.Comparator[] comparators = GoalPoleBlockEntity.Comparator.values();
    private int currentComparatorIndex = 0;
    private boolean openSoundPlayed = false;

    public GoalPoleScreen(GoalPoleScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 180;
        this.backgroundWidth = 146;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        // Comparator button (init() runs again on every resize: keep the current selection)
        if (!openSoundPlayed) currentComparatorIndex = handler.getComparator().ordinal();
        comparatorButton = ButtonWidget.builder(Text.literal(comparators[currentComparatorIndex].name()), button -> {
            currentComparatorIndex = (currentComparatorIndex + 1) % comparators.length;
            button.setMessage(Text.literal(comparators[currentComparatorIndex].name()));
        }).dimensions(x + 15, y + 30, backgroundWidth - 30, 20).build();
        addDrawableChild(comparatorButton);

        // Value text field
        String valueText = valueField != null ? valueField.getText() : Integer.toString(handler.getValue());
        valueField = new TextFieldWidget(textRenderer, x + 15, y + 70, backgroundWidth - 30, 20,
                Text.literal("Value"));
        valueField.setText(valueText);
        addDrawableChild(valueField);

        // Buttons
        int buttonWidth = (backgroundWidth - 35) / 2;
        int buttonHeight = 20;
        int buttonY = y + 100;

        cancelButton = ButtonWidget.builder(Text.translatable("gui.steveparty.cancel"), button -> close())
                .dimensions(x + 15, buttonY, buttonWidth, buttonHeight).build();
        addDrawableChild(cancelButton);

        validateButton = ButtonWidget.builder(Text.translatable("gui.steveparty.validate"), button -> {
            int valueInput = 0;
            try {
                valueInput = Integer.parseInt(valueField.getText());
            } catch (NumberFormatException ignored) {}
            GoalPolePayload payload = new GoalPolePayload(
                    handler.getPos(),
                    comparators[currentComparatorIndex],
                    valueInput
            );
            ClientPlayNetworking.send(payload);
            close();
        }).dimensions(x + 10 + buttonWidth + 10, buttonY, buttonWidth, buttonHeight).build();
        addDrawableChild(validateButton);

        // Play open sound once (not on every resize)
        if (!openSoundPlayed && client != null && client.player != null) {
            client.player.playSound(OPEN_TILE_GUI_SOUND_EVENT, 1.0F, 1.0F);
        }
        openSoundPlayed = true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        context.drawText(textRenderer, Text.literal("Comparator:"), x + 15, y + 20, 0x404040, false);
        context.drawText(textRenderer, Text.literal("Value:"), x + 15, y + 60, 0x404040, false);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(RenderLayer::getGuiOpaqueTexturedBackground, TEXTURE, x, y, 0f, 0f, backgroundWidth, backgroundHeight, 256, 256);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escape always closes; while typing a value, the inventory key must not close the screen
        if (keyCode != GLFW.GLFW_KEY_ESCAPE && keyCode != GLFW.GLFW_KEY_TAB && valueField != null && valueField.isActive()) {
            valueField.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (client != null && client.player != null) {
            client.player.playSound(CLOSE_TILE_GUI_SOUND_EVENT, 1.0F, 1.0F);
        }
        super.close();
    }
}
