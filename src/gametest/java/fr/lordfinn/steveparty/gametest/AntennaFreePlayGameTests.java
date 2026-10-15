package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.MiniGameControllerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlock;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.blocks.custom.PartyController.StepPulse;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlock;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeSolid;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.MiniGamePageRef;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.gametest.kit.TestCleanup;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.minigame.MiniGameTest;
import fr.lordfinn.steveparty.service.TokenMovementService;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * Out of a party, a Party Antenna hears only through its brush link, never by distance: the free tokens moving on the
 * board it is linked to, the runs of the mini-game it is linked to; only the moments {@link PartyMoment#outOfParty};
 * and it never waits.
 */
public class AntennaFreePlayGameTests implements SteveGameTest {
    private static final BlockPos CONTROLLER_A = new BlockPos(1, 1, 1), START_A = new BlockPos(3, 1, 1), NEXT_A = new BlockPos(5, 1, 1);
    private static final BlockPos CONTROLLER_B = new BlockPos(1, 1, 9), START_B = new BlockPos(3, 1, 9), NEXT_B = new BlockPos(5, 1, 9);
    private static final int GREEN = 13;

    private static PartyBellBlockEntity antenna(TestContext context, BlockPos pos, PartyMoment moment, boolean waiting, BlockPos link) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.PARTY_BELL.getDefaultState()
                .with(PartyBellBlock.MOMENT, moment).with(PartyBellBlock.WAITING, waiting));
        TestCleanup.atEnd(context, () -> TestCleanup.removeIf(context, pos, ModBlocks.PARTY_BELL));
        PartyBellBlockEntity antenna = context.getBlockEntity(pos);
        antenna.setLink(link == null ? null : GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(link)));
        return antenna;
    }

    private static void tile(TestContext context, BlockPos pos, ItemStack cartridge, BlockPos to) {
        context.setBlockState(pos, ModBlocks.TILE);
        cartridge.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(new ArrayList<>(List.of(context.getAbsolutePos(to))), ""));
        BoardSpaceBlockEntity space = context.getBlockEntity(pos);
        space.setStack(0, cartridge);
    }

    /** A board: its controller (no party), its start tile linked to it, a plain space, a loop between them. */
    private static void board(TestContext context, BlockPos controllerPos, BlockPos start, BlockPos next) {
        context.setBlockState(controllerPos, ModBlocks.PARTY_CONTROLLER);
        TestCleanup.atEnd(context, () -> TestCleanup.removeIf(context, controllerPos, ModBlocks.PARTY_CONTROLLER));
        PartyControllerEntity controller = context.getBlockEntity(controllerPos);
        ItemStack startCartridge = new ItemStack(ModItems.TILE_BEHAVIOR_START);
        startCartridge.set(ModComponents.TB_START_BOUND_ENTITY, UUID.randomUUID().toString());
        tile(context, start, startCartridge, next);
        tile(context, next, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), start);
        controller.setStartLinks(List.of(context.getAbsolutePos(start)));
        controller.board(context.getWorld(), true);
    }

    /** The moments heard out of a party: the free play ones and the mini-game runs, no other. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void onlyFreePlayAndMiniGameRunMomentsAreHeardOutOfAParty(TestContext context) {
        Set<PartyMoment> expected = EnumSet.of(PartyMoment.DICE_ROLLED, PartyMoment.CROSSROADS, PartyMoment.PATH_CHOSEN,
                PartyMoment.TOKEN_LANDS, PartyMoment.TURN_END, PartyMoment.PRACTICE_ROUND, PartyMoment.MINIGAME_START, PartyMoment.MINIGAME_END);
        for (PartyMoment moment : PartyMoment.values())
            context.assertEquals(moment.outOfParty(), expected.contains(moment), moment + " out of a party");
        context.complete();
    }

    /**
     * A free token on a board: the antennas linked to that board hear it (the die, its landing, its turn end), a waiting
     * one without holding anything; the antennas linked to another board, or unlinked right beside it, do not; a
     * party-only moment is never heard.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "antenna_free_play")
    public void aFreeTokenRingsTheAntennasLinkedToItsBoard(TestContext context) {
        TestBoards.floor(context, 12);
        board(context, CONTROLLER_A, START_A, NEXT_A);
        board(context, CONTROLLER_B, START_B, NEXT_B);
        PigEntity pig = TestBoards.token(context, START_A, null);
        TestCleanup.atEnd(context, () -> ((TokenizedEntityInterface) pig).steveparty$setTokenized(false));
        ServerWorld world = context.getWorld();

        PartyBellBlockEntity diceA = antenna(context, new BlockPos(8, 1, 1), PartyMoment.DICE_ROLLED, false, NEXT_A);
        PartyBellBlockEntity diceB = antenna(context, new BlockPos(8, 1, 9), PartyMoment.DICE_ROLLED, false, START_B);
        PartyBellBlockEntity diceUnlinked = antenna(context, new BlockPos(3, 1, 3), PartyMoment.DICE_ROLLED, false, null);
        PartyBellBlockEntity landsA = antenna(context, new BlockPos(8, 1, 3), PartyMoment.TOKEN_LANDS, false, CONTROLLER_A);
        PartyBellBlockEntity endA = antenna(context, new BlockPos(8, 1, 5), PartyMoment.TURN_END, true, START_A);
        PartyBellBlockEntity endB = antenna(context, new BlockPos(8, 1, 7), PartyMoment.TURN_END, false, CONTROLLER_B);
        PartyBellBlockEntity coinsA = antenna(context, new BlockPos(10, 1, 1), PartyMoment.COINS_WON, false, START_A);

        context.waitAndRun(2, () -> {
            PartyControllerEntity.onTokenDiceRolled(world, pig, 4);
            context.assertTrue(diceA.getHeardAt() >= 0 && diceA.getHeardValue() == 4 && diceA.heardPassed(), "its board's antenna hears the die");
            context.assertTrue(diceB.getHeardAt() < 0, "another board's antenna does not");
            context.assertTrue(diceUnlinked.getHeardAt() < 0, "an unlinked antenna beside the token does not");
            PartyBellBlockEntity.ringFreePlay(world, pig, PartyMoment.COINS_WON, 3);
            context.assertTrue(coinsA.getHeardAt() < 0, "a party-only moment is never heard out of a party");

            TokenMovementService.moveEntityOnBoard(pig, 1);
            when(context, () -> endA.getHeardAt() >= 0, 150, "the free token arrived", () -> {
                context.assertTrue(landsA.getHeardAt() >= 0 && landsA.getHeardValue() == 15, "its landing heard (a plain space: 15)");
                context.assertTrue(endA.heardPassed() && endA.getHeardValue() == 1, "its turn end heard");
                context.assertTrue(!endA.isHolding(), "a waiting antenna holds nothing out of a party: nothing to pause");
                context.assertTrue(endB.getHeardAt() < 0, "nor on the other board");

                // The same token on the other board: its antennas only
                long heardA = diceA.getHeardAt();
                Vec3d onB = context.getAbsolutePos(START_B).toCenterPos();
                pig.refreshPositionAndAngles(onB.x, onB.y - 0.4, onB.z, 0, 0);
                PartyControllerEntity.onTokenDiceRolled(world, pig, 5);
                context.assertTrue(diceB.getHeardAt() >= 0 && diceB.getHeardValue() == 5, "the other board's antenna hears it there");
                context.assertTrue(diceA.getHeardAt() == heardA && diceA.getHeardValue() == 4, "the first board's does not");
                context.complete();
            });
        });
    }

    /**
     * A mini-game played out of a party: an antenna linked to its Mini-game Controller hears it launched (practice
     * round, a waiting one holding nothing), the players sent out (start) and its results (end, winners); an unlinked
     * one hears nothing.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = "antenna_free_play_minigame")
    public void aMiniGameRunRingsTheAntennasLinkedToItsController(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        context.setBlockState(new BlockPos(1, 1, 1), Blocks.STONE);
        BlockPos pipe = new BlockPos(1, 2, 1);
        context.setBlockState(pipe, ModBlocks.PIPES[PipeKind.OPAQUE.ordinal()][GREEN].getDefaultState().with(PipeBlock.SOLID, PipeSolid.DOWN));
        UUID page = UUID.randomUUID();
        MiniGamePages.update(server, MiniGamePageData.empty(page).withTexts("Antenne", ""));
        MiniGamePages.toggleLink(server, page, GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(pipe)),
                Direction.UP, MiniGamePipeRole.ofPipe(context.getBlockState(pipe)));
        BlockPos home = new BlockPos(5, 1, 5);
        context.setBlockState(home, ModBlocks.MINI_GAME_CONTROLLER);
        TestCleanup.atEnd(context, () -> TestCleanup.removeIf(context, home, ModBlocks.MINI_GAME_CONTROLLER));
        ItemStack pageItem = new ItemStack(ModItems.MINI_GAME_PAGE);
        pageItem.set(ModComponents.MINI_GAME_PAGE, new MiniGamePageRef(page, "", false));
        ((MiniGameControllerBlockEntity) context.getBlockEntity(home)).setPage(pageItem);

        PartyBellBlockEntity practice = antenna(context, new BlockPos(7, 1, 1), PartyMoment.PRACTICE_ROUND, true, home);
        PartyBellBlockEntity start = antenna(context, new BlockPos(7, 1, 3), PartyMoment.MINIGAME_START, false, home);
        PartyBellBlockEntity end = antenna(context, new BlockPos(7, 1, 5), PartyMoment.MINIGAME_END, false, home);
        PartyBellBlockEntity unlinked = antenna(context, new BlockPos(7, 1, 7), PartyMoment.PRACTICE_ROUND, false, null);
        ServerPlayerEntity player = TestPlayers.joinedCreative(context, "t", "ant", 1.5, 1, 2.5);
        try {
            TestPlayers.alone(context, new Vec3d(4, 2, 4), 40, player);
            context.assertEquals(MiniGameTest.start(server, page, player, 0), MiniGameTest.Status.READY, "the run starts");
            context.assertTrue(practice.getHeardAt() >= 0 && practice.getHeardValue() == 1 && !practice.isHolding(),
                    "launched: its practice heard (1 player), never held");
            context.assertTrue(start.getHeardAt() >= 0 && start.getHeardValue() == 1, "the player sent out: its start heard");
            context.assertTrue(unlinked.getHeardAt() < 0, "an unlinked antenna hears nothing");
            MiniGameTest test = MiniGameTest.of(page);
            context.assertTrue(test != null && test.step(StepPulse.Mode.NEXT), "ended");
            context.assertTrue(end.getHeardAt() >= 0 && end.getHeardValue() == 0, "its end heard: nobody on a podium, no winner");
        } finally {
            MiniGameTest.stop(page);
            TestPlayers.leaveMiniGames(context, player);
        }
        context.complete();
    }
}
