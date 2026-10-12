package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLiveData;
import fr.lordfinn.steveparty.payloads.custom.PawnBeamsPayload;
import fr.lordfinn.steveparty.utils.ServerMemory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The light beam over a pawn (the star's beam, in the pawn's colour: client side, PawnBeamRenderer), so that everyone
 * sees at once whose pawn matters now. A pawn of a running party shows it:
 * <ul>
 *     <li>while its player has a choice to make ({@link PendingChoices}: their roll, a fork, a shop, a picker...),
 *     from the moment it is asked until it is made;</li>
 *     <li>for {@link #LANDING_TICKS} once it stops on a space (the end of a move: {@link #onLanded}).</li>
 * </ul>
 * Server-decided: checked every few ticks in each world, the list of beams sent to its players when it changes, and
 * every second while it is not empty (a player joining or coming from another world gets it then; a pawn the client
 * does not have, too far, still has its place). A party over, a pawn gone: its beam goes.
 */
public final class PawnBeams {
    /** How long a pawn that stopped on a space shows its beam: 4 seconds. */
    public static final int LANDING_TICKS = 80;
    /** The colour of a pawn without one (no Tokenizer Wand colour, no coloured name). */
    public static final int DEFAULT_COLOR = 0xFFFFFF;
    /** Every how many ticks the beams are checked; how often a list that is not empty is sent again. */
    private static final int CHECK_INTERVAL = 5, RESEND_INTERVAL = 20;

    /** When each pawn last stopped on a space (world time). */
    private static final Map<UUID, Long> LANDED = ServerMemory.forgetOnStop(new HashMap<>());
    /** What the players of each world were last sent. */
    private static final Map<RegistryKey<World>, List<PawnBeamsPayload.Beam>> SENT = ServerMemory.forgetOnStop(new HashMap<>());

    private PawnBeams() {
    }

    public static void initialize() {
        ServerTickEvents.END_WORLD_TICK.register(PawnBeams::tick);
    }

    /** {@code mob} stopped on a space (its move is over): in a running party, its beam shows for a while. */
    public static void onLanded(MobEntity mob) {
        if (mob.getWorld() instanceof ServerWorld world && PartyControllerEntity.isTokenInRunningParty(mob.getUuid()))
            LANDED.put(mob.getUuid(), world.getTime());
    }

    /** True while the token {@code token} shows its beam in {@code world}. */
    public static boolean isShown(ServerWorld world, UUID token) {
        Entity entity = world.getEntity(token);
        return entity != null && beams(world).stream().anyMatch(beam -> beam.entityId() == entity.getId());
    }

    /** The beams shown in {@code world} now: the pawns of its running parties waiting for a choice or just landed. */
    public static List<PawnBeamsPayload.Beam> beams(ServerWorld world) {
        List<PawnBeamsPayload.Beam> beams = new ArrayList<>();
        long now = world.getTime();
        for (PartyControllerEntity party : PartyControllerEntity.getActivePartyControllers()) {
            if (party.isRemoved() || party.getWorld() != world || !party.getPartyData().isStarted()) continue;
            for (UUID token : party.getPartyData().getTokens()) {
                if (!(world.getEntity(token) instanceof MobEntity mob) || !mob.isAlive()) continue;
                Long landed = LANDED.get(token);
                boolean shown = landed != null && now - landed >= 0 && now - landed < LANDING_TICKS
                        || PendingChoices.waits(party, world, mob);
                if (!shown || beams.stream().anyMatch(beam -> beam.entityId() == mob.getId())) continue;
                int color = PartyLiveData.colorOf(mob);
                beams.add(new PawnBeamsPayload.Beam(mob.getId(), color < 0 ? DEFAULT_COLOR : color,
                        (float) mob.getX(), (float) mob.getY(), (float) mob.getZ()));
            }
        }
        return beams;
    }

    private static void tick(ServerWorld world) {
        long now = world.getTime();
        if (now % CHECK_INTERVAL != 0) return;
        LANDED.values().removeIf(time -> now - time >= LANDING_TICKS || now < time);
        List<PawnBeamsPayload.Beam> beams = beams(world);
        List<PawnBeamsPayload.Beam> sent = SENT.getOrDefault(world.getRegistryKey(), List.of());
        if (!changed(sent, beams) && (beams.isEmpty() || now % RESEND_INTERVAL != 0)) return;
        SENT.put(world.getRegistryKey(), beams);
        PawnBeamsPayload payload = new PawnBeamsPayload(beams);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (ServerPlayNetworking.canSend(player, PawnBeamsPayload.ID)) ServerPlayNetworking.send(player, payload);
        }
    }

    /** Another pawn or colour (where they stand aside: the clients follow the pawns themselves). */
    private static boolean changed(List<PawnBeamsPayload.Beam> before, List<PawnBeamsPayload.Beam> now) {
        if (before.size() != now.size()) return true;
        for (int i = 0; i < now.size(); i++) {
            if (!before.get(i).sameAs(now.get(i))) return true;
        }
        return false;
    }
}
