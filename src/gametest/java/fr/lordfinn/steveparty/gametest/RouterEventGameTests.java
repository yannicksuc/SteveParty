package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.events.TileReachedEvent;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.ComparatorBlock;
import net.minecraft.block.entity.ComparatorBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Router as a sensor: a comparator reading it pulses when a token stops on one of its board spaces (the strength
 * tells the role) and weakly when a token goes over one, while its redstone input keeps routing.
 */
public class RouterEventGameTests implements FabricGameTest {
    private static final BlockPos TILE = new BlockPos(1, 1, 1);
    private static final BlockPos ROUTER = new BlockPos(5, 1, 5);
    /** Reads the router (its input is on its south side, the router's north). */
    private static final BlockPos COMPARATOR = ROUTER.north();
    /** Comparators react 2 ticks after their input: checked a little later. */
    private static final int SETTLE = 4;

    private static BoardSpaceBlockEntity setUp(TestContext context) {
        context.setBlockState(TILE.down(), Blocks.STONE);
        context.setBlockState(TILE, ModBlocks.ADVANCED_TILE);
        BoardSpaceBlockEntity tile = context.getBlockEntity(TILE);
        tile.setStack(0, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));

        context.setBlockState(ROUTER.down(), Blocks.STONE);
        context.setBlockState(ROUTER, ModBlocks.BOARD_SPACE_REDSTONE_ROUTER);
        BoardSpaceRedstoneRouterBlockEntity router = context.getBlockEntity(ROUTER);
        ItemStack cartridge = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
        cartridge.set(ModComponents.DESTINATIONS_COMPONENT,
                new DestinationsComponent(new ArrayList<>(List.of(context.getAbsolutePos(TILE))), ""));
        router.setStack(0, cartridge);

