package fr.lordfinn.steveparty.blocks.custom;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import static net.minecraft.text.Text.literal;

public class LootingBoxBlock extends Block {
    public LootingBoxBlock(Settings settings) {
        super(settings);
    }

    public static void testForCollision(ServerPlayerEntity player) {
        if (player.isOnGround()) return;
        double playerHeight =  player.getBoundingBox().maxY;
        BlockPos posBlockAbove = player.getBlockPos().add(0, (int)(playerHeight + 0.2f), 0);
        Block blockAbove = player.getWorld().getBlockState(posBlockAbove).getBlock();
        if (blockAbove instanceof LootingBoxBlock)
            player.sendMessage(literal("Bravo t'as touché"), false);
    }

    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        world.scheduleBlockTick(pos, this, 1);
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {

        world.scheduleBlockTick(pos, this, 1);
    }
}
