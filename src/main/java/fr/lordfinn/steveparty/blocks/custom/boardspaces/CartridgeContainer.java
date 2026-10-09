package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlock;
import fr.lordfinn.steveparty.items.custom.CartridgeContainerOpener;
import fr.lordfinn.steveparty.items.custom.TileLinkerBrushItem;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.util.ActionResult.*;

public abstract class CartridgeContainer extends Block implements BlockEntityProvider {
    protected CartridgeContainer(Settings settings, int numberOfCartridges) {
        super(settings);
        this.numberOfCartridges = numberOfCartridges;
    }
    protected final int numberOfCartridges;

    @Override
    public NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof NamedScreenHandlerFactory) {
            return (NamedScreenHandlerFactory) blockEntity;
        }
        return null;
    }

    @Override
    public boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return ScreenHandler.calculateComparatorOutput(world.getBlockEntity(pos));
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        // The decision must be the same on both sides, otherwise the client mispredicts (ghost blocks, missing swings...)
        ItemStack mainHandStack = player.getMainHandStack();
        ItemStack offHandStack = player.getOffHandStack();
        // Vanilla then calls onUse (main hand) on both sides
        if (mainHandStack.isEmpty() && offHandStack.isEmpty()) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        // The Wrench opens board spaces and routers, or swaps their cartridge for the off hand one (see WrenchActions)
        if (mainHandStack.getItem() instanceof WrenchItem && isLinkedWithWrench()) return ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        // The Tile Linker Brush paints links over every holder of a cartridge (see BrushLinks)
        if (mainHandStack.getItem() instanceof TileLinkerBrushItem) return ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        // A Wrench in the off hand lets the main hand item act
        boolean offHandOpener = offHandStack.getItem() instanceof CartridgeContainerOpener
                && (mainHandStack.isEmpty() || !(offHandStack.getItem() instanceof WrenchItem));
        if (!(mainHandStack.getItem() instanceof CartridgeContainerOpener) && !offHandOpener) {
            // Implementations must be client-safe (see ABoardSpaceBlock)
            return onUseWithoutCartridgeContainerOpener(stack, state, world, pos, player, hand, hit);
        }
        if (world.isClient) return ItemActionResult.SUCCESS;
        ActionResult success = openScreen(state, world, pos, player);
        if (success != null) return ItemActionResult.SUCCESS;
        return ItemActionResult.FAIL;
    }

    /** Board spaces and routers: the Wrench opens them (or swaps their cartridge), the Tile Linker Brush paints their links. */
    public boolean isLinkedWithWrench() {
        return this instanceof ABoardSpaceBlock || this instanceof BoardSpaceRedstoneRouterBlock;
    }

    /** Opens the interface of this container for {@code player} (server side), e.g. a right click with the Wrench. */
    public ActionResult openContainerScreen(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        ActionResult success = openScreen(state, world, pos, player);
        return success != null ? success : FAIL;
    }

    protected @Nullable ActionResult openScreen(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        NamedScreenHandlerFactory screenHandlerFactory = state.createScreenHandlerFactory(world, pos);
        if (screenHandlerFactory != null) {
            world.playSound(null, pos, ModSounds.OPEN_TILE_GUI_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
            player.openHandledScreen(screenHandlerFactory);
            return SUCCESS;
        }
        return null;
    }

    protected abstract ItemActionResult onUseWithoutCartridgeContainerOpener(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit);

    /** Cartridge containers don't tick by default; subclasses whose block entity needs it opt in. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    /**
     * Whether the cartridges spill out when {@code player} breaks it. A container that keeps them (a tile broken with
     * Silk Touch) records it on its block entity, read back by {@link #keepsContents} when the block goes.
     */
    protected boolean dropsContentsOnBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        return true;
    }

    /** Whether the cartridges of {@code blockEntity} go with the dropped block instead of spilling out. */
    protected boolean keepsContents(CartridgeContainerBlockEntity blockEntity) {
        return false;
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        // Decided here, where the player is known (a tile broken with Silk Touch keeps them): spilled when the block goes
        if (world.getBlockEntity(pos) instanceof CartridgeContainerBlockEntity) dropsContentsOnBreak(world, pos, state, player);
        super.onBreak(world, pos, state, player);
        return state;
    }

    /**
     * The cartridges spill out whatever removes the block (a player, an explosion, a command, a piston...), not only
     * a player breaking it: they were lost otherwise.
     */
    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && !world.isClient
                && world.getBlockEntity(pos) instanceof CartridgeContainerBlockEntity inventory && !keepsContents(inventory)) {
            ItemScatterer.spawn(world, pos, inventory);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
