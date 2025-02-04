package fr.lordfinn.steveparty.utils;

import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

import java.util.List;

public class SoundsUtils {
    public static void playSoundToPlayers(List<ServerPlayerEntity> players, SoundEvent soundEvent, SoundCategory category, float volume, float pitch) {
        RegistryEntry<SoundEvent> soundEntry = Registries.SOUND_EVENT.getEntry(soundEvent);

        players.forEach(player -> {
            player.networkHandler.sendPacket(new PlaySoundS2CPacket(
                    soundEntry,
                    category,
                    player.getX(), player.getY(), player.getZ(),
                    volume, // Volume
                    pitch, // Pitch
                    1     // Seed
            ));
        });
    }
}
