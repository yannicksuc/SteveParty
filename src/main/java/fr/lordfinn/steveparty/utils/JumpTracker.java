package fr.lordfinn.steveparty.utils;

import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JumpTracker {
    /**
     * Max delay (ticks) between two consecutive jumps for the combo to continue.
     * Covers the airtime of a boosted 2nd jump (~17 ticks) plus a short grace period after landing.
     */
    public static final int COMBO_WINDOW_TICKS = 30;

    private static final Map<UUID, Integer> COMBOS = new HashMap<>();
    private static final Map<UUID, Long> LAST_JUMP_TIMES = new HashMap<>();

    public static int getCombo(PlayerEntity player) {
        UUID uuid = player.getUuid();
        Long lastJump = LAST_JUMP_TIMES.get(uuid);
        long now = player.getWorld().getTime();
        // Combo only continues with consecutive jumps: reset if too much time passed since the last one
        if (lastJump == null || now - lastJump > COMBO_WINDOW_TICKS || now < lastJump) {
            COMBOS.remove(uuid);
            return 0;
        }
        return COMBOS.getOrDefault(uuid, 0);
    }

    public static void incrementCombo(PlayerEntity player) {
        int combo = getCombo(player) + 1;
        if (combo > 2) combo = 0; // reset après le 3e saut
        COMBOS.put(player.getUuid(), combo);
        LAST_JUMP_TIMES.put(player.getUuid(), player.getWorld().getTime());
    }

    public static void reset(PlayerEntity player) {
        COMBOS.put(player.getUuid(), 0);
        LAST_JUMP_TIMES.remove(player.getUuid());
    }
}
