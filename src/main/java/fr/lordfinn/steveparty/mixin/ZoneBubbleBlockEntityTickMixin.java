package fr.lordfinn.steveparty.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * What a block entity does on its tick stays on its side of a mini-game zone's border, like the tick of a block
 * ({@link ZoneBubbleServerWorldMixin}): a sculk catalyst spreads no sculk across it, a machine of another mod places
 * or breaks no block over it. Its position is the origin of the tick ({@link ZoneBorder#enter}). With no session, one
 * read of {@link ZoneBorder#ACTIVE}.
 */
@Mixin(targets = "net.minecraft.world.chunk.WorldChunk$DirectBlockEntityTickInvoker", priority = 2000)
public abstract class ZoneBubbleBlockEntityTickMixin {

    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/entity/BlockEntityTicker;tick(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/entity/BlockEntity;)V"))
    private void steveparty$tickOnItsSide(BlockEntityTicker<BlockEntity> ticker, World world, BlockPos pos, BlockState state,
                                          BlockEntity blockEntity, Operation<Void> original) {
        if (!ZoneBorder.ACTIVE) {
            original.call(ticker, world, pos, state, blockEntity);
            return;
        }
        ZoneBorder.enter(world, pos);
        try {
            original.call(ticker, world, pos, state, blockEntity);
        } finally {
            ZoneBorder.exit(world);
        }
    }
}
