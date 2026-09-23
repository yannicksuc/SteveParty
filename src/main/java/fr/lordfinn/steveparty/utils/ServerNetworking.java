package fr.lordfinn.steveparty.utils;

import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.payloads.custom.SelectionStatePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import static fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem.setSelectionState;

public class ServerNetworking {
    /** Selection states: 0 = random, 1 = all, 2 = cycle. */
    private static final int SELECTION_STATE_COUNT = 3;

    public static void initialize() {
        ServerPlayNetworking.registerGlobalReceiver(SelectionStatePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            // Handlers run on the netty thread: touch game state on the server thread only
            player.server.execute(() -> {
                ItemStack stack = player.getMainHandStack();
                if (stack.isEmpty() || !(stack.getItem() instanceof InventoryCartridgeItem)) return;
                int state = payload.selectionState();
                if (state < 0 || state >= SELECTION_STATE_COUNT) return;
                setSelectionState(stack, state);
            });
        });
    }
}
