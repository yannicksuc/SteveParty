package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.TokenizedEntityInterface;
import fr.lordfinn.steveparty.components.MobEntityComponent;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

import static fr.lordfinn.steveparty.components.ModComponents.MOB_ENTITY_COMPONENT;

public class TokenMovementService {

    public static void moveEntity(MobEntity mob, BlockPos target, PlayerEntity user) {
        Vector3d preciseTargetPos = calculateTargetPosition(user, target);
        double distance = mob.squaredDistanceTo(preciseTargetPos.x(), preciseTargetPos.y(), preciseTargetPos.z());

        if (isTooFar(distance)) {
            teleportEntity(mob, user, target, preciseTargetPos);
        } else {
            moveEntityToTarget(mob, preciseTargetPos);
            playSound(user, target, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP);
        }
    }

    private static Vector3d calculateTargetPosition(PlayerEntity user, BlockPos targetPos) {
        BlockState blockState = user.getWorld().getBlockState(targetPos);
        double blockHeight = blockState.getCollisionShape(user.getWorld(), targetPos).getMax(Direction.Axis.Y);
        return new Vector3d(targetPos.getX() + 0.5, targetPos.getY() + blockHeight, targetPos.getZ() + 0.5);
    }

    private static boolean isTooFar(double distance) {
        return distance > 400; // 20 blocks squared
    }

    private static void teleportEntity(MobEntity mob, PlayerEntity user, BlockPos targetPos, Vector3d preciseTargetPos) {
        mob.setPosition(preciseTargetPos.x(), preciseTargetPos.y(), preciseTargetPos.z());
        playSound(user, targetPos, SoundEvents.ENTITY_ENDERMAN_TELEPORT);
    }

    private static void moveEntityToTarget(MobEntity mob, Vector3d target) {
        // Set the target position (if applicable to your custom interface)
        if (mob instanceof TokenizedEntityInterface tokenizedEntity) {
            tokenizedEntity.steveparty$setTargetPosition(target, 0.5);
        }

        // Calculate rotation
        double deltaX = target.x() - mob.getX();
        double deltaZ = target.z() - mob.getZ();
        float yaw = (float) (Math.atan2(deltaZ, deltaX) * (180 / Math.PI)) - 90; // Convert radians to degrees
        mob.setYaw(yaw);

        // Optionally update pitch for vertical rotation
        double deltaY = target.y() - mob.getY();
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float pitch = (float) -(Math.atan2(deltaY, horizontalDistance) * (180 / Math.PI)); // Convert radians to degrees
        mob.setPitch(pitch);
    }

    private static void playSound(PlayerEntity user, BlockPos targetPos, SoundEvent soundEvent) {
        user.getWorld().playSound(
                null, // Null plays sound to all nearby players
                targetPos,
                soundEvent,
                SoundCategory.PLAYERS,
                100,
                1.0F
        );
    }
}
