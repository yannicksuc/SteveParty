package fr.lordfinn.steveparty.utils;

import fr.lordfinn.steveparty.payloads.custom.FloatingTextPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public class FloatingTextParticleHelper {
    public static void spawnFloatingText(ServerPlayerEntity player,
                                         Vec3d pos,
                                         Vec3d velocity,
                                         float duration,
                                         float scale,
                                         int color,
                                         float fadeStart,
                                         String text) {

        FloatingTextPayload payload = new FloatingTextPayload(pos, velocity, duration, scale, color, fadeStart, text);

        ServerPlayNetworking.send(player, payload);
    }

    public static void spawnFloatingText(ServerWorld world,
                                         Vec3d pos,
                                         Vec3d velocity,
                                         float duration,
                                         float scale,
                                         int color,
                                         float fadeStart,
                                         String text,
                                         double radius) {
        for (ServerPlayerEntity player : world.getPlayers(playerEntity -> playerEntity.squaredDistanceTo(pos.x, pos.y, pos.z) < radius * radius)) {
            spawnFloatingText(player, pos, velocity, duration, scale, color, fadeStart, text);
        }
    }
}
