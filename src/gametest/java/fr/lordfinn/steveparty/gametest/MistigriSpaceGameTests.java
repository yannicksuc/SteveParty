package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.dice.CursedRolls;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriDieEntity;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.MistigriCartridgeItem;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.service.AdvanceBackMoves;
import fr.lordfinn.steveparty.service.MistigriSentences;
import fr.lordfinn.steveparty.components.MistigriOutcomes;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Kind;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Outcome;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Target;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyResources;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestAsserts.count;
import static fr.lordfinn.steveparty.gametest.kit.TestCleanup.atEnd;
import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * The Mistigri space: each sentence does what it says (never more than a player holds), the move back walks the token
 * back and holds the turn, the Mistigri and his die are board actors removed at the end (or when the party ends in the
 * middle), the draw follows the cartridge's weights, and the cartridge's settings.
 */
public class MistigriSpaceGameTests implements SteveGameTest {
    private static final String BATCH = "mistigri_space";
    private static final BlockPos TILE = new BlockPos(3, 1, 3);
    private static final int WHOLE = MistigriSentences.WHOLE + 20;

    /** How many of {@code item} the party's bank holds (its Party Controller's own inventory). */
    private static int bank(TestContext context, Item item) {
        PartyControllerEntity controller = context.getBlockEntity(DiceTestKit.CONTROLLER);
        return InventoryUtils.count(controller.getBankItems(), new ItemStack(item));
    }

    private static void give(ServerPlayerEntity player, Item item, int count) {
        InventoryUtils.giveOrDrop(player, new ItemStack(item), count);
    }

    /** A party of these players (one token each, the first one's turn), gold ingots as coins, emeralds as stars. */
    private static PartyControllerEntity party(TestContext context, List<MobEntity> tokens, ServerPlayerEntity... players) {
        for (int i = 0; i < players.length; i++) tokens.add(token(context, new BlockPos(1 + 2 * i, 1, 1), players[i].getUuid()));
        PartyControllerEntity controller = DiceTestKit.party(context, players[0].getUuid(), tokens.toArray(MobEntity[]::new));
        context.assertTrue(controller.setCurrency(PartyCurrency.COIN, new ItemStack(Items.GOLD_INGOT)), "coins: gold ingots");
        context.assertTrue(controller.setCurrency(PartyCurrency.STAR, new ItemStack(Items.EMERALD)), "stars: emeralds");
        return controller;
    }

    /** A sentence on the player who landed. */
    private static Outcome on(Kind kind, int amount, boolean percent) {
        return new Outcome(kind, Target.LANDED, amount, percent, 1);
    }

    /** Starts the show for {@code token} on a Mistigri tile, the sentence forced; checks his actors. */
    private static MistigriSentences.Start start(TestContext context, PartyControllerEntity party, MobEntity token,
                                                 Outcome sentence, boolean[] done) {
        ItemStack cartridge = new ItemStack(ModItems.MISTIGRI_CARTRIDGE);
        BoardSpaceBlockEntity tile = tile(context, TILE, cartridge);
        MistigriSentences.Start start = MistigriSentences.start(context.getWorld(), tile.getPos(), token, party, cartridge,
                sentence, () -> done[0] = true);
        if (start == MistigriSentences.Start.STARTED) {
            MistigriEntity actor = MistigriSentences.actor(token);
            context.assertTrue(actor != null && actor.isBoardActor() && actor.isInvulnerable() && !actor.shouldSave(),
                    "a board actor: invulnerable, never saved");
        }
        return start;
    }

    /** Runs a whole show with {@code sentence} on the first player, then {@code check} once it is over. */
    private static void show(TestContext context, Outcome sentence, ServerPlayerEntity[] players, Runnable before, Runnable check) {
        List<MobEntity> tokens = new ArrayList<>();
        PartyControllerEntity party = party(context, tokens, players);
        before.run();
        boolean[] done = {false};
        MobEntity token = tokens.getFirst();
        context.assertTrue(start(context, party, token, sentence, done) == MistigriSentences.Start.STARTED, "started");
        MistigriEntity actor = MistigriSentences.actor(token);
        when(context, () -> MistigriSentences.die(token) != null, WHOLE, "his die rolls", () -> {
            MistigriDieEntity die = MistigriSentences.die(token);
            context.assertTrue(die.isRolling() && !die.shouldSave(), "his die tumbles, never saved");
            when(context, () -> done[0], WHOLE, "the show ends", () -> {
                context.assertTrue(actor.isRemoved() && die.isRemoved(), "he and his die are gone");
                context.assertFalse(MistigriSentences.isRunning(token), "over");
                check.run();
                context.complete();
            });
        });
    }

