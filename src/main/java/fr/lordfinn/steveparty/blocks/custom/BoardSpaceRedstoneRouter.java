package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainer;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.jetbrains.annotations.Nullable;

public class BoardSpaceRedstoneRouter extends CartridgeContainer {
    public static final MapCodec<BoardSpaceRedstoneRouter> CODEC = Block.createCodec(BoardSpaceRedstoneRouter::new);
    public BoardSpaceRedstoneRouter(Settings settings) {
        super(settings);
    }

    @Override
    protected ActionResult onUseWithoutCartridgeContainerOpener(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        return ActionResult.PASS;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BoardSpaceRedstoneRouterBlockEntity(pos, state);
    }

    @Override
    protected MapCodec<BoardSpaceRedstoneRouter> getCodec() {
        return CODEC;
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, @Nullable WireOrientation wireOrientation, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, wireOrientation, notify);
        if (world.isClient) return;
        if (world.getBlockEntity(pos) instanceof BoardSpaceRedstoneRouterBlockEntity entity) {
            entity.updateRoutedDestinations();
        }
    }
}
