package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TrapSetComponent;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.BoardRuleCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.TrapCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.TrapCartridgeItem.Effect;
import fr.lordfinn.steveparty.service.AdvanceBackMoves;
import fr.lordfinn.steveparty.service.BoardTraps;
import fr.lordfinn.steveparty.service.TokenMovementService;
import fr.lordfinn.steveparty.service.TurnMoves;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The Trap Cartridge: setting a Trap where one's token stopped (asked, the item used up), one trap per space (or
 * replaced), the next other player springing it (coins, back, a lost turn, an item), never its setter nor their other
 * tokens. Players: {@code a} sets the traps, {@code b} is the victim. Each test has its own batch.
 * <p>
 * Board: T0 → T1 → TRAP → T3 along x.
 */
public class BoardTrapGameTests implements FabricGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(1, 1, 6);
    private static final BlockPos T0 = new BlockPos(1, 1, 1), T1 = new BlockPos(3, 1, 1), TRAP = new BlockPos(5, 1, 1),
            T3 = new BlockPos(7, 1, 1);

    private record Game(PartyControllerEntity party, ServerPlayerEntity a, ServerPlayerEntity b, CowEntity tokenA,
                        CowEntity tokenB, BoardSpaceBlockEntity trap, ItemStack coin) {
        int coins(ServerPlayerEntity player) {
            return InventoryUtils.count(player.getInventory(), coin);
        }
    }

    private static void space(TestContext context, BlockPos pos, ItemStack cartridge, BlockPos... to) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.TILE);
        List<BlockPos> next = new ArrayList<>();
        for (BlockPos p : to) next.add(context.getAbsolutePos(p));
        cartridge.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(next, ""));
        BoardSpaceBlockEntity space = context.getBlockEntity(pos);
        space.setStack(0, cartridge);
    }

    private static CowEntity token(TestContext context, BlockPos at, ServerPlayerEntity owner) {
        CowEntity cow = context.spawnEntity(EntityType.COW, at);
        TokenizedEntityInterface token = (TokenizedEntityInterface) cow;
        token.steveparty$setTokenized(true);
        token.steveparty$setTokenOwner(owner.getUuid());
        token.steveparty$setStatus(TokenStatus.IN_GAME);
        ThresholdGameTests.atEnd(context, () -> {
            token.steveparty$setTokenized(false);
            TurnMoves.forget(cow.getUuid());
        });
        return cow;
    }

    /** The board with {@code cartridge} on TRAP, a's token on T0, b's on {@code bAt}; a party of both, a's turn first. */
    private static Game game(TestContext context, ItemStack cartridge, BlockPos bAt) {
        space(context, T0, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), T1);
        space(context, T1, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), TRAP);
        space(context, TRAP, cartridge, T3);
        space(context, T3, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
        ServerPlayerEntity a = context.createMockCreativeServerPlayerInWorld();
        ServerPlayerEntity b = context.createMockCreativeServerPlayerInWorld();
        a.changeGameMode(GameMode.SURVIVAL);
        b.changeGameMode(GameMode.SURVIVAL);
        a.getInventory().clear();
        b.getInventory().clear();
        ThresholdGameTests.atEnd(context, () -> {
            context.getWorld().getServer().getPlayerManager().remove(a);
            context.getWorld().getServer().getPlayerManager().remove(b);
        });
        CowEntity tokenA = token(context, T0, a);
        CowEntity tokenB = token(context, bAt, b);
        context.setBlockState(CONTROLLER.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity party = context.getBlockEntity(CONTROLLER);
        PartyData data = new PartyData();
        data.addToken(tokenA.getUuid());
        data.addToken(tokenB.getUuid());
        data.addStep(new PartyStep());
        for (int round = 0; round < 3; round++) {
            data.addStep(new TokenTurnPartyStep(tokenA.getUuid(), null));
            data.addStep(new TokenTurnPartyStep(tokenB.getUuid(), null));
        }
        party.setPartyData(data);
        party.nextStep();
        party.nextStep();
        ThresholdGameTests.atEnd(context, () -> context.removeBlock(CONTROLLER));
        return new Game(party, a, b, tokenA, tokenB, context.getBlockEntity(TRAP), party.getCurrency(PartyCurrency.COIN));
    }

    private static void roll(MobEntity token, int steps) {
        BoardSpaceBlockEntity from = BoardSpaces.boardSpaceOf(token);
        TurnMoves.record(token, steps, List.of(steps), from == null ? null : from.getPos());
        TokenMovementService.moveEntityOnBoard(token, steps);
    }

    private static void when(TestContext context, BooleanSupplier condition, int ticks, String what, Runnable then) {
        if (condition.getAsBoolean()) {
            then.run();
            return;
        }
        context.assertTrue(ticks > 0, "timed out: " + what);
        context.waitAndRun(1, () -> when(context, condition, ticks - 1, what, then));
    }

    /** Puts a's trap on the space (as if a had stopped there and said yes). */
    private static void setA(TestContext context, Game game) {
        game.a().getInventory().insertStack(new ItemStack(ModItems.BOARD_TRAP));
        context.assertTrue(BoardTraps.set(context.getWorld(), game.trap(), game.tokenA()), "a's trap set");
    }

    // ---------------------------------------------------------------- setting

    /** Stopping on a Trap space with a Trap: its player is asked; yes: the trap is set, the item used up, the turn goes on. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "board_trap_place")
    public void stoppingThereOffersToSetATrap(TestContext context) {
        Game game = game(context, new ItemStack(ModItems.TRAP_CARTRIDGE), T3);
        game.a().getInventory().insertStack(new ItemStack(ModItems.BOARD_TRAP, 2));
        context.waitAndRun(2, () -> {
            roll(game.tokenA(), 2);
            when(context, () -> DicePrompts.pending(game.a()) != null, 100, "a is asked", () -> {
                context.assertEquals(game.party().getPartyData().getStepIndex(), 1, "the turn waits for the answer");
                DicePrompts.Prompt prompt = DicePrompts.pending(game.a());
                DicePrompts.answer(game.a(), prompt.id(), 0);
                TrapSetComponent trap = BoardTraps.trapAt(game.trap());
                context.assertTrue(trap != null && trap.owner().equals(game.a().getUuid()), "a's trap is set");
                context.assertEquals(game.a().getInventory().count(ModItems.BOARD_TRAP), 1, "one Trap used up");
                when(context, () -> game.party().getPartyData().getStepIndex() >= 2, 40, "the turn goes on", context::complete);
            });
        });
    }

    /** One trap per space: refused on a trapped space, unless the cartridge lets it replace the old one. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "board_trap_one")
    public void oneTrapPerSpace(TestContext context) {
        Game game = game(context, new ItemStack(ModItems.TRAP_CARTRIDGE), TRAP);
        setA(context, game);
        game.b().getInventory().insertStack(new ItemStack(ModItems.BOARD_TRAP));
        context.assertTrue(!BoardTraps.canSet(context.getWorld(), game.trap(), game.tokenB()), "refused by default");
        BoardRuleCartridgeItem.putSetting(game.trap().getActiveCartridgeItemStack(), TrapCartridgeItem.REPLACE, 1);
        context.assertTrue(BoardTraps.set(context.getWorld(), game.trap(), game.tokenB()), "replaced with the option");
        context.assertEquals(BoardTraps.trapAt(game.trap()).owner(), game.b().getUuid(), "b's trap now");
        context.complete();
    }

    // ---------------------------------------------------------------- springing

    /** Coins: b stopping there loses up to 5 coins to a; the trap is gone. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "board_trap_coins")
    public void anOpponentSpringsItForTheSetter(TestContext context) {
        Game game = game(context, TrapCartridgeItem.with(ModItems.TRAP_CARTRIDGE, Effect.COINS, 5), TRAP);
        setA(context, game);
        InventoryUtils.giveOrDrop(game.b(), game.coin(), 8);
        context.assertEquals(BoardTraps.spring(context.getWorld(), game.trap(), game.tokenB(), game.party()), BoardTraps.Outcome.SPRUNG, "sprung");
        context.assertEquals(game.coins(game.b()), 3, "b lost 5");
        context.assertEquals(game.coins(game.a()), 5, "a got them");
        context.assertTrue(BoardTraps.trapAt(game.trap()) == null, "the trap is gone");
        context.complete();
    }

    /** Its setter never springs it, nor another token of the same player. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "board_trap_own")
    public void itsSetterAndTheirOtherTokensDoNotSpringIt(TestContext context) {
        Game game = game(context, new ItemStack(ModItems.TRAP_CARTRIDGE), TRAP);
        setA(context, game);
        CowEntity other = token(context, TRAP, game.a());
        context.assertEquals(BoardTraps.spring(context.getWorld(), game.trap(), game.tokenA(), game.party()), BoardTraps.Outcome.OWN, "its setter");
        context.assertEquals(BoardTraps.spring(context.getWorld(), game.trap(), other, game.party()), BoardTraps.Outcome.OWN, "a teammate");
        context.assertTrue(BoardTraps.trapAt(game.trap()) != null, "still set");
        context.complete();
    }

    /** A lost turn: b's next turn is skipped. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "board_trap_skip")
    public void aLostTurn(TestContext context) {
        Game game = game(context, TrapCartridgeItem.with(ModItems.TRAP_CARTRIDGE, Effect.SKIP_TURN, 1), TRAP);
        setA(context, game);
        BoardTraps.spring(context.getWorld(), game.trap(), game.tokenB(), game.party());
        context.assertTrue(game.party().getPartyData().getSkippedTokens().contains(game.tokenB().getUuid()), "b loses a turn");
        // a's turn ends: b's turn is skipped, a plays again
        game.party().nextStep();
        when(context, () -> game.party().getPartyData().getStepIndex() >= 3, 60, "b's turn skipped", () -> {
            context.assertTrue(game.party().getPartyData().getSkippedTokens().isEmpty(), "only one turn");
            context.complete();
        });
    }

    /** Back: the token goes back the way it came. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "board_trap_back")
    public void sendBack(TestContext context) {
        Game game = game(context, TrapCartridgeItem.with(ModItems.TRAP_CARTRIDGE, Effect.BACK, 2), T0);
        setA(context, game);
        context.waitAndRun(2, () -> {
            // b walks onto the trap, then is sent back 2 spaces
            AdvanceBackMoves.noteAt(game.tokenB(), context.getAbsolutePos(T0));
            AdvanceBackMoves.noteAt(game.tokenB(), context.getAbsolutePos(T1));
            game.tokenB().refreshPositionAndAngles(BoardSpaces.standPos(context.getWorld(), context.getAbsolutePos(TRAP)), 0, 0);
            AdvanceBackMoves.noteAt(game.tokenB(), context.getAbsolutePos(TRAP));
            BoardTraps.spring(context.getWorld(), game.trap(), game.tokenB(), game.party());
            when(context, () -> {
                BoardSpaceBlockEntity on = BoardSpaces.boardSpaceOf(game.tokenB());
                return on != null && on.getPos().equals(context.getAbsolutePos(T0))
                        && ((TokenizedEntityInterface) game.tokenB()).steveparty$getNbSteps() == 0;
            }, 200, "back on T0", context::complete);
        });
    }

    /** An item: one of b's items (never coins) goes to a. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "board_trap_item")
    public void stealAnItem(TestContext context) {
        Game game = game(context, TrapCartridgeItem.with(ModItems.TRAP_CARTRIDGE, Effect.STEAL_ITEM, 1), TRAP);
        setA(context, game);
        InventoryUtils.giveOrDrop(game.b(), game.coin(), 4);
        game.b().getInventory().insertStack(new ItemStack(Items.EMERALD));
        BoardTraps.spring(context.getWorld(), game.trap(), game.tokenB(), game.party());
        context.assertEquals(game.a().getInventory().count(Items.EMERALD), 1, "a got the emerald");
        context.assertEquals(game.b().getInventory().count(Items.EMERALD), 0, "b lost it");
        context.assertEquals(game.coins(game.b()), 4, "not the coins");
        context.complete();
    }

    /** End to end: b's token stopping on a's trap during b's turn springs it (a malus landing). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "board_trap_landing")
    public void landingOnItSpringsIt(TestContext context) {
        Game game = game(context, TrapCartridgeItem.with(ModItems.TRAP_CARTRIDGE, Effect.COINS, 3), T1);
        setA(context, game);
        InventoryUtils.giveOrDrop(game.b(), game.coin(), 3);
        game.party().nextStep(); // b's turn
        context.waitAndRun(2, () -> {
            roll(game.tokenB(), 1);
            when(context, () -> game.coins(game.a()) == 3, 100, "a gets b's coins", () -> {
                context.assertTrue(BoardTraps.trapAt(game.trap()) == null, "the trap is gone");
                context.complete();
            });
        });
    }
}
