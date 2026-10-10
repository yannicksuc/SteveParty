package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.api.StevePartyRegistries;
import fr.lordfinn.steveparty.api.board.BoardSpaceRoles;
import fr.lordfinn.steveparty.api.party.PartySteps;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepFactory;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.StopBoardSpaceBehavior;
import fr.lordfinn.steveparty.gametest.addon.TestAddon;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock.TILE_TYPE;

/** The addon API: Steve Party's content registered in it, and an addon's ({@link TestAddon}) working through it. */
public class ApiGameTests implements SteveGameTest {

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theAddonEntrypointRanOnce(TestContext context) {
        context.assertEquals(TestAddon.initialized, 1, "the steveparty entrypoint ran once");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyBuiltInRoleIsRegistered(TestContext context) {
        for (BoardSpaceType type : BoardSpaceType.values())
            context.assertTrue(BoardSpaceRoles.get(type.id()) == type.behavior(), type + " registered with its behaviour");
        context.assertTrue(BoardSpaceRoles.behaviorOf(new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP)) instanceof StopBoardSpaceBehavior, "a Stop cartridge, a Stop space");
        context.assertTrue(BoardSpaceRoles.behaviorOf(ItemStack.EMPTY) == BoardSpaceType.DEFAULT.behavior(), "no cartridge, the default role");
        context.complete();
    }

    /** The addon's cartridge gives its role: the look of a Simple tile, the addon's behaviour. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anAddonCartridgeGivesItsRole(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.ADVANCED_TILE);
        BoardSpaceBlockEntity tile = context.getBlockEntity(pos);
        tile.setStack(0, new ItemStack(TestAddon.PROBE_CARTRIDGE));
        context.assertEquals(tile.getCachedState().get(TILE_TYPE), BoardSpaceType.DEFAULT, "the look of a Simple tile");
        TestAddon.ProbeBehavior probe = (TestAddon.ProbeBehavior) BoardSpaceRoles.get(TestAddon.PROBE_ROLE);
        context.assertTrue(tile.getBoardSpaceBehavior() == probe, "the addon's behaviour");
        context.assertTrue(BoardSpaceRoles.behaviorAt(context.getWorld(), context.getAbsolutePos(pos), tile.getCachedState()) == probe, "found from the block too");

        PigEntity token = TestBoards.token(context, pos.up(), null);
        PartyControllerEntity controller = TestBoards.party(context, new BlockPos(1, 1, 5), 1, token.getUuid());
        int before = probe.landed.size();
        tile.onDestinationReached(token, controller);
        context.assertEquals(probe.landed.size(), before + 1, "it answers the landing");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyBuiltInStepKindIsRegistered(TestContext context) {
        for (PartyStepType type : PartyStepType.values())
            context.assertTrue(StevePartyRegistries.PARTY_STEPS.contains(PartySteps.idOf(type)), type + " registered");
        // Steve Party's steps still save the name of their kind
        TokenTurnPartyStep turn = new TokenTurnPartyStep(UUID.randomUUID(), null);
        context.assertEquals(turn.toNbt().getString("Type"), "TOKEN_TURN", "saved as before");
        context.assertTrue(PartyStepFactory.get(turn.toNbt()) instanceof TokenTurnPartyStep, "and rebuilt");
        context.complete();
    }

    /** An addon's step saves its id and its own fields, and comes back as itself. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anAddonStepIsSavedAndRebuilt(TestContext context) {
        TestAddon.ProbeStep step = new TestAddon.ProbeStep(42);
        step.setStatus(PartyStep.Status.IN_PROGRESS);
        NbtCompound nbt = step.toNbt();
        context.assertEquals(nbt.getString("Type"), TestAddon.PROBE_STEP.toString(), "saved with its id");
        PartyStep rebuilt = PartyStepFactory.get(nbt);
        context.assertTrue(rebuilt instanceof TestAddon.ProbeStep probe && probe.value == 42, "rebuilt with its field, got " + rebuilt);
        context.assertEquals(rebuilt.getStatus(), PartyStep.Status.IN_PROGRESS, "and its status");
        NbtCompound unknown = new NbtCompound();
        unknown.putString("Type", "missing-addon:gone");
        context.assertTrue(PartyStepFactory.get(unknown).getClass() == PartyStep.class, "an unknown kind: a placeholder");
        context.complete();
    }
}
