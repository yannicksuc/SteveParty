package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.powerups.effects.ThiefBellEffect;
import fr.lordfinn.steveparty.powerups.effects.ThiefBellEffect.Outcome;
import fr.lordfinn.steveparty.powerups.effects.ThiefBellEffect.Protection;
import fr.lordfinn.steveparty.powerups.effects.ThiefBellEffect.Result;
import fr.lordfinn.steveparty.powerups.effects.ThiefBellEffect.Variant;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
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

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;

/**
 * The Thief Bell power-up: coins stolen at random (5 to 15, no more than the target holds), the Golden one steals a
 * star, the default target is the richest player, the Padlock hook cancels the theft.
 */
public class ThiefBellGameTests implements FabricGameTest {
    private static final String BATCH = "thief_bell";

    private static int count(ServerPlayerEntity player, Item item) {
        return InventoryUtils.count(player.getInventory(), new ItemStack(item));
    }

    /** A party of the given players (one token each, in this order), counting gold ingots as coins, emeralds as stars. */
    private static PartyControllerEntity party(TestContext context, ServerPlayerEntity... players) {
        List<MobEntity> tokens = new ArrayList<>();
        for (int i = 0; i < players.length; i++) tokens.add(token(context, new BlockPos(1 + 2 * i, 1, 1), players[i].getUuid()));
        PartyControllerEntity controller = DiceTestKit.party(context, players[0].getUuid(), tokens.toArray(MobEntity[]::new));
        context.assertTrue(controller.setCurrency(PartyCurrency.COIN, new ItemStack(Items.GOLD_INGOT)), "coins: gold ingots");
        context.assertTrue(controller.setCurrency(PartyCurrency.STAR, new ItemStack(Items.EMERALD)), "stars: emeralds");
        return controller;
    }

