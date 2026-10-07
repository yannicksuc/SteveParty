package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import fr.lordfinn.steveparty.compat.CartridgeApplications;
import fr.lordfinn.steveparty.items.custom.cartridges.AdvanceBackCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** What the REI plugin shows of the tiles (its displays are built from CartridgeApplications, plain item logic). */
public class CartridgeApplicationsGameTests implements FabricGameTest {

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyRoleHasACartridgeApplicationOnEveryTile(TestContext context) {
        List<ItemStack> cartridges = CartridgeApplications.cartridges();
        Set<BoardSpaceType> roles = EnumSet.noneOf(BoardSpaceType.class);
        for (ItemStack cartridge : cartridges) roles.add(((CartridgeItem) cartridge.getItem()).getBoardSpaceType());
        context.assertTrue(roles.equals(EnumSet.allOf(BoardSpaceType.class)), "a cartridge for every role, got " + roles);
        context.assertTrue(cartridges.stream().anyMatch(c -> c.getItem() instanceof AdvanceBackCartridgeItem && AdvanceBackCartridgeItem.steps(c) < 0),
                "the Move Back (-3) cartridge too");

        List<CartridgeApplications.Application> applications = CartridgeApplications.all();
        context.assertTrue(applications.size() == CartridgeApplications.tiles().size() * cartridges.size(),
                "one display per tile and cartridge, got " + applications.size());
        for (CartridgeApplications.Application application : applications) {
            context.assertTrue(application.tiles().size() == TileSize.values().length, "the tile in its 3 sizes");
            for (int i = 0; i < application.tiles().size(); i++) {
                ItemStack tile = application.tiles().get(i), result = application.results().get(i);
                context.assertTrue(result.isOf(application.tile().asItem()) && TileSize.of(result) == TileSize.of(tile),
                        "the result is the same tile in the same size");
                context.assertTrue(ItemStack.areItemsAndComponentsEqual(CartridgeApplications.heldCartridge(result), application.cartridge()),
                        "the result holds its cartridge");
                context.assertTrue(CartridgeApplications.heldCartridge(tile).isEmpty(), "the input tile holds nothing");
            }
        }

        // The order of the creative tab and REI: Tile, Advanced Tile, the Tiles per cartridge, the Advanced Tiles per
        // cartridge, then the 1x1 sizes and the 2x2 ones
        List<ItemStack> entries = CartridgeApplications.tileEntries();
        int n = cartridges.size();
        context.assertTrue(entries.size() == 2 + 2 * n + 4, "every tile entry once, got " + entries.size());
        context.assertTrue(entries.get(0).isOf(ModBlocks.TILE.asItem()) && CartridgeApplications.heldCartridge(entries.get(0)).isEmpty()
                && entries.get(1).isOf(ModBlocks.ADVANCED_TILE.asItem()) && CartridgeApplications.heldCartridge(entries.get(1)).isEmpty(),
                "the Tile, then the Advanced Tile");
        for (int i = 0; i < n; i++) {
            ItemStack tile = entries.get(2 + i), advanced = entries.get(2 + n + i);
            context.assertTrue(tile.isOf(ModBlocks.TILE.asItem()) && TileSize.of(tile) == TileSize.STANDARD
                    && ItemStack.areItemsAndComponentsEqual(CartridgeApplications.heldCartridge(tile), cartridges.get(i)), "then a Tile per cartridge");
            context.assertTrue(advanced.isOf(ModBlocks.ADVANCED_TILE.asItem()) && TileSize.of(advanced) == TileSize.STANDARD
                    && ItemStack.areItemsAndComponentsEqual(CartridgeApplications.heldCartridge(advanced), cartridges.get(i)), "then an Advanced Tile per cartridge");
        }
        List<ItemStack> sizes = entries.subList(2 + 2 * n, entries.size());
        context.assertTrue(TileSize.of(sizes.get(0)) == TileSize.SMALL && sizes.get(0).isOf(ModBlocks.TILE.asItem())
                && TileSize.of(sizes.get(1)) == TileSize.SMALL && sizes.get(1).isOf(ModBlocks.ADVANCED_TILE.asItem())
                && TileSize.of(sizes.get(2)) == TileSize.LARGE && sizes.get(2).isOf(ModBlocks.TILE.asItem())
                && TileSize.of(sizes.get(3)) == TileSize.LARGE && sizes.get(3).isOf(ModBlocks.ADVANCED_TILE.asItem()), "then the 1x1 sizes, then the 2x2");

        List<ItemStack> roleTiles = CartridgeApplications.roleTiles();
        for (int i = 0; i < roleTiles.size(); i++) for (int j = i + 1; j < roleTiles.size(); j++) {
            context.assertFalse(ItemStack.areItemsAndComponentsEqual(roleTiles.get(i), roleTiles.get(j)), "each role tile is its own entry");
        }
        context.complete();
    }
}
