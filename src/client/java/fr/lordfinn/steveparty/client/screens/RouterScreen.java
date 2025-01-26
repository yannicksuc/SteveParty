package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.screen_handlers.RouterScreenHandler;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class RouterScreen extends CartridgeContainerScreen<RouterScreenHandler> {
    private static final Identifier TEXTURE = Identifier.of(Steveparty.MOD_ID, "textures/gui/router.png");
    public RouterScreen(RouterScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title, 134);
    }

    @Override
    public Identifier getTexture() {
        return TEXTURE;
    }

}
