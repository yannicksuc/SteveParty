package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TilePartBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSupport;
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

    private static void floor(TestContext context) {
        for (int x = 0; x < 6; x++) for (int z = 0; z < 6; z++) context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
    }

    private static ItemStack sized(ItemStack stack, TileSize size) {
        return TileSize.with(stack, size);
    }

    private static void assertLarge(TestContext context, BlockPos master) {
        context.expectBlockProperty(master, SIZE, TileSize.LARGE);
        for (TilePartBlock.Part part : TilePartBlock.Part.values()) {
            context.expectBlockProperty(part.fromMaster(master), TilePartBlock.PART, part);
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void largeTilePlacedByAPlayerTakesItsFourBlocks(TestContext context) {
        floor(context);
        PlayerEntity player = context.createMockPlayer(GameMode.CREATIVE);
        ItemStack item = sized(new ItemStack(ModBlocks.SIMPLE_TILE), TileSize.LARGE);
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

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void largeTileNeedsFreeBlocks(TestContext context) {
        floor(context);
        context.setBlockState(TILE.east(), Blocks.STONE);
        PlayerEntity player = context.createMockPlayer(GameMode.CREATIVE);
        ItemStack item = sized(new ItemStack(ModBlocks.TILE), TileSize.LARGE);
        player.setStackInHand(Hand.MAIN_HAND, item);
        context.useStackOnBlock(player, item, TILE.down(), Direction.UP);
        context.expectBlock(Blocks.AIR, TILE);
        // Put by a command on a crowded spot: it shrinks back to the standard size
        context.setBlockState(TILE, ModBlocks.TILE.getDefaultState().with(SIZE, TileSize.LARGE));
        context.expectBlockProperty(TILE, SIZE, TileSize.STANDARD);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void breakingAPartBreaksTheLargeTile(TestContext context) {
        floor(context);
        context.setBlockState(TILE, ModBlocks.TILE.getDefaultState().with(SIZE, TileSize.LARGE));
        assertLarge(context, TILE);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.interactionManager.tryBreakBlock(context.getAbsolutePos(TilePartBlock.Part.SOUTH_EAST.fromMaster(TILE)));
        context.expectBlock(Blocks.AIR, TILE);
        for (TilePartBlock.Part part : TilePartBlock.Part.values()) context.expectBlock(Blocks.AIR, part.fromMaster(TILE));
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void brokenTileDropsItsSize(TestContext context) {
        floor(context);
        context.setBlockState(TILE, ModBlocks.SIMPLE_TILE.getDefaultState().with(SIZE, TileSize.LARGE));
        BlockPos abs = context.getAbsolutePos(TILE);
        context.getWorld().breakBlock(abs, true);
        for (TilePartBlock.Part part : TilePartBlock.Part.values()) context.expectBlock(Blocks.AIR, part.fromMaster(TILE));
        List<ItemEntity> drops = context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(abs).expand(2), e -> true);
        context.assertTrue(drops.size() == 1 && drops.getFirst().getStack().isOf(ModBlocks.SIMPLE_TILE.asItem())
                && TileSize.of(drops.getFirst().getStack()) == TileSize.LARGE, "one large tile dropped: " + drops);
        drops.forEach(ItemEntity::discard);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void largeTileLiesOnAFloorOfSlabs(TestContext context) {
        for (int x = 2; x < 4; x++) for (int z = 2; z < 4; z++) context.setBlockState(new BlockPos(x, 1, z), Blocks.OAK_SLAB);
        context.setBlockState(TILE, ModBlocks.TILE.getDefaultState().with(SIZE, TileSize.LARGE));
        context.expectBlockProperty(TILE, SUPPORT, TileSupport.DROP_8);
        // One block of the 2x2 on a full block: not one level surface any more
        context.setBlockState(new BlockPos(3, 1, 3), Blocks.STONE);
        context.runAtTick(context.getTick() + 3, () -> {
            context.expectBlockProperty(TILE, SUPPORT, TileSupport.FLAT);
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aTileAloneInTheGridCyclesItsSize(TestContext context) {
        TileSizeRecipe recipe = new TileSizeRecipe(CraftingRecipeCategory.MISC);
        ItemStack tile = new ItemStack(ModBlocks.SIMPLE_TILE, 5);
        var registries = context.getWorld().getRegistryManager();
        ItemStack small = recipe.craft(CraftingRecipeInput.create(1, 1, List.of(tile)), registries);
        context.assertTrue(small.getCount() == 1 && TileSize.of(small) == TileSize.SMALL, "standard -> small: " + small);
        ItemStack large = recipe.craft(CraftingRecipeInput.create(1, 1, List.of(small)), registries);
        context.assertTrue(TileSize.of(large) == TileSize.LARGE, "small -> large");
        ItemStack standard = recipe.craft(CraftingRecipeInput.create(1, 1, List.of(large)), registries);
        context.assertTrue(TileSize.of(standard) == TileSize.STANDARD && standard.getComponentChanges().isEmpty(),
                "large -> standard, a plain tile again: " + standard.getComponentChanges());
        context.assertFalse(recipe.matches(CraftingRecipeInput.create(2, 1, List.of(tile, tile)), context.getWorld()),
                "two tiles: no recipe");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void smallTilePlacedFromItsItem(TestContext context) {
        floor(context);
        PlayerEntity player = context.createMockPlayer(GameMode.CREATIVE);
        ItemStack item = sized(new ItemStack(ModBlocks.TILE), TileSize.SMALL);
        player.setStackInHand(Hand.MAIN_HAND, item);
        context.useStackOnBlock(player, item, TILE.down(), Direction.UP);
        context.expectBlockProperty(TILE, SIZE, TileSize.SMALL);
        context.expectBlock(Blocks.AIR, TILE.east());
        context.assertTrue(ATileBlock.levelState(context.getBlockState(TILE)).get(SIZE) == TileSize.STANDARD, "drawn as standard for arrows");
        context.complete();
    }
}
