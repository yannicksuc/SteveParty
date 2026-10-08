package fr.lordfinn.steveparty.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxCompanion;
import net.minecraft.block.BlockState;
import net.minecraft.block.CobwebBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A tamed Frousseux by its owner burns their way through cobwebs: they are not slowed, as through air (and can
 * still break them). Checked the same on both sides (FrousseuxCompanion#shieldsFromWebs, from the entities each
 * side sees), so the client's movement and the server's agree.
 */
@Mixin(CobwebBlock.class)
public abstract class CobwebFrousseuxMixin {
    @WrapOperation(method = "onEntityCollision", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;slowMovement(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/Vec3d;)V"))
    private void steveparty$frousseuxBurnsWebs(Entity entity, BlockState state, Vec3d multiplier, Operation<Void> original) {
        if (!FrousseuxCompanion.shieldsFromWebs(entity)) original.call(entity, state, multiplier);
    }
}
