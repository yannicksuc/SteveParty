package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.FrousseuxCartridgeItem;
import fr.lordfinn.steveparty.service.BoardActors;
import fr.lordfinn.steveparty.service.FrousseuxThefts;
import fr.lordfinn.steveparty.service.FrousseuxThefts.Phase;
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

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestAsserts.count;
import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * The Frousseux space: coins stolen at once then given to the sender after the defence, each blow of the victim
 * gives one back, never more than the victim holds, stars stolen with no defence, nothing without another player, the
 * loot settled when the party ends in the middle; and the cartridge's settings.
 */
public class FrousseuxSpaceGameTests implements SteveGameTest {
    private static final String BATCH = "frousseux_space";
    private static final BlockPos TILE = new BlockPos(3, 1, 3);
    /** Appearing, flying, the defence, flying back, giving: well under this. */
    private static final int WHOLE_THEFT = 500;

    private static void give(ServerPlayerEntity player, Item item, int count) {
        InventoryUtils.giveOrDrop(player, new ItemStack(item), count);
    }

    /** A party of the given players (one token each, the first one's turn), gold ingots as coins, emeralds as stars. */
    private static PartyControllerEntity party(TestContext context, List<MobEntity> tokens, ServerPlayerEntity... players) {
        for (int i = 0; i < players.length; i++) tokens.add(token(context, new BlockPos(1 + 2 * i, 1, 1), players[i].getUuid()));
        PartyControllerEntity controller = DiceTestKit.party(context, players[0].getUuid(), tokens.toArray(MobEntity[]::new));
        context.assertTrue(controller.setCurrency(PartyCurrency.COIN, new ItemStack(Items.GOLD_INGOT)), "coins: gold ingots");
        context.assertTrue(controller.setCurrency(PartyCurrency.STAR, new ItemStack(Items.EMERALD)), "stars: emeralds");
        return controller;
    }

    /** Starts a theft by {@code token} and answers the picker with the first candidate. */
    private static FrousseuxThefts.Start start(TestContext context, PartyControllerEntity party, MobEntity token,
                                               ServerPlayerEntity sender, boolean stars, int amount, boolean[] done) {
        BoardSpaceBlockEntity tile = tile(context, TILE, new ItemStack(ModItems.FROUSSEUX_CARTRIDGE));
        FrousseuxThefts.Start start = FrousseuxThefts.start(context.getWorld(), tile.getPos(), token, party, stars, amount,
                () -> done[0] = true);
        DicePrompts.Prompt prompt = DicePrompts.pending(sender);
        if (start == FrousseuxThefts.Start.STARTED) {
            context.assertTrue(prompt != null, "the sender picks the victim");
            context.assertTrue(DicePrompts.answer(sender, prompt.id(), 0), "picked");
            FrousseuxEntity actor = FrousseuxThefts.actor(token);
            context.assertTrue(actor != null && actor.isBoardActor() && actor.isInvulnerable() && !actor.shouldSave(),
                    "a board actor: invulnerable, never saved");
        }
        return start;
    }

