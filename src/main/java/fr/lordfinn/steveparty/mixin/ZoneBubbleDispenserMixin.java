package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.block.BlockState;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.DropperBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A dispenser or a dropper facing the border of a mini-game zone in session, from either side, keeps what it
 * holds: nothing is thrown, placed or handed over across it.
 */
@Mixin({DispenserBlock.class, DropperBlock.class})
public abstract class ZoneBubbleDispenserMixin {

    @Inject(method = "dispense(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)V",
            at = @At("HEAD"), cancellable = true)
    private void steveparty$noDispenseAcrossZoneBorder(ServerWorld world, BlockState state, BlockPos pos, CallbackInfo ci) {
        if (!ZoneBorder.ACTIVE) return;
        if (ZoneBorder.across(world, pos, pos.offset(state.get(DispenserBlock.FACING)))) ci.cancel();
    }
}
