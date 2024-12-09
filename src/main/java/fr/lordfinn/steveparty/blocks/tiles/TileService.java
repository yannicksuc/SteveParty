package fr.lordfinn.steveparty.blocks.tiles;
import fr.lordfinn.steveparty.components.TileBehaviorComponent;
import fr.lordfinn.steveparty.items.tilebehaviors.TileBehaviorItem;
import fr.lordfinn.steveparty.components.ModComponents;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class TileService {
    /**
     * Retrieves the destinations from the inventory of the block at the given position.
     *
     * @param tileEntity The block to retrieve the destinations from.
     * @return A list of destinations with their positions and whether they are tiles.
     */
    public static List<TileDestination> getCurrentDestinations(TileEntity tileEntity, int slotNumber) {
        List<TileDestination> tileDestinations = new ArrayList<>();
        DefaultedList<ItemStack> stacks = tileEntity.getItems(); // Replace with the actual method if different.

        if (stacks != null
                && slotNumber < stacks.size() && slotNumber >= 0
                && !stacks.get(slotNumber).isEmpty()) {
            ItemStack stack = stacks.get(slotNumber);

            // Check if the item in the first slot is of type TileBehavior.
            if (stack.getItem() instanceof TileBehaviorItem) {
                TileBehaviorComponent component = stack.getOrDefault(ModComponents.TILE_BEHAVIOR_COMPONENT, TileBehaviorComponent.DEFAULT_TILE_BEHAVIOR);
                List<BlockPos> destinations = new ArrayList<>(component.destinations()); // Copy destinations to a new list.
                tileDestinations = getDestinationsStatus(destinations, tileEntity.getWorld());
            }
        }
        return tileDestinations;
    }

    public static List<TileDestination> getDestinationsStatus(List<BlockPos> blockPosList, World world) {
        List<TileDestination> tileDestinations = new ArrayList<>();
        for (BlockPos pos : blockPosList) {
            boolean isTile = isTileBlock(pos, world);
            tileDestinations.add(new TileDestination(pos, isTile));
        }
        return tileDestinations;
    }

    /**
     * Checks if the given position is a valid tile block in the world.
     *
     * @param pos The position to check.
     * @param world The world instance to check the block in.
     * @return true if the position is a valid tile block, false otherwise.
     */
    private static boolean isTileBlock(BlockPos pos, World world) {
        if (world == null) return false;
        BlockState blockState = world.getBlockState(pos);
        if (blockState == null) return false;
        return blockState.getBlock() instanceof Tile;
    }
}

