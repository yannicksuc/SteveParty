package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.components.DiceFacesComponent.Kind;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.dice.DiceOutcome;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.dice.DiceRollSequence;
import fr.lordfinn.steveparty.dice.DiceRollSequence.Phase;
import fr.lordfinn.steveparty.dice.DiceThrow;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.events.DiceThrowRevealed;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.payloads.custom.DiceRevealPayload;
import fr.lordfinn.steveparty.service.TurnMoves;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static fr.lordfinn.steveparty.dice.DiceRollSequence.REVEAL_STEP_TICKS;
import static fr.lordfinn.steveparty.dice.DiceRollSequence.REVEAL_TOTAL_TICKS;
import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestAsserts.assertOn;
import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * The reveal of a throw: the dice of a Double / Triple Dice stop one after the other, a double / triple shows as soon
 * as they make it, then the total; {@link DiceThrowRevealed} is fired once, and only then the token moves. A single die
 * is revealed at once, as before.
 */
public class DiceRevealGameTests implements FabricGameTest {
    private static final String BATCH = "dice_reveal";
    private static final BlockPos DICE = new BlockPos(2, 1, 2);
    /** The throws revealed, by lead die. */
    private static final Map<UUID, List<DiceThrow>> REVEALED = new ConcurrentHashMap<>();

    static {
        DiceThrowRevealed.EVENT.register((lead, roller, diceThrow) ->
                REVEALED.computeIfAbsent(lead.getUuid(), uuid -> new ArrayList<>()).add(diceThrow));
    }

    private static List<DiceThrow> revealed(DiceEntity lead) {
        return REVEALED.getOrDefault(lead.getUuid(), List.of());
    }

    private static DiceRollSequence sequence(DiceEntity dice) {
        return dice.lead().sequence();
    }

