package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.blocks.custom.pipe.PipeTravel;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Landing after a fall of {@link PipeTravel#FALL_ENTRY} blocks or more on an upward mouth (anywhere on its top, even
 * when the block it is said to land on is the one next to it), or of any height when it was just thrown out of
 * another pipe: in it goes, before the fall hurts.
 */
@Mixin(Entity.class)
public abstract class PipeFallEntryMixin {
    @Inject(method = "fall", at = @At("HEAD"))
    private void steveparty$fallIntoPipe(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (onGround && entity.getWorld() instanceof ServerWorld world
                && (entity.fallDistance >= PipeTravel.FALL_ENTRY || PipeTravel.launched(entity)) && PipeTravel.fallOnto(world, entity)) {
            entity.fallDistance = 0;
        }
    }
}