    private static void hit(FrousseuxEntity actor, ServerPlayerEntity by) {
        actor.damage(by.getDamageSources().playerAttack(by), 4f);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE_THEFT + 20, batchId = BATCH)
    public void stealsCoinsAtOnceAndBringsThemBack(TestContext context) {
        ServerPlayerEntity thief = player(context), victim = player(context);
        List<MobEntity> tokens = new ArrayList<>();
        PartyControllerEntity party = party(context, tokens, thief, victim);
        give(victim, Items.GOLD_INGOT, 30);
        boolean[] done = {false};
        context.assertTrue(start(context, party, tokens.getFirst(), thief, false, 15, done) == FrousseuxThefts.Start.STARTED, "started");
        when(context, () -> FrousseuxThefts.phase(tokens.getFirst()) == Phase.DEFENCE, WHOLE_THEFT, "the Frousseux reaches the victim", () -> {
            context.assertEquals(count(victim, Items.GOLD_INGOT), 15, "stolen right away");
            context.assertEquals(FrousseuxThefts.carried(tokens.getFirst()), 15, "it carries them");
            context.assertEquals(count(thief, Items.GOLD_INGOT), 0, "not given yet");
            when(context, () -> done[0], WHOLE_THEFT, "the theft ends", () -> {
                context.assertEquals(count(thief, Items.GOLD_INGOT), 15, "the sender gets them");
                context.assertEquals(count(victim, Items.GOLD_INGOT), 15, "the victim lost them");
                context.assertTrue(!FrousseuxThefts.isRunning(tokens.getFirst()), "over");
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE_THEFT + 20, batchId = BATCH)
    public void eachBlowGivesOneCoinBack(TestContext context) {
        ServerPlayerEntity thief = player(context), victim = player(context);
        List<MobEntity> tokens = new ArrayList<>();
        PartyControllerEntity party = party(context, tokens, thief, victim);
        give(victim, Items.GOLD_INGOT, 30);
        boolean[] done = {false};
        start(context, party, tokens.getFirst(), thief, false, 15, done);
        MobEntity token = tokens.getFirst();
        context.assertFalse(FrousseuxThefts.actor(token).canHit(), "a hologram until its defence: not aimed at");
        when(context, () -> FrousseuxThefts.phase(token) == Phase.DEFENCE, WHOLE_THEFT, "the defence", () -> {
            FrousseuxEntity actor = FrousseuxThefts.actor(token);
            float health = actor.getHealth();
            context.assertTrue(actor.canHit() && BoardActors.isTouchable(actor), "its defence: the victim can aim at it");
            context.waitAndRun(FrousseuxThefts.HIT_COOLDOWN, () -> {
                hit(actor, thief); // not the victim: nothing
                // a real blow, as the attack packet does it
                victim.attack(actor);
                hit(actor, victim); // too soon: nothing
                context.assertEquals(FrousseuxThefts.carried(token), 14, "one blow, one coin back");
                context.assertEquals(count(victim, Items.GOLD_INGOT), 16, "back to the victim");
                context.waitAndRun(FrousseuxThefts.HIT_COOLDOWN, () -> {
                    hit(actor, victim);
                    context.waitAndRun(FrousseuxThefts.HIT_COOLDOWN, () -> {
                        hit(actor, victim);
                        context.assertEquals(FrousseuxThefts.carried(token), 12, "three blows");
                        when(context, () -> FrousseuxThefts.phase(token) != Phase.DEFENCE, WHOLE_THEFT, "the defence ends",
                                () -> context.assertFalse(actor.canHit(), "its defence over: a hologram again"));
                        context.assertTrue(actor.isAlive() && actor.getHealth() == health, "the blows never hurt it");
                        when(context, () -> done[0], WHOLE_THEFT, "the theft ends", () -> {
                            context.assertEquals(count(thief, Items.GOLD_INGOT), 12, "the sender gets what is left");
                            context.assertEquals(count(victim, Items.GOLD_INGOT), 18, "the victim kept three more");
                            context.complete();
                        });
                    });
                });
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE_THEFT + 20, batchId = BATCH)
    public void stealsNoMoreThanTheVictimHolds(TestContext context) {
        ServerPlayerEntity thief = player(context), victim = player(context);
        List<MobEntity> tokens = new ArrayList<>();
        PartyControllerEntity party = party(context, tokens, thief, victim);
        give(victim, Items.GOLD_INGOT, 4);
        give(victim, Items.GOLD_NUGGET, 20); // not the party's coin
        boolean[] done = {false};
        start(context, party, tokens.getFirst(), thief, false, 15, done);
        when(context, () -> done[0], WHOLE_THEFT, "the theft ends", () -> {
            context.assertEquals(count(thief, Items.GOLD_INGOT), 4, "only the 4 held");
            context.assertEquals(count(victim, Items.GOLD_INGOT), 0, "none left");
            context.assertEquals(count(victim, Items.GOLD_NUGGET), 20, "other items untouched");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE_THEFT + 20, batchId = BATCH)
    public void starsAreStolenWithNoDefence(TestContext context) {
        ServerPlayerEntity thief = player(context), victim = player(context);
        List<MobEntity> tokens = new ArrayList<>();
        PartyControllerEntity party = party(context, tokens, thief, victim);
        give(victim, Items.EMERALD, 2);
        give(victim, Items.GOLD_INGOT, 30);
        boolean[] done = {false};
        MobEntity token = tokens.getFirst();
        start(context, party, token, thief, true, 1, done);
        when(context, () -> FrousseuxThefts.phase(token) == Phase.SHOW, WHOLE_THEFT, "the star stolen", () -> {
            context.assertEquals(count(victim, Items.EMERALD), 1, "one star taken");
            hit(FrousseuxThefts.actor(token), victim);
            context.assertEquals(FrousseuxThefts.carried(token), 1, "no defence for stars");
            when(context, () -> done[0], WHOLE_THEFT, "the theft ends", () -> {
                context.assertEquals(count(thief, Items.EMERALD), 1, "the sender gets the star");
                context.assertEquals(count(victim, Items.GOLD_INGOT), 30, "no coin taken");
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void nobodyToRob(TestContext context) {
        ServerPlayerEntity thief = player(context);
        List<MobEntity> tokens = new ArrayList<>();
        PartyControllerEntity party = party(context, tokens, thief);
        boolean[] done = {false};
        context.assertTrue(start(context, party, tokens.getFirst(), thief, false, 15, done) == FrousseuxThefts.Start.NOBODY, "nobody");
        context.assertTrue(!FrousseuxThefts.isRunning(tokens.getFirst()) && FrousseuxThefts.actor(tokens.getFirst()) == null, "no Frousseux");
        context.assertTrue(DicePrompts.pending(thief) == null, "nothing asked");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE_THEFT + 20, batchId = BATCH)
    public void partyEndingSettlesTheLoot(TestContext context) {
        ServerPlayerEntity thief = player(context), victim = player(context);
        List<MobEntity> tokens = new ArrayList<>();
        PartyControllerEntity party = party(context, tokens, thief, victim);
        give(victim, Items.GOLD_INGOT, 30);
        boolean[] done = {false};
        MobEntity token = tokens.getFirst();
        start(context, party, token, thief, false, 10, done);
        when(context, () -> FrousseuxThefts.phase(token) == Phase.DEFENCE, WHOLE_THEFT, "the defence", () -> {
            FrousseuxEntity actor = FrousseuxThefts.actor(token);
            context.removeBlock(DiceTestKit.CONTROLLER);
            context.waitAndRun(2, () -> {
                context.assertTrue(done[0] && !FrousseuxThefts.isRunning(token), "stopped");
                context.assertTrue(actor.isRemoved(), "the Frousseux is gone");
                context.assertEquals(count(thief, Items.GOLD_INGOT), 10, "the loot settled to the sender");
                context.assertEquals(count(victim, Items.GOLD_INGOT), 20, "nothing lost");
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE_THEFT + 20, batchId = BATCH)
    public void victimLeavingEndsTheDefence(TestContext context) {
        ServerPlayerEntity thief = player(context), victim = player(context);
        List<MobEntity> tokens = new ArrayList<>();
        PartyControllerEntity party = party(context, tokens, thief, victim);
        give(victim, Items.GOLD_INGOT, 30);
        boolean[] done = {false};
        MobEntity token = tokens.getFirst();
        start(context, party, token, thief, false, 10, done);
        when(context, () -> FrousseuxThefts.phase(token) == Phase.DEFENCE, WHOLE_THEFT, "the defence", () -> {
            FrousseuxEntity actor = FrousseuxThefts.actor(token);
            TestPlayers.remove(context, victim);
            when(context, () -> done[0], WHOLE_THEFT, "the theft ends", () -> {
                context.assertTrue(actor.isRemoved(), "the Frousseux is gone");
                context.assertEquals(count(thief, Items.GOLD_INGOT), 10, "the sender still gets the loot");
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void cartridgeSettings(TestContext context) {
        ItemStack cartridge = new ItemStack(ModItems.FROUSSEUX_CARTRIDGE);
        context.assertTrue(!FrousseuxCartridgeItem.stealsStars(cartridge), "coins by default");
        context.assertEquals(FrousseuxCartridgeItem.amount(cartridge), 15, "15 coins by default");
        cartridge.set(ModComponents.FROUSSEUX_COINS, 99);
        context.assertEquals(FrousseuxCartridgeItem.amount(cartridge), 99, "up to 99 coins");
        cartridge.set(ModComponents.FROUSSEUX_STARS, true);
        context.assertEquals(FrousseuxCartridgeItem.amount(cartridge), 1, "1 star by default");
        BoardSpaceBlockEntity tile = tile(context, TILE, cartridge);
        context.assertEquals(TileFeedback.landingOf(tile), TileFeedback.Landing.FROUSSEUX, "its landing");
        context.assertEquals(TileFeedback.tileColor(tile), FrousseuxCartridgeItem.COLOR, "night indigo");
        context.complete();
    }
}
