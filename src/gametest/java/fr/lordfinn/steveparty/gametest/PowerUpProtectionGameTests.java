package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLiveData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.powerups.effects.PowerUpProtection;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The Padlock power-up's protection: given, used up by one attack, ended by the next turn, saved with the party. */
public class PowerUpProtectionGameTests implements SteveGameTest {

    private static PartyControllerEntity placeController(TestContext context, BlockPos pos) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.PARTY_CONTROLLER);
        return context.getBlockEntity(pos);
    }

    /** A party of a and b (unloaded tokens): a lead step, then a, b, a, and the end. Not started yet. */
    private static PartyData party(PartyControllerEntity controller, UUID a, UUID b) {
        PartyData data = new PartyData();
        data.addToken(a);
        data.addToken(b);
        data.addStep(new PartyStep());
        data.addStep(new TokenTurnPartyStep(a, null));
        data.addStep(new TokenTurnPartyStep(b, null));
        data.addStep(new TokenTurnPartyStep(a, null));
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(a, b))));
        controller.setPartyData(data);
        return data;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void padlockProtectsItsPlayerOnly(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PartyControllerEntity controller = placeController(context, pos);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        party(controller, a, b);

        context.assertTrue(!PowerUpProtection.isProtected(controller, a), "nobody protected at first");
        context.assertTrue(PowerUpProtection.protect(controller, a), "a protected");
        context.assertTrue(PowerUpProtection.isProtected(controller, a), "a is protected");
        context.assertTrue(!PowerUpProtection.isProtected(controller, b), "b is not");
        context.assertTrue(!PowerUpProtection.protect(controller, a), "a second Padlock is not stacked");
        context.assertTrue(!PowerUpProtection.protect(controller, UUID.randomUUID()), "a token out of the party can't be protected");
        context.assertTrue(!PowerUpProtection.isProtected(controller, null), "null token");

        // The HUD's sign: the bonus column of the standings
        context.assertEquals(PartyLiveData.bonusesOf(controller, context.getWorld(), a).size(), 1, "a shows the Padlock");
        context.assertTrue(PartyLiveData.bonusesOf(controller, context.getWorld(), b).isEmpty(), "b shows nothing");

        // Excluded from the party: the protection goes with the token
        controller.getPartyData().removeToken(a);
        context.assertTrue(!PowerUpProtection.isProtected(controller, a), "removed with the token");
        context.removeBlock(pos);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void padlockParriesOneAttackThenIsUsedUp(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PartyControllerEntity controller = placeController(context, pos);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        party(controller, a, b);

        context.assertTrue(!PowerUpProtection.consume(controller, b, PowerUpProtection.Attack.TRAP), "b unprotected: the trap goes on");
        PowerUpProtection.protect(controller, a);
        context.assertTrue(PowerUpProtection.consume(controller, a, PowerUpProtection.Attack.THIEF_BELL), "the Bell is blocked");
        context.assertTrue(!PowerUpProtection.isProtected(controller, a), "used up");
        context.assertTrue(!PowerUpProtection.consume(controller, a, PowerUpProtection.Attack.TRAP), "the next attack goes through");
        context.assertTrue(PartyLiveData.bonusesOf(controller, context.getWorld(), a).isEmpty(), "sign gone");

        PowerUpProtection.protect(controller, a);
        context.assertTrue(PowerUpProtection.consume(controller, a), "generic attack blocked too");
        context.assertTrue(!PowerUpProtection.isProtected(controller, a), "used up again");
        context.removeBlock(pos);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void padlockExpiresAtThePlayersNextTurn(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PartyControllerEntity controller = placeController(context, pos);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        PartyData data = party(controller, a, b);
        controller.nextStep();
        controller.nextStep();
        context.assertEquals(data.getStepIndex(), 1, "turn of a");

        // Used at the start of a's turn
        PowerUpProtection.protect(controller, a);
        controller.nextStep();
        context.assertTrue(data.getCurrentStep() instanceof TokenTurnPartyStep turn && b.equals(turn.getTokenUUID()), "turn of b");
        context.assertTrue(PowerUpProtection.isProtected(controller, a), "still protected during b's turn");

        controller.nextStep();
        context.assertTrue(data.getCurrentStep() instanceof TokenTurnPartyStep turn && a.equals(turn.getTokenUUID()), "a's next turn");
        context.assertTrue(!PowerUpProtection.isProtected(controller, a), "expired at a's next turn");
        context.removeBlock(pos);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void padlockSurvivesSaveAndReload(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PartyControllerEntity controller = placeController(context, pos);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        PartyData data = party(controller, a, b);
        controller.nextStep();
        controller.nextStep();
        controller.nextStep();
        context.assertEquals(data.getStepIndex(), 2, "turn of b");

        NbtCompound empty = controller.createNbt(context.getWorld().getRegistryManager());
        context.assertTrue(!empty.contains("ProtectedTokens"), "nothing written while nobody is protected");

        PowerUpProtection.protect(controller, a);
        NbtCompound saved = controller.createNbt(context.getWorld().getRegistryManager());
        controller.readNbt(saved, context.getWorld().getRegistryManager());
        context.assertTrue(controller.getPartyData() != data, "party reloaded from NBT");
        context.assertTrue(PowerUpProtection.isProtected(controller, a), "a still protected after the reload");
        context.assertTrue(!PowerUpProtection.isProtected(controller, b), "b still not");

        // Resuming the reloaded turn of b does not end a's protection; a's next turn does
        context.waitAndRun(5, () -> {
            context.assertTrue(PowerUpProtection.isProtected(controller, a), "kept while the party resumes");
            controller.nextStep();
            context.assertTrue(!PowerUpProtection.isProtected(controller, a), "expired at a's next turn after the reload");
            context.removeBlock(pos);
            context.complete();
        });
    }
}
