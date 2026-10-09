package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronEntity;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;

/**
 * A player riding a Trichaudron: his attack and use keys fire the head he holds (or grab a wall in the air) instead of
 * attacking or using; his arms are held forward on the reins (PlayerEntityModelTrichaudronRiderMixin).
 */
public final class TrichaudronRiderClient {
    /** The use key, held last tick (a press is its edge: some inputs hold it without counting a press). */
    private static boolean useHeld;

    private TrichaudronRiderClient() {
    }

    /** Whether this entity rides a Trichaudron. */
    public static boolean riding(Entity entity) {
        return entity.getVehicle() instanceof TrichaudronEntity;
    }

    public static void initialize() {
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (client.currentScreen != null || !riding(player)) return false;
            if (clickCount > 0) click();
            return true;
        });
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            boolean held = client.options.useKey.isPressed();
            boolean fresh = held && !useHeld;
            useHeld = held;
            if (client.player == null || client.currentScreen != null || !riding(client.player)) return;
            boolean used = fresh;
            while (client.options.useKey.wasPressed()) used = true;
            if (used) click();
        });
    }

    private static void click() {
        if (ClientPlayNetworking.canSend(TrichaudronEvents.RiderClick.ID)) ClientPlayNetworking.send(new TrichaudronEvents.RiderClick());
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.player.swingHand(Hand.MAIN_HAND);
    }
}
