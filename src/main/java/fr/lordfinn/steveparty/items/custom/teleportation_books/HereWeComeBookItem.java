package fr.lordfinn.steveparty.items.custom.teleportation_books;

import fr.lordfinn.steveparty.screen_handlers.custom.HereWeComeBookScreenHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
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


    /** Max number of teleporting conditions a book can hold. */
    public static final int MAX_TARGETS = 32;

    /** Must run on the server thread. */
    public static void handleHereWeComeBookPayload(ServerPlayerEntity player, List<TeleportingTarget> teleportingTargets) {
        // The book screen must be open, with the book in the main hand (where it was opened from)
        if (!(player.currentScreenHandler instanceof HereWeComeBookScreenHandler handler) || !handler.canUse(player)) return;
        if (teleportingTargets == null || teleportingTargets.size() > MAX_TARGETS) return;
        ItemStack bookStack = player.getMainHandStack();
        if (bookStack.getItem() instanceof HereWeComeBookItem) {
            // Sanitized, independent copies (never keep objects coming from the network/another stack)
            List<TeleportingTarget> sanitized = new ArrayList<>(teleportingTargets.size());
            for (TeleportingTarget target : teleportingTargets) {
                if (target == null) continue;
                sanitized.add(new TeleportingTarget(target.getGroup(),
                        Math.max(0, target.getFillCapacity()),
                        Math.max(0, target.getFillPriorityWeight())));
            }
            bookStack.set(TP_TARGETS, List.copyOf(sanitized));
            player.getInventory().markDirty();
        }
    }
}
