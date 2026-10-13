package fr.lordfinn.steveparty.gametest;

import com.mojang.authlib.GameProfile;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMemberships;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.StartRollsStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A player leaving the party he is in ({@code /steveparty leave}), at any moment of it. */
public class PartyLeaveGameTests implements SteveGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(2, 1, 2);

    private static ServerPlayerEntity player(TestContext context, String name) {
        ServerPlayerEntity player = TestPlayers.joined(context, "leave", name, GameMode.SURVIVAL, 1, 2, 1);
        TestPlayers.removeAtEnd(context, player);
        return player;
    }

    private static UUID token(TestContext context, int x, ServerPlayerEntity owner) {
        return TestBoards.token(context, new BlockPos(x, 1, 5), owner.getUuid()).getUuid();
    }

    private static boolean inGame(TestContext context, UUID token) {
        return context.getWorld().getEntity(token) instanceof TokenizedEntityInterface tokenized
                && TokenStatus.hasStatus(tokenized.steveparty$getStatus(), TokenStatus.IN_GAME);
    }

    private static void follow(PartyControllerEntity controller, ServerPlayerEntity... players) {
        for (ServerPlayerEntity player : players) controller.addInterestedPlayer(player);
    }

    /** Leaving while the turn order is rolled: the rolls go on without him, the party starts with the others. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void leavingDuringTheTurnOrderRolls(TestContext context) {
        ServerPlayerEntity alice = player(context, "A"), bob = player(context, "B");
        UUID a = token(context, 1, alice), b = token(context, 3, bob);
        context.setBlockState(CONTROLLER.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        PartyData data = new PartyData();
        data.addToken(a);
        data.addToken(b);
        StartRollsStep rolls = new StartRollsStep();
        data.addStep(rolls);
        data.addStep(new TokenTurnPartyStep(a, alice.getUuid()));
        data.addStep(new TokenTurnPartyStep(b, bob.getUuid()));
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(a, b))));
        controller.setPartyData(data);
        controller.nextStep();
        follow(controller, alice, bob);
        context.assertTrue(data.getCurrentStep() == rolls, "the turn order is rolled");
        rolls.rolls.put(b, 4);

        context.assertTrue(controller.removePlayer(alice.getUuid()), "alice leaves");
        context.assertEquals(data.getTokens(), List.of(b), "her pawn is out");
        context.assertTrue(!inGame(context, a), "her pawn is out of the game");
        context.assertTrue(!controller.getInterestedPlayers().contains(alice.getUuid()), "she no longer follows the party");
        context.waitAndRun(5, () -> {
            context.assertTrue(data.isStarted(), "the party goes on");
            context.assertTrue(data.getCurrentStep() instanceof TokenTurnPartyStep turn && b.equals(turn.getTokenUUID()),
                    "bob, who had rolled, plays");
            context.assertTrue(data.getSteps().stream().noneMatch(step -> step instanceof TokenTurnPartyStep turn && a.equals(turn.getTokenUUID())),
                    "no turn of alice left");
            controller.stopParty(null);
            context.complete();
        });
    }

    /** Leaving during someone else's turn: his turns are removed, the current turn goes on. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void leavingDuringSomeoneElsesTurn(TestContext context) {
        ServerPlayerEntity alice = player(context, "A"), bob = player(context, "B");
        UUID a = token(context, 1, alice), b = token(context, 3, bob);
        PartyControllerEntity controller = TestBoards.party(context, CONTROLLER, 2, a, b);
        PartyData data = controller.getPartyData();
        follow(controller, alice, bob);
        TokenTurnPartyStep turnOfA = (TokenTurnPartyStep) data.getCurrentStep();

        context.assertTrue(controller.removePlayer(bob.getUuid()), "bob leaves");
        context.assertTrue(data.getCurrentStep() == turnOfA, "still alice's turn");
        context.assertEquals(data.getTokens(), List.of(a), "his pawn is out");
        context.assertTrue(data.getSteps().stream().noneMatch(step -> step instanceof TokenTurnPartyStep turn && b.equals(turn.getTokenUUID())),
                "no turn of bob left");
        context.assertTrue(!inGame(context, b), "his pawn is out of the game");
        context.assertTrue(inGame(context, a), "alice still plays");
        context.assertTrue(PartyMemberships.get(context.getWorld().getServer()).of(bob.getUuid()) == null, "bob follows no party");
        context.assertTrue(!controller.removePlayer(bob.getUuid()), "nothing more to leave");
        controller.stopParty(null);
        context.complete();
    }

    /** Leaving during his own turn ({@code /steveparty leave}): the turn passes to the next player at once. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void leavingDuringHisOwnTurnPassesIt(TestContext context) {
        ServerPlayerEntity alice = player(context, "A"), bob = player(context, "B");
        UUID a = token(context, 1, alice), b = token(context, 3, bob);
        PartyControllerEntity controller = TestBoards.party(context, CONTROLLER, 2, a, b);
        PartyData data = controller.getPartyData();
        follow(controller, alice, bob);
        context.assertTrue(data.getCurrentStep() instanceof TokenTurnPartyStep turn && a.equals(turn.getTokenUUID()), "alice's turn");

        context.getWorld().getServer().getCommandManager().executeWithPrefix(alice.getCommandSource(), "/steveparty leave");
        context.assertTrue(data.isStarted(), "the party goes on");
        context.assertTrue(data.getCurrentStep() instanceof TokenTurnPartyStep turn && b.equals(turn.getTokenUUID())
                && turn.getStatus() == fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep.Status.IN_PROGRESS,
                "bob's turn now");
        context.assertEquals(data.getTokens(), List.of(b), "her pawn is out");
        context.assertTrue(!inGame(context, a), "her pawn is out of the game");
        context.assertTrue(!controller.getInterestedPlayers().contains(alice.getUuid()), "she no longer follows the party");
        controller.stopParty(null);
        context.complete();
    }

    /** The last player leaving (with all his pawns) ends the party: everything is put back as after a stop. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void lastPlayerLeavingEndsTheParty(TestContext context) {
        ServerPlayerEntity alice = player(context, "A");
        UUID a1 = token(context, 1, alice), a2 = token(context, 3, alice);
        PartyControllerEntity controller = TestBoards.party(context, CONTROLLER, 2, a1, a2);
        follow(controller, alice);

        context.assertTrue(controller.removePlayer(alice.getUuid()), "alice leaves");
        PartyData data = controller.getPartyData();
        context.assertTrue(!data.isStarted(), "the party is over");
        context.assertTrue(data.getTokens().isEmpty(), "no pawn left in it");
        context.assertTrue(!inGame(context, a1) && !inGame(context, a2), "both her pawns are out of the game");
        context.assertTrue(controller.getInterestedPlayers().isEmpty(), "nobody follows it");
        context.complete();
    }

    /**
     * Reconnecting: a remembered party that is gone (no controller there any more) is forgotten; one that still runs
     * and is followed is kept.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void staleOrLivePartyOnReconnect(TestContext context) {
        PartyMemberships memberships = PartyMemberships.get(context.getWorld().getServer());
        // Gone: the controller's place is plain stone now
        context.setBlockState(new BlockPos(5, 1, 5), Blocks.STONE);
        GameProfile ghost = TestPlayers.profile("leave", "Ghost");
        memberships.follow(ghost.getId(), GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(new BlockPos(5, 1, 5))));
        ServerPlayerEntity ghostPlayer = TestPlayers.join(context, ghost);
        TestPlayers.removeAtEnd(context, ghostPlayer);
        context.assertTrue(memberships.of(ghost.getId()) == null, "the gone party is forgotten");

        // Still running, still followed: kept
        ServerPlayerEntity alice = TestPlayers.joined(context, "leave", "A", GameMode.SURVIVAL, 1, 2, 1);
        UUID a = token(context, 1, alice);
        PartyControllerEntity controller = TestBoards.party(context, CONTROLLER, 1, a, ((Entity) TestBoards.token(context, new BlockPos(3, 1, 5), null)).getUuid());
        follow(controller, alice);
        GameProfile profile = alice.getGameProfile();
        TestPlayers.remove(context, alice);
        ServerPlayerEntity back = TestPlayers.join(context, profile);
        TestPlayers.removeAtEnd(context, back);
        context.assertTrue(controller.getGlobalPos().equals(memberships.of(profile.getId())), "the running party is kept");

        // And /steveparty leave from there takes her out
        context.getWorld().getServer().getCommandManager().executeWithPrefix(back.getCommandSource(), "/steveparty leave");
        context.assertTrue(memberships.of(profile.getId()) == null, "left");
        context.assertTrue(!controller.getInterestedPlayers().contains(profile.getId()), "no longer followed");
        controller.stopParty(null);
        context.complete();
    }
}
