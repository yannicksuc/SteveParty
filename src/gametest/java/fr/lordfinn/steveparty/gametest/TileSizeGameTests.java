package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TilePartBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileLayout;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSupport;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.recipes.TileSizeRecipe;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.List;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock.SIZE;
import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock.SUPPORT;

/** The 3 tile sizes: standard (2 wide, centred), small (1x1) and large (2x2 blocks: the tile + 3 parts). */
public class TileSizeGameTests implements FabricGameTest {
    private static final BlockPos TILE = new BlockPos(2, 2, 2);

    private static ItemStack sized(ItemStack stack, TileSize size) {
        return TileSize.with(stack, size);
    }

    private static final TileLayout SE = TileLayout.LARGE_SOUTH_EAST;

    private static List<BlockPos> parts(BlockPos master) {
        return SE.parts(master);
    }

    private static void assertLarge(TestContext context, BlockPos master, TileLayout layout) {
        context.expectBlockProperty(master, SIZE, layout);
        BlockPos absMaster = context.getAbsolutePos(master);
        for (BlockPos part : layout.parts(master)) {
            context.assertTrue(TilePartBlock.isPartOf(context.getBlockState(part), context.getAbsolutePos(part), absMaster),
                    "a part of the tile at " + part);
        }
    }

    private static void assertLarge(TestContext context, BlockPos master) {
        assertLarge(context, master, SE);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void largeTilePlacedByAPlayerTakesItsFourBlocks(TestContext context) {
        TestBoards.floor(context, 6, 6, 1);
        PlayerEntity player = context.createMockPlayer(GameMode.CREATIVE);
        ItemStack item = sized(new ItemStack(ModBlocks.TILE), TileSize.LARGE);
        player.setStackInHand(Hand.MAIN_HAND, item);
        context.useStackOnBlock(player, item, TILE.down(), Direction.UP);
        // Aimed at the middle of the block: the 2x2 starts there and goes east and south
        assertLarge(context, TILE);
        // A board space for tokens in its middle, whichever of its blocks they stand in
        BlockPos abs = context.getAbsolutePos(TILE);
        Vec3d stand = BoardSpaces.standPos(context.getWorld(), abs);
        context.assertTrue(stand.distanceTo(new Vec3d(abs.getX() + 1, abs.getY() + TileSupport.THICKNESS, abs.getZ() + 1)) < 1.0E-6,
                "tokens stand in the middle of the 2x2: " + stand);
        context.assertEquals(BoardSpaces.boardSpacePosAt(context.getWorld(), BlockPos.ofFloored(stand)), abs, "found from a part");
        context.complete();
    }

    /** On stairs, a large tile is anchored on the aimed (highest) block and spreads downhill, sloped like its stairs. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void largeTileFollowsTheSlopeUnderItsAnchor(TestContext context) {
        // Stairs climbing north under the anchor; its downhill side is south
        context.setBlockState(TILE.down(), Blocks.OAK_STAIRS.getDefaultState().with(net.minecraft.block.StairsBlock.FACING, Direction.NORTH));
        PlayerEntity player = context.createMockPlayer(GameMode.CREATIVE);
        ItemStack item = sized(new ItemStack(ModBlocks.ADVANCED_TILE), TileSize.LARGE);
        player.setStackInHand(Hand.MAIN_HAND, item);
        // Aimed at the west half of the step: across the slope, it spreads west
        BlockPos abs = context.getAbsolutePos(TILE.down());
        net.minecraft.util.hit.BlockHitResult hit = new net.minecraft.util.hit.BlockHitResult(
                new Vec3d(abs.getX() + 0.25, abs.getY() + 1, abs.getZ() + 0.3), Direction.UP, abs, false);
        item.useOnBlock(new net.minecraft.item.ItemUsageContext(player, Hand.MAIN_HAND, hit));
        assertLarge(context, TILE, TileLayout.LARGE_SOUTH_WEST);
        context.expectBlockProperty(TILE, SUPPORT, TileSupport.SLOPE_NORTH);
        // Its middle (the corner of its 4 blocks) is on the slope, half a block down from the step nose
        Vec3d stand = BoardSpaces.standPos(context.getWorld(), context.getAbsolutePos(TILE));
        BlockPos tile = context.getAbsolutePos(TILE);
        context.assertTrue(stand.distanceTo(new Vec3d(tile.getX(), tile.getY() + TileSupport.SLOPE_NORTH.standY(0, 1), tile.getZ() + 1)) < 1.0E-6
                && Math.abs(TileSupport.SLOPE_NORTH.surfaceY(0, 1) + 0.5) < 1.0E-6,
                "stands in its middle, on the slope: " + stand);
        // The parts carry the slope on: aimed at from above, the south part's outline is lower
        double southTop = context.getBlockState(TILE.south()).getOutlineShape(context.getWorld(), context.getAbsolutePos(TILE.south()))
                .getMax(Direction.Axis.Y);
        context.assertTrue(southTop < 0, "the downhill part is below its block's floor: " + southTop);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void largeTileNeedsFreeBlocks(TestContext context) {
        TestBoards.floor(context, 6, 6, 1);
        context.setBlockState(TILE.east(), Blocks.STONE);
        PlayerEntity player = context.createMockPlayer(GameMode.CREATIVE);
        ItemStack item = sized(new ItemStack(ModBlocks.ADVANCED_TILE), TileSize.LARGE);
        player.setStackInHand(Hand.MAIN_HAND, item);
        context.useStackOnBlock(player, item, TILE.down(), Direction.UP);
        context.expectBlock(Blocks.AIR, TILE);
        // Put by a command on a crowded spot: it shrinks back to the standard size
        context.setBlockState(TILE, ModBlocks.ADVANCED_TILE.getDefaultState().with(SIZE, SE));
        context.expectBlockProperty(TILE, SIZE, TileLayout.STANDARD);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void breakingAPartBreaksTheLargeTile(TestContext context) {
        TestBoards.floor(context, 6, 6, 1);
        context.setBlockState(TILE, ModBlocks.ADVANCED_TILE.getDefaultState().with(SIZE, SE));
        assertLarge(context, TILE);
        ServerPlayerEntity player = TestPlayers.mock(context);
        player.interactionManager.tryBreakBlock(context.getAbsolutePos(TILE.add(1, 0, 1)));
        context.expectBlock(Blocks.AIR, TILE);
        for (BlockPos part : parts(TILE)) context.expectBlock(Blocks.AIR, part);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void brokenTileDropsItsSize(TestContext context) {
        TestBoards.floor(context, 6, 6, 1);
        context.setBlockState(TILE, ModBlocks.TILE.getDefaultState().with(SIZE, SE));
        BlockPos abs = context.getAbsolutePos(TILE);
        context.getWorld().breakBlock(abs, true);
        for (BlockPos part : parts(TILE)) context.expectBlock(Blocks.AIR, part);
        List<ItemEntity> drops = context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(abs).expand(2), e -> true);
        context.assertTrue(drops.size() == 1 && drops.getFirst().getStack().isOf(ModBlocks.TILE.asItem())
                && TileSize.of(drops.getFirst().getStack()) == TileSize.LARGE, "one large tile dropped: " + drops);
        drops.forEach(ItemEntity::discard);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void largeTileLiesOnAFloorOfSlabs(TestContext context) {
        for (int x = 2; x < 4; x++) for (int z = 2; z < 4; z++) context.setBlockState(new BlockPos(x, 1, z), Blocks.OAK_SLAB);
        context.setBlockState(TILE, ModBlocks.ADVANCED_TILE.getDefaultState().with(SIZE, SE));
        context.expectBlockProperty(TILE, SUPPORT, TileSupport.DROP_8);
        // One block of the 2x2 on a full block: not one level surface any more
        context.setBlockState(new BlockPos(3, 1, 3), Blocks.STONE);
        context.runAtTick(context.getTick() + 3, () -> {
            context.expectBlockProperty(TILE, SUPPORT, TileSupport.FLAT);
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tilesChangeSizeKeepingTheirMaterial(TestContext context) {
        TileSizeRecipe recipe = new TileSizeRecipe(CraftingRecipeCategory.MISC);
        var registries = context.getWorld().getRegistryManager();
        ItemStack tile = new ItemStack(ModBlocks.TILE);
        // 4 standard tiles in a square: a large one
        ItemStack large = recipe.craft(CraftingRecipeInput.create(2, 2, List.of(tile, tile, tile, tile)), registries);
        context.assertTrue(large.isOf(ModBlocks.TILE.asItem()) && large.getCount() == 1 && TileSize.of(large) == TileSize.LARGE,
                "4 tiles -> a large one: " + large);
        // ... and back
        ItemStack back = recipe.craft(CraftingRecipeInput.create(1, 1, List.of(large)), registries);
        context.assertTrue(back.getCount() == 4 && TileSize.of(back) == TileSize.STANDARD && back.getComponentChanges().isEmpty(),
                "a large tile -> 4 plain tiles: " + back);
        // 2 small tiles: a standard one
        ItemStack small = TileSize.with(new ItemStack(ModBlocks.ADVANCED_TILE), TileSize.SMALL);
        ItemStack standard = recipe.craft(CraftingRecipeInput.create(2, 1, List.of(small, small.copy())), registries);
        context.assertTrue(standard.isOf(ModBlocks.ADVANCED_TILE.asItem()) && TileSize.of(standard) == TileSize.STANDARD,
                "2 small -> 1 standard: " + standard);
        // Mixed kinds or sizes: nothing
        context.assertFalse(recipe.matches(CraftingRecipeInput.create(2, 2, List.of(tile, tile, tile, new ItemStack(ModBlocks.ADVANCED_TILE))),
                context.getWorld()), "a tile and an advanced tile don't merge");
        context.assertFalse(recipe.matches(CraftingRecipeInput.create(1, 1, List.of(tile)), context.getWorld()), "a tile alone: nothing");
        // The stonecutter cuts a tile into 2 small ones
        var cut = context.getWorld().getServer().getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.STONECUTTING,
                new net.minecraft.recipe.input.SingleStackRecipeInput(tile), context.getWorld());
        context.assertTrue(cut.isPresent(), "a stonecutting recipe for the tile");
        ItemStack cutResult = cut.get().value().craft(new net.minecraft.recipe.input.SingleStackRecipeInput(tile), registries);
        context.assertTrue(cutResult.isOf(ModBlocks.TILE.asItem()) && cutResult.getCount() == 2 && TileSize.of(cutResult) == TileSize.SMALL,
                "2 small tiles: " + cutResult);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void smallTilePlacedFromItsItem(TestContext context) {
        TestBoards.floor(context, 6, 6, 1);
        PlayerEntity player = context.createMockPlayer(GameMode.CREATIVE);
        ItemStack item = sized(new ItemStack(ModBlocks.ADVANCED_TILE), TileSize.SMALL);
        player.setStackInHand(Hand.MAIN_HAND, item);
        context.useStackOnBlock(player, item, TILE.down(), Direction.UP);
        context.expectBlockProperty(TILE, SIZE, TileLayout.SMALL);
        context.expectBlock(Blocks.AIR, TILE.east());
        context.assertTrue(ATileBlock.levelState(context.getBlockState(TILE)).get(SIZE) == TileLayout.STANDARD, "drawn as standard for arrows");
        context.complete();
    }
}
