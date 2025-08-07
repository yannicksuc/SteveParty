package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.screen_handlers.custom.TeleportationBookScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class TeleportationBookScreen extends HandledScreen<TeleportationBookScreenHandler> {
    protected static final Identifier TEXTURE = Steveparty.id("textures/gui/teleportation_book_gui.png");

    public TeleportationBookScreen(TeleportationBookScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 180;
        this.backgroundWidth = 146;
        this.titleY = 12;
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        this.titleX = (this.backgroundWidth - this.textRenderer.getWidth(this.title)) / 2;
        context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 0x8a060c, false);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        context.drawTexture(RenderLayer::getGuiOpaqueTexturedBackground, TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);
    }
}
