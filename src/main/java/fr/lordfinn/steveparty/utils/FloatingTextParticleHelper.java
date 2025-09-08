package fr.lordfinn.steveparty.utils;

import fr.lordfinn.steveparty.payloads.custom.FloatingTextPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.TextColor;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

public class FloatingTextParticleHelper {
    public static void spawnFloatingText(ServerPlayerEntity player,
                                         Vector3f pos,
                                         Vector3f velocity,
                                         float duration,
                                         float scale,
                                         int color,
                                         float fadeStart,
                                         String text) {
        FloatingTextPayload payload = new FloatingTextPayload(pos, velocity, duration, scale, color, fadeStart, text);
        ServerPlayNetworking.send(player, payload);
    }

    public static void spawnFloatingText(ServerWorld world,
                                         Vector3f pos,
                                         Vector3f velocity,
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

    // Nouvelle méthode simplifiée avec valeurs par défaut
    public static void spawnFloatingText(ServerPlayerEntity player,
                                         String text,
                                         Vector3f pos,
                                         TextColor color) {
        Vector3f defaultVelocity = new Vector3f(0, 0.02f, 0);
        float defaultDuration = 60;   // 2 secondes
        float defaultScale = 0.02f;
        float defaultFadeStart = 0.5f;

        spawnFloatingText(player, pos, defaultVelocity, defaultDuration, defaultScale, color.getRgb(), defaultFadeStart, text);
    }

    // Variante pour tous les joueurs dans un rayon
    public static void spawnFloatingText(ServerWorld world,
                                         String text,
                                         Vector3f pos,
                                         TextColor color,
                                         double radius) {
        Vector3f defaultVelocity = new Vector3f(0, 0.02f, 0);
        float defaultDuration = 60;   // 2 secondes
        float defaultScale = 0.02f;
        float defaultFadeStart = 0.5f;

        for (ServerPlayerEntity player : world.getPlayers(playerEntity -> playerEntity.squaredDistanceTo(pos.x, pos.y, pos.z) < radius * radius)) {
            spawnFloatingText(player, pos, defaultVelocity, defaultDuration, defaultScale, color.getRgb(), defaultFadeStart, text);
        }
    }
}
