package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxSafeZone;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every monster keeps away from the Frousseux (FrousseuxSafeZone): one more goal, ahead of their attacks. */
@Mixin(HostileEntity.class)
public abstract class HostileFrousseuxMixin extends PathAwareEntity {
    protected HostileFrousseuxMixin(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void steveparty$avoidFrousseux(EntityType<? extends HostileEntity> entityType, World world, CallbackInfo ci) {
        if (world != null && !world.isClient) this.goalSelector.add(FrousseuxSafeZone.PRIORITY, new FrousseuxSafeZone.Avoid(this));
    }
}
