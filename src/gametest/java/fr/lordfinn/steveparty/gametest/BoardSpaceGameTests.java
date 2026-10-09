package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.AdvancedTileBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileContents;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileMigration;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock.TILE_TYPE;

/** Board spaces: the active cartridge follows the redstone power, locally or through a router. */
public class BoardSpaceGameTests implements FabricGameTest {
    private static final BlockPos TILE = new BlockPos(2, 1, 2);

    private static BoardSpaceBlockEntity placeTile(TestContext context) {
        context.setBlockState(TILE.down(), Blocks.STONE);
        context.setBlockState(TILE, ModBlocks.ADVANCED_TILE);
        return context.getBlockEntity(TILE);
    }

    private static void assertTile(TestContext context, BoardSpaceBlockEntity tile, int slot, BoardSpaceType type) {
        context.assertEquals(tile.getActiveSlot(), slot, "active slot");
        context.expectBlockProperty(TILE, TILE_TYPE, type);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cartridgeRoleFollowsOwnRedstone(TestContext context) {
        BoardSpaceBlockEntity tile = placeTile(context);
        tile.setStack(0, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
        tile.setStack(15, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP));
        assertTile(context, tile, 0, BoardSpaceType.DEFAULT);

        context.setBlockState(TILE.east(), Blocks.REDSTONE_BLOCK);
        assertTile(context, tile, 15, BoardSpaceType.BOARD_SPACE_STOP);

        context.removeBlock(TILE.east());
        assertTile(context, tile, 0, BoardSpaceType.DEFAULT);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void insertingCartridgeAppliesRole(TestContext context) {
        BoardSpaceBlockEntity tile = placeTile(context);
        assertTile(context, tile, 0, BoardSpaceType.DEFAULT);
        tile.setStack(0, new ItemStack(ModItems.TILE_BEHAVIOR_START));
        assertTile(context, tile, 0, BoardSpaceType.TILE_START);
        tile.setStack(0, new ItemStack(ModItems.INVENTORY_CARTRIDGE));
        assertTile(context, tile, 0, BoardSpaceType.TILE_INVENTORY_INTERACTOR);
        tile.removeStack(0);
        assertTile(context, tile, 0, BoardSpaceType.DEFAULT);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placingTileHoldingCartridgeAppliesRole(TestContext context) {
        // A tile item holding a start cartridge, as made by the creative pick block (block entity data and components)
        BlockPos source = new BlockPos(5, 1, 5);
        context.setBlockState(source, ModBlocks.ADVANCED_TILE);
        BoardSpaceBlockEntity sourceTile = context.getBlockEntity(source);
        sourceTile.setStack(0, new ItemStack(ModItems.TILE_BEHAVIOR_START));
        ItemStack item = TileContents.copyOf(
                new ItemStack(ModBlocks.ADVANCED_TILE), sourceTile, context.getWorld());

        context.setBlockState(TILE.down(), Blocks.STONE);
        PlayerEntity player = context.createMockPlayer(GameMode.CREATIVE);
        player.setStackInHand(Hand.MAIN_HAND, item); // the placement reads the stack in hand
        context.useStackOnBlock(player, item, TILE.down(), Direction.UP);
        BoardSpaceBlockEntity tile = context.getBlockEntity(TILE);
        assertTile(context, tile, 0, BoardSpaceType.TILE_START);
        context.complete();
    }

    /** Worlds saved before the tiles were renamed: simple_tile was the Tile, tile the Advanced Tile. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tilesSavedUnderTheirOldNamesLoad(TestContext context) {
        var registries = context.getWorld().getRegistryManager();
        context.assertTrue(Registries.BLOCK.get(Steveparty.id("simple_tile")) == ModBlocks.TILE,
                "the old Tile's block id is the Tile's");
        context.assertTrue(Registries.ITEM.get(Steveparty.id("simple_tile")) == ModBlocks.TILE.asItem(),
                "the old Tile's item id is the Tile's");
        // An old Advanced Tile: its block id ("tile") is now the Tile's, its block entity ("tile_entity") says what it was
        BlockPos source = new BlockPos(5, 1, 5);
        context.setBlockState(source, ModBlocks.ADVANCED_TILE);
        AdvancedTileBlockEntity advanced = context.getBlockEntity(source);
        advanced.setStack(7, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP));
        NbtCompound saved = advanced.createNbtWithIdentifyingData(registries);
        saved.putString("id", "steveparty:tile_entity");
        context.setBlockState(TILE.down(), Blocks.STONE);
        context.setBlockState(TILE, ModBlocks.TILE);
        BlockPos abs = context.getAbsolutePos(TILE);
        BlockEntity loaded = BlockEntity.createFromNbt(abs, context.getBlockState(TILE), saved, registries);
        context.assertTrue(loaded instanceof AdvancedTileBlockEntity,
                "its block entity loads as an Advanced Tile's: " + loaded);
        context.getWorld().addBlockEntity(loaded);
        context.assertTrue(TileMigration.migrate(context.getWorld(), abs), "migrated: "
                + context.getWorld().getBlockEntity(abs) + " / " + context.getWorld().getBlockState(abs));
        context.expectBlock(ModBlocks.ADVANCED_TILE, TILE);
        BoardSpaceBlockEntity restored = context.getBlockEntity(TILE);
        context.assertTrue(restored.getStack(7).isOf(ModItems.BOARD_SPACE_BEHAVIOR_STOP), "with its cartridges");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void routerDrivesItsBoardSpaces(TestContext context) {
        BoardSpaceBlockEntity tile = placeTile(context);
        tile.setStack(0, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
        tile.setStack(15, new ItemStack(ModItems.TILE_BEHAVIOR_START));

        BlockPos router = new BlockPos(5, 1, 5);
        context.setBlockState(router.down(), Blocks.STONE);
        context.setBlockState(router, ModBlocks.BOARD_SPACE_REDSTONE_ROUTER);
        BoardSpaceRedstoneRouterBlockEntity routerEntity = context.getBlockEntity(router);
        ItemStack routerCartridge = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
        routerCartridge.set(ModComponents.DESTINATIONS_COMPONENT,
                new DestinationsComponent(new ArrayList<>(List.of(context.getAbsolutePos(TILE))), ""));
        routerEntity.setStack(0, routerCartridge);

        // The router's power selects the tile's cartridge
        context.setBlockState(router.east(), Blocks.REDSTONE_BLOCK);
        assertTile(context, tile, 15, BoardSpaceType.TILE_START);

        // While routed, the tile ignores its own redstone
        context.setBlockState(TILE.east(), Blocks.REDSTONE_BLOCK);
        context.removeBlock(router.east());
        assertTile(context, tile, 0, BoardSpaceType.DEFAULT);

        // Once the router is gone, the tile follows its own redstone again
        context.removeBlock(router);
        assertTile(context, tile, 15, BoardSpaceType.TILE_START);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void removingTileFromRouterCartridgeUnroutesIt(TestContext context) {
        BoardSpaceBlockEntity tile = placeTile(context);
        tile.setStack(15, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP));

        BlockPos router = new BlockPos(5, 1, 5);
        context.setBlockState(router, ModBlocks.BOARD_SPACE_REDSTONE_ROUTER);
        BoardSpaceRedstoneRouterBlockEntity routerEntity = context.getBlockEntity(router);
        ItemStack routerCartridge = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
        routerCartridge.set(ModComponents.DESTINATIONS_COMPONENT,
                new DestinationsComponent(new ArrayList<>(List.of(context.getAbsolutePos(TILE))), ""));
        routerEntity.setStack(0, routerCartridge);
        context.setBlockState(router.east(), Blocks.REDSTONE_BLOCK);
        assertTile(context, tile, 15, BoardSpaceType.BOARD_SPACE_STOP);

        // Taking the cartridge out of the router gives the tile back its own (unpowered) redstone
        routerEntity.removeStack(0);
        assertTile(context, tile, 0, BoardSpaceType.DEFAULT);
        context.complete();
    }
}
