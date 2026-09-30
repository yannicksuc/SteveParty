package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLiveData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The live state of a party sent to its party HUDs ({@link PartyLiveData}): the current turn (roll, steps left), and
 * the standings (names, colours, owners, stars, coins, power-ups). Each test in a batch of its own: the mock players
 * share one name.
 */
public class PartyHudGameTests implements FabricGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(1, 1, 5);

    private static PigEntity spawnToken(TestContext context, BlockPos pos, UUID owner) {
        PigEntity pig = context.spawnMob(EntityType.PIG, pos);
        TokenizedEntityInterface token = (TokenizedEntityInterface) pig;
        token.steveparty$setTokenized(true);
        token.steveparty$setTokenOwner(owner);
        token.steveparty$setStatus(TokenStatus.IN_GAME);
        return pig;
    }

    /** A party of {@code a} and {@code b} (one round), started up to the turn of {@code a}. */
    private static PartyControllerEntity startParty(TestContext context, UUID a, UUID b) {
        context.setBlockState(CONTROLLER.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        PartyData data = new PartyData();
        data.addToken(a);
        data.addToken(b);
        data.addStep(new PartyStep());
        data.addStep(new TokenTurnPartyStep(a, null));
        data.addStep(new TokenTurnPartyStep(b, null));
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(a, b))));
        controller.setPartyData(data);
        controller.nextStep();
        controller.nextStep();
        return controller;
    }

    private static PartyLiveData capture(TestContext context, PartyControllerEntity controller) {
        return PartyLiveData.capture(controller, context.getWorld());
    }

    /** The current turn: nothing rolled yet, then the roll, then the steps left while the token walks. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_hud_turn")
    public void liveDataFollowsTheCurrentTurn(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            PigEntity pig = spawnToken(context, new BlockPos(2, 1, 2), player.getUuid());
            pig.setCustomName(Text.literal("Cochonou"));
            ((TokenizedEntityInterface) pig).steveparty$setTokenColor(0x3366FF);
            UUID absent = UUID.randomUUID();
            PartyControllerEntity controller = startParty(context, pig.getUuid(), absent);
            TokenTurnPartyStep turn = (TokenTurnPartyStep) controller.getPartyData().getCurrentStep();

            PartyLiveData live = capture(context, controller);
            context.assertEquals(live.roll(), 0, "not rolled yet");
            context.assertEquals(live.stepsLeft(), 0, "no steps yet");
            context.assertTrue(!live.moving() && !live.shopping(), "waiting for the roll");
            context.assertEquals(live.absentSeconds(), -1, "its token is here");
            context.assertEquals(live.standings().size(), 2, "one standing per token");

            PartyLiveData.Standing first = live.standings().getFirst();
            context.assertEquals(first.token(), pig.getUuid(), "the turn order");
            context.assertEquals(first.tokenName(), "Cochonou", "the token's name");
            context.assertEquals(first.owner(), Optional.of(player.getUuid()), "its owner");
            context.assertEquals(first.ownerName(), player.getNameForScoreboard(), "its owner's name");
            context.assertEquals(first.color(), 0x3366FF, "the Tokenizer Wand's colour");
            context.assertTrue(first.online(), "its owner is connected");
            PartyLiveData.Standing second = live.standings().get(1);
            context.assertEquals(second.tokenName(), absent.toString().substring(0, 8), "an unloaded token: the start of its UUID");
            context.assertTrue(second.owner().isEmpty() && second.color() == -1, "unknown owner, no colour");

            DiceEntity dice = context.spawnEntity(ModEntities.DICE_ENTITY, new BlockPos(2, 2, 3));
            turn.onDiceRoll(dice, player.getUuid(), 5, controller);
            context.assertEquals(capture(context, controller).roll(), 5, "the roll");
            ((TokenizedEntityInterface) pig).steveparty$setNbSteps(3);
            live = capture(context, controller);
            context.assertEquals(live.stepsLeft(), 3, "steps left");
            context.assertTrue(live.moving(), "walking");
            context.assertTrue(live.sameAs(capture(context, controller)), "nothing changed: same state");
            ((TokenizedEntityInterface) pig).steveparty$setNbSteps(2);
            context.assertTrue(!live.sameAs(capture(context, controller)), "one step walked: a new state");

            // The next turn starts from nothing
            ((TokenizedEntityInterface) pig).steveparty$setNbSteps(0);
            controller.nextStep();
            live = capture(context, controller);
            context.assertEquals(live.roll(), 0, "next turn: not rolled");
            context.assertEquals(live.absentSeconds(), (int) (TokenTurnPartyStep.ABSENT_TURN_DELAY_TICKS / 20), "its token is absent: the countdown");
            context.setBlockState(CONTROLLER, Blocks.AIR);
            context.complete();
        } finally {
            context.getWorld().getServer().getPlayerManager().remove(player);
        }
    }

    /**
     * The standings: the party's stars and coins in the owner's inventory (the items picked in the controller's
     * settings, same components), and the power-ups they hold (Double and Triple dice, forged dice; a plain die is no
     * power-up), one stack per kind with the number held.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_hud_standings")
    public void standingsCountTheStarsCoinsAndPowerUps(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            PigEntity pig = spawnToken(context, new BlockPos(2, 1, 2), player.getUuid());
            PartyControllerEntity controller = startParty(context, pig.getUuid(), UUID.randomUUID());
            player.getInventory().setStack(10, new ItemStack(Items.NETHER_STAR, 2));
            player.getInventory().setStack(11, new ItemStack(Items.NETHER_STAR));
            player.getInventory().setStack(12, new ItemStack(Items.GOLD_NUGGET, 20));
            ItemStack renamed = new ItemStack(Items.GOLD_NUGGET, 5);
            renamed.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Fake coin"));
            player.getInventory().setStack(13, renamed);

            PartyLiveData live = capture(context, controller);
            context.assertTrue(live.starItem().isOf(Items.NETHER_STAR) && live.coinItem().isOf(Items.GOLD_NUGGET), "the default currencies, for the icons");
            PartyLiveData.Standing standing = live.standings().getFirst();
            context.assertEquals(standing.stars(), 3, "the nether stars held");
            context.assertEquals(standing.coins(), 20, "the plain gold nuggets held (not the renamed ones)");
            context.assertEquals(live.standings().get(1).stars(), 0, "an unknown owner holds nothing");

            // Other items picked: the counts follow
            controller.setCurrency(fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency.COIN, renamed);
            context.assertEquals(capture(context, controller).standings().getFirst().coins(), 5, "the renamed nuggets are the coins now");

            ItemStack forged = DiceFacesComponent.createDie(List.of(new ItemStack(ModItems.DICE_FACES.get(1))));
            player.getInventory().setStack(0, new ItemStack(ModItems.DOUBLE_DICE, 2));
            player.getInventory().setStack(3, new ItemStack(ModItems.DOUBLE_DICE));
            player.getInventory().setStack(4, new ItemStack(ModItems.TRIPLE_DICE));
            player.getInventory().setStack(5, new ItemStack(ModItems.DEFAULT_DICE));
            player.getInventory().setStack(6, forged);
            player.getInventory().setStack(7, new ItemStack(Items.DIAMOND, 12));

            List<ItemStack> powerUps = capture(context, controller).standings().getFirst().powerUps();
            context.assertEquals(powerUps.size(), 3, "three kinds of power-ups: " + powerUps);
            context.assertTrue(powerUps.getFirst().isOf(ModItems.DOUBLE_DICE) && powerUps.getFirst().getCount() == 3,
                    "the Double dice, counted together, first (the most numerous)");
            context.assertTrue(powerUps.stream().anyMatch(stack -> stack.isOf(ModItems.TRIPLE_DICE)), "the Triple die");
            context.assertTrue(powerUps.stream().anyMatch(stack -> ItemStack.areItemsAndComponentsEqual(stack, forged)), "the forged die");
            context.assertTrue(!PartyLiveData.isPowerUp(new ItemStack(ModItems.DEFAULT_DICE)), "a plain die is no power-up");

            context.setBlockState(CONTROLLER, Blocks.AIR);
            context.complete();
        } finally {
            context.getWorld().getServer().getPlayerManager().remove(player);
        }
    }

    /** Ranks: the most stars first, the most coins between equal stars; the same stars and coins share their rank. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ranksByStarsThenCoinsAndShareTies(TestContext context) {
        List<PartyLiveData.Standing> standings = new ArrayList<>();
        int[][] held = {{3, 10}, {5, 0}, {3, 10}, {0, 50}, {3, 12}};
        for (int[] starsCoins : held)
            standings.add(new PartyLiveData.Standing(UUID.randomUUID(), "", Optional.empty(), "", -1, true, starsCoins[0], starsCoins[1], List.of()));
        int[] ranks = PartyLiveData.ranks(standings);
        context.assertTrue(java.util.Arrays.equals(ranks, new int[]{3, 1, 3, 5, 2}), "ranks " + java.util.Arrays.toString(ranks));
        context.complete();
    }

    /**
     * The live state is sent to the interested players only when it changed; the steps' packet carries it too, up to
     * date.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_hud_sync")
    public void liveStateIsSentOnlyWhenItChanged(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            PigEntity pig = spawnToken(context, new BlockPos(2, 1, 2), player.getUuid());
            PartyControllerEntity controller = startParty(context, pig.getUuid(), UUID.randomUUID());
            controller.addInterestedPlayer(player);
            controller.syncLiveData(context.getWorld());
            PartyLiveData sent = controller.getLastLiveData();
            context.assertTrue(sent != PartyLiveData.EMPTY && sent.standings().size() == 2, "sent once the party runs");
            controller.syncLiveData(context.getWorld());
            context.assertTrue(controller.getLastLiveData() == sent, "nothing changed: nothing sent");
            ((TokenizedEntityInterface) pig).steveparty$setNbSteps(4);
            controller.syncLiveData(context.getWorld());
            context.assertEquals(controller.getLastLiveData().stepsLeft(), 4, "a change: sent");
            ((TokenizedEntityInterface) pig).steveparty$setNbSteps(0);
            controller.nextStep();
            context.assertEquals(controller.getLastLiveData().stepsLeft(), 0, "a new step: sent with it, up to date");
            context.setBlockState(CONTROLLER, Blocks.AIR);
            context.complete();
        } finally {
            context.getWorld().getServer().getPlayerManager().remove(player);
        }
    }
}
