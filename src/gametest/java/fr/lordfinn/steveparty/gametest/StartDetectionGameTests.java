package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyPawnGuard;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestCleanup;
import fr.lordfinn.steveparty.gametest.kit.TestWait;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

import static fr.lordfinn.steveparty.gametest.kit.TestAsserts.assertOn;

/**
 * Who plays when a party starts: exactly the pawns standing on their start tiles, each put back on its own; a pawn gone
 * for good (killed, picked up) is no phantom: its start tile forgets it, a running party excludes it.
 */
public class StartDetectionGameTests implements SteveGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(3, 1, 6);
    private static final BlockPos START_A = new BlockPos(1, 1, 1), START_B = new BlockPos(5, 1, 1);
    private static final BlockPos SPACE = new BlockPos(3, 1, 3);

    private static BoardSpaceBlockEntity tile(TestContext context, BlockPos pos, ItemStack cartridge) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.TILE);
        BoardSpaceBlockEntity tile = context.getBlockEntity(pos);
        tile.setStack(0, cartridge);
        return tile;
    }

    private static PigEntity pawn(TestContext context, BlockPos on) {
        PigEntity pig = context.spawnMob(EntityType.PIG, on);
        pig.setAiDisabled(true);
        ((TokenizedEntityInterface) pig).steveparty$setTokenized(true);
        return pig;
    }

    private static void startTile(TestContext context, BlockPos pos, MobEntity token) {
        ItemStack start = new ItemStack(ModItems.TILE_BEHAVIOR_START);
        start.set(ModComponents.TB_START_BOUND_ENTITY, token.getUuid().toString());
        tile(context, pos, start);
    }

    /** The board: two start tiles linked to the controller (with the brush), a plain space. */
    private static PartyControllerEntity board(TestContext context, MobEntity a, MobEntity b) {
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
        startTile(context, START_A, a);
        startTile(context, START_B, b);
        tile(context, SPACE, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        controller.setStartLinks(List.of(context.getAbsolutePos(START_A), context.getAbsolutePos(START_B)));
        TestCleanup.atEnd(context, () -> {
            controller.stopParty(null);
            ((TokenizedEntityInterface) a).steveparty$setTokenized(false);
            ((TokenizedEntityInterface) b).steveparty$setTokenized(false);
            TestCleanup.removeIf(context, CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        });
        return controller;
    }

    /** A pawn a little off its start tile (pushed, drifted) plays, and starts the party right on its tile. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aPawnBesideItsStartPlaysFromItsStart(TestContext context) {
        PigEntity a = pawn(context, START_A), b = pawn(context, START_B);
        PartyControllerEntity controller = board(context, a, b);
        Vec3d off = context.getAbsolute(new Vec3d(START_A.getX() + 1.9, START_A.getY(), START_A.getZ() + 0.5));
        a.requestTeleport(off.x, off.y, off.z);
        controller.boot();
        context.assertTrue(controller.getPartyData().getTokens().containsAll(List.of(a.getUuid(), b.getUuid())),
                "both pawns play: " + controller.getPartyData().getTokens());
        Vec3d stand = BoardSpaces.standPos(context.getWorld(), context.getAbsolutePos(START_A));
        context.assertTrue(a.getPos().squaredDistanceTo(stand) < 0.01, "a was put back on its start: " + a.getPos() + " vs " + stand);
        assertOn(context, a, START_A, "a on its start");
        context.complete();
    }

    /** A pawn killed on its start: its tile forgets it, the party starts without it (no phantom player). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aKilledPawnIsForgottenByItsStart(TestContext context) {
        PigEntity a = pawn(context, START_A), b = pawn(context, START_B);
        PartyControllerEntity controller = board(context, a, b);
        b.discard();
        TestWait.when(context, () -> PartyPawnGuard.boundTo(context.getWorld(), context.getAbsolutePos(START_B)) == null, 40,
                "the start tile of b forgets it", () -> {
                    controller.boot();
                    context.assertEquals(controller.getPartyData().getTokens(), List.of(a.getUuid()), "only a plays");
                    context.assertTrue(PartyPawnGuard.boundTo(context.getWorld(), context.getAbsolutePos(START_A)) != null,
                            "a's start keeps it");
                    context.complete();
                });
    }

    /** A pawn killed during the party is excluded: its next turns go, the others play on. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aPawnKilledDuringThePartyIsExcluded(TestContext context) {
        PigEntity a = pawn(context, START_A), b = pawn(context, START_B);
        PartyControllerEntity controller = board(context, a, b);
        controller.boot();
        context.assertTrue(controller.getPartyData().getTokens().contains(b.getUuid()), "b plays");
        b.kill();
        TestWait.when(context, () -> !controller.getPartyData().getTokens().contains(b.getUuid()), 80,
                "b is excluded", () -> {
                    var data = controller.getPartyData();
                    List<PartyStep> steps = data.getSteps();
                    for (int i = data.getStepIndex() + 1; i < steps.size(); i++)
                        context.assertFalse(steps.get(i) instanceof TokenTurnPartyStep turn && b.getUuid().equals(turn.getTokenUUID()),
                                "no turn of b left");
                    context.assertTrue(data.isStarted() && data.getTokens().contains(a.getUuid()), "the party goes on with a");
                    context.complete();
                });
    }
}
