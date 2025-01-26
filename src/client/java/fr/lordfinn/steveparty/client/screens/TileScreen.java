package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.screen_handlers.TileScreenHandler;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class TileScreen extends CartridgeContainerScreen<TileScreenHandler> {
    private static final Identifier TEXTURE = Identifier.of(Steveparty.MOD_ID, "textures/gui/tile.png");
    public TileScreen(TileScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title, 190);
    }

    @Override
    public Identifier getTexture() {
        return TEXTURE;
    }
}
