package fr.lordfinn.steveparty.blocks.custom.PartyController;

import net.minecraft.text.Text;
import net.minecraft.util.StringIdentifiable;

/**
 * The moments of a party that Party Antennas can listen to (their "frequencies"), in five families. Each moment
 * comes with a value (0-15, above 15 = 15) read by a comparator on the antenna: see {@link #valueDescriptionKey()}.
 * The order is the antenna screen's: one row per family. The names are saved (block states, steps): never rename one.
 */
public enum PartyMoment implements StringIdentifiable {
    /** Value: number of players. */
    PARTY_START("party_start", Family.PARTY, true, false),
    /** At the start of each of the last 3 rounds. Value: rounds left, this one included (3, 2, 1). */
    FINAL_ROUNDS("final_rounds", Family.PARTY, true, false),
    /** Value: number of players. */
    PARTY_END("party_end", Family.PARTY, true, false),
    /** The Party Rewinder took the party back. Value: turns undone. */
    REWIND("rewind", Family.PARTY, false, false),

    /** A "turns" card begins (every token will play once). Value: round number. */
    ROUND_START("round_start", Family.TURNS, true, false),
    /** Value: rank of the token in the play order. */
    TURN_START("turn_start", Family.TURNS, true, false),
    /** A dice moved a token (party or free play). Value: rolled value. Never waits: the token is already moving. */
    DICE_ROLLED("dice_rolled", Family.TURNS, false, true),
    /** A token reaches a fork, before its player chooses. Value: number of paths. */
    CROSSROADS("crossroads", Family.TURNS, false, true),
    /** The path is chosen at a fork. Value: its direction, 1 north, 2 east, 3 south, 4 west. */
    PATH_CHOSEN("path_chosen", Family.TURNS, false, true),
    /** A token stops on its space, before the space acts. Value: the role of the space, as a Tile Router says it. */
    TOKEN_LANDS("token_lands", Family.TURNS, false, true),
    /** A turn is over (token arrived, or turn skipped). Free play: a token finished its movement, value 1. Value: rank. */
    TURN_END("turn_end", Family.TURNS, true, true),

    /** Value: how many. */
    COINS_WON("coins_won", Family.COINS, false, false),
    /** Value: how many. */
    COINS_LOST("coins_lost", Family.COINS, false, false),
    /** Value: the player's Party Stars after. */
    STAR_WON("star_won", Family.COINS, false, false),
    /** The Party Star goes to another space. Value: the index of that space among the board's star spaces (from 1). */
    STAR_MOVES("star_moves", Family.COINS, false, false),
    /** Someone else leads the ranking. Value: the new leader's rank in the play order. */
    NEW_LEADER("new_leader", Family.COINS, false, false),

    /** An "event" card of the program or an Event space. Value: channel. */
    EVENT("event", Family.EVENTS, true, false),
    /** The event's time out is over. Value: channel. */
    EVENT_OVER("event_over", Family.EVENTS, true, false),

    /** The roulette chose a mini-game. Value: 1 free for all, else the number of teams. */
    MINIGAME_CHOSEN("minigame_chosen", Family.MINIGAMES, true, false),
    /**
     * The practice round of the mini-game starts. Out of a party: a run of the mini-game launched from its Mini-game
     * Controller (« Play », or « Test » in its page's editor), as its countdown begins. Value: number of players.
     */
    PRACTICE_ROUND("practice_round", Family.MINIGAMES, true, true),
    /** The mini-game's real round starts. Value: number of players. */
    MINIGAME_START("minigame_start", Family.MINIGAMES, true, true),
    /** Value: number of winners (0: nobody). */
    MINIGAME_END("minigame_end", Family.MINIGAMES, true, true);

    /** The antenna screen's rows, each with its colour (the mod's colours for these concepts). */
    public enum Family {
        PARTY("party", 0xFBE54D),
        TURNS("turns", 0x26C0E3),
        COINS("coins", 0x267C3E),
        EVENTS("events", 0xA722D0),
        MINIGAMES("minigames", 0xF58121);

        private final String id;
        private final int colour;

        Family(String id, int colour) {
            this.id = id;
            this.colour = colour;
        }

        /** RGB. */
        public int colour() {
            return colour;
        }

        public String translationKey() {
            return "party_moment_family.steveparty." + id;
        }

        public Text getText() {
            return Text.translatable(translationKey());
        }
    }

    private final String name;
    private final Family family;
    private final boolean canWait;
    private final boolean outOfParty;

    PartyMoment(String name, Family family, boolean canWait, boolean outOfParty) {
        this.name = name;
        this.family = family;
        this.canWait = canWait;
        this.outOfParty = outOfParty;
    }

    @Override
    public String asString() {
        return name;
    }

    public Family family() {
        return family;
    }

    /** Whether an antenna in waiting mode may pause the party at this moment. */
    public boolean canWait() {
        return canWait;
    }

    /**
     * Whether an antenna may hear this moment out of any party (the single source of truth; the antenna screen greys
     * the others out of a party). Only through its brush link, never by distance:
     * <ul>
     *     <li>linked to an element of a board: the free tokens (in no running party) moving on that board (the board
     *     of a space is the one of its Party Controller, its party running or not): {@link #DICE_ROLLED},
     *     {@link #CROSSROADS}, {@link #PATH_CHOSEN}, {@link #TOKEN_LANDS} and {@link #TURN_END} (value 1);</li>
     *     <li>linked to an element of a mini-game (its Mini-game Controller, a podium, a pipe...): the runs of that
     *     mini-game out of a party: {@link #PRACTICE_ROUND} (launched), {@link #MINIGAME_START} (players sent out),
     *     {@link #MINIGAME_END} (results, value: winners).</li>
     * </ul>
     * The others need a party: nothing else happens out of one (spaces, events and coins act in a party only).
     * Heard out of a party, a moment never waits: there is no party to pause.
     */
    public boolean outOfParty() {
        return outOfParty;
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
