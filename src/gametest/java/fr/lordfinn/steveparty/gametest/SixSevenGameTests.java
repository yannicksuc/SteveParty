package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.components.DiceFacesComponent.Kind;
import fr.lordfinn.steveparty.dice.DiceOutcome;
import fr.lordfinn.steveparty.dice.DiceThrow;
import fr.lordfinn.steveparty.dice.SixSeven;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.payloads.custom.SixSevenPayload;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static fr.lordfinn.steveparty.dice.DiceRollSequence.REVEAL_STEP_TICKS;
import static fr.lordfinn.steveparty.dice.DiceRollSequence.REVEAL_TOTAL_TICKS;
import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestCleanup.atEnd;
import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * The hidden « 6-7 » ({@link SixSeven}): a Double Dice showing a 6 and a 7 makes the players near the dice and the
 * roller's party do it (told to them and to who sees them); any other throw, a single die or a triple, doesn't.
 */
public class SixSevenGameTests implements FabricGameTest {
    private static final String BATCH = "six_seven";
    private static final BlockPos DICE = new BlockPos(2, 1, 2);

    private static DiceFace face(int value) {
        return new DiceFace(Kind.NORMAL, value);
    }

    private static DiceThrow throwOf(DiceFace... faces) {
        return new DiceThrow(List.of(faces), DiceOutcome.of(List.of(faces)));
    }

