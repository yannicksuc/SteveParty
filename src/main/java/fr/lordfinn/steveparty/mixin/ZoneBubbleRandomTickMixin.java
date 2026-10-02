package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * What grows or spreads on a random tick stays on its side of a mini-game zone's border: grass, vines, stems and
 * their fruit, a sapling's tree (cut at the border), sculk.
 */
@Mixin(value = AbstractBlock.AbstractBlockState.class, priority = 2000)
public abstract class ZoneBubbleRandomTickMixin {

    @Inject(method = "randomTick(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/random/Random;)V", at = @At("HEAD"))
    private void steveparty$enterRandomTick(ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (ZoneBorder.ACTIVE) ZoneBorder.enter(world, pos);
    }

    @Inject(method = "randomTick(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/random/Random;)V", at = @At("RETURN"))
    private void steveparty$exitRandomTick(ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        if (ZoneBorder.ACTIVE) ZoneBorder.exit(world);
    }
}
