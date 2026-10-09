package fr.lordfinn.steveparty.dice;

import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.events.DiceThrowRevealed;
import fr.lordfinn.steveparty.payloads.custom.SixSevenPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A hidden surprise: a Double Dice showing a 6 and a 7 (in either order) makes everyone following the throw (the
 * players near the dice and the roller's party, {@link DiceReveal#recipients}) do the « 6-7 » with their arms for a
 * moment, the hands going up and down in turn like scales. The server only says who and from when
 * ({@link SixSevenPayload}): the clients play it, to whoever sees them.
 */
public final class SixSeven {
    private SixSeven() {
    }

    public static void initialize() {
        DiceThrowRevealed.EVENT.register((lead, roller, diceThrow) -> perform(lead, diceThrow));
    }

    /** Exactly two dice, one showing a 6 and the other a 7 (normal, premium or cursed faces). */
    public static boolean matches(DiceThrow diceThrow) {
        if (diceThrow.dice() != 2) return false;
        List<Integer> numbers = diceThrow.numbers();
        return numbers.size() == 2 && numbers.contains(6) && numbers.contains(7);
    }

    /** What was sent, to whom (the dancers and whoever sees them). */
    public record Performance(SixSevenPayload payload, List<ServerPlayerEntity> audience) {
    }

    /** On a 6-7, the dancers start now: told to them and to the players who see them. Null on any other throw. */
    public static @Nullable Performance perform(DiceEntity lead, DiceThrow diceThrow) {
        if (!matches(diceThrow) || !(lead.getWorld() instanceof ServerWorld world)) return null;
        List<ServerPlayerEntity> dancers = DiceReveal.recipients(lead);
        if (dancers.isEmpty()) return null;
        List<UUID> ids = new ArrayList<>(dancers.size());
        List<ServerPlayerEntity> audience = new ArrayList<>(dancers);
        for (ServerPlayerEntity dancer : dancers) {
            ids.add(dancer.getUuid());
            for (ServerPlayerEntity watcher : PlayerLookup.tracking(dancer)) {
                if (!audience.contains(watcher)) audience.add(watcher);
            }
        }
        SixSevenPayload payload = new SixSevenPayload(world.getTime(), lead.getX(), lead.getY(), lead.getZ(), ids);
        for (ServerPlayerEntity player : audience) ServerPlayNetworking.send(player, payload);
        return new Performance(payload, audience);
    }
}
