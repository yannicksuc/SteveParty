package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.screen_handlers.custom.HopSwitchScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class HopSwitchScreen extends CartridgeContainerScreen<HopSwitchScreenHandler> {
    private static final Identifier TEXTURE = Steveparty.id("textures/gui/hop_switch.png");
    // Button position (relative to GUI)
    private final int buttonX = 72;
    private final int buttonY = 14;
    private final int buttonSize = 16; // 16x16

    // Hover overlay texture offsets
    private static final int HOVER_OVERLAY_X = 16; // x:16
    private static final int HOVER_OVERLAY_Y = 137; // y:137

    // Base textures (3 modes, stacked vertically at x:0, y:137+)
    private static final int BASE_X = 0;
    private static final int BASE_Y = 137;

    public HopSwitchScreen(HopSwitchScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title, 136);
    }

    @Override
    protected void init() {
        super.init();
    }

    private Text getModeText() {
        return Text.of("Mode: " + handler.getMode().name());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        drawModeButton(context, mouseX, mouseY);

        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    private void drawModeButton(DrawContext context, int mouseX, int mouseY) {
        int screenX = this.x + buttonX;
        int screenY = this.y + buttonY;

        // Pick texture Y offset based on mode (0,1,2)
        int modeIndex = handler.getMode().getId();
        int textureY = BASE_Y + (modeIndex * buttonSize);

        // Draw base mode icon
        context.drawTexture(RenderLayer::getGuiTexturedOverlay, TEXTURE, screenX, screenY, BASE_X, textureY, buttonSize, buttonSize,256, 256);

        // Draw hover overlay if mouse is inside
        if (isPointWithinBounds(buttonX, buttonY, buttonSize, buttonSize, mouseX, mouseY)) {
            context.drawTexture(RenderLayer::getGuiTexturedOverlay, TEXTURE, screenX, screenY, HOVER_OVERLAY_X, HOVER_OVERLAY_Y, buttonSize, buttonSize,256, 256);

            // Tooltip
            context.drawTooltip(
                    client.textRenderer,
                    Text.translatable("message.steveparty.hopswitch.mode." + modeIndex),
                    mouseX,
                    mouseY
            );
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // left click
            if (isPointWithinBounds(buttonX, buttonY, buttonSize, buttonSize, mouseX, mouseY)) {
                MinecraftClient.getInstance().interactionManager.clickButton(handler.syncId, 0);
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.6F));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }


    @Override
    public Identifier getTexture() {
        return TEXTURE;
    }
}
