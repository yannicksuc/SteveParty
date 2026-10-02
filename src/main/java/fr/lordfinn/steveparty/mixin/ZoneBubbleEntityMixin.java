package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.block.Portal;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The wall of a mini-game zone in session: every move of an entity ends here, whatever makes it (walking, falling,
 * being pushed, pulled, thrown, carried or teleported), and one that would cross the border stops at it
 * ({@link ZoneBorder#wall}). And the portals of the zone take nobody anywhere.
 */
@Mixin(Entity.class)
public abstract class ZoneBubbleEntityMixin {

    @Inject(method = "setPos(DDD)V", at = @At("HEAD"), cancellable = true)
    private void steveparty$stopAtZoneBorder(double x, double y, double z, CallbackInfo ci) {
        if (!ZoneBorder.ACTIVE) return;
        Entity self = (Entity) (Object) this;
        Vec3d stopped = ZoneBorder.wall(self, x, y, z);
        if (stopped == null) return;
        ci.cancel();
        // on its side of the border: goes through
        self.setPos(stopped.x, stopped.y, stopped.z);
    }

    @Inject(method = "tryUsePortal(Lnet/minecraft/block/Portal;Lnet/minecraft/util/math/BlockPos;)V", at = @At("HEAD"), cancellable = true)
    private void steveparty$noPortalInZone(Portal portal, BlockPos pos, CallbackInfo ci) {
        if (!ZoneBorder.ACTIVE) return;
        if (ZoneBorder.blocksPortal((Entity) (Object) this, pos)) ci.cancel();
    }
}
