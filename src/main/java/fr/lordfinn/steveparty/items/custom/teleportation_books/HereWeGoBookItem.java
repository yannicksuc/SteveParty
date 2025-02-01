package fr.lordfinn.steveparty.items.custom.teleportation_books;

import fr.lordfinn.steveparty.screen_handlers.HereWeGoBookScreenHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;

public class HereWeGoBookItem extends AbstractTeleportationBookItem {
    public HereWeGoBookItem(Settings settings) {
        super(settings);
    }

    @Override
    public ScreenHandler createScreenHandler(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new HereWeGoBookScreenHandler(syncId, inv);
    }
}
