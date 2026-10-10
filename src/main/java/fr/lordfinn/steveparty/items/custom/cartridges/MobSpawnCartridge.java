package fr.lordfinn.steveparty.items.custom.cartridges;

import net.minecraft.entity.EntityType;

/**
 * A cartridge whose board space summons a mob for its show (Frousseux, Glandouille, Mistigri, Trichaudron, the Shop's merchant): it may be
 * linked to a Spawn Marker, where its mob appears ({@link CartridgeSpawnMarker}). An addon's cartridge implements it
 * too: the Tile Linker Brush then links it to a Spawn Marker, its behaviour finds the marker with
 * {@link CartridgeSpawnMarker#marker} (null: beside the space) and summons its mob there.
 */
public interface MobSpawnCartridge {
    /** The kind of mob its space summons (shown on the Spawn Marker's preview). */
    EntityType<?> spawnedMob();
}
