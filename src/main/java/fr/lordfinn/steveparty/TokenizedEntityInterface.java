package fr.lordfinn.steveparty;

import net.minecraft.entity.player.PlayerEntity;
import org.joml.Vector3d;

import java.util.UUID;

public interface TokenizedEntityInterface {

    boolean steveparty$isTokenized();
    void steveparty$setTargetPosition(Vector3d target, double speed);

    int steveparty$getNbSteps();

    void steveparty$setNbSteps(int nbSteps);

    void steveparty$setTokenized(boolean tokenized);

    void steveparty$setTokenOwner(PlayerEntity owner);
    void steveparty$setTokenOwner(UUID owner);

    UUID steveparty$getTokenOwner();
}
