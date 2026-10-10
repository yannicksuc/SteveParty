package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.screen_handlers.custom.MiniGamesCatalogueScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class MiniGamesCatalogueScreen extends HandledScreen<MiniGamesCatalogueScreenHandler> {
    private static final Identifier TEXTURE = Steveparty.id("textures/gui/mini-games-catalogue.png");

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
        context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);
    }

    /** The vanilla titles: the catalogue's across the top, the inventory's up to its panel's right padding. */
    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        UiText.line(context, textRenderer, title, titleX, titleY, backgroundWidth - 8 - titleX, 0x404040, false);
        // The inventory panel runs from x 36 to 212 in the texture
        UiText.line(context, textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY,
                212 - 8 - playerInventoryTitleX, 0x404040, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
