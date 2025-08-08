package fr.lordfinn.steveparty.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.screen_handlers.custom.TradingStallScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class TradingStallScreen extends HandledScreen<TradingStallScreenHandler> {
    private static final Identifier TEXTURE = Steveparty.id("textures/gui/trading_stall.png");

    public TradingStallScreen(TradingStallScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 204;
        this.backgroundHeight = 187;
        this.playerInventoryTitleY = this.backgroundHeight - 93;
        this.playerInventoryTitleX += 4;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        int x = (this.width - this.backgroundWidth) / 2; //12 is the size of the additional hiding trader slot
        int y = (this.height - this.backgroundHeight) / 2;
        context.drawTexture(RenderLayer::getGuiOpaqueTexturedBackground,
                TEXTURE, x, y, 0,0,
                this.backgroundWidth, this.backgroundHeight, 256, 256);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        //context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 0xfff1f1f1, false);
        context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, 4210752, false);
    }
}
