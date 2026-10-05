package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * An explosion only destroys blocks on its side of a mini-game zone's border: one started in the zone leaves the
 * world around untouched, one started outside leaves the zone untouched (and drops nothing of either). It hurts
 * nobody on the other side either ({@link ZoneBorder#blocksDamage}).
 * <p>
 * 1.21.1: {@code collectBlocksAndDamageEntities} collects the blocks then hurts the entities. Right after the blocks
 * are collected, the list is filtered and the explosion's place is set for the entity part; it is cleared on every
 * way out. Applied after the other mods' mixins (priority): the filter sees the list they leave.
 */
@Mixin(value = Explosion.class, priority = 2000)
public abstract class ZoneBubbleExplosionMixin {
    @Shadow
    @Final
    private World world;
    @Shadow
    @Final
    private ObjectArrayList<BlockPos> affectedBlocks;

    @Shadow
    public abstract Vec3d getPosition();

    @Inject(method = "collectBlocksAndDamageEntities", at = @At(value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/objects/ObjectArrayList;addAll(Ljava/util/Collection;)Z", shift = At.Shift.AFTER))
    private void steveparty$keepOtherSideOfZoneBorder(CallbackInfo ci) {
        if (!ZoneBorder.ACTIVE || !(world instanceof ServerWorld serverWorld)) return;
        Vec3d pos = getPosition();
        List<BlockPos> kept = ZoneBorder.explosionBlocks(serverWorld, pos, affectedBlocks);
        if (kept != affectedBlocks) {
            affectedBlocks.clear();
            affectedBlocks.addAll(kept);
        }
        // then the entities are hurt
        ZoneBorder.explosionAt(world, pos);
    }

    @Inject(method = "collectBlocksAndDamageEntities", at = @At("RETURN"))
    private void steveparty$exitExplosionDamage(CallbackInfo ci) {
        ZoneBorder.explosionAt(null, null);
    }
}
