package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * How the players of a mini-game are split. A page ticks the layouts its mini-game accepts
 * ({@link MiniGamePageData#modes()}).
 */
public enum MiniGameMode {
    /** Everyone for themselves. */
    FREE_FOR_ALL(1),
    /** Two teams of any sizes: 2 vs 2, 1 vs 3... */
    TWO_TEAMS(2),
    THREE_TEAMS(3),
    FOUR_TEAMS(4);

    private static final MiniGameMode[] VALUES = values();
    /** Number of teams (1: no team, everyone for themselves). */
    private final int teams;

    MiniGameMode(int teams) {
        this.teams = teams;
    }

    public int teams() {
        return teams;
    }

    public String translationKey() {
        return "minigame_mode.steveparty." + name().toLowerCase(Locale.ROOT);
    }

    public MutableText text() {
        return Text.translatable(translationKey());
    }

    /** The layout of a number of teams (0 or 1: free for all), null when no mode has that many teams. */
    public static MiniGameMode ofTeams(int teams) {
        if (teams <= 1) return FREE_FOR_ALL;
        for (MiniGameMode mode : VALUES) if (mode.teams == teams) return mode;
        return null;
    }

    /** The layout of the teams drawn by the party controller (no team: free for all). */
    public static MiniGameMode of(TeamDisposition disposition) {
        return disposition == null || disposition.getTeamA().isEmpty() ? FREE_FOR_ALL : TWO_TEAMS;
    }

    public static int toMask(Set<MiniGameMode> modes) {
        int mask = 0;
        for (MiniGameMode mode : modes) mask |= 1 << mode.ordinal();
        return mask;
    }

    public static EnumSet<MiniGameMode> fromMask(int mask) {
        EnumSet<MiniGameMode> modes = EnumSet.noneOf(MiniGameMode.class);
        for (MiniGameMode mode : VALUES) if ((mask & (1 << mode.ordinal())) != 0) modes.add(mode);
        return modes;
    }
}
