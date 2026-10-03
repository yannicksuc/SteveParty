package fr.lordfinn.steveparty.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.lordfinn.steveparty.blocks.custom.PlasticBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.BubbleColumnBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Bubble columns go through plastic (blocks and studs): a column cell standing on plastic takes its source (soul
 * sand, magma or the column below) from under the plastic, so nothing made of plastic cuts the column.
 * <p>
 * Wrapped rather than redirected: another mod may hook the same calls (a redirect is exclusive and would make the
 * game fail to start), and the original read still happens first.
 */
@Mixin(BubbleColumnBlock.class)
public abstract class BubbleColumnBlockMixin {
    @WrapOperation(method = "scheduledTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/world/ServerWorld;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
    private BlockState steveparty$sourceThroughPlastic(ServerWorld world, BlockPos below, Operation<BlockState> original) {
        BlockState state = original.call(world, below);
        return PlasticBlock.isPlasticPiece(state) ? PlasticBlock.getColumnSource(world, below) : state;
    }

    @WrapOperation(method = "canPlaceAt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/WorldView;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
    private BlockState steveparty$supportThroughPlastic(WorldView world, BlockPos below, Operation<BlockState> original) {
        BlockState state = original.call(world, below);
        return PlasticBlock.isPlasticPiece(state) ? PlasticBlock.getColumnSource(world, below) : state;
    }
}
