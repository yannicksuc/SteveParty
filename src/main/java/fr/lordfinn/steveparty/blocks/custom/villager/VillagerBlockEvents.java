package fr.lordfinn.steveparty.blocks.custom.villager;

import fr.lordfinn.steveparty.payloads.custom.VillagerBlockPunchPayload;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Game hooks of the villager block's reactions that aren't block methods:
 * <ul>
 *   <li>punching it where it can't be broken (adventure mode: the party players): vanilla sends nothing to the
 *   server then, so the client sends a {@link VillagerBlockPunchPayload}, checked by {@link #onPunchRequest}; where
 *   it can be broken, a punch breaks it as before (it breaks instantly);</li>
 *   <li>the chat: saying "hmm" (or hello) close to it makes it answer. Only a counter per player is kept, read by the
 *   block entities around the player.</li>
 * </ul>
 */
public final class VillagerBlockEvents {
    private static final Map<UUID, Integer> CHAT_COUNTS = new ConcurrentHashMap<>();

    private VillagerBlockEvents() {
    }

    public static void initialize() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            if (isVillagerTalk(message.getSignedContent())) {
                CHAT_COUNTS.merge(sender.getUuid(), 1, Integer::sum);
            }
        });
    }

    /**
     * Server: a player says they punched the villager block at {@code pos}. Only where they can't break it (else the
     * punch breaks it), within reach, not a spectator.
     *
     * @return whether it reacted to the punch
     */
    public static boolean onPunchRequest(ServerPlayerEntity player, BlockPos pos) {
        if (player.isSpectator() || player.canModifyBlocks() || !ScreenHandlerChecks.isInReach(player, pos)) return false;
        if (!(player.getWorld().getBlockEntity(pos) instanceof VillagerBlockEntity villager)) return false;
        villager.onPunched(player);
        return true;
    }

    /** "hmm", "hrmm", "hello", "bonjour"...: what a villager block answers to. */
    public static boolean isVillagerTalk(String message) {
        String m = message.toLowerCase(Locale.ROOT).trim();
        return m.contains("hmm") || m.contains("hrm") || m.startsWith("hello") || m.startsWith("bonjour")
                || m.startsWith("salut") || m.equals("hi") || m.startsWith("hi ");
    }

    /** How many villager-talk chat messages this player has sent (since the server started). */
    public static int chatCount(PlayerEntity player) {
        return CHAT_COUNTS.getOrDefault(player.getUuid(), 0);
    }

    /** For tests: as if the player had said "hmm" in the chat. */
    public static void recordVillagerTalk(PlayerEntity player) {
        CHAT_COUNTS.merge(player.getUuid(), 1, Integer::sum);
    }
}
