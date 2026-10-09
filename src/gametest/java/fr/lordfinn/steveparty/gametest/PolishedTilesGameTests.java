package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.tiles.PolishedTilesBlock;
import fr.lordfinn.steveparty.blocks.custom.tiles.PolishedTilesColor;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.StonecuttingRecipe;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static fr.lordfinn.steveparty.blocks.custom.tiles.PolishedTilesColor.*;

/**
 * Polished Concrete / Terracotta Tiles: the checker recipe gives the colours (in its order), the item keeps them when
 * the block is mined, and gives them back when placed.
 */
public class PolishedTilesGameTests implements FabricGameTest {
    private static final PolishedTilesBlock CONCRETE = (PolishedTilesBlock) ModBlocks.POLISHED_CONCRETE_TILES;
    private static final PolishedTilesBlock TERRACOTTA = (PolishedTilesBlock) ModBlocks.POLISHED_TERRACOTTA_TILES;

    /** @return what the crafting grid « a b / c d » gives (empty if no recipe matches). */
    private static ItemStack craft(TestContext context, Block a, Block b, Block c, Block d) {
        CraftingRecipeInput input = CraftingRecipeInput.create(2, 2,
                List.of(new ItemStack(a), new ItemStack(b), new ItemStack(c), new ItemStack(d)));
        Optional<RecipeEntry<CraftingRecipe>> recipe = context.getWorld().getServer().getRecipeManager()
                .getFirstMatch(RecipeType.CRAFTING, input, context.getWorld());
        return recipe.map(entry -> entry.value().craft(input, context.getWorld().getRegistryManager())).orElse(ItemStack.EMPTY);
    }

    private static ItemStack checker(TestContext context, PolishedTilesBlock tiles, PolishedTilesColor a, PolishedTilesColor b) {
        return craft(context, tiles.polished(a), tiles.polished(b), tiles.polished(b), tiles.polished(a));
    }

