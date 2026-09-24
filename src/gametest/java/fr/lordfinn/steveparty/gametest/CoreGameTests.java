package fr.lordfinn.steveparty.gametest;

import com.mojang.serialization.DataResult;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.BasicGameGeneratorStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.StepControllerBlockEntity;
import fr.lordfinn.steveparty.commands.PartyCommands;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.TokenizerWandItem;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class CoreGameTests implements FabricGameTest {

    /** A task scheduling another task from its callback used to crash the server (ConcurrentModificationException). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void schedulerAcceptsTasksScheduledFromTasks(TestContext context) {
        AtomicBoolean first = new AtomicBoolean();
        AtomicBoolean second = new AtomicBoolean();
        Steveparty.SCHEDULER.schedule(UUID.randomUUID(), 1, () -> {
            first.set(true);
            Steveparty.SCHEDULER.schedule(UUID.randomUUID(), 1, () -> second.set(true));
        });
        context.waitAndRun(5, () -> {
            context.assertTrue(first.get(), "first task ran");
            context.assertTrue(second.get(), "task scheduled from a task ran");
            context.complete();
        });
    }

    /** Saving/loading a regular mob must not touch its NoAI / Invulnerable flags (token mixin regression). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nonTokenMobsKeepTheirFlagsThroughSaveAndLoad(TestContext context) {
        PigEntity pig = context.spawnMob(EntityType.PIG, new BlockPos(1, 1, 1));
        pig.setAiDisabled(true);
        pig.setInvulnerable(true);
        pig.setCustomNameVisible(true);
        NbtCompound nbt = new NbtCompound();
        pig.writeNbt(nbt);
        context.assertTrue(!nbt.contains("Tokenized"), "no token data written for a regular mob");

        PigEntity reloaded = EntityType.PIG.create(context.getWorld(), SpawnReason.LOAD);
        context.assertTrue(reloaded != null, "pig created");
        reloaded.readNbt(nbt);
        context.assertTrue(reloaded.isAiDisabled(), "NoAI kept");
        context.assertTrue(reloaded.isInvulnerable(), "Invulnerable kept");
        context.assertTrue(reloaded.isCustomNameVisible(), "CustomNameVisible kept");
        context.complete();
    }

    /** Ghost quantities above 99 (cartridge slots) must survive serialization. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void inventoryComponentKeepsBigCounts(TestContext context) {
        InventoryComponent component = new InventoryComponent(List.of(new ItemStack(Items.DIAMOND, 500), ItemStack.EMPTY));
        RegistryOps<NbtElement> ops = context.getWorld().getRegistryManager().getOps(NbtOps.INSTANCE);
        DataResult<NbtElement> encoded = InventoryComponent.CODEC.encodeStart(ops, component);
        context.assertTrue(encoded.isSuccess(), "encodes: " + encoded);
        InventoryComponent decoded = InventoryComponent.CODEC.parse(ops, encoded.getOrThrow()).getOrThrow();
        context.assertEquals(decoded.getStack(0).getCount(), 500, "count");
        context.assertTrue(decoded.getStack(1).isEmpty(), "empty slot kept");
        context.assertEquals(decoded, component, "value equality");
        context.complete();
    }

    /** Starting a party with no start tile around must not crash nor leave a broken party. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bootingEmptyPartyDoesNotCrash(TestContext context) {
        BlockPos controller = new BlockPos(3, 1, 3);
        context.setBlockState(controller.down(), Blocks.STONE);
        context.setBlockState(controller, ModBlocks.PARTY_CONTROLLER);
        context.setBlockState(controller.east(), Blocks.REDSTONE_BLOCK);
        context.waitAndRun(40, context::complete);
    }

    private static PartyControllerEntity placeController(TestContext context, BlockPos pos) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.PARTY_CONTROLLER);
        return context.getBlockEntity(pos);
    }

    private static TokenizedEntityInterface spawnToken(TestContext context, BlockPos pos, @Nullable UUID owner) {
        PigEntity pig = context.spawnMob(EntityType.PIG, pos);
        TokenizedEntityInterface token = (TokenizedEntityInterface) pig;
        token.steveparty$setTokenized(true);
        token.steveparty$setTokenOwner(owner);
        token.steveparty$setStatus(TokenStatus.IN_GAME);
        return token;
    }

    private static UUID uuid(TokenizedEntityInterface token) {
        return ((Entity) token).getUuid();
    }

    /** Excluding tokens removes their next turns only: the current step and the step index stay consistent. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void excludingTokenKeepsStepIndexConsistent(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PartyControllerEntity controller = placeController(context, pos);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        PartyData data = new PartyData();
        List.of(a, b, c).forEach(data::addToken);
        data.addStep(new PartyStep());
        for (int turn = 0; turn < 2; turn++)
            for (UUID token : List.of(a, b, c))
                data.addStep(new TokenTurnPartyStep(token, null));
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(a, b, c))));
        controller.setPartyData(data);
        controller.nextStep();
        controller.nextStep();

        // Unloaded tokens: the turn waits for them instead of being skipped
        context.assertEquals(data.getStepIndex(), 1, "turn of a");
        TokenTurnPartyStep turnOfA = (TokenTurnPartyStep) data.getCurrentStep();
        context.assertTrue(turnOfA.isWaitingForAbsentToken(), "absent token countdown");

        context.assertTrue(controller.excludeToken(b), "b excluded");
        context.assertEquals(data.getSteps().size(), 6, "the 2 turns of b removed");
        context.assertEquals(data.getStepIndex(), 1, "index unchanged");
        context.assertTrue(data.getCurrentStep() == turnOfA, "still the turn of a");
        context.assertTrue(turnOfA.isWaitingForAbsentToken(), "countdown still running");

        // Excluding the token whose turn it is goes on with the next remaining turn
        context.assertTrue(controller.excludeToken(a), "a excluded");
        context.assertEquals(data.getSteps().size(), 5, "next turn of a removed, current kept");
        context.assertEquals(data.getStepIndex(), 2, "moved to the next step");
        context.assertTrue(data.getCurrentStep() instanceof TokenTurnPartyStep turn && c.equals(turn.getTokenUUID()), "turn of c");
        context.assertEquals(data.getTokens(), List.of(c), "remaining tokens");
        context.assertTrue(!controller.excludeToken(a), "a no longer in the party");

        // Skipping an absent turn
        TokenTurnPartyStep turnOfC = (TokenTurnPartyStep) data.getCurrentStep();
        context.assertTrue(turnOfC.skipAbsentTurn(controller), "skipped");
        context.assertEquals(data.getStepIndex(), 3, "second turn of c");
        context.removeBlock(pos);
        context.complete();
    }

    /** /steveparty exclude: own token only, unless holding a Tokenizer Wand enchanted with Game Master (any token). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void gameMasterWandMayExcludeAnyToken(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PartyControllerEntity controller = placeController(context, pos);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        UUID othersToken = UUID.randomUUID(), ownToken = UUID.randomUUID();
        PartyData data = new PartyData();
        data.addToken(othersToken);
        data.addToken(ownToken);
        data.addStep(new PartyStep());
        data.addStep(new TokenTurnPartyStep(othersToken, UUID.randomUUID()));
        data.addStep(new TokenTurnPartyStep(ownToken, player.getUuid()));
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(othersToken, ownToken))));
        controller.setPartyData(data);

        context.assertTrue(PartyCommands.canExcludeToken(player, controller, ownToken), "own token");
        context.assertTrue(!PartyCommands.canExcludeToken(player, controller, othersToken), "another player's token refused");

        ItemStack plainWand = new ItemStack(ModItems.TOKENIZER_WAND);
        player.setStackInHand(Hand.OFF_HAND, plainWand);
        context.assertTrue(!PartyCommands.canExcludeToken(player, controller, othersToken), "plain wand is not enough");

        RegistryEntry<Enchantment> gameMaster = context.getWorld().getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(TokenizerWandItem.GAME_MASTER);
        ItemStack enchantedStick = new ItemStack(Items.STICK);
        enchantedStick.addEnchantment(gameMaster, 1);
        player.setStackInHand(Hand.MAIN_HAND, enchantedStick);
        context.assertTrue(!PartyCommands.canExcludeToken(player, controller, othersToken), "only a Tokenizer Wand counts");

        ItemStack gameMasterWand = new ItemStack(ModItems.TOKENIZER_WAND);
        gameMasterWand.addEnchantment(gameMaster, 1);
        player.setStackInHand(Hand.OFF_HAND, gameMasterWand);
        context.assertTrue(PartyCommands.holdsGameMasterWand(player), "game master wand held (off hand)");
        context.assertTrue(PartyCommands.canExcludeToken(player, controller, othersToken), "game master may exclude any token");
        context.removeBlock(pos);
        context.complete();
    }

    /** An absent token gets its turn back (CAN_MOVE) as soon as it becomes available before the delay ends. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void absentTokenComingBackPlaysItsTurn(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PartyControllerEntity controller = placeController(context, pos);
        // Owned by an offline player: absent
        TokenizedEntityInterface token = spawnToken(context, new BlockPos(4, 1, 4), UUID.randomUUID());
        PartyData data = new PartyData();
        data.addToken(uuid(token));
        data.addStep(new PartyStep());
        data.addStep(new TokenTurnPartyStep(uuid(token), token.steveparty$getTokenOwner()));
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(uuid(token)))));
        controller.setPartyData(data);
        controller.nextStep();
        controller.nextStep();
        TokenTurnPartyStep turn = (TokenTurnPartyStep) data.getCurrentStep();
        context.assertTrue(turn.isWaitingForAbsentToken(), "owner offline: countdown");
        context.assertTrue(!TokenStatus.hasStatus(token.steveparty$getStatus(), TokenStatus.CAN_MOVE), "cannot move yet");

        // An ownerless token can be played by anyone: it is available
        token.steveparty$setTokenOwner((UUID) null);
        context.waitAndRun(25, () -> {
            context.assertTrue(!turn.isWaitingForAbsentToken(), "countdown cancelled");
            context.assertEquals(data.getStepIndex(), 1, "turn not skipped");
            context.assertTrue(TokenStatus.canMoveInGame(token.steveparty$getStatus()), "can move");
            context.removeBlock(pos);
            context.complete();
        });
    }

    /**
     * An owner leaving during their own turn starts the absent countdown, but only once the token stopped moving;
     * the turn goes on if they come back before the end.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void ownerLeavingDuringTurnStartsCountdownAfterMove(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PartyControllerEntity controller = placeController(context, pos);
        TokenizedEntityInterface token = spawnToken(context, new BlockPos(4, 1, 4), null);
        PartyData data = new PartyData();
        data.addToken(uuid(token));
        data.addStep(new PartyStep());
        data.addStep(new TokenTurnPartyStep(uuid(token), null));
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(uuid(token)))));
        controller.setPartyData(data);
        controller.nextStep();
        controller.nextStep();
        TokenTurnPartyStep turn = (TokenTurnPartyStep) data.getCurrentStep();
        context.assertTrue(!turn.isWaitingForAbsentToken(), "available token: turn played");

        // Mid-move when the owner (now an offline player) leaves: the movement is not interrupted
        token.steveparty$setNbSteps(2);
        token.steveparty$setTokenOwner(UUID.randomUUID());
        context.waitAndRun(25, () -> {
            context.assertTrue(!turn.isWaitingForAbsentToken(), "no countdown while moving");
            token.steveparty$setNbSteps(0);
            context.waitAndRun(25, () -> {
                context.assertTrue(turn.isWaitingForAbsentToken(), "countdown once stopped");
                token.steveparty$setTokenOwner((UUID) null);
                context.waitAndRun(25, () -> {
                    context.assertTrue(!turn.isWaitingForAbsentToken(), "countdown cancelled");
                    context.assertEquals(data.getStepIndex(), 1, "turn goes on");
                    context.assertTrue(TokenStatus.canMoveInGame(token.steveparty$getStatus()), "can move");
                    context.removeBlock(pos);
                    context.complete();
                });
            });
        });
    }

    /** The generator creates the turns of every token of the party, even the ones not loaded. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void generatorIncludesUnloadedTokens(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PartyControllerEntity controller = placeController(context, pos);
        TokenizedEntityInterface loaded = spawnToken(context, new BlockPos(4, 1, 4), null);
        UUID unloaded = UUID.randomUUID();
        PartyData data = new PartyData();
        data.addToken(uuid(loaded));
        data.addToken(unloaded);
        data.setNbTurn(1);
        data.addStep(new BasicGameGeneratorStep());
        controller.setPartyData(data);
        controller.nextStep();
        // generator, 2 turns, mini-game, end
        context.assertEquals(data.getSteps().size(), 5, "steps generated");
        context.assertTrue(data.getSteps().get(2) instanceof TokenTurnPartyStep turn && unloaded.equals(turn.getTokenUUID()),
                "turn of the unloaded token");
        context.removeBlock(pos);
        context.complete();
    }

    /** A step controller in "previous" mode brings a party back from its END step, tokens in game again. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stepControllerGoesBackFromEnd(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PartyControllerEntity controller = placeController(context, pos);
        TokenizedEntityInterface token = spawnToken(context, new BlockPos(4, 1, 4), null);
        PartyData data = new PartyData();
        data.addToken(uuid(token));
        data.addStep(new PartyStep());
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(uuid(token)))));
        controller.setPartyData(data);
        controller.nextStep();
        controller.nextStep();
        context.assertTrue(!data.isStarted() && data.isAtEnd(), "party over");
        context.assertTrue(!TokenStatus.isInGame(token.steveparty$getStatus()), "token released");

        BlockPos stepControllerPos = pos.east(2);
        context.setBlockState(stepControllerPos.down(), Blocks.STONE);
        context.setBlockState(stepControllerPos, ModBlocks.STEP_CONTROLLER);
        StepControllerBlockEntity stepController = context.getBlockEntity(stepControllerPos);
        stepController.mode = 2; // previous
        context.setBlockState(stepControllerPos.south(), Blocks.REDSTONE_BLOCK);
        context.waitAndRun(5, () -> {
            context.assertEquals(data.getStepIndex(), 0, "back to the previous step");
            context.assertTrue(data.isStarted(), "party running again");
            context.assertTrue(TokenStatus.isInGame(token.steveparty$getStatus()), "token in game again");
            context.removeBlock(pos);
            context.complete();
        });
    }
}
