package fr.lordfinn.steveparty.entities;

import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;

import java.util.function.ObjIntConsumer;

/**
 * Poses of the pawns made of mobs (any mob, vanilla or modded): a right click with an empty hand freezes the pawn in
 * its next pose.
 * <p>
 * The server only keeps a number per pawn ({@link TokenizedEntityInterface#steveparty$getTokenPose}, saved and synced);
 * each client turns it into a pose of the mob's own model, among the poses that model really has (the client side
 * finds them once per mob type, the same way on every client). The number keeps counting: the client takes it
 * modulo its number of poses.
 * <p>
 * The other clicks keep their use (items acting on entities, shearing, a horse ridden with an empty hand: sneak to
 * pose it). Player pawns have their own statue poses.
 */
public final class TokenPoses {
    /** The number wraps here, far above any mob's number of poses. */
    private static final int WRAP = 1 << 16;
    /** Client side: tells the player which pose the pawn takes (set by the client). */
    private static ObjIntConsumer<MobEntity> clientFeedback = (mob, pose) -> {
    };

    private TokenPoses() {
    }

    public static void setClientFeedback(ObjIntConsumer<MobEntity> feedback) {
        clientFeedback = feedback;
    }

    /** Whether this click poses the pawn: a mob pawn, the main hand empty (a horse: sneaking, else it is ridden). */
    public static boolean posesOnClick(MobEntity mob, PlayerEntity player, Hand hand) {
        if (!TokenBase.isToken(mob) || mob instanceof PlayerPawnEntity || player.isSpectator()) return false;
        if (hand != Hand.MAIN_HAND || !player.getMainHandStack().isEmpty()) return false;
        return !(mob instanceof AbstractHorseEntity) || player.isSneaking();
    }

    /** The next pose (server side; the client shows which one it will be). */
    public static void nextPose(MobEntity mob) {
        TokenizedEntityInterface token = (TokenizedEntityInterface) mob;
        int next = (token.steveparty$getTokenPose() + 1) % WRAP;
        if (mob.getWorld().isClient) {
            clientFeedback.accept(mob, next);
            return;
        }
        token.steveparty$setTokenPose(next);
        mob.getWorld().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENTITY_ARMOR_STAND_HIT,
                SoundCategory.NEUTRAL, 0.5F, 1.6F);
    }
}