    private static void assertTiles(TestContext context, ItemStack got, ItemStack expected) {
        context.assertTrue(ItemStack.areItemsAndComponentsEqual(got, expected) && got.getCount() == expected.getCount(),
                "expected " + expected + " " + expected.getComponentChanges() + ", got " + got + " " + got.getComponentChanges());
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void checkerRecipeGivesFourTilesInItsColours(TestContext context) {
        assertTiles(context, checker(context, CONCRETE, WHITE, BLACK), CONCRETE.stack(WHITE, BLACK, 4));
        assertTiles(context, checker(context, CONCRETE, LIGHT_BLUE, LIGHT_GRAY), CONCRETE.stack(LIGHT_BLUE, LIGHT_GRAY, 4));
        assertTiles(context, checker(context, TERRACOTTA, RED, YELLOW), TERRACOTTA.stack(RED, YELLOW, 4));
        // The plain terracotta is a colour like the others
        assertTiles(context, checker(context, TERRACOTTA, DEFAULT, WHITE), TERRACOTTA.stack(DEFAULT, WHITE, 4));
        assertTiles(context, checker(context, TERRACOTTA, CYAN, DEFAULT), TERRACOTTA.stack(CYAN, DEFAULT, 4));
        context.complete();
    }

    /** The colour in the top-left corner of the grid is the first one: (A, B) and (B, A) are two tilings. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void recipeOrderIsThePatternOrder(TestContext context) {
        ItemStack whiteFirst = checker(context, CONCRETE, WHITE, BLACK), blackFirst = checker(context, CONCRETE, BLACK, WHITE);
        assertTiles(context, blackFirst, CONCRETE.stack(BLACK, WHITE, 4));
        context.assertFalse(ItemStack.areItemsAndComponentsEqual(whiteFirst, blackFirst), "(white, black) and (black, white) are different items");
        context.assertFalse(CONCRETE.state(whiteFirst) == CONCRETE.state(blackFirst), "and different blocks");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyPairOfColoursIsCraftable(TestContext context) {
        for (PolishedTilesBlock tiles : ModBlocks.POLISHED_TILES) {
            for (PolishedTilesColor a : tiles.colors()) {
                for (PolishedTilesColor b : tiles.colors()) {
                    if (a != b) assertTiles(context, checker(context, tiles, a, b), tiles.stack(a, b, 4));
                }
            }
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void onlyACheckerOfOneMaterialMakesTiles(TestContext context) {
        Block white = CONCRETE.polished(WHITE), black = CONCRETE.polished(BLACK);
        // 4 polished blocks of one colour stay the bricks
        ItemStack bricks = craft(context, white, white, white, white);
        context.assertTrue(bricks.isOf(ModBlocks.POLISHED_CONCRETE_BRICKS_BLOCKS[0].asItem()), "4 white polished concrete: bricks, got " + bricks);
        context.assertTrue(craft(context, white, black, white, black).isEmpty(), "columns are not a checker");
        context.assertTrue(craft(context, white, white, black, black).isEmpty(), "rows are not a checker");
        context.assertTrue(craft(context, white, black, black, CONCRETE.polished(RED)).isEmpty(), "three colours");
        Block terracotta = TERRACOTTA.polished(BLACK);
        context.assertTrue(craft(context, white, terracotta, terracotta, white).isEmpty(), "concrete and terracotta don't mix");
        context.assertTrue(craft(context, Blocks.WHITE_CONCRETE, Blocks.BLACK_CONCRETE, Blocks.BLACK_CONCRETE, Blocks.WHITE_CONCRETE).isEmpty(),
                "unpolished concrete");
        context.complete();
    }

    /** A single-colour tiling comes from the stonecutter (the 2x2 square of one polished block is the bricks). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stonecutterCutsSingleColourTiles(TestContext context) {
        for (PolishedTilesBlock tiles : ModBlocks.POLISHED_TILES) {
            for (PolishedTilesColor color : tiles.colors()) {
                SingleStackRecipeInput input = new SingleStackRecipeInput(new ItemStack(tiles.polished(color)));
                boolean found = false;
                for (RecipeEntry<?> entry : context.getWorld().getServer().getRecipeManager().values()) {
                    if (!(entry.value() instanceof StonecuttingRecipe recipe) || !recipe.matches(input, context.getWorld())) continue;
                    ItemStack result = recipe.craft(input, context.getWorld().getRegistryManager());
                    if (!result.isOf(tiles.asItem())) continue;
                    assertTiles(context, result, tiles.stack(color, color, 1));
                    found = true;
                }
                context.assertTrue(found, "no stonecutter recipe for " + tiles.material() + " tiles in " + color.asString());
            }
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void minedTilesKeepTheirColours(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        for (PolishedTilesBlock tiles : ModBlocks.POLISHED_TILES) {
            BlockState state = tiles.with(RED, YELLOW);
            context.setBlockState(pos, state);
            List<ItemStack> drops = Block.getDroppedStacks(state, context.getWorld(), context.getAbsolutePos(pos), null,
                    null, new ItemStack(Items.IRON_PICKAXE));
            context.assertTrue(drops.size() == 1, "one drop, got " + drops);
            assertTiles(context, drops.getFirst(), tiles.stack(RED, YELLOW, 1));
            // Picked in creative: the same item
            assertTiles(context, tiles.getPickStack(context.getWorld(), context.getAbsolutePos(pos), state), tiles.stack(RED, YELLOW, 1));
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placedTilesGetTheColoursOfTheItem(TestContext context) {
        BlockPos floor = new BlockPos(1, 1, 1);
        context.setBlockState(floor, Blocks.STONE);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        BlockPos abs = context.getAbsolutePos(floor);
        BlockHitResult hit = new BlockHitResult(new Vec3d(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() + 0.5), Direction.UP, abs, false);

        ItemStack crafted = checker(context, CONCRETE, LIME, PINK);
        player.setStackInHand(Hand.MAIN_HAND, crafted);
        crafted.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
        context.expectBlock(CONCRETE, floor.up());
        context.expectBlockProperty(floor.up(), PolishedTilesBlock.Concrete.COLOR_A, LIME);
        context.expectBlockProperty(floor.up(), PolishedTilesBlock.Concrete.COLOR_B, PINK);
        context.assertTrue(crafted.getCount() == 3, "one of the 4 crafted tiles was placed");

        // An item without colours (/give) is the default single-colour tiling, and stacks with it
        context.setBlockState(floor.up(), Blocks.AIR);
        ItemStack plain = new ItemStack(TERRACOTTA);
        assertTiles(context, plain, TERRACOTTA.stack(DEFAULT, DEFAULT, 1));
        player.setStackInHand(Hand.MAIN_HAND, plain);
        plain.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
        context.expectBlockProperty(floor.up(), PolishedTilesBlock.Terracotta.COLOR_A, DEFAULT);
        context.expectBlockProperty(floor.up(), PolishedTilesBlock.Terracotta.COLOR_B, DEFAULT);
        context.complete();
    }

    /** One state per pair, the map colour of the first colour, the hardness of the polished block. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void statesAndProperties(TestContext context) {
        context.assertTrue(CONCRETE.getStateManager().getStates().size() == 16 * 16, "256 concrete tilings");
        context.assertTrue(TERRACOTTA.getStateManager().getStates().size() == 17 * 17, "289 terracotta tilings");
        for (PolishedTilesBlock tiles : ModBlocks.POLISHED_TILES) {
            BlockPos origin = context.getAbsolutePos(BlockPos.ORIGIN);
            for (PolishedTilesColor color : tiles.colors()) {
                BlockState state = tiles.with(color, tiles.colors().getLast());
                BlockState polished = tiles.polished(color).getDefaultState();
                context.assertTrue(state.getMapColor(context.getWorld(), origin) == polished.getMapColor(context.getWorld(), origin),
                        "map colour of " + color.asString() + " " + tiles.material() + " tiles");
            }
            BlockState polished = tiles.polished(WHITE).getDefaultState();
            context.assertTrue(tiles.getHardness() == polished.getBlock().getHardness() && tiles.getBlastResistance() == polished.getBlock().getBlastResistance(),
                    tiles.material() + " tiles are as hard as the polished block");
            context.assertTrue(tiles.getDefaultState().isToolRequired(), "needs a pickaxe");
            context.assertTrue(tiles.getDefaultState().isIn(BlockTags.PICKAXE_MINEABLE), "mined with a pickaxe");
            context.assertFalse(tiles.getDefaultState().hasBlockEntity(), "no block entity");
        }
        context.complete();
    }

    /** The name tells the colours; the creative tab shows a short list of distinct tilings, not every pair. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nameAndCreativeEntries(TestContext context) {
        ItemStack two = CONCRETE.stack(WHITE, BLACK, 1), one = CONCRETE.stack(WHITE, WHITE, 1);
        context.assertEquals(((TranslatableTextContent) two.getName().getContent()).getKey(), "block.steveparty.polished_tiles.two_colors", "two colours");
        context.assertEquals(((TranslatableTextContent) one.getName().getContent()).getKey(), "block.steveparty.polished_tiles.one_color", "one colour");
        for (PolishedTilesBlock tiles : ModBlocks.POLISHED_TILES) {
            List<ItemStack> stacks = tiles.creativeStacks();
            Set<BlockState> states = new HashSet<>();
            for (ItemStack stack : stacks) states.add(tiles.state(stack));
            context.assertTrue(states.size() == stacks.size(), "distinct creative entries");
            context.assertTrue(stacks.size() <= 32, "a short list, got " + stacks.size());
        }
        context.complete();
    }
}
