package fr.lordfinn.steveparty.entities;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocation;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.Heightmap;

import java.util.function.Predicate;

/** Natural spawns of the Steve Party mobs (see the *Spawns classes): where they may be born, and in which biomes. */
public final class MobSpawns {
    private MobSpawns() {
    }

    /**
     * {@code type} may be born where {@code location} and {@code predicate} allow it (on the highest block that blocks
     * motion, leaves aside), and spawns naturally among {@code group} in {@code biomes}: {@code weight}, by groups of
     * {@code min} to {@code max}. More biomes: {@link BiomeModifications#addSpawn} again.
     */
    public static <T extends MobEntity> void register(EntityType<T> type, SpawnLocation location,
                                                      SpawnRestriction.SpawnPredicate<T> predicate,
                                                      Predicate<BiomeSelectionContext> biomes, SpawnGroup group,
                                                      int weight, int min, int max) {
        SpawnRestriction.register(type, location, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, predicate);
        BiomeModifications.addSpawn(biomes, group, type, weight, min, max);
    }
}
