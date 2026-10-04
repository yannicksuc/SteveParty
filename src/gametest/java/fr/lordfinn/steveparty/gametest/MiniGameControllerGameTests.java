package fr.lordfinn.steveparty.gametest;

import com.mojang.authlib.GameProfile;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.MiniGameControllerBlock;
import fr.lordfinn.steveparty.blocks.custom.MiniGameControllerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlock;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlock;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeSolid;
import fr.lordfinn.steveparty.components.MiniGamePageRef;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.minigame.PageZone;
import fr.lordfinn.steveparty.minigame.ZoneFaces;
import fr.lordfinn.steveparty.minigame.PageZoneTool;
import fr.lordfinn.steveparty.minigame.MiniGameArena;
import fr.lordfinn.steveparty.minigame.MiniGamePageNetworking;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.minigame.MiniGameControllers;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import fr.lordfinn.steveparty.minigame.MiniGamePodiumLink;
import fr.lordfinn.steveparty.minigame.MiniGameResults;
import fr.lordfinn.steveparty.minigame.MiniGameSession;
import fr.lordfinn.steveparty.minigame.MiniGameTest;
import fr.lordfinn.steveparty.minigame.MiniGameReturns;
import fr.lordfinn.steveparty.minigame.PartyMiniGameSession;
import fr.lordfinn.steveparty.podium.Podiums;
import fr.lordfinn.steveparty.screen_handlers.custom.MiniGameControllerScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.MiniGameControllerScreenHandler.State;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Mini-game Controller: its page going in and out (one controller per page), « Play » out of a party, and in a
 * party the practice round, the ready vote and the real round (the only one that pays). And the page's zone: drawn in
 * zone mode by clicks and face moves the server decides, shared by the page's copies, read by every reader of the
 * zone, and the zone of an old Zone Cartridge moved into its page.
 */
public class MiniGameControllerGameTests implements FabricGameTest {
    private static final int GREEN = 13;
    private static final AtomicInteger SERIAL = new AtomicInteger();
    private static final BlockPos HOME = new BlockPos(6, 1, 6), PARTY = new BlockPos(0, 1, 7);

    // ------------------------------------------------------------------ helpers

    private static BlockPos pipe(TestContext context, int color, int x, int z) {
        context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
        BlockPos pos = new BlockPos(x, 2, z);
        context.setBlockState(pos, ModBlocks.PIPES[PipeKind.OPAQUE.ordinal()][color].getDefaultState().with(PipeBlock.SOLID, PipeSolid.DOWN));
        return pos;
    }

