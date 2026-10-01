package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.items.custom.BoxCostumeBlock;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player hidden in a Box Costume who stops becomes a block of the grid: see {@link BoxCostumeBlock}. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityBoxCostumeMixin implements BoxCostumeBlock.Hider {
    @Unique
    private boolean steveparty$blockAligned = false;
    @Unique
    private int steveparty$stillTicks = 0;
    @Unique
    @Nullable
    private Vec3d steveparty$lastPos = null;

    @Inject(method = "tick", at = @At("TAIL"))
    private void steveparty$boxCostumeBlock(CallbackInfo ci) {
        BoxCostumeBlock.tick((PlayerEntity) (Object) this);
    }

    /** Block-aligned: the full cube of his cell, whatever his pose. */
    @Inject(method = "getBaseDimensions", at = @At("HEAD"), cancellable = true)
    private void steveparty$cubeDimensions(EntityPose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        EntityDimensions cube = BoxCostumeBlock.dimensions((PlayerEntity) (Object) this);
        if (cube != null) cir.setReturnValue(cube);
    }

    @Override
    public boolean steveparty$isBlockAligned() {
        return steveparty$blockAligned;
    }

    @Override
    public void steveparty$setBlockAligned(boolean aligned) {
        this.steveparty$blockAligned = aligned;
    }

    @Override
    public int steveparty$getStillTicks() {
        return steveparty$stillTicks;
    }

    @Override
    public void steveparty$setStillTicks(int ticks) {
        this.steveparty$stillTicks = ticks;
    }

    @Override
    public @Nullable Vec3d steveparty$getLastPos() {
        return steveparty$lastPos;
    }

    @Override
    public void steveparty$setLastPos(@Nullable Vec3d pos) {
        this.steveparty$lastPos = pos;
    }
}
