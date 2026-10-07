package fr.lordfinn.steveparty.compat;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileContents;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.AdvanceBackCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.List;

/**
 * What a recipe viewer (REI, see the client's compat.rei plugin) shows of the tiles and their cartridges, as plain item
 * logic (no viewer class here, so it is checked by a GameTest): every tile in each size, a tile per role, and the
 * « cartridge application » displays (a tile + a cartridge gives the tile holding it).
 */
public final class CartridgeApplications {
    private CartridgeApplications() {
    }

    /** One « cartridge application »: {@code cartridge} put in {@code tile}; inputs and results in the same size order. */
    public record Application(Block tile, ItemStack cartridge, List<ItemStack> tiles, List<ItemStack> results) {
        /** The Advanced Tile: it holds up to 16 cartridges, the redstone picks the active one. */
        public boolean advanced() {
            return tile == ModBlocks.ADVANCED_TILE;
        }
    }

    /** The Tile and the Advanced Tile. */
    public static List<Block> tiles() {
        return List.of(ModBlocks.TILE, ModBlocks.ADVANCED_TILE);
    }

    /** {@code tile}'s item in each size (standard, small, large). */
    public static List<ItemStack> sizes(Block tile) {
        List<ItemStack> sizes = new ArrayList<>();
        for (TileSize size : TileSize.values()) sizes.add(TileSize.with(new ItemStack(tile), size));
        return sizes;
    }

    /**
     * One cartridge of each role, in registration order (the creative tab's): every {@link CartridgeItem} of the mod,
     * the Move Forward / Back one twice (+3 and -3).
     */
    public static List<ItemStack> cartridges() {
        List<ItemStack> cartridges = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (!(item instanceof CartridgeItem) || !Steveparty.MOD_ID.equals(Registries.ITEM.getId(item).getNamespace())) continue;
            cartridges.add(new ItemStack(item));
            if (item == ModItems.ADVANCE_BACK_CARTRIDGE) cartridges.add(AdvanceBackCartridgeItem.withSteps(-AdvanceBackCartridgeItem.DEFAULT_STEPS));
        }
        return cartridges;
    }

    /** {@code tile} (a tile item) holding {@code cartridge}, as a tile taken with its contents ({@link TileContents}). */
    public static ItemStack holding(ItemStack tile, ItemStack cartridge) {
        return TileContents.holding(tile, cartridge);
    }

    /** The first cartridge a tile item holds (empty: none). */
    public static ItemStack heldCartridge(ItemStack tile) {
        List<TileContents.Slot> cartridges = TileContents.cartridges(tile);
        return cartridges.isEmpty() ? ItemStack.EMPTY : cartridges.getFirst().cartridge();
    }

    /** Every « cartridge application »: each tile with each cartridge. */
    public static List<Application> all() {
        List<Application> applications = new ArrayList<>();
        List<ItemStack> cartridges = cartridges();
        for (Block tile : tiles()) {
            List<ItemStack> sizes = sizes(tile);
            for (ItemStack cartridge : cartridges) {
                List<ItemStack> results = new ArrayList<>(sizes.size());
                for (ItemStack size : sizes) results.add(holding(size, cartridge));
                applications.add(new Application(tile, cartridge, sizes, List.copyOf(results)));
            }
        }
        return applications;
    }

    /**
     * The tile items in the order of the creative tab and of REI's list: the Tile, the Advanced Tile, a Tile per
     * cartridge, an Advanced Tile per cartridge, then the small (1x1) sizes, then the large (2x2) ones.
     */
    public static List<ItemStack> tileEntries() {
        List<ItemStack> entries = new ArrayList<>();
        for (Block tile : tiles()) entries.add(new ItemStack(tile));
        entries.addAll(roleTiles());
        for (TileSize size : List.of(TileSize.SMALL, TileSize.LARGE)) {
            for (Block tile : tiles()) entries.add(TileSize.with(new ItemStack(tile), size));
        }
        return entries;
    }

    /** A tile per role for the viewer's list: each tile (standard size) holding each cartridge. */
    public static List<ItemStack> roleTiles() {
        List<ItemStack> tiles = new ArrayList<>();
        for (Block tile : tiles()) for (ItemStack cartridge : cartridges()) tiles.add(holding(new ItemStack(tile), cartridge));
        return tiles;
    }
}
