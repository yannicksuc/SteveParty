package fr.lordfinn.steveparty.client.villager;

import fr.lordfinn.steveparty.blocks.custom.villager.VillagerExpression;

/**
 * The villager block's pose for one frame, filled by {@link VillagerBlockAnimator} (one reused instance: nothing is
 * allocated per frame, render thread only).
 * <p>
 * Applied by the renderer around the block's bottom centre: offset, then turn ({@link #yaw} plus the look yaw times
 * {@link #lookWeight}), tilt (the top toward the face's chin side, i.e. toward whoever it looks at), roll (around a
 * pivot {@link #rollPivot} blocks to the side: 0.5 tips it over its edge), then scale.
 */
public final class VillagerPose {
    public float offsetX, offsetY, offsetZ;
    public float yaw, tilt, roll, rollPivot;
    public float scaleX, scaleY, scaleZ;
    /** How much it follows its look target (1: fully, 0: faces its rest direction / straight up). */
    public float lookWeight;
    /** Drawn as a plain cobblestone block. */
    public boolean disguise;
    public VillagerExpression expression;

    public void reset() {
        offsetX = offsetY = offsetZ = 0;
        yaw = tilt = roll = rollPivot = 0;
        scaleX = scaleY = scaleZ = 1;
        lookWeight = 1;
        disguise = false;
        expression = VillagerExpression.NONE;
    }

    /** Vertical squash/stretch, half keeping the volume (wider when flattened, but not much into its neighbours). */
    public void squash(float sy) {
        scaleY *= sy;
        float side = Math.min(1.25f, 1f + (1f / (float) Math.sqrt(Math.max(sy, 0.05f)) - 1f) * 0.5f);
        scaleX *= side;
        scaleZ *= side;
    }
}
