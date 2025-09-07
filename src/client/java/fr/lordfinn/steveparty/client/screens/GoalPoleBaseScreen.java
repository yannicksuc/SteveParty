package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.payloads.custom.GoalPoleBasePayload;
import fr.lordfinn.steveparty.screen_handlers.custom.GoalPoleBaseScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static fr.lordfinn.steveparty.sounds.ModSounds.CLOSE_TILE_GUI_SOUND_EVENT;
import static fr.lordfinn.steveparty.sounds.ModSounds.OPEN_TILE_GUI_SOUND_EVENT;

public class GoalPoleBaseScreen extends HandledScreen<GoalPoleBaseScreenHandler> {
    private static final Identifier TEXTURE = Steveparty.id("textures/gui/book.png");
    private TextFieldWidget selectorField;
    private TextFieldWidget goalField;
    private ButtonWidget cancelButton;
    private ButtonWidget validateButton;

    public GoalPoleBaseScreen(GoalPoleBaseScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 180;
        this.backgroundWidth = 146;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        // Création des champs de texte
        selectorField = new TextFieldWidget(textRenderer, x + 15, y + 30, backgroundWidth - 31, 20,
                Text.translatable("gui.steveparty.goal_pole_base.selector"));
        selectorField.setText(handler.getSelector());
        addDrawableChild(selectorField);

        goalField = new TextFieldWidget(textRenderer, x + 15, y + 70, backgroundWidth - 31, 20,
                Text.translatable("gui.steveparty.goal_pole_base.goal"));
        goalField.setText(handler.getGoal());
        addDrawableChild(goalField);
        // Création des boutons
        // Cancel button (left)
        int buttonWidth = (backgroundWidth - 35) / 2; // 10px left + 10px right + 10px margin between buttons
        int buttonHeight = 20;
        int buttonY = y + 140;
        cancelButton = ButtonWidget.builder(Text.translatable("gui.steveparty.cancel"), button -> close())
                .dimensions(x + 15, buttonY, buttonWidth, buttonHeight)
                .build();
        addDrawableChild(cancelButton);

        validateButton = ButtonWidget.builder(Text.translatable("gui.steveparty.validate"), button -> {
                    GoalPoleBasePayload payload = new GoalPoleBasePayload(
                            handler.getPos(),
                            selectorField.getText(),
                            goalField.getText()
                    );
                    ClientPlayNetworking.send(payload);
                    close();
                })
                .dimensions(x + 10 + buttonWidth + 10, buttonY, buttonWidth, buttonHeight)
                .build();
        addDrawableChild(validateButton);

        // Play open sound once when screen opens
        if (client != null && client.player != null) {
            client.player.playSound(
                    OPEN_TILE_GUI_SOUND_EVENT, // example sound
                    1.0F, // volume
                    1.0F  // pitch
            );
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        
        // Rendu des labels
        context.drawText(textRenderer, Text.translatable("gui.steveparty.goal_pole_base.selector").getString() + ":", x + 15, y + 20, 0x404040, false);
        context.drawText(textRenderer, Text.translatable("gui.steveparty.goal_pole_base.goal").getString() + ":", x + 15, y + 60, 0x404040, false);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(RenderLayer::getGuiOpaqueTexturedBackground, TEXTURE, x, y, 0f, 0f, backgroundWidth, backgroundHeight, 256, 256);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
    }

    @Override
    public void close() {
        if (client != null && client.player != null) {
            client.player.playSound(
                    CLOSE_TILE_GUI_SOUND_EVENT, // example sound
                    1.0F, // volume
                    1.0F  // pitch
            );
        }
        super.close();
    }
}
