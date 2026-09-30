package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.persistent_state.ShopProtection;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Explosions don't destroy the blocks of an owned shop (their contents would be scattered): see {@link ShopProtection}. */
@Mixin(ExplosionImpl.class)
public abstract class ExplosionImplMixin {
    @Shadow
    @Final
    private ServerWorld world;

    @Inject(method = "getBlocksToDestroy", at = @At("RETURN"), cancellable = true)
    private void steveparty$keepShopBlocks(CallbackInfoReturnable<List<BlockPos>> cir) {
        List<BlockPos> blocks = cir.getReturnValue();
        List<BlockPos> kept = new ArrayList<>(blocks.size());
        for (BlockPos pos : blocks) {
            if (!ShopProtection.isProtected(world, pos)) kept.add(pos);
        }
        if (kept.size() != blocks.size()) cir.setReturnValue(kept);
    }
}
