package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.service.TokenMovementService;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepFactory;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.Kind;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.Landing;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.Played;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ReplayBoardSpaceBehavior;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock.TILE_TYPE;

/**
 * Rejouer / Roll Again: landing on it during one's turn gives the same player another turn right away, then the turn
 * order goes on; the extra turn never gives another one.
 */
public class ReplayTileGameTests implements FabricGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(1, 1, 5);

    private static BoardSpaceBlockEntity placeTile(TestContext context, BlockPos pos, ItemStack cartridge) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.ADVANCED_TILE);
        BoardSpaceBlockEntity tile = context.getBlockEntity(pos);
        tile.setStack(0, cartridge);
        return tile;
    }

    private static PigEntity spawnToken(TestContext context, BlockPos pos, @Nullable UUID owner) {
        return TestBoards.token(context, pos.up(), owner);
    }

    private static List<Played> record(TestContext context) {
        List<Played> played = new ArrayList<>();
        Consumer<Played> listener = event -> {
            if (event.kind() == Kind.LAND) played.add(event);
        };
        TileFeedback.LISTENERS.add(listener);
        context.addFinalTask(() -> TileFeedback.LISTENERS.remove(listener));
        return played;
    }

    private static TokenTurnPartyStep turn(PartyData data) {
        return (TokenTurnPartyStep) data.getCurrentStep();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theCartridgeMakesAReplayTile(TestContext context) {
        BoardSpaceBlockEntity tile = placeTile(context, new BlockPos(2, 1, 2), new ItemStack(ModItems.REPLAY_CARTRIDGE));
        context.assertEquals(tile.getCachedState().get(TILE_TYPE), BoardSpaceType.TILE_REPLAY, "a replay tile");
        context.assertEquals(TileFeedback.landingOf(tile), Landing.REPLAY, "its landing");
        context.assertEquals(TileFeedback.tileColor(tile), ReplayBoardSpaceBehavior.COLOR, "a cyan face");
        context.assertEquals(new ItemStack(ModItems.REPLAY_CARTRIDGE).getMaxCount(), 64, "cartridges stack");
        context.complete();
    }

    /** The same player plays again right away, once: the replay turn gives no other one, then the order goes on. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void landingGivesTheSamePlayerOneMoreTurnThenTheOrderGoesOn(TestContext context) {
        List<Played> played = record(context);
        BlockPos pos = new BlockPos(2, 1, 2);
        BoardSpaceBlockEntity tile = placeTile(context, pos, new ItemStack(ModItems.REPLAY_CARTRIDGE));
        PigEntity token = spawnToken(context, pos, null);
        UUID a = token.getUuid(), b = UUID.randomUUID();
        PartyControllerEntity controller = TestBoards.party(context, CONTROLLER, 2, a, b);
        PartyData data = controller.getPartyData();
        context.assertEquals(data.getStepIndex(), 1, "turn of a");
        int steps = data.getSteps().size();

        // Lands on the Replay tile at the end of its move
        TokenizedEntityInterface tokenized = (TokenizedEntityInterface) token;
        tokenized.steveparty$setStatus(TokenStatus.IN_GAME); // its move is over: no more CAN_MOVE
        tile.onDestinationReached(token, controller);
        context.assertEquals(played.getLast().landing(), Landing.REPLAY, "the replay landing");
        context.assertEquals(data.getStepIndex(), 2, "the next step started");
        context.assertEquals(data.getSteps().size(), steps + 1, "one turn inserted");
        context.assertTrue(turn(data).isReplay() && a.equals(turn(data).getTokenUUID()), "a plays again");
        context.assertEquals(turn(data).getName(), "party_step_type.token_turn_replay", "shown as a replay in the steps HUD");
        context.assertTrue(TokenStatus.canMoveInGame(tokenized.steveparty$getStatus()), "the token may move again");
        context.assertTrue(data.getSteps().get(3) instanceof TokenTurnPartyStep next && b.equals(next.getTokenUUID()) && !next.isReplay(),
                "b plays next");

        // The replay move ends on the Replay tile again: no endless chain
        tile.onDestinationReached(token, controller);
        context.assertEquals(played.getLast().landing(), Landing.REPLAY_SPENT, "no further replay");
        context.assertEquals(data.getSteps().size(), steps + 1, "no other turn inserted");
        context.assertEquals(data.getStepIndex(), 3, "the turn order goes on");
        context.assertTrue(b.equals(turn(data).getTokenUUID()) && !turn(data).isReplay(), "turn of b");
        context.assertTrue(!TokenStatus.hasStatus(tokenized.steveparty$getStatus(), TokenStatus.CAN_MOVE), "a no longer moves");

        // Next round: a lands on it again during its normal turn, it plays again
        controller.nextStep();
        context.assertTrue(a.equals(turn(data).getTokenUUID()) && !turn(data).isReplay(), "second turn of a");
        tile.onDestinationReached(token, controller);
        context.assertTrue(turn(data).isReplay() && a.equals(turn(data).getTokenUUID()), "a replay again, a new turn");
        context.removeBlock(CONTROLLER);
        context.complete();
    }

    /** The replay move may end on any other special tile: it plays as usual, then the turn order goes on. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theReplayMoveMayEndOnAnotherSpecialTile(TestContext context) {
        List<Played> played = record(context);
        BlockPos replayPos = new BlockPos(2, 1, 2), bonusPos = new BlockPos(4, 1, 2), stopPos = new BlockPos(6, 1, 2);
        BoardSpaceBlockEntity replay = placeTile(context, replayPos, new ItemStack(ModItems.REPLAY_CARTRIDGE));
        ItemStack bonusCartridge = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
        bonusCartridge.set(ModComponents.INVENTORY_COMPONENT, new InventoryComponent(List.of(new ItemStack(Items.DIAMOND))));
        BoardSpaceBlockEntity bonus = placeTile(context, bonusPos, bonusCartridge);
        PigEntity token = spawnToken(context, replayPos, null);
        UUID a = token.getUuid(), b = UUID.randomUUID();
        PartyControllerEntity controller = TestBoards.party(context, CONTROLLER, 2, a, b);
        PartyData data = controller.getPartyData();

        replay.onDestinationReached(token, controller);
        context.assertTrue(turn(data).isReplay(), "replay turn");

        // The replay move goes over a bonus tile, then a stop tile ends it (forced arrival)
        token.refreshPositionAndAngles(context.getAbsolutePos(bonusPos).up().toCenterPos(), 0, 0);
        ((TokenizedEntityInterface) token).steveparty$setNbSteps(3);
        context.assertTrue(!TokenMovementService.endMoveIfForcedStop(token, bonus), "a bonus tile doesn't end the move");
        BoardSpaceBlockEntity stop = placeTile(context, stopPos, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP));
        token.refreshPositionAndAngles(context.getAbsolutePos(stopPos).up().toCenterPos(), 0, 0);
        context.assertTrue(TokenMovementService.endMoveIfForcedStop(token, stop), "the stop tile ends the replay move");
        context.assertTrue(turn(data).isReplay(), "still the replay turn");
        stop.onDestinationReached(token, controller);
        context.assertEquals(played.getLast().landing(), Landing.STOP, "the stop tile's own landing");
        context.assertEquals(data.getStepIndex(), 3, "then the turn order goes on");
        context.assertTrue(b.equals(turn(data).getTokenUUID()), "turn of b");
        context.removeBlock(CONTROLLER);
        context.complete();
    }

    /** Outside the token's own turn a Replay tile gives nothing (another token's turn, no party). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void noReplayOutsideTheTokensTurn(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        placeTile(context, pos, new ItemStack(ModItems.REPLAY_CARTRIDGE));
        PigEntity token = spawnToken(context, pos, null);
        UUID other = UUID.randomUUID();
        PartyControllerEntity controller = TestBoards.party(context, CONTROLLER, 2, other, token.getUuid());
        int steps = controller.getPartyData().getSteps().size();
        context.assertEquals(ReplayBoardSpaceBehavior.grant(controller, token), Landing.DEFAULT, "not its turn");
        context.assertEquals(ReplayBoardSpaceBehavior.grant(null, token), Landing.DEFAULT, "no party");
        context.assertEquals(controller.getPartyData().getSteps().size(), steps, "no turn inserted");
        context.removeBlock(CONTROLLER);
        context.complete();
    }

    /** The Power-up die spent for the turn comes back for the replay (a forged die included); the replay flag is saved. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theSpentDieComesBackAndTheReplayIsSaved(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        context.addFinalTask(() -> TestPlayers.remove(context, player));
        BlockPos pos = new BlockPos(2, 1, 2);
        BoardSpaceBlockEntity tile = placeTile(context, pos, new ItemStack(ModItems.REPLAY_CARTRIDGE));
        PigEntity token = spawnToken(context, pos, player.getUuid());
        PartyControllerEntity controller = TestBoards.party(context, CONTROLLER, 2, token.getUuid(), UUID.randomUUID());
        PartyData data = controller.getPartyData();

        // The player threw a (named, like a forged one) Power-up die: it was spent
        ItemStack die = fr.lordfinn.steveparty.dice.DiceModules.set(new ItemStack(ModItems.DEFAULT_DICE),
                java.util.Map.of(fr.lordfinn.steveparty.dice.DiceModules.POWER_UP, 1));
        die.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal("Lucky"));
        DiceEntity dice = ModEntities.DICE_ENTITY.create(context.getWorld());
        context.assertTrue(dice != null, "dice created");
        dice.setItemReference(die.copy());
        turn(data).onDiceRoll(dice, player.getUuid(), 4, controller);
        dice.discard();

        tile.onDestinationReached(token, controller);
        context.assertTrue(turn(data).isReplay(), "replay turn");
        ItemStack back = player.getInventory().main.stream().filter(stack -> stack.isOf(ModItems.DEFAULT_DICE)).findFirst().orElse(ItemStack.EMPTY);
        context.assertTrue(ItemStack.areItemsAndComponentsEqual(back, die), "the same die came back, got " + back);

        PartyStep reloaded = PartyStepFactory.get(turn(data).toNbt());
        context.assertTrue(reloaded instanceof TokenTurnPartyStep step && step.isReplay(), "the replay turn is saved");
        context.assertTrue(PartyStepFactory.get(data.getSteps().get(1).toNbt()) instanceof TokenTurnPartyStep step && !step.isReplay(),
                "a normal turn stays normal");
        context.removeBlock(CONTROLLER);
        context.complete();
    }
}
