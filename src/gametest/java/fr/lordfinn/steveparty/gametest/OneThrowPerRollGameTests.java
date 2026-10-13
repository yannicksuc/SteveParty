package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.StartRollsStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.dice.DiceRollSequence;
import fr.lordfinn.steveparty.dice.OneThrowPerRoll;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.CONTROLLER;
import static fr.lordfinn.steveparty.gametest.DiceTestKit.PATH;
import static fr.lordfinn.steveparty.gametest.DiceTestKit.die;
import static fr.lordfinn.steveparty.gametest.DiceTestKit.with;

/**
 * One throw per roll ({@link OneThrowPerRoll}): in a party, the first die a player throws is their roll; another throw
 * is refused (nothing thrown, nothing spent) while it rolls and once it counts, until a new roll opens (blank sides, a
 * replay, the next turn, the turn order rolled again). A Reroll module rolls again inside the same throw. Outside a
 * party, nothing changes.
 * <p>
 * These tests clean up by hand (no final task: see TestCleanup).
 */
public class OneThrowPerRollGameTests implements SteveGameTest {
    private static final String BATCH = "one_throw_per_roll";

    /** What a test made: undone by {@link #cleanUp}. */
    private record Scene(List<ServerPlayerEntity> players, List<MobEntity> tokens) {
        Scene() {
            this(new ArrayList<>(), new ArrayList<>());
        }
    }

    private static ServerPlayerEntity player(TestContext context, Scene scene) {
        @SuppressWarnings("removal")
        ServerPlayerEntity player = TestPlayers.mock(context);
        player.changeGameMode(GameMode.SURVIVAL);
        scene.players().add(player);
        return player;
    }

    private static PigEntity token(TestContext context, Scene scene, BlockPos on, UUID owner) {
        context.setBlockState(on.down(), Blocks.STONE);
        context.setBlockState(on, ModBlocks.TILE);
        PigEntity pig = context.spawnMob(EntityType.PIG, on);
        TokenizedEntityInterface token = (TokenizedEntityInterface) pig;
        token.steveparty$setTokenized(true);
        token.steveparty$setTokenOwner(owner);
        token.steveparty$setStatus(TokenStatus.IN_GAME);
        scene.tokens().add(pig);
        return pig;
    }

