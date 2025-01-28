package fr.lordfinn.steveparty.utils;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;

public class RaycastUtils {

    public static boolean isTargetingBlock(PlayerEntity player) {
        double reachDistance = player.isCreative() ? 5.0 : 4.5;
        HitResult hitResult = player.raycast(reachDistance, 0.0F, false);
        return hitResult.getType() == HitResult.Type.BLOCK;
    }

    public static boolean isTargetingEntity(PlayerEntity player) {
        double reachDistance = player.isCreative() ? 5.0 : 4.5;
        HitResult hitResult = player.raycast(reachDistance, 0.0F, false);
        return hitResult.getType() == HitResult.Type.ENTITY;
    }

    public static boolean isTargetingBlockOrEntity(PlayerEntity player) {
        double reachDistance = player.isCreative() ? 5.0 : 4.5;
        HitResult hitResult = player.raycast(reachDistance, 0.0F, false);
        return hitResult.getType() == HitResult.Type.BLOCK || hitResult.getType() == HitResult.Type.ENTITY;
    }
}
