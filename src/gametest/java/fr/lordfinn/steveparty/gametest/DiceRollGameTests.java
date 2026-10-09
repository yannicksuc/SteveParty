package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLiveData;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.Kind;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.Played;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.dice.DiceOutcome;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.service.DiceRollEffects;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;

import java.util.List;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestAsserts.assertOn;
import static fr.lordfinn.steveparty.gametest.kit.TestAsserts.count;
import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * The special dice faces: the face 0 (the tile lands again), the coin and debt faces (the roller's coins), the swap
 * face (two tokens swap places), in a party and outside, alone and with other dice.
 */
public class DiceRollGameTests implements FabricGameTest {
    private static final String BATCH = "dice_rolls";

    // ---------------------------------------------------------------- the faces themselves

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyFaceItemIsItsFace(TestContext context) {
        for (Item item : ModItems.DICE_FACES) {
            DiceFace face = DiceFace.fromItem(item).orElse(null);
            context.assertTrue(face != null, item + " is a face");
            context.assertEquals(face.toItem(), item, "the face gives its item back");
        }
        context.assertEquals(ModItems.DICE_FACES.size(), 1 + 11 + 10 + 3 + 10 + 10 + 1, "blank, 0..10, premium, cursed, coins, debts, swap");
        DiceFace zero = DiceFace.fromItem(face("dice_face_0")).orElseThrow();
        context.assertTrue(zero.isZero() && zero.steps() == 0 && zero.coins() == 0, "the face 0");
        DiceFace coin = DiceFace.fromItem(face("coin_dice_face_7")).orElseThrow();
        context.assertTrue(coin.kind() == DiceFacesComponent.Kind.COIN && coin.coins() == 7 && coin.steps() == 0, "+7 coins, no step");
        DiceFace debt = DiceFace.fromItem(face("debt_dice_face_10")).orElseThrow();
        context.assertTrue(debt.kind() == DiceFacesComponent.Kind.DEBT && debt.coins() == -10 && debt.steps() == 0, "-10 coins, no step");
        DiceFace swap = DiceFace.fromItem(face("swap_dice_face")).orElseThrow();
        context.assertTrue(swap.kind() == DiceFacesComponent.Kind.SWAP && swap.steps() == 0, "the swap face");
        context.assertTrue(!DiceFace.fromItem(face("blank_dice_face")).orElseThrow().isZero(), "the blank side is not the face 0");

        // They are forged like any face, with their weights
        ItemStack die = DiceFacesComponent.createDie(List.of(new ItemStack(face("coin_dice_face_3"), 4), new ItemStack(face("swap_dice_face")),
                new ItemStack(face("dice_face_0"), 2)));
        context.assertEquals(die.get(DiceFacesComponent.TYPE).faces(), List.of(new DiceFace(DiceFacesComponent.Kind.NORMAL, 0, 2),
                new DiceFace(DiceFacesComponent.Kind.COIN, 3, 4), new DiceFace(DiceFacesComponent.Kind.SWAP, 0, 1)), "the forged faces");
        context.complete();
    }

    /** Several dice: the numbers add up, the coins add up into one change, one swap at most. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theFacesOfSeveralDiceCombine(TestContext context) {
        DiceFace three = new DiceFace(DiceFacesComponent.Kind.NORMAL, 3), premium = new DiceFace(DiceFacesComponent.Kind.PREMIUM, 5);
        DiceFace coin = new DiceFace(DiceFacesComponent.Kind.COIN, 4), debt = new DiceFace(DiceFacesComponent.Kind.DEBT, 6);
        DiceFace swap = new DiceFace(DiceFacesComponent.Kind.SWAP, 0), zero = new DiceFace(DiceFacesComponent.Kind.NORMAL, 0);
        DiceFace blank = new DiceFace(DiceFacesComponent.Kind.BLANK, 0);

        DiceOutcome numbers = DiceOutcome.of(List.of(three, premium));
        context.assertTrue(numbers.steps() == 8 && !numbers.isSpecial() && !numbers.landsInPlace(), "3 + 5 = 8 steps");
        DiceOutcome mixed = DiceOutcome.of(List.of(three, coin, debt));
        context.assertTrue(mixed.steps() == 3 && mixed.coins() == -2 && mixed.coinFace() && mixed.isSpecial(), "3 steps, +4 -6 = -2 coins");
        DiceOutcome swaps = DiceOutcome.of(List.of(swap, swap, coin));
        context.assertTrue(swaps.swap() && swaps.steps() == 0 && swaps.coins() == 4, "two swap faces: one swap; +4 coins");
        context.assertTrue(DiceOutcome.of(List.of(zero)).landsInPlace(), "a 0 alone lands in place");
        context.assertTrue(DiceOutcome.of(List.of(zero, zero)).landsInPlace(), "0 + 0 lands in place");
        context.assertTrue(!DiceOutcome.of(List.of(zero, three)).landsInPlace(), "0 + 3 walks 3 steps");
        context.assertTrue(!DiceOutcome.of(List.of(zero, coin)).landsInPlace(), "0 + coins: the turn ends without landing");
        DiceOutcome blanks = DiceOutcome.of(List.of(blank));
        context.assertTrue(blanks.steps() == 0 && !blanks.isSpecial() && !blanks.landsInPlace(), "the blank side does nothing");
        DiceOutcome reversed = mixed.reversed();
        context.assertTrue(reversed.steps() == -3 && reversed.coins() == 2 && reversed.coinFace(), "reversed: 3 back, +2 coins");
        context.complete();
    }

    // ---------------------------------------------------------------- face 0

    /** In a party: the token stays, its tile plays its landing again, the turn ends. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void faceZeroLandsInPlaceInAParty(TestContext context) {
        path(context, 3, -1, null);
        List<Played> played = record(context);
        ServerPlayerEntity player = player(context);
        PigEntity pig = token(context, PATH.get(1), player.getUuid());
        PartyControllerEntity controller = party(context, player.getUuid(), pig);
        context.waitAndRun(2, () -> {
            DiceEntity dice = thrown(context, player, die("dice_face_0"), PATH.get(1));
            hit(context, dice, player);
            context.assertTrue(dice.getOutcome().landsInPlace() && dice.getRolledFace().isZero(), "a 0 was rolled");
            when(context, turnEnded(controller), 150, "the turn ends", () -> {
                assertOn(context, pig, PATH.get(1), "the token did not move");
                context.assertTrue(played(played, context, Kind.LAND, PATH.get(1)), "its tile landed again");
                context.assertTrue(!DiceRollEffects.isResolving(pig.getUuid()), "the roll is resolved");
                context.complete();
            });
        });
    }

    /** Outside a party: the token stays and arrives on its tile again (the free play pop), nothing else. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void faceZeroArrivesInPlaceOutsideAParty(TestContext context) {
        path(context, 3, -1, null);
        List<Played> played = record(context);
        ServerPlayerEntity player = player(context);
        PigEntity pig = token(context, PATH.get(1), player.getUuid());
        context.waitAndRun(2, () -> {
            DiceEntity dice = thrown(context, player, die("dice_face_0"), PATH.get(1));
            hit(context, dice, player);
            when(context, () -> played(played, context, Kind.AMBIENT, PATH.get(1)), 100, "the tile is reached again", () -> {
                assertOn(context, pig, PATH.get(1), "the token did not move");
                context.complete();
            });
        });
    }

    // ---------------------------------------------------------------- coins

    /** In a party: the roller gains the party's coin item, the token stays, nothing lands, the turn ends; the HUD tells it. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void coinFaceGivesThePartysCoins(TestContext context) {
        path(context, 3, -1, null);
        List<Played> played = record(context);
        ServerPlayerEntity player = player(context);
        PigEntity pig = token(context, PATH.get(1), player.getUuid());
        PartyControllerEntity controller = party(context, player.getUuid(), pig);
        context.assertTrue(controller.setCurrency(PartyCurrency.COIN, new ItemStack(Items.GOLD_INGOT)), "the party counts gold ingots");
        context.waitAndRun(2, () -> {
            DiceEntity dice = thrown(context, player, die("coin_dice_face_5"), PATH.get(1));
            hit(context, dice, player);
            context.assertTrue(DiceRollEffects.isResolving(pig.getUuid()) || dice.getOutcome().coins() == 5, "+5 was rolled");
            when(context, () -> count(player, Items.GOLD_INGOT) == 5, 100, "the coins are given", () -> {
                context.assertEquals(count(player, ModItems.COIN), 0, "not the default coin");
                PartyLiveData live = PartyLiveData.capture(controller, context.getWorld());
                context.assertTrue(live.effect().rolled() && live.effect().coinFace() && live.effect().coins() == 5, "the HUD: +5 coins");
                RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
                PartyLiveData.PACKET_CODEC.encode(buf, live);
                context.assertEquals(PartyLiveData.PACKET_CODEC.decode(buf).effect(), live.effect(), "the effect is sent to the clients");
                when(context, turnEnded(controller), 100, "the turn ends", () -> {
                    assertOn(context, pig, PATH.get(1), "the token did not move");
                    context.assertTrue(!played(played, context, Kind.LAND, PATH.get(1)), "no landing");
                    context.assertEquals(count(player, Items.GOLD_INGOT), 5, "given once");
                    context.complete();
                });
            });
        });
    }

    /** Outside a party: the default coin (the mod's coin). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void coinFaceGivesCoinsOutsideAParty(TestContext context) {
        path(context, 3, -1, null);
        List<Played> played = record(context);
        ServerPlayerEntity player = player(context);
        PigEntity pig = token(context, PATH.get(1), player.getUuid());
        context.assertTrue(PartyCurrency.COIN.defaultStack().isOf(ModItems.COIN), "the default coin is the mod's coin");
        context.waitAndRun(2, () -> {
            DiceEntity dice = thrown(context, player, die("coin_dice_face_10"), PATH.get(1));
            hit(context, dice, player);
            when(context, () -> count(player, ModItems.COIN) == 10, 100, "the coins are given", () ->
                    when(context, () -> !DiceRollEffects.isResolving(pig.getUuid()), 100, "the roll is resolved", () -> {
                        assertOn(context, pig, PATH.get(1), "the token did not move");
                        context.assertTrue(!played(played, context, Kind.AMBIENT, PATH.get(1)), "it did not arrive on its tile again");
                        context.complete();
                    }));
        });
    }

    /** A debt face never takes more than the roller holds, and only the party's coin. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void debtFaceTakesNoMoreThanHeld(TestContext context) {
        path(context, 3, -1, null);
        ServerPlayerEntity player = player(context);
        PigEntity pig = token(context, PATH.get(1), player.getUuid());
        PartyControllerEntity controller = party(context, player.getUuid(), pig);
        player.getInventory().setStack(3, new ItemStack(ModItems.COIN, 2));
        ItemStack renamed = new ItemStack(ModItems.COIN, 9);
        renamed.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Not a coin"));
        player.getInventory().setStack(4, renamed);
        context.waitAndRun(2, () -> {
            DiceEntity dice = thrown(context, player, die("debt_dice_face_5"), PATH.get(1));
            hit(context, dice, player);
            context.assertEquals(dice.getOutcome().coins(), -5, "-5 was rolled");
            when(context, turnEnded(controller), 150, "the turn ends", () -> {
                context.assertEquals(count(player, ModItems.COIN), 0, "the 2 coins held are taken");
                context.assertEquals(player.getInventory().getStack(4).getCount(), 9, "a renamed coin is not the party's coin");
                assertOn(context, pig, PATH.get(1), "the token did not move");
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void coinsAreGivenAndTakenByTheirTemplate(TestContext context) {
        ServerPlayerEntity player = player(context);
        ItemStack coin = new ItemStack(Items.EMERALD);
        InventoryUtils.giveOrDrop(player, coin, 70);
        context.assertEquals(count(player, Items.EMERALD), 70, "70 given (more than a stack)");
        context.assertEquals(InventoryUtils.take(player.getInventory(), coin, 100), 70, "no more than held is taken");
        context.assertEquals(count(player, Items.EMERALD), 0, "nothing left");
        context.assertEquals(InventoryUtils.take(player.getInventory(), coin, 3), 0, "nothing to take");
        context.complete();
    }

    // ---------------------------------------------------------------- swap

    /** In a party: the roller picks one of the other tokens; both end on the other's tile, nothing lands, the turn ends. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = BATCH)
    public void swapFaceSwapsWithTheChosenToken(TestContext context) {
        path(context, 6, -1, null);
        List<Played> played = record(context);
        ServerPlayerEntity player = player(context);
        PigEntity a = token(context, PATH.get(0), player.getUuid());
        PigEntity b = token(context, PATH.get(2), player.getUuid());
        PigEntity c = token(context, PATH.get(4), player.getUuid());
        PartyControllerEntity controller = party(context, player.getUuid(), a, b, c);
        context.waitAndRun(2, () -> {
            DiceEntity dice = thrown(context, player, die("swap_dice_face"), PATH.get(0));
            hit(context, dice, player);
            context.assertTrue(dice.getOutcome().swap(), "the swap face was rolled");
            when(context, () -> DicePrompts.pending(player) != null, 100, "the roller is asked who to swap with", () -> {
                DicePrompts.Prompt prompt = DicePrompts.pending(player);
                context.assertEquals(prompt.options().size(), 2, "the two other tokens of the party");
                PartyLiveData choosing = PartyLiveData.capture(controller, context.getWorld());
                context.assertTrue(choosing.effect().swap() && choosing.effect().swapWith().isEmpty() && choosing.moving(), "the HUD: choosing");
                context.assertTrue(DicePrompts.answer(player, prompt.id(), 1), "the roller picks the second one");
                assertOn(context, a, PATH.get(4), "a is where c was");
                assertOn(context, c, PATH.get(0), "c is where a was");
                assertOn(context, b, PATH.get(2), "b did not move");
                context.assertTrue(!PartyLiveData.capture(controller, context.getWorld()).effect().swapWith().isEmpty(), "the HUD: who it swapped with");
                when(context, turnEnded(controller), 100, "the turn ends", () -> {
                    context.assertTrue(played.stream().noneMatch(event -> event.kind() == Kind.LAND), "no landing");
                    context.assertEquals(controller.getPartyData().getStepIndex(), 2, "the next turn");
                    context.complete();
                });
            });
        });
    }

    /** Outside a party: the tokens nearby; without an answer, a random one. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void swapTimesOutToARandomTokenNearby(TestContext context) {
        path(context, 6, -1, null);
        ServerPlayerEntity player = player(context);
        PigEntity a = token(context, PATH.get(0), player.getUuid());
        PigEntity b = token(context, PATH.get(2), player.getUuid());
        PigEntity c = token(context, PATH.get(4), player.getUuid());
        context.waitAndRun(2, () -> {
            List<MobEntity> candidates = DiceRollEffects.swapCandidates(context.getWorld(), a);
            context.assertTrue(candidates.contains(b) && candidates.contains(c) && !candidates.contains(a), "the tokens nearby, not itself");
            DiceRollEffects.resolve(context.getWorld(), a, player.getUuid(), DiceOutcome.of(List.of(new DiceFace(DiceFacesComponent.Kind.SWAP, 0))), 1, 10);
            when(context, () -> DicePrompts.pending(player) != null, 20, "the roller is asked", () ->
                    when(context, () -> !isOn(context, a, PATH.get(0)), 40, "the time is over: swapped anyway", () -> {
                        context.assertTrue(DicePrompts.pending(player) == null, "the prompt is over");
                        boolean withB = isOn(context, a, PATH.get(2)) && isOn(context, b, PATH.get(0)) && isOn(context, c, PATH.get(4));
                        boolean withC = isOn(context, a, PATH.get(4)) && isOn(context, c, PATH.get(0)) && isOn(context, b, PATH.get(2));
                        context.assertTrue(withB || withC, "swapped with one of them");
                        when(context, () -> !DiceRollEffects.isResolving(a.getUuid()), 60, "the roll is resolved", context::complete);
                    }));
        });
    }

    /** Nobody to swap with: nothing happens, the turn ends. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void swapWithNobodyEndsTheTurn(TestContext context) {
        path(context, 3, -1, null);
        ServerPlayerEntity player = player(context);
        PigEntity pig = token(context, PATH.get(1), player.getUuid());
        PartyControllerEntity controller = party(context, player.getUuid(), pig);
        context.waitAndRun(2, () -> {
            DiceRollEffects.resolve(context.getWorld(), pig, player.getUuid(), DiceOutcome.of(List.of(new DiceFace(DiceFacesComponent.Kind.SWAP, 0))), 1, 10);
            when(context, turnEnded(controller), 100, "the turn ends", () -> {
                context.assertTrue(DicePrompts.pending(player) == null, "nobody was asked");
                assertOn(context, pig, PATH.get(1), "the token did not move");
                context.complete();
            });
        });
    }

    // ---------------------------------------------------------------- several effects in one roll

    /** Coins, then the swap, then the steps: the token walks from the tile it swapped to. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = BATCH)
    public void coinsThenSwapThenSteps(TestContext context) {
        path(context, 6, -1, null);
        ServerPlayerEntity player = player(context);
        PigEntity a = token(context, PATH.get(0), player.getUuid());
        PigEntity b = token(context, PATH.get(2), player.getUuid());
        PartyControllerEntity controller = party(context, player.getUuid(), a, b);
        context.waitAndRun(2, () -> {
            DiceOutcome outcome = DiceOutcome.of(List.of(new DiceFace(DiceFacesComponent.Kind.COIN, 3),
                    new DiceFace(DiceFacesComponent.Kind.SWAP, 0), new DiceFace(DiceFacesComponent.Kind.NORMAL, 2)));
            DiceRollEffects.resolve(context.getWorld(), a, player.getUuid(), outcome, 1, 100);
            when(context, () -> DicePrompts.pending(player) != null, 20, "asked who to swap with", () -> {
                context.assertEquals(count(player, ModItems.COIN), 3, "the coins come first");
                DicePrompts.answer(player, DicePrompts.pending(player).id(), 0);
                when(context, turnEnded(controller), 200, "the turn ends", () -> {
                    assertOn(context, a, PATH.get(4), "a swapped to the third tile, then walked 2 steps");
                    assertOn(context, b, PATH.get(0), "b is where a was");
                    context.complete();
                });
            });
        });
    }

    /** While a roll is being resolved, another die does not move the token. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void noOtherRollWhileOneIsResolved(TestContext context) {
        path(context, 4, -1, null);
        ServerPlayerEntity player = player(context);
        PigEntity pig = token(context, PATH.get(0), player.getUuid());
        context.waitAndRun(2, () -> {
            DiceEntity coins = thrown(context, player, die("coin_dice_face_2"), PATH.get(0));
            hit(context, coins, player);
            context.assertTrue(DiceRollEffects.isResolving(pig.getUuid()), "the coin roll is being resolved");
            DiceEntity steps = thrown(context, player, die("dice_face_3"), PATH.get(0));
            hit(context, steps, player);
            when(context, () -> !DiceRollEffects.isResolving(pig.getUuid()), 100, "resolved", () -> context.waitAndRun(40, () -> {
                assertOn(context, pig, PATH.get(0), "the second die moved nothing");
                context.assertEquals(count(player, ModItems.COIN), 2, "the coins of the first one");
                context.complete();
            }));
        });
    }

    /** The dice of a Double Dice carry the same faces: both roll them, and the faces add up into one roll. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void linkedDiceAddUpTheirSpecialFaces(TestContext context) {
        path(context, 3, -1, null);
        ServerPlayerEntity player = player(context);
        PigEntity pig = token(context, PATH.get(1), player.getUuid());
        ItemStack doubleDie = new ItemStack(ModItems.DOUBLE_DICE);
        doubleDie.set(DiceFacesComponent.TYPE, die("coin_dice_face_4").get(DiceFacesComponent.TYPE));
        context.waitAndRun(2, () -> {
            DiceEntity lead = thrown(context, player, doubleDie, PATH.get(1));
            DiceEntity second = context.spawnEntity(ModEntities.DICE_ENTITY, PATH.get(1).up(3));
            second.setNoGravity(true);
            second.age = DiceEntity.THROW_GRACE_TICKS;
            second.setOwner(player.getUuid());
            second.follow(doubleDie.copy());
            lead.setLinkedDice(List.of(lead.getUuid(), second.getUuid()));
            second.setLinkedDice(List.of(lead.getUuid(), second.getUuid()));
            context.assertTrue(second.lead() == lead && lead.group().size() == 2, "the second die follows the first");
            hit(context, second, player); // hitting any of them stops them all
            context.assertTrue(!lead.isRolling() && !second.isRolling(), "both stopped");
            context.assertEquals(lead.getRolledFaces().size(), 2, "one face per die");
            context.assertEquals(second.getOutcome().coins(), 8, "+4 and +4");
            when(context, () -> count(player, ModItems.COIN) == 8, 100, "the coins of both dice", () -> {
                assertOn(context, pig, PATH.get(1), "the token did not move");
                if (!second.isRemoved()) second.discard();
                context.complete();
            });
        });
    }
}
