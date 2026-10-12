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
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Glow ink on a board space, as on a sign: a glow ink sac makes the whole tile or check point ({@link #GLOWING}) show
 * at full light, readable in the dark (a tile's support stays lit as the world is); an ink sac puts it back to normal.
 * One sac used each time (none in creative), nothing used when nothing changes. Its item keeps it (see ATileBlock,
 * CheckPointBlock).
 * <p>
 * Decided the same way on both sides (the block state), so that the client predicts it.
 */
public final class TileGlow {
    /**
     * Painted with glow ink: drawn at full light by its block entity renderer, not baked in the chunk. A property of
     * the board spaces that take glow ink (tiles, check points), false by default (and so in worlds saved before).
     */
    public static final BooleanProperty GLOWING = BooleanProperty.of("glowing");

    private TileGlow() {
    }

    /** @return the result, or null when {@code stack} is no ink sac (the space handles the click as usual) */
    public static @Nullable ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                                       PlayerEntity player, Hand hand) {
        boolean glow = stack.isOf(Items.GLOW_INK_SAC);
        if (!glow && !stack.isOf(Items.INK_SAC)) return null;
        if (!state.contains(GLOWING)) return null;
        if (state.get(GLOWING) == glow) return ActionResult.CONSUME;
        if (world.isClient) return ActionResult.SUCCESS;
        world.setBlockState(pos, state.with(GLOWING, glow), Block.NOTIFY_ALL);
        world.playSound(null, pos, glow ? SoundEvents.ITEM_GLOW_INK_SAC_USE : SoundEvents.ITEM_INK_SAC_USE,
                SoundCategory.BLOCKS, 1.0F, 1.0F);
        if (world instanceof ServerWorld serverWorld) {
            // On the tile as it is seen (lowered, sloped, a large tile's middle), around a check point's floating cube
            boolean tile = state.getBlock() instanceof ATileBlock;
            Vec3d centre = tile ? BoardSpaces.standPos(serverWorld, pos).add(0, 0.1, 0)
                    : state.getOutlineShape(serverWorld, pos).getBoundingBox().getCenter().add(Vec3d.of(pos));
            double spread = tile ? 0.3 : 0.15;
            serverWorld.spawnParticles(glow ? ParticleTypes.GLOW : ParticleTypes.SQUID_INK,
                    centre.x, centre.y, centre.z, glow ? 10 : 6, spread, tile ? 0.05 : spread, spread, 0.02);
        }
        world.emitGameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Emitter.of(player, state));
        if (!player.isCreative()) stack.decrement(1);
        return ActionResult.SUCCESS;
    }
}