    /** A Double Dice thrown by {@code roller}: the lead rolls {@code lead}, the other die {@code other}. */
    private static List<DiceEntity> doubleDice(TestContext context, ServerPlayerEntity roller, ItemStack lead, ItemStack other) {
        List<DiceEntity> dice = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            DiceEntity one = context.spawnEntity(ModEntities.DICE_ENTITY, DICE.up(2 + i));
            one.age = DiceEntity.THROW_GRACE_TICKS;
            one.setNoGravity(true);
            one.setOwner(roller.getUuid());
            if (i == 0) one.setItemReference(lead.copyWithCount(1));
            else one.follow(other.copyWithCount(1));
            dice.add(one);
            atEnd(context, () -> {
                if (!one.isRemoved()) one.discard();
            });
        }
        List<UUID> ids = dice.stream().map(DiceEntity::getUuid).toList();
        for (DiceEntity one : dice) one.setLinkedDice(ids);
        dice.getFirst().startRoll();
        return dice;
    }

    /** Only two dice showing a 6 and a 7, in either order, premium faces included. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void onlyASixAndASevenOnTwoDice(TestContext context) {
        context.assertTrue(SixSeven.matches(throwOf(face(6), face(7))), "6 then 7");
        context.assertTrue(SixSeven.matches(throwOf(face(7), face(6))), "7 then 6");
        context.assertTrue(SixSeven.matches(throwOf(new DiceFace(Kind.PREMIUM, 6), face(7))), "a premium 6 and a 7");
        context.assertFalse(SixSeven.matches(throwOf(face(6), face(6))), "a double of 6");
        context.assertFalse(SixSeven.matches(throwOf(face(7), face(7))), "a double of 7");
        context.assertFalse(SixSeven.matches(throwOf(face(5), face(7))), "5 and 7");
        context.assertFalse(SixSeven.matches(throwOf(face(6), new DiceFace(Kind.COIN, 7))), "a 6 and 7 coins");
        context.assertFalse(SixSeven.matches(throwOf(face(6))), "a single 6");
        context.assertFalse(SixSeven.matches(throwOf(face(7))), "a single 7");
        context.assertFalse(SixSeven.matches(throwOf(face(6), face(7), face(1))), "a triple throw with a 6 and a 7");
        context.complete();
    }

    /**
     * A real Double Dice landing on 6 and 7: the roller (by the dice) and a player of the party far away do it; a far
     * player outside the party doesn't.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void aSixSevenThrowMakesThePartyAndTheNeighboursDoIt(TestContext context) {
        ServerPlayerEntity roller = player(context);
        Vec3d dicePos = context.getAbsolute(Vec3d.ofBottomCenter(DICE));
        roller.refreshPositionAndAngles(dicePos.x + 1, dicePos.y, dicePos.z, 0, 0);
        ServerPlayerEntity partyFar = TestPlayers.joined(context, "s", "Far", GameMode.CREATIVE, 2, 1, 150);
        ServerPlayerEntity stranger = TestPlayers.joined(context, "s", "Stranger", GameMode.CREATIVE, 2, 1, -150);
        atEnd(context, () -> TestPlayers.removeIfOnline(context, partyFar, stranger));
        PigEntity pig = token(context, new BlockPos(4, 1, 4), roller.getUuid());
        PartyControllerEntity controller = party(context, roller.getUuid(), pig);
        controller.addInterestedPlayer(partyFar);

        List<DiceEntity> dice = doubleDice(context, roller, die("dice_face_6"), die("dice_face_7"));
        DiceEntity lead = dice.getFirst();
        hit(context, lead, roller);
        when(context, lead::isRollFinished, REVEAL_STEP_TICKS + REVEAL_TOTAL_TICKS + 10, "the reveal", () -> {
            DiceThrow diceThrow = lead.getThrow();
            context.assertTrue(diceThrow != null && SixSeven.matches(diceThrow), "a 6 and a 7: " + (diceThrow == null ? null : diceThrow.faces()));
            SixSeven.Performance performance = SixSeven.perform(lead, diceThrow);
            context.assertTrue(performance != null, "the 6-7 starts");
            List<UUID> dancers = performance.payload().dancers();
            context.assertTrue(dancers.contains(roller.getUuid()), "the roller by the dice does it");
            context.assertTrue(dancers.contains(partyFar.getUuid()), "the party does it, even far away");
            context.assertFalse(dancers.contains(stranger.getUuid()), "a far player outside the party doesn't");
            context.assertTrue(performance.audience().contains(roller) && performance.audience().contains(partyFar),
                    "the dancers are told");
            context.assertFalse(performance.audience().contains(stranger), "the stranger isn't");
            context.assertEquals(performance.payload().startTick(), context.getWorld().getTime(), "it starts now");
            context.complete();
        });
    }

    /** A Double Dice of 6s, a single die and a 6-7 triple: no 6-7. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void otherThrowsDoNotDoIt(TestContext context) {
        ServerPlayerEntity roller = player(context);
        List<DiceEntity> sixes = doubleDice(context, roller, die("dice_face_6"), die("dice_face_6"));
        DiceEntity lead = sixes.getFirst();
        hit(context, lead, roller);
        when(context, lead::isRollFinished, REVEAL_STEP_TICKS + REVEAL_TOTAL_TICKS + 10, "the reveal", () -> {
            context.assertTrue(lead.getThrow() != null && SixSeven.perform(lead, lead.getThrow()) == null, "a double of 6: nothing");
            DiceEntity single = thrown(context, roller, die("dice_face_6", "dice_face_7"), DICE.east(2));
            hit(context, single, roller);
            context.assertTrue(single.getThrow() != null && SixSeven.perform(single, single.getThrow()) == null, "a single die: never");
            context.assertTrue(SixSeven.perform(lead, throwOf(face(6), face(7), face(2))) == null, "a triple with a 6 and a 7: nothing");
            context.complete();
        });
    }

    /** The payload goes over the network whole. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void thePayloadRoundTrips(TestContext context) {
        SixSevenPayload payload = new SixSevenPayload(1234L, 1.5, 64, -3.25, List.of(UUID.randomUUID(), UUID.randomUUID()));
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
        SixSevenPayload.CODEC.encode(buf, payload);
        context.assertEquals(SixSevenPayload.CODEC.decode(buf), payload, "the same payload");
        context.complete();
    }
}
