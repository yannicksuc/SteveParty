package fr.lordfinn.steveparty.gametest;

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

        List<ItemStack> roleTiles = CartridgeApplications.roleTiles();
        for (int i = 0; i < roleTiles.size(); i++) for (int j = i + 1; j < roleTiles.size(); j++) {
            context.assertFalse(ItemStack.areItemsAndComponentsEqual(roleTiles.get(i), roleTiles.get(j)), "each role tile is its own entry");
        }
        context.complete();
    }
}
