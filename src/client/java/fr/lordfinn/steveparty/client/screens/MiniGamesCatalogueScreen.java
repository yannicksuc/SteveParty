package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.screen_handlers.MiniGamesCatalogueScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class MiniGamesCatalogueScreen extends HandledScreen<MiniGamesCatalogueScreenHandler> {
    private static final Identifier TEXTURE = Identifier.of(Steveparty.MOD_ID, "textures/gui/mini-games-catalogue.png");

    public MiniGamesCatalogueScreen(MiniGamesCatalogueScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 240;
        this.backgroundWidth = 248;
        this.playerInventoryTitleX = 44;
        this.playerInventoryTitleY = 148;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        context.drawTexture(RenderLayer::getGuiOpaqueTexturedBackground, TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);
    }
}
