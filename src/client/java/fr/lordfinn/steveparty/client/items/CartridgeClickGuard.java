package fr.lordfinn.steveparty.client.items;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

/**
 * A right click with a cartridge on a block toggles what it selects (a board space, a router, a container). The game
 * repeats the use every 4 ticks while the button is held, and a lagging client runs several ticks for one short click:
 * the block was selected then unselected at once, the click seemed to do nothing. One press of the button now uses
 * the cartridge on a block once; the next press again.
 */
public final class CartridgeClickGuard {
    private static final Identifier BEFORE_ITEMS = Steveparty.id("cartridge_click_guard");
    /** Whether the use button, held since, already used a cartridge on a block. */
    private static boolean pressUsed;

    private CartridgeClickGuard() {
    }

    public static void initialize() {
        // Before the item callbacks (the Inventory Cartridge's chooses a container in its own callback)
        UseBlockCallback.EVENT.addPhaseOrdering(BEFORE_ITEMS, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(BEFORE_ITEMS, (player, world, hand, hit) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!world.isClient || player != client.player || !usesCartridge(player, hand)) return ActionResult.PASS;
            // A use not made by the button (none held) is not a repeat
            if (!client.options.useKey.isPressed()) return ActionResult.PASS;
            // FAIL: no packet, and the click goes no further (no use in the air either)
            if (pressUsed) return ActionResult.FAIL;
            pressUsed = true;
            return ActionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!client.options.useKey.isPressed()) pressUsed = false;
        });
    }

    /** Whether this hand's turn of a click uses a cartridge (the off hand's Inventory Cartridge with an empty main hand too). */
    private static boolean usesCartridge(PlayerEntity player, Hand hand) {
        if (player.getStackInHand(hand).getItem() instanceof CartridgeItem) return true;
        return hand == Hand.MAIN_HAND && player.getMainHandStack().isEmpty()
                && player.getOffHandStack().getItem() instanceof InventoryCartridgeItem;
    }
}
