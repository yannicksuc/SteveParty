package fr.lordfinn.steveparty.mixin;

import net.minecraft.block.PistonBlock;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.util.math.Box;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

import static fr.lordfinn.steveparty.blocks.ModBlocks.VILLAGER_BLOCK;
import static net.minecraft.util.math.Direction.DOWN;

@Mixin(PistonBlock.class)
public class MixinPistonBlock {

    @Inject(
            method = "move(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;Z)Z",
            at = @At("TAIL")
    )
    private void steveparty$convertVillager(World world, BlockPos pos, Direction dir, boolean extend,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (world.isClient) {
            return;
        }

        if (dir != DOWN)
            return;

        BlockPos front = pos.offset(dir);
        Box box = new Box(front).expand(1.0);

        List<VillagerEntity> villagers = world.getEntitiesByClass(
                VillagerEntity.class, box, v -> true
        );
        for (VillagerEntity villager : villagers) {

            BlockPos vpos = villager.getBlockPos();
            if (!vpos.equals(front.offset(dir)))
                continue;

            boolean noBlockBelow = !(world.getBlockState(vpos).isSolidBlock(world, vpos));

            boolean hasAdultVillagerBelow = !villager.isBaby();

            if (noBlockBelow && hasAdultVillagerBelow) {
                villager.discard();
                world.setBlockState(vpos, VILLAGER_BLOCK.getDefaultState());
            }
        }
    }
}
