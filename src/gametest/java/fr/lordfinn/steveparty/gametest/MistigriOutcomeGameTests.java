package fr.lordfinn.steveparty.gametest;

import com.mojang.serialization.JsonOps;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyResources;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.components.MistigriOutcomes;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Kind;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Outcome;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Target;
import fr.lordfinn.steveparty.dice.CursedRolls;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenus;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeRef;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.OutcomeListModule;
import fr.lordfinn.steveparty.service.MistigriSentences;
import fr.lordfinn.steveparty.service.OutcomeRoulettes;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestAsserts.count;
import static fr.lordfinn.steveparty.gametest.kit.TestCleanup.atEnd;
import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * The Mistigri's own list of sentences ({@link MistigriOutcomes}): who each target aims at, what each kind does on each
 * target (coins and stars only ever moved: into the bank, out of it, from player to player), a number or a share of
 * what they hold, the picking of some rows for a landing (each as likely) then the weights, the menu's actions checked
 * on the server, and a show passing a sentence written in the menu.
 */
public class MistigriOutcomeGameTests implements SteveGameTest {
    private static final String BATCH = "mistigri_outcomes";
    private static final BlockPos TILE = new BlockPos(3, 1, 3);
    private static final int WHOLE = MistigriSentences.WHOLE + 20;
    private static final Item COIN = Items.GOLD_INGOT, STAR = Items.EMERALD;

    /** Four players A (who landed), B, C, D, one token each; gold ingots as coins, emeralds as stars. */
    private record Table(PartyControllerEntity party, List<ServerPlayerEntity> players, List<MobEntity> tokens) {
        ServerPlayerEntity get(int i) {
            return players.get(i);
        }
    }

