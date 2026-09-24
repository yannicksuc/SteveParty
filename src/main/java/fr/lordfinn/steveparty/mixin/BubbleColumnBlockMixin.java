package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.blocks.custom.PlasticBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.BubbleColumnBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Bubble columns go through plastic (blocks and studs): a column cell standing on plastic takes its source (soul
 * sand, magma or the column below) from under the plastic, so nothing made of plastic cuts the column.
 */
@Mixin(BubbleColumnBlock.class)
public abstract class BubbleColumnBlockMixin {
    @Redirect(method = "scheduledTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/world/ServerWorld;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
    private BlockState steveparty$sourceThroughPlastic(ServerWorld world, BlockPos below) {
        return PlasticBlock.getColumnSource(world, below);
    }

    @Redirect(method = "canPlaceAt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/WorldView;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
    private BlockState steveparty$supportThroughPlastic(WorldView world, BlockPos below) {
        return PlasticBlock.getColumnSource(world, below);
    }
}
