package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.fluid.FluidState;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lava sets nothing on fire across a mini-game zone's border. */
@Mixin(value = FluidState.class, priority = 2000)
public abstract class ZoneBubbleFluidRandomTickMixin {

    @Inject(method = "onRandomTick(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/random/Random;)V", at = @At("HEAD"))
    private void steveparty$enterRandomTick(World world, BlockPos pos, Random random, CallbackInfo ci) {
        if (ZoneBorder.ACTIVE) ZoneBorder.enter(world, pos);
    }

    @Inject(method = "onRandomTick(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/random/Random;)V", at = @At("RETURN"))
    private void steveparty$exitRandomTick(World world, BlockPos pos, Random random, CallbackInfo ci) {
        if (ZoneBorder.ACTIVE) ZoneBorder.exit(world);
    }
}
