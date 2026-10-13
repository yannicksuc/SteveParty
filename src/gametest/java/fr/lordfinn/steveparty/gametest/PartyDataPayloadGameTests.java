package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.StartRollsStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.payloads.custom.PartyDataPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The party sent to the clients is frozen when sent: steps added afterwards (at launch) can't break its encoding. */
public class PartyDataPayloadGameTests implements SteveGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stepsAddedAfterSendingAreNotInThePacket(TestContext context) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), owner = UUID.randomUUID();
        PartyData data = new PartyData();
        data.addToken(a);
        data.addToken(b);
        data.addStep(new StartRollsStep());
        data.setStepIndex(0);
        PartyDataPayload payload = PartyDataPayload.fromPartyData(data);

        // What the launch does right after sending (it used to happen while Netty was encoding the live party)
        data.addStep(new TokenTurnPartyStep(a, owner));
        data.addStep(new TokenTurnPartyStep(b, owner));
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(a, b))));
        data.addToken(UUID.randomUUID());

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            PartyDataPayload.CODEC.encode(buf, payload);
            PartyData received = PartyDataPayload.CODEC.decode(buf).partyData();
            context.assertEquals(received.getSteps().size(), 1, "only the step there when sent");
            context.assertTrue(received.getSteps().getFirst() instanceof StartRollsStep, "the start rolls");
            context.assertEquals(received.getTokens(), List.of(a, b), "the tokens there when sent");
            context.assertEquals(received.getStepIndex(), 0, "the step index");
            context.assertEquals(received.getNbTurn(), data.getNbTurn(), "the number of turns");
        } finally {
            buf.release();
        }
        context.complete();
    }
}