        context.setBlockState(COMPARATOR.down(), Blocks.STONE);
        context.setBlockState(COMPARATOR, Blocks.COMPARATOR.getDefaultState().with(ComparatorBlock.FACING, Direction.SOUTH));
        return tile;
    }

    private static int comparator(TestContext context) {
        ComparatorBlockEntity comparator = context.getBlockEntity(COMPARATOR);
        return comparator.getOutputSignal();
    }

    private static PigEntity token(TestContext context, int steps) {
        PigEntity pig = context.spawnMob(EntityType.PIG, TILE.up());
        ((TokenizedEntityInterface) pig).steveparty$setTokenized(true);
        ((TokenizedEntityInterface) pig).steveparty$setNbSteps(steps);
        return pig;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void landingPulsesTheRoleLevel(TestContext context) {
        BoardSpaceBlockEntity tile = setUp(context);
        PigEntity pig = token(context, 0);
        context.assertEquals(comparator(context), 0, "no event: no output (not the fill level)");
        TileFeedback.land(context.getWorld(), tile, pig, null);
        context.waitAndRun(SETTLE, () -> {
            context.assertEquals(comparator(context), BoardSpaceRedstoneRouterBlockEntity.LEVEL_DEFAULT, "plain tile landing");
            context.waitAndRun(BoardSpaceRedstoneRouterBlockEntity.LANDING_TICKS + 2, () -> {
                context.assertEquals(comparator(context), 0, "the pulse ended");
                // A stop tile: its own level
                tile.setStack(0, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP));
                TileFeedback.land(context.getWorld(), tile, pig, null);
                context.waitAndRun(SETTLE, () -> {
                    context.assertEquals(comparator(context), BoardSpaceRedstoneRouterBlockEntity.LEVEL_STOP, "stop tile landing");
                    context.complete();
                });
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void passingPulsesWeaklyAndNeverCutsALanding(TestContext context) {
        BoardSpaceBlockEntity tile = setUp(context);
        TileFeedback.pass(context.getWorld(), tile.getPos());
        context.waitAndRun(SETTLE - 1, () -> {
            context.assertEquals(comparator(context), BoardSpaceRedstoneRouterBlockEntity.PASS_SIGNAL, "a token going over");
            context.waitAndRun(BoardSpaceRedstoneRouterBlockEntity.PASS_TICKS + 2, () -> {
                context.assertEquals(comparator(context), 0, "a short pulse");
                TileFeedback.land(context.getWorld(), tile, token(context, 0), null);
                TileFeedback.pass(context.getWorld(), tile.getPos());
                context.waitAndRun(SETTLE, () -> {
                    context.assertEquals(comparator(context), BoardSpaceRedstoneRouterBlockEntity.LEVEL_DEFAULT,
                            "a token passing doesn't cut a landing short");
                    context.complete();
                });
            });
        });
    }

    /** Each role its level, the shop's included; a role without a level of its own gets its landing kind's. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyRoleHasItsLevel(TestContext context) {
        BoardSpaceBlockEntity tile = setUp(context);
        tile.setStack(0, new ItemStack(ModItems.SHOP_CARTRIDGE));
        context.assertEquals(tile.getBoardSpaceBehavior().comparatorLevel(tile, tile.getStack(0)),
                BoardSpaceRedstoneRouterBlockEntity.LEVEL_SHOP, "shop");
        Set<Integer> levels = new HashSet<>();
        for (TileFeedback.Landing landing : TileFeedback.Landing.values()) {
            int level = BoardSpaceRedstoneRouterBlockEntity.landingSignal(landing);
            context.assertTrue(level > BoardSpaceRedstoneRouterBlockEntity.PASS_SIGNAL && level <= 15, landing + " in range");
            // A spent Replay tile is still a Replay tile: same level; the Mistigri's space is a malus space (no level left)
            if (landing == TileFeedback.Landing.MISTIGRI) {
                context.assertEquals(level, BoardSpaceRedstoneRouterBlockEntity.LEVEL_MALUS, "the Mistigri: a malus");
            } else if (landing != TileFeedback.Landing.REPLAY_SPENT) {
                context.assertTrue(levels.add(level), landing + " has a level of its own");
            }
        }
        TileFeedback.land(context.getWorld(), tile, token(context, 0), null);
        context.waitAndRun(SETTLE, () -> {
            context.assertEquals(comparator(context), BoardSpaceRedstoneRouterBlockEntity.LEVEL_SHOP, "shop tile landing");
            context.complete();
        });
    }

    /** Outside a game too: a token moved with a dice stops, or goes over. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void outsideAGameTheRouterStillPulses(TestContext context) {
        BoardSpaceBlockEntity tile = setUp(context);
        tile.setStack(0, new ItemStack(ModItems.TILE_BEHAVIOR_START));
        TileReachedEvent.EVENT.invoker().onTileReached(token(context, 0), tile);
        context.waitAndRun(SETTLE, () -> {
            context.assertEquals(comparator(context), BoardSpaceRedstoneRouterBlockEntity.LEVEL_START, "stopped on the start");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void inputStillRoutesWithAComparatorAttached(TestContext context) {
        BoardSpaceBlockEntity tile = setUp(context);
        tile.setStack(15, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP));
        context.setBlockState(ROUTER.east(), Blocks.REDSTONE_BLOCK);
        context.assertEquals(tile.getActiveSlot(), 15, "the router's power picks the slot");
        context.waitAndRun(SETTLE, () -> {
            context.assertEquals(comparator(context), 0, "power in is no event out");
            TileFeedback.land(context.getWorld(), tile, token(context, 0), null);
            context.waitAndRun(SETTLE, () -> {
                context.assertEquals(comparator(context), BoardSpaceRedstoneRouterBlockEntity.LEVEL_STOP, "the active (stop) role's level");
                context.assertEquals(tile.getActiveSlot(), 15, "the pulse doesn't feed back into the routing");
                context.removeBlock(ROUTER.east());
                context.assertEquals(tile.getActiveSlot(), 0, "still routed");
                context.complete();
            });
        });
    }
}
