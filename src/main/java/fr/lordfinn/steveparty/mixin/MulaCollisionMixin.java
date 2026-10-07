package fr.lordfinn.steveparty.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * A Mula's moves look for the entities in its way among the players only ({@link MulaEntity#entityCollisions}): the
 * vanilla query went through every entity round its path at each move, in a crowd all the other Mulas, which it
 * passes through anyway.
 */
@Mixin(Entity.class)
public abstract class MulaCollisionMixin {
    @WrapOperation(method = "adjustMovementForCollisions(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getEntityCollisions(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/util/List;"))
    private List<VoxelShape> steveparty$mulaCollisions(World world, Entity entity, Box box, Operation<List<VoxelShape>> original) {
        if (entity instanceof MulaEntity) return MulaEntity.entityCollisions(world, entity, box);
        return original.call(world, entity, box);
    }
}
