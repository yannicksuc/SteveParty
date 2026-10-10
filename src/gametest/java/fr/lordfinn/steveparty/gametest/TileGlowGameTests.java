package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock.TILE_TYPE;
import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock.GLOWING;

/** Glow ink on a tile, as on a sign: a glow ink sac lights it up, an ink sac puts it out; its item keeps it. */
public class TileGlowGameTests implements SteveGameTest {
    private static final BlockPos TILE = new BlockPos(2, 2, 2);
    private static final BlockPos ELSEWHERE = new BlockPos(6, 1, 2);

    private static BoardSpaceBlockEntity tile(TestContext context) {
        context.setBlockState(TILE.down(), Blocks.STONE);
        context.setBlockState(TILE, ModBlocks.TILE);
        return context.getBlockEntity(TILE);
    }

    private static ItemActionResult use(TestContext context, PlayerEntity player, BlockPos pos) {
        BlockPos abs = context.getAbsolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(abs), Direction.UP, abs, false);
        return context.getBlockState(pos).onUseWithItem(player.getMainHandStack(), context.getWorld(), player, Hand.MAIN_HAND, hit);
    }

    private static PlayerEntity holding(TestContext context, ItemStack stack) {
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        return player;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void glowInkLightsTheTileAndInkPutsItOut(TestContext context) {
        tile(context);
        context.expectBlockProperty(TILE, GLOWING, false);
        PlayerEntity player = holding(context, new ItemStack(Items.GLOW_INK_SAC, 3));
        context.assertEquals(use(context, player, TILE), ItemActionResult.SUCCESS, "glow ink used");
        context.expectBlockProperty(TILE, GLOWING, true);
        context.assertEquals(player.getMainHandStack().getCount(), 2, "one glow ink sac used");
        context.assertTrue(context.getBlockState(TILE).getRenderType() == BlockRenderType.INVISIBLE,
                "drawn by its renderer at full light, not baked in the chunk");
        // Already glowing: nothing used
        use(context, player, TILE);
        context.assertEquals(player.getMainHandStack().getCount(), 2, "no glow ink used on a glowing tile");

        PlayerEntity inker = holding(context, new ItemStack(Items.INK_SAC, 2));
        context.assertEquals(use(context, inker, TILE), ItemActionResult.SUCCESS, "ink used");
        context.expectBlockProperty(TILE, GLOWING, false);
        context.assertEquals(inker.getMainHandStack().getCount(), 1, "one ink sac used");
        context.assertTrue(context.getBlockState(TILE).getRenderType() == BlockRenderType.MODEL, "baked again");
        use(context, inker, TILE);
        context.assertEquals(inker.getMainHandStack().getCount(), 1, "no ink used on a tile that doesn't glow");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void creativeUsesNoInk(TestContext context) {
        tile(context);
        PlayerEntity player = context.createMockPlayer(GameMode.CREATIVE);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.GLOW_INK_SAC));
        use(context, player, TILE);
        context.expectBlockProperty(TILE, GLOWING, true);
        context.assertEquals(player.getMainHandStack().getCount(), 1, "nothing used in creative");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theGlowStaysWithACartridgeInserted(TestContext context) {
        BoardSpaceBlockEntity tile = tile(context);
        use(context, holding(context, new ItemStack(Items.GLOW_INK_SAC)), TILE);
        // Its role changes the block state: the glow stays
        tile.setStack(0, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP));
        context.expectBlockProperty(TILE, TILE_TYPE, BoardSpaceType.BOARD_SPACE_STOP);
        context.expectBlockProperty(TILE, GLOWING, true);
        tile.removeStack(0);
        context.expectBlockProperty(TILE, TILE_TYPE, BoardSpaceType.DEFAULT);
        context.expectBlockProperty(TILE, GLOWING, true);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void otherItemsDontTouchTheGlow(TestContext context) {
        tile(context);
        PlayerEntity player = holding(context, new ItemStack(Items.STONE));
        use(context, player, TILE);
        context.expectBlockProperty(TILE, GLOWING, false);
        context.assertEquals(player.getMainHandStack().getCount(), 1, "a stone isn't used");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theGlowIsSavedAndKeptByTheItem(TestContext context) {
        TestBoards.floor(context, 9, 6, 1);
        BoardSpaceBlockEntity tile = tile(context);
        use(context, holding(context, new ItemStack(Items.GLOW_INK_SAC)), TILE);
        // Saved with the world (the block state) and with the tile's item
        BlockState state = context.getBlockState(TILE);
        context.assertTrue(state.get(GLOWING), "glowing");
        tile = context.getBlockEntity(TILE);
        ItemStack item = new ItemStack(ModBlocks.TILE);
        item.applyComponentsFrom(tile.createComponentMap());
        context.assertTrue(item.contains(ModComponents.GLOWING_TILE), "its item keeps the glow: " + item.getComponents());
        NbtCompound nbt = tile.createNbtWithIdentifyingData(context.getWorld().getRegistryManager());
        context.assertFalse(nbt.contains("glowing"), "not in the block entity data (the block state has it)");
        // Placed again: glowing
        PlayerEntity player = holding(context, item);
        BlockPos abs = context.getAbsolutePos(ELSEWHERE);
        item.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND,
                new BlockHitResult(abs.toCenterPos().add(0, 0.5, 0), Direction.UP, abs, false)));
        context.expectBlock(ModBlocks.TILE, ELSEWHERE.up());
        context.expectBlockProperty(ELSEWHERE.up(), GLOWING, true);
        // A plain tile item places a plain tile
        ItemStack plain = new ItemStack(ModBlocks.TILE);
        BlockPos other = ELSEWHERE.add(0, 0, 2);
        BlockPos otherAbs = context.getAbsolutePos(other);
        plain.useOnBlock(new ItemUsageContext(holding(context, plain), Hand.MAIN_HAND,
                new BlockHitResult(otherAbs.toCenterPos().add(0, 0.5, 0), Direction.UP, otherAbs, false)));
        context.expectBlockProperty(other.up(), GLOWING, false);
        context.complete();
    }
}
