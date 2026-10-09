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
import fr.lordfinn.steveparty.service.AdvanceBackMoves;
import fr.lordfinn.steveparty.service.MistigriSentences;
import fr.lordfinn.steveparty.service.MistigriSentences.Sentence;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

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

    /** Starts the show for {@code token} on a Mistigri tile, the sentence forced; checks his actors. */
    private static MistigriSentences.Start start(TestContext context, PartyControllerEntity party, MobEntity token,
                                                 Sentence sentence, boolean[] done) {
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
    private static void show(TestContext context, Sentence sentence, ServerPlayerEntity[] players, Runnable before, Runnable check) {
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

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void theSmallFine(TestContext context) {
        ServerPlayerEntity player = player(context);
        show(context, Sentence.COINS_SMALL, new ServerPlayerEntity[]{player}, () -> give(player, Items.GOLD_INGOT, 30),
                () -> context.assertEquals(count(player, Items.GOLD_INGOT), 20, "10 coins taken"));
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void theBigFineNeverMoreThanHeld(TestContext context) {
        ServerPlayerEntity player = player(context);
        show(context, Sentence.COINS_BIG, new ServerPlayerEntity[]{player}, () -> give(player, Items.GOLD_INGOT, 12),
                () -> context.assertEquals(count(player, Items.GOLD_INGOT), 0, "all 12, no more"));
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void halfTheCoins(TestContext context) {
        ServerPlayerEntity player = player(context);
        show(context, Sentence.COINS_HALF, new ServerPlayerEntity[]{player}, () -> give(player, Items.GOLD_INGOT, 31),
                () -> context.assertEquals(count(player, Items.GOLD_INGOT), 16, "half of 31, rounded down: 15 taken"));
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void aStar(TestContext context) {
        ServerPlayerEntity player = player(context);
        show(context, Sentence.STAR, new ServerPlayerEntity[]{player}, () -> {
            give(player, Items.EMERALD, 2);
            give(player, Items.GOLD_INGOT, 10);
        }, () -> {
            context.assertEquals(count(player, Items.EMERALD), 1, "one star taken");
            context.assertEquals(count(player, Items.GOLD_INGOT), 10, "no coin");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void everyonePays(TestContext context) {
        ServerPlayerEntity first = player(context), second = player(context);
        show(context, Sentence.EVERYONE, new ServerPlayerEntity[]{first, second}, () -> {
            give(first, Items.GOLD_INGOT, 10);
            give(second, Items.GOLD_INGOT, 3);
        }, () -> {
            context.assertEquals(count(first, Items.GOLD_INGOT), 5, "the token's player pays 5");
            context.assertEquals(count(second, Items.GOLD_INGOT), 0, "the other pays what they hold");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void aCursedDie(TestContext context) {
        ServerPlayerEntity player = player(context);
        atEnd(context, () -> player.removeCommandTag(CursedRolls.TAG));
        show(context, Sentence.CURSED, new ServerPlayerEntity[]{player}, () -> {
        }, () -> context.assertTrue(CursedRolls.isCursed(player), "their next roll is cursed"));
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void theLenientOne(TestContext context) {
        ServerPlayerEntity player = player(context);
        show(context, Sentence.JOKE, new ServerPlayerEntity[]{player}, () -> give(player, Items.GOLD_INGOT, 30),
                () -> context.assertEquals(count(player, Items.GOLD_INGOT), 29, "one coin, out of pity"));
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
        context.assertTrue(MistigriSentences.start(context.getWorld(), tile.getPos(), token, party, cartridge, Sentence.BACK,
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
        start(context, party, token, Sentence.COINS_SMALL, done);
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
        context.assertTrue(start(context, party, tokens.getFirst(), Sentence.JOKE, done) == MistigriSentences.Start.NO_PLAYER, "no player");
        context.assertTrue(MistigriSentences.actor(tokens.getFirst()) == null && !done[0], "no Mistigri");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void theWeightsDecide(TestContext context) {
        ItemStack cartridge = new ItemStack(ModItems.MISTIGRI_CARTRIDGE);
        Random random = Random.create(42);
        for (Sentence sentence : Sentence.values()) {
            context.assertEquals(MistigriCartridgeItem.weight(cartridge, sentence), sentence.defaultWeight, sentence.id + " default weight");
        }
        for (Sentence sentence : Sentence.values()) MistigriCartridgeItem.setWeight(cartridge, sentence, 0);
        context.assertTrue(MistigriSentences.draw(cartridge, random) == null, "every weight 0: nothing drawn");
        MistigriCartridgeItem.setWeight(cartridge, Sentence.STAR, 9);
        for (int i = 0; i < 20; i++) context.assertTrue(MistigriSentences.draw(cartridge, random) == Sentence.STAR, "only the star");
        MistigriCartridgeItem.setWeight(cartridge, Sentence.JOKE, 9);
        int jokes = 0;
        for (int i = 0; i < 400; i++) if (MistigriSentences.draw(cartridge, random) == Sentence.JOKE) jokes++;
        context.assertTrue(jokes > 140 && jokes < 260, "two even weights: about half each (" + jokes + ")");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void cartridgeSettings(TestContext context) {
        ItemStack cartridge = new ItemStack(ModItems.MISTIGRI_CARTRIDGE);
        context.assertEquals(MistigriCartridgeItem.amount(cartridge, MistigriCartridgeItem.COINS_SMALL), 10, "small fine: 10");
        context.assertEquals(MistigriCartridgeItem.amount(cartridge, MistigriCartridgeItem.COINS_BIG), 20, "big fine: 20");
        context.assertEquals(MistigriCartridgeItem.amount(cartridge, MistigriCartridgeItem.EVERYONE), 5, "everyone: 5");
        context.assertEquals(MistigriCartridgeItem.amount(cartridge, MistigriCartridgeItem.BACK), 3, "back: 3");
        MistigriCartridgeItem.setAmount(cartridge, MistigriCartridgeItem.BACK, 50);
        context.assertEquals(MistigriCartridgeItem.amount(cartridge, MistigriCartridgeItem.BACK), MistigriCartridgeItem.MAX_BACK, "capped");
        BoardSpaceBlockEntity tile = tile(context, TILE, cartridge);
        context.assertEquals(TileFeedback.landingOf(tile), TileFeedback.Landing.MISTIGRI, "its landing");
        context.assertEquals(TileFeedback.tileColor(tile), MistigriCartridgeItem.COLOR, "witch plum");
        context.complete();
    }
}
