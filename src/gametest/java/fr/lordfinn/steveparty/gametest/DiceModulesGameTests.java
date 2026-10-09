package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLiveData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.components.DiceFacesComponent.Kind;
import fr.lordfinn.steveparty.dice.DiceModule;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.dice.DiceModulesComponent;
import fr.lordfinn.steveparty.dice.DiceOutcome;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.dice.DiceRollSequence;
import fr.lordfinn.steveparty.dice.DiceRollSequence.Phase;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.entities.custom.DirectionDisplayEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.DiceModuleItem;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import fr.lordfinn.steveparty.service.AdvanceBackMoves;
import fr.lordfinn.steveparty.service.DiceRollEffects;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestAsserts.assertOn;
import static fr.lordfinn.steveparty.gametest.kit.TestCleanup.atEnd;
import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * The dice modules: what a die carries and shows, how each one changes the roll (Slow, Choice, Lucky, Reroll,
 * Reversed, Power-up) or the move (Skeleton Key, Homing), and how they combine.
 */
public class DiceModulesGameTests implements FabricGameTest {
    private static final String BATCH = "dice_modules";
    private static final BlockPos DICE = new BlockPos(4, 1, 5);

    private static DiceRollSequence sequence(DiceEntity dice) {
        return dice.lead().sequence();
    }

    private static List<DiceFace> faces(ItemStack die) {
        return DiceFacesComponent.facesOf(die);
    }

    // ---------------------------------------------------------------- the modules on a die

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void modulesAreRegisteredWithTheirItems(TestContext context) {
        context.assertEquals(DiceModules.all().size(), 9, "slow, choice, power-up, lucky, reroll, reversed, skeleton key, homing, firecracker");
        context.assertEquals(ModItems.DICE_MODULES.size(), DiceModules.all().size(), "one item per module");
        for (DiceModule module : DiceModules.all()) {
            Item item = module.item();
            context.assertTrue(item instanceof DiceModuleItem moduleItem && moduleItem.module() == module, module + " has its item");
            context.assertEquals(DiceModules.fromItem(new ItemStack(item)), module, "the item gives its module");
            context.assertEquals(DiceModules.get(module.id()), module, "found by its id");
        }
        context.assertTrue(DiceModules.LUCKY.stacks() && DiceModules.REROLL.stacks(), "Lucky and Reroll stack");
        for (DiceModule module : List.of(DiceModules.SLOW, DiceModules.CHOICE, DiceModules.POWER_UP, DiceModules.REVERSED,
                DiceModules.SKELETON_KEY, DiceModules.HOMING, DiceModules.FIRECRACKER)) {
            context.assertTrue(!module.stacks(), module + " does not stack");
        }
        context.assertTrue(DiceModules.REVERSED.negative() && DiceModules.REVERSED.color() == Formatting.RED, "Reversed is negative: red");
        context.assertTrue(!DiceModules.LUCKY.negative() && DiceModules.LUCKY.color() != Formatting.RED, "the others are not");
        context.complete();
    }

