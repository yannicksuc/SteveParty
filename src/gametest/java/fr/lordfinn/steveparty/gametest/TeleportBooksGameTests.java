package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.blocks.custom.TeleportationPadBlockEntity;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.items.custom.teleportation_books.HereWeGoBookItem;
import fr.lordfinn.steveparty.items.custom.teleportation_books.TeleportingTarget;
import fr.lordfinn.steveparty.persistent_state.TeleportationPadStorageManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static fr.lordfinn.steveparty.components.ModComponents.DESTINATIONS_COMPONENT;
import static fr.lordfinn.steveparty.components.ModComponents.STATE;
import static fr.lordfinn.steveparty.components.ModComponents.TP_TARGETS;

public class TeleportBooksGameTests implements FabricGameTest {
    /** The pad waits 10 ticks before teleporting. */
    private static final int TELEPORT_DELAY = 10;
    private static final double EPSILON = 1.0E-4;

    // ------------------------------------------------------------------ helpers

    /** A teleportation pad on stone, holding {@code book} (may be empty). */
    static TeleportationPadBlockEntity placePad(TestContext context, BlockPos pos, ItemStack book) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.TELEPORTATION_PAD);
        TeleportationPadBlockEntity pad = context.getBlockEntity(pos);
        if (!book.isEmpty()) pad.setBook(book);
        return pad;
    }

    static ItemStack hereWeGo(HereWeGoBookItem.State state, BlockPos... absoluteDestinations) {
        ItemStack book = new ItemStack(ModItems.HERE_WE_GO_BOOK);
        book.set(STATE, state.getValue());
        if (absoluteDestinations.length > 0)
            book.set(DESTINATIONS_COMPONENT, new DestinationsComponent(new ArrayList<>(List.of(absoluteDestinations)), ""));
        return book;
    }

    static ServerPlayerEntity playerAt(TestContext context, BlockPos relativePos, double feetOffset) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        moveTo(context, player, relativePos, feetOffset);
        return player;
    }

    static void moveTo(TestContext context, ServerPlayerEntity player, BlockPos relativePos, double feetOffset) {
        BlockPos abs = context.getAbsolutePos(relativePos);
        player.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY() + feetOffset, abs.getZ() + 0.5, 0, 0);
    }

    /** What the pad does every tick a player touches it. */
    static void stepOn(TestContext context, BlockPos padPos, ServerPlayerEntity player) {
        context.getBlockState(padPos).onEntityCollision(context.getWorld(), context.getAbsolutePos(padPos), player);
    }

    static void disconnect(TestContext context, ServerPlayerEntity player) {
        context.getWorld().getServer().getPlayerManager().remove(player);
    }

    static void assertFeetAt(TestContext context, ServerPlayerEntity player, BlockPos relativeBlock, double feetOffset, String what) {
        BlockPos abs = context.getAbsolutePos(relativeBlock);
        context.assertTrue(Math.abs(player.getX() - (abs.getX() + 0.5)) < EPSILON
                        && Math.abs(player.getZ() - (abs.getZ() + 0.5)) < EPSILON
                        && Math.abs(player.getY() - (abs.getY() + feetOffset)) < EPSILON,
                what + ": expected feet at " + abs + " +" + feetOffset + ", got " + player.getPos());
        context.assertTrue(context.getWorld().isSpaceEmpty(player, player.getBoundingBox().contract(1.0E-7)),
                what + ": the player is not inside a block");
    }

    // ------------------------------------------------------------------ saved positions land on top of the block

    /** A saved position is the clicked block: the player lands standing on it, not inside it. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void savedPositionLandsOnTopOfTheClickedBlock(TestContext context) {
        BlockPos padPos = new BlockPos(1, 1, 1);
        BlockPos clicked = new BlockPos(4, 1, 4);
        context.setBlockState(clicked, Blocks.STONE);
        placePad(context, padPos, hereWeGo(HereWeGoBookItem.State.TP_REGISTERED_POS, context.getAbsolutePos(clicked)));
        ServerPlayerEntity player = playerAt(context, padPos, 0.25);
        stepOn(context, padPos, player);
        context.waitAndRun(TELEPORT_DELAY + 2, () -> {
            try {
                assertFeetAt(context, player, clicked, 1.0, "on top of the stone");
            } finally {
                disconnect(context, player);
            }
            context.complete();
        });
    }

    /** Something right above the clicked block: the player lands in the nearest free space above. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void savedPositionUnderABlockLandsInTheFreeSpaceAbove(TestContext context) {
        BlockPos padPos = new BlockPos(1, 1, 1);
        BlockPos clicked = new BlockPos(4, 1, 4);
        context.setBlockState(clicked, Blocks.STONE);
        context.setBlockState(clicked.up(), Blocks.OAK_SLAB); // bottom slab: top at +0.5
        placePad(context, padPos, hereWeGo(HereWeGoBookItem.State.TP_REGISTERED_POS, context.getAbsolutePos(clicked)));
        ServerPlayerEntity player = playerAt(context, padPos, 0.25);
        stepOn(context, padPos, player);
        context.waitAndRun(TELEPORT_DELAY + 2, () -> {
            try {
                assertFeetAt(context, player, clicked.up(), 0.5, "on the slab above the clicked block");
            } finally {
                disconnect(context, player);
            }
            context.complete();
        });
    }

    /** A saved teleportation pad: the player stands on the pad (4 pixels high), not in it. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void savedPadPositionLandsOnThePad(TestContext context) {
        BlockPos padPos = new BlockPos(1, 1, 1);
        BlockPos targetPad = new BlockPos(5, 1, 5);
        placePad(context, targetPad, ItemStack.EMPTY);
        placePad(context, padPos, hereWeGo(HereWeGoBookItem.State.TP_REGISTERED_POS, context.getAbsolutePos(targetPad)));
        ServerPlayerEntity player = playerAt(context, padPos, 0.25);
        stepOn(context, padPos, player);
        context.waitAndRun(TELEPORT_DELAY + 2, () -> {
            try {
                assertFeetAt(context, player, targetPad, 0.25, "on the target pad");
            } finally {
                disconnect(context, player);
            }
            context.complete();
        });
    }

    // ------------------------------------------------------------------ mini-game mode

    private static final BlockPos CONTROLLER_POS = new BlockPos(7, 1, 7);
    private static final String MINI_GAME_BATCH = "teleport_books_mini_game";

    /** Removes the controller: a started party left in the world would be found by other tests. */
    static void endParty(TestContext context) {
        context.removeBlock(CONTROLLER_POS);
    }

    /** A party controller whose current step is a mini-game step ({@code chosen}: the roulette picked the mini-game). */
    static PartyControllerEntity placeMiniGameParty(TestContext context, boolean chosen, @Nullable ItemStack page,
                                                    @Nullable TeamDisposition disposition) {
        context.setBlockState(CONTROLLER_POS.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER_POS, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER_POS);
        NbtCompound stepNbt = new NbtCompound();
        stepNbt.putString("Status", PartyStep.Status.IN_PROGRESS.name());
        stepNbt.putString("Type", PartyStepType.MINI_GAME.name());
        if (chosen) stepNbt.putBoolean("MiniGameChosen", true);
        PartyData data = new PartyData();
        data.addStep(new MiniGamePartyStep(stepNbt));
        data.setStepIndex(0);
        controller.setPartyData(data);
        ItemStack catalogue = new ItemStack(ModItems.MINI_GAMES_CATALOGUE);
        if (page != null) MiniGamesCatalogueItem.setCurrentMiniGamePage(catalogue, page);
        if (disposition != null) MiniGamesCatalogueItem.setCurrentMiniGameTeamDisposition(catalogue, disposition);
        controller.catalogue = catalogue;
        return controller;
    }

    static ItemStack hereWeCome(TeleportingTarget... targets) {
        ItemStack book = new ItemStack(ModItems.HERE_WE_COME_BOOK);
        book.set(TP_TARGETS, List.of(targets));
        return book;
    }

    static ItemStack miniGamePage(TestContext context, BlockPos... relativePads) {
        ItemStack page = new ItemStack(ModItems.MINI_GAME_PAGE);
        List<BlockPos> destinations = new ArrayList<>();
        for (BlockPos pad : relativePads) destinations.add(context.getAbsolutePos(pad));
        page.set(DESTINATIONS_COMPONENT, new DestinationsComponent(destinations, ""));
        return page;
    }

    static PlayerEntity mockPlayerAt(TestContext context, BlockPos relativePos) {
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        BlockPos abs = context.getAbsolutePos(relativePos);
        player.setPosition(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        return player;
    }

    static String failureKey(HereWeGoBookItem.Destination destination) {
        if (destination.failure() == null || !(destination.failure().getContent() instanceof TranslatableTextContent content))
            return null;
        return content.getKey();
    }

    /** A fresh book (mini-game mode, the default) sends the players of the current mini-game to its pads. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = MINI_GAME_BATCH)
    public void freshBookSendsThePlayerToTheCurrentMiniGame(TestContext context) {
        BlockPos startPad = new BlockPos(1, 1, 1);
        BlockPos arenaPad = new BlockPos(5, 1, 2);
        placePad(context, arenaPad, hereWeCome(new TeleportingTarget(TeleportingTarget.Group.EVERYONE, 0, 0)));
        placePad(context, startPad, new ItemStack(ModItems.HERE_WE_GO_BOOK)); // no state: default mode
        ServerPlayerEntity player = playerAt(context, startPad, 0.25);
        placeMiniGameParty(context, true, miniGamePage(context, arenaPad),
                new TeamDisposition(new HashSet<>(), new HashSet<>(Set.of(player.getUuid()))));
        stepOn(context, startPad, player);
        context.waitAndRun(TELEPORT_DELAY + 2, () -> {
            try {
                assertFeetAt(context, player, arenaPad, 0.25, "on the pad of the mini-game");
            } finally {
                disconnect(context, player);
                endParty(context);
            }
            context.complete();
        });
    }

    /** Teams, capacities, fill priorities and spectators of the « Here we come » books. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = MINI_GAME_BATCH)
    public void miniGameBooksDispatchPlayersByGroupCapacityAndPriority(TestContext context) {
        try {
            BlockPos teamAPad = new BlockPos(1, 1, 5);
            BlockPos teamBFirstPad = new BlockPos(3, 1, 5);
            BlockPos teamBSecondPad = new BlockPos(5, 1, 5);
            BlockPos spectatorsPad = new BlockPos(5, 1, 1);
            // Team A conditions have more room than team B's: they are swapped (team A of a disposition is the smaller one)
            placePad(context, teamAPad, hereWeCome(new TeleportingTarget(TeleportingTarget.Group.PLAYER_TEAM_B, 1, 0)));
            placePad(context, teamBFirstPad, hereWeCome(new TeleportingTarget(TeleportingTarget.Group.PLAYER_TEAM_A, 1, 5)));
            placePad(context, teamBSecondPad, hereWeCome(new TeleportingTarget(TeleportingTarget.Group.PLAYER_TEAM_A, 0, 0)));
            placePad(context, spectatorsPad, hereWeCome(new TeleportingTarget(TeleportingTarget.Group.SPECTATORS, 0, 0)));

            BlockPos standing = new BlockPos(1, 1, 1);
            PlayerEntity alone = mockPlayerAt(context, standing);
            PlayerEntity first = mockPlayerAt(context, standing);
            PlayerEntity second = mockPlayerAt(context, standing);
            PlayerEntity third = mockPlayerAt(context, standing);
            PlayerEntity spectator = mockPlayerAt(context, standing);
            placeMiniGameParty(context, true, miniGamePage(context, teamAPad, teamBFirstPad, teamBSecondPad, spectatorsPad),
                    new TeamDisposition(new HashSet<>(Set.of(alone.getUuid())),
                            new HashSet<>(Set.of(first.getUuid(), second.getUuid(), third.getUuid()))));
            ItemStack book = new ItemStack(ModItems.HERE_WE_GO_BOOK);

            context.assertEquals(HereWeGoBookItem.getTpPos(book, alone), context.getAbsolutePos(teamAPad), "team A (smaller)");
            context.assertEquals(HereWeGoBookItem.getTpPos(book, first), context.getAbsolutePos(teamBFirstPad), "team B: higher priority first");
            context.assertEquals(HereWeGoBookItem.getTpPos(book, second), context.getAbsolutePos(teamBSecondPad), "team B: first place full");
            context.assertEquals(HereWeGoBookItem.getTpPos(book, third), context.getAbsolutePos(teamBSecondPad), "team B: no limit");
            context.assertEquals(HereWeGoBookItem.getTpPos(book, spectator), context.getAbsolutePos(spectatorsPad), "spectator");
            context.assertEquals(HereWeGoBookItem.getTpPos(book, first), context.getAbsolutePos(teamBFirstPad), "same place when coming back");
        } finally {
            endParty(context);
        }
        context.complete();
    }

    /** No room left for a player: no teleport, and he is told why. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = MINI_GAME_BATCH)
    public void miniGameWithoutRoomForThePlayerSaysSo(TestContext context) {
        try {
            BlockPos pad = new BlockPos(5, 1, 5);
            placePad(context, pad, hereWeCome(new TeleportingTarget(TeleportingTarget.Group.PLAYERS, 1, 0)));
            PlayerEntity first = mockPlayerAt(context, new BlockPos(1, 1, 1));
            PlayerEntity second = mockPlayerAt(context, new BlockPos(1, 1, 1));
            PlayerEntity spectator = mockPlayerAt(context, new BlockPos(1, 1, 1));
            placeMiniGameParty(context, true, miniGamePage(context, pad),
                    new TeamDisposition(new HashSet<>(), new HashSet<>(Set.of(first.getUuid(), second.getUuid()))));
            ItemStack book = new ItemStack(ModItems.HERE_WE_GO_BOOK);
            context.assertEquals(HereWeGoBookItem.getTpPos(book, first), context.getAbsolutePos(pad), "first player");
            HereWeGoBookItem.Destination full = HereWeGoBookItem.getDestination(book, second);
            context.assertTrue(full.pos() == null, "capacity reached");
            context.assertEquals(failureKey(full), "message.steveparty.here_we_go.no_room", "message");
            context.assertEquals(failureKey(HereWeGoBookItem.getDestination(book, spectator)), "message.steveparty.here_we_go.no_room",
                    "spectators don't fill a « players » condition");
        } finally {
            endParty(context);
        }
        context.complete();
    }

    /** No mini-game running (or not chosen yet): no teleport, a message instead of nothing. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = MINI_GAME_BATCH)
    public void noMiniGameInProgressIsExplained(TestContext context) {
        try {
            BlockPos pad = new BlockPos(5, 1, 5);
            placePad(context, pad, hereWeCome(new TeleportingTarget(TeleportingTarget.Group.EVERYONE, 0, 0)));
            PlayerEntity player = mockPlayerAt(context, new BlockPos(1, 1, 1));
            ItemStack book = new ItemStack(ModItems.HERE_WE_GO_BOOK);

            // Roulette still running
            PartyControllerEntity controller = placeMiniGameParty(context, false, miniGamePage(context, pad), null);
            context.assertEquals(failureKey(HereWeGoBookItem.getDestination(book, player)),
                    "message.steveparty.here_we_go.minigame_not_chosen", "not chosen yet");

            // Another step of the party
            PartyData data = new PartyData();
            data.addStep(new PartyStep());
            data.setStepIndex(0);
            controller.setPartyData(data);
            HereWeGoBookItem.Destination destination = HereWeGoBookItem.getDestination(book, player);
            context.assertTrue(destination.pos() == null, "no destination");
            context.assertEquals(failureKey(destination), "message.steveparty.here_we_go.no_minigame", "no mini-game");
        } finally {
            endParty(context);
        }
        context.complete();
    }

    // ------------------------------------------------------------------ "just arrived" guard

    /**
     * « Last used » brings the player back onto a pad that has a « Here we go » book: he stays there until he steps
     * off (no immediate bounce), then that pad works again.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void playerSentBackOntoAPadIsNotBouncedUntilHeStepsOff(TestContext context) {
        BlockPos lastUsedPad = new BlockPos(1, 1, 1);
        BlockPos previousPad = new BlockPos(5, 1, 5);
        BlockPos previousPadTarget = new BlockPos(1, 1, 5);
        context.setBlockState(previousPadTarget, Blocks.STONE);
        placePad(context, previousPad, hereWeGo(HereWeGoBookItem.State.TP_REGISTERED_POS, context.getAbsolutePos(previousPadTarget)));
        placePad(context, lastUsedPad, hereWeGo(HereWeGoBookItem.State.TP_BACK_LAST_USED_TP_PAD));

        ServerPlayerEntity player = playerAt(context, lastUsedPad, 0.25);
        // He used the previous pad before
        TeleportationPadStorageManager.getTeleportationHistoryStorage(context.getWorld())
                .addTeleportation(player.getUuid(), context.getAbsolutePos(previousPad), context.getAbsolutePos(previousPadTarget));
        stepOn(context, lastUsedPad, player);

        context.waitAndRun(TELEPORT_DELAY + 2, () -> {
            try {
                assertFeetAt(context, player, previousPad, 0.25, "back on the previous pad");
                // Touching the pad he just landed on (every tick) doesn't send him away
                for (int i = 0; i < 3; i++) stepOn(context, previousPad, player);
            } catch (RuntimeException e) {
                disconnect(context, player);
                throw e;
            }
            context.waitAndRun(TELEPORT_DELAY + 2, () -> {
                try {
                    assertFeetAt(context, player, previousPad, 0.25, "still on the previous pad");
                    // Steps off, then back on: the pad teleports him again
                    moveTo(context, player, previousPad.north(2), 0);
                } catch (RuntimeException e) {
                    disconnect(context, player);
                    throw e;
                }
                context.waitAndRun(2, () -> {
                    try {
                        moveTo(context, player, previousPad, 0.25);
                        stepOn(context, previousPad, player);
                    } catch (RuntimeException e) {
                        disconnect(context, player);
                        throw e;
                    }
                    context.waitAndRun(TELEPORT_DELAY + 2, () -> {
                        try {
                            assertFeetAt(context, player, previousPadTarget, 1.0, "sent by the previous pad once he came back on it");
                        } finally {
                            disconnect(context, player);
                        }
                        context.complete();
                    });
                });
            });
        });
    }
}
