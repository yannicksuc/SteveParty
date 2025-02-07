package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.payloads.custom.SaveStencilPayload;
import fr.lordfinn.steveparty.screen_handlers.StencilMakerScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.awt.*;

public class StencilMakerScreen extends HandledScreen<StencilMakerScreenHandler> {
    private static final Identifier BACKGROUND_TEXTURE = Steveparty.id("textures/gui/stencil_maker.png");
    private static final Identifier STENCIL_TEXTURE =  Steveparty.id("textures/item/stencil.png");
    private static final Identifier BUTTONS =  Steveparty.id("textures/gui/stencil_maker_buttons.png");

    private int stencilX, stencilY; // Position of the stencil
    private int bgX, bgY;

    private static int pixelSize = 8;
    private static int widthUnit = 32;

    private static byte [] shape;

    public StencilMakerScreen(StencilMakerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = pixelSize * widthUnit;
        this.backgroundHeight = pixelSize * widthUnit;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        bgX = centerX - pixelSize * widthUnit / 2;
        bgY = centerY - pixelSize * widthUnit / 2;
        stencilX = bgX + pixelSize * widthUnit / 4;
        stencilY = bgY + pixelSize * widthUnit / 4;
        shape = StencilItem.getShape(handler.getBlockEntity().getStencil()).clone();
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(RenderLayer::getGuiTexturedOverlay, BACKGROUND_TEXTURE, bgX, bgY, backgroundWidth, backgroundHeight, backgroundWidth, backgroundHeight, backgroundWidth, backgroundHeight);
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                if (shape[i * 16 + j] == 1) continue;
                context.drawTexture(RenderLayer::getGuiTexturedOverlay, STENCIL_TEXTURE, (stencilX + i * pixelSize), (stencilY + j * pixelSize), i * pixelSize, j * pixelSize, (int)pixelSize, (int)pixelSize, backgroundWidth / 2, backgroundHeight / 2);
            }
        }

        int buttonX = this.width / 2 - 32;
        int buttonY = bgY + (pixelSize * widthUnit / 4) * 3 + pixelSize * 3;
        boolean isHovering = isSaveButtonHovered(mouseX, mouseY);

        if (isHovering) {
            context.drawTexture(RenderLayer::getGuiTexturedOverlay, BUTTONS, buttonX, buttonY, 0, 16, 64, 16, 64, 64);
        } else {
            context.drawTexture(RenderLayer::getGuiTexturedOverlay, BUTTONS, buttonX, buttonY, 0, 0, 64, 16, 64, 64);
        }

        Text text = Text.translatable("gui.steveparty.stencil_save");
        int textWidth = textRenderer.getWidth(text);
        context.drawText(textRenderer, text, this.width / 2 - textWidth / 2, bgY + (pixelSize * widthUnit / 4) * 3 + pixelSize * 3 + 4, Color.WHITE.getRGB(), true);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isSaveButtonHovered(mouseX, mouseY)) {
            StencilItem.setShape(shape, handler.getBlockEntity().getStencil());
            SaveStencilPayload payload = new SaveStencilPayload(shape, handler.getBlockEntity().getPos());
            ClientPlayNetworking.send(payload);
            playClickSound();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isSaveButtonHovered(double mouseX, double mouseY) {
        int buttonX = this.width / 2 - 32;
        int buttonY = bgY + (pixelSize * widthUnit / 4) * 3 + pixelSize * 3;
        return mouseX >= buttonX && mouseX < buttonX + 64 && mouseY >= buttonY && mouseY < buttonY + 16;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isInsideStencil(mouseX, mouseY)) {
            int pixelX = (int) ((mouseX - stencilX) * 16 / ((double) (pixelSize * widthUnit) / 2));
            int pixelY = (int) ((mouseY - stencilY) * 16 / ((double) (pixelSize * widthUnit) / 2));
            onStencilPixelClicked(pixelX, pixelY, button);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private boolean isInsideStencil(double mouseX, double mouseY) {
        return mouseX >= stencilX && mouseX < stencilX + ((double) (pixelSize * widthUnit) / 2) &&
                mouseY >= stencilY && mouseY < stencilY + ((double) (pixelSize * widthUnit) / 2);
    }

    private void onStencilPixelClicked(int pixelX, int pixelY, int button) {
        byte currentState = shape[pixelX * 16 + pixelY];
        byte newState = (byte) (button == 0 ? 1 : 0);

        if (currentState != newState) {
            shape[pixelX * 16 + pixelY] = newState;
            playMetalInteractionSound();
        }
    }

    private void playMetalInteractionSound() {
        if (client != null)
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_ANVIL_BREAK, 1.0F, 0.5f));
    }

    private void playClickSound() {
        if (client != null)
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_ANVIL_USE, 1.0F, 1f));
    }
}
