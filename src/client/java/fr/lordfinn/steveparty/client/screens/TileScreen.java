package fr.lordfinn.steveparty.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.screens.TileScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public class TileScreen extends HandledScreen<TileScreenHandler> {
    // A path to the gui texture. In this example we use the texture from the dispenser

    private static final Identifier TEXTURE = Identifier.of("steveparty", "textures/gui/tile.png");
    private Function<Identifier, RenderLayer> func;
    // For versions before 1.21:
    // private static final Identifier TEXTURE = new Identifier("minecraft", "textures/gui/container/dispenser.png");

    public TileScreen(TileScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundHeight = 190;
    }

    /*protected RenderLayer renderLayers(Identifier identifier) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);
        return RenderLayer.getEntityCutoutNoCullZOffset(identifier);
    }*/
    Function<Identifier, RenderLayer> renderLayers = RenderLayer::getEntityTranslucent;

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        context.drawTexture(RenderLayer::getGuiOpaqueTexturedBackground, TEXTURE, x, y, 0f, 0f, backgroundWidth, backgroundHeight, 256, 256);
    }

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
