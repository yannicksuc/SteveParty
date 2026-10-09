package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.powerups.PowerUpService;
import fr.lordfinn.steveparty.powerups.PowerUps;
import fr.lordfinn.steveparty.powerups.effects.PartyStarRelocator;
import fr.lordfinn.steveparty.powerups.effects.PowerUpStar;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;

/**
 * The Golden Pipe and the Star Whistle with the real Star Cartridge (the party's star, {@link PartyStarRelocator}):
 * the pipe brings the pawn just before the star, the whistle sends the star to another star space. Each test in its
 * own batch: a party's star sees the star spaces of the boards around it.
 */
public class PowerUpStarGameTests implements SteveGameTest {
    private static final BlockPos START = new BlockPos(1, 1, 1), STAR = new BlockPos(3, 1, 1), END = new BlockPos(5, 1, 1);
    private static final BlockPos OTHER = new BlockPos(1, 1, 5), ANOTHER = new BlockPos(5, 1, 5);
    private static final BlockPos CONTROLLER = new BlockPos(8, 1, 8);
    private static final List<BlockPos> STAR_SPACES = List.of(STAR, OTHER, ANOTHER);

    private static void space(TestContext context, BlockPos pos, ItemStack cartridge, BlockPos to) {
        context.setBlockState(pos, ModBlocks.TILE);
        BoardSpaceBlockEntity space = context.getBlockEntity(pos);
        if (to != null)
            cartridge.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(new ArrayList<>(List.of(context.getAbsolutePos(to))), ""));
        space.setStack(0, cartridge);
    }

    /** START → STAR → END, three star spaces (STAR, OTHER, ANOTHER), the star on STAR, a token of {@code owner} on {@code on}. */
    private static PartyControllerEntity board(TestContext context, ServerPlayerEntity owner, BlockPos on) {
        for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
        space(context, START, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), STAR);
        space(context, STAR, new ItemStack(ModItems.STAR_CARTRIDGE), END);
        space(context, END, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), null);
        space(context, OTHER, new ItemStack(ModItems.STAR_CARTRIDGE), null);
        space(context, ANOTHER, new ItemStack(ModItems.STAR_CARTRIDGE), null);
        CowEntity cow = context.spawnEntity(EntityType.COW, on);
        TokenizedEntityInterface token = (TokenizedEntityInterface) cow;
        token.steveparty$setTokenized(true);
        token.steveparty$setTokenOwner(owner.getUuid());
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        PartyData data = new PartyData();
        data.addToken(cow.getUuid());
        data.addStep(new PartyStep());
        data.addStep(new TokenTurnPartyStep(cow.getUuid(), owner.getUuid()));
        data.addStep(new TokenTurnPartyStep(cow.getUuid(), owner.getUuid()));
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(cow.getUuid()))));
        controller.setPartyData(data);
        controller.nextStep();
        controller.nextStep();
        controller.setStarSpace(context.getAbsolutePos(STAR));
        return controller;
    }

    private static boolean use(TestContext context, ServerPlayerEntity player, ItemStack stack) {
        player.setStackInHand(Hand.MAIN_HAND, stack);
        return player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND).getResult().isAccepted();
    }

    /** No star space left for the next batches, no party, no player. */
    private static void finish(TestContext context, ServerPlayerEntity player) {
        for (BlockPos pos : STAR_SPACES) context.setBlockState(pos, Blocks.AIR);
        context.setBlockState(CONTROLLER, Blocks.AIR);
        TestPlayers.remove(context, player);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "powerup_star_goldenPipe")
    public void goldenPipeBringsThePawnJustBeforeTheStar(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        PartyControllerEntity controller = board(context, player, END);
        context.assertTrue(PowerUpStar.relocator() instanceof PartyStarRelocator, "the party's star by default");
        context.waitAndRun(2, () -> {
            context.assertTrue(use(context, player, new ItemStack(PowerUps.GOLDEN_PIPE.item(), 2)), "used");
            context.assertEquals(player.getMainHandStack().getCount(), 1, "consumed");
            context.assertTrue(PowerUpService.rollRefusal(player) != null, "the roll waits for the warp");
            context.waitAndRun(60, () -> {
                CowEntity cow = (CowEntity) context.getWorld().getEntity(controller.getPartyData().getTokens().getFirst());
                BoardSpaceBlockEntity on = cow == null ? null : BoardSpaces.boardSpaceOf(cow);
                context.assertTrue(on != null && on.getPos().equals(context.getAbsolutePos(START)),
                        "on the space before the star: " + (on == null ? "nothing" : on.getPos()));
                context.assertTrue(PowerUpService.rollRefusal(player) == null, "then the roll");
                context.assertEquals(controller.getStarSpace(), context.getAbsolutePos(STAR), "the star stays");
                finish(context, player);
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = "powerup_star_whistle")
    public void starWhistleSendsTheStarToAnotherStarSpace(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        PartyControllerEntity controller = board(context, player, START);
        context.waitAndRun(2, () -> {
            context.assertTrue(use(context, player, new ItemStack(PowerUps.STAR_WHISTLE.item(), 2)), "used");
            context.assertEquals(player.getMainHandStack().getCount(), 1, "consumed");
            BlockPos star = controller.getStarSpace();
            context.assertTrue(context.getAbsolutePos(OTHER).equals(star) || context.getAbsolutePos(ANOTHER).equals(star),
                    "on another star space: " + star);
            finish(context, player);
        });
    }
}
