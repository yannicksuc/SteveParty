package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Glow ink on a tile, as on a sign: a glow ink sac makes the whole tile ({@link ATileBlock#GLOWING}) show at full light,
 * readable in the dark (its support stays lit as the world is); an ink sac puts it back to normal. One sac used each
 * time (none in creative), nothing used when nothing changes. The tile's item keeps it (see ATileBlock).
 * <p>
 * Decided the same way on both sides (the block state), so that the client predicts it.
 */
public final class TileGlow {
    private TileGlow() {
    }

    /** @return the result, or null when {@code stack} is no ink sac (the tile handles the click as usual) */
    public static @Nullable ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                                       PlayerEntity player, Hand hand) {
        boolean glow = stack.isOf(Items.GLOW_INK_SAC);
        if (!glow && !stack.isOf(Items.INK_SAC)) return null;
        if (!state.contains(ATileBlock.GLOWING)) return null;
        if (state.get(ATileBlock.GLOWING) == glow) return ActionResult.CONSUME;
        if (world.isClient) return ActionResult.SUCCESS;
        world.setBlockState(pos, state.with(ATileBlock.GLOWING, glow), Block.NOTIFY_ALL);
        world.playSound(null, pos, glow ? SoundEvents.ITEM_GLOW_INK_SAC_USE : SoundEvents.ITEM_INK_SAC_USE,
                SoundCategory.BLOCKS, 1.0F, 1.0F);
        if (world instanceof ServerWorld serverWorld) {
            // On the tile as it is seen (lowered, sloped, a large tile's middle)
            Vec3d centre = BoardSpaces.standPos(serverWorld, pos).add(0, 0.1, 0);
            serverWorld.spawnParticles(glow ? ParticleTypes.GLOW : ParticleTypes.SQUID_INK,
                    centre.x, centre.y, centre.z, glow ? 10 : 6, 0.3, 0.05, 0.3, 0.02);
        }
        world.emitGameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Emitter.of(player, state));
        if (!player.isCreative()) stack.decrement(1);
        return ActionResult.SUCCESS;
    }
}
