package fr.lordfinn.steveparty.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlock;
import fr.lordfinn.steveparty.client.pipe.PipeTravellerPose;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The view of a player travelling in a pipe ({@link PipeTravellerPose#updateView}): worked out each frame, the camera
 * in the middle of the tube where the traveller is at this frame, turning with the pipe; the player's own look (the
 * mouse) is added to it, untouched. In third person the pipes do not hold the camera back.
 */
@Mixin(Camera.class)
public abstract class CameraPipeViewMixin {
    @Unique
    private boolean steveparty$inPipe;

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F"))
    private float steveparty$pipeYaw(Entity focused, float tickDelta, Operation<Float> original) {
        // First (the yaw is read first): the player's look may take the turn of the pipe it just left
        steveparty$inPipe = PipeTravellerPose.updateView(focused, tickDelta);
        float yaw = original.call(focused, tickDelta);
        return steveparty$inPipe ? yaw + PipeTravellerPose.viewYaw() : yaw;
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F"))
    private float steveparty$pipePitch(Entity focused, float tickDelta, Operation<Float> original) {
        float pitch = original.call(focused, tickDelta);
        return steveparty$inPipe ? MathHelper.clamp(pitch + PipeTravellerPose.viewPitch(), -90, 90) : pitch;
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V"))
    private void steveparty$pipePos(Camera camera, double x, double y, double z, Operation<Void> original,
                                    @Local(argsOnly = true) Entity focused, @Local(argsOnly = true) float tickDelta) {
        if (steveparty$inPipe) {
            Vector3d at = PipeTravellerPose.viewPos(focused, tickDelta);
            original.call(camera, at.x, at.y, at.z);
        } else {
            original.call(camera, x, y, z);
        }
    }

    /**
     * In third person, the camera stands back as from a full size player: the traveller's hitbox is shrunk inside (the
     * camera would come close with it), but it is drawn big.
     */
    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"))
    private float steveparty$pipeDistance(float distance) {
        return steveparty$inPipe ? Math.max(distance, 4.0F) : distance;
    }

    /**
     * In third person, the pipes do not push the camera against the traveller (it is inside them): the camera keeps
     * its distance, held back only by the other blocks.
     */
    @WrapOperation(method = "clipToSpace", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/BlockView;raycast(Lnet/minecraft/world/RaycastContext;)Lnet/minecraft/util/hit/BlockHitResult;"))
    private BlockHitResult steveparty$throughPipes(BlockView area, RaycastContext context, Operation<BlockHitResult> original) {
        if (!steveparty$inPipe) return original.call(area, context);
        return BlockView.raycast(context.getStart(), context.getEnd(), context, (ray, pos) -> {
            BlockState state = area.getBlockState(pos);
            if (PipeBlock.isPipe(state)) return null;
            return area.raycastBlock(ray.getStart(), ray.getEnd(), pos, ray.getBlockShape(state, area, pos), state);
        }, ray -> {
            Vec3d way = ray.getStart().subtract(ray.getEnd());
            return BlockHitResult.createMissed(ray.getEnd(), Direction.getFacing(way.x, way.y, way.z), BlockPos.ofFloored(ray.getEnd()));
        });
    }
}
