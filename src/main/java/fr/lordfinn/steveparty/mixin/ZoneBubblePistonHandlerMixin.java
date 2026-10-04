package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * A piston moves nothing across the border of a mini-game zone in session: if its head, a block it would push,
 * pull (slime and honey included) or break, or the place one of them would go to is on the other side, it does
 * not move at all. Applied after the other mods' mixins (priority): the check also sees a push they allowed.
 */
@Mixin(value = PistonHandler.class, priority = 2000)
public abstract class ZoneBubblePistonHandlerMixin {
    @Shadow
    @Final
    private World world;
    @Shadow
    @Final
    private BlockPos posFrom;
    @Shadow
    @Final
    private Direction pistonDirection;

    @Shadow
    public abstract List<BlockPos> getMovedBlocks();

    @Shadow
    public abstract List<BlockPos> getBrokenBlocks();

    @Shadow
    public abstract Direction getMotionDirection();

    @Inject(method = "calculatePush()Z", at = @At("RETURN"), cancellable = true)
    private void steveparty$noPushAcrossZoneBorder(CallbackInfoReturnable<Boolean> cir) {
        if (!ZoneBorder.ACTIVE || !cir.getReturnValueZ()) return;
        if (ZoneBorder.blocksPush(world, posFrom, posFrom.offset(pistonDirection), getMovedBlocks(), getBrokenBlocks(), getMotionDirection())) {
            cir.setReturnValue(false);
        }
    }
}