    private static Table table(TestContext context, int count) {
        List<ServerPlayerEntity> players = new ArrayList<>();
        List<MobEntity> tokens = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ServerPlayerEntity player = player(context);
            players.add(player);
            tokens.add(token(context, new BlockPos(1 + 2 * i, 1, 1), player.getUuid()));
            atEnd(context, () -> player.removeCommandTag(CursedRolls.TAG));
        }
        PartyControllerEntity party = DiceTestKit.party(context, players.getFirst().getUuid(), tokens.toArray(MobEntity[]::new));
        context.assertTrue(party.setCurrency(PartyCurrency.COIN, new ItemStack(COIN)), "coins: gold ingots");
        context.assertTrue(party.setCurrency(PartyCurrency.STAR, new ItemStack(STAR)), "stars: emeralds");
        return new Table(party, players, tokens);
    }

    /** {@code player} holds exactly these stars and coins, and no curse. */
    private static void hold(ServerPlayerEntity player, int stars, int coins) {
        player.getInventory().clear();
        player.removeCommandTag(CursedRolls.TAG);
        if (stars > 0) InventoryUtils.giveOrDrop(player, new ItemStack(STAR), stars);
        if (coins > 0) InventoryUtils.giveOrDrop(player, new ItemStack(COIN), coins);
    }

    /** A: 2 stars 10 coins; B: 3 stars 0 coins; C: 0 stars 30 coins; D: 3 stars 0 coins. */
    private static void deal(Table table) {
        hold(table.get(0), 2, 10);
        hold(table.get(1), 3, 0);
        hold(table.get(2), 0, 30);
        hold(table.get(3), 3, 0);
    }

    private static int bank(Table table, Item item) {
        return InventoryUtils.count(table.party.getBankItems(), new ItemStack(item));
    }

    private static @org.jetbrains.annotations.Nullable MutableText apply(TestContext context, Table table, Outcome outcome, long seed) {
        return MistigriSentences.apply(context.getWorld(), table.party, table.get(0), outcome, Random.create(seed), MistigriSentences.Moves.NONE);
    }

    private static List<ServerPlayerEntity> targets(TestContext context, Table table, Target target, long seed) {
        return MistigriSentences.targets(context.getWorld(), table.party, table.get(0), target, Random.create(seed));
    }

    private static String key(MutableText text) {
        return text != null && text.getContent() instanceof TranslatableTextContent translatable ? translatable.getKey() : "";
    }

    /** Who each target aims at, as they hold now; a tie aims at every tied one; one at random, each in turn. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void everyTargetAimsAtTheRightPlayers(TestContext context) {
        Table table = table(context, 4);
        deal(table);
        ServerPlayerEntity a = table.get(0), b = table.get(1), c = table.get(2), d = table.get(3);
        context.assertEquals(targets(context, table, Target.LANDED, 1), List.of(a), "who landed");
        context.assertEquals(targets(context, table, Target.FIRST, 1), List.of(b, d), "first place: 3 stars and 0 coins, tied");
        context.assertEquals(targets(context, table, Target.LAST, 1), List.of(c), "last place: no star, whatever the coins");
        context.assertEquals(targets(context, table, Target.ALL, 1), List.of(a, b, c, d), "everyone");
        context.assertEquals(targets(context, table, Target.OTHERS, 1), List.of(b, c, d), "everyone but who landed");
        context.assertEquals(targets(context, table, Target.RICHEST, 1), List.of(c), "the most coins");
        context.assertEquals(targets(context, table, Target.POOREST, 1), List.of(b, d), "the fewest coins, tied");
        Set<ServerPlayerEntity> drawn = new HashSet<>();
        Random random = Random.create(5);
        for (int n = 0; n < 200; n++) {
            List<ServerPlayerEntity> one = MistigriSentences.targets(context.getWorld(), table.party, a, Target.RANDOM, random);
            context.assertEquals(one.size(), 1, "one at random");
            drawn.add(one.getFirst());
        }
        context.assertEquals(drawn.size(), 4, "anyone may be drawn");
        hold(b, 2, 10);
        hold(d, 2, 10);
        context.assertEquals(targets(context, table, Target.FIRST, 1), List.of(a, b, d), "the same stars and coins: all first");
        context.complete();
    }

    /**
     * Every kind on every target: it touches the players aimed at and only them; what is taken goes to the bank, what
     * is given comes out of it, a redistribution only passes coins and stars from player to player.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void everyKindOnEveryTarget(TestContext context) {
        Table table = table(context, 4);
        for (Kind kind : Kind.values()) {
            if (kind == Kind.MOVE_BACK) continue; // the show's, see MistigriSpaceGameTests
            for (Target target : Target.values()) {
                String name = kind.id + " on " + target.id;
                deal(table);
                table.party.getBankItems().clear();
                if (kind == Kind.PITY) PartyResources.deposit(table.party, new ItemStack(COIN, 5));
                int amount = switch (kind) {
                    case LOSE_STARS, SHARE_STARS -> 1;
                    case CURSED_DIE -> 0;
                    default -> 4;
                };
                Outcome outcome = new Outcome(kind, target, amount, false, 1);
                long seed = 11 + target.ordinal();
                List<ServerPlayerEntity> aimed = targets(context, table, target, seed);
                Item currency = kind == Kind.LOSE_STARS || kind == Kind.SHARE_STARS ? STAR : COIN;
                Map<ServerPlayerEntity, Integer> before = new HashMap<>();
                int total = 0;
                for (ServerPlayerEntity each : table.players) {
                    before.put(each, count(each, currency));
                    total += count(each, currency);
                }
                int bankBefore = bank(table, currency);
                apply(context, table, outcome, seed);
                int totalAfter = 0, lost = 0, given = 0;
                for (ServerPlayerEntity each : table.players) {
                    int was = before.get(each), now = count(each, currency);
                    totalAfter += now;
                    boolean isAimed = aimed.contains(each);
                    switch (kind) {
                        case LOSE_COINS, LOSE_STARS -> {
                            context.assertEquals(now, isAimed ? was - Math.min(amount, was) : was, name + ": " + each.getNameForScoreboard());
                            lost += was - now;
                        }
                        case SHARE_COINS, SHARE_STARS -> {
                            if (!isAimed) context.assertTrue(now >= was, name + ": the others never pay");
                            else if (aimed.size() < table.players.size()) context.assertEquals(now, was - Math.min(amount, was), name + ": pays in");
                        }
                        case PITY -> {
                            if (!isAimed) context.assertEquals(now, was, name + ": not aimed at");
                            given += now - was;
                        }
                        case CURSED_DIE -> context.assertEquals(CursedRolls.isCursed(each), isAimed, name + ": cursed " + each.getNameForScoreboard());
                        default -> {
                        }
                    }
                }
                switch (kind) {
                    case LOSE_COINS, LOSE_STARS -> context.assertEquals(bank(table, currency), bankBefore + lost, name + ": all of it into the bank");
                    case SHARE_COINS, SHARE_STARS -> {
                        context.assertEquals(totalAfter, total, name + ": nothing made, nothing lost");
                        context.assertEquals(bank(table, currency), bankBefore, name + ": the bank plays no part");
                    }
                    case PITY -> {
                        context.assertEquals(given, Math.min(5, amount * aimed.size()), name + ": as much as the bank held");
                        context.assertEquals(bank(table, currency), bankBefore - given, name + ": out of the bank");
                    }
                    default -> {
                    }
                }
            }
        }
        context.complete();
    }

    /** A number, or a share of what they hold (rounded down); never more than they hold. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void aNumberOrAPercentage(TestContext context) {
        Table table = table(context, 1);
        ServerPlayerEntity a = table.get(0);
        hold(a, 3, 31);
        apply(context, table, new Outcome(Kind.LOSE_COINS, Target.LANDED, 50, true, 1), 1);
        context.assertEquals(count(a, COIN), 16, "half of 31: 15 taken");
        apply(context, table, new Outcome(Kind.LOSE_STARS, Target.LANDED, 50, true, 1), 1);
        context.assertEquals(count(a, STAR), 2, "half of 3 stars: 1 taken");
        apply(context, table, new Outcome(Kind.LOSE_COINS, Target.LANDED, 20, false, 1), 1);
        context.assertEquals(count(a, COIN), 0, "20 asked, the 16 held taken");
        hold(a, 0, 9);
        MutableText nothing = apply(context, table, new Outcome(Kind.LOSE_COINS, Target.LANDED, 10, true, 1), 1);
        context.assertEquals(count(a, COIN), 9, "10% of 9: nothing");
        context.assertEquals(key(nothing), "message.steveparty.mistigri_space.nothing", "nothing to take, said so");

        // Each kind keeps its ranges
        Outcome back = new Outcome(Kind.MOVE_BACK, Target.ALL, 50, true, 12);
        context.assertEquals(back, new Outcome(Kind.MOVE_BACK, Target.LANDED, Kind.MOVE_BACK.max, false, MistigriOutcomes.MAX_WEIGHT),
                "a move back: only who landed, a number, its max; the weight capped");
        Outcome fine = new Outcome(Kind.LOSE_COINS, Target.FIRST, 10, false, 3);
        context.assertEquals(fine.withPercent(true), new Outcome(Kind.LOSE_COINS, Target.FIRST, 50, true, 3), "a share: half to start with");
        context.assertEquals(fine.withPercent(true).withPercent(false).amount(), Kind.LOSE_COINS.defaultAmount, "a number: the default again");
        context.assertEquals(fine.withKind(Kind.LOSE_STARS), new Outcome(Kind.LOSE_STARS, Target.FIRST, 1, false, 3), "another kind: its default amount, the same target");
        context.assertEquals(fine.withKind(Kind.MOVE_BACK).target(), Target.LANDED, "a move back: who landed");
        context.assertEquals(new Outcome(Kind.CURSED_DIE, Target.LAST, 7, true, 2).amount(), 0, "a cursed die has no amount");
        context.complete();
    }

    /** A redistribution: dealt out to those who gave nothing, the poorest first; among all when all gave. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void theCommonPotFeedsThePoorestFirst(TestContext context) {
        Table table = table(context, 4);
        ServerPlayerEntity a = table.get(0), b = table.get(1), c = table.get(2), d = table.get(3);
        hold(a, 0, 10);
        hold(b, 0, 0);
        hold(c, 0, 5);
        hold(d, 0, 0);
        apply(context, table, new Outcome(Kind.SHARE_COINS, Target.LANDED, 10, false, 1), 1);
        context.assertEquals(List.of(count(a, COIN), count(b, COIN), count(c, COIN), count(d, COIN)), List.of(0, 4, 8, 3),
                "10 coins dealt out: 4 to B and 3 to D (none), 3 to C (5)");
        hold(a, 0, 10);
        hold(b, 0, 0);
        hold(c, 0, 30);
        hold(d, 0, 0);
        apply(context, table, new Outcome(Kind.SHARE_COINS, Target.ALL, 50, true, 1), 1);
        context.assertEquals(List.of(count(a, COIN), count(b, COIN), count(c, COIN), count(d, COIN)), List.of(10, 5, 20, 5),
                "half of everyone's coins (20) dealt out evenly among all");
        context.assertEquals(bank(table, COIN), 0, "the bank plays no part");
        context.complete();
    }

    /** Pity gives out of the party's bank: an empty bank gives nothing, and says so. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void pityComesOutOfTheBank(TestContext context) {
        Table table = table(context, 2);
        hold(table.get(0), 0, 0);
        hold(table.get(1), 0, 0);
        table.party.getBankItems().clear();
        MutableText empty = apply(context, table, new Outcome(Kind.PITY, Target.ALL, 2, false, 1), 1);
        context.assertEquals(count(table.get(0), COIN) + count(table.get(1), COIN), 0, "nothing made from nothing");
        context.assertEquals(key(empty), "message.steveparty.mistigri_space.bank_empty", "the bank is empty, said so");
        PartyResources.deposit(table.party, new ItemStack(COIN, 3));
        apply(context, table, new Outcome(Kind.PITY, Target.ALL, 2, false, 1), 1);
        context.assertEquals(count(table.get(0), COIN), 2, "the first served");
        context.assertEquals(count(table.get(1), COIN), 1, "the last coin of the bank");
        context.assertEquals(bank(table, COIN), 0, "the bank emptied");
        context.complete();
    }

    /**
     * The rows put on the roulette are picked each as likely, whatever their weights (a weight 0: never); the weights
     * then draw the one that falls among them; the chances shown add both steps up.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void someArePickedThenTheWeightsDecide(TestContext context) {
        List<Outcome> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) rows.add(new Outcome(Kind.LOSE_COINS, Target.LANDED, i + 1, false, i == 0 ? 9 : 1));
        rows.add(new Outcome(Kind.PITY, Target.LANDED, 1, false, 0));
        MistigriOutcomes outcomes = new MistigriOutcomes(rows, 5);
        context.assertEquals(outcomes.drawCount(), 5, "5 of the 10 that may be picked");
        Random random = Random.create(42);
        int[] picked = new int[rows.size()];
        int heavyFalls = 0, heavyOn = 0;
        for (int n = 0; n < 2000; n++) {
            List<Integer> pick = outcomes.pick(random);
            context.assertEquals(pick.size(), 5, "five on the roulette");
            context.assertEquals(new HashSet<>(pick).size(), 5, "five different ones");
            for (int k = 1; k < pick.size(); k++) context.assertTrue(pick.get(k - 1) < pick.get(k), "in the list's order");
            for (int row : pick) picked[row]++;
            int falls = outcomes.draw(pick, random);
            context.assertTrue(pick.contains(falls), "the one that falls is on the roulette");
            if (pick.contains(0)) {
                heavyOn++;
                if (falls == 0) heavyFalls++;
            }
        }
        context.assertEquals(picked[10], 0, "weight 0: never on the roulette");
        context.assertTrue(picked[0] > 850 && picked[0] < 1150, "the heavy one picked like the others (" + picked[0] + ")");
        context.assertTrue(picked[9] > 850 && picked[9] < 1150, "a light one picked about half the time (" + picked[9] + ")");
        context.assertTrue(heavyFalls > heavyOn * 0.6, "once on it, the heavy one mostly falls (" + heavyFalls + "/" + heavyOn + ")");

        double[] chances = outcomes.chances();
        double sum = 0;
        for (double chance : chances) sum += chance;
        context.assertTrue(Math.abs(sum - 1) < 1e-9, "one falls at every landing");
        MistigriOutcomes three = new MistigriOutcomes(List.of(new Outcome(Kind.LOSE_COINS, Target.LANDED, 1, false, 2),
                new Outcome(Kind.LOSE_COINS, Target.LANDED, 2, false, 1), new Outcome(Kind.LOSE_COINS, Target.LANDED, 3, false, 1)), 2);
        double[] exact = three.chances();
        context.assertTrue(Math.abs(exact[0] - 4 / 9.0) < 1e-9 && Math.abs(exact[1] - 5 / 18.0) < 1e-9, "2 of 3: picked, then weighed");
        context.assertTrue(Math.abs(three.withDraws(0).chances()[0] - 0.5) < 1e-9, "all of them: the weights alone");
        context.assertEquals(three.withDraws(9).drawCount(), 3, "more than there are: all of them");
        context.assertEquals(three.chanceAmong(List.of(0, 1), 0), 67, "its chance on the roulette");
        context.assertEquals(new MistigriOutcomes(List.of(rows.get(10)), 0).drawCount(), 0, "nothing that may fall");
        context.complete();
    }

    /** The menu's actions, checked on the server against the cartridge's list, then written. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void theMenuEditsTheList(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            ItemStack stack = new ItemStack(ModItems.MISTIGRI_CARTRIDGE);
            player.setStackInHand(Hand.MAIN_HAND, stack);
            CartridgeRef hand = CartridgeRef.hand(Hand.MAIN_HAND);
            int size = MistigriOutcomes.DEFAULT.size(), row = size;
            context.assertTrue(menu(player, hand, OutcomeListModule.ADD, 0, 0), "add");
            context.assertEquals(MistigriOutcomes.of(stack).size(), size + 1, "one more row");
            context.assertEquals(MistigriOutcomes.of(stack).get(row), MistigriOutcomes.ADDED, "a small fine");

            context.assertTrue(menu(player, hand, OutcomeListModule.KIND, row, Kind.MOVE_BACK.ordinal()), "a move back");
            context.assertEquals(MistigriOutcomes.of(stack).get(row).amount(), Kind.MOVE_BACK.defaultAmount, "its default amount");
            context.assertFalse(menu(player, hand, OutcomeListModule.TARGET, row, Target.ALL.ordinal()), "a move back aims at who landed only");
            context.assertFalse(menu(player, hand, OutcomeListModule.PERCENT, row, 1), "spaces: no percentage");
            context.assertFalse(menu(player, hand, OutcomeListModule.AMOUNT, row, Kind.MOVE_BACK.max + 1), "beyond its max");
            context.assertTrue(menu(player, hand, OutcomeListModule.AMOUNT, row, 5), "5 spaces");

            context.assertTrue(menu(player, hand, OutcomeListModule.KIND, row, Kind.LOSE_STARS.ordinal()), "a star theft");
            context.assertTrue(menu(player, hand, OutcomeListModule.TARGET, row, Target.FIRST.ordinal()), "on the first place");
            context.assertTrue(menu(player, hand, OutcomeListModule.PERCENT, row, 1), "a share");
            context.assertTrue(menu(player, hand, OutcomeListModule.AMOUNT, row, 100), "all of them");
            context.assertFalse(menu(player, hand, OutcomeListModule.AMOUNT, row, 101), "no more than all");
            context.assertTrue(menu(player, hand, OutcomeListModule.WEIGHT, row, 0), "weight 0");
            context.assertFalse(menu(player, hand, OutcomeListModule.WEIGHT, row, MistigriOutcomes.MAX_WEIGHT + 1), "weight capped");
            context.assertEquals(MistigriOutcomes.of(stack).get(row), new Outcome(Kind.LOSE_STARS, Target.FIRST, 100, true, 0), "written");
            context.assertEquals(MistigriOutcomes.of(stack).drawCount(), size, "weight 0: never picked");

            context.assertTrue(menu(player, hand, OutcomeListModule.DRAWS, 0, 3), "3 on the roulette");
            context.assertEquals(MistigriOutcomes.of(stack).drawCount(), 3, "3 picked");
            context.assertFalse(menu(player, hand, OutcomeListModule.DRAWS, 0, MistigriOutcomes.MAX + 1), "no more than a roulette lists");
            context.assertTrue(menu(player, hand, OutcomeListModule.DRAWS, 0, 0), "all of them");

            context.assertTrue(menu(player, hand, OutcomeListModule.DELETE, row, 0), "delete");
            context.assertFalse(menu(player, hand, OutcomeListModule.DELETE, row, 0), "no such row");
            context.assertEquals(MistigriOutcomes.of(stack), MistigriOutcomes.DEFAULT, "the default list again");
            context.assertTrue(stack.get(fr.lordfinn.steveparty.components.ModComponents.MISTIGRI_OUTCOMES) == null, "the default is not stored");

            while (MistigriOutcomes.of(stack).size() < MistigriOutcomes.MAX) context.assertTrue(menu(player, hand, OutcomeListModule.ADD, 0, 0), "add");
            context.assertFalse(menu(player, hand, OutcomeListModule.ADD, 0, 0), MistigriOutcomes.MAX + " at most");
            while (MistigriOutcomes.of(stack).size() > 0) context.assertTrue(menu(player, hand, OutcomeListModule.DELETE, 0, 0), "delete");
            context.assertEquals(MistigriOutcomes.of(stack).drawCount(), 0, "nothing left to fall");
            context.assertFalse(CartridgeMenus.apply(player, hand, "outcomes", -1), "no such action");

            // Saved and sent as is
            MistigriOutcomes custom = MistigriOutcomes.DEFAULT.add(new Outcome(Kind.SHARE_STARS, Target.RICHEST, 25, true, 7)).withDraws(4);
            MistigriOutcomes back = MistigriOutcomes.CODEC.parse(JsonOps.INSTANCE, MistigriOutcomes.CODEC.encodeStart(JsonOps.INSTANCE, custom).getOrThrow()).getOrThrow();
            context.assertEquals(back, custom, "a round trip");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    private static boolean menu(ServerPlayerEntity player, CartridgeRef hand, int op, int row, int value) {
        return CartridgeMenus.apply(player, hand, "outcomes", OutcomeListModule.action(op, row, value));
    }

    /** No sentence that may fall (all deleted, or all weight 0): no Mistigri, a plain landing. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void nothingToFallNoMistigri(TestContext context) {
        Table table = table(context, 1);
        ItemStack cartridge = new ItemStack(ModItems.MISTIGRI_CARTRIDGE);
        MistigriOutcomes.set(cartridge, new MistigriOutcomes(List.of(new Outcome(Kind.PITY, Target.LANDED, 1, false, 0)), 0));
        BoardSpaceBlockEntity tile = tile(context, TILE, cartridge);
        boolean[] done = {false};
        context.assertEquals(MistigriSentences.start(context.getWorld(), tile.getPos(), table.tokens.getFirst(), table.party, cartridge,
                null, () -> done[0] = true), MistigriSentences.Start.NO_SENTENCE, "no sentence");
        context.assertTrue(MistigriSentences.actor(table.tokens.getFirst()) == null && !done[0], "no Mistigri");
        context.complete();
    }

    /** A sentence written in the menu: the roulette lists the rows picked, and the one it stops on happens. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void aSentenceWrittenInTheMenuFalls(TestContext context) {
        Table table = table(context, 2);
        ServerPlayerEntity a = table.get(0), b = table.get(1);
        hold(a, 0, 0);
        hold(b, 1, 10);
        Outcome robinHood = new Outcome(Kind.SHARE_COINS, Target.FIRST, 10, false, 1);
        ItemStack cartridge = new ItemStack(ModItems.MISTIGRI_CARTRIDGE);
        MistigriOutcomes.set(cartridge, new MistigriOutcomes(List.of(robinHood, new Outcome(Kind.LOSE_STARS, Target.ALL, 1, false, 0)), 1));
        BoardSpaceBlockEntity tile = tile(context, TILE, cartridge);
        MobEntity token = table.tokens.getFirst();
        boolean[] done = {false};
        context.assertEquals(MistigriSentences.start(context.getWorld(), tile.getPos(), token, table.party, cartridge, null,
                () -> done[0] = true), MistigriSentences.Start.STARTED, "started");
        context.assertEquals(MistigriSentences.outcome(token), robinHood, "the only one that may fall");
        when(context, () -> MistigriSentences.roulette(token) != null, WHOLE, "the roulette starts", () -> {
            OutcomeRoulettes.Roulette roulette = MistigriSentences.roulette(token);
            context.assertEquals(roulette.lines().size(), 1, "one picked");
            context.assertEquals(roulette.lines().getFirst().text(), robinHood.text(null, new ItemStack(COIN), new ItemStack(STAR)), "its sentence, in the party's coins");
            context.assertEquals(roulette.lines().getFirst().chance(), 100, "its chance");
            when(context, () -> done[0], WHOLE, "the show ends", () -> {
                context.assertEquals(count(b, COIN), 0, "the first place gave 10 coins");
                context.assertEquals(count(a, COIN), 10, "dealt out to the other");
                context.assertEquals(count(b, STAR), 1, "nothing else");
                context.complete();
            });
        });
    }
}
