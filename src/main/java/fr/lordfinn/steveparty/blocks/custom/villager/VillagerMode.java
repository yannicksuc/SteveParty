package fr.lordfinn.steveparty.blocks.custom.villager;

/**
 * The villager block's long-lasting state, synced with the block entity data (a player coming later sees it too),
 * under the short reactions: asleep at night, dancing while a jukebox plays nearby (a funkier dance on Pigstep).
 */
public enum VillagerMode {
    NONE, SLEEP, DANCE, DANCE_FUNKY;

    public static VillagerMode byId(int id) {
        VillagerMode[] values = values();
        return id >= 0 && id < values.length ? values[id] : NONE;
    }

    public boolean isDancing() {
        return this == DANCE || this == DANCE_FUNKY;
    }
}
