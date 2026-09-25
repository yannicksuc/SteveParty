package fr.lordfinn.steveparty.entities.custom.goals;

import net.minecraft.entity.ai.control.BodyControl;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Body rotation of a Mula: it eases towards where it flies (or, hovering, towards where it looks) a share of the gap
 * each tick. The vanilla one copies the yaw at once while moving and, standing still, swings the body round in one go
 * after 10 ticks: on a creature whose whole body is its head, both read as jerks.
 * Runs on both sides like the vanilla one (the client uses it to render).
 */
public class MulaBodyControl extends BodyControl {
    /** Share of the remaining angle turned each tick, and the most turned in one tick (degrees). */
    private static final float SHARE = 0.2f, MAX_STEP = 15f;
    /** Squared horizontal movement (blocks) above which the Mula is moving (same threshold as vanilla). */
    private static final double MOVING = 2.5E-7;

    private final MobEntity mob;

    public MulaBodyControl(MobEntity mob) {
        super(mob);
        this.mob = mob;
    }

    @Override
    public void tick() {
        double dx = mob.getX() - mob.prevX;
        double dz = mob.getZ() - mob.prevZ;
        float target = dx * dx + dz * dz > MOVING ? mob.getYaw() : mob.headYaw;
        float gap = MathHelper.wrapDegrees(target - mob.bodyYaw);
        mob.bodyYaw += MathHelper.clamp(gap * SHARE, -MAX_STEP, MAX_STEP);
    }
}
