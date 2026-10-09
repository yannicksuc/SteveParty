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
import fr.lordfinn.steveparty.hud.DiceRevealLayout;
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

    // ---------------------------------------------------------------- the HUD's grid

    /** A font like Minecraft's: 6 pixels a character (a space 4, « ! » 2), the trailing pixel included. */
    private static final DiceRevealLayout.Measure FONT = text -> {
        int width = 0;
        for (char c : text.toCharArray()) width += c == ' ' ? 4 : c == '!' ? 2 : 6;
        return width;
    };

    /**
     * The reveal's row on a fixed grid, in every state of 1, 2 and 3 dice, wide special faces and a long name: the dice
     * never move nor change width, the same gutter around « + » and « = », the same gap before the badge (after the
     * last die or the total), room inside the plate and the pills, and the row never wider than reserved.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void theRevealIsLaidOutOnAFixedGrid(TestContext context) {
        List<List<String>> throwsToShow = List.of(List.of("4"), List.of("+10¢", "−10¢"), List.of("3", "3", "10★"),
                List.of("10★", "10★", "10★"));
        for (String name : List.of("LordFinn :", "Maximilien_Dupont :")) {
            for (List<String> faces : throwsToShow) {
                int dice = faces.size();
                String total = dice == 1 ? faces.getFirst() : "13";
                int reserved = DiceRevealLayout.reserved(FONT, name, dice, faces, total, List.of("Double !", "Triple !"));
                List<DiceRevealLayout.Box> first = null;
                // Each die stopping (a double showing from the second one on), then the total
                for (int shown = dice == 1 ? 1 : 0; shown <= dice + 1; shown++) {
                    List<String> revealed = faces.subList(0, Math.min(shown, dice));
                    long same = revealed.stream().filter(face -> face.equals(revealed.getFirst())).count();
                    String combo = dice < 2 ? null : same >= 3 ? "Triple !" : same == 2 ? "Double !" : null;
                    String state = name + " " + faces + " " + shown;
                    List<DiceRevealLayout.Box> boxes = DiceRevealLayout.layout(FONT, name, dice, revealed, shown > dice || dice == 1 ? total : null, combo);
                    checkRow(context, boxes, name, state);
                    context.assertTrue(DiceRevealLayout.width(boxes) <= reserved, "never wider than reserved: " + state);
                    if (first == null) first = boxes;
                    for (int i = 0; i < first.size(); i++) {
                        DiceRevealLayout.Box was = first.get(i);
                        if (was.kind() != DiceRevealLayout.Kind.PLATE && was.kind() != DiceRevealLayout.Kind.DIE
                                && was.kind() != DiceRevealLayout.Kind.PLUS) continue;
                        context.assertTrue(boxes.contains(was), "the plate and the dice don't move: " + state);
                    }
                }
            }
        }
        context.complete();
    }

    private static void checkRow(TestContext context, List<DiceRevealLayout.Box> boxes, String name, String state) {
        int dieWidth = -1;
        for (int i = 0; i < boxes.size(); i++) {
            DiceRevealLayout.Box box = boxes.get(i);
            switch (box.kind()) {
                case PLATE -> context.assertTrue(box.width() - DiceRevealLayout.PLATE_TEXT_X - (FONT.width(name) - 1) >= 3,
                        "room after the name: " + state);
                case DIE -> {
                    if (dieWidth < 0) dieWidth = box.width();
                    context.assertTrue(box.width() == dieWidth, "every die the same width: " + state);
                    context.assertTrue(box.width() - (FONT.width("−10¢") - 1) >= 2 * 3, "room inside a die: " + state);
                }
                case TOTAL, BADGE -> context.assertTrue(box.width() >= 2 * 3 + FONT.width("00") - 1, "room inside a pill: " + state);
                default -> {
                }
            }
            if (i == 0) continue;
            DiceRevealLayout.Box before = boxes.get(i - 1);
            int gap = box.x() - before.right();
            int expected = before.kind() == DiceRevealLayout.Kind.PLATE ? DiceRevealLayout.PLATE_GAP
                    : box.kind() == DiceRevealLayout.Kind.BADGE ? DiceRevealLayout.BADGE_GAP : DiceRevealLayout.GUTTER;
            context.assertTrue(gap == expected, "gap " + gap + " before " + box.kind() + " (" + expected + " expected): " + state);
        }
    }

    private static DiceThrow throwOf(DiceFace... faces) {
        List<DiceFace> list = List.of(faces);
        return new DiceThrow(list, DiceOutcome.of(list));
    }
}
