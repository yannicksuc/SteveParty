package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.InventoryInteractorTileBehavior;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.powerups.PowerUp;
import fr.lordfinn.steveparty.powerups.PowerUpService;
import fr.lordfinn.steveparty.powerups.PowerUps;
import fr.lordfinn.steveparty.powerups.effects.PowerUpProtection;
import fr.lordfinn.steveparty.powerups.effects.PowerUpStar;
import fr.lordfinn.steveparty.powerups.effects.StarRelocator;
import fr.lordfinn.steveparty.powerups.effects.TrapEffect;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestAsserts.assertOn;
import static fr.lordfinn.steveparty.gametest.kit.TestAsserts.count;
import static fr.lordfinn.steveparty.gametest.kit.TestCleanup.atEnd;
import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * The power-ups plugged into the base ({@link PowerUps}): each is used through its item during its player's turn and is
 * consumed only if its effect took place. The Padlock (kept when already protected), the Trap (on the pawn's space),
 * the Thief Bells (a picked player, kept against an empty target, parried by a Padlock), the Star Whistle and the
 * Golden Pipe (kept with no Star; the Pipe holds the roll until the pawn has arrived).
 */
public class PowerUpWiringGameTests implements SteveGameTest {
    private static final String BATCH = "powerups_wiring";
    /** The Star power-ups change the mod's Star ({@link PowerUpStar}): alone in their batch. */
    private static final String STAR_BATCH = "powerups_wiring_star";

    private static ServerPlayerEntity survivalPlayer(TestContext context) {
        ServerPlayerEntity player = player(context);
        player.changeGameMode(GameMode.SURVIVAL);
        return player;
    }

    /** {@code player} right-clicks the air with {@code count} of the power-up's item in their main hand. */
    private static boolean use(TestContext context, ServerPlayerEntity player, PowerUp powerUp, int count) {
        if (count > 0) player.setStackInHand(Hand.MAIN_HAND, new ItemStack(powerUp.item(), count));
        return player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND).getResult().isAccepted();
    }

    private static int held(ServerPlayerEntity player) {
        return player.getMainHandStack().getCount();
    }

    private static TokenTurnPartyStep turn(PartyControllerEntity controller) {
        return (TokenTurnPartyStep) controller.getPartyData().getCurrentStep();
    }

    /** A party of the given players, one token each on {@link DiceTestKit#PATH}, at the first one's turn; gold ingots are coins, emeralds stars. */
    private static PartyControllerEntity party(TestContext context, List<MobEntity> tokens, ServerPlayerEntity... players) {
        path(context, players.length, -1, null);
        for (int i = 0; i < players.length; i++) tokens.add(token(context, PATH.get(i), players[i].getUuid()));
        PartyControllerEntity controller = DiceTestKit.party(context, players[0].getUuid(), tokens.toArray(MobEntity[]::new));
        context.assertTrue(controller.setCurrency(PartyCurrency.COIN, new ItemStack(Items.GOLD_INGOT)), "coins: gold ingots");
        context.assertTrue(controller.setCurrency(PartyCurrency.STAR, new ItemStack(Items.EMERALD)), "stars: emeralds");
        return controller;
    }

    /** The thief answers their pending prompt with {@code target}. */
    private static void pick(TestContext context, ServerPlayerEntity thief, ServerPlayerEntity target) {
        DicePrompts.Prompt prompt = DicePrompts.pending(thief);
        context.assertTrue(prompt != null, "the thief is asked whom to rob");
        int index = -1;
        for (int i = 0; i < prompt.options().size(); i++)
            if (prompt.options().get(i).label().getString().contains(target.getName().getString())) index = i;
        context.assertTrue(index >= 0, "the target is in the list");
        context.assertTrue(DicePrompts.answer(thief, prompt.id(), index), "answered");
    }

    // ---------------------------------------------------------------- how they are given

    /**
     * The board's maker chooses how power-ups are given: an Inventory space handing out a chest's content gives one
     * like any item, and it is used as if bought.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void anInventorySpaceGivesAUsablePowerUp(TestContext context) {
        ServerPlayerEntity player = survivalPlayer(context);
        List<MobEntity> tokens = new ArrayList<>();
        PartyControllerEntity controller = party(context, tokens, player);
        BlockPos chestPos = new BlockPos(6, 1, 4);
        context.setBlockState(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = context.getBlockEntity(chestPos);
        chest.setStack(0, new ItemStack(PowerUps.MUSHROOM.item(), 2));
        ItemStack cartridge = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
        cartridge.set(ModComponents.INVENTORY_COMPONENT, new InventoryComponent(List.of(new ItemStack(PowerUps.MUSHROOM.item()))));
        cartridge.set(ModComponents.SELECTION_STATE, InventoryCartridgeItem.ALL);
        CartridgeContainers.set(cartridge, List.of(GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(chestPos))));
        BoardSpaceBlockEntity space = tile(context, PATH.get(2), cartridge);
        context.waitAndRun(2, () -> {
            // The pawn stops on the Inventory space
            new InventoryInteractorTileBehavior().onDestinationReached(context.getWorld(), space.getPos(), tokens.getFirst(), space, controller);
            context.assertEquals(count(player, PowerUps.MUSHROOM.item()), 1, "the player got the Mushroom");
            context.assertEquals(chest.count(PowerUps.MUSHROOM.item()), 1, "taken from the chest");
            int slot = player.getInventory().getSlotWithStack(new ItemStack(PowerUps.MUSHROOM.item()));
            context.assertTrue(slot >= 0, "in the inventory");
            player.setStackInHand(Hand.MAIN_HAND, player.getInventory().getStack(slot).copy());
            player.getInventory().setStack(slot, ItemStack.EMPTY);
            context.assertTrue(use(context, player, PowerUps.MUSHROOM, 0), "used during its turn");
            context.assertEquals(held(player), 0, "consumed");
            context.assertEquals(turn(controller).getPowerUps().used(), PowerUps.MUSHROOM, "the turn has it");
            context.complete();
        });
    }

    // ---------------------------------------------------------------- Padlock

    /** Used: its player is protected. Already protected: refused and kept. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void padlockProtectsAndIsKeptWhenAlreadyProtected(TestContext context) {
        ServerPlayerEntity player = survivalPlayer(context);
        PartyControllerEntity controller = party(context, new ArrayList<>(), player);
        context.waitAndRun(2, () -> {
            UUID token = turn(controller).getTokenUUID();
            context.assertTrue(PowerUpProtection.protect(controller, token), "already protected (a Padlock of before)");
            context.assertTrue(!use(context, player, PowerUps.PADLOCK, 2), "refused while protected");
            context.assertEquals(held(player), 2, "kept");
            context.assertTrue(!turn(controller).getPowerUps().hasUsed(), "the turn has no power-up");
            PowerUpProtection.expire(controller, token);
            context.assertTrue(use(context, player, PowerUps.PADLOCK, 0), "used once not protected");
            context.assertEquals(held(player), 1, "consumed");
            context.assertTrue(PowerUpProtection.isProtected(controller, token), "protected");
            context.complete();
        });
    }

    // ---------------------------------------------------------------- Trap

    /** On the pawn's space, consumed; a pawn on no board space: refused, kept. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void trapGoesOnThePawnsSpace(TestContext context) {
        ServerPlayerEntity player = survivalPlayer(context);
        List<MobEntity> tokens = new ArrayList<>();
        PartyControllerEntity controller = party(context, tokens, player);
        context.waitAndRun(2, () -> {
            context.assertTrue(use(context, player, PowerUps.TRAP, 2), "used");
            context.assertEquals(held(player), 1, "consumed");
            context.assertTrue(TrapEffect.trapAt(controller, context.getAbsolutePos(PATH.get(0))) != null, "a trap on its space");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void trapOffTheBoardIsKept(TestContext context) {
        ServerPlayerEntity player = survivalPlayer(context);
        PigEntity pig = token(context, new BlockPos(6, 1, 1), player.getUuid());
        PartyControllerEntity controller = DiceTestKit.party(context, player.getUuid(), pig);
        context.waitAndRun(2, () -> {
            context.assertTrue(!use(context, player, PowerUps.TRAP, 2), "refused off the board");
            context.assertEquals(held(player), 2, "kept");
            context.assertTrue(controller.getPartyData().getTraps().isEmpty(), "no trap");
            context.complete();
        });
    }

    /** A Padlock parries another player's trap (used up), never its own player's. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void padlockParriesAnotherPlayersTrapOnly(TestContext context) {
        ServerPlayerEntity a = player(context), b = player(context);
        List<MobEntity> tokens = new ArrayList<>();
        PartyControllerEntity controller = party(context, tokens, a, b);
        context.waitAndRun(2, () -> {
            BoardSpaceBlockEntity space = context.getBlockEntity(PATH.get(1));
            MobEntity tokenB = tokens.get(1);
            context.assertTrue(PowerUpProtection.protect(controller, tokenB.getUuid()), "b protected");
            TrapEffect.place(controller, space.getPos(), b.getUuid(), tokenB.getUuid());
            context.assertEquals(TrapEffect.onTokenStopped(controller, space, tokenB).outcome(), TrapEffect.Outcome.OWN, "its own trap");
            context.assertTrue(PowerUpProtection.isProtected(controller, tokenB.getUuid()), "its own trap uses no Padlock");
            TrapEffect.place(controller, space.getPos(), a.getUuid(), tokens.get(0).getUuid());
            context.assertEquals(TrapEffect.onTokenStopped(controller, space, tokenB).outcome(), TrapEffect.Outcome.DISARMED, "a's trap is parried");
            context.assertTrue(!PowerUpProtection.isProtected(controller, tokenB.getUuid()), "the Padlock is used up");
            context.assertTrue(TrapEffect.trapAt(controller, space.getPos()) == null, "the trap is gone");
            context.complete();
        });
    }

    // ---------------------------------------------------------------- Thief Bells

    /** The thief picks a player: the theft, then the bell consumed; the roll waits for the pick. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void thiefBellStealsFromThePickedPlayer(TestContext context) {
        ServerPlayerEntity thief = survivalPlayer(context), poor = player(context), rich = player(context);
        PartyControllerEntity controller = party(context, new ArrayList<>(), thief, poor, rich);
        InventoryUtils.giveOrDrop(poor, new ItemStack(Items.GOLD_INGOT), 6);
        InventoryUtils.giveOrDrop(rich, new ItemStack(Items.GOLD_INGOT), 30);
        context.waitAndRun(2, () -> {
            context.assertTrue(use(context, thief, PowerUps.THIEF_BELL, 2), "the list is shown");
            context.assertEquals(held(thief), 2, "not consumed while picking");
            context.assertTrue(PowerUpService.rollRefusal(thief) != null, "no roll while picking");
            context.assertTrue(!use(context, thief, PowerUps.MUSHROOM, 1), "no other power-up while picking");
            thief.setStackInHand(Hand.MAIN_HAND, new ItemStack(PowerUps.THIEF_BELL.item(), 2));
            pick(context, thief, poor);
            context.assertEquals(count(poor, Items.GOLD_INGOT), 0, "the picked player is robbed (5 to 15, at most 6)");
            context.assertEquals(count(thief, Items.GOLD_INGOT), 6, "the thief gets them");
            context.assertEquals(count(rich, Items.GOLD_INGOT), 30, "not the other one");
            context.assertEquals(held(thief), 1, "consumed");
            context.assertEquals(turn(controller).getPowerUps().used(), PowerUps.THIEF_BELL, "the turn remembers it");
            context.assertTrue(PowerUpService.rollRefusal(thief) == null, "the roll may come");
            context.complete();
        });
    }

    /** A target with nothing to steal: refused, the bell kept, the turn free. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void thiefBellIsKeptAgainstAnEmptyTarget(TestContext context) {
        ServerPlayerEntity thief = survivalPlayer(context), target = player(context);
        PartyControllerEntity controller = party(context, new ArrayList<>(), thief, target);
        context.waitAndRun(2, () -> {
            context.assertTrue(use(context, thief, PowerUps.GOLDEN_THIEF_BELL, 1), "the list is shown");
            pick(context, thief, target);
            context.assertEquals(held(thief), 1, "kept: no star to steal");
            context.assertTrue(!turn(controller).getPowerUps().hasUsed(), "the turn has no power-up");
            context.assertTrue(PowerUpService.rollRefusal(thief) == null, "the roll may come");
            context.complete();
        });
    }

    /** A Padlock parries the bell: both used up, nothing stolen. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void padlockParriesTheThiefBell(TestContext context) {
        ServerPlayerEntity thief = survivalPlayer(context), target = player(context);
        List<MobEntity> tokens = new ArrayList<>();
        PartyControllerEntity controller = party(context, tokens, thief, target);
        InventoryUtils.giveOrDrop(target, new ItemStack(Items.EMERALD), 2);
        context.waitAndRun(2, () -> {
            context.assertTrue(PowerUpProtection.protect(controller, tokens.get(1).getUuid()), "the target is protected");
            context.assertTrue(use(context, thief, PowerUps.GOLDEN_THIEF_BELL, 1), "the list is shown");
            pick(context, thief, target);
            context.assertEquals(count(target, Items.EMERALD), 2, "nothing stolen");
            context.assertTrue(!PowerUpProtection.isProtected(controller, tokens.get(1).getUuid()), "the Padlock is used up");
            context.assertEquals(held(thief), 0, "the bell is used up too");
            context.complete();
        });
    }

    // ---------------------------------------------------------------- the Star

    /** No Star (the default until the Star cartridge plugs its own in): both kept, the turn free. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void starPowerUpsAreKeptWithoutAStar(TestContext context) {
        ServerPlayerEntity player = survivalPlayer(context);
        PartyControllerEntity controller = party(context, new ArrayList<>(), player);
        context.waitAndRun(2, () -> {
            context.assertTrue(!use(context, player, PowerUps.STAR_WHISTLE, 1), "the whistle: refused");
            context.assertEquals(held(player), 1, "kept");
            context.assertTrue(!use(context, player, PowerUps.GOLDEN_PIPE, 1), "the pipe: refused");
            context.assertEquals(held(player), 1, "kept");
            context.assertTrue(!turn(controller).getPowerUps().hasUsed(), "the turn has no power-up");
            context.complete();
        });
    }

    /** A Star the tests move by hand. */
    private static final class TestStar implements StarRelocator {
        final List<BlockPos> spaces = new ArrayList<>();
        BlockPos star;

        @Override
        public Optional<BlockPos> currentStarSpace(PartyControllerEntity party) {
            return Optional.ofNullable(star);
        }

        @Override
        public List<BlockPos> activeStarSpaces(PartyControllerEntity party) {
            return spaces;
        }

        @Override
        public boolean moveStarTo(PartyControllerEntity party, BlockPos space) {
            if (!spaces.contains(space)) return false;
            star = space;
            return true;
        }
    }

    private static TestStar install(TestContext context, BlockPos... spaces) {
        TestStar star = new TestStar();
        for (BlockPos space : spaces) star.spaces.add(context.getAbsolutePos(space));
        star.star = star.spaces.getFirst();
        StarRelocator previous = PowerUpStar.install(star);
        atEnd(context, () -> PowerUpStar.install(previous));
        return star;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = STAR_BATCH)
    public void starWhistleMovesTheStarAndIsConsumed(TestContext context) {
        ServerPlayerEntity player = survivalPlayer(context);
        PartyControllerEntity controller = party(context, new ArrayList<>(), player);
        TestStar star = install(context, PATH.get(1), PATH.get(2));
        context.waitAndRun(2, () -> {
            context.assertTrue(use(context, player, PowerUps.STAR_WHISTLE, 2), "used");
            context.assertEquals(star.star, context.getAbsolutePos(PATH.get(2)), "the Star flew to the other space");
            context.assertEquals(held(player), 1, "consumed");
            context.assertEquals(turn(controller).getPowerUps().used(), PowerUps.STAR_WHISTLE, "the turn remembers it");
            context.complete();
        });
    }

    /** The pawn warps before the Star; the roll waits until it has arrived. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = STAR_BATCH)
    public void goldenPipeHoldsTheRollUntilThePawnArrives(TestContext context) {
        ServerPlayerEntity player = survivalPlayer(context);
        path(context, 3, -1, null);
        PigEntity pig = token(context, PATH.get(0), player.getUuid());
        PartyControllerEntity controller = DiceTestKit.party(context, player.getUuid(), pig);
        install(context, PATH.get(2));
        context.waitAndRun(2, () -> {
            context.assertTrue(use(context, player, PowerUps.GOLDEN_PIPE, 2), "used");
            context.assertEquals(held(player), 1, "consumed");
            context.assertTrue(PowerUpService.rollRefusal(player) != null, "no roll during the warp");
            when(context, () -> PowerUpService.rollRefusal(player) == null, 120, "the turn goes on", () -> {
                assertOn(context, pig, PATH.get(1), "on the space before the Star");
                context.assertTrue(!turn(controller).hasRolled(), "its roll is still to come");
                context.complete();
            });
        });
    }
}