    private static GlobalPos global(TestContext context, BlockPos relative) {
        return GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(relative));
    }

    private static UUID page(TestContext context, BlockPos... pipes) {
        MinecraftServer server = context.getWorld().getServer();
        UUID id = UUID.randomUUID();
        MiniGamePages.update(server, MiniGamePageData.empty(id).withTexts("Manche " + SERIAL.incrementAndGet(), ""));
        for (BlockPos pipe : pipes) {
            MiniGamePages.toggleLink(server, id, global(context, pipe), Direction.UP, MiniGamePipeRole.ofPipe(context.getBlockState(pipe)));
        }
        return id;
    }

    private static ItemStack pageItem(UUID id) {
        ItemStack stack = new ItemStack(ModItems.MINI_GAME_PAGE);
        stack.set(ModComponents.MINI_GAME_PAGE, new MiniGamePageRef(id, "", false));
        return stack;
    }

    private static BlockPos podium(TestContext context, UUID page, int x, int z, int halves) {
        BlockPos bottom = new BlockPos(x, 1, z);
        if (halves >= 2) context.setBlockState(bottom, ModBlocks.PODIUM.getDefaultState().with(PodiumBlock.FULL, true));
        else context.setBlockState(bottom, ModBlocks.GOLD_PODIUM.getDefaultState());
        MiniGamePages.addPodiumLink(context.getWorld().getServer(), page, new MiniGamePodiumLink(global(context, bottom), MiniGamePodiumLink.Kind.PODIUM));
        return bottom;
    }

    private static UUID occupant(TestContext context, BlockPos podium) {
        PodiumBlockEntity entity = context.getBlockEntity(podium);
        return entity.getOccupant() == null ? null : entity.getOccupant().player();
    }

    private static ServerPlayerEntity player(TestContext context, String name, double x, double y, double z) {
        ServerWorld world = context.getWorld();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "c" + SERIAL.incrementAndGet() + name);
        ConnectedClientData data = ConnectedClientData.createDefault(profile, false);
        ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, profile, data.syncedOptions()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new EmbeddedChannel(connection);
        world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
        player.changeGameMode(GameMode.CREATIVE);
        player.getInventory().clear();
        Vec3d abs = context.getAbsolute(new Vec3d(x, y, z));
        player.refreshPositionAndAngles(abs.x, abs.y, abs.z, 0, 0);
        return player;
    }

    /** The players other tests left around are sent away: they would be recruited like anyone near a pipe. */
    private static void alone(TestContext context, ServerPlayerEntity... mine) {
        List<ServerPlayerEntity> own = List.of(mine);
        Vec3d center = context.getAbsolute(new Vec3d(4, 2, 4));
        for (ServerPlayerEntity other : new ArrayList<>(context.getWorld().getServer().getPlayerManager().getPlayerList())) {
            if (!own.contains(other) && other.getPos().squaredDistanceTo(center) < 40 * 40) context.getWorld().getServer().getPlayerManager().remove(other);
        }
    }

    private static void cleanUp(TestContext context, UUID page, ServerPlayerEntity... players) {
        MiniGameTest.stop(page);
        if (context.getBlockState(PARTY).isOf(ModBlocks.PARTY_CONTROLLER)) context.removeBlock(PARTY);
        if (context.getBlockState(HOME).isOf(ModBlocks.MINI_GAME_CONTROLLER)) context.removeBlock(HOME);
        for (ServerPlayerEntity player : players) {
            if (player.hasVehicle()) player.stopRiding();
            MiniGamePipes.leaveParty(player.getUuid());
            if (context.getWorld().getServer().getPlayerManager().getPlayer(player.getUuid()) != null)
                context.getWorld().getServer().getPlayerManager().remove(player);
        }
    }

    /** A Mini-game Controller at {@code pos}, holding the page when {@code page} is not null. */
    private static MiniGameControllerBlockEntity home(TestContext context, BlockPos pos, UUID page) {
        context.setBlockState(pos, ModBlocks.MINI_GAME_CONTROLLER);
        MiniGameControllerBlockEntity controller = context.getBlockEntity(pos);
        if (page != null) controller.setPage(pageItem(page));
        return controller;
    }

    private static BlockHitResult hit(TestContext context, BlockPos pos) {
        BlockPos abs = context.getAbsolutePos(pos);
        return new BlockHitResult(Vec3d.ofCenter(abs), Direction.UP, abs, false);
    }

    /** A party whose mini-game step, on {@code pageId}, is at {@code phase} with {@code participants}. */
    private static PartyControllerEntity party(TestContext context, UUID pageId, MiniGamePartyStep.Phase phase, ServerPlayerEntity... participants) {
        context.setBlockState(PARTY.down(), Blocks.STONE);
        context.setBlockState(PARTY, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(PARTY);
        NbtCompound stepNbt = new NbtCompound();
        stepNbt.putString("Type", PartyStepType.MINI_GAME.name());
        stepNbt.putString("Status", PartyStep.Status.IN_PROGRESS.name());
        stepNbt.putString("Phase", phase.name());
        stepNbt.putBoolean("MiniGameChosen", true);
        NbtList list = new NbtList();
        List<UUID> uuids = new ArrayList<>();
        for (ServerPlayerEntity player : participants) {
            list.add(NbtString.of(player.getUuid().toString()));
            uuids.add(player.getUuid());
        }
        stepNbt.put("Participants", list);
        UUID token = UUID.randomUUID();
        PartyData data = new PartyData();
        data.addToken(token);
        data.addStep(new PartyStep());
        data.addStep(new MiniGamePartyStep(stepNbt));
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(token))));
        data.setStepIndex(1);
        controller.setPartyData(data);
        ItemStack catalogue = new ItemStack(ModItems.MINI_GAMES_CATALOGUE);
        MiniGamesCatalogueItem.setCurrentMiniGamePage(catalogue, pageItem(pageId));
        MiniGamesCatalogueItem.setCurrentMiniGameTeamDisposition(catalogue, TeamDisposition.freeForAll(uuids));
        controller.catalogue = catalogue;
        // A well stocked bank: the gains are taken from it
        BankFixtures.stock(context, controller, PARTY.up(), 640, 64);
        return controller;
    }

    private static MiniGamePartyStep step(PartyControllerEntity controller) {
        return (MiniGamePartyStep) controller.getPartyData().getCurrentStep();
    }

    private static int coins(ServerPlayerEntity player) {
        return player.getInventory().count(PartyCurrency.COIN.defaultStack().getItem());
    }

    // ------------------------------------------------------------------ the page in the controller

    /**
     * A click with a page puts it in, a sneaking click with empty hands takes it back; the controller is then the
     * home of the page, and a second controller refuses a linked copy of it (by a click or in its screen). Adventure
     * players change nothing. A broken controller drops its page and frees it.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_controller_page")
    public void aPageGoesInAndOutOfItsController(TestContext context) {
        ServerPlayerEntity player = player(context, "a", 4.5, 1, 4.5);
        MinecraftServer server = context.getWorld().getServer();
        ServerWorld world = context.getWorld();
        UUID id = page(context);
        BlockPos other = new BlockPos(2, 1, 6);
        try {
            MiniGameControllerBlockEntity controller = home(context, HOME, null);
            context.assertTrue(!MiniGameControllers.has(server, id), "no controller holds the page yet");

            // Adventure: nothing goes in
            player.changeGameMode(GameMode.ADVENTURE);
            player.setStackInHand(Hand.MAIN_HAND, pageItem(id));
            context.getBlockState(HOME).onUseWithItem(player.getMainHandStack(), world, player, Hand.MAIN_HAND, hit(context, HOME));
            context.assertTrue(controller.getPage().isEmpty() && !player.getMainHandStack().isEmpty(), "Adventure: the page stays in hand");
            player.changeGameMode(GameMode.CREATIVE);

            // A click with the page: in
            context.getBlockState(HOME).onUseWithItem(player.getMainHandStack(), world, player, Hand.MAIN_HAND, hit(context, HOME));
            context.assertTrue(id.equals(controller.getPageId()) && player.getMainHandStack().isEmpty(), "the page went in");
            context.assertEquals(MiniGameControllers.of(server, id), Optional.of(global(context, HOME)), "the controller is the home of the page");

            // A second controller refuses a linked copy
            MiniGameControllerBlockEntity second = home(context, other, null);
            player.setStackInHand(Hand.MAIN_HAND, pageItem(id));
            context.getBlockState(other).onUseWithItem(player.getMainHandStack(), world, player, Hand.MAIN_HAND, hit(context, other));
            context.assertTrue(second.getPage().isEmpty() && !player.getMainHandStack().isEmpty(), "one controller per page: refused");
            MiniGameControllerScreenHandler screen = new MiniGameControllerScreenHandler(1, player.getInventory(), second);
            context.assertTrue(!screen.getSlot(MiniGameControllerScreenHandler.SLOT_PAGE).canInsert(player.getMainHandStack()), "refused in its screen too");
            context.assertEquals(screen.state(), State.NO_PAGE, "its screen says it has no page");
            UUID free = page(context);
            context.assertTrue(screen.getSlot(MiniGameControllerScreenHandler.SLOT_PAGE).canInsert(pageItem(free)), "another page is fine");
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);

            // Sneaking, empty hands: back
            player.setSneaking(true);
            context.getBlockState(HOME).onUse(world, player, hit(context, HOME));
            player.setSneaking(false);
            context.assertTrue(controller.getPage().isEmpty() && id.equals(MiniGamePages.idOf(player.getMainHandStack())), "the page came back in hand");
            context.assertTrue(!MiniGameControllers.has(server, id), "the page has no home any more");

            // Now the second one takes it; saved and read again, it still holds it
            context.getBlockState(other).onUseWithItem(player.getMainHandStack(), world, player, Hand.MAIN_HAND, hit(context, other));
            context.assertEquals(MiniGameControllers.of(server, id), Optional.of(global(context, other)), "the second controller is now its home");
            NbtCompound saved = second.createNbt(world.getRegistryManager());
            MiniGameControllerBlockEntity read = new MiniGameControllerBlockEntity(context.getAbsolutePos(other), context.getBlockState(other));
            read.read(saved, world.getRegistryManager());
            context.assertEquals(read.getPageId(), id, "the page is saved with the controller");

            // Broken: the page drops, and is free again
            context.removeBlock(other);
            context.assertTrue(!MiniGameControllers.has(server, id), "a broken controller frees its page");
            context.expectItem(ModItems.MINI_GAME_PAGE);
        } finally {
            if (context.getBlockState(other).isOf(ModBlocks.MINI_GAME_CONTROLLER)) context.removeBlock(other);
            cleanUp(context, id, player);
        }
        context.complete();
    }

    // ------------------------------------------------------------------ out of a party

    /**
     * « Play » on the controller's screen: the mini-game played out of any party with those near its pipes, « Stop »
     * to end it; its results say nothing is paid because no party plays.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_controller_play", tickLimit = 100)
    public void playStartsTheMiniGameOutOfAParty(TestContext context) {
        BlockPos green = pipe(context, GREEN, 1, 1);
        ServerPlayerEntity p1 = player(context, "a", 1.5, 1, 2.5), p2 = player(context, "b", 2.5, 1, 1.5);
        MinecraftServer server = context.getWorld().getServer();
        UUID id = page(context, green);
        try {
            alone(context, p1, p2);
            BlockPos first = podium(context, id, 4, 3, 2), second = podium(context, id, 5, 3, 1);
            MiniGameControllerBlockEntity controller = home(context, HOME, id);
            MiniGameControllerScreenHandler screen = new MiniGameControllerScreenHandler(1, p1.getInventory(), controller);
            context.assertTrue(screen.state() == State.READY && screen.players() == 2, "two players near the pipe: it can be played");
            context.assertTrue(!screen.onButtonClick(p1, MiniGameControllerScreenHandler.BUTTON_READY), "no vote out of a party");
            context.assertTrue(screen.onButtonClick(p1, MiniGameControllerScreenHandler.BUTTON_PLAY), "Play");
            MiniGameTest played = MiniGameTest.of(id);
            context.assertTrue(played != null && played.phase() == MiniGameTest.Phase.COUNTDOWN, "its card and countdown, as the editor's button");
            context.assertEquals(screen.state(), State.RUNNING, "the screen says it is being played");
            context.assertTrue(screen.onButtonClick(p2, MiniGameControllerScreenHandler.BUTTON_PLAY), "Stop");
            context.assertTrue(MiniGameTest.of(id) == null && screen.state() == State.READY, "stopped");

            // Played to its podiums: the results are not paid, because no party plays
            context.assertEquals(MiniGameTest.start(server, id, p1, 0), MiniGameTest.Status.READY, "played again, without the countdown");
            played = MiniGameTest.of(id);
            Podiums.toggle(p1, context.getWorld(), context.getAbsolutePos(first));
            Podiums.toggle(p2, context.getWorld(), context.getAbsolutePos(second));
            context.assertTrue(played.results() != null && played.results().kind() == MiniGameResults.Kind.NO_PARTY, "results of a mini-game without party");
            context.assertEquals(coins(p1), 0, "nothing is paid");

            // An empty controller plays nothing
            controller.setPage(ItemStack.EMPTY);
            MiniGameTest.stop(id);
            context.assertTrue(!screen.onButtonClick(p1, MiniGameControllerScreenHandler.BUTTON_PLAY) && MiniGameTest.of(id) == null, "no page: nothing to play");
        } finally {
            cleanUp(context, id, p1, p2);
        }
        context.complete();
    }

    // ------------------------------------------------------------------ in a party

    /**
     * A party draws a page that has its controller: a practice round first (played for nothing, started again after
     * its results), each player votes, and once everyone is ready the real round starts and is the only one paid.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_controller_practice", tickLimit = 300)
    public void aPracticeRoundThenTheVoteThenTheRealRound(TestContext context) {
        BlockPos green = pipe(context, GREEN, 1, 1);
        ServerPlayerEntity p1 = player(context, "a", 6.5, 1, 1.5), p2 = player(context, "b", 6.5, 1, 2.5), watcher = player(context, "w", 6.5, 1, 3.5);
        ServerWorld world = context.getWorld();
        UUID id = page(context, green);
        BlockPos first = podium(context, id, 4, 3, 2), second = podium(context, id, 5, 3, 1);
        PartyControllerEntity controller;
        MiniGamePartyStep step;
        try {
            alone(context, p1, p2, watcher);
            MiniGameControllerBlockEntity home = home(context, HOME, id);
            controller = party(context, id, MiniGamePartyStep.Phase.COUNTDOWN, p1, p2);
            step = step(controller);
            step.leaveForMiniGame(controller);
            context.assertTrue(step.isPractice() && !step.isPlaying(), "the page has a controller: a practice round first");
            context.assertTrue(step.isAway(p1.getUuid()) && MiniGamePipes.isInParty(p2.getUuid()), "the players were sent to the mini-game");
            context.assertTrue(MiniGameSession.playing(List.of(id)) instanceof PartyMiniGameSession, "its podiums know the party's players");
            MiniGameControllerScreenHandler screen = new MiniGameControllerScreenHandler(1, p1.getInventory(), home);
            context.assertTrue(screen.state() == State.PARTY_PRACTICE && screen.isVoter() && !screen.isReady() && screen.voters() == 2, "the controller's screen shows the vote");
            context.assertTrue(!screen.onButtonClick(p1, MiniGameControllerScreenHandler.BUTTON_PLAY), "no « Play » while a party plays it");

            // The practice is played to its podiums: results, nothing paid, the party does not move
            context.assertTrue(Podiums.toggle(p1, world, context.getAbsolutePos(first)), "p1 takes the first place");
            context.assertTrue(Podiums.toggle(p2, world, context.getAbsolutePos(second)), "p2 takes the second place");
            context.assertTrue(step.isPractice(), "everyone placed: still the practice round");
            context.assertTrue(step.getLastResults() != null && step.getLastResults().kind() == MiniGameResults.Kind.PRACTICE, "its results are a practice round's");
            context.assertTrue(coins(p1) == 0 && coins(p2) == 0, "nothing is paid");

            // One vote is not enough; who does not play has no vote
            context.assertTrue(screen.onButtonClick(p1, MiniGameControllerScreenHandler.BUTTON_READY), "p1 is ready (the screen's button)");
            context.assertTrue(step.isPractice() && step.getReady().equals(Set.of(p1.getUuid())) && screen.isReady() && screen.readyCount() == 1, "1/2: the practice goes on");
            context.assertTrue(!MiniGamePartyStep.toggleReady(watcher), "who does not play has no vote");
            context.assertTrue(MiniGamePartyStep.toggleReady(p1) && step.getReady().isEmpty(), "the key again: no longer ready");
            context.assertTrue(MiniGamePartyStep.toggleReady(p1), "ready again");
        } catch (RuntimeException e) {
            cleanUp(context, id, p1, p2, watcher);
            throw e;
        }
        context.waitAndRun(MiniGamePartyStep.RETURN_DELAY_TICKS + 5, () -> {
            try {
                // After its results the practice starts again by itself, the votes kept
                context.assertTrue(step.isPractice() && occupant(context, first) == null && occupant(context, second) == null, "the practice started again: podiums emptied");
                context.assertEquals(step.getReady(), Set.of(p1.getUuid()), "the vote is kept");
                context.assertTrue(coins(p1) == 0, "still nothing paid");

                // Everyone ready: the real round at once
                context.assertTrue(MiniGamePartyStep.toggleReady(p2), "p2 is ready (the key)");
                context.assertTrue(step.isPlaying() && !step.isPractice() && step.getReady().isEmpty(), "everyone is ready: the real round");
                context.assertTrue(step.isAway(p1.getUuid()) && step.isAway(p2.getUuid()), "sent through their pipes again");
                context.assertTrue(!MiniGamePartyStep.toggleReady(p1), "no vote in the real round");
                context.assertTrue(Podiums.toggle(p2, world, context.getAbsolutePos(first)), "p2 takes the first place");
                context.assertTrue(Podiums.toggle(p1, world, context.getAbsolutePos(second)), "p1 takes the second place");
                context.assertEquals(step.getPhase(), MiniGamePartyStep.Phase.FINISHED, "everyone placed: the mini-game is over");
                context.assertEquals(step.getLastResults().kind(), MiniGameResults.Kind.PAID, "real results");
                context.assertTrue(coins(p2) == 10 && coins(p1) == 5, "the real round pays: 10 for the first place, 5 for the second");
            } finally {
                cleanUp(context, id, p1, p2, watcher);
            }
            context.complete();
        });
    }

    /**
     * No practice round when the party's setting is off, or when the page has no controller: the real round starts
     * directly. The setting is a button of the dashboard, saved with the controller.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_controller_setting")
    public void withoutPracticeTheRealRoundStartsDirectly(TestContext context) {
        BlockPos green = pipe(context, GREEN, 1, 1);
        ServerPlayerEntity p1 = player(context, "a", 6.5, 1, 1.5);
        ServerWorld world = context.getWorld();
        UUID id = page(context, green);
        try {
            alone(context, p1);
            // No Mini-game Controller: as before
            PartyControllerEntity controller = party(context, id, MiniGamePartyStep.Phase.COUNTDOWN, p1);
            context.assertTrue(controller.hasPracticeRound(), "practice rounds are on by default");
            step(controller).leaveForMiniGame(controller);
            context.assertTrue(step(controller).isPlaying(), "a page without controller: the real round directly");
            context.removeBlock(PARTY);
            MiniGamePipes.leaveParty(p1.getUuid());

            // A controller, but the setting off
            home(context, HOME, id);
            context.setBlockState(PARTY, ModBlocks.PARTY_CONTROLLER);
            PartyControllerScreenHandler dashboard = new PartyControllerScreenHandler(1, p1.getInventory(), context.getBlockEntity(PARTY));
            context.assertTrue(dashboard.onButtonClick(p1, PartyControllerScreenHandler.BUTTON_PRACTICE), "the dashboard's button, before the party");
            controller = party(context, id, MiniGamePartyStep.Phase.COUNTDOWN, p1);
            context.assertTrue(!controller.hasPracticeRound(), "practice rounds are off");
            PartyControllerEntity read = new PartyControllerEntity(controller.getPos(), controller.getCachedState());
            read.read(controller.createNbt(world.getRegistryManager()), world.getRegistryManager());
            context.assertTrue(!read.hasPracticeRound(), "the setting is saved");
            step(controller).leaveForMiniGame(controller);
            context.assertTrue(step(controller).isPlaying() && !step(controller).isPractice(), "practice rounds off: the real round directly");
            context.removeBlock(PARTY);
            MiniGamePipes.leaveParty(p1.getUuid());

            // On again: the practice round
            controller = party(context, id, MiniGamePartyStep.Phase.COUNTDOWN, p1);
            step(controller).leaveForMiniGame(controller);
            context.assertTrue(step(controller).isPractice(), "on, with a controller: the practice round");
        } finally {
            cleanUp(context, id, p1);
        }
        context.complete();
    }

    /**
     * A player who left the server no longer holds the vote back; a linked step controller shows the practice's
     * results (next) or starts it again, without moving the party.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_controller_vote")
    public void aPlayerWhoLeftDoesNotBlockTheVote(TestContext context) {
        BlockPos green = pipe(context, GREEN, 1, 1);
        ServerPlayerEntity p1 = player(context, "a", 6.5, 1, 1.5), p2 = player(context, "b", 6.5, 1, 2.5);
        ServerWorld world = context.getWorld();
        UUID id = page(context, green);
        try {
            alone(context, p1, p2);
            podium(context, id, 4, 3, 2);
            home(context, HOME, id);
            PartyControllerEntity controller = party(context, id, MiniGamePartyStep.Phase.COUNTDOWN, p1, p2);
            MiniGamePartyStep step = step(controller);
            step.leaveForMiniGame(controller);
            context.assertTrue(step.isPractice(), "the practice round");

            // A step controller linked to the page, during the practice
            MiniGameSession session = MiniGameSession.playing(List.of(id));
            session.step(0);
            context.assertTrue(step.isPractice() && step.getLastResults() != null && step.getLastResults().kind() == MiniGameResults.Kind.PRACTICE,
                    "next: the practice's results, the party stays on its mini-game");
            context.assertTrue(controller.getPartyData().getCurrentStep() == step, "the party did not move");
            session.step(1);
            context.assertTrue(step.isPractice() && controller.getPartyData().getCurrentStep() == step, "restart: the practice starts again");

            // p1 is ready, p2 is not: no real round; p2 leaves the server: it starts
            context.assertTrue(MiniGamePartyStep.toggleReady(p1) && step.isPractice(), "1/2");
            context.assertTrue(!step.checkReady(controller), "p2 is connected and not ready");
            world.getServer().getPlayerManager().remove(p2);
            context.assertEquals(step.voters(controller).size(), 1, "only the connected players vote");
            context.assertTrue(step.checkReady(controller), "who left no longer holds the vote back (checked every second)");
            context.assertTrue(step.isPlaying(), "the real round started");
        } finally {
            cleanUp(context, id, p1, p2);
        }
        context.complete();
    }

    // ------------------------------------------------------------------ the page's zone

    private static void lookFrom(TestContext context, ServerPlayerEntity player, double x, double y, double z, float yaw, float pitch) {
        Vec3d abs = context.getAbsolute(new Vec3d(x, y, z));
        player.refreshPositionAndAngles(abs.x, abs.y, abs.z, yaw, pitch);
        player.setHeadYaw(yaw);
    }

    /** The faces of a box: which one a ray meets, and how far a face may move. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theFacesOfAZone(TestContext context) {
        Box box = new Box(0, 0, 0, 4, 4, 4);
        context.assertEquals(ZoneFaces.lookedAt(new Vec3d(-3, 2, 2), new Vec3d(1, 0, 0), box), Direction.WEST, "from outside: the face the ray enters by");
        context.assertEquals(ZoneFaces.lookedAt(new Vec3d(2, 9, 2), new Vec3d(0, -1, 0), box), Direction.UP, "from above");
        context.assertEquals(ZoneFaces.lookedAt(new Vec3d(2, 2, 2), new Vec3d(1, 0, 0), box), Direction.EAST, "from inside: the face the ray leaves by");
        context.assertEquals(ZoneFaces.lookedAt(new Vec3d(2, 2, 2), new Vec3d(0.1, 0, -1), box), Direction.NORTH, "from inside, towards the north");
        context.assertTrue(ZoneFaces.lookedAt(new Vec3d(-3, 2, 2), new Vec3d(-1, 0, 0), box) == null, "looking away: no face");
        context.assertTrue(ZoneFaces.lookedAt(new Vec3d(-3, 9, 2), new Vec3d(1, 0, 0), box) == null, "passing over: no face");
        context.assertTrue(ZoneFaces.lookedAt(new Vec3d(-3 - ZoneFaces.REACH, 2, 2), new Vec3d(1, 0, 0), box) == null, "too far: no face");

        BlockBox blocks = new BlockBox(0, 0, 0, 3, 3, 3);
        context.assertEquals(ZoneFaces.moved(blocks, Direction.EAST, 1, -64, 319), new BlockBox(0, 0, 0, 4, 3, 3), "a face moved outward");
        context.assertEquals(ZoneFaces.moved(blocks, Direction.WEST, 4, -64, 319), new BlockBox(-4, 0, 0, 3, 3, 3), "by four");
        context.assertEquals(ZoneFaces.moved(blocks, Direction.NORTH, -1, -64, 319), new BlockBox(0, 0, 1, 3, 3, 3), "inward");
        context.assertEquals(ZoneFaces.moved(blocks, Direction.UP, -9, -64, 319), new BlockBox(0, 0, 0, 3, 0, 3), "never thinner than a block");
        context.assertTrue(ZoneFaces.moved(new BlockBox(0, 0, 0, 0, 3, 3), Direction.EAST, -1, -64, 319) == null, "one block thick: it can't shrink");
        BlockBox wide = new BlockBox(0, 0, 0, PageZone.MAX_SIDE - 2, 3, 3);
        context.assertEquals(ZoneFaces.moved(wide, Direction.EAST, 4, -64, 319).getBlockCountX(), PageZone.MAX_SIDE, "grown up to the cap, not past it");
        BlockBox huge = new BlockBox(0, 0, 0, PageZone.MAX_SIDE + 10, 3, 3);
        context.assertTrue(PageZone.tooBig(huge) && !PageZone.tooBig(wide), "a side over the cap: too big");
        context.assertTrue(ZoneFaces.moved(huge, Direction.EAST, 1, -64, 319) == null && ZoneFaces.moved(huge, Direction.EAST, -1, -64, 319) != null,
                "a box too big only shrinks");
        context.assertEquals(ZoneFaces.moved(new BlockBox(0, 310, 0, 3, 318, 3), Direction.UP, 4, -64, 319).getMaxY(), 319, "not above the world");
        context.assertTrue(ZoneFaces.moved(new BlockBox(0, -64, 0, 3, 0, 3), Direction.DOWN, 1, -64, 319) == null, "not under the world");
        context.complete();
    }

    /**
     * The zone mode of a page, as the server takes it: the editor's button starts it (not without the right to
     * build), two clicks on blocks draw the page's zone (every block is a corner, a controller too), a box too big
     * is refused, a sneaking scroll moves the face the player is found looking at (from outside, from inside), sneak
     * + click in the air or opening the editor end it, and so does putting the page away.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_controller_zone")
    public void thePageDrawsItsZoneInZoneMode(TestContext context) {
        ServerPlayerEntity player = player(context, "z", 3.5, 2, 3.5);
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        UUID id = page(context);
        try {
            BlockPos a = new BlockPos(2, 1, 2), b = new BlockPos(5, 4, 5);
            context.setBlockState(a, Blocks.STONE);
            context.setBlockState(b, Blocks.STONE);
            ItemStack stack = pageItem(id);
            player.setStackInHand(Hand.MAIN_HAND, stack);
            context.assertTrue(MiniGamePages.get(server, id).zone() == null && !PageZoneTool.isInMode(stack), "a new page has no zone, and is not in zone mode");
            context.assertEquals(UseBlockCallback.EVENT.invoker().interact(player, world, Hand.MAIN_HAND, hit(context, a)), ActionResult.PASS,
                    "out of the mode, a click on a block is the block's (a pipe is linked as ever)");
            context.assertTrue(!PageZoneTool.click(player, stack, context.getAbsolutePos(a)), "out of the mode: no corner");

            // The editor's button: the page in hand in zone mode
            context.assertTrue(MiniGamePageNetworking.action(player, new MiniGamePagePayloads.Action(Hand.MAIN_HAND, id, MiniGamePagePayloads.Action.Kind.DRAW_ZONE)),
                    "« Tracer la zone »");
            context.assertTrue(PageZoneTool.isInMode(stack) && PageZoneTool.corner(stack).isEmpty(), "zone mode, no corner yet");

            // Two clicks: two corners, the page's zone (a controller clicked is a corner, it does not take the page)
            home(context, HOME, null);
            context.assertEquals(UseBlockCallback.EVENT.invoker().interact(player, world, Hand.MAIN_HAND, hit(context, HOME)), ActionResult.SUCCESS,
                    "in the mode, every block is a corner");
            context.assertTrue(MiniGamePages.isPage(player.getMainHandStack()) && this.<MiniGameControllerBlockEntity>at(context, HOME).getPage().isEmpty(),
                    "the controller did not take the page");
            context.assertEquals(PageZoneTool.corner(stack), Optional.of(global(context, HOME)), "first corner");
            context.assertTrue(!PageZoneTool.scroll(player, stack, 1), "no zone yet: nothing to move");
            UseBlockCallback.EVENT.invoker().interact(player, world, Hand.MAIN_HAND, hit(context, a));
            BlockBox first = BlockBox.create(context.getAbsolutePos(HOME), context.getAbsolutePos(a));
            context.assertEquals(MiniGamePages.get(server, id).zone(), new PageZone(world.getRegistryKey(), first), "second corner: the page's zone");
            context.assertTrue(PageZoneTool.isInMode(stack) && PageZoneTool.corner(stack).isEmpty(), "still in the mode, no corner");
            PageZoneTool.click(player, stack, context.getAbsolutePos(a));
            PageZoneTool.click(player, stack, context.getAbsolutePos(b));
            BlockBox box = BlockBox.create(context.getAbsolutePos(a), context.getAbsolutePos(b));
            context.assertEquals(MiniGamePages.get(server, id).zone(), new PageZone(world.getRegistryKey(), box), "drawn again");

            // Too big: refused, the first corner kept
            PageZoneTool.click(player, stack, context.getAbsolutePos(a));
            context.assertTrue(!PageZoneTool.click(player, stack, context.getAbsolutePos(a).east(PageZone.MAX_SIDE + 5)), "too big: refused");
            context.assertEquals(MiniGamePages.get(server, id).zone().box(), box, "the zone stays");
            context.assertEquals(PageZoneTool.corner(stack), Optional.of(global(context, a)), "the first corner is kept for another try");
            context.assertTrue(PageZoneTool.click(PageZoneTool.corner(stack), GlobalPos.create(World.NETHER, BlockPos.ORIGIN)).corner().isPresent(),
                    "another dimension: a first corner there");
            PageZoneTool.click(player, stack, context.getAbsolutePos(b));

            // From outside, looking east at its west face
            lookFrom(context, player, -2.5, 2, 3.5, -90, 0);
            context.assertTrue(!PageZoneTool.scroll(player, stack, 1), "not sneaking: the wheel is the hotbar's");
            player.setSneaking(true);
            context.assertTrue(PageZoneTool.scroll(player, stack, 1), "sneak + wheel up");
            context.assertEquals(MiniGamePages.get(server, id).zone().box().getMinX(), box.getMinX() - 1, "the west face moved outward by one");
            context.assertTrue(PageZoneTool.scroll(player, stack, -PageZoneTool.FAST_STEP), "sneak + Ctrl + wheel down");
            context.assertEquals(MiniGamePages.get(server, id).zone().box().getMinX(), box.getMinX() + 3, "inward by four, never thinner than a block");
            context.assertTrue(!PageZoneTool.scroll(player, stack, -1), "one block thick: refused");
            context.assertTrue(PageZoneTool.scroll(player, stack, 50), "a made-up amount");
            context.assertEquals(MiniGamePages.get(server, id).zone().box().getMinX(), box.getMinX() - 1, "moves four blocks at most");
            // Looking away: no face; from inside, looking up: the top face
            lookFrom(context, player, -2.5, 2, 3.5, 90, 0);
            context.assertTrue(!PageZoneTool.scroll(player, stack, 1), "no face looked at: nothing moves");
            lookFrom(context, player, 3.5, 2, 3.5, 0, -90);
            context.assertTrue(PageZoneTool.scroll(player, stack, 1), "from inside");
            context.assertEquals(MiniGamePages.get(server, id).zone().box().getMaxY(), box.getMaxY() + 1, "the top face moved up");
            context.assertTrue(!PageZoneTool.scroll(player, pageItem(id), 1), "only a page in zone mode");
            player.setSneaking(false);

            // Sneak + click in the air: the mode ends, the zone stays
            player.setSneaking(true);
            context.assertTrue(stack.use(world, player, Hand.MAIN_HAND).isAccepted(), "sneak + click in the air");
            player.setSneaking(false);
            context.assertTrue(!PageZoneTool.isInMode(stack) && MiniGamePages.get(server, id).zone() != null, "the mode ended, the zone stays");
            // Opening the editor ends it too
            PageZoneTool.start(player, Hand.MAIN_HAND);
            MiniGamePageNetworking.open(player, Hand.MAIN_HAND);
            context.assertTrue(!PageZoneTool.isInMode(stack), "the editor opened: the mode ended");
            // Put away (out of the hands): it ends
            PageZoneTool.start(player, Hand.MAIN_HAND);
            player.getInventory().setStack(5, stack);
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            stack.inventoryTick(world, player, 5, false);
            context.assertTrue(!PageZoneTool.isInMode(stack), "put away: the mode ended");

            // Without the right to build: no mode, and a page in the mode draws nothing
            player.setStackInHand(Hand.MAIN_HAND, stack);
            player.getInventory().setStack(5, ItemStack.EMPTY);
            PageZoneTool.start(player, Hand.MAIN_HAND);
            player.changeGameMode(GameMode.ADVENTURE);
            context.assertTrue(!PageZoneTool.start(player, Hand.MAIN_HAND) || !MiniGamePages.canEdit(player), "adventure: no mode");
            PageZoneTool.click(player, stack, context.getAbsolutePos(a));
            context.assertTrue(PageZoneTool.corner(stack).isEmpty() && !PageZoneTool.isInMode(stack), "adventure: no corner, and the mode ends");
            player.changeGameMode(GameMode.CREATIVE);

            // The editor's ×: the page has no zone any more
            context.assertTrue(MiniGamePageNetworking.action(player, new MiniGamePagePayloads.Action(Hand.MAIN_HAND, id, MiniGamePagePayloads.Action.Kind.CLEAR_ZONE)),
                    "the zone cleared");
            context.assertTrue(MiniGamePages.get(server, id).zone() == null, "no zone");
            context.assertTrue(MiniGamePages.get(server, id).withZone(new PageZone(world.getRegistryKey(), new BlockBox(0, 0, 0, PageZone.MAX_SIDE, 1, 1))).zone() == null,
                    "a page never holds a zone too big");
        } finally {
            cleanUp(context, id, player);
        }
        context.complete();
    }

    @SuppressWarnings("unchecked")
    private <T> T at(TestContext context, BlockPos pos) {
        return (T) context.getBlockEntity(pos);
    }

    /**
     * The zone is the page's: its linked copies share it (drawn with one, seen through the others), a page unlinked
     * takes a copy of it of its own; every reader of the zone reads the page (no controller needed), saved and sent
     * with the page; the controller's screen has no slot for it any more and shows the page's.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_controller_zone_of")
    public void theZoneIsThePagesAndSharedByItsCopies(TestContext context) {
        ServerPlayerEntity player = player(context, "z", 4.5, 1, 4.5);
        MinecraftServer server = context.getWorld().getServer();
        ServerWorld world = context.getWorld();
        UUID id = page(context);
        try {
            BlockBox box = new BlockBox(10, 60, 10, 30, 70, 40);
            PageZone zone = new PageZone(world.getRegistryKey(), box);
            ItemStack original = pageItem(id), copy = MiniGamePages.linkedCopy(original, 1);
            player.setStackInHand(Hand.MAIN_HAND, copy);
            PageZoneTool.start(player, Hand.MAIN_HAND);
            PageZoneTool.click(player, copy, new BlockPos(10, 60, 10));
            PageZoneTool.click(player, copy, new BlockPos(30, 70, 40));
            context.assertEquals(MiniGamePages.of(server, original).zone(), zone, "drawn with a linked copy: the zone of every copy");

            // Its readers: no controller needed
            context.assertEquals(MiniGameControllers.zoneOf(server, id), Optional.of(zone), "the zone of the page's mini-game, without controller");
            context.assertTrue(MiniGameArena.zoneOf(server, id) == null, "a new zone is not restored: no bubble's zone");
            MiniGamePages.update(server, MiniGamePages.get(server, id).withRestore(true));
            context.assertTrue(MiniGameArena.zoneOf(server, id) != null && MiniGameArena.zoneOf(server, id).box().equals(box), "restored: the bubble's zone");
            // Saved and sent with the page
            context.assertEquals(MiniGamePageData.fromNbt(MiniGamePages.get(server, id).toNbt()).zone(), zone, "saved with the page");
            net.minecraft.network.PacketByteBuf buf = new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
            MiniGamePageData.PACKET_CODEC.encode(buf, MiniGamePages.get(server, id));
            context.assertEquals(MiniGamePageData.PACKET_CODEC.decode(buf).zone(), zone, "sent with the page");

            // The controller: one slot, the page's; its screen shows the page's zone
            MiniGameControllerBlockEntity controller = home(context, HOME, id);
            MiniGameControllerScreenHandler screen = new MiniGameControllerScreenHandler(1, player.getInventory(), controller);
            context.assertTrue(MiniGameControllerScreenHandler.PLAYER_SLOTS == 1 && screen.slots.size() == 1 + 36, "one slot of its own, then the inventory");
            context.assertTrue(!screen.getSlot(MiniGameControllerScreenHandler.SLOT_PAGE).canInsert(new ItemStack(Items.STONE)), "its slot only takes pages");
            context.assertTrue(java.util.Arrays.equals(screen.zoneSize(), new int[]{21, 11, 31}), "its screen shows the size of the page's zone");
            context.assertEquals(controller.getZone(), Optional.of(zone), "the controller's zone is its page's");
            ItemStack stone = new ItemStack(Items.STONE, 3);
            player.getInventory().setStack(9, stone);
            screen.quickMove(player, 1 + 0);
            context.assertTrue(player.getInventory().getStack(9).getCount() == 3, "a shift-click on anything else: nothing goes in");

            // Unlinked: a copy of the zone of its own
            MiniGamePageData unlinked = MiniGamePages.unlink(server, copy);
            context.assertTrue(unlinked != null && !unlinked.id().equals(id) && zone.equals(unlinked.zone()), "unlinked: a page of its own, with a copy of the zone");
            MiniGamePages.update(server, unlinked.withZone(null));
            context.assertEquals(MiniGamePages.get(server, id).zone(), zone, "its zone cleared: the other page keeps its own");
            context.assertTrue(new MiniGameControllerScreenHandler(2, player.getInventory(), controller).zoneSize()[0] == 21, "the controller's page still has its zone");
            MiniGamePages.update(server, MiniGamePages.get(server, id).withZone(null));
            context.assertTrue(new MiniGameControllerScreenHandler(3, player.getInventory(), controller).zoneSize()[0] == 0, "no zone: « Pas de zone »");
            context.assertTrue(MiniGameControllers.zoneOf(server, id).isEmpty() && MiniGameArena.zoneOf(server, id) == null, "nor for its readers");
        } finally {
            cleanUp(context, id, player);
        }
        context.complete();
    }

    /**
     * A zone kept by an earlier version, in a controller's Zone Cartridge (in its slot, and with the controllers'
     * homes), goes to the page when it has none; a page with a zone keeps its own. The cartridge is gone: an item the
     * game no longer knows, it reads as nothing.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_controller_zone_migration")
    public void aCartridgeZoneMovesIntoItsPage(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        ServerWorld world = context.getWorld();
        net.minecraft.registry.RegistryWrapper.WrapperLookup registries = world.getRegistryManager();
        UUID id = page(context), other = page(context), kept = page(context);
        try {
            BlockBox box = new BlockBox(10, 60, 10, 30, 70, 40), own = new BlockBox(0, 60, 0, 3, 63, 3);
            PageZone zone = new PageZone(world.getRegistryKey(), box);
            MiniGamePages.update(server, MiniGamePages.get(server, kept).withZone(new PageZone(world.getRegistryKey(), own)));

            // In a controller's slot, as saved before
            NbtCompound cartridge = new NbtCompound();
            cartridge.putString("id", "steveparty:zone_cartridge");
            cartridge.putInt("count", 1);
            NbtCompound selection = new NbtCompound();
            selection.putString("dimension", world.getRegistryKey().getValue().toString());
            selection.put("box", BlockBox.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, box).getOrThrow());
            NbtCompound components = new NbtCompound();
            components.put("steveparty:zone-selection", selection);
            cartridge.put("components", components);
            context.assertTrue(ItemStack.fromNbt(registries, cartridge).isEmpty(), "the cartridge is an unknown item: it reads as nothing");
            context.assertEquals(MiniGameControllerBlockEntity.oldZone(cartridge), zone, "its zone is read from what was saved");

            MiniGameControllerBlockEntity controller = home(context, HOME, null);
            NbtCompound saved = controller.createNbt(registries);
            saved.put("Page", pageItem(id).toNbt(registries));
            saved.put("ZoneCartridge", cartridge);
            controller.read(saved, registries);
            controller.serverTick(world);
            context.assertEquals(MiniGamePages.get(server, id).zone(), zone, "the controller's page took the zone of its cartridge");
            context.assertTrue(!controller.createNbt(registries).contains("ZoneCartridge"), "the cartridge is gone");

            // With the controllers' homes, as saved before
            NbtCompound homes = new NbtCompound();
            net.minecraft.nbt.NbtList list = new net.minecraft.nbt.NbtList();
            for (UUID page : List.of(other, kept)) {
                NbtCompound home = new NbtCompound();
                home.putUuid("Page", page);
                home.putString("Dimension", world.getRegistryKey().getValue().toString());
                home.putLong("Pos", context.getAbsolutePos(HOME).asLong());
                home.put("Zone", PageZone.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, zone).getOrThrow());
                list.add(home);
            }
            homes.put("Homes", list);
            MiniGameControllers read = MiniGameControllers.fromNbt(homes, registries);
            read.adoptOldZones(server);
            context.assertEquals(MiniGamePages.get(server, other).zone(), zone, "a page without zone takes the one saved with its home");
            context.assertEquals(MiniGamePages.get(server, kept).zone().box(), own, "a page with a zone keeps its own");
            context.assertTrue(!read.writeNbt(new NbtCompound(), registries).getList("Homes", 10).getCompound(0).contains("Zone"), "the homes no longer keep zones");
        } finally {
            cleanUp(context, id);
        }
        context.complete();
    }

    // ------------------------------------------------------------------ what the block shows

    private static MiniGameControllerBlock.Signal lamp(TestContext context) {
        return context.getBlockState(HOME).get(MiniGameControllerBlock.SIGNAL);
    }

    private static boolean showsPage(TestContext context) {
        return context.getBlockState(HOME).get(MiniGameControllerBlock.PAGE);
    }

    /**
     * The block shows its page, and its lamp follows the mini-game of that page in a party: red while nobody plays it,
     * orange during the practice round, green during the real round, red again once it is over.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_controller_lamp", tickLimit = 100)
    public void theLampFollowsThePartysMiniGame(TestContext context) {
        BlockPos green = pipe(context, GREEN, 1, 1);
        ServerPlayerEntity p1 = player(context, "a", 6.5, 1, 1.5), p2 = player(context, "b", 6.5, 1, 2.5);
        UUID id = page(context, green);
        try {
            alone(context, p1, p2);
            BlockPos first = podium(context, id, 4, 3, 2), second = podium(context, id, 5, 3, 1);
            MiniGameControllerBlockEntity home = home(context, HOME, null);
            context.assertTrue(!showsPage(context) && lamp(context) == MiniGameControllerBlock.Signal.RED, "no page: no page shown, the lamp red");
            context.assertEquals(context.getBlockState(HOME).rotate(net.minecraft.util.BlockRotation.CLOCKWISE_90).get(MiniGameControllerBlock.FACING),
                    Direction.EAST, "it turns with what it stands on");
            home.setPage(pageItem(id));
            context.assertTrue(showsPage(context) && lamp(context) == MiniGameControllerBlock.Signal.RED, "the page is shown at once; nobody plays: red");
            context.assertTrue(context.getBlockEntity(HOME) == home && id.equals(home.getPageId()), "the controller is still itself, with its page");

            PartyControllerEntity controller = party(context, id, MiniGamePartyStep.Phase.COUNTDOWN, p1, p2);
            MiniGamePartyStep step = step(controller);
            home.refreshState();
            context.assertEquals(lamp(context), MiniGameControllerBlock.Signal.RED, "the countdown: not played yet");
            step.leaveForMiniGame(controller);
            home.refreshState();
            context.assertEquals(lamp(context), MiniGameControllerBlock.Signal.ORANGE, "the practice round: orange");
            MiniGamePartyStep.toggleReady(p1);
            MiniGamePartyStep.toggleReady(p2);
            context.assertTrue(step.isPlaying(), "everyone ready: the real round");
            home.refreshState();
            context.assertEquals(lamp(context), MiniGameControllerBlock.Signal.GREEN, "the real round: green");
            Podiums.toggle(p1, context.getWorld(), context.getAbsolutePos(first));
            Podiums.toggle(p2, context.getWorld(), context.getAbsolutePos(second));
            context.assertEquals(step.getPhase(), MiniGamePartyStep.Phase.FINISHED, "the mini-game is over");
            home.refreshState();
            context.assertEquals(lamp(context), MiniGameControllerBlock.Signal.RED, "over: red again");

            // The page taken out: nothing shown
            home.setPage(ItemStack.EMPTY);
            context.assertTrue(!showsPage(context) && lamp(context) == MiniGameControllerBlock.Signal.RED, "the page out: gone from the block at once");
        } finally {
            cleanUp(context, id, p1, p2);
        }
        context.complete();
    }

    /** Out of a party: green while the mini-game is played, red once stopped; the lamp follows by itself within a few ticks. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_controller_lamp_play", tickLimit = 100)
    public void theLampFollowsAMiniGamePlayedOutOfAParty(TestContext context) {
        BlockPos green = pipe(context, GREEN, 1, 1);
        ServerPlayerEntity p1 = player(context, "a", 1.5, 1, 2.5);
        MinecraftServer server = context.getWorld().getServer();
        UUID id = page(context, green);
        try {
            alone(context, p1);
            home(context, HOME, id);
            context.assertEquals(lamp(context), MiniGameControllerBlock.Signal.RED, "nobody plays: red");
            context.assertEquals(MiniGameTest.start(server, id, p1, 0), MiniGameTest.Status.READY, "played out of a party");
        } catch (RuntimeException e) {
            cleanUp(context, id, p1);
            throw e;
        }
        // Nothing asks the block to look: it does, a few times a second
        context.waitAndRun(8, () -> {
            try {
                context.assertEquals(lamp(context), MiniGameControllerBlock.Signal.GREEN, "being played: green");
                MiniGameTest.stop(id);
            } catch (RuntimeException e) {
                cleanUp(context, id, p1);
                throw e;
            }
            context.waitAndRun(8, () -> {
                try {
                    context.assertEquals(lamp(context), MiniGameControllerBlock.Signal.RED, "stopped: red");
                    context.assertTrue(showsPage(context), "the page is still shown");
                } finally {
                    cleanUp(context, id, p1);
                }
                context.complete();
            });
        });
    }

    /**
     * A player of a party who left the server during the practice round is brought back, when it comes again, where
     * it stood before the mini-game; the others are brought back when the step ends.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_controller_away", tickLimit = 100)
    public void aPartyPlayerAwayAtTheEndIsBroughtBackWhenItComes(TestContext context) {
        BlockPos green = pipe(context, GREEN, 1, 1);
        ServerPlayerEntity p1 = player(context, "a", 6.5, 1, 1.5), p2 = player(context, "b", 6.5, 1, 2.5);
        MinecraftServer server = context.getWorld().getServer();
        UUID id = page(context, green);
        Vec3d start1 = p1.getPos(), start2 = p2.getPos();
        GameProfile away = p2.getGameProfile();
        ServerPlayerEntity back = null;
        try {
            alone(context, p1, p2);
            home(context, HOME, id);
            PartyControllerEntity controller = party(context, id, MiniGamePartyStep.Phase.COUNTDOWN, p1, p2);
            MiniGamePartyStep step = step(controller);
            step.leaveForMiniGame(controller);
            context.assertTrue(step.isPractice() && step.isAway(p2.getUuid()), "the practice round, p2 sent to it");
            Reconnect.leave(p2);
            step.end(controller);
            context.assertTrue(p1.getPos().distanceTo(start1) < 0.01, "p1, there, is back at once");
            context.assertTrue(MiniGameReturns.isPending(server, away.getId()), "p2, gone, is waited for");
            back = Reconnect.join(context, away);
            context.assertTrue(back.getPos().distanceTo(start2) < 0.01, "p2 comes back where it stood before the mini-game");
            context.assertTrue(!MiniGameReturns.isPending(server, away.getId()), "once");
        } finally {
            if (back != null) cleanUp(context, id, back);
            cleanUp(context, id, p1, p2);
        }
        context.complete();
    }
}
