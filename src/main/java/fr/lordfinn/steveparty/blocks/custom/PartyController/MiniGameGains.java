package fr.lordfinn.steveparty.blocks.custom.PartyController;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

import java.util.Arrays;

/**
 * What a party pays at the end of each of its mini-games (the dashboard's Gains page): for each place, an amount of
 * coins and an amount of stars (see {@link PartyCurrency}). The rows are the 1st, 2nd, 3rd and 4th places, then the
 * « participants »: everyone who took none of those places (no podium, or a place beyond the 4th). In a team mini-game
 * every member of a team gets the gain of the team's place; tied players get the same gain.
 * <p>
 * Immutable: change it with {@link #with}.
 */
public final class MiniGameGains {
    /** Rows: places 1 to 4, then the participants. */
    public static final int ROWS = 5;
    /** The row of those who took no place. */
    public static final int PARTICIPANTS = ROWS - 1;
    public static final int MAX = 99;
    /** Like in Mario Party: 10 coins for the winner, then 5, 3 and 1; nothing for the others, and no star. */
    public static final MiniGameGains DEFAULT = new MiniGameGains(new int[]{10, 5, 3, 1, 0}, new int[ROWS]);

    private final int[] coins;
    private final int[] stars;

    private MiniGameGains(int[] coins, int[] stars) {
        this.coins = clean(coins);
        this.stars = clean(stars);
    }

    private static int[] clean(int[] values) {
        int[] clean = new int[ROWS];
        for (int i = 0; i < ROWS && values != null && i < values.length; i++) clean[i] = Math.clamp(values[i], 0, MAX);
        return clean;
    }

    /** The row of a place: 1 to 4, anything else (0: no place) is the participants row. */
    public static int rowOf(int place) {
        return place >= 1 && place < ROWS ? place - 1 : PARTICIPANTS;
    }

    /** The amount of a currency paid to a row. */
    public int amount(PartyCurrency currency, int row) {
        if (row < 0 || row >= ROWS) return 0;
        return (currency == PartyCurrency.STAR ? stars : coins)[row];
    }

    /** The amount of a currency paid for a place (see {@link #rowOf}). */
    public int forPlace(PartyCurrency currency, int place) {
        return amount(currency, rowOf(place));
    }

    public MiniGameGains with(PartyCurrency currency, int row, int amount) {
        if (row < 0 || row >= ROWS) return this;
        int[] newCoins = coins.clone(), newStars = stars.clone();
        (currency == PartyCurrency.STAR ? newStars : newCoins)[row] = amount;
        return new MiniGameGains(newCoins, newStars);
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putIntArray("Coins", coins);
        nbt.putIntArray("Stars", stars);
        return nbt;
    }

    public static MiniGameGains fromNbt(NbtCompound nbt) {
        if (!nbt.contains("Coins") && !nbt.contains("Stars")) return DEFAULT;
        return new MiniGameGains(nbt.getIntArray("Coins"), nbt.getIntArray("Stars"));
    }

    public void write(PacketByteBuf buf) {
        for (int value : coins) buf.writeVarInt(value);
        for (int value : stars) buf.writeVarInt(value);
    }

    public static MiniGameGains read(PacketByteBuf buf) {
        int[] coins = new int[ROWS], stars = new int[ROWS];
        for (int i = 0; i < ROWS; i++) coins[i] = buf.readVarInt();
        for (int i = 0; i < ROWS; i++) stars[i] = buf.readVarInt();
        return new MiniGameGains(coins, stars);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MiniGameGains gains && Arrays.equals(coins, gains.coins) && Arrays.equals(stars, gains.stars);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(coins) + Arrays.hashCode(stars);
    }

    @Override
    public String toString() {
        return "coins " + Arrays.toString(coins) + ", stars " + Arrays.toString(stars);
    }
}
