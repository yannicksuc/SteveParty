package fr.lordfinn.steveparty.dice;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A fast party's dice ({@link PartyControllerEntity#isFastParty}): a die thrown by one of its players stops by itself
 * {@value #STOP_SECONDS} seconds after it reached the pawn it goes around (or its thrower's head), which the chat tells
 * him. In a classic party the player stops it himself.
 */
public final class FastDice {
    public static final int STOP_SECONDS = 5;
    /** How close to its place above its target a die must be to have reached it, in blocks. */
    static final double ARRIVED = 0.75;

    private FastDice() {
    }

    /** @return true if {@code roller} plays a fast party in {@code world} */
    public static boolean inFastParty(ServerWorld world, @Nullable UUID roller) {
        if (roller == null) return false;
        PartyControllerEntity party = DiceTargets.partyOf(world, roller);
        return party != null && party.isFastParty();
    }

    /** @return true once {@code die} floats where it goes, above {@code target} (see AttractionSimulation) */
    public static boolean arrived(DiceEntity die, Entity target) {
        Vec3d place = target.getPos().add(0, target.getHeight() + die.getHeight() / 2 + 0.5, 0);
        return die.getPos().isInRange(place, ARRIVED);
    }

    /** The die reached its pawn: it stops by itself in {@value #STOP_SECONDS} seconds, the roller is told. */
    public static void arm(DiceEntity die) {
        die.sequence().stopIn(STOP_SECONDS * 20);
        ServerPlayerEntity roller = die.getOnlineOwner();
        if (roller != null) {
            MessageUtils.sendToPlayer(roller, Text.translatable("message.steveparty.dice.fast_stop", STOP_SECONDS).formatted(Formatting.GRAY),
                    MessageUtils.MessageType.CHAT);
        }
    }
}