    /** Several modules on one die, with their counts; the same modules give stackable dice; the tooltip lists them. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aDieCarriesSeveralModulesWithTheirCounts(TestContext context) {
        ItemStack die = with(with(with(die("dice_face_1", "dice_face_6"), DiceModules.LUCKY, 2), DiceModules.POWER_UP, 1), DiceModules.REVERSED, 1);
        Map<DiceModule, Integer> modules = DiceModules.of(die);
        context.assertEquals(modules.size(), 3, "three modules");
        context.assertEquals(DiceModules.count(die, DiceModules.LUCKY), 2, "Lucky x2");
        context.assertTrue(DiceModules.has(die, DiceModules.POWER_UP) && !DiceModules.has(die, DiceModules.SLOW), "has / has not");
        context.assertTrue(DiceModules.isPowerUp(die) && !DiceModules.returnsToRoller(die), "Power-up: it is spent");
        context.assertTrue(DiceModules.returnsToRoller(die("dice_face_1")) && !DiceModules.isPowerUp(die("dice_face_1")),
                "any other die goes back to its roller");

        ItemStack same = with(with(with(die("dice_face_1", "dice_face_6"), DiceModules.REVERSED, 1), DiceModules.POWER_UP, 1), DiceModules.LUCKY, 2);
        context.assertTrue(ItemStack.areItemsAndComponentsEqual(die, same), "the same modules in another order: the same die (stackable)");

        // Counts are capped, a module that doesn't stack counts once
        ItemStack capped = with(with(new ItemStack(ModItems.DEFAULT_DICE), DiceModules.LUCKY, 40), DiceModules.SLOW, 3);
        context.assertEquals(DiceModules.count(capped, DiceModules.LUCKY), DiceModules.LUCKY.maxCount(), "Lucky is capped");
        context.assertEquals(DiceModules.count(capped, DiceModules.SLOW), 1, "Slow counts once");
        context.assertTrue(DiceModules.set(capped.copy(), Map.of()).get(DiceModulesComponent.TYPE) == null, "no module: no component");

        // The tooltip: no tag (its Power-up module line says it all), its faces, one line per module (its count if
        // several; a negative module in red), then the Shift hint.
        Tooltips.forTests(false);
        List<Text> tooltip = new ArrayList<>();
        die.getItem().appendTooltip(die, Item.TooltipContext.DEFAULT, tooltip, TooltipType.BASIC);
        context.assertEquals(tooltip.size(), 5, "the faces, three modules and the hint");
        context.assertTrue(tagKeys(tooltip.getFirst()).isEmpty(), "no tag: the Power-up module line says it");
        List<Text> plainTooltip = new ArrayList<>();
        ItemStack plain = with(die("dice_face_1", "dice_face_6"), DiceModules.LUCKY, 2);
        plain.getItem().appendTooltip(plain, Item.TooltipContext.DEFAULT, plainTooltip, TooltipType.BASIC);
        Tooltips.forTests(null);
        context.assertEquals(plainTooltip.size(), 3, "a plain die: the faces, its module and the hint");
        List<Text> lines = DiceModules.tooltip(die);
        Text lucky = lines.stream().filter(line -> line.getString().contains("×2")).findFirst().orElse(null);
        context.assertTrue(lucky != null, "Lucky shows its count");
        Text reversed = DiceModules.line(DiceModules.REVERSED, 1);
        context.assertTrue(!reversed.getString().contains("×"), "a single module shows no count");
        Set<TextColor> colors = new HashSet<>();
        reversed.visit((style, text) -> {
            if (!text.isBlank()) colors.add(style.getColor());
            return Optional.empty();
        }, Style.EMPTY);
        context.assertEquals(colors, Set.of(TextColor.fromFormatting(Formatting.RED)), "a negative module's line is all red");
        context.complete();
    }

    // ---------------------------------------------------------------- Slow

    /** The faces of a Slow die go by as often as their weights, spread evenly. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void slowCycleFollowsTheWeights(TestContext context) {
        DiceFace one = new DiceFace(Kind.NORMAL, 1, 1), six = new DiceFace(Kind.NORMAL, 6, 3);
        List<DiceFace> cycle = DiceRollSequence.cycle(List.of(one, six));
        context.assertEquals(cycle.size(), 4, "1 + 3 places");
        context.assertEquals(cycle.stream().filter(face -> face.value() == 6).count(), 3L, "the 6 comes up three times as often");
        List<DiceFace> reduced = DiceRollSequence.cycle(List.of(one.withWeight(10), six.withWeight(30)));
        context.assertEquals(reduced.size(), 4, "weights are reduced by their common divisor");
        List<DiceFace> heavy = DiceRollSequence.cycle(List.of(one.withWeight(1), six.withWeight(700)));
        context.assertTrue(heavy.size() <= DiceRollSequence.MAX_CYCLE + 1 && heavy.contains(one), "a long cycle is scaled down, every face kept");
        context.assertEquals(DiceRollSequence.cycle(faces(new ItemStack(ModItems.DEFAULT_DICE))).size(), 10, "a plain die: 1 to 10");
        context.complete();
    }

    /** A Slow die shows its faces one after the other; only its roller stops it, on the face shown. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void slowDieIsStoppedByItsRollerOnTheFaceShown(TestContext context) {
        ServerPlayerEntity roller = player(context), other = player(context);
        ItemStack die = with(die("dice_face_2", "dice_face_5", "dice_face_9"), DiceModules.SLOW, 1);
        DiceEntity dice = thrown(context, roller, die, DICE);
        context.assertTrue(dice.isRolling() && dice.isFaceShown(), "it rolls showing its faces");
        Set<Integer> seen = new HashSet<>();
        context.waitAndRun(DiceRollSequence.SLOW_FACE_TICKS * 4, () -> {
            seen.add(dice.getRollValue());
            context.waitAndRun(DiceRollSequence.SLOW_FACE_TICKS, () -> {
                seen.add(dice.getRollValue());
                context.assertEquals(seen.size(), 2, "the face shown changes");
                context.assertTrue(Set.of(2, 5, 9).containsAll(seen), "its own faces only: " + seen);
                hit(context, dice, other);
                context.assertTrue(dice.isRolling(), "another player's hit does not stop it");
                int shown = dice.getRollValue();
                hit(context, dice, roller);
                context.assertTrue(!dice.isRolling() && dice.isRollFinished(), "its roller stops it");
                context.assertEquals(dice.getRollValue(), shown, "on the face shown");
                context.assertEquals(dice.getOutcome().steps(), shown, "that face is the roll");
                context.complete();
            });
        });
    }

    /** Left alone, a Slow die stops by itself. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = DiceRollSequence.SLOW_TIMEOUT_TICKS + 60, batchId = BATCH)
    public void slowDieStopsByItself(TestContext context) {
        ServerPlayerEntity roller = player(context);
        DiceEntity dice = thrown(context, roller, with(die("dice_face_3", "dice_face_4"), DiceModules.SLOW, 1), DICE);
        when(context, dice::isRollFinished, DiceRollSequence.SLOW_TIMEOUT_TICKS + 20, "it stops by itself", () -> {
            context.assertTrue(Set.of(3, 4).contains(dice.getOutcome().steps()), "on one of its faces");
            context.complete();
        });
    }

    // ---------------------------------------------------------------- Choice

    /** The roller picks the face; hits don't stop the die meanwhile. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void choiceLetsTheRollerPickTheFace(TestContext context) {
        ServerPlayerEntity roller = player(context);
        ItemStack die = with(die("dice_face_1", "dice_face_4", "coin_dice_face_3"), DiceModules.CHOICE, 1);
        DiceEntity dice = thrown(context, roller, die, DICE);
        DicePrompts.Prompt prompt = DicePrompts.pending(roller);
        context.assertTrue(prompt != null && prompt == sequence(dice).prompt(), "the picker is asked as soon as the die is thrown");
        context.assertEquals(prompt.options().size(), 3, "the faces of the die");
        hit(context, dice, roller);
        context.assertTrue(dice.isRolling() && !dice.isRollFinished(), "a hit does not stop a Choice die");
        int coin = faces(die).indexOf(new DiceFace(Kind.COIN, 3));
        context.assertTrue(DicePrompts.answer(roller, prompt.id(), coin), "the roller picks the coin face");
        context.assertTrue(dice.isRollFinished() && !dice.isRolling(), "the die stops");
        context.assertEquals(dice.getRolledFace(), new DiceFace(Kind.COIN, 3), "on the face picked");
        context.assertEquals(dice.getOutcome().coins(), 3, "that face is the roll");
        context.assertTrue(DicePrompts.pending(roller) == null, "nothing else is asked");
        context.complete();
    }

    /** No answer: a face at random. A plain die offers 1 to 10. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void choiceTimesOutToARandomFace(TestContext context) {
        ServerPlayerEntity roller = player(context);
        DiceEntity dice = thrown(context, roller, with(new ItemStack(ModItems.DEFAULT_DICE), DiceModules.CHOICE, 1), DICE);
        context.assertEquals(DicePrompts.pending(roller).options().size(), 10, "a plain die: 1 to 10");
        DiceEntity quick = thrown(context, roller, with(die("dice_face_7", "dice_face_8"), DiceModules.CHOICE, 1), DICE.east());
        sequence(quick).setPromptTimeout(5);
        // The second prompt replaced the first one (one prompt per player): the first die took a face at random
        context.assertTrue(dice.isRollFinished(), "a prompt replaced by another answers itself");
        int value = dice.getOutcome().steps();
        context.assertTrue(value >= DiceEntity.MIN && value <= DiceEntity.MAX, "a face of the plain die: " + value);
        // Its prompt was asked with the usual time: ask it again with a short one
        DicePrompts.cancel(sequence(quick).prompt());
        when(context, quick::isRollFinished, 60, "the time is over: a face at random", () -> {
            context.assertTrue(Set.of(7, 8).contains(quick.getOutcome().steps()), "one of its faces");
            context.complete();
        });
    }

    // ---------------------------------------------------------------- Lucky

    /** Lucky x2: three results, the roller keeps one. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void luckyRollsMoreAndTheRollerKeepsOne(TestContext context) {
        ServerPlayerEntity roller = player(context);
        ItemStack die = with(die("dice_face_1", "dice_face_2", "dice_face_3", "dice_face_4", "dice_face_5", "dice_face_6"), DiceModules.LUCKY, 2);
        DiceEntity dice = thrown(context, roller, die, DICE);
        context.assertTrue(DicePrompts.pending(roller) == null, "nothing asked while it rolls");
        hit(context, dice, roller);
        DiceRollSequence sequence = sequence(dice);
        context.assertEquals(sequence.phase(), Phase.PICKING, "the roller picks");
        context.assertEquals(sequence.results().size(), 3, "1 + 2 results");
        DicePrompts.Prompt prompt = DicePrompts.pending(roller);
        context.assertTrue(prompt != null && prompt.options().size() == 3, "the three results are offered");
        context.assertTrue(!dice.isRollFinished() && dice.getOutcome() == DiceOutcome.NONE, "the roll is not final yet");
        int kept = sequence.results().get(1).getFirst().value();
        DicePrompts.answer(roller, prompt.id(), 1);
        context.assertTrue(dice.isRollFinished(), "the roll is final");
        context.assertEquals(dice.getOutcome().steps(), kept, "the result kept");
        context.complete();
    }

    /** No answer: the highest number. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void luckyTimesOutToTheBestResult(TestContext context) {
        DiceFace two = new DiceFace(Kind.NORMAL, 2), nine = new DiceFace(Kind.NORMAL, 9);
        DiceFace coin = new DiceFace(Kind.COIN, 5), debt = new DiceFace(Kind.DEBT, 5);
        context.assertEquals(DiceRollSequence.best(List.of(List.of(two), List.of(nine), List.of(coin))), 1, "the highest number");
        context.assertEquals(DiceRollSequence.best(List.of(List.of(debt), List.of(coin))), 1, "else the most coins");
        context.assertEquals(DiceRollSequence.best(List.of(List.of(two, two), List.of(nine))), 1, "sets are compared by their total");

        ServerPlayerEntity roller = player(context);
        DiceEntity dice = thrown(context, roller, with(die("dice_face_1", "dice_face_8"), DiceModules.LUCKY, 5), DICE);
        sequence(dice).setPromptTimeout(5);
        hit(context, dice, roller);
        context.assertEquals(sequence(dice).results().size(), 6, "1 + 5 results");
        int best = sequence(dice).results().stream().mapToInt(set -> set.getFirst().value()).max().orElse(0);
        when(context, dice::isRollFinished, 40, "the time is over", () -> {
            context.assertEquals(dice.getOutcome().steps(), best, "the best result is kept");
            context.complete();
        });
    }

    // ---------------------------------------------------------------- Reroll

    /** Reroll x2: keep or roll again, twice at most; the last roll is final. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void rerollIsStopOrGoOn(TestContext context) {
        ServerPlayerEntity roller = player(context);
        DiceEntity dice = thrown(context, roller, with(die("dice_face_2", "dice_face_7"), DiceModules.REROLL, 2), DICE);
        hit(context, dice, roller);
        DiceRollSequence sequence = sequence(dice);
        context.assertEquals(sequence.phase(), Phase.DECIDING, "keep or roll again?");
        context.assertEquals(sequence.rerollsLeft(), 2, "two rerolls");
        context.assertTrue(!dice.isRolling() && !dice.isRollFinished(), "the result is shown, not final");
        DicePrompts.Prompt first = DicePrompts.pending(roller);
        context.assertEquals(first.options().size(), 2, "keep / roll again");
        context.assertEquals(first.defaultIndex(), 0, "no answer: kept");
        DicePrompts.answer(roller, first.id(), 1);
        context.assertTrue(dice.isRolling() && sequence.phase() == Phase.ROLLING && sequence.rerollsLeft() == 1, "it rolls again");
        when(context, () -> sequence.phase() == Phase.DECIDING, DiceRollSequence.REROLL_SPIN_TICKS + 10, "the reroll stops by itself", () -> {
            DicePrompts.answer(roller, DicePrompts.pending(roller).id(), 1);
            context.assertEquals(sequence.rerollsLeft(), 0, "the last reroll");
            when(context, dice::isRollFinished, DiceRollSequence.REROLL_SPIN_TICKS + 10, "the last roll is final", () -> {
                context.assertTrue(DicePrompts.pending(roller) == null, "nothing more is asked");
                context.assertTrue(Set.of(2, 7).contains(dice.getOutcome().steps()), "a face of the die");
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void rerollKeepsTheResult(TestContext context) {
        ServerPlayerEntity roller = player(context);
        DiceEntity dice = thrown(context, roller, with(die("dice_face_4"), DiceModules.REROLL, 1), DICE);
        hit(context, dice, roller);
        DicePrompts.answer(roller, DicePrompts.pending(roller).id(), 0);
        context.assertTrue(dice.isRollFinished() && dice.getOutcome().steps() == 4, "kept: the roll is final");

        // No answer: kept too
        DiceEntity other = thrown(context, roller, with(die("dice_face_6"), DiceModules.REROLL, 1), DICE.east());
        sequence(other).setPromptTimeout(5);
        hit(context, other, roller);
        when(context, other::isRollFinished, 40, "the time is over: kept", () -> {
            context.assertEquals(other.getOutcome().steps(), 6, "the result shown");
            context.complete();
        });
    }

    // ---------------------------------------------------------------- combinations

    /** Choice decides everything: Slow, Lucky and Reroll do nothing on that die. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void choiceOverridesSlowLuckyAndReroll(TestContext context) {
        ServerPlayerEntity roller = player(context);
        ItemStack die = with(with(with(with(die("dice_face_3", "dice_face_9"), DiceModules.CHOICE, 1), DiceModules.SLOW, 1),
                DiceModules.LUCKY, 3), DiceModules.REROLL, 3);
        DiceEntity dice = thrown(context, roller, die, DICE);
        context.assertTrue(!dice.isFaceShown(), "not turning slowly");
        DicePrompts.Prompt prompt = DicePrompts.pending(roller);
        context.assertEquals(prompt.options().size(), 2, "the faces of the die are offered");
        DicePrompts.answer(roller, prompt.id(), 1);
        context.assertTrue(dice.isRollFinished() && dice.getOutcome().steps() == 9, "the face picked is final");
        context.assertTrue(DicePrompts.pending(roller) == null, "no Lucky pick, no reroll");
        context.complete();
    }

    /** Slow + Lucky: each roll is stopped by hand, then the roller keeps one; Reroll comes last and redoes it all. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void slowAppliesToEachLuckyRollThenRerollRedoesTheSet(TestContext context) {
        ServerPlayerEntity roller = player(context);
        ItemStack die = with(with(with(die("dice_face_1", "dice_face_2", "dice_face_3"), DiceModules.SLOW, 1), DiceModules.LUCKY, 1), DiceModules.REROLL, 1);
        DiceEntity dice = thrown(context, roller, die, DICE);
        DiceRollSequence sequence = sequence(dice);
        hit(context, dice, roller);
        context.assertTrue(sequence.phase() == Phase.ROLLING && sequence.results().size() == 1 && dice.isRolling(), "first result, it goes on turning");
        context.assertTrue(DicePrompts.pending(roller) == null, "nothing asked yet");
        context.waitAndRun(DiceRollSequence.SLOW_FACE_TICKS, () -> {
            hit(context, dice, roller);
            context.assertTrue(sequence.phase() == Phase.PICKING && sequence.results().size() == 2, "second result: the roller picks");
            DicePrompts.answer(roller, DicePrompts.pending(roller).id(), 0);
            context.assertEquals(sequence.phase(), Phase.DECIDING, "then keep or roll again");
            DicePrompts.answer(roller, DicePrompts.pending(roller).id(), 1);
            context.assertTrue(sequence.phase() == Phase.ROLLING && sequence.results().isEmpty() && dice.isFaceShown(), "everything is rolled again, slowly");
            context.waitAndRun(DiceRollSequence.REROLL_SPIN_TICKS + 10, () -> {
                context.assertTrue(dice.isRolling(), "a Slow reroll waits for its roller");
                hit(context, dice, roller);
                hit(context, dice, roller);
                context.assertEquals(sequence.phase(), Phase.PICKING, "two results again");
                DicePrompts.answer(roller, DicePrompts.pending(roller).id(), 1);
                context.assertTrue(dice.isRollFinished(), "no reroll left: final");
                context.complete();
            });
        });
    }

    // ---------------------------------------------------------------- back to the roller, or spent (Power-up)

    /** Outside a party, a die goes back to its roller after the roll, with its faces and modules. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void aPlainDieComesBackToItsRoller(TestContext context) {
        ServerPlayerEntity roller = player(context);
        roller.changeGameMode(GameMode.SURVIVAL);
        ItemStack die = with(die("dice_face_4"), DiceModules.LUCKY, 1);
        DiceEntity dice = thrown(context, roller, die, DICE);
        hit(context, dice, roller);
        DicePrompts.answer(roller, DicePrompts.pending(roller).id(), 0);
        when(context, dice::isRemoved, 100, "the die goes away once seen", () -> {
            List<ItemStack> held = roller.getInventory().main.stream().filter(stack -> stack.isOf(ModItems.DEFAULT_DICE)).toList();
            context.assertEquals(held.size(), 1, "the die came back");
            context.assertTrue(ItemStack.areItemsAndComponentsEqual(held.getFirst(), die) && held.getFirst().getCount() == 1,
                    "the same die, with its faces and modules");
            context.complete();
        });
    }

    /** A die carrying the Power-up module is spent once rolled, outside a party too. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void aPowerUpDieIsSpent(TestContext context) {
        ServerPlayerEntity roller = player(context);
        roller.changeGameMode(GameMode.SURVIVAL);
        DiceEntity dice = thrown(context, roller, with(die("dice_face_4"), DiceModules.POWER_UP, 1), DICE);
        hit(context, dice, roller);
        when(context, dice::isRemoved, 100, "the die goes away", () -> {
            context.assertTrue(roller.getInventory().main.stream().noneMatch(stack -> stack.isOf(ModItems.DEFAULT_DICE)), "it is spent");
            context.complete();
        });
    }

    /** A creative roller kept their die when throwing it: none is given back (no copies piling up). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void aCreativeRollerGetsNoCopyBack(TestContext context) {
        ServerPlayerEntity roller = player(context);
        DiceEntity dice = thrown(context, roller, die("dice_face_4"), DICE);
        hit(context, dice, roller);
        hit(context, dice, roller);
        context.assertTrue(dice.isRemoved(), "gone");
        context.assertTrue(roller.getInventory().main.stream().noneMatch(stack -> stack.isOf(ModItems.DEFAULT_DICE)), "no copy given");
        context.complete();
    }

    /** A rolled die hit again goes away at once (back to its roller); it is not rolled twice. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void aRolledDieIsNotRolledAgain(TestContext context) {
        ServerPlayerEntity roller = player(context);
        roller.changeGameMode(GameMode.SURVIVAL);
        ItemStack die = die("dice_face_4");
        DiceEntity dice = thrown(context, roller, die, DICE);
        hit(context, dice, roller);
        context.assertTrue(dice.isRollFinished() && !dice.isRemoved(), "rolled, shown for a moment");
        hit(context, dice, roller);
        context.assertTrue(dice.isRemoved(), "hit again: it goes away");
        context.assertTrue(roller.getInventory().main.stream().anyMatch(stack -> ItemStack.areItemsAndComponentsEqual(stack, die)), "back to its roller");
        context.complete();
    }

    /** A replay gives a spent Power-up die back, but not a plain die: it comes back by itself. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void aPlainDieIsNotGivenBackTwiceByAReplay(TestContext context) {
        path(context, 2, 0, new ItemStack(ModItems.REPLAY_CARTRIDGE));
        ServerPlayerEntity roller = player(context);
        roller.changeGameMode(GameMode.SURVIVAL);
        PigEntity pig = token(context, PATH.get(0), roller.getUuid());
        PartyControllerEntity controller = party(context, roller.getUuid(), pig);
        TokenTurnPartyStep turn = (TokenTurnPartyStep) controller.getPartyData().getCurrentStep();
        DiceEntity dice = context.spawnEntity(ModEntities.DICE_ENTITY, DICE);
        dice.setItemReference(die("dice_face_4"));
        turn.onDiceRoll(dice, roller.getUuid(), 4, controller);
        dice.discard();
        context.assertTrue(turn.grantReplay(controller) != null, "a replay is granted");
        context.assertTrue(roller.getInventory().main.stream().noneMatch(stack -> stack.isOf(ModItems.DEFAULT_DICE)), "no die given back by the replay");
        context.complete();
    }

    // ---------------------------------------------------------------- Reversed

    /** The token walks back the way it came, and the coins of the roll change sign. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = BATCH)
    public void reversedWalksBackwardAndFlipsTheCoins(TestContext context) {
        path(context, 6, -1, null);
        ServerPlayerEntity roller = player(context);
        PigEntity pig = token(context, PATH.get(4), roller.getUuid());
        PartyControllerEntity controller = party(context, roller.getUuid(), pig);
        roller.getInventory().setStack(2, new ItemStack(ModItems.COIN, 9));
        context.waitAndRun(2, () -> {
            DiceEntity coins = thrown(context, roller, with(die("coin_dice_face_4"), DiceModules.REVERSED, 1), DICE);
            context.assertTrue(coins.isRolling(), "thrown");
            // Not hit: only read what it would do
            coins.discard();
            DiceOutcome flipped = DiceModules.REVERSED.modifyOutcome(DiceOutcome.of(List.of(new DiceFace(Kind.COIN, 4))), 1);
            context.assertEquals(flipped.coins(), -4, "+4 coins become -4");
            context.assertEquals(DiceModules.REVERSED.modifyOutcome(DiceOutcome.of(List.of(new DiceFace(Kind.DEBT, 4))), 1).coins(), 4, "-4 become +4");

            DiceEntity dice = thrown(context, roller, with(die("dice_face_3"), DiceModules.REVERSED, 1), PATH.get(4));
            hit(context, dice, roller);
            context.assertEquals(dice.getOutcome().steps(), -3, "3 becomes 3 steps backward");
            PartyLiveData live = PartyLiveData.capture(controller, context.getWorld());
            context.assertEquals(live.roll(), -3, "the HUD shows a roll going backward");
            when(context, turnEnded(controller), 250, "the turn ends", () -> {
                assertOn(context, pig, PATH.get(1), "3 spaces back");
                context.assertTrue(!AdvanceBackMoves.isExtraMove(pig), "an ordinary move, not a tile's extra move");
                context.assertEquals(roller.getInventory().getStack(2).getCount(), 9, "no coin involved");
                context.complete();
            });
        });
    }

    /** Nowhere to go back to: the turn ends where the token stands. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void reversedWithNoWayBackEndsTheTurn(TestContext context) {
        path(context, 3, -1, null);
        ServerPlayerEntity roller = player(context);
        PigEntity pig = token(context, PATH.get(0), roller.getUuid());
        PartyControllerEntity controller = party(context, roller.getUuid(), pig);
        context.waitAndRun(2, () -> {
            DiceRollEffects.resolve(context.getWorld(), pig, roller.getUuid(), DiceOutcome.ofSteps(-4), 1, 10);
            when(context, turnEnded(controller), 100, "the turn ends", () -> {
                assertOn(context, pig, PATH.get(0), "it did not move");
                context.complete();
            });
        });
    }

    // ---------------------------------------------------------------- Skeleton Key

    /** A Stop space halts a token; with the Skeleton Key it walks through. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = BATCH)
    public void skeletonKeyWalksThroughStopSpaces(TestContext context) {
        path(context, 5, 1, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP));
        ServerPlayerEntity roller = player(context), other = player(context);
        PigEntity halted = token(context, PATH.get(0), other.getUuid());
        PigEntity keyed = token(context, PATH.get(0), roller.getUuid());
        context.waitAndRun(2, () -> {
            DiceEntity plain = thrown(context, other, die("dice_face_3"), PATH.get(0));
            hit(context, plain, other);
            DiceEntity key = thrown(context, roller, with(die("dice_face_3"), DiceModules.SKELETON_KEY, 1), PATH.get(0));
            hit(context, key, roller);
            context.assertTrue(DiceRollEffects.ignoresStops(keyed) && !DiceRollEffects.ignoresStops(halted), "the key is on the roller's token only");
            when(context, () -> isOn(context, keyed, PATH.get(3)), 200, "the keyed token walks its 3 steps", () -> {
                assertOn(context, halted, PATH.get(1), "the other one was halted by the Stop space");
                context.assertTrue(!DiceRollEffects.ignoresStops(keyed), "the key lasts one move");
                context.complete();
            });
        });
    }

    // ---------------------------------------------------------------- Homing

    /** At a fork the token takes a branch by itself: nobody is asked. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = BATCH)
    public void homingTakesABranchByItself(TestContext context) {
        BlockPos start = PATH.get(0), fork = PATH.get(1), a = PATH.get(2), b = new BlockPos(3, 1, 3);
        tile(context, start, plain(), fork);
        tile(context, fork, plain(), a, b);
        tile(context, a, plain());
        tile(context, b, plain());
        ServerPlayerEntity roller = player(context);
        PigEntity pig = token(context, start, roller.getUuid());
        Box around = new Box(context.getAbsolutePos(fork)).expand(4);
        context.waitAndRun(2, () -> {
            DiceEntity dice = thrown(context, roller, with(die("dice_face_2"), DiceModules.HOMING, 1), start);
            hit(context, dice, roller);
            when(context, () -> isOn(context, pig, a) || isOn(context, pig, b), 200, "it reaches one of the branches", () -> {
                context.assertTrue(context.getWorld().getEntitiesByClass(DirectionDisplayEntity.class, around, e -> true).isEmpty(),
                        "no direction was asked at the fork");
                context.complete();
            });
        });
    }

    // ---------------------------------------------------------------- Double / Triple Dice

    /** {@code roller} throws a Double / Triple Dice ({@code multi}) carrying the faces and modules of {@code die}. */
    private static DiceEntity thrownMulti(TestContext context, ServerPlayerEntity roller, Item multi, ItemStack die) {
        ItemStack stack = new ItemStack(multi);
        if (die.get(DiceFacesComponent.TYPE) != null) stack.set(DiceFacesComponent.TYPE, die.get(DiceFacesComponent.TYPE));
        stack = DiceModules.set(stack, DiceModules.of(die));
        roller.setStackInHand(Hand.MAIN_HAND, stack);
        Box around = roller.getBoundingBox().expand(8);
        Set<DiceEntity> before = new HashSet<>(context.getWorld().getEntitiesByClass(DiceEntity.class, around, e -> true));
        stack.use(context.getWorld(), roller, Hand.MAIN_HAND);
        List<DiceEntity> dice = context.getWorld().getEntitiesByClass(DiceEntity.class, around, e -> !before.contains(e));
        for (DiceEntity die1 : dice) atEnd(context, () -> {
            if (!die1.isRemoved()) die1.discard();
        });
        context.assertTrue(!dice.isEmpty(), "the dice are thrown");
        return dice.getFirst().lead();
    }

