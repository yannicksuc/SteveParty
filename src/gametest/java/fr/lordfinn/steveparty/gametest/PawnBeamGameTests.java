package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.StartRollsStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.entities.custom.DirectionDisplayEntity;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.TrichaudronCartridgeItem;
import fr.lordfinn.steveparty.payloads.custom.PawnBeamsPayload;
import fr.lordfinn.steveparty.service.PawnBeams;
import fr.lordfinn.steveparty.service.TrichaudronPrizes;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;

import java.util.List;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * The light beam over a pawn ({@link PawnBeams}): shown while its player has a choice to make (the turn order roll,
 * their turn's roll, a picker, a fork, the Trichaudron's heads...), from the moment it is asked until it is made, and
 * for a few seconds once it stops on a space; in the pawn's colour; none once the party is over.
 */
public class PawnBeamGameTests implements SteveGameTest {
    private static final String BATCH = "pawn_beams";
    private static final BlockPos TILE = new BlockPos(3, 1, 3), NEXT = new BlockPos(3, 1, 5), OTHER = new BlockPos(5, 1, 3);
    private static final BlockPos CHEST = new BlockPos(1, 1, 3);
    private static final int TO_CHOICE = TrichaudronPrizes.RISE_TICKS + TrichaudronPrizes.DIVE_TICKS + TrichaudronPrizes.EMERGE_TICKS + 20;

    private static boolean shown(TestContext context, Entity token) {
        return PawnBeams.isShown(context.getWorld(), token.getUuid());
    }

    /** Its player rolls for this turn (something that plays: 4 steps). */
    private static void roll(TestContext context, PartyControllerEntity party, ServerPlayerEntity player) {
        TokenTurnPartyStep turn = (TokenTurnPartyStep) party.getPartyData().getCurrentStep();
        DiceEntity dice = context.spawnEntity(ModEntities.DICE_ENTITY, new BlockPos(2, 2, 2));
        turn.onDiceRoll(dice, player.getUuid(), 4, party);
        dice.discard();
    }

