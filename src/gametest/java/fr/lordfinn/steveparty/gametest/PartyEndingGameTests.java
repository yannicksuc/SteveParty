package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.EndPipe;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyEnding;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyParking;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.board.BrushLinkable;
import fr.lordfinn.steveparty.board.BrushLinks;
import fr.lordfinn.steveparty.board.ControllerEndLinks;
import fr.lordfinn.steveparty.board.LinkHistory;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestCleanup;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.gametest.kit.TestWait;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.EndCartridgeItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.gametest.kit.TestAsserts.assertOn;

/**
 * Where everyone goes once a party is over: the pawns onto the podiums and the Finish spaces linked to the Party
 * Controller with the brush (the others home), the ranking kept for the panel over the podiums, and a pawn set aside
 * there still plays the next party from its start tile.
 */
public class PartyEndingGameTests implements SteveGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(4, 1, 7);
    private static final BlockPos START_A = new BlockPos(1, 1, 1), START_B = new BlockPos(3, 1, 1), START_C = new BlockPos(5, 1, 1);
    private static final BlockPos FINISH = new BlockPos(3, 1, 4), PLAIN = new BlockPos(5, 1, 4);

    private static BoardSpaceBlockEntity tile(TestContext context, BlockPos pos, ItemStack cartridge) {
        context.setBlockState(pos, ModBlocks.TILE);
        BoardSpaceBlockEntity tile = context.getBlockEntity(pos);
        tile.setStack(0, cartridge);
        return tile;
    }

    private static PigEntity pawn(TestContext context, BlockPos start, String name) {
        PigEntity pig = context.spawnMob(EntityType.PIG, start);
        pig.setAiDisabled(true);
        pig.setCustomName(Text.literal(name));
        ((TokenizedEntityInterface) pig).steveparty$setTokenized(true);
        ItemStack cartridge = new ItemStack(ModItems.TILE_BEHAVIOR_START);
        cartridge.set(ModComponents.TB_START_BOUND_ENTITY, pig.getUuidAsString());
        tile(context, start, cartridge);
        return pig;
    }

    private record Board(PartyControllerEntity controller, List<PigEntity> pawns) {
    }

    /** Three pawns on their linked start tiles, a Finish space, a plain space, a controller. */
    private static Board board(TestContext context) {
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
        List<PigEntity> pawns = List.of(pawn(context, START_A, "A"), pawn(context, START_B, "B"), pawn(context, START_C, "C"));
        tile(context, FINISH, EndCartridgeItem.with(ModItems.END_CARTRIDGE, EndCartridgeItem.Ending.FINISH_ROUND, EndCartridgeItem.Ranking.STARS));
        tile(context, PLAIN, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        controller.setStartLinks(List.of(context.getAbsolutePos(START_A), context.getAbsolutePos(START_B), context.getAbsolutePos(START_C)));
        TestCleanup.atEnd(context, () -> {
            controller.stopParty(null);
            for (PigEntity pawn : pawns) {
                ((TokenizedEntityInterface) pawn).steveparty$setTokenized(false);
                PartyParking.restoreSize(pawn);
            }
            TestCleanup.removeIf(context, CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        });
        return new Board(controller, pawns);
    }

    /** The party reaches its end at once. */
    private static void endParty(PartyControllerEntity controller) {
        var data = controller.getPartyData();
        data.getSteps().add(data.getStepIndex() + 1, new EndPartyStep(new ArrayList<>(data.getTokens())));
        controller.nextStep();
    }

    /** From the controller, the brush links a Finish space (not a plain one) and a podium, by its bottom block. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theBrushLinksFinishSpacesAndPodiumsToTheController(TestContext context) {
        Board board = board(context);
        place(context, new BlockPos(7, 1, 4), ModBlocks.PODIUM.getDefaultState().with(PodiumBlock.FULL, true));
        place(context, new BlockPos(7, 2, 4), ModBlocks.GOLD_PODIUM.getDefaultState());
        var world = context.getWorld();
        BlockPos controller = context.getAbsolutePos(CONTROLLER);
        List<BrushLinkable> kinds = BrushLinks.of(world, controller, -1);
        context.assertTrue(BrushLinks.kindFor(kinds, world, context.getAbsolutePos(FINISH)) instanceof ControllerEndLinks.Arrivals, "a Finish space: an arrival");
        context.assertTrue(BrushLinks.kindFor(kinds, world, context.getAbsolutePos(PLAIN)) == null, "a plain space: nothing");
        BlockPos podiumTop = context.getAbsolutePos(new BlockPos(7, 2, 4));
        context.assertTrue(BrushLinks.kindFor(kinds, world, podiumTop) instanceof ControllerEndLinks.Podiums, "a podium");
        ServerPlayerEntity player = TestPlayers.mock(context);
        LinkHistory.begin(player, world, null);
        new ControllerEndLinks.Arrivals(board.controller()).link(player, world, context.getAbsolutePos(FINISH));
        new ControllerEndLinks.Podiums(board.controller()).link(player, world, podiumTop);
        LinkHistory.commit(player, Text.literal("test"), null);
        context.assertEquals(board.controller().getArrivalLinks(), List.of(context.getAbsolutePos(FINISH)), "the Finish space linked");
        context.assertEquals(board.controller().getPodiumLinks(), List.of(context.getAbsolutePos(new BlockPos(7, 1, 4))), "the podium linked by its bottom");
        // From the Finish space to the controller: the same link, painted the other way round
        context.assertTrue(BrushLinks.outcome(world, context.getAbsolutePos(FINISH), -1, controller) == BrushLinks.Outcome.ERASE, "the other way round: erased");
        // Undone together
        LinkHistory.undo(player, true, null);
        context.assertTrue(board.controller().getArrivalLinks().isEmpty() && board.controller().getPodiumLinks().isEmpty(), "undone");
        context.complete();
    }

    /** No podium: every pawn gathers on the Finish space, small and turning; the next party they play from their start. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void thePawnsGatherOnTheFinishSpaceAndStillPlayTheNextParty(TestContext context) {
        Board board = board(context);
        PartyControllerEntity controller = board.controller();
        controller.setArrivalLinks(List.of(context.getAbsolutePos(FINISH)));
        controller.boot();
        context.assertEquals(controller.getPartyData().getTokens().size(), 3, "three pawns play");
        endParty(controller);
        context.assertTrue(controller.getPartyData().isAtEnd(), "the party is over");
        TestWait.when(context, () -> board.pawns().stream().allMatch(PartyParking::isSmall), 40, "the pawns are small on the Finish space", () -> {
            Vec3d middle = BoardSpaces.standPos(context.getWorld(), context.getAbsolutePos(FINISH));
            for (PigEntity pawn : board.pawns())
                context.assertTrue(pawn.getPos().squaredDistanceTo(middle) < 0.6 * 0.6, pawn.getName().getString() + " round the Finish space's middle");
            context.assertTrue(!controller.getEndResults().isEmpty(), "the ranking is kept");
            // A new party: they all play again, from their own start tile, their size back
            controller.boot();
            context.assertEquals(controller.getPartyData().getTokens().size(), 3, "the three pawns play again");
            assertOn(context, board.pawns().get(0), START_A, "A on its start");
            assertOn(context, board.pawns().get(2), START_C, "C on its start");
            context.assertTrue(board.pawns().stream().noneMatch(PartyParking::isSmall), "their size back");
            context.assertTrue(controller.getEndResults().isEmpty(), "the last ranking is gone");
            context.complete();
        });
    }

    /** Podiums linked: the best pawns stand on them, the tallest for the winner, the others go to the Finish space. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void theBestPawnsStandOnThePodiums(TestContext context) {
        Board board = board(context);
        PartyControllerEntity controller = board.controller();
        // Two columns: the tallest (2 blocks) for the 1st, then 1 block for the 2nd
        place(context, new BlockPos(7, 1, 3), ModBlocks.PODIUM.getDefaultState().with(PodiumBlock.FULL, true));
        place(context, new BlockPos(7, 2, 3), ModBlocks.PODIUM.getDefaultState().with(PodiumBlock.FULL, true));
        place(context, new BlockPos(7, 1, 4), ModBlocks.PODIUM.getDefaultState().with(PodiumBlock.FULL, true));
        controller.setPodiumLinks(List.of(context.getAbsolutePos(new BlockPos(7, 1, 4))));
        controller.setArrivalLinks(List.of(context.getAbsolutePos(FINISH)));
        controller.boot();
        List<java.util.UUID> order = List.copyOf(controller.getPartyData().getTokens());
        endParty(controller);
        // Same stars and coins: the turn order ranks them
        MobEntity first = (MobEntity) context.getWorld().getEntity(order.get(0));
        MobEntity second = (MobEntity) context.getWorld().getEntity(order.get(1));
        MobEntity third = (MobEntity) context.getWorld().getEntity(order.get(2));
        TestWait.when(context, () -> first.getY() > context.getAbsolute(new Vec3d(0, 2.9, 0)).y, 40, "the winner on the tallest podium", () -> {
            Vec3d tallest = context.getAbsolute(new Vec3d(7.5, 3, 3.5)), lower = context.getAbsolute(new Vec3d(7.5, 2, 4.5));
            context.assertTrue(first.getPos().squaredDistanceTo(tallest) < 0.01, "1st on the tallest column: " + first.getPos());
            context.assertTrue(second.getPos().squaredDistanceTo(lower) < 0.01, "2nd on the other: " + second.getPos());
            assertOn(context, third, FINISH, "3rd on the Finish space");
            context.assertEquals(controller.getEndResults().size(), 3, "the ranking for the panel");
            context.complete();
        });
    }

    /** The End party card of the program gives the pipe the players come out of. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theEndCardGivesThePipe(TestContext context) {
        Board board = board(context);
        PartyControllerEntity controller = board.controller();
        context.assertTrue(PartyEnding.endPipe(controller) == null, "no End card: no pipe");
        ItemStack card = new ItemStack(ModItems.PARTY_CARD_END);
        EndPipe pipe = new EndPipe(GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(new BlockPos(8, 1, 8))), Direction.UP);
        card.set(ModComponents.END_PIPE, pipe);
        controller.getProgram().setStack(3, card);
        context.assertEquals(PartyEnding.endPipe(controller), pipe, "its pipe");
        // It plays no step: a program of an End card alone is the default party
        context.assertEquals(fr.lordfinn.steveparty.blocks.custom.PartyController.steps.BasicGameGeneratorStep.expand(List.of(card), 3),
                fr.lordfinn.steveparty.blocks.custom.PartyController.steps.BasicGameGeneratorStep.expand(List.of(), 3), "no step of its own");
        controller.getProgram().setStack(3, ItemStack.EMPTY);
        context.complete();
    }

    private static void place(TestContext context, BlockPos pos, BlockState state) {
        BlockPos abs = context.getAbsolutePos(pos);
        context.getWorld().setBlockState(abs, Block.postProcessState(state, context.getWorld(), abs));
    }
}
