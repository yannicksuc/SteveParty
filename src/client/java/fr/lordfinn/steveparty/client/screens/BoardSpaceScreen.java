package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.screen_handlers.custom.BoardSpaceScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public class BoardSpaceScreen extends CartridgeContainerScreen<BoardSpaceScreenHandler> {
    private static final Identifier TEXTURE = Steveparty.id("textures/gui/tile.png");
    private static final List<Identifier> TEXTURES_OVERLAY = List.of(
            Steveparty.id("textures/gui/tile-overlay-0.png"),
            Steveparty.id("textures/gui/tile-overlay-1.png"),
            Steveparty.id("textures/gui/tile-overlay-2.png"),
            Steveparty.id("textures/gui/tile-overlay-3.png"),
            Steveparty.id("textures/gui/tile-overlay-4.png"),
            Steveparty.id("textures/gui/tile-overlay-5.png"),
            Steveparty.id("textures/gui/tile-overlay-6.png"),
            Steveparty.id("textures/gui/tile-overlay-7.png"),
            Steveparty.id("textures/gui/tile-overlay-8.png"),
            Steveparty.id("textures/gui/tile-overlay-9.png"),
            Steveparty.id("textures/gui/tile-overlay-10.png"),
            Steveparty.id("textures/gui/tile-overlay-11.png"),
            Steveparty.id("textures/gui/tile-overlay-12.png"),
            Steveparty.id("textures/gui/tile-overlay-13.png"),
            Steveparty.id("textures/gui/tile-overlay-14.png"),
            Steveparty.id("textures/gui/tile-overlay-15.png")
    );
    private static final Identifier SIMPLE_TEXTURE = Steveparty.id("textures/gui/simple_tile.png");

    private final boolean isSingle;

    public BoardSpaceScreen(BoardSpaceScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title, 183);
        isSingle = this.handler.isSingle();
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        super.drawBackground(context, delta, mouseX, mouseY);
        if (isSingle) return;
        int activeSlot = this.handler.getActiveSlot();
        if (activeSlot >= 0 && activeSlot < TEXTURES_OVERLAY.size()) {
            Identifier overlayTexture = TEXTURES_OVERLAY.get(activeSlot);
            int x = (width - backgroundWidth) / 2;
            int y = (height - backgroundHeight) / 2;
            context.drawTexture(RenderLayer::getGuiTexturedOverlay, overlayTexture, x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    public Identifier getTexture() {
        return isSingle ? SIMPLE_TEXTURE : TEXTURE;
    }
}
