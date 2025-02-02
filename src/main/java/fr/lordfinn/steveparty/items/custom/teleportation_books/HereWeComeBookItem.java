package fr.lordfinn.steveparty.items.custom.teleportation_books;

import fr.lordfinn.steveparty.screen_handlers.HereWeComeBookScreenHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

import static fr.lordfinn.steveparty.components.ModComponents.TP_TARGETS;

public class HereWeComeBookItem extends AbstractTeleportationBookItem {
    public HereWeComeBookItem(Settings settings) {
        super(settings);
    }

    @Override
    public ScreenHandler createScreenHandler(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new HereWeComeBookScreenHandler(syncId, inv);
    }


    public static void handleHereWeComeBookPayload(ServerPlayerEntity player, List<TeleportingTarget> teleportingTargets) {
        ItemStack bookStack = player.getMainHandStack();
        if (bookStack.getItem() instanceof AbstractTeleportationBookItem) {
            bookStack.set(TP_TARGETS, teleportingTargets);
            player.getInventory().markDirty();
        }
    }
}