    private static void give(ServerPlayerEntity player, Item item, int count) {
        InventoryUtils.giveOrDrop(player, new ItemStack(item), count);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void stealsBetweenFiveAndFifteenCoins(TestContext context) {
        ServerPlayerEntity thief = player(context), target = player(context);
        PartyControllerEntity party = party(context, thief, target);
        give(target, Items.GOLD_INGOT, 30);
        int expected = Random.create(7).nextBetween(ThiefBellEffect.MIN_COINS, ThiefBellEffect.MAX_COINS);
        Result result = ThiefBellEffect.steal(party, thief, target, Variant.THIEF, Random.create(7), Protection.NONE);
        context.assertTrue(result.outcome() == Outcome.STOLEN, "stolen: " + result);
        context.assertTrue(expected >= 5 && expected <= 15, "5 to 15: " + expected);
        context.assertEquals(result.amount(), expected, "the amount drawn");
        context.assertEquals(count(thief, Items.GOLD_INGOT), expected, "the thief gets them");
        context.assertEquals(count(target, Items.GOLD_INGOT), 30 - expected, "the target loses them");
        context.assertTrue(result.target().equals(target.getUuid()) && result.thief().equals(thief.getUuid()), "who robbed whom");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void stealsNoMoreThanTheTargetHolds(TestContext context) {
        ServerPlayerEntity thief = player(context), target = player(context);
        PartyControllerEntity party = party(context, thief, target);
        give(target, Items.GOLD_INGOT, 3);
        give(target, Items.GOLD_NUGGET, 20); // not the party's coin
        Result result = ThiefBellEffect.steal(party, thief, target, Variant.THIEF, Random.create(1), Protection.NONE);
        context.assertEquals(result.amount(), 3, "only the 3 coins held");
        context.assertEquals(count(thief, Items.GOLD_INGOT), 3, "the thief gets 3");
        context.assertEquals(count(target, Items.GOLD_INGOT), 0, "the target has none left");
        context.assertEquals(count(target, Items.GOLD_NUGGET), 20, "other items untouched");
        Result again = ThiefBellEffect.steal(party, thief, target, Variant.THIEF, Random.create(1), Protection.NONE);
        context.assertTrue(again.outcome() == Outcome.NOTHING && again.amount() == 0, "nothing left to steal: " + again);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void goldenBellStealsOneStar(TestContext context) {
        ServerPlayerEntity thief = player(context), target = player(context);
        PartyControllerEntity party = party(context, thief, target);
        give(target, Items.EMERALD, 2);
        give(target, Items.GOLD_INGOT, 40);
        Result result = ThiefBellEffect.steal(party, thief, target, Variant.GOLDEN, Random.create(3), Protection.NONE);
        context.assertTrue(result.outcome() == Outcome.STOLEN && result.amount() == 1, "one star: " + result);
        context.assertEquals(count(thief, Items.EMERALD), 1, "the thief gets the star");
        context.assertEquals(count(target, Items.EMERALD), 1, "the target keeps the other");
        context.assertEquals(count(target, Items.GOLD_INGOT), 40, "no coin taken");
        context.assertEquals(count(thief, Items.GOLD_INGOT), 0, "no coin given");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void goldenBellFindsNoStar(TestContext context) {
        ServerPlayerEntity thief = player(context), target = player(context);
        PartyControllerEntity party = party(context, thief, target);
        give(target, Items.GOLD_INGOT, 40);
        Result result = ThiefBellEffect.steal(party, thief, target, Variant.GOLDEN, Random.create(3), Protection.NONE);
        context.assertTrue(result.outcome() == Outcome.NOTHING && result.amount() == 0, "no star, nothing: " + result);
        context.assertEquals(count(target, Items.GOLD_INGOT), 40, "not coins instead");
        context.assertEquals(count(thief, Items.GOLD_INGOT) + count(thief, Items.EMERALD), 0, "the thief gets nothing");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void unansweredPickerRobsTheRichest(TestContext context) {
        ServerPlayerEntity thief = player(context), poor = player(context), rich = player(context), starry = player(context);
        PartyControllerEntity party = party(context, thief, poor, rich, starry);
        give(thief, Items.GOLD_INGOT, 99); // the thief's own wealth does not count
        give(poor, Items.GOLD_INGOT, 4);
        give(rich, Items.GOLD_INGOT, 25);
        give(starry, Items.GOLD_INGOT, 2);
        give(starry, Items.EMERALD, 1);
        context.assertTrue(ThiefBellEffect.candidates(party, thief).equals(List.of(poor, rich, starry)), "the others, in play order");
        context.assertTrue(ThiefBellEffect.defaultTarget(party, thief, Variant.THIEF) == rich, "most coins");
        context.assertTrue(ThiefBellEffect.defaultTarget(party, thief, Variant.GOLDEN) == starry, "most stars");
        List<Result> results = new ArrayList<>();
        ThiefBellEffect.use(party, thief, Variant.THIEF, Random.create(5), Protection.NONE, 5, results::add);
        DicePrompts.Prompt prompt = DicePrompts.pending(thief);
        context.assertTrue(prompt != null && prompt.options().size() == 3, "the thief is asked whom to rob");
        context.assertEquals(prompt.defaultIndex(), 1, "the richest is the default");
        when(context, () -> !results.isEmpty(), 40, "the picker times out", () -> {
            Result result = results.getFirst();
            context.assertTrue(result.target().equals(rich.getUuid()) && result.outcome() == Outcome.STOLEN, "the richest is robbed: " + result);
            context.assertEquals(count(rich, Items.GOLD_INGOT), 25 - result.amount(), "from the richest");
            context.assertEquals(count(poor, Items.GOLD_INGOT), 4, "not the others");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void padlockHookCancelsTheTheft(TestContext context) {
        ServerPlayerEntity thief = player(context), target = player(context);
        PartyControllerEntity party = party(context, thief, target);
        give(target, Items.GOLD_INGOT, 30);
        int[] asked = {0};
        Result result = ThiefBellEffect.steal(party, thief, target, Variant.THIEF, Random.create(2), (t, v, variant) -> {
            asked[0]++;
            return v == target;
        });
        context.assertTrue(result.outcome() == Outcome.PROTECTED && result.amount() == 0, "protected: " + result);
        context.assertEquals(asked[0], 1, "the hook is asked once");
        context.assertEquals(count(target, Items.GOLD_INGOT), 30, "nothing taken");
        Result alone = ThiefBellEffect.steal(party, thief, null, Variant.THIEF, Random.create(2), Protection.NONE);
        context.assertTrue(alone.outcome() == Outcome.NO_TARGET, "nobody to rob: " + alone);
        context.complete();
    }
}
