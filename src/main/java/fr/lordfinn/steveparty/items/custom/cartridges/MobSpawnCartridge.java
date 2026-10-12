package fr.lordfinn.steveparty.items.custom.cartridges;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;

/**
 * A cartridge whose board space summons a mob for its show (Frousseux, Glandouille, Mistigri, Trichaudron, the Shop's merchant): it may be
 * linked to a Spawn Bubble (« spawn marker » in the code), where its mob appears ({@link CartridgeSpawnMarker}). An addon's cartridge implements it
 * too: the Tile Linker Brush then links it to a Spawn Bubble, its behaviour finds the marker with
 * {@link CartridgeSpawnMarker#marker} (null: beside the space) and summons its mob there.
 */
public interface MobSpawnCartridge {
    /** The kind of mob its space summons (shown in miniature in its Spawn Bubble, see MarkerMobs). */
    EntityType<?> spawnedMob();

    /**
     * Dresses {@code mob}, summoned by {@code cartridge}'s space (its show, or living on its Spawn Bubble), as the
     * cartridge says (the Shop's merchant: the block of his box). Called again when the cartridge changes: only what
     * differs is set. Nothing by default.
     */
    default void dress(MobEntity mob, ItemStack cartridge) {
    }
}
