package fr.lordfinn.steveparty.entities;

import net.minecraft.entity.player.PlayerEntity;
import org.joml.Vector3d;

import java.util.UUID;

public interface TokenizedEntityInterface {

    boolean steveparty$isTokenized();
    void steveparty$setTargetPosition(Vector3d target, double speed);

    /** Drops the position it was moving to (it stops where it is, without arriving anywhere). */
    void steveparty$stopMoving();

    int steveparty$getNbSteps();

    void steveparty$setNbSteps(int nbSteps);

    int steveparty$getStatus();

    void steveparty$setStatus(int status);

    void steveparty$setTokenized(boolean tokenized);

    void steveparty$setTokenOwner(PlayerEntity owner);
    void steveparty$setTokenOwner(UUID owner);

    UUID steveparty$getTokenOwner();

    /**
     * Size chosen with the Tokenizer Wand spell: the token's biggest dimension (height, or width if wider than tall),
     * in blocks. 0 when never chosen (the squish effect then uses its amplifier, as before).
     */
    float steveparty$getTokenSize();

    void steveparty$setTokenSize(float size);

    /** Token colour (0xRRGGBB) computed from the mob texture by the client, or -1 if never set. */
    int steveparty$getTokenColor();

    void steveparty$setTokenColor(int color);

    /**
     * Age at which this side first ticked it as a token (a pawn: its idle animations stay frozen on that frame), -1
     * when it is not a token. Not saved nor synced.
     */
    int steveparty$getPawnAge();

    /**
     * Pose the pawn is frozen in, cycled with a right click (see {@code TokenPoses}). Only a number: each client turns
     * it into a pose of the mob's own model (0 = the still default), the number of poses being the client's business.
     */
    int steveparty$getTokenPose();

    void steveparty$setTokenPose(int pose);
}
