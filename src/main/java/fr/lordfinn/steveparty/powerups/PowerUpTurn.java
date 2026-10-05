package fr.lordfinn.steveparty.powerups;

import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

/**
 * The power-up state of one turn ({@code TokenTurnPartyStep#getPowerUps}): the power-up used during it, if any, and
 * what it remembers until the end of the turn. Saved with the turn (and so with the party), reset when the turn
 * starts: a Roll Again turn is a new turn, with its own power-up.
 */
public final class PowerUpTurn {
    private @Nullable PowerUp used;
    private NbtCompound data = new NbtCompound();
    /**
     * World time until which the turn is held by a power-up still at work (a player being picked, a warp): no roll and
     * no other power-up meanwhile. 0: not held. Not saved: a restart ends what held it.
     */
    private long heldUntil;

    /** The power-up used this turn, null if none yet. */
    public @Nullable PowerUp used() {
        return used;
    }

    public boolean hasUsed() {
        return used != null;
    }

    /** Remembers the power-up just used (its data starts empty). */
    void use(PowerUp powerUp) {
        this.used = powerUp;
        this.data = new NbtCompound();
    }

    /**
     * What the power-up used remembers until the end of the turn (saved): the Mushroom notes there that its bonus
     * was spent, for instance. Keys are the power-up's own.
     */
    public NbtCompound data() {
        return data;
    }

    /** Forgets the power-up just remembered: it was refused after all. */
    void forget() {
        used = null;
        data = new NbtCompound();
    }

    /** Holds the turn until {@code until} (world time) at the latest, or until {@link #release}. */
    void hold(long until) {
        heldUntil = until;
    }

    void release() {
        heldUntil = 0;
    }

    /** True while a power-up still at work holds the turn: no roll, no other power-up. */
    public boolean isHeld(long now) {
        return heldUntil > now;
    }

    public void reset() {
        used = null;
        data = new NbtCompound();
        heldUntil = 0;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        if (used != null) {
            nbt.putString("Used", used.id());
            if (!data.isEmpty()) nbt.put("Data", data.copy());
        }
        return nbt;
    }

    /** Reads a saved state; an unknown power-up (removed from the mod) counts as used, with no effect left. */
    public void fromNbt(@Nullable NbtCompound nbt) {
        reset();
        if (nbt == null || !nbt.contains("Used")) return;
        used = PowerUps.byId(nbt.getString("Used"));
        if (used == null) used = PowerUps.SPENT;
        data = nbt.getCompound("Data").copy();
    }
}
