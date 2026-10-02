package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every block change of the world goes through here: in a mini-game zone in session, the block a position held is
 * remembered at its first change, and a change coming from the other side of the border does not happen
 * ({@link ZoneBorder#onBlockChange}).
 */
@Mixin(WorldChunk.class)
public abstract class ZoneBubbleWorldChunkMixin {

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Z)Lnet/minecraft/block/BlockState;",
            at = @At("HEAD"), cancellable = true)
    private void steveparty$journalZoneChange(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
        if (!ZoneBorder.ACTIVE) return;
        // null: the block stays as it is, as when it already is the one asked for
        if (ZoneBorder.onBlockChange((WorldChunk) (Object) this, pos, state)) cir.setReturnValue(null);
    }
}
