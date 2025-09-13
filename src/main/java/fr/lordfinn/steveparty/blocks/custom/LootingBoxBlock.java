package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.text.Text.literal;

public class LootingBoxBlock extends CartridgeContainer implements BlockEntityProvider {
    public LootingBoxBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return Block.createCodec(LootingBoxBlock::new);
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
    protected ActionResult onUseWithoutCartridgeContainerOpener(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        return null;
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {

        world.scheduleBlockTick(pos, this, 1);
    }

    @Override
    public @Nullable LootingBoxBlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new LootingBoxBlockEntity(pos, state);
    }
}
