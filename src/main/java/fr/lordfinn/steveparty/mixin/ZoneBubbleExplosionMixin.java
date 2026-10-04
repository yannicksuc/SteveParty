package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * An explosion only destroys blocks on its side of a mini-game zone's border: one started in the zone leaves the
 * world around untouched, one started outside leaves the zone untouched (and drops nothing of either). It hurts
 * nobody on the other side either ({@link ZoneBorder#blocksDamage}).
 * <p>
 * Applied after the other mods' mixins (priority): the filter sees the list they return, early returns included,
 * and the explosion's place is cleared on every way out of {@code damageEntities}.
 */
@Mixin(value = ExplosionImpl.class, priority = 2000)
public abstract class ZoneBubbleExplosionMixin {
    @Shadow
    @Final
    private ServerWorld world;
    @Shadow
    @Final
    private Vec3d pos;

    @Inject(method = "getBlocksToDestroy", at = @At("RETURN"), cancellable = true)
    private void steveparty$keepOtherSideOfZoneBorder(CallbackInfoReturnable<List<BlockPos>> cir) {
        if (!ZoneBorder.ACTIVE) return;
        List<BlockPos> blocks = cir.getReturnValue();
        List<BlockPos> kept = ZoneBorder.explosionBlocks(world, pos, blocks);
        if (kept != blocks) cir.setReturnValue(kept);
    }

    /** Where it went off, for the entities it hurts: one with no entity behind it (a command, a mod) has no other place. */
    @Inject(method = "damageEntities", at = @At("HEAD"))
    private void steveparty$enterExplosionDamage(CallbackInfo ci) {
        if (ZoneBorder.ACTIVE) ZoneBorder.explosionAt(world, pos);
    }

    @Inject(method = "damageEntities", at = @At("RETURN"))
    private void steveparty$exitExplosionDamage(CallbackInfo ci) {
        ZoneBorder.explosionAt(null, null);
    }
}
