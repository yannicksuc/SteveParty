package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A block change and all it sets off (neighbours reacting, and theirs) stay on its side of a mini-game zone's border:
 * a lever of the zone powers no wire out of it, the half of a door across the border does not follow.
 * <p>
 * Applied after the other mods' mixins (priority), like every mixin that enters an origin: the exit is then also
 * put on the returns they add, so that an origin entered is always left.
 */
@Mixin(value = World.class, priority = 2000)
public abstract class ZoneBubbleWorldMixin {

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At("HEAD"))
    private void steveparty$enterZoneOrigin(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if (ZoneBorder.ACTIVE) ZoneBorder.enterChange((World) (Object) this, pos);
    }

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At("RETURN"))
    private void steveparty$exitZoneOrigin(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if (ZoneBorder.ACTIVE) ZoneBorder.exitChange((World) (Object) this);
    }
}