    /** A running party of these steps, at the first one after the start step. */
    private static PartyControllerEntity party(TestContext context, List<MobEntity> tokens, PartyStep... steps) {
        context.setBlockState(CONTROLLER.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        PartyData data = new PartyData();
        for (MobEntity token : tokens) data.addToken(token.getUuid());
        data.addStep(new PartyStep());
        for (PartyStep step : steps) data.addStep(step);
        data.addStep(new PartyStep());
        controller.setPartyData(data);
        controller.nextStep();
        controller.nextStep();
        context.assertEquals(data.getStepIndex(), 1, "the first step played");
        return controller;
    }

    /** The dice {@code player} threw that are still there. */
    private static List<DiceEntity> diceOf(TestContext context, ServerPlayerEntity player) {
        return context.getWorld().getEntitiesByClass(DiceEntity.class, player.getBoundingBox().expand(32),
                dice -> !dice.isRemoved() && dice.getOwner().map(player.getUuid()::equals).orElse(false));
    }

    /** {@code player} throws the die in their main hand: the lead of the throw, null if nothing flew. */
    private static DiceEntity throwHeld(TestContext context, ServerPlayerEntity player) {
        List<DiceEntity> before = diceOf(context, player);
        ItemStack held = player.getMainHandStack();
        held.use(context.getWorld(), player, Hand.MAIN_HAND);
        List<DiceEntity> thrown = new ArrayList<>(diceOf(context, player));
        thrown.removeAll(before);
        return thrown.stream().filter(dice -> dice.lead() == dice).findFirst().orElse(null);
    }

    /** The roll of {@code lead} is announced to the party's step (the die then goes back to its roller). */
    private static void lands(PartyControllerEntity controller, DiceEntity lead, UUID roller, int steps) {
        lead.group().forEach(DiceEntity::discard);
        controller.getPartyData().getCurrentStep().onDiceRoll(lead, roller, steps, controller);
    }

    private static void cleanUp(TestContext context, Scene scene) {
        for (ServerPlayerEntity player : scene.players()) diceOf(context, player).forEach(DiceEntity::discard);
        for (MobEntity token : scene.tokens()) {
            ((TokenizedEntityInterface) token).steveparty$setTokenized(false);
            token.discard();
        }
        if (context.getBlockState(CONTROLLER).isOf(ModBlocks.PARTY_CONTROLLER)) context.removeBlock(CONTROLLER);
        for (ServerPlayerEntity player : scene.players()) DicePrompts.cancel(DicePrompts.pending(player));
        TestPlayers.removeIfOnline(context, scene.players().toArray(ServerPlayerEntity[]::new));
    }

    /**
     * The turn's roll: thrown once; another throw refused while it rolls and once it counts, the die kept in hand;
     * blank sides open the roll again.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void aSecondThrowOfTheTurnIsRefused(TestContext context) {
        Scene scene = new Scene();
        try {
            ServerPlayerEntity player = player(context, scene);
            PigEntity pig = token(context, scene, PATH.get(0), player.getUuid());
            PartyControllerEntity controller = party(context, List.of(pig),
                    new TokenTurnPartyStep(pig.getUuid(), player.getUuid()), new TokenTurnPartyStep(pig.getUuid(), player.getUuid()));
            ItemStack dice = new ItemStack(ModItems.DEFAULT_DICE, 3);
            player.setStackInHand(Hand.MAIN_HAND, dice);
            DiceEntity first = throwHeld(context, player);
            context.assertTrue(first != null, "the first throw is the roll");
            context.assertEquals(dice.getCount(), 2, "one die thrown");
            context.assertTrue(throwHeld(context, player) == null, "while it rolls: another throw is refused");
            context.assertEquals(dice.getCount(), 2, "the refused die stays in hand");

            lands(controller, first, player.getUuid(), 0);
            DiceEntity again = throwHeld(context, player);
            context.assertTrue(again != null, "blank sides: the roll is open again");

            lands(controller, again, player.getUuid(), 3);
            context.assertTrue(throwHeld(context, player) == null, "the roll counts: another throw is refused");
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.TRIPLE_DICE));
            context.assertTrue(throwHeld(context, player) == null, "whatever the die");
            context.assertEquals(player.getMainHandStack().getCount(), 1, "nothing spent");
        } finally {
            cleanUp(context, scene);
        }
        context.complete();
    }

    /** A Double / Triple Dice is one throw: its dice fly together, then the roll is in. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void aTripleDiceIsOneThrow(TestContext context) {
        Scene scene = new Scene();
        try {
            ServerPlayerEntity player = player(context, scene);
            PigEntity pig = token(context, scene, PATH.get(0), player.getUuid());
            party(context, List.of(pig), new TokenTurnPartyStep(pig.getUuid(), player.getUuid()));
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.TRIPLE_DICE, 2));
            DiceEntity lead = throwHeld(context, player);
            context.assertTrue(lead != null && lead.group().size() == 3, "three dice in one throw");
            context.assertTrue(throwHeld(context, player) == null, "no second throw");
            context.assertEquals(player.getMainHandStack().getCount(), 1, "nothing more spent");
        } finally {
            cleanUp(context, scene);
        }
        context.complete();
    }

    /** A Reroll die rolls again inside its throw: the module's reroll is played, a second throw is still refused. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void theRerollModuleKeepsItsRolls(TestContext context) {
        Scene scene = new Scene();
        try {
            ServerPlayerEntity player = player(context, scene);
            PigEntity pig = token(context, scene, PATH.get(0), player.getUuid());
            party(context, List.of(pig), new TokenTurnPartyStep(pig.getUuid(), player.getUuid()));
            player.setStackInHand(Hand.MAIN_HAND, with(die("dice_face_4"), DiceModules.REROLL, 2).copyWithCount(2));
            DiceEntity dice = throwHeld(context, player);
            context.assertTrue(dice != null, "thrown");
            dice.age = DiceEntity.THROW_GRACE_TICKS;
            dice.damage(context.getWorld().getDamageSources().playerAttack(player), 1F);
            DiceRollSequence sequence = dice.sequence();
            context.assertEquals(sequence.phase(), DiceRollSequence.Phase.DECIDING, "keep or roll again?");
            context.assertTrue(throwHeld(context, player) == null, "deciding: no second throw");
            DicePrompts.answer(player, DicePrompts.pending(player).id(), 1);
            context.assertTrue(dice.isRolling() && sequence.rerollsLeft() == 1, "the reroll is played");
            context.assertTrue(throwHeld(context, player) == null, "rerolling: still no second throw");
        } finally {
            cleanUp(context, scene);
        }
        context.complete();
    }

    /** The next turn, and a replay of the turn, are new rolls. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void aReplayAndTheNextTurnRollAgain(TestContext context) {
        Scene scene = new Scene();
        try {
            ServerPlayerEntity player = player(context, scene);
            PigEntity pig = token(context, scene, PATH.get(0), player.getUuid());
            TokenTurnPartyStep turn = new TokenTurnPartyStep(pig.getUuid(), player.getUuid());
            PartyControllerEntity controller = party(context, List.of(pig), turn, new TokenTurnPartyStep(pig.getUuid(), player.getUuid()));
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.DEFAULT_DICE, 8));
            lands(controller, throwHeld(context, player), player.getUuid(), 2);
            context.assertTrue(throwHeld(context, player) == null, "rolled");

            context.assertTrue(turn.replay(controller), "the Step Controller replays the turn");
            controller.nextStep();
            context.assertTrue(controller.getPartyData().getCurrentStep() instanceof TokenTurnPartyStep extra && extra.isReplay(), "the replay");
            DiceEntity replayed = throwHeld(context, player);
            context.assertTrue(replayed != null, "the replay is a new roll");
            lands(controller, replayed, player.getUuid(), 2);
            context.assertTrue(throwHeld(context, player) == null, "the replay rolled");

            controller.nextStep();
            context.assertTrue(controller.getPartyData().getCurrentStep() instanceof TokenTurnPartyStep next && !next.isReplay(), "the next turn");
            context.assertTrue(throwHeld(context, player) != null, "the next turn is a new roll");
        } finally {
            cleanUp(context, scene);
        }
        context.complete();
    }

    /** The turn order: each player throws once; the others still throw theirs; rolled again, everyone throws again. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void theTurnOrderRollIsOneThrowToo(TestContext context) {
        Scene scene = new Scene();
        try {
            ServerPlayerEntity alice = player(context, scene), bob = player(context, scene);
            PigEntity a = token(context, scene, PATH.get(0), alice.getUuid());
            PigEntity b = token(context, scene, PATH.get(2), bob.getUuid());
            StartRollsStep rolls = new StartRollsStep();
            PartyControllerEntity controller = party(context, List.of(a, b), rolls);
            alice.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.DEFAULT_DICE, 4));
            bob.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.DEFAULT_DICE, 4));

            DiceEntity first = throwHeld(context, alice);
            context.assertTrue(first != null, "Alice throws");
            context.assertTrue(throwHeld(context, alice) == null, "while it rolls: refused");
            lands(controller, first, alice.getUuid(), 5);
            context.assertTrue(rolls.rolls.containsKey(a.getUuid()), "Alice's roll is in");
            context.assertTrue(throwHeld(context, alice) == null, "rolled: refused");
            context.assertEquals(alice.getMainHandStack().getCount(), 3, "nothing more spent");
            context.assertTrue(throwHeld(context, bob) != null, "Bob still throws his");

            context.assertTrue(rolls.rollAgain(controller), "the Step Controller rolls again");
            context.assertTrue(throwHeld(context, alice) != null, "everyone throws again");
        } finally {
            cleanUp(context, scene);
        }
        context.complete();
    }

    /** Outside a party, a die is thrown as often as wanted. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void outsideAPartyNothingChanges(TestContext context) {
        Scene scene = new Scene();
        try {
            ServerPlayerEntity player = player(context, scene);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.DEFAULT_DICE, 3));
            context.assertTrue(throwHeld(context, player) != null && throwHeld(context, player) != null, "thrown twice");
        } finally {
            cleanUp(context, scene);
        }
        context.complete();
    }
}
