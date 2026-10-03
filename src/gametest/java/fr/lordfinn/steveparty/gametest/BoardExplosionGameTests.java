package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The blocks a game is built with ({@code steveparty:board_infrastructure}) are not broken by explosions, withers nor
 * the dragon, and are mined as before.
 */
public class BoardExplosionGameTests implements FabricGameTest {
    private static final TagKey<Block> BOARD = TagKey.of(RegistryKeys.BLOCK, Steveparty.id("board_infrastructure"));

    /** Every block of the list: bedrock's explosion resistance, and immune to withers and the dragon. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theBoardBlocksResistExplosionsWithersAndTheDragon(TestContext context) {
        List<Block> blocks = new ArrayList<>();
        for (RegistryEntry<Block> entry : Registries.BLOCK.iterateEntries(BOARD)) blocks.add(entry.value());
        context.assertTrue(blocks.size() == 26, "the 26 blocks of the list are there, got " + blocks.size());
        for (Block block : blocks) {
            String id = Registries.BLOCK.getId(block).toString();
            context.assertTrue(block.getBlastResistance() == ModBlocks.BOARD_RESISTANCE, id + ": bedrock's explosion resistance");
            context.assertTrue(block.getDefaultState().isIn(BlockTags.WITHER_IMMUNE), id + ": immune to withers");
            context.assertTrue(block.getDefaultState().isIn(BlockTags.DRAGON_IMMUNE), id + ": immune to the dragon");
        }
        context.complete();
    }

    /** A TNT-sized explosion in the middle of a board leaves each kind of block in place; the stone around goes. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anExplosionLeavesTheBoardInPlace(TestContext context) {
        List<Block> kinds = List.of(ModBlocks.TILE, ModBlocks.PARTY_CONTROLLER, ModBlocks.MINI_GAME_CONTROLLER, ModBlocks.STEP_CONTROLLER,
                ModBlocks.PODIUM, ModBlocks.GOAL_POLE_BASE, ModBlocks.PARTY_BELL, ModBlocks.HOP_SWITCH, ModBlocks.LOOTING_BOX,
                ModBlocks.PIGGY_BANK, ModBlocks.DICE_FORGE, ModBlocks.TELESCOPE, ModBlocks.CASH_REGISTER, ModBlocks.STENCIL_MAKER,
                ModBlocks.COPPER_MINIGAME_PIPE);
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.BEDROCK);
        List<BlockPos> placed = new ArrayList<>();
        for (int i = 0; i < kinds.size(); i++) {
            // A ring of 3 x 5 around the blast, one block apart
            BlockPos pos = new BlockPos(2 + (i % 5), 1, 2 + (i / 5) * 2);
            context.setBlockState(pos, kinds.get(i));
            placed.add(pos);
        }
        BlockPos stone = new BlockPos(4, 2, 4);
        context.setBlockState(stone, Blocks.STONE);
        BlockPos center = context.getAbsolutePos(new BlockPos(4, 1, 3));
        context.getWorld().createExplosion(null, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 4.0f, World.ExplosionSourceType.TNT);
        for (int i = 0; i < kinds.size(); i++) {
            context.assertTrue(context.getBlockState(placed.get(i)).isOf(kinds.get(i)), Registries.BLOCK.getId(kinds.get(i)) + " is still there");
        }
        context.assertTrue(context.getBlockState(stone).isAir(), "the stone of the board is blown away");
        context.complete();
    }

    /** Mined in survival, a board block still breaks and drops itself. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aBoardBlockIsMinedAsBefore(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.PARTY_CONTROLLER);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            player.changeGameMode(GameMode.SURVIVAL);
            player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            BlockPos absolute = context.getAbsolutePos(pos);
            player.refreshPositionAndAngles(absolute.getX() + 1.5, absolute.getY(), absolute.getZ() + 0.5, 0, 0);
            context.assertTrue(player.interactionManager.tryBreakBlock(absolute), "the player breaks it");
            context.assertTrue(context.getBlockState(pos).isAir(), "it is gone");
            List<ItemEntity> drops = context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(absolute).expand(1.5),
                    item -> item.getStack().isOf(ModBlocks.PARTY_CONTROLLER.asItem()));
            context.assertTrue(!drops.isEmpty(), "and drops itself");
        } finally {
            context.getWorld().getServer().getPlayerManager().remove(player);
        }
        context.complete();
    }
}
