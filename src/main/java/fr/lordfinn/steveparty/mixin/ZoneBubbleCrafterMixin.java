package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.block.BlockState;
import net.minecraft.block.CrafterBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A crafter facing the border of a mini-game zone in session, from either side, crafts nothing across it. */
@Mixin(CrafterBlock.class)
public abstract class ZoneBubbleCrafterMixin {

    @Inject(method = "craft(Lnet/minecraft/block/BlockState;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;)V",
            at = @At("HEAD"), cancellable = true)
    private void steveparty$noCraftAcrossZoneBorder(BlockState state, ServerWorld world, BlockPos pos, CallbackInfo ci) {
        if (!ZoneBorder.ACTIVE) return;
        if (ZoneBorder.across(world, pos, pos.offset(state.get(Properties.ORIENTATION).getFacing()))) ci.cancel();
    }
}
