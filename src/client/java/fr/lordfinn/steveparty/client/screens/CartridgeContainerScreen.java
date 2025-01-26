package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.screen_handlers.CartridgeContainerScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public abstract class CartridgeContainerScreen<T extends CartridgeContainerScreenHandler> extends HandledScreen<T> {
    public CartridgeContainerScreen(T handler, PlayerInventory inventory, Text title, int backgroundHeight) {
        super(handler, inventory, title);
        this.backgroundHeight = backgroundHeight;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        context.drawTexture(RenderLayer::getGuiOpaqueTexturedBackground, getTexture(), x, y, 0f, 0f, backgroundWidth, backgroundHeight, 256, 256);
    }

    public abstract Identifier getTexture();

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void init() {
        super.init();
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }
}
