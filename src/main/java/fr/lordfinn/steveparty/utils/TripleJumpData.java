package fr.lordfinn.steveparty.utils;

import net.minecraft.entity.player.PlayerEntity;

import java.util.WeakHashMap;

public class TripleJumpData {
    private static final WeakHashMap<PlayerEntity, TripleJumpData> DATA = new WeakHashMap<>();

    private int jumpCount = 0;

    public static TripleJumpData get(PlayerEntity player) {
        return DATA.computeIfAbsent(player, p -> new TripleJumpData());
    }

    public void reset() {
        jumpCount = 0;
    }

    public void incrementJump() {
        jumpCount++;
    }

    public int getJumpCount() {
        return jumpCount;
    }

    public boolean canTripleJump() {
        return jumpCount < 3;
    }
}
