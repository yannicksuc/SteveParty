package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What a block, a fluid or an entity does on its tick stays on its side of a mini-game zone's border: fire and water
 * don't spread across, a dispenser places nothing over it, an enderman takes no block from the other side, a
 * creeper breaks none ({@link ZoneBorder}). And nothing so started spawns an entity across.
 */
@Mixin(value = ServerWorld.class, priority = 2000)
public abstract class ZoneBubbleServerWorldMixin {

    @Inject(method = "tickBlock(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;)V", at = @At("HEAD"))
    private void steveparty$enterBlockTick(BlockPos pos, Block block, CallbackInfo ci) {
        if (ZoneBorder.ACTIVE) ZoneBorder.enter((ServerWorld) (Object) this, pos);
    }

    @Inject(method = "tickBlock(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;)V", at = @At("RETURN"))
    private void steveparty$exitBlockTick(BlockPos pos, Block block, CallbackInfo ci) {
        if (ZoneBorder.ACTIVE) ZoneBorder.exit((ServerWorld) (Object) this);
    }

    @Inject(method = "tickFluid(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/fluid/Fluid;)V", at = @At("HEAD"))
    private void steveparty$enterFluidTick(BlockPos pos, Fluid fluid, CallbackInfo ci) {
        if (ZoneBorder.ACTIVE) ZoneBorder.enter((ServerWorld) (Object) this, pos);
    }

    @Inject(method = "tickFluid(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/fluid/Fluid;)V", at = @At("RETURN"))
    private void steveparty$exitFluidTick(BlockPos pos, Fluid fluid, CallbackInfo ci) {
        if (ZoneBorder.ACTIVE) ZoneBorder.exit((ServerWorld) (Object) this);
    }

    @Inject(method = "tickEntity(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"))
    private void steveparty$enterEntityTick(Entity entity, CallbackInfo ci) {
        if (ZoneBorder.ACTIVE) ZoneBorder.enter((ServerWorld) (Object) this, entity.getBlockPos());
    }

    @Inject(method = "tickEntity(Lnet/minecraft/entity/Entity;)V", at = @At("RETURN"))
    private void steveparty$exitEntityTick(Entity entity, CallbackInfo ci) {
        if (ZoneBorder.ACTIVE) ZoneBorder.exit((ServerWorld) (Object) this);
    }

    @Inject(method = "addEntity(Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void steveparty$noSpawnAcrossZoneBorder(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!ZoneBorder.ACTIVE) return;
        if (ZoneBorder.blocksSpawn((ServerWorld) (Object) this, entity)) cir.setReturnValue(false);
    }
}
