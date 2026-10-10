package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.api.StevePartyRegistries;
import fr.lordfinn.steveparty.api.event.PartyEvents;
import fr.lordfinn.steveparty.api.board.BoardSpaceRoles;
import fr.lordfinn.steveparty.api.party.PartySteps;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.BasicGameGeneratorStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
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
import fr.lordfinn.steveparty.dice.DiceModule;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.powerups.PowerUp;
import fr.lordfinn.steveparty.powerups.PowerUps;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
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

    /** What the party events told for the party under test (the listeners stay: they only note this one). */
    private static PartyControllerEntity watched;
    private static final List<String> EVENTS = new ArrayList<>();

    static {
        PartyEvents.STEP_STARTED.register((controller, step, index) -> {
            if (controller == watched) EVENTS.add("start " + step.getTypeId().getPath() + " " + index);
        });
        PartyEvents.STEP_ENDED.register((controller, step) -> {
            if (controller == watched) EVENTS.add("end " + step.getTypeId().getPath());
        });
        PartyEvents.ENDED.register(controller -> {
            if (controller == watched) EVENTS.add("party over");
        });
    }

    /** The steps start and end in order, an addon's step among them, and the end of the party is told. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void thePartyEventsFollowTheSteps(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(pos);
        UUID token = UUID.randomUUID();
        PartyData data = new PartyData();
        data.addToken(token);
        data.addStep(new PartyStep());
        data.addStep(new TestAddon.ProbeStep(7));
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(token))));
        controller.setPartyData(data);
        EVENTS.clear();
        watched = controller;
        context.addFinalTask(() -> watched = null);
        controller.nextStep();
        controller.nextStep(); // the probe step ends right away: the end comes
        context.assertEquals(EVENTS, List.of("start default 0", "end default", "start probe_step 1", "end probe_step",
                "start end 2", "party over"), "the events in order");
        context.complete();
    }

    /** The addon's dice module and power-up: registered in their namespace, with their items, saved by their id. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anAddonDiceModuleAndPowerUpHaveTheirItems(TestContext context) {
        DiceModule sticky = TestAddon.STICKY;
        context.assertEquals(sticky.id(), "steveparty-gametest:sticky", "its key on a die: its whole id");
        context.assertEquals(Registries.ITEM.getId(sticky.item()), Identifier.of(TestAddon.NAMESPACE, "dice_module_sticky"), "its item, in its namespace");
        context.assertTrue(DiceModules.fromItem(new ItemStack(sticky.item())) == sticky, "the item gives the module");
        context.assertTrue(ModItems.DICE_MODULES.contains(sticky.item()), "listed with the others (creative tab)");
        ItemStack die = DiceModules.set(new ItemStack(ModItems.DEFAULT_DICE), Map.of(sticky, 1, DiceModules.LUCKY, 2));
        context.assertEquals(DiceModules.of(die), Map.of(sticky, 1, DiceModules.LUCKY, 2), "carried by a die with Steve Party's");
        context.assertEquals(DiceModules.get("lucky"), DiceModules.LUCKY, "Steve Party's keys unchanged");

        PowerUp banana = TestAddon.BANANA;
        context.assertEquals(Registries.ITEM.getId(banana.item()), Identifier.of(TestAddon.NAMESPACE, "powerup_banana"), "the power-up's item");
        context.assertTrue(PowerUps.byId(banana.id()) == banana && ModItems.POWER_UPS.contains(banana.item()), "found by its key, listed");
        context.complete();
    }

    /** An addon's party card in the program: its steps come in their place, the loops of the program included. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anAddonPartyCardAddsItsSteps(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(pos);
        controller.getProgram().setStack(0, new ItemStack(ModItems.PARTY_CARD_TURNS));
        controller.getProgram().setStack(1, new ItemStack(TestAddon.PROBE_CARD_ITEM, 3));
        controller.getProgram().setStack(2, new ItemStack(ModItems.PARTY_CARD_REPEAT, 2));
        UUID a = UUID.randomUUID();
        PartyData data = new PartyData();
        data.addToken(a);
        data.addStep(new BasicGameGeneratorStep());
        controller.setPartyData(data);
        controller.nextStep();
        // generator, (turn of a, probe 3) x 2, end
        List<PartyStep> steps = data.getSteps();
        context.assertEquals(steps.size(), 6, "steps generated");
        context.assertTrue(steps.get(2) instanceof TestAddon.ProbeStep probe && probe.value == 3, "the addon's card, its number");
        context.assertTrue(steps.get(4) instanceof TestAddon.ProbeStep, "repeated with the others");
        context.assertTrue(steps.get(5) instanceof EndPartyStep, "the end last");
        context.removeBlock(pos);
        context.complete();
    }
}
