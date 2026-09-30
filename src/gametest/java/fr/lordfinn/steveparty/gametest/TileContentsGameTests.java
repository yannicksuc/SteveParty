package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileContents;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileLayout;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TileStampComponent;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.recipes.TileSizeRecipe;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.function.Consumer;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock.TILE_TYPE;
import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock.SIZE;

/** A tile broken with Silk Touch drops one item holding its cartridges, its look and its size; placed, it has them back. */
public class TileContentsGameTests implements FabricGameTest {
    private static final BlockPos TILE = new BlockPos(2, 2, 2);
    /** Where the dropped item is placed again: its ground block. */
    private static final BlockPos ELSEWHERE = new BlockPos(6, 1, 2);

    private static void floor(TestContext context) {
        for (int x = 0; x < 9; x++) for (int z = 0; z < 6; z++) context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
    }

    private static TileStampComponent stamp() {
        byte[] shape = new byte[16 * 16];
        for (int i = 0; i < 16; i++) shape[i * 16 + i] = 1;
        return TileStampComponent.of(shape, DyeColor.RED);
    }

    /**
     * A survival player holding a diamond pickaxe (with Silk Touch or not). A mock player: the mock server player counts
     * as creative whatever its game mode.
     */
    private static void withMiner(TestContext context, boolean silkTouch, Consumer<PlayerEntity> test) {
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        if (silkTouch) {
            pickaxe.addEnchantment(context.getWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT)
                    .getOrThrow(Enchantments.SILK_TOUCH), 1);
        }
        player.setStackInHand(Hand.MAIN_HAND, pickaxe);
        test.accept(player);
    }

    /** {@code player} mines the block at {@code pos}, as the server does (ServerPlayerInteractionManager#tryBreakBlock). */
    private static void mine(TestContext context, PlayerEntity player, BlockPos pos) {
        ServerWorld world = context.getWorld();
        BlockPos abs = context.getAbsolutePos(pos);
        BlockState state = world.getBlockState(abs);
        BlockEntity blockEntity = world.getBlockEntity(abs);
        ItemStack tool = player.getMainHandStack().copy();
        BlockState broken = state.getBlock().onBreak(world, abs, state, player);
        if (world.removeBlock(abs, false)) broken.getBlock().onBroken(world, abs, broken);
        if (player.canHarvest(broken)) broken.getBlock().afterBreak(world, player, abs, broken, blockEntity, tool);
    }

    /** The items dropped around the test (the emptied ones left behind by the scattering excepted), then removed. */
    private static List<ItemStack> takeDrops(TestContext context) {
        Box box = new Box(context.getAbsolutePos(BlockPos.ORIGIN)).expand(10);
        List<ItemEntity> entities = context.getWorld().getEntitiesByClass(ItemEntity.class, box, e -> !e.getStack().isEmpty());
        List<ItemStack> drops = entities.stream().map(e -> e.getStack().copy()).toList();
        entities.forEach(ItemEntity::discard);
        return drops;
    }

    /** {@code player} places {@code item} on the ground block {@code ground}. */
    private static void place(TestContext context, PlayerEntity player, ItemStack item, BlockPos ground) {
        player.setStackInHand(Hand.MAIN_HAND, item);
        BlockPos abs = context.getAbsolutePos(ground);
        item.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND,
                new BlockHitResult(abs.toCenterPos().add(0, 0.5, 0), Direction.UP, abs, false)));
    }

    private static ItemStack linkedStop(TestContext context) {
        ItemStack stop = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP);
        stop.set(ModComponents.DESTINATIONS_COMPONENT, new fr.lordfinn.steveparty.components.DestinationsComponent(
                List.of(context.getAbsolutePos(new BlockPos(0, 2, 0))), "overworld"));
        return stop;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void silkTouchKeepsCartridgesLookAndSize(TestContext context) {
        floor(context);
        context.setBlockState(TILE, ModBlocks.TILE.getDefaultState().with(SIZE, TileLayout.SMALL));
        BoardSpaceBlockEntity tile = context.getBlockEntity(TILE);
        tile.setStamp(stamp());
        tile.setStack(0, linkedStop(context));
        List<BlockPos> links = BoardLinks.links(tile, 0);
        withMiner(context, true, player -> {
            mine(context, player, TILE);
            context.expectBlock(Blocks.AIR, TILE);
            List<ItemStack> drops = takeDrops(context);
            context.assertTrue(drops.size() == 1, "only the tile drops: " + drops);
            ItemStack item = drops.getFirst();
            context.assertTrue(item.isOf(ModBlocks.TILE.asItem()) && TileSize.of(item) == TileSize.SMALL, "a small tile: " + item);
            List<TileContents.Slot> cartridges = TileContents.cartridges(item);
            context.assertTrue(cartridges.size() == 1 && cartridges.getFirst().cartridge().isOf(ModItems.BOARD_SPACE_BEHAVIOR_STOP)
                    && BoardLinks.links(cartridges.getFirst().cartridge()).equals(links), "holding its linked stop cartridge: " + cartridges);
            context.assertTrue(stamp().equals(TileContents.ownStamp(item)), "and its look");
            context.assertFalse(item.contains(DataComponentTypes.BLOCK_ENTITY_DATA), "not a copy: no block entity data");

            // Placed elsewhere: the same tile, links kept (it moved: they still lead to its next spaces)
            place(context, player, item, ELSEWHERE);
            BlockPos placed = ELSEWHERE.up();
            context.expectBlockProperty(placed, SIZE, TileLayout.SMALL);
            context.expectBlockProperty(placed, TILE_TYPE, BoardSpaceType.BOARD_SPACE_STOP);
            BoardSpaceBlockEntity again = context.getBlockEntity(placed);
            context.assertTrue(again.getStack(0).isOf(ModItems.BOARD_SPACE_BEHAVIOR_STOP), "its cartridge back");
            context.assertEquals(BoardLinks.links(again, 0), links, "its links back");
            context.assertTrue(stamp().equals(again.getStamp()), "its look back");
        });
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void withoutSilkTouchTheCartridgesSpillOut(TestContext context) {
        floor(context);
        context.setBlockState(TILE, ModBlocks.TILE);
        BoardSpaceBlockEntity tile = context.getBlockEntity(TILE);
        tile.setStamp(stamp());
        tile.setStack(0, linkedStop(context));
        withMiner(context, false, player -> {
            mine(context, player, TILE);
            List<ItemStack> drops = takeDrops(context);
            List<ItemStack> tiles = drops.stream().filter(s -> s.isOf(ModBlocks.TILE.asItem())).toList();
            int stops = drops.stream().filter(s -> s.isOf(ModItems.BOARD_SPACE_BEHAVIOR_STOP)).mapToInt(ItemStack::getCount).sum();
            context.assertTrue(tiles.size() == 1 && !TileContents.holdsContents(tiles.getFirst()), "a plain tile: " + drops);
            context.assertEquals(stops, 1, "its cartridge dropped apart");
        });
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anAdvancedTileKeepsEverySlot(TestContext context) {
        floor(context);
        context.setBlockState(TILE, ModBlocks.ADVANCED_TILE);
        BoardSpaceBlockEntity tile = context.getBlockEntity(TILE);
        tile.setStack(0, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
        tile.setStack(3, linkedStop(context));
        ItemStack back = new ItemStack(ModItems.ADVANCE_BACK_CARTRIDGE);
        back.set(ModComponents.ADVANCE_BACK_STEPS, -2);
        tile.setStack(15, back);
        withMiner(context, true, player -> {
            mine(context, player, TILE);
            List<ItemStack> drops = takeDrops(context);
            context.assertTrue(drops.size() == 1 && drops.getFirst().isOf(ModBlocks.ADVANCED_TILE.asItem()), "one advanced tile: " + drops);
            ItemStack item = drops.getFirst();
            List<Integer> slots = TileContents.cartridges(item).stream().map(TileContents.Slot::slot).toList();
            context.assertEquals(slots, List.of(0, 3, 15), "its 3 cartridges in their slots");
            // Not cut up in the crafting grid (they would be lost)
            context.assertFalse(new TileSizeRecipe(CraftingRecipeCategory.MISC).matches(
                    CraftingRecipeInput.create(2, 2, List.of(item, item.copy(), item.copy(), item.copy())), context.getWorld()),
                    "a tile holding cartridges doesn't merge");

            place(context, player, item, ELSEWHERE);
            BoardSpaceBlockEntity again = context.getBlockEntity(ELSEWHERE.up());
            context.assertTrue(again.getStack(0).isOf(ModItems.BOARD_SPACE_BEHAVIOR), "slot 1");
            context.assertTrue(again.getStack(3).isOf(ModItems.BOARD_SPACE_BEHAVIOR_STOP) && BoardLinks.links(again, 3).size() == 1, "slot 4, linked");
            context.assertTrue(again.getStack(15).isOf(ModItems.ADVANCE_BACK_CARTRIDGE)
                    && again.getStack(15).get(ModComponents.ADVANCE_BACK_STEPS) == -2, "slot 16, 2 spaces back");
            for (int slot : new int[]{1, 2, 4, 14}) context.assertTrue(again.getStack(slot).isEmpty(), "slot " + (slot + 1) + " empty");
            // No redstone: the first slot is the active one
            context.assertEquals(again.getActiveSlot(), 0, "active slot");
        });
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aLargeTileIsOneItem(TestContext context) {
        floor(context);
        context.setBlockState(TILE, ModBlocks.ADVANCED_TILE.getDefaultState().with(SIZE, TileLayout.LARGE_SOUTH_EAST));
        BoardSpaceBlockEntity tile = context.getBlockEntity(TILE);
        tile.setStack(0, linkedStop(context));
        withMiner(context, true, player -> {
            // Broken from one of its parts
            mine(context, player, TILE.add(1, 0, 1));
            context.expectBlock(Blocks.AIR, TILE);
            for (BlockPos part : TileLayout.LARGE_SOUTH_EAST.parts(TILE)) context.expectBlock(Blocks.AIR, part);
            List<ItemStack> drops = takeDrops(context);
            context.assertTrue(drops.size() == 1 && drops.getFirst().getCount() == 1 && TileSize.of(drops.getFirst()) == TileSize.LARGE,
                    "one large tile: " + drops);
            List<TileContents.Slot> cartridges = TileContents.cartridges(drops.getFirst());
            context.assertTrue(cartridges.size() == 1 && cartridges.getFirst().cartridge().isOf(ModItems.BOARD_SPACE_BEHAVIOR_STOP),
                    "holding its cartridge: " + cartridges);
        });
        context.complete();
    }
}
