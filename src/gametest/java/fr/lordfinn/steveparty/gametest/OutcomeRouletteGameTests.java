package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.dice.CursedRolls;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.hud.OutcomeRoulette;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.MistigriCartridgeItem;
import fr.lordfinn.steveparty.payloads.custom.OutcomeRoulettePayload;
import fr.lordfinn.steveparty.service.MistigriSentences;
import fr.lordfinn.steveparty.service.MistigriSentences.Sentence;
import fr.lordfinn.steveparty.service.OutcomeRoulettes;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestAsserts.count;
import static fr.lordfinn.steveparty.gametest.kit.TestCleanup.atEnd;
import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * The outcome roulette, and the Mistigri space's: the light always stops on the line the server drew (its timing the
 * same on every side), the sentence applied is the one the roulette stopped on, it only happens once the roulette is
 * over, and every player of the party is sent it, wherever they are.
 */
public class OutcomeRouletteGameTests implements SteveGameTest {
    private static final String BATCH = "outcome_roulette";
    private static final BlockPos TILE = new BlockPos(3, 1, 3);
    private static final int WHOLE = MistigriSentences.WHOLE + 20;

    /** A party of these players (a token each, the first one's turn), gold ingots as coins, emeralds as stars. */
    private static PartyControllerEntity party(TestContext context, List<MobEntity> tokens, ServerPlayerEntity... players) {
        for (int i = 0; i < players.length; i++) tokens.add(token(context, new BlockPos(1 + 2 * i, 1, 1), players[i].getUuid()));
        PartyControllerEntity controller = DiceTestKit.party(context, players[0].getUuid(), tokens.toArray(MobEntity[]::new));
        context.assertTrue(controller.setCurrency(PartyCurrency.COIN, new ItemStack(Items.GOLD_INGOT)), "coins: gold ingots");
        context.assertTrue(controller.setCurrency(PartyCurrency.STAR, new ItemStack(Items.EMERALD)), "stars: emeralds");
        return controller;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void theLightStopsOnTheResult(TestContext context) {
        for (int lines = 1; lines <= OutcomeRoulette.MAX_LINES; lines++) {
            for (int result = 0; result < lines; result++) {
                int steps = OutcomeRoulette.steps(lines, result);
                double[] times = OutcomeRoulette.stepTimes(steps);
                context.assertTrue(steps >= OutcomeRoulette.MIN_STEPS && steps < OutcomeRoulette.MIN_STEPS + lines, "a few rounds");
                context.assertEquals(OutcomeRoulette.litLine(lines, times, OutcomeRoulette.REVEAL_TICKS + OutcomeRoulette.SPIN_TICKS), result,
                        "stops on " + result + " of " + lines);
                context.assertEquals(OutcomeRoulette.litLine(lines, times, OutcomeRoulette.TOTAL_TICKS - 1), result, "held on it");
                context.assertEquals(OutcomeRoulette.litLine(lines, times, OutcomeRoulette.REVEAL_TICKS - 1), -1, "nothing lit while read");
                context.assertEquals(OutcomeRoulette.litLine(lines, times, OutcomeRoulette.REVEAL_TICKS), 0, "starts at the top");
                context.assertTrue(Math.abs(times[steps - 1] - OutcomeRoulette.SPIN_TICKS) < 1.0E-6, "the last step ends the spin");
                for (int k = 1; k < steps; k++) {
                    double gap = times[k] - times[k - 1], before = k == 1 ? times[0] : times[k - 1] - times[k - 2];
                    context.assertTrue(gap >= before - 1.0E-6, "slower and slower");
                }
                double last = times[steps - 1] - times[steps - 2];
                context.assertTrue(last >= 9 && last <= 16, "the last steps about 0.6 s apart (" + last + " ticks)");
            }
        }
        // Sent whole
        OutcomeRoulettePayload payload = new OutcomeRoulettePayload(7, Optional.of(UUID.randomUUID()), Optional.of(new Vec3d(1.5, 70.2, -3)),
                Text.literal("LordFinn"), Text.literal("?"), List.of(new OutcomeRoulettePayload.Line(new ItemStack(Items.GOLD_INGOT),
                Text.literal("-10"), OutcomeRoulettePayload.Tone.BAD, 30), new OutcomeRoulettePayload.Line(ItemStack.EMPTY,
                Text.literal("+1"), OutcomeRoulettePayload.Tone.GOOD, -1)), 1, 21, 15);
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
        OutcomeRoulettePayload.CODEC.encode(buf, payload);
        OutcomeRoulettePayload back = OutcomeRoulettePayload.CODEC.decode(buf);
        context.assertTrue(back.id() == 7 && back.trigger().equals(payload.trigger()) && back.anchor().equals(payload.anchor())
                && back.lines().size() == 2 && back.lines().getFirst().icon().isOf(Items.GOLD_INGOT)
                && back.lines().getFirst().chance() == 30 && back.lines().get(1).tone() == OutcomeRoulettePayload.Tone.GOOD
                && back.lines().get(1).text().getString().equals("+1") && back.result() == 1 && back.steps() == 21
                && back.elapsed() == 15, "the roulette reaches the clients whole");
        context.assertTrue(OutcomeRoulettePayload.CODEC.decode(encoded(context, OutcomeRoulettePayload.stop(7))).isStop(), "a stop");
        context.complete();
    }

    private static RegistryByteBuf encoded(TestContext context, OutcomeRoulettePayload payload) {
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
        OutcomeRoulettePayload.CODEC.encode(buf, payload);
        return buf;
    }

    /** Two sentences possible, drawn by the server: the one applied is the one the roulette stopped on. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void theSentenceIsTheOneAnnounced(TestContext context) {
        ServerPlayerEntity player = player(context);
        atEnd(context, () -> player.removeCommandTag(CursedRolls.TAG));
        List<MobEntity> tokens = new java.util.ArrayList<>();
        PartyControllerEntity party = party(context, tokens, player);
        InventoryUtils.giveOrDrop(player, new ItemStack(Items.GOLD_INGOT), 30);
        ItemStack cartridge = new ItemStack(ModItems.MISTIGRI_CARTRIDGE);
        for (Sentence sentence : Sentence.values()) MistigriCartridgeItem.setWeight(cartridge, sentence, 0);
        MistigriCartridgeItem.setWeight(cartridge, Sentence.COINS_SMALL, 1);
        MistigriCartridgeItem.setWeight(cartridge, Sentence.CURSED, 1);
        BoardSpaceBlockEntity tile = tile(context, TILE, cartridge);
        MobEntity token = tokens.getFirst();
        boolean[] done = {false};
        context.assertTrue(MistigriSentences.start(context.getWorld(), tile.getPos(), token, party, cartridge, null,
                () -> done[0] = true) == MistigriSentences.Start.STARTED, "started");
        Sentence drawn = MistigriSentences.sentence(token);
        when(context, () -> MistigriSentences.roulette(token) != null, WHOLE, "the roulette starts", () -> {
            OutcomeRoulettes.Roulette roulette = MistigriSentences.roulette(token);
            context.assertEquals(roulette.lines().size(), 2, "only what may fall is listed");
            context.assertEquals(roulette.lines().getFirst().chance(), 50, "with its chance");
            Sentence announced = List.of(Sentence.COINS_SMALL, Sentence.CURSED).get(roulette.result());
            context.assertEquals(announced, drawn, "it stops on the drawn sentence");
            when(context, () -> done[0], WHOLE, "the show ends", () -> {
                boolean coins = count(player, Items.GOLD_INGOT) == 20, cursed = CursedRolls.isCursed(player);
                context.assertTrue(announced == Sentence.COINS_SMALL ? coins && !cursed : cursed && !coins,
                        "the announced sentence, and only it, is applied (" + announced + ")");
                context.complete();
            });
        });
    }

    /** Nothing is taken until the light has stopped and the result was held. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void theSentenceWaitsForTheRoulette(TestContext context) {
        ServerPlayerEntity player = player(context);
        List<MobEntity> tokens = new java.util.ArrayList<>();
        PartyControllerEntity party = party(context, tokens, player);
        InventoryUtils.giveOrDrop(player, new ItemStack(Items.GOLD_INGOT), 30);
        ItemStack cartridge = new ItemStack(ModItems.MISTIGRI_CARTRIDGE);
        BoardSpaceBlockEntity tile = tile(context, TILE, cartridge);
        MobEntity token = tokens.getFirst();
        boolean[] done = {false};
        MistigriSentences.start(context.getWorld(), tile.getPos(), token, party, cartridge, Sentence.COINS_SMALL, () -> done[0] = true);
        when(context, () -> MistigriSentences.roulette(token) != null, WHOLE, "the roulette starts", () -> {
            OutcomeRoulettes.Roulette roulette = MistigriSentences.roulette(token);
            context.waitAndRun(OutcomeRoulette.TOTAL_TICKS - 3, () -> {
                context.assertFalse(roulette.isOver(), "still held");
                context.assertEquals(MistigriSentences.phase(token), MistigriSentences.Phase.ROLL, "still rolling");
                context.assertEquals(count(player, Items.GOLD_INGOT), 30, "nothing taken yet");
                when(context, () -> count(player, Items.GOLD_INGOT) == 20, 20, "taken right after", () -> {
                    context.assertTrue(roulette.isOver(), "once it is over");
                    context.complete();
                });
            });
        });
    }

    /** Every player of the party is sent the roulette, even far from the board. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void everyPlayerOfThePartySeesIt(TestContext context) {
        ServerPlayerEntity first = player(context), second = player(context);
        List<MobEntity> tokens = new java.util.ArrayList<>();
        PartyControllerEntity party = party(context, tokens, first, second);
        Vec3d far = context.getAbsolute(new Vec3d(0, 1, 0)).add(400, 0, 400);
        second.refreshPositionAndAngles(far.x, far.y, far.z, 0, 0);
        ItemStack cartridge = new ItemStack(ModItems.MISTIGRI_CARTRIDGE);
        BoardSpaceBlockEntity tile = tile(context, TILE, cartridge);
        MobEntity token = tokens.getFirst();
        boolean[] done = {false};
        MistigriSentences.start(context.getWorld(), tile.getPos(), token, party, cartridge, Sentence.JOKE, () -> done[0] = true);
        when(context, () -> MistigriSentences.roulette(token) != null, WHOLE, "the roulette starts", () -> {
            OutcomeRoulettes.Roulette roulette = MistigriSentences.roulette(token);
            context.assertTrue(roulette.sentTo().contains(first.getUuid()), "the token's player");
            context.assertTrue(roulette.sentTo().contains(second.getUuid()), "the other player, far away");
            context.assertTrue(OutcomeRoulettes.recipients(party, null).contains(second), "a recipient wherever they are");
            context.assertEquals(roulette.lines().size(), Sentence.values().length, "every sentence of the default cartridge");
            context.complete();
        });
    }
}