    /**
     * During the spin he lies on his back juggling his small die low over his chest, the roulette's panel just over
     * them (never overlapping); at the stop he is back on his feet and the die has landed in front of him.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void heJugglesHisDieOnHisBack(TestContext context) {
        ServerPlayerEntity player = player(context);
        List<MobEntity> tokens = new ArrayList<>();
        PartyControllerEntity party = party(context, tokens, player);
        MobEntity token = tokens.getFirst();
        boolean[] done = {false};
        context.assertTrue(start(context, party, token, on(Kind.PITY, 2, false), done) == MistigriSentences.Start.STARTED, "started");
        MistigriEntity actor = MistigriSentences.actor(token);
        when(context, () -> MistigriSentences.die(token) != null, WHOLE, "his die appears", () -> {
            MistigriDieEntity die = MistigriSentences.die(token);
            context.assertEquals(actor.getPlayPose(), MistigriEntity.PlayPose.JUGGLE, "on his back, juggling");
            context.assertTrue(die.isRolling(), "it tumbles");
            context.assertTrue(die.getWidth() <= 0.75f, "a small die");
            double up = die.getY() - actor.getY();
            context.assertTrue(up > 0.9 && up < 1.6, "held low, over his chest: " + up);
            context.assertTrue(Math.hypot(die.getX() - actor.getX(), die.getZ() - actor.getZ()) < 0.8, "between his paws");
            var anchor = MistigriSentences.roulette(token).anchor();
            context.assertTrue(anchor != null, "the panel floats in the world");
            double panel = anchor.y - actor.getY();
            context.assertTrue(panel >= 2.4 && panel <= 3.0, "the panel just over him: " + panel);
            double[] top = {0};
            when(context, () -> {
                top[0] = Math.max(top[0], die.getY() + die.getHeight());
                return !die.isRolling();
            }, WHOLE, "his die lands", () -> {
                context.assertTrue(top[0] < anchor.y, "never up into the panel: " + top[0] + " < " + anchor.y);
                context.assertEquals(actor.getPlayPose(), MistigriEntity.PlayPose.NONE, "off his back");
                context.assertTrue(Math.abs(die.getY() - actor.getY()) < 0.1, "landed on the ground");
                when(context, () -> done[0], WHOLE, "the show ends", context::complete);
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void theSmallFine(TestContext context) {
        ServerPlayerEntity player = player(context);
        show(context, on(Kind.LOSE_COINS, 10, false), new ServerPlayerEntity[]{player}, () -> give(player, Items.GOLD_INGOT, 30),
                () -> {
                    context.assertEquals(count(player, Items.GOLD_INGOT), 20, "10 coins taken");
                    context.assertEquals(bank(context, Items.GOLD_INGOT), 10, "into the party's bank");
                });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void theBigFineNeverMoreThanHeld(TestContext context) {
        ServerPlayerEntity player = player(context);
        show(context, on(Kind.LOSE_COINS, 20, false), new ServerPlayerEntity[]{player}, () -> give(player, Items.GOLD_INGOT, 12),
                () -> context.assertEquals(count(player, Items.GOLD_INGOT), 0, "all 12, no more"));
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void halfTheCoins(TestContext context) {
        ServerPlayerEntity player = player(context);
        show(context, on(Kind.LOSE_COINS, 50, true), new ServerPlayerEntity[]{player}, () -> give(player, Items.GOLD_INGOT, 31),
                () -> context.assertEquals(count(player, Items.GOLD_INGOT), 16, "half of 31, rounded down: 15 taken"));
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void aStar(TestContext context) {
        ServerPlayerEntity player = player(context);
        show(context, on(Kind.LOSE_STARS, 1, false), new ServerPlayerEntity[]{player}, () -> {
            give(player, Items.EMERALD, 2);
            give(player, Items.GOLD_INGOT, 10);
        }, () -> {
            context.assertEquals(count(player, Items.EMERALD), 1, "one star taken");
            context.assertEquals(bank(context, Items.EMERALD), 1, "into the party's bank");
            context.assertEquals(count(player, Items.GOLD_INGOT), 10, "no coin");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void everyonePays(TestContext context) {
        ServerPlayerEntity first = player(context), second = player(context);
        show(context, new Outcome(Kind.LOSE_COINS, Target.ALL, 5, false, 1), new ServerPlayerEntity[]{first, second}, () -> {
            give(first, Items.GOLD_INGOT, 10);
            give(second, Items.GOLD_INGOT, 3);
        }, () -> {
            context.assertEquals(count(first, Items.GOLD_INGOT), 5, "the token's player pays 5");
            context.assertEquals(count(second, Items.GOLD_INGOT), 0, "the other pays what they hold");
            context.assertEquals(bank(context, Items.GOLD_INGOT), 8, "all of it into the party's bank");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void aCursedDie(TestContext context) {
        ServerPlayerEntity player = player(context);
        atEnd(context, () -> player.removeCommandTag(CursedRolls.TAG));
        show(context, on(Kind.CURSED_DIE, 0, false), new ServerPlayerEntity[]{player}, () -> {
        }, () -> context.assertTrue(CursedRolls.isCursed(player), "their next roll is cursed"));
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void theLenientOne(TestContext context) {
        ServerPlayerEntity player = player(context);
        show(context, on(Kind.PITY, 2, false), new ServerPlayerEntity[]{player}, () -> {
            give(player, Items.GOLD_INGOT, 30);
            PartyResources.deposit(context.getBlockEntity(DiceTestKit.CONTROLLER), new ItemStack(Items.GOLD_INGOT, 5));
        }, () -> {
            context.assertEquals(count(player, Items.GOLD_INGOT), 32, "two coins, out of pity");
            context.assertEquals(bank(context, Items.GOLD_INGOT), 3, "out of the party's bank");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void movingBackHoldsTheTurn(TestContext context) {
        ServerPlayerEntity player = player(context);
        // a path of four tiles, the Mistigri's last: the token stands on it
        path(context, 4, -1, null);
        BlockPos on = PATH.get(3);
        ItemStack cartridge = new ItemStack(ModItems.MISTIGRI_CARTRIDGE);
        BoardSpaceBlockEntity tile = tile(context, on, cartridge);
        MobEntity token = token(context, on, player.getUuid());
        PartyControllerEntity party = DiceTestKit.party(context, player.getUuid(), token);
        boolean[] done = {false};
        context.assertTrue(MistigriSentences.start(context.getWorld(), tile.getPos(), token, party, cartridge, on(Kind.MOVE_BACK, 3, false),
                () -> done[0] = true) == MistigriSentences.Start.STARTED, "started");
        when(context, () -> !MistigriSentences.isRunning(token), WHOLE, "the show ends", () -> {
            context.assertFalse(done[0], "the turn waits for the move back");
            context.assertTrue(AdvanceBackMoves.isExtraMove(token), "an extra move back");
            context.assertTrue(((TokenizedEntityInterface) token).steveparty$getNbSteps() == 3, "3 spaces back");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void partyEndingRemovesHim(TestContext context) {
        ServerPlayerEntity player = player(context);
        List<MobEntity> tokens = new ArrayList<>();
        PartyControllerEntity party = party(context, tokens, player);
        boolean[] done = {false};
        MobEntity token = tokens.getFirst();
        start(context, party, token, on(Kind.LOSE_COINS, 10, false), done);
        MistigriEntity actor = MistigriSentences.actor(token);
        context.waitAndRun(5, () -> {
            context.removeBlock(DiceTestKit.CONTROLLER);
            context.waitAndRun(2, () -> {
                context.assertTrue(done[0] && !MistigriSentences.isRunning(token), "stopped");
                context.assertTrue(actor.isRemoved(), "the Mistigri is gone");
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void nobodyToCurse(TestContext context) {
        ServerPlayerEntity player = player(context);
        List<MobEntity> tokens = new ArrayList<>();
        PartyControllerEntity party = party(context, tokens, player);
        ((TokenizedEntityInterface) tokens.getFirst()).steveparty$setTokenOwner((UUID) null);
        boolean[] done = {false};
        context.assertTrue(start(context, party, tokens.getFirst(), on(Kind.PITY, 2, false), done) == MistigriSentences.Start.NO_PLAYER, "no player");
        context.assertTrue(MistigriSentences.actor(tokens.getFirst()) == null && !done[0], "no Mistigri");
        context.complete();
    }

    /** A new cartridge's sentences: all on the roulette, and their colour. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void cartridgeSettings(TestContext context) {
        ItemStack cartridge = new ItemStack(ModItems.MISTIGRI_CARTRIDGE);
        MistigriOutcomes outcomes = MistigriOutcomes.of(cartridge);
        context.assertEquals(outcomes, MistigriOutcomes.DEFAULT, "the default sentences");
        context.assertEquals(outcomes.drawCount(), outcomes.size(), "all of them on the roulette");
        context.assertFalse(cartridge.contains(ModComponents.MISTIGRI_OUTCOMES), "the default is not stored");
        BoardSpaceBlockEntity tile = tile(context, TILE, cartridge);
        context.assertEquals(TileFeedback.landingOf(tile), TileFeedback.Landing.MISTIGRI, "its landing");
        context.assertEquals(TileFeedback.tileColor(tile), MistigriCartridgeItem.COLOR, "witch plum");
        context.complete();
    }
}
