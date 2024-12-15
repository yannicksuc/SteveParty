package fr.lordfinn.steveparty;

import org.joml.Vector3d;

public interface TokenizedEntityInterface {

    boolean steveparty$isTokenized();
    void steveparty$setTokenized(boolean tokenized);
    void steveparty$setTargetPosition(Vector3d target, double speed);

    int steveparty$getNbSteps();

    void steveparty$setNbSteps(int nbSteps);
}
