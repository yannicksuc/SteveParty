package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.screen_handlers.TeleportationBookScreenHandler;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

public class HereWeComeBookScreen extends TeleportationBookScreen {
    public HereWeComeBookScreen(TeleportationBookScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }
}
