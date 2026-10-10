package fr.lordfinn.steveparty.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.screen_handlers.custom.CashRegisterScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class CashRegisterScreen extends HandledScreen<CashRegisterScreenHandler> {
    private static final Identifier TEXTURE = Steveparty.id("textures/gui/cash_register.png");

    public CashRegisterScreen(CashRegisterScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 168;
        this.playerInventoryTitleY = this.backgroundHeight - 93;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        int x = (this.width - this.backgroundWidth) / 2; //12 is the size of the additional boxed trader slot
        int y = (this.height - this.backgroundHeight) / 2;
        context.drawTexture(TEXTURE, x, y, 0,0,
                this.backgroundWidth, this.backgroundHeight, 256, 256);
    }
    /** The titles as vanilla places them, each on one line up to 8 px before the panel's right edge (too long, it scrolls). */
    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        UiText.line(context, textRenderer, title, titleX, titleY, backgroundWidth - 8 - titleX, 0x404040, false);
        UiText.line(context, textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY,
                backgroundWidth - 8 - playerInventoryTitleX, 0x404040, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
