package fr.lordfinn.steveparty.components;

import fr.lordfinn.steveparty.utils.TripleJumpData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.WeakHashMap;

public class TripleJumpComponent {
    private int jumpCount = 0;
    private boolean wasOnGround = true;
    private static final WeakHashMap<PlayerEntity, TripleJumpComponent> DATA = new WeakHashMap<>();


    public static TripleJumpComponent get(PlayerEntity player) {
        return DATA.computeIfAbsent(player, p -> new TripleJumpComponent());
    }

    private long jumpBufferExpire = 0;

    public int getJumpCount() {
        return jumpCount;
    }

    public void setJumpCount(int jumpCount) {
        this.jumpCount = jumpCount;
    }

    public void incrementJump() {
        this.jumpCount++;
    }

    public void reset() {
        this.jumpCount = 0;
    }

    public boolean canTripleJump() {
        return jumpCount < 3;
    }

    public long getJumpBufferExpire() {
        return jumpBufferExpire;
    }

    public void setJumpBufferExpire(long jumpBufferExpire) {
        this.jumpBufferExpire = jumpBufferExpire;
    }
}

