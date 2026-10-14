package fr.lordfinn.steveparty.dice;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllers;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.StartRollsStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Which pawn a die thrown by a player goes around: his own, never another player's because it stood closer. In a
 * running party the die never looks for anything else: his pawn (at most {@value #PARTY_PAWN_RANGE} blocks away), else
 * above his own head; sneaking, above his own head at once.
 */
public final class DiceTargets {
    /** How far from the die the thrower's own pawn is looked for (he may walk away from it during a party). */
    static final double OWN_PAWN_RANGE = 64;
    /** In a party, how far from the thrower his pawn may be: further, the die stays above his head. */
    public static final double PARTY_PAWN_RANGE = 30;

    private DiceTargets() {
    }

    /**
     * What a die thrown by {@code thrower} in a running party he plays in goes around: himself (above his head) when
     * he sneaks or when his pawn is missing or more than {@value #PARTY_PAWN_RANGE} blocks away, else his pawn. Null when
     * he plays in no running party (the die looks for its target as out of a party).
     */
    public static @Nullable LivingEntity inParty(ServerWorld world, PlayerEntity thrower) {
        PartyControllerEntity party = partyOf(world, thrower.getUuid());
        if (party == null) return null;
        if (thrower.isSneaking()) return thrower;
        MobEntity pawn = partyPawn(world, party, thrower.getUuid(), thrower.getPos());
        return pawn != null && pawn.squaredDistanceTo(thrower) <= PARTY_PAWN_RANGE * PARTY_PAWN_RANGE ? pawn : thrower;
    }

    /** The running party of this world {@code player} plays a pawn in, null for none. */
    public static @Nullable PartyControllerEntity partyOf(ServerWorld world, UUID player) {
        for (PartyControllerEntity party : PartyControllers.all()) {
            if (party.isRemoved() || party.getWorld() != world || !party.getPartyData().isStarted()) continue;
            if (party.getPlayersInOrder().contains(player)) return party;
        }
        return null;
    }

    /**
     * The pawn of {@code thrower} his die goes around, null if he has none around: in a running party, his pawn whose
     * turn it is, else (the turn order rolls) his nearest pawn without a roll yet; else his nearest pawn.
     */
    public static @Nullable MobEntity ownPawn(ServerWorld world, PlayerEntity thrower, Vec3d from) {
        UUID id = thrower.getUuid();
        for (PartyControllerEntity party : PartyControllers.all()) {
            if (party.isRemoved() || party.getWorld() != world || !party.getPartyData().isStarted()) continue;
            MobEntity pawn = turnPawn(world, party, id, from);
            if (pawn != null) return pawn;
        }
        return nearest(ownPawns(world, id, from), from);
    }

    /** His pawn in {@code party}: the one whose turn it is, the one still to roll for the turn order, else his nearest. */
    private static @Nullable MobEntity partyPawn(ServerWorld world, PartyControllerEntity party, UUID player, Vec3d from) {
        MobEntity pawn = turnPawn(world, party, player, from);
        if (pawn != null) return pawn;
        return nearest(ownPawns(world, player, from).stream().filter(mob -> party.getPartyData().getTokens().contains(mob.getUuid())).toList(), from);
    }

    private static @Nullable MobEntity turnPawn(ServerWorld world, PartyControllerEntity party, UUID player, Vec3d from) {
        PartyStep step = party.getPartyData().getCurrentStep();
        if (step instanceof TokenTurnPartyStep turn && turn.getTokenUUID() != null && party.isTokenOwnedBy(turn.getTokenUUID(), player)
                && world.getEntity(turn.getTokenUUID()) instanceof MobEntity mob && mob.isAlive()) return mob;
        if (step instanceof StartRollsStep rolls) {
            return nearest(ownPawns(world, player, from).stream()
                    .filter(mob -> party.getPartyData().getTokens().contains(mob.getUuid()))
                    .filter(mob -> rolls.rolls == null || !rolls.rolls.containsKey(mob.getUuid())).toList(), from);
        }
        return null;
    }

    private static List<MobEntity> ownPawns(ServerWorld world, UUID owner, Vec3d from) {
        return world.getEntitiesByClass(MobEntity.class, new Box(from, from).expand(OWN_PAWN_RANGE), mob -> mob.isAlive()
                && mob instanceof TokenizedEntityInterface token && token.steveparty$isTokenized()
                && owner.equals(token.steveparty$getTokenOwner()));
    }

    private static @Nullable MobEntity nearest(List<MobEntity> pawns, Vec3d from) {
        return pawns.stream().min(Comparator.comparingDouble(mob -> mob.squaredDistanceTo(from))).orElse(null);
    }
}
