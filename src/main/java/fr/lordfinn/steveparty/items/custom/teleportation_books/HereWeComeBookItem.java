package fr.lordfinn.steveparty.items.custom.teleportation_books;

import fr.lordfinn.steveparty.screen_handlers.HereWeComeBookScreenHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;

public class HereWeComeBookItem extends AbstractTeleportationBookItem {
    public HereWeComeBookItem(Settings settings) {
        super(settings);
    }

    @Override
    public ScreenHandler createScreenHandler(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new HereWeComeBookScreenHandler(syncId, inv);
    }
}
