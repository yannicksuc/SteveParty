package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.world.World;
import net.minecraft.world.block.ChainRestrictedNeighborUpdater;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a mini-game zone is put back as it was, the neighbours of a restored block are told nothing: every block
 * comes back in the state it had, so there is nothing to react to (a piston must not move for a lever that is
 * only passing).
 */
@Mixin(ChainRestrictedNeighborUpdater.class)
public abstract class ZoneBubbleNeighborUpdaterMixin {
    @Shadow
    @Final
    private World world;

    @Inject(method = "enqueue", at = @At("HEAD"), cancellable = true)
    private void steveparty$quietZoneRestoration(CallbackInfo ci) {
        // the server's worlds only: a client in the same game goes on as usual
        if (ZoneBorder.RESTORING && !world.isClient) ci.cancel();
    }
}