    /**
     * A turn starts: the beam, in the pawn's colour, until its player rolls; a landing shows it for
     * {@link PawnBeams#LANDING_TICKS}; the party over, none.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = PawnBeams.LANDING_TICKS + 60, batchId = BATCH)
    public void turnStartUntilTheRollThenTheLanding(TestContext context) {
        ServerPlayerEntity player = player(context);
        tile(context, TILE, plain());
        MobEntity token = token(context, TILE, player.getUuid());
        ((TokenizedEntityInterface) token).steveparty$setTokenColor(0x33AA55);
        PartyControllerEntity party = party(context, player.getUuid(), token);

        context.assertTrue(shown(context, token), "its turn starts: the beam until the roll");
        List<PawnBeamsPayload.Beam> beams = PawnBeams.beams(context.getWorld());
        context.assertTrue(beams.size() == 1 && beams.getFirst().color() == 0x33AA55 && beams.getFirst().entityId() == token.getId(),
                "one beam, on the pawn, in its colour: " + beams);
        roll(context, party, player);
        context.assertFalse(shown(context, token), "rolled: no beam");

        PawnBeams.onLanded(token);
        context.assertTrue(shown(context, token), "landed: the beam");
        context.runAtTick(context.getTick() + PawnBeams.LANDING_TICKS / 2, () ->
                context.assertTrue(shown(context, token), "still a while after the landing"));
        context.runAtTick(context.getTick() + PawnBeams.LANDING_TICKS + 5, () -> {
            context.assertFalse(shown(context, token), "a few seconds later: gone");
            // The next turn waits for a roll again, then the party is stopped: none
            party.nextStep();
            context.assertTrue(shown(context, token), "its next turn: the beam");
            party.stopParty(null);
            context.assertTrue(PawnBeams.beams(context.getWorld()).isEmpty(), "the party over: no beam");
            context.complete();
        });
    }

    /** The turn order rolls: each pawn whose player has not rolled yet; one that has its roll, none. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void turnOrderRollsUntilEachHasRolled(TestContext context) {
        ServerPlayerEntity player = player(context);
        tile(context, TILE, plain());
        tile(context, OTHER, plain());
        MobEntity first = token(context, TILE, player.getUuid());
        MobEntity second = token(context, OTHER, player.getUuid());
        BlockPos at = new BlockPos(0, 1, 7);
        context.setBlockState(at.down(), Blocks.STONE);
        context.setBlockState(at, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity party = context.getBlockEntity(at);
        PartyData data = new PartyData();
        data.addToken(first.getUuid());
        data.addToken(second.getUuid());
        data.addStep(new PartyStep());
        StartRollsStep rolls = new StartRollsStep();
        data.addStep(rolls);
        data.addStep(new EndPartyStep(new java.util.ArrayList<>(List.of(first.getUuid(), second.getUuid()))));
        party.setPartyData(data);
        party.nextStep();
        party.nextStep();
        context.assertTrue(shown(context, first) && shown(context, second), "nobody rolled: both");
        rolls.rolls.put(first.getUuid(), 3);
        context.assertTrue(!shown(context, first) && shown(context, second), "the first has its roll: only the second");
        party.stopParty(null);
        context.removeBlock(at);
        context.complete();
    }

    /** A picker to answer (a die's, a power-up's...): the beam until it is answered. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void aPickerUntilItIsAnswered(TestContext context) {
        ServerPlayerEntity player = player(context);
        tile(context, TILE, plain());
        MobEntity token = token(context, TILE, player.getUuid());
        PartyControllerEntity party = party(context, player.getUuid(), token);
        roll(context, party, player);
        context.assertFalse(shown(context, token), "rolled: no beam");
        DicePrompts.Prompt prompt = DicePrompts.ask(player, Text.literal("?"), DicePrompts.Layout.LIST,
                List.of(new DicePrompts.Option(ItemStack.EMPTY, Text.literal("a")), new DicePrompts.Option(ItemStack.EMPTY, Text.literal("b"))),
                DicePrompts.TIMEOUT_TICKS, 0, index -> {
                });
        context.assertTrue(shown(context, token), "asked: the beam");
        DicePrompts.answer(player, prompt.id(), 1);
        context.assertFalse(shown(context, token), "answered: gone");
        context.complete();
    }

    /** A fork: the beam while its arrows wait for its player, none once a way is picked. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void aForkUntilAWayIsPicked(TestContext context) {
        ServerPlayerEntity player = player(context);
        tile(context, TILE, plain(), NEXT, OTHER);
        tile(context, NEXT, plain());
        tile(context, OTHER, plain());
        MobEntity token = token(context, TILE, player.getUuid());
        PartyControllerEntity party = party(context, player.getUuid(), token);
        roll(context, party, player);
        ((TokenizedEntityInterface) token).steveparty$setNbSteps(2);
        context.assertFalse(shown(context, token), "walking: no beam");
        BlockPos origin = context.getAbsolutePos(TILE);
        DirectionDisplayEntity left = new DirectionDisplayEntity(context.getWorld(),
                new BoardSpaceDestination(context.getAbsolutePos(NEXT), true), origin, player, token.getUuid());
        DirectionDisplayEntity right = new DirectionDisplayEntity(context.getWorld(),
                new BoardSpaceDestination(context.getAbsolutePos(OTHER), true), origin, player, token.getUuid());
        context.assertTrue(shown(context, token), "its arrows shown: the beam");
        left.discard();
        right.discard();
        context.assertFalse(shown(context, token), "a way picked: gone");
        ((TokenizedEntityInterface) token).steveparty$setNbSteps(0);
        context.complete();
    }

    /** The Trichaudron's heads held out: the beam right away, until a head is picked. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = TrichaudronPrizes.WHOLE + 40, batchId = BATCH)
    public void theTrichaudronsHeadsUntilOneIsPicked(TestContext context) {
        ServerPlayerEntity player = player(context);
        context.setBlockState(CHEST.down(), Blocks.STONE);
        context.setBlockState(CHEST, Blocks.CHEST);
        ChestBlockEntity chest = context.getBlockEntity(CHEST);
        chest.setStack(0, new ItemStack(Items.DIAMOND, 5));
        chest.setStack(1, new ItemStack(Items.EMERALD, 5));
        ItemStack cartridge = new ItemStack(ModItems.TRICHAUDRON_CARTRIDGE);
        TrichaudronCartridgeItem.setFilters(cartridge, List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.EMERALD)));
        CartridgeContainers.set(cartridge, List.of(GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(CHEST))));
        BoardSpaceBlockEntity tile = tile(context, TILE, cartridge);
        MobEntity token = token(context, TILE, player.getUuid());
        PartyControllerEntity party = party(context, player.getUuid(), token);
        roll(context, party, player);
        TrichaudronPrizes.Start start = TrichaudronPrizes.start(context.getWorld(), tile.getPos(), token, party, () -> {
        });
        context.assertTrue(start == TrichaudronPrizes.Start.STARTED, "the show starts: " + start);
        context.assertFalse(shown(context, token), "it rises: nothing to choose yet");
        when(context, () -> TrichaudronPrizes.phase(token) == TrichaudronPrizes.Phase.CHOOSE, TO_CHOICE, "its heads held out", () -> {
            context.assertTrue(shown(context, token), "its heads held out: the beam at once");
            TrichaudronPrizes.pick(token, TrichaudronPrizes.actor(token).shownHeads()[0]);
            context.assertFalse(shown(context, token), "a head picked: gone");
            TrichaudronPrizes.stop(token);
            context.complete();
        });
    }
}
