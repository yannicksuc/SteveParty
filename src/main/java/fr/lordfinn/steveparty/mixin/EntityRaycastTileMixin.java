package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What a player aims at (crosshair, breaking, using, pick block): a lowered or sloped tile, which reaches down into the
 * cell of its slab or stairs, is found from every angle (see {@link BoardSpaces#preferTile}).
 */
@Mixin(Entity.class)
public abstract class EntityRaycastTileMixin {
    @Inject(method = "raycast(DFZ)Lnet/minecraft/util/hit/HitResult;", at = @At("RETURN"), cancellable = true)
    private void steveparty$aimAtLoweredTiles(double maxDistance, float tickDelta, boolean includeFluids,
                                              CallbackInfoReturnable<HitResult> cir) {
        Entity self = (Entity) (Object) this;
        Vec3d start = self.getCameraPosVec(tickDelta);
        Vec3d end = start.add(self.getRotationVec(tickDelta).multiply(maxDistance));
        HitResult hit = cir.getReturnValue();
        if (hit == null) return;
        HitResult tile = BoardSpaces.preferTile(self.getWorld(), start, end, hit);
        if (tile != hit) cir.setReturnValue(tile);
    }
}
