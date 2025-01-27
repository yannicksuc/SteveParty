package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.screen_handlers.TileScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public class TileScreen extends CartridgeContainerScreen<TileScreenHandler> {
    private static final Identifier TEXTURE = Identifier.of(Steveparty.MOD_ID, "textures/gui/tile.png");
    private static final List<Identifier> TEXTURES_OVERLAY = List.of(
            Identifier.of(Steveparty.MOD_ID, "textures/gui/tile-overlay-0.png"),
            Identifier.of(Steveparty.MOD_ID, "textures/gui/tile-overlay-1.png"),
            Identifier.of(Steveparty.MOD_ID, "textures/gui/tile-overlay-2.png"),
            Identifier.of(Steveparty.MOD_ID, "textures/gui/tile-overlay-3.png"),
            Identifier.of(Steveparty.MOD_ID, "textures/gui/tile-overlay-4.png"),
            Identifier.of(Steveparty.MOD_ID, "textures/gui/tile-overlay-5.png"),
            Identifier.of(Steveparty.MOD_ID, "textures/gui/tile-overlay-6.png"),
            Identifier.of(Steveparty.MOD_ID, "textures/gui/tile-overlay-7.png"),
            Identifier.of(Steveparty.MOD_ID, "textures/gui/tile-overlay-8.png"),
            Identifier.of(Steveparty.MOD_ID, "textures/gui/tile-overlay-9.png"),
            Identifier.of(Steveparty.MOD_ID, "textures/gui/tile-overlay-10.png"),
            Identifier.of(Steveparty.MOD_ID, "textures/gui/tile-overlay-11.png"),
            Identifier.of(Steveparty.MOD_ID, "textures/gui/tile-overlay-12.png"),
            Identifier.of(Steveparty.MOD_ID, "textures/gui/tile-overlay-13.png"),
            Identifier.of(Steveparty.MOD_ID, "textures/gui/tile-overlay-14.png"),
            Identifier.of(Steveparty.MOD_ID, "textures/gui/tile-overlay-15.png")
    );

    public TileScreen(TileScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title, 183);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        super.drawBackground(context, delta, mouseX, mouseY);
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
        return TEXTURE;
    }
}
