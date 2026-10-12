package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.screen_handlers.custom.LootingBoxScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** The Looting Box's menu: its Inventory Cartridge slot on the tab and, on the right, the cartridge's menu. */
public class LootingBoxScreen extends CartridgeContainerScreen<LootingBoxScreenHandler> {
    private static final Identifier TEXTURE = Steveparty.id("textures/gui/router.png");
    public LootingBoxScreen(LootingBoxScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title, 136);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawTooltips(context, mouseX, mouseY);
    }

    @Override
    public Identifier getTexture() {
        return TEXTURE;
    }

    /** The tab's right edge (its outline is at 101). */
    @Override
    protected int wireFromX() {
        return 102;
    }

}
