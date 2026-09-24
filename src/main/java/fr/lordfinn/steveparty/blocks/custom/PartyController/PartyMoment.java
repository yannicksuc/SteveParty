package fr.lordfinn.steveparty.blocks.custom.PartyController;

import net.minecraft.text.Text;
import net.minecraft.util.StringIdentifiable;

/**
 * The moments of a party that party bells can listen to. Each moment comes with a value (0-15) read by a
 * comparator on the bell: see {@link #valueDescriptionKey()}.
 */
public enum PartyMoment implements StringIdentifiable {
    /** Value: number of tokens. */
    PARTY_START("party_start", true),
    /** A "turns" card begins (every token will play once). Value: round number. */
    ROUND_START("round_start", true),
    /** Value: rank of the token in the play order. */
    TURN_START("turn_start", true),
    /** A dice moved a token (party or free play). Value: rolled value. Never waits: the token is already moving. */
    DICE_ROLLED("dice_rolled", false),
    /** A turn is over (token arrived, or turn skipped). Free play: a token finished its movement. Value: rank. */
    TURN_END("turn_end", true),
    /** The roulette chose a mini-game. Value: 1 free for all, 2 teams. */
    MINIGAME_CHOSEN("minigame_chosen", true),
    /** Value: number of winners. */
    MINIGAME_END("minigame_end", true),
    /** An "event" card of the program. Value: channel (size of the card stack). */
    EVENT("event", true),
    /** Value: number of tokens. */
    PARTY_END("party_end", true);

    private final String name;
    private final boolean canWait;

    PartyMoment(String name, boolean canWait) {
        this.name = name;
        this.canWait = canWait;
    }

    @Override
    public String asString() {
        return name;
    }

    /** Whether a bell in waiting mode may pause the party at this moment. */
    public boolean canWait() {
        return canWait;
    }

    public String translationKey() {
        return "party_moment.steveparty." + name;
    }

    public String valueDescriptionKey() {
        return "party_moment.steveparty." + name + ".value";
    }

    public Text getText() {
        return Text.translatable(translationKey());
    }

    public PartyMoment next(boolean backwards) {
        PartyMoment[] values = values();
        return values[Math.floorMod(ordinal() + (backwards ? -1 : 1), values.length)];
    }

    public static PartyMoment byName(String name, PartyMoment fallback) {
        for (PartyMoment moment : values()) {
            if (moment.name.equals(name) || moment.name().equals(name)) return moment;
        }
        return fallback;
    }
}
