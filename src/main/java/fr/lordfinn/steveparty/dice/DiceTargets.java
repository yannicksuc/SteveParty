package fr.lordfinn.steveparty.dice;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllers;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.StartRollsStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Which pawn a die thrown by a player goes around: his own, never another player's because it stood closer. */
public final class DiceTargets {
    /** How far from the die the thrower's own pawn is looked for (he may walk away from it during a party). */
    static final double OWN_PAWN_RANGE = 64;

    private DiceTargets() {
    }

    /**
     * The pawn of {@code thrower} his die goes around, null if he has none around: in a running party, his pawn whose
     * turn it is, else (the turn order rolls) his nearest pawn without a roll yet; else his nearest pawn.
     */
    public static @Nullable MobEntity ownPawn(ServerWorld world, PlayerEntity thrower, Vec3d from) {
        UUID id = thrower.getUuid();
        for (PartyControllerEntity party : PartyControllers.all()) {
            if (party.isRemoved() || party.getWorld() != world || !party.getPartyData().isStarted()) continue;
            PartyStep step = party.getPartyData().getCurrentStep();
            if (step instanceof TokenTurnPartyStep turn && turn.getTokenUUID() != null && party.isTokenOwnedBy(turn.getTokenUUID(), id)
                    && world.getEntity(turn.getTokenUUID()) instanceof MobEntity mob && mob.isAlive()) return mob;
            if (step instanceof StartRollsStep rolls) {
                MobEntity notRolled = nearest(ownPawns(world, id, from).stream()
                        .filter(mob -> party.getPartyData().getTokens().contains(mob.getUuid()))
                        .filter(mob -> rolls.rolls == null || !rolls.rolls.containsKey(mob.getUuid())).toList(), from);
                if (notRolled != null) return notRolled;
            }
        }
        return nearest(ownPawns(world, id, from), from);
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
