package fr.lordfinn.steveparty.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerSoul;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

import static fr.lordfinn.steveparty.blocks.ModBlocks.VILLAGER_BLOCK;
import static net.minecraft.util.math.Direction.DOWN;

/**
 * The villager block and pistons:
 * <ul>
 *   <li><b>made by a piston</b>: a piston (sticky or not) facing down, whose body is 2 blocks above an adult
 *   villager's feet (the villager's head right under the piston), extends onto it: the villager becomes a villager
 *   block where its feet were, keeping all its data (VillagerSoul);</li>
 *   <li><b>pulled out by a sticky piston</b>: a sticky piston retracting while its head touches the villager block
 *   (the block right in front of the head) doesn't pull it: the villager pops back out, the same villager;</li>
 *   <li><b>pushed</b> (or pulled along by slime...): it moves like any block, its data follows it.</li>
 * </ul>
 */
@Mixin(PistonBlock.class)
public class MixinPistonBlock {

    /**
     * The villager block has a block entity but pistons still push it, as before it had one: its data follows it
     * ({@link #steveparty$carryVillagerBlocks}).
     */
    @ModifyExpressionValue(
            method = "isMovable",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;hasBlockEntity()Z")
    )
    private static boolean steveparty$pushVillagerBlock(boolean hasBlockEntity, @Local(argsOnly = true) BlockState state) {
        return hasBlockEntity && !state.isOf(VILLAGER_BLOCK);
    }

    /**
     * A sticky piston retracting with a villager block at its head: the villager comes back out instead. Clients run
     * the same pull (block event): the block goes there too, or their piston would drag a villager block the server
     * no longer has and leave it at the piston's face (a ghost block, until the chunk is reloaded).
     */
    @Inject(
            method = "move(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;Z)Z",
            at = @At("HEAD")
    )
    private void steveparty$pullVillagerOut(World world, BlockPos pos, Direction dir, boolean extend,
                                            CallbackInfoReturnable<Boolean> cir) {
        // move(..., false) only happens for sticky pistons pulling a block
        if (extend) return;
        BlockPos pulled = pos.offset(dir, 2);
        if (!world.getBlockState(pulled).isOf(VILLAGER_BLOCK)) return;
        if (!(world instanceof ServerWorld server)) {
            world.setBlockState(pulled, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            return;
        }
        NbtCompound soul = world.getBlockEntity(pulled) instanceof VillagerBlockEntity villager ? villager.getSoul() : null;
        // gone before the piston handles it: nothing left to pull
        world.setBlockState(pulled, Blocks.AIR.getDefaultState());
        VillagerSoul.release(server, pulled, soul);
    }

    /** Pistons move blocks without their block entity: the villager inside a moved villager block comes along. */
    @ModifyExpressionValue(
            method = "move(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;Z)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/piston/PistonHandler;getMovedBlocks()Ljava/util/List;")
    )
    private List<BlockPos> steveparty$carryVillagerBlocks(List<BlockPos> moved, @Local(argsOnly = true) World world,
                                                          @Local(argsOnly = true) Direction dir,
                                                          @Local(argsOnly = true) boolean extend) {
        if (world.isClient) return moved;
        Direction motion = extend ? dir : dir.getOpposite();
        for (BlockPos from : moved) {
            if (world.getBlockEntity(from) instanceof VillagerBlockEntity villager) {
                VillagerSoul.carry(world, from.offset(motion), villager.keptData());
            }
        }
        return moved;
    }

    @Inject(
            method = "move(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;Z)Z",
            at = @At("TAIL")
    )
    private void steveparty$convertVillager(World world, BlockPos pos, Direction dir, boolean extend,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (world.isClient) {
            return;
        }

        // Only when the piston pushes down (extension), never on retraction
        if (!extend || dir != DOWN)
            return;

        // The piston head comes into `front`, the villager's head: its feet are the block below
        BlockPos front = pos.offset(dir);
        Box box = new Box(front).expand(1.0);

        List<VillagerEntity> villagers = world.getEntitiesByClass(
                VillagerEntity.class, box, v -> true
        );
        for (VillagerEntity villager : villagers) {

            BlockPos vpos = villager.getBlockPos();
            if (!vpos.equals(front.offset(dir)))
                continue;

            // The villager block replaces the villager's own position: only overwrite air/replaceable blocks
            // (never slabs, snow layers, chests...)
            boolean noBlockBelow = world.getBlockState(vpos).isReplaceable();

            boolean hasAdultVillagerBelow = !villager.isBaby();

            if (noBlockBelow && hasAdultVillagerBelow && !villager.isRemoved()) {
                // Its whole self goes into the block: profession, trades, name... (VillagerSoul)
                NbtCompound soul = VillagerSoul.capture(villager);
                villager.discard();
                world.setBlockState(vpos, VILLAGER_BLOCK.getDefaultState());
                if (world.getBlockEntity(vpos) instanceof VillagerBlockEntity block) block.setSoul(soul);
            }
        }
    }
}
