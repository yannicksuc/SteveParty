package fr.lordfinn.steveparty.powerups.effects;

import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.MathHelper;

/**
 * What a sprung Trap does to the player whose pawn stopped on it ({@link TrapEffect}). An unsigned Trap steals
 * {@link #COINS} {@link #defaultAmount} coins; a signed one does what its signer chose ({@code TrapSetup}).
 */
public enum TrapKind implements StringIdentifiable {
    /** Coins taken from the victim's player for the setter (at most what they hold; never through the bank). */
    COINS("coins", 10, 30),
    /** The pawn goes back that many spaces, the way it came. */
    BACK("back", 3, 10),
    /** The pawn's next turn is lost. */
    SKIP_TURN("skip_turn", 0, 0),
    /** One item of the victim (not the party's coins or stars) goes to the setter. */
    STEAL_ITEM("steal_item", 0, 0);

    public static final StringIdentifiable.EnumCodec<TrapKind> CODEC = StringIdentifiable.createCodec(TrapKind::values);

    private final String id;
    public final int defaultAmount;
    public final int maxAmount;

    TrapKind(String id, int defaultAmount, int maxAmount) {
        this.id = id;
        this.defaultAmount = defaultAmount;
        this.maxAmount = maxAmount;
    }

    public String id() {
        return id;
    }

    @Override
    public String asString() {
        return id;
    }

    /** It has an amount to choose (coins, spaces). */
    public boolean hasAmount() {
        return maxAmount > 0;
    }

    /** {@code amount} brought within 1 and its maximum (0 when it has none). */
    public int clamp(int amount) {
        return hasAmount() ? MathHelper.clamp(amount, 1, maxAmount) : 0;
    }

    /** Coins and items go to the setter: both players must be online for it to spring. */
    public boolean isTheft() {
        return this == COINS || this == STEAL_ITEM;
    }

    public static TrapKind byOrdinal(int ordinal) {
        TrapKind[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}
