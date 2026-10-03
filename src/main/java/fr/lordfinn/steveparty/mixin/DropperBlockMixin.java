package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.persistent_state.ShopProtection;
import net.minecraft.block.BlockState;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.DropperBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A dropper facing a merchant's container (see {@link ShopProtection#blocksAutomation}) gives it nothing: it fails,
 * clicking like an empty dropper. Cancelled before it looks for the container: Fabric's transfer API would otherwise
 * fall back to the block's item storage.
 */
@Mixin(DropperBlock.class)
public abstract class DropperBlockMixin {
    @Inject(method = "dispense(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)V", at = @At("HEAD"), cancellable = true)
    private void steveparty$noDropIntoMerchants(ServerWorld world, BlockState state, BlockPos pos, CallbackInfo ci) {
        if (!state.contains(DispenserBlock.FACING)) return;
        if (!ShopProtection.blocksAutomation(world, pos.offset(state.get(DispenserBlock.FACING)))) return;
        world.syncWorldEvent(WorldEvents.DISPENSER_FAILS, pos, 0);
        ci.cancel();
    }
}
