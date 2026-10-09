package fr.lordfinn.steveparty.dice;

import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.components.DiceFacesComponent.Kind;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * A cursed roll: the next die a player throws, whatever die it is, only rolls the cursed 1, 2 or 3 (each as likely),
 * on every die of the throw; a Slow die turns through those three faces, a Choice die offers only them. One roll and
 * the curse is gone. Put by the Mistigri's « cursed die » sentence and by the Loaded Die (a player used it on them).
 * <p>
 * Kept as a scoreboard tag of the player, so it is saved with them (a party saved in the middle keeps it). The roll
 * reads it when it starts ({@link DiceRollSequence}) and lifts it when it is final. Server side.
 */
public final class CursedRolls {
    /** The player's tag while their next roll is cursed. */
    public static final String TAG = "steveparty.cursed_roll";
    /** The faces of a cursed roll. */
    public static final List<DiceFace> FACES = List.of(new DiceFace(Kind.CURSED, 1), new DiceFace(Kind.CURSED, 2),
            new DiceFace(Kind.CURSED, 3));

    private CursedRolls() {
    }

    /** Curses {@code player}'s next roll. False if it already was. */
    public static boolean curse(PlayerEntity player) {
        return player.addCommandTag(TAG);
    }

    public static boolean isCursed(@Nullable PlayerEntity player) {
        return player != null && player.getCommandTags().contains(TAG);
    }

    /** Whether the roller {@code roller} (online on {@code server}) is cursed. */
    public static boolean isCursed(@Nullable MinecraftServer server, @Nullable UUID roller) {
        if (server == null || roller == null) return false;
        return isCursed(server.getPlayerManager().getPlayer(roller));
    }

    /** The curse is spent (their cursed roll is final). */
    public static void lift(@Nullable MinecraftServer server, @Nullable UUID roller) {
        if (server == null || roller == null) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(roller);
        if (player != null) player.removeCommandTag(TAG);
    }
}