    /** Answers the pending prompt of {@code roller} with the face {@code face} of {@code die}. */
    private static void pick(TestContext context, ServerPlayerEntity roller, ItemStack die, DiceFace face, String what) {
        DicePrompts.Prompt prompt = DicePrompts.pending(roller);
        context.assertTrue(prompt != null, "asked: " + what);
        context.assertTrue(DicePrompts.answer(roller, prompt.id(), faces(die).indexOf(face)), "answered: " + what);
    }

    /** Choice on a Double Dice: the roller picks the face of each of its two dice, one after the other. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void choicePicksEachDieOfADoubleDice(TestContext context) {
        ServerPlayerEntity roller = player(context);
        ItemStack die = with(die("dice_face_1", "dice_face_4", "coin_dice_face_3"), DiceModules.CHOICE, 1);
        DiceEntity dice = thrownMulti(context, roller, ModItems.DOUBLE_DICE, die);
        context.assertEquals(dice.group().size(), 2, "two dice");
        pick(context, roller, die, new DiceFace(Kind.NORMAL, 4), "the first die");
        context.assertTrue(!dice.isRollFinished(), "the second die is still to be picked");
        context.assertTrue(dice.group().get(1).isRolling(), "the second die still turns");
        DicePrompts.Prompt second = DicePrompts.pending(roller);
        context.assertTrue(second != null && second.steps() == 2 && second.picked().size() == 1,
                "the second picker shows both dice, the first one picked");
        pick(context, roller, die, new DiceFace(Kind.COIN, 3), "the second die");
        context.assertTrue(dice.isRollFinished(), "both picked: the roll is final");
        context.assertEquals(dice.getRolledFaces(), List.of(new DiceFace(Kind.NORMAL, 4), new DiceFace(Kind.COIN, 3)), "the faces picked");
        context.assertEquals(dice.group().get(1).getRolledFace(), new DiceFace(Kind.COIN, 3), "the second die shows its face");
        context.assertEquals(dice.getOutcome().steps(), 4, "4 steps");
        context.assertEquals(dice.getOutcome().coins(), 3, "and 3 coins");
        context.assertTrue(DicePrompts.pending(roller) == null, "nothing else is asked");
        context.complete();
    }

    /** Choice on a Triple Dice: three picks; a double picked is a double. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void choicePicksEachDieOfATripleDice(TestContext context) {
        ServerPlayerEntity roller = player(context);
        ItemStack die = with(new ItemStack(ModItems.DEFAULT_DICE), DiceModules.CHOICE, 1);
        DiceEntity dice = thrownMulti(context, roller, ModItems.TRIPLE_DICE, die);
        context.assertEquals(dice.group().size(), 3, "three dice");
        pick(context, roller, die, new DiceFace(Kind.NORMAL, 2), "the first die");
        pick(context, roller, die, new DiceFace(Kind.NORMAL, 7), "the second die");
        context.assertTrue(!dice.isRollFinished(), "the third die is still to be picked");
        pick(context, roller, die, new DiceFace(Kind.NORMAL, 7), "the third die");
        context.assertTrue(dice.isRollFinished(), "all three picked");
        context.assertEquals(dice.getRolledFaces(), List.of(new DiceFace(Kind.NORMAL, 2), new DiceFace(Kind.NORMAL, 7),
                new DiceFace(Kind.NORMAL, 7)), "the faces picked, in order");
        context.assertEquals(dice.getOutcome().steps(), 16, "2 + 7 + 7");
        context.complete();
    }

    /** Choice with Reversed and Lucky on a Double Dice: both faces picked (no Lucky pick), the total is reversed. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void choiceOnADoubleDiceCombinesWithReversed(TestContext context) {
        ServerPlayerEntity roller = player(context);
        ItemStack die = with(with(with(new ItemStack(ModItems.DEFAULT_DICE), DiceModules.CHOICE, 1), DiceModules.REVERSED, 1),
                DiceModules.LUCKY, 2);
        DiceEntity dice = thrownMulti(context, roller, ModItems.DOUBLE_DICE, die);
        pick(context, roller, die, new DiceFace(Kind.NORMAL, 3), "the first die");
        pick(context, roller, die, new DiceFace(Kind.NORMAL, 5), "the second die");
        context.assertTrue(dice.isRollFinished(), "both picked");
        context.assertEquals(dice.getOutcome().steps(), -8, "3 + 5, reversed");
        context.assertTrue(DicePrompts.pending(roller) == null, "no Lucky pick");
        context.complete();
    }

    /** The tags of a badge line, in order. */
    private static List<String> tagKeys(Text line) {
        List<String> keys = new ArrayList<>();
        if (line.getContent() instanceof TranslatableTextContent t && t.getKey().startsWith("tooltip.steveparty.tag.")) keys.add(t.getKey());
        for (Text sibling : line.getSiblings()) keys.addAll(tagKeys(sibling));
        return keys;
    }
}
