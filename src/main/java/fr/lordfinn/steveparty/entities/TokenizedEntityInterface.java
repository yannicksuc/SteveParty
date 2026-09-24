package fr.lordfinn.steveparty.entities;

import net.minecraft.entity.player.PlayerEntity;
import org.joml.Vector3d;

import java.util.UUID;

public interface TokenizedEntityInterface {

    boolean steveparty$isTokenized();
    void steveparty$setTargetPosition(Vector3d target, double speed);

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
}
