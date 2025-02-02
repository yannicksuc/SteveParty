package fr.lordfinn.steveparty.items.custom.teleportation_books;

import fr.lordfinn.steveparty.screen_handlers.HereWeGoBookScreenHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;

import static fr.lordfinn.steveparty.components.ModComponents.STATE;

public class HereWeGoBookItem extends AbstractTeleportationBookItem {
    public HereWeGoBookItem(Settings settings) {
        super(settings);
    }

    @Override
    public ScreenHandler createScreenHandler(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new HereWeGoBookScreenHandler(syncId, inv);
    }

    public static void handleHereWeGoBookPayload(ServerPlayerEntity player, int newState) {
        ItemStack bookStack = player.getMainHandStack();
        if (bookStack.getItem() instanceof AbstractTeleportationBookItem) {
            bookStack.set(STATE, newState);
            player.getInventory().markDirty();
        }
    }
}