    /** A Triple Dice of 3s: die 1 at once, die 2 (a double), die 3 (a triple), then the total; the event once. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void aTripleIsRevealedDieAfterDie(TestContext context) {
        ServerPlayerEntity player = player(context);
        List<DiceEntity> dice = thrownTogether(context, player, die("dice_face_3"), 3, DICE);
        DiceEntity lead = dice.getFirst();
        hit(context, dice.get(2), player); // any of them stops the throw
        DiceRollSequence sequence = sequence(lead);
        context.assertEquals(sequence.phase(), Phase.REVEALING, "the throw is being revealed");
        context.assertTrue(!dice.get(0).isRolling() && dice.get(1).isRolling() && dice.get(2).isRolling(), "the first die stops at once");
        context.assertEquals(dice.get(0).getRolledFace(), new DiceFace(Kind.NORMAL, 3), "on its face");
        context.assertTrue(!lead.isRollFinished() && lead.getOutcome() == DiceOutcome.NONE && revealed(lead).isEmpty(), "not final yet");
        hit(context, lead, player);
        context.assertTrue(!lead.isRemoved() && sequence.revealed() == 1, "a hit does not hurry the reveal");
        context.waitAndRun(REVEAL_STEP_TICKS - 3, () -> {
            context.assertTrue(sequence.revealed() == 1 && dice.get(1).isRolling(), "the second die still turns");
            context.waitAndRun(5, () -> {
                context.assertTrue(sequence.revealed() == 2 && !dice.get(1).isRolling() && dice.get(2).isRolling(), "then the second die stops");
                context.assertEquals(sequence.comboShown(), 2, "a double at once");
                context.assertTrue(dice.get(0).isGlowing() && dice.get(1).isGlowing() && !dice.get(2).isGlowing(), "the pair glows");
                context.waitAndRun(REVEAL_STEP_TICKS, () -> {
                    context.assertTrue(sequence.revealed() == 3 && dice.stream().noneMatch(DiceEntity::isRolling), "then the third");
                    context.assertEquals(sequence.comboShown(), 3, "a triple");
                    context.assertTrue(dice.stream().allMatch(DiceEntity::isGlowing), "the three glow");
                    context.assertTrue(!lead.isRollFinished() && revealed(lead).isEmpty(), "the total is still to come");
                    when(context, lead::isRollFinished, REVEAL_TOTAL_TICKS + 4, "the total", () -> {
                        context.assertEquals(lead.getOutcome().steps(), 9, "3 + 3 + 3");
                        List<DiceThrow> throwsRevealed = revealed(lead);
                        context.assertEquals(throwsRevealed.size(), 1, "the reveal is told once");
                        DiceThrow diceThrow = throwsRevealed.getFirst();
                        context.assertEquals(diceThrow.faces(), List.of(new DiceFace(Kind.NORMAL, 3), new DiceFace(Kind.NORMAL, 3),
                                new DiceFace(Kind.NORMAL, 3)), "with the faces, in order");
                        context.assertTrue(diceThrow.total() == 9 && diceThrow.isTriple() && diceThrow.isDouble(), "a triple of 9");
                        context.assertTrue(lead.getThrow() == diceThrow, "the die keeps it");
                        context.waitAndRun(10, () -> {
                            context.assertEquals(revealed(lead).size(), 1, "only once");
                            context.complete();
                        });
                    });
                });
            });
        });
    }

    /** A Double Dice of 5s: the double shows with the second die, the total comes after. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void aDoubleShowsWithTheSecondDie(TestContext context) {
        ServerPlayerEntity player = player(context);
        List<DiceEntity> dice = thrownTogether(context, player, die("dice_face_5"), 2, DICE);
        DiceEntity lead = dice.getFirst();
        hit(context, lead, player);
        DiceRollSequence sequence = sequence(lead);
        context.assertEquals(sequence.comboShown(), 0, "one die: no double yet");
        when(context, () -> sequence.revealed() == 2, REVEAL_STEP_TICKS + 4, "the second die", () -> {
            context.assertEquals(sequence.comboShown(), 2, "a double");
            context.assertTrue(!lead.isRollFinished(), "the total after a pause");
            when(context, lead::isRollFinished, REVEAL_TOTAL_TICKS + 4, "the total", () -> {
                DiceThrow diceThrow = lead.getThrow();
                context.assertTrue(diceThrow != null && diceThrow.isDouble() && !diceThrow.isTriple() && diceThrow.total() == 10, "a double of 10");
                context.complete();
            });
        });
    }

    /** A single die: revealed at once, as before; the event too. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void aSingleDieIsRevealedAtOnce(TestContext context) {
        ServerPlayerEntity player = player(context);
        DiceEntity dice = thrown(context, player, die("dice_face_4"), DICE);
        hit(context, dice, player);
        context.assertTrue(dice.isRollFinished() && !dice.isRolling(), "final at once");
        context.assertEquals(dice.getOutcome().steps(), 4, "a 4");
        context.assertEquals(revealed(dice).size(), 1, "the reveal is told");
        context.assertTrue(!revealed(dice).getFirst().isDouble(), "one die makes no double");
        context.complete();
    }

    /** The token walks only once the throw is revealed; its roll is a double for the Threshold obstacle too. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = BATCH)
    public void theTokenMovesAfterTheReveal(TestContext context) {
        path(context, 3, -1, null);
        ServerPlayerEntity player = player(context);
        PigEntity pig = token(context, PATH.get(0), player.getUuid());
        context.waitAndRun(2, () -> {
            List<DiceEntity> dice = thrownTogether(context, player, die("dice_face_1"), 2, PATH.get(0));
            DiceEntity lead = dice.getFirst();
            hit(context, lead, player);
            context.waitAndRun(REVEAL_STEP_TICKS + REVEAL_TOTAL_TICKS - 3, () -> {
                context.assertTrue(!lead.isRollFinished(), "still revealing");
                context.assertTrue(TurnMoves.rollOf(pig) == null && ((TokenizedEntityInterface) pig).steveparty$getNbSteps() == 0,
                        "the token waits for the total");
                assertOn(context, pig, PATH.get(0), "the token has not moved");
                when(context, lead::isRollFinished, 10, "the total", () -> {
                    TurnMoves.Roll roll = TurnMoves.rollOf(pig);
                    context.assertTrue(roll != null && roll.total() == 2 && roll.hasSame(2) && !roll.hasSame(3),
                            "the roll the board reads: 2, a double");
                    when(context, () -> isOn(context, pig, PATH.get(2)), 200, "the token walks 1 + 1", context::complete);
                });
            });
        });
    }

    /** Lucky on a Double Dice: the results stop together for the pick, then the one kept is revealed die by die. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void luckyOnADoubleIsRevealedAfterThePick(TestContext context) {
        ServerPlayerEntity roller = player(context);
        ItemStack die = with(die("dice_face_2", "dice_face_6"), DiceModules.LUCKY, 1);
        List<DiceEntity> dice = thrownTogether(context, roller, die, 2, DICE);
        DiceEntity lead = dice.getFirst();
        hit(context, lead, roller);
        DiceRollSequence sequence = sequence(lead);
        context.assertEquals(sequence.phase(), Phase.PICKING, "the roller picks a result");
        context.assertTrue(dice.stream().noneMatch(DiceEntity::isRolling), "the dice stopped for the pick");
        List<DiceFace> kept = sequence.results().get(1);
        DicePrompts.Prompt prompt = DicePrompts.pending(roller);
        context.assertTrue(prompt != null, "asked");
        DicePrompts.answer(roller, prompt.id(), 1);
        context.assertEquals(sequence.phase(), Phase.REVEALING, "the result kept is revealed");
        context.assertTrue(!dice.get(0).isRolling() && dice.get(1).isRolling(), "die after die");
        when(context, lead::isRollFinished, REVEAL_STEP_TICKS + REVEAL_TOTAL_TICKS + 4, "the total", () -> {
            context.assertEquals(lead.getRolledFaces(), kept, "the result kept");
            context.assertEquals(revealed(lead).size(), 1, "told once");
            context.complete();
        });
    }

    /** Double and triple read the numbers, as the Threshold obstacle; the reveal goes to the clients whole. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void doublesAndTriplesReadTheNumbers(TestContext context) {
        DiceFace five = new DiceFace(Kind.NORMAL, 5), premiumFive = new DiceFace(Kind.PREMIUM, 5), two = new DiceFace(Kind.NORMAL, 2);
        DiceFace coin = new DiceFace(Kind.COIN, 3), zero = new DiceFace(Kind.NORMAL, 0), blank = new DiceFace(Kind.BLANK, 0);
        context.assertTrue(throwOf(five, premiumFive).isDouble(), "5 and 5★: a double");
        context.assertTrue(!throwOf(five, two).isDouble(), "5 and 2: none");
        context.assertTrue(!throwOf(coin, coin).isDouble(), "two coin faces: no number");
        context.assertTrue(!throwOf(zero, zero).isDouble() && !throwOf(blank, blank).isDouble(), "0 and blank walk nothing");
        context.assertTrue(throwOf(two, five, two).isDouble() && !throwOf(two, five, two).isTriple(), "2, 5, 2: a double");
        context.assertTrue(throwOf(two, two, two).isTriple(), "2, 2, 2: a triple");
        context.assertEquals(throwOf(two, five, five).sameNumber(), 5, "the double's number");
        for (List<DiceFace> faces : List.of(List.of(five, premiumFive), List.of(five, two, coin), List.of(two, two, two))) {
            DiceThrow diceThrow = new DiceThrow(faces, DiceOutcome.of(faces));
            TurnMoves.Roll roll = new TurnMoves.Roll(diceThrow.total(), DiceThrow.numbers(faces), null);
            context.assertTrue(roll.hasSame(2) == diceThrow.isDouble() && roll.hasSame(3) == diceThrow.isTriple(),
                    "the Threshold obstacle reads the same double / triple: " + faces);
        }
        DiceRevealPayload payload = DiceRevealPayload.of(42, UUID.randomUUID(), "LordFinn", 3, List.of(two, two), 2, 2, Text.literal("11"));
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
        DiceRevealPayload.CODEC.encode(buf, payload);
        DiceRevealPayload back = DiceRevealPayload.CODEC.decode(buf);
        context.assertTrue(back.throwId() == 42 && back.roller().equals(payload.roller()) && back.name().equals("LordFinn")
                && back.dice() == 3 && back.faces().equals(payload.faces()) && back.combo() == 2 && back.number() == 2
                && back.total().map(Text::getString).orElse("").equals("11"), "the reveal reaches the clients whole");
        context.complete();
    }

    private static DiceThrow throwOf(DiceFace... faces) {
        List<DiceFace> list = List.of(faces);
        return new DiceThrow(list, DiceOutcome.of(list));
    }
}
