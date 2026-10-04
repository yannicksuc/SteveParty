package fr.lordfinn.steveparty.minigame;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

/**
 * The page's « Mode aventure » for a round played without bubble ({@code MiniGameArena}; in a bubble, the bubble does
 * it): its participants play in adventure mode, and get their own mode back when they leave the round or it ends.
 * <p>
 * Their own mode is kept as a command tag on the player, saved with it: a player who leaves the server gets it back
 * (before he is saved), and one who comes back with the tag still on (the server stopped during the round) too.
 */
public final class MiniGameAdventure {
    static final String TAG = "steveparty_round_mode_";

    private MiniGameAdventure() {
    }

    public static void initialize() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> restore(handler.getPlayer()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> restore(handler.getPlayer()));
    }

    /** @return true if the player was put in adventure mode (it was not in it already) */
    public static boolean apply(ServerPlayerEntity player) {
        GameMode mode = player.interactionManager.getGameMode();
        if (mode == GameMode.ADVENTURE || mode == GameMode.SPECTATOR || own(player) != null) return false;
        player.addCommandTag(TAG + mode.asString());
        player.changeGameMode(GameMode.ADVENTURE);
        return true;
    }

    /** The player gets its own mode back, if a round took it. */
    public static void restore(@Nullable ServerPlayerEntity player) {
        if (player == null) return;
        GameMode mode = own(player);
        if (mode == null) return;
        player.removeCommandTag(TAG + mode.asString());
        if (player.interactionManager.getGameMode() == GameMode.ADVENTURE) player.changeGameMode(mode);
    }

    /** @return the mode a round took from the player, null for none */
    public static @Nullable GameMode own(ServerPlayerEntity player) {
        for (String tag : player.getCommandTags()) {
            if (tag.startsWith(TAG)) return GameMode.byName(tag.substring(TAG.length()), GameMode.SURVIVAL);
        }
        return null;
    }
}
