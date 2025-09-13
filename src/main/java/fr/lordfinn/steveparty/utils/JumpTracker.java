package fr.lordfinn.steveparty.utils;

import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JumpTracker {
    private static final Map<UUID, Integer> COMBOS = new HashMap<>();

    public static int getCombo(PlayerEntity player) {
        return COMBOS.getOrDefault(player.getUuid(), 0);
    }

    public static void incrementCombo(PlayerEntity player) {
        int combo = getCombo(player) + 1;
        if (combo > 2) combo = 0; // reset après le 3e saut
        COMBOS.put(player.getUuid(), combo);
    }

    public static void reset(PlayerEntity player) {
        COMBOS.put(player.getUuid(), 0);
    }
}
