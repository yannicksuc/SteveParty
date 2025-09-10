package fr.lordfinn.steveparty.utils;

import fr.lordfinn.steveparty.payloads.custom.SelectionStatePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import static fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem.setSelectionState;

public class ServerNetworking {
    public static void initialize() {
        ServerPlayNetworking.registerGlobalReceiver(SelectionStatePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ItemStack stack = player.getMainHandStack();
            setSelectionState(stack, payload.selectionState());
        });
    }
}
