package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlock;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind;
import net.minecraft.block.BlockState;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * What a pipe mouth linked to a mini-game page is for. A pipe gets a role from its colour when it is linked
 * ({@link #ofPipe}); the page's editor changes it.
 */
public enum MiniGamePipeRole {
    /** Where the players of a free-for-all come out, and those who come by an {@link #ENTRY} pipe. Green pipes. */
    PLAYERS(0x5DB83A, "green", "lime"),
    /** Where the party's audience comes out. White and plain glass pipes. */
    SPECTATORS(0xE6E6E6, "white"),
    /** Team A: the players on a positive tile. Blue pipes. */
    TEAM_A(0x3F6FE0, "blue"),
    /** Team B: the players on a negative tile. Red pipes. */
    TEAM_B(0xE0453A, "red"),
    /** Purple pipes. */
    TEAM_C(0xA85CE0, "purple"),
    /** Orange pipes. */
    TEAM_D(0xE08A1E, "orange"),
    /** The default arrival of those who come by a Golden Mini-game Pipe, out of a party. Black pipes. */
    ENTRY(0x4A4A4A, "black"),
    /** The way out of the mini-game: who goes in goes back where it came from. Yellow pipes. */
    EXIT(0xF2C230, "yellow");

    private static final MiniGamePipeRole[] VALUES = values();
    private static final List<MiniGamePipeRole> TEAMS = List.of(TEAM_A, TEAM_B, TEAM_C, TEAM_D);

    /** Its colour in the editor (0xRRGGBB): the colour of the pipes that get it. */
    private final int color;
    private final List<String> pipeColors;

    MiniGamePipeRole(int color, String... pipeColors) {
        this.color = color;
        this.pipeColors = List.of(pipeColors);
    }

    public int color() {
        return color;
    }

    public String translationKey() {
        return "minigame_pipe_role.steveparty." + name().toLowerCase(Locale.ROOT);
    }

    public MutableText text() {
        return Text.translatable(translationKey());
    }

    /** @return true if the players of a mini-game are sent to these pipes (not the default arrival, nor the way out). */
    public boolean isArrival() {
        return this != ENTRY && this != EXIT;
    }

    /** @return true if someone comes out of these pipes (all but the way out): they are used each in turn, or at random. */
    public boolean hasOrder() {
        return this != EXIT;
    }

    /** The role of team {@code index} (0: A ... 3: D). */
    public static MiniGamePipeRole ofTeam(int index) {
        return TEAMS.get(index);
    }

    public static @Nullable MiniGamePipeRole byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
    }

    /** The roles a mini-game needs a pipe of, to be played in {@code mode}. */
    public static List<MiniGamePipeRole> needed(MiniGameMode mode) {
        return mode == MiniGameMode.FREE_FOR_ALL ? List.of(PLAYERS) : TEAMS.subList(0, mode.teams());
    }

    /**
     * The role a pipe gets from its colour: green players, white or plain glass spectators, blue team A, red team B,
     * purple team C, orange team D, yellow the exit, black an entry; players for any other colour.
     */
    public static MiniGamePipeRole ofPipe(BlockState state) {
        if (!(state.getBlock() instanceof PipeBlock pipe)) return PLAYERS;
        if (pipe.kind() == PipeKind.GLASS) return SPECTATORS;
        // A golden pipe has no role of its own: who comes by it goes to the default arrival
        if (pipe.kind().isGolden()) return ENTRY;
        String color = ModBlocks.COLORS[Math.floorMod(pipe.color(), ModBlocks.COLORS.length)];
        for (MiniGamePipeRole role : VALUES) {
            if (role.pipeColors.contains(color)) return role;
        }
        return PLAYERS;
    }
}
