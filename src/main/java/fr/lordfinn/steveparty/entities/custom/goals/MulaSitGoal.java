package fr.lordfinn.steveparty.entities.custom.goals;

import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.EnumSet;

/**
 * "Sit" for a flying Mula: the vanilla {@link net.minecraft.entity.ai.goal.SitGoal} requires the entity to be on
 * the ground, which a no-gravity Mula never is. Instead the Mula glides straight down and hovers low
 * ({@value #REST_HEIGHT} block above the ground) where it was ordered to sit, and stays there.
 * <p>
 * Uses the vanilla tameable state: {@link MulaEntity#isSitting()} (the order, saved in NBT as "Sitting") and
 * {@link MulaEntity#setInSittingPose} (the pose, synced to clients). Following the owner stops by itself while
 * sitting ({@link MulaEntity#cannotFollowOwner()}).
 */
public class MulaSitGoal extends Goal {
    /** Hovering height above the ground while sitting (blocks). */
    public static final double REST_HEIGHT = 0.4;
    /** Maximum distance scanned below the Mula to find the ground. */
    private static final int MAX_GROUND_SCAN = 16;
    private static final double DESCENT_SPEED = 0.1;

    private final MulaEntity entity;
    private Vec3d restPos;

    public MulaSitGoal(MulaEntity entity) {
        this.entity = entity;
        this.setControls(EnumSet.of(Control.JUMP, Control.MOVE));
    }

    @Override
    public boolean canStart() {
        return entity.isTamed() && entity.isSitting();
    }

    @Override
    public boolean shouldContinue() {
        return canStart();
    }

    @Override
    public void start() {
        entity.getNavigation().stop();
        entity.setInSittingPose(true);
        restPos = findRestPos();
    }

    @Override
    public void stop() {
        entity.setInSittingPose(false);
        restPos = null;
    }

    @Override
    public void tick() {
        if (restPos == null) restPos = findRestPos();
        // Pushed away (or the ground changed): settle again from where it is now
        if (entity.getPos().squaredDistanceTo(restPos) > 4.0) restPos = findRestPos();
        if (entity.getPos().squaredDistanceTo(restPos) > 0.04) {
            entity.getMoveControl().moveTo(restPos.x, restPos.y, restPos.z, DESCENT_SPEED);
        }
    }

    /** Straight below the Mula, {@link #REST_HEIGHT} above the first solid block (or where it is if none nearby). */
    private Vec3d findRestPos() {
        BlockPos pos = entity.getBlockPos();
        for (int i = 0; i < MAX_GROUND_SCAN && pos.getY() > entity.getWorld().getBottomY(); i++) {
            BlockPos below = pos.down();
            BlockState state = entity.getWorld().getBlockState(below);
            VoxelShape shape = state.getCollisionShape(entity.getWorld(), below);
            if (!shape.isEmpty()) {
                double groundY = below.getY() + shape.getMax(Direction.Axis.Y);
                return new Vec3d(entity.getX(), Math.min(entity.getY(), groundY + REST_HEIGHT), entity.getZ());
            }
            pos = below;
        }
        return entity.getPos();
    }
}
