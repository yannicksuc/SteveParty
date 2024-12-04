package fr.lordfinn.steveparty.blocks;
import fr.lordfinn.steveparty.components.TileBehaviorComponent;
import fr.lordfinn.steveparty.items.TileBehavior;
import fr.lordfinn.steveparty.components.ModComponents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

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
            if (stack.getItem() instanceof TileBehavior tileBehaviorItem) {
                TileBehaviorComponent component = stack.getOrDefault(ModComponents.TILE_BEHAVIOR_COMPONENT, TileBehaviorComponent.DEFAULT_TILE_BEHAVIOR);
                List<BlockPos> destinations = new ArrayList<>(component.destinations()); // Copy destinations to a new list.
                World world = requireNonNull(tileEntity.getWorld());
                tileDestinations = getDestinationsStatus(destinations, world);
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
        return world != null && world.getBlockEntity(pos) instanceof TileEntity;
    }
}

