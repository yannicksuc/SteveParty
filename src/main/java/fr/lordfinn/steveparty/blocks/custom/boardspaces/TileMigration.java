package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.ServerTask;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Worlds saved before the tiles were renamed: {@code simple_tile} is now {@code tile}, and {@code tile} is now
 * {@code advanced_tile}.
 * <ul>
 *     <li>ids of the old Tile ({@code simple_tile}, its block entity {@code simple_tile_entity}) are aliases of the new (LegacyIds);</li>
 *     <li>the old Advanced Tile's block id is now the Tile's, but its block entity ({@code tile_entity}, an alias of
 *     {@code advanced_tile}) still says what it was: such a Tile holding an Advanced Tile's block entity is turned back
 *     into an Advanced Tile as soon as it loads, with all its cartridges and data.</li>
 * </ul>
 * Items can't tell: an old Advanced Tile item loads as a Tile item.
 */
public final class TileMigration {
    private TileMigration() {
    }

    public static void initialize() {
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, world) -> {
            if (blockEntity instanceof AdvancedTileBlockEntity && blockEntity.getCachedState().isOf(ModBlocks.TILE)) {
                BlockPos pos = blockEntity.getPos().toImmutable();
                // Not while the chunk is loading: queued (execute() would run it right away on the server thread)
                world.getServer().send(new ServerTask(world.getServer().getTicks(), () -> migrate(world, pos)));
            }
        });
    }

    /** Turns the Tile at {@code pos} holding an Advanced Tile's block entity into that Advanced Tile. */
    public static boolean migrate(ServerWorld world, BlockPos pos) {
        if (!(world.getBlockEntity(pos) instanceof AdvancedTileBlockEntity old)) return false;
        BlockState state = world.getBlockState(pos);
        if (!state.isOf(ModBlocks.TILE)) return false;
        NbtCompound data = old.createNbt(world.getRegistryManager());
        // Gone first: replacing the block must not drop its cartridges
        world.removeBlockEntity(pos);
        world.setBlockState(pos, ModBlocks.ADVANCED_TILE.getStateWithProperties(state), Block.NOTIFY_ALL);
        if (!(world.getBlockEntity(pos) instanceof AdvancedTileBlockEntity advanced)) return false;
        advanced.read(data, world.getRegistryManager());
        advanced.update();
        Steveparty.LOGGER.info("Tile at {} was an Advanced Tile (saved before the tiles were renamed): restored", pos);
        return true;
    }
}
