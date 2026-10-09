package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.List;

/**
 * A cartridge in hand right-clicked through the whole server path (every global {@code UseBlockCallback}, the block,
 * then the item) on a board space, sneaking, or on a plain block of the ground: that block is selected as a
 * destination, and a second click removes it.
 */
public class CartridgeSelectGameTests implements FabricGameTest {

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aCartridgeClickSelectsTheBoardSpaceOrTheGround(TestContext context) {
        List<BlockPos> tiles = BoardLinkingGameTests.tiles(context, ModBlocks.TILE, new BlockPos(1, 1, 1));
        BlockPos tile = tiles.getFirst();
        context.setBlockState(new BlockPos(5, 0, 1), net.minecraft.block.Blocks.STONE);
        BlockPos ground = context.getAbsolutePos(new BlockPos(5, 0, 1));
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            player.changeGameMode(GameMode.SURVIVAL);
            Vec3d at = context.getAbsolute(new Vec3d(3.5, 1, 3.5));
            player.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
            player.setSneaking(true);
            for (Item kind : List.of(ModItems.TILE_BEHAVIOR_START, ModItems.TELEPORT_CARTRIDGE, ModItems.STAR_CARTRIDGE,
                    ModItems.ADVANCE_BACK_CARTRIDGE, ModItems.INVENTORY_CARTRIDGE, ModItems.FROUSSEUX_CARTRIDGE)) {
                player.setStackInHand(Hand.MAIN_HAND, new ItemStack(kind));
                click(context, player, tile);
                DestinationsComponent links = player.getMainHandStack().get(ModComponents.DESTINATIONS_COMPONENT);
                context.assertTrue(links != null && links.destinations().equals(List.of(tile)),
                        kind + " selects the board space it is used on, got " + links);
                click(context, player, tile);
                context.assertTrue(!player.getMainHandStack().contains(ModComponents.DESTINATIONS_COMPONENT),
                        kind + " unselects it on a second click");
                // Not sneaking, a plain block of the ground: selected the same way
                player.setSneaking(false);
                click(context, player, ground);
                links = player.getMainHandStack().get(ModComponents.DESTINATIONS_COMPONENT);
                context.assertTrue(links != null && links.destinations().equals(List.of(ground)),
                        kind + " selects the ground block it is used on, got " + links);
                click(context, player, ground);
                context.assertTrue(!player.getMainHandStack().contains(ModComponents.DESTINATIONS_COMPONENT),
                        kind + " unselects the ground block on a second click");
                player.setSneaking(true);
            }
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    private static void click(TestContext context, ServerPlayerEntity player, BlockPos abs) {
        player.interactionManager.interactBlock(player, context.getWorld(), player.getMainHandStack(), Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(abs), Direction.UP, abs, false));
    }
}
