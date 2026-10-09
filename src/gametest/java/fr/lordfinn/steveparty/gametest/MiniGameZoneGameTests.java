package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.TestBank;
import fr.lordfinn.steveparty.gametest.kit.TestCleanup;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.minigame.PageZone;
import java.util.Map;
import java.util.HashMap;
import fr.lordfinn.steveparty.minigame.MiniGameNameColors;
import com.mojang.authlib.GameProfile;
import fr.lordfinn.steveparty.blocks.ModBlocks;
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
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.minigame.MiniGameControllers;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGameAdventure;
import fr.lordfinn.steveparty.minigame.MiniGamePageNetworking;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import fr.lordfinn.steveparty.minigame.MiniGamePodiumLink;
import fr.lordfinn.steveparty.minigame.MiniGameResults;
import fr.lordfinn.steveparty.minigame.MiniGameTest;
import fr.lordfinn.steveparty.minigame.MiniGameReturns;
import fr.lordfinn.steveparty.minigame.zone.MiniGameZone;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubble;
import fr.lordfinn.steveparty.config.ServerConfig;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubbles;
import fr.lordfinn.steveparty.podium.Podiums;
import fr.lordfinn.steveparty.screen_handlers.custom.MiniGameControllerScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.MiniGameControllerScreenHandler.State;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A mini-game whose page has a zone ({@code MiniGamePageData#zone}) is played, round after round,
 * in a bubble: out of a party, in a party's practice round and in its real round. Each round starts from the arena
 * as it was built and from empty session inventories; what the round pays goes to what the players own; a zone that
 * can't take a round stops one out of a party, never a party.
 * <p>
 * The arena of every test: a players pipe at (1, 2, 1), two podiums, the controller at (6, 1, 6), and a zone from
 * (0, 1, 0) to (6, 6, 6); x = 7 and z = 7 are out of it. Each test has a batch of its own: the rounds are the
 * server's.
 */
public class MiniGameZoneGameTests implements FabricGameTest {
    private static final int GREEN = 13;
    private static final AtomicInteger SERIAL = new AtomicInteger();
    private static final BlockPos HOME = new BlockPos(6, 1, 6), PARTY = new BlockPos(0, 1, 7);
    private static final BlockPos FIRST = new BlockPos(4, 1, 3), SECOND = new BlockPos(5, 1, 3);
    private static final BlockPos STONE = new BlockPos(2, 1, 5), CHEST = new BlockPos(3, 1, 5);
    private static final String STASH_TAG = "steveparty.zone_bubble";
    /** The time of day each test began at: put back when it ends (the suite goes on at its own hour). */
    private long hour;

    // ------------------------------------------------------------------ helpers

    /** A test that fails ends the rounds it left going all the same. */
    @Override
    public void invokeTestMethod(TestContext context, Method method) {
        hour = context.getWorld().getTimeOfDay();
        try {
            FabricGameTest.super.invokeTestMethod(context, method);
        } catch (RuntimeException | Error e) {
            for (ZoneBubble left : ZoneBubbles.all()) left.endNow();
            throw e;
        }
    }

    /** The test is over: the hour of the world is put back where the test found it. */
    private void done(TestContext context) {
        context.getWorld().setTimeOfDay(hour);
        context.complete();
    }

    private static GlobalPos global(TestContext context, BlockPos relative) {
        return GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(relative));
    }

    private static ItemStack pageItem(UUID id) {
        ItemStack stack = new ItemStack(ModItems.MINI_GAME_PAGE);
        stack.set(ModComponents.MINI_GAME_PAGE, new MiniGamePageRef(id, "", false));
        return stack;
    }

    private static MiniGameZone zone(TestContext context) {
        return MiniGameZone.of(context.getWorld().getRegistryKey(), context.getAbsolutePos(new BlockPos(0, 1, 0)), context.getAbsolutePos(new BlockPos(6, 6, 6)));
    }

    /**
     * The arena: a floor, a players pipe, two podiums, a stone block and a chest of diamonds, and the page of it all.
     *
     * @param zoned its page has the zone of the arena
     * @return the id of its page
     */
    private static UUID arena(TestContext context, boolean zoned) {
        MinecraftServer server = context.getWorld().getServer();
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
        context.setBlockState(new BlockPos(1, 1, 1), Blocks.STONE);
        BlockPos pipe = new BlockPos(1, 2, 1);
        context.setBlockState(pipe, ModBlocks.PIPES[PipeKind.OPAQUE.ordinal()][GREEN].getDefaultState().with(PipeBlock.SOLID, PipeSolid.DOWN));
        UUID id = UUID.randomUUID();
        MiniGamePages.update(server, MiniGamePageData.empty(id).withTexts("Zone " + SERIAL.incrementAndGet(), ""));
        MiniGamePages.toggleLink(server, id, global(context, pipe), Direction.UP, MiniGamePipeRole.ofPipe(context.getBlockState(pipe)));
        context.setBlockState(FIRST, ModBlocks.PODIUM.getDefaultState().with(PodiumBlock.FULL, true));
        context.setBlockState(SECOND, ModBlocks.GOLD_PODIUM.getDefaultState());
        MiniGamePages.addPodiumLink(server, id, new MiniGamePodiumLink(global(context, FIRST), MiniGamePodiumLink.Kind.PODIUM));
        MiniGamePages.addPodiumLink(server, id, new MiniGamePodiumLink(global(context, SECOND), MiniGamePodiumLink.Kind.PODIUM));
        context.setBlockState(STONE, Blocks.STONE);
        context.setBlockState(CHEST, Blocks.CHEST);
        context.<ChestBlockEntity>getBlockEntity(CHEST).setStack(0, new ItemStack(Items.DIAMOND, 5));
        context.setBlockState(HOME, ModBlocks.MINI_GAME_CONTROLLER);
        MiniGameControllerBlockEntity controller = context.getBlockEntity(HOME);
        controller.setPage(pageItem(id));
        if (zoned) MiniGamePages.update(server, MiniGamePages.get(server, id).withZone(
                new PageZone(context.getWorld().getRegistryKey(), zone(context).box())).withRestore(true));
        return id;
    }

    /** A connected survival player with a name of its own, standing at a relative position. */
    private static ServerPlayerEntity player(TestContext context, String name, double x, double y, double z) {
        return TestPlayers.joined(context, "a", name, GameMode.SURVIVAL, x, y, z);
    }

    /** The players other tests left around are sent away: they would be recruited like anyone near a pipe. */
    private static void alone(TestContext context, ServerPlayerEntity... mine) {
        TestPlayers.alone(context, new Vec3d(4, 2, 4), 40, mine);
    }

    private static void cleanUp(TestContext context, UUID page, ServerPlayerEntity... players) {
        MiniGameTest.stop(page);
        TestCleanup.removeIf(context, PARTY, ModBlocks.PARTY_CONTROLLER);
        for (ZoneBubble left : ZoneBubbles.all()) left.endNow();
        TestCleanup.removeIf(context, HOME, ModBlocks.MINI_GAME_CONTROLLER);
        TestPlayers.leaveMiniGamesIfOnline(context, players);
    }

    /** A party whose mini-game step, on {@code pageId}, is at its countdown with {@code participants}. */
    private static PartyControllerEntity party(TestContext context, UUID pageId, ServerPlayerEntity... participants) {
        context.setBlockState(PARTY.down(), Blocks.STONE);
        context.setBlockState(PARTY, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(PARTY);
        NbtCompound stepNbt = new NbtCompound();
        stepNbt.putString("Type", PartyStepType.MINI_GAME.name());
        stepNbt.putString("Status", PartyStep.Status.IN_PROGRESS.name());
        stepNbt.putString("Phase", MiniGamePartyStep.Phase.COUNTDOWN.name());
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
        TestBank.stock(context, controller, PARTY.up(), 640, 64);
        return controller;
    }

    private static int coins(ServerPlayerEntity player) {
        return player.getInventory().count(PartyCurrency.COIN.defaultStack().getItem());
    }

    private static UUID occupant(TestContext context, BlockPos podium) {
        PodiumBlockEntity entity = context.getBlockEntity(podium);
        return entity.getOccupant() == null ? null : entity.getOccupant().player();
    }

    private static int diamonds(TestContext context) {
        return context.<ChestBlockEntity>getBlockEntity(CHEST).count(Items.DIAMOND);
    }

    /** The round does what a round does to an arena: a block broken, a chest emptied into a pocket, a block left behind. */
    private static void spoil(TestContext context, ServerPlayerEntity player) {
        context.setBlockState(STONE, Blocks.AIR);
        ChestBlockEntity chest = context.getBlockEntity(CHEST);
        player.getInventory().insertStack(chest.removeStack(0));
        context.setBlockState(new BlockPos(3, 3, 3), Blocks.GOLD_BLOCK);
    }

    private static void expectArenaAsBuilt(TestContext context) {
        context.expectBlock(Blocks.STONE, STONE);
        context.expectBlock(Blocks.AIR, new BlockPos(3, 3, 3));
        context.assertTrue(diamonds(context) == 5, "the chest holds its diamonds again");
    }

    // ------------------------------------------------------------------ out of a party

    /**
     * A round out of a party, in a zone: its players leave what they own at the door, the round is played to its
     * podiums, and when its results are read the arena is as it was built (block, chest, item on the ground), the
     * inventories are given back and the controller, which stands in the zone, still holds its page.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_zone_play")
    public void aRoundOutOfAPartyIsPlayedInItsZone(TestContext context) {
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        ServerPlayerEntity p1 = player(context, "a", 1.5, 1, 2.5), p2 = player(context, "b", 2.5, 1, 1.5);
        UUID id = arena(context, true);
        try {
            alone(context, p1, p2);
            p1.getInventory().setStack(0, new ItemStack(Items.DIAMOND_SWORD));
            p2.getInventory().setStack(0, new ItemStack(Items.COBBLESTONE, 12));
            ItemEntity lying = context.spawnItem(Items.EMERALD, new Vec3d(4.5, 1, 5.5));
            lying.setVelocity(Vec3d.ZERO);
            lying.setPickupDelay(0);
            UUID lyingId = lying.getUuid();
            MiniGameControllerBlockEntity controller = context.getBlockEntity(HOME);
            MiniGameControllerScreenHandler screen = new MiniGameControllerScreenHandler(1, p1.getInventory(), controller);
            context.assertTrue(screen.state() == State.READY && MiniGameControllers.zoneOf(server, id).isPresent(), "a zone, two players: it can be played");

            context.assertEquals(MiniGameTest.start(server, id, p1, 0), MiniGameTest.Status.READY, "played");
            MiniGameTest played = MiniGameTest.of(id);
            ZoneBubble bubble = ZoneBubbles.of(world, context.getAbsolutePos(STONE));
            context.assertTrue(bubble != null && bubble.isActive() && bubble.zone().equals(zone(context)), "the round is played in a bubble over its zone");
            context.assertTrue(bubble.isParticipant(p1.getUuid()) && bubble.isParticipant(p2.getUuid()), "its players are those of the bubble");
            context.assertTrue(p1.getInventory().isEmpty() && p2.getInventory().isEmpty(), "they play with a session inventory");
            context.assertTrue(p1.interactionManager.getGameMode() == GameMode.SURVIVAL, "without the option, they keep their game mode");
            screen = new MiniGameControllerScreenHandler(2, p1.getInventory(), controller);
            context.assertTrue(screen.isLocked() && !screen.getSlot(MiniGameControllerScreenHandler.SLOT_PAGE).canTakeItems(p1), "the controller keeps its page during a round");

            spoil(context, p1);
            lying.onPlayerCollision(p2);
            context.assertTrue(lying.isRemoved() && p2.getInventory().count(Items.EMERALD) == 1, "an item of the arena is picked up");
            context.assertTrue(Podiums.toggle(p1, world, context.getAbsolutePos(FIRST)) && Podiums.toggle(p2, world, context.getAbsolutePos(SECOND)), "both take a place");

            context.assertTrue(played.results() != null && played.results().kind() == MiniGameResults.Kind.NO_PARTY, "the results were read on the podiums");
            context.assertTrue(played.places().get(p1.getUuid()) == 1 && played.places().get(p2.getUuid()) == 2, "with the places taken");
            context.assertTrue(bubble.state() == ZoneBubble.State.ENDED && ZoneBubbles.ofPlayer(p1) == null, "the round is over: so is its bubble");
            expectArenaAsBuilt(context);
            context.assertTrue(world.getEntity(lyingId) instanceof ItemEntity back && back.getStack().isOf(Items.EMERALD), "the item lies there again");
            context.assertTrue(occupant(context, FIRST) == null && occupant(context, SECOND) == null, "the podiums are as the round found them: empty");
            context.assertTrue(p1.getInventory().getStack(0).isOf(Items.DIAMOND_SWORD) && p1.getInventory().count(Items.DIAMOND) == 0, "p1 has its sword, not the diamonds");
            context.assertTrue(p2.getInventory().count(Items.COBBLESTONE) == 12 && p2.getInventory().count(Items.EMERALD) == 0, "p2 has its cobblestone, not the emerald");
            context.assertTrue(!p1.getCommandTags().contains(STASH_TAG), "nobody holds a session inventory any more");
            controller = context.getBlockEntity(HOME);
            context.assertTrue(id.equals(controller.getPageId()) && controller.getZone().isPresent(), "the controller still holds its page, the zone of its page");
            context.assertTrue(MiniGameControllers.zoneOf(server, id).isPresent(), "and its mini-game still has its zone");
        } finally {
            cleanUp(context, id, p1, p2);
        }
        done(context);
    }

    /**
     * Stopped during its round (the same button), a round gives everything back at once; a controller broken during
     * the round comes back with its page. And a page without zone is played as ever: no bubble, the inventories kept.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_zone_stop")
    public void stoppingARoundGivesEverythingBack(TestContext context) {
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        ServerPlayerEntity p1 = player(context, "a", 1.5, 1, 2.5), p2 = player(context, "b", 2.5, 1, 1.5);
        UUID id = arena(context, true);
        try {
            alone(context, p1, p2);
            p1.getInventory().setStack(0, new ItemStack(Items.DIAMOND_SWORD));
            MiniGameControllerScreenHandler screen = new MiniGameControllerScreenHandler(1, p1.getInventory(), context.getBlockEntity(HOME));
            context.assertEquals(MiniGameTest.start(server, id, p1, 0), MiniGameTest.Status.READY, "played");
            context.assertTrue(ZoneBubbles.ofPlayer(p1) != null && p1.getInventory().isEmpty(), "in its bubble");
            spoil(context, p1);
            world.breakBlock(context.getAbsolutePos(HOME), true);
            context.assertTrue(MiniGameControllers.of(server, id).isEmpty() && MiniGameControllers.zoneOf(server, id).isPresent(),
                    "the controller was broken during the round: the page keeps its zone");

            MiniGameTest.stop(id);
            context.assertTrue(MiniGameTest.of(id) == null && ZoneBubbles.all().isEmpty(), "stopped: no round, no bubble");
            expectArenaAsBuilt(context);
            context.assertTrue(p1.getInventory().getStack(0).isOf(Items.DIAMOND_SWORD) && p1.getInventory().count(Items.DIAMOND) == 0, "p1 has what it owns, nothing of the round");
            MiniGameControllerBlockEntity controller = context.getBlockEntity(HOME);
            context.assertTrue(id.equals(controller.getPageId()) && controller.getZone().isPresent(), "the controller is back with its page");
            controller.serverTick(world);
            context.assertTrue(MiniGameControllers.of(server, id).isPresent(), "and, from its first tick, the home of its page again");

            // A page without zone: the mini-game is played as it always was
            MiniGamePages.update(server, MiniGamePages.get(server, id).withZone(null));
            screen = new MiniGameControllerScreenHandler(1, p1.getInventory(), controller);
            context.assertTrue(screen.onButtonClick(p1, MiniGameControllerScreenHandler.BUTTON_PLAY), "Play, without zone");
            context.assertTrue(MiniGameTest.of(id) != null && ZoneBubbles.all().isEmpty() && ZoneBubbles.ofPlayer(p1) == null, "a round, and no bubble");
            context.assertTrue(p1.getInventory().getStack(0).isOf(Items.DIAMOND_SWORD), "everyone keeps its inventory");
        } finally {
            cleanUp(context, id, p1, p2);
        }
        done(context);
    }

    /**
     * « Remettre l'arène en état » is the page's: off, a page with a zone is played like one without (no bubble, every
     * player keeps its own inventory); on, in a bubble. It can't be on without a zone, and a new zone starts it off.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_zone_restore_option")
    public void theArenaIsRestoredOnlyWhenThePageSaysSo(TestContext context) {
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        ServerPlayerEntity p1 = player(context, "a", 1.5, 1, 2.5), p2 = player(context, "b", 2.5, 1, 1.5);
        UUID id = arena(context, true);
        try {
            alone(context, p1, p2);
            p1.getInventory().setStack(0, new ItemStack(Items.DIAMOND_SWORD));
            MiniGamePages.update(server, MiniGamePages.get(server, id).withRestore(false));
            context.assertTrue(MiniGamePages.get(server, id).zone() != null && !MiniGamePages.get(server, id).restores(), "a zone, the option off");
            context.assertEquals(MiniGameTest.start(server, id, p1, 0), MiniGameTest.Status.READY, "played");
            context.assertTrue(ZoneBubbles.all().isEmpty() && ZoneBubbles.ofPlayer(p1) == null, "option off: no bubble");
            context.assertTrue(p1.getInventory().getStack(0).isOf(Items.DIAMOND_SWORD), "everyone keeps its own inventory");
            MiniGameTest.stop(id);

            MiniGamePages.update(server, MiniGamePages.get(server, id).withRestore(true));
            context.assertEquals(MiniGameTest.start(server, id, p1, 0), MiniGameTest.Status.READY, "played again");
            context.assertTrue(ZoneBubbles.ofPlayer(p1) != null && p1.getInventory().isEmpty(), "option on: in a bubble, with a session inventory");
            MiniGameTest.stop(id);
            context.assertTrue(ZoneBubbles.all().isEmpty() && p1.getInventory().getStack(0).isOf(Items.DIAMOND_SWORD), "stopped: the sword back");

            MiniGamePageData page = MiniGamePages.get(server, id);
            context.assertTrue(!page.withZone(null).restores() && !page.withZone(null).withRestore(true).restore(), "no zone, no restore");
            context.assertTrue(!page.withZone(null).withZone(page.zone()).restore(), "a new zone starts without restore");
            context.assertTrue(page.withZone(new PageZone(page.zone().dimension(), page.zone().box().expand(1))).restore(),
                    "a zone moved keeps it");
            ServerPlayerEntity editor = p2;
            editor.changeGameMode(GameMode.CREATIVE);
            ItemStack held = pageItem(id);
            editor.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, held);
            MiniGamePagePayloads.Action toggle = new MiniGamePagePayloads.Action(net.minecraft.util.Hand.MAIN_HAND, id, MiniGamePagePayloads.Action.Kind.RESTORE);
            context.assertTrue(MiniGamePageNetworking.action(editor, toggle) && !MiniGamePages.get(server, id).restore(), "the editor's checkbox: off");
            MiniGamePages.update(server, MiniGamePages.get(server, id).withZone(null));
            context.assertTrue(!MiniGamePageNetworking.action(editor, toggle) && !MiniGamePages.get(server, id).restore(), "no zone: the checkbox can't be ticked");
            editor.changeGameMode(GameMode.SURVIVAL);
        } finally {
            cleanUp(context, id, p1, p2);
        }
        done(context);
    }

    /**
     * « Mode aventure » is the page's too: its players play in adventure mode and get their own mode back, with the
     * restore (in the bubble) as without it (no bubble); a mode taken by a round comes back when the player leaves or
     * comes back to the server.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_zone_adventure")
    public void theAdventureModeOfThePage(TestContext context) {
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        ServerPlayerEntity p1 = player(context, "a", 1.5, 1, 2.5), p2 = player(context, "b", 2.5, 1, 1.5);
        UUID id = arena(context, true);
        try {
            alone(context, p1, p2);
            context.assertTrue(!MiniGamePages.get(server, id).adventure(), "off by default");
            ItemStack held = pageItem(id);
            p2.changeGameMode(GameMode.CREATIVE);
            p2.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, held);
            context.assertTrue(MiniGamePageNetworking.action(p2, new MiniGamePagePayloads.Action(net.minecraft.util.Hand.MAIN_HAND, id,
                    MiniGamePagePayloads.Action.Kind.ADVENTURE)) && MiniGamePages.get(server, id).adventure(), "the editor's checkbox: on");
            p2.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, ItemStack.EMPTY);
            p2.changeGameMode(GameMode.SURVIVAL);

            // Without the restore: no bubble, adventure mode all the same
            MiniGamePages.update(server, MiniGamePages.get(server, id).withRestore(false));
            context.assertEquals(MiniGameTest.start(server, id, p1, 0), MiniGameTest.Status.READY, "played without restore");
            context.assertTrue(ZoneBubbles.all().isEmpty(), "no bubble");
            context.assertTrue(p1.interactionManager.getGameMode() == GameMode.ADVENTURE && p2.interactionManager.getGameMode() == GameMode.ADVENTURE, "its players are in adventure mode");
            MiniGameTest.stop(id);
            context.assertTrue(p1.interactionManager.getGameMode() == GameMode.SURVIVAL && p2.interactionManager.getGameMode() == GameMode.SURVIVAL, "and get their mode back");
            context.assertTrue(MiniGameAdventure.own(p1) == null, "nothing left on them");

            // With the restore: in the bubble, adventure mode
            MiniGamePages.update(server, MiniGamePages.get(server, id).withRestore(true));
            context.assertEquals(MiniGameTest.start(server, id, p1, 0), MiniGameTest.Status.READY, "played with restore");
            context.assertTrue(ZoneBubbles.ofPlayer(p1) != null && p1.interactionManager.getGameMode() == GameMode.ADVENTURE, "in the bubble, in adventure mode");
            MiniGameTest.stop(id);
            context.assertTrue(p1.interactionManager.getGameMode() == GameMode.SURVIVAL, "their mode back");

            // A mode a round took comes back whatever happens (it is kept on the player)
            context.assertTrue(MiniGameAdventure.apply(p1) && p1.interactionManager.getGameMode() == GameMode.ADVENTURE && MiniGameAdventure.own(p1) == GameMode.SURVIVAL,
                    "taken, and remembered");
            MiniGameAdventure.restore(p1);
            context.assertTrue(p1.interactionManager.getGameMode() == GameMode.SURVIVAL && MiniGameAdventure.own(p1) == null, "given back");
        } finally {
            cleanUp(context, id, p1, p2);
        }
        done(context);
    }

    /**
     * The options of the pages saved before them: a zone already there keeps being restored (it always was), a page
     * without zone is not; the « adventure mode » of a controller (in it, and with the controllers' homes) goes to its page.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_zone_options_migration")
    public void theOptionsOfOlderPages(TestContext context) {
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        net.minecraft.registry.RegistryWrapper.WrapperLookup registries = world.getRegistryManager();
        UUID id = arena(context, true);
        try {
            NbtCompound saved = MiniGamePages.get(server, id).withRestore(false).toNbt();
            saved.putInt("Format", 5);
            context.assertTrue(MiniGamePageData.fromNbt(saved).restores(), "a zone saved before the option: restored");
            saved.remove("Zone");
            context.assertTrue(!MiniGamePageData.fromNbt(saved).restore(), "no zone: not restored");
            NbtCompound now = MiniGamePages.get(server, id).withRestore(false).toNbt();
            context.assertTrue(!MiniGamePageData.fromNbt(now).restore() && MiniGamePageData.fromNbt(MiniGamePages.get(server, id).withAdventure(true).toNbt()).adventure(),
                    "saved since: as it is");

            // The controller's own option, as saved before
            MiniGameControllerBlockEntity controller = context.getBlockEntity(HOME);
            NbtCompound old = controller.createNbt(registries);
            old.putBoolean("Adventure", true);
            controller.read(old, registries);
            controller.serverTick(world);
            context.assertTrue(MiniGamePages.get(server, id).adventure(), "a controller's adventure mode goes to its page");
            context.assertTrue(!controller.createNbt(registries).contains("Adventure"), "and is no longer the controller's");

            // With the controllers' homes, as saved before
            UUID other = UUID.randomUUID();
            MiniGamePages.update(server, MiniGamePageData.empty(other).withTexts("Old " + SERIAL.incrementAndGet(), ""));
            NbtCompound homes = new NbtCompound();
            NbtList list = new NbtList();
            NbtCompound home = new NbtCompound();
            home.putUuid("Page", other);
            home.putString("Dimension", world.getRegistryKey().getValue().toString());
            home.putLong("Pos", context.getAbsolutePos(HOME).asLong());
            home.putBoolean("Adventure", true);
            list.add(home);
            homes.put("Homes", list);
            MiniGameControllers read = MiniGameControllers.fromNbt(homes, registries);
            read.adoptOldZones(server);
            context.assertTrue(MiniGamePages.get(server, other).adventure(), "saved with the homes: to the page");
            context.assertTrue(!read.writeNbt(new NbtCompound(), registries).getList("Homes", 10).getCompound(0).contains("Adventure"), "no longer with the homes");
        } finally {
            cleanUp(context, id);
        }
        done(context);
    }

    /**
     * A zone that can't take a round: out of a party nothing starts, and the controller says why; a zone found too
     * full when the round begins stops it. With the bubble turned off on the server, the round is played without one.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_zone_refusals")
    public void aZoneThatCantTakeARoundOutOfAParty(TestContext context) {
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        ServerConfig config = ServerConfig.get();
        int maxSize = config.miniGameBubbleMaxSize, maxBlockEntities = config.miniGameBubbleMaxBlockEntities;
        ServerPlayerEntity p1 = player(context, "a", 1.5, 1, 2.5), p2 = player(context, "b", 2.5, 1, 1.5);
        UUID id = arena(context, true);
        try {
            alone(context, p1, p2);
            p1.getInventory().setStack(0, new ItemStack(Items.DIAMOND_SWORD));
            MiniGameControllerScreenHandler screen = new MiniGameControllerScreenHandler(1, p1.getInventory(), context.getBlockEntity(HOME));

            // Bigger than the server lets a zone be
            config.miniGameBubbleMaxSize = 4;
            context.assertEquals(MiniGameTest.check(server, id).status(), MiniGameTest.Status.ZONE_TOO_BIG, "too big for this server");
            context.assertTrue(!screen.onButtonClick(p1, MiniGameControllerScreenHandler.BUTTON_PLAY) && screen.state() == State.ZONE_TOO_BIG, "the controller says so, and plays nothing");
            context.assertEquals(MiniGameTest.start(server, id, p1, 0), MiniGameTest.Status.ZONE_TOO_BIG, "nothing starts");
            context.assertTrue(MiniGameTest.of(id) == null, "no round");
            config.miniGameBubbleMaxSize = maxSize;

            // Taken by another round
            BlockPos corner = context.getAbsolutePos(new BlockPos(5, 4, 5));
            ZoneBubble other = ZoneBubbles.begin(server, UUID.randomUUID(), MiniGameZone.of(world.getRegistryKey(), corner, corner.add(4, 2, 4)), List.of(), List.of(), ZoneBubble.Options.DEFAULT);
            context.assertTrue(other.isActive(), "another round next door, over a corner of the zone");
            context.assertEquals(MiniGameTest.check(server, id).status(), MiniGameTest.Status.ZONE_BUSY, "the zone is taken");
            context.assertEquals(MiniGameTest.start(server, id, p1, 0), MiniGameTest.Status.ZONE_BUSY, "nothing starts");
            other.end();
            context.assertEquals(MiniGameTest.check(server, id).status(), MiniGameTest.Status.READY, "free again");

            // Too full: only known when the round begins, which stops it
            config.miniGameBubbleMaxBlockEntities = 1;
            context.assertEquals(MiniGameTest.start(server, id, p1, 0), MiniGameTest.Status.READY, "it starts");
            context.assertTrue(MiniGameTest.of(id) == null && ZoneBubbles.all().isEmpty(), "and stops at the departure: the zone holds too much");
            context.assertTrue(p1.getInventory().getStack(0).isOf(Items.DIAMOND_SWORD) && !p1.getCommandTags().contains(STASH_TAG), "nobody left anything at the door");
            config.miniGameBubbleMaxBlockEntities = maxBlockEntities;

            // The bubble turned off on the server: played as ever, silently
            config.miniGameBubble = false;
            context.assertEquals(MiniGameTest.check(server, id).status(), MiniGameTest.Status.READY, "no bubble on this server: it can be played");
            context.assertEquals(MiniGameTest.start(server, id, p1, 0), MiniGameTest.Status.READY, "played");
            context.assertTrue(MiniGameTest.of(id) != null && MiniGameTest.of(id).phase() == MiniGameTest.Phase.PLAYING && ZoneBubbles.all().isEmpty(), "without a bubble");
            context.assertTrue(p1.getInventory().getStack(0).isOf(Items.DIAMOND_SWORD), "everyone keeps its inventory");
        } finally {
            config.miniGameBubble = true;
            config.miniGameBubbleMaxSize = maxSize;
            config.miniGameBubbleMaxBlockEntities = maxBlockEntities;
            cleanUp(context, id, p1, p2);
        }
        done(context);
    }

    /**
     * A block the server forbids in a zone (here the lodestone, by the tag) in the arena: the controller names it and
     * says where it is, nothing starts out of a party, and a party plays its round without the bubble.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_zone_forbidden")
    public void aForbiddenBlockInTheArena(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        ServerPlayerEntity p1 = player(context, "a", 1.5, 1, 2.5), p2 = player(context, "b", 2.5, 1, 1.5);
        UUID id = arena(context, true);
        try {
            alone(context, p1, p2);
            p1.getInventory().setStack(0, new ItemStack(Items.DIAMOND_SWORD));
            BlockPos forbidden = new BlockPos(3, 2, 3);
            context.setBlockState(forbidden, Blocks.LODESTONE);
            MiniGameControllerScreenHandler screen = new MiniGameControllerScreenHandler(1, p1.getInventory(), context.getBlockEntity(HOME));
            context.assertEquals(screen.state(), State.ZONE_FORBIDDEN, "the controller says a forbidden block is in the zone");
            context.assertTrue(screen.forbiddenBlock() == Blocks.LODESTONE && screen.forbiddenPos().equals(context.getAbsolutePos(forbidden)), "which one, and where");
            context.assertTrue(!screen.onButtonClick(p1, MiniGameControllerScreenHandler.BUTTON_PLAY), "« Play » does nothing");
            context.assertEquals(MiniGameTest.start(server, id, p1, 0), MiniGameTest.Status.ZONE_FORBIDDEN, "nothing starts out of a party");
            context.assertTrue(MiniGameTest.of(id) == null && p1.getInventory().getStack(0).isOf(Items.DIAMOND_SWORD), "no round, no inventory left at the door");

            PartyControllerEntity controller = party(context, id, p1, p2);
            MiniGamePartyStep step = (MiniGamePartyStep) controller.getPartyData().getCurrentStep();
            step.leaveForMiniGame(controller);
            context.assertTrue(step.isPractice() && step.isAway(p1.getUuid()), "a party plays its round all the same");
            context.assertTrue(ZoneBubbles.all().isEmpty() && p1.getInventory().getStack(0).isOf(Items.DIAMOND_SWORD), "without the bubble");
        } finally {
            cleanUp(context, id, p1, p2);
        }
        done(context);
    }

    // ------------------------------------------------------------------ in a party

    /**
     * A party's mini-game in a zone: the practice round and the real round are each played in a bubble of their own,
     * the arena put back in between; what the real round pays goes to what the players own.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_zone_party", tickLimit = 60)
    public void aPartyPlaysItsRoundsInTheZone(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity p1 = player(context, "a", 7.5, 1, 1.5), p2 = player(context, "b", 7.5, 1, 2.5);
        UUID id = arena(context, true);
        MiniGamePartyStep step;
        ZoneBubble practice;
        try {
            alone(context, p1, p2);
            p1.getInventory().setStack(0, new ItemStack(Items.DIAMOND_SWORD));
            PartyControllerEntity controller = party(context, id, p1, p2);
            step = (MiniGamePartyStep) controller.getPartyData().getCurrentStep();

            // The practice round
            step.leaveForMiniGame(controller);
            practice = ZoneBubbles.ofPlayer(p1);
            context.assertTrue(step.isPractice() && practice != null && practice.isActive() && practice.isParticipant(p2.getUuid()), "the practice round is played in a bubble");
            context.assertTrue(p1.getInventory().isEmpty() && zone(context).contains(p1.getBlockPos()), "its players left their inventory at the door, and are in the arena");
            MiniGameControllerScreenHandler screen = new MiniGameControllerScreenHandler(1, p1.getInventory(), context.getBlockEntity(HOME));
            context.assertTrue(screen.state() == State.PARTY_PRACTICE && screen.isVoter(), "the controller of the arena shows the vote");
            spoil(context, p1);
            context.assertTrue(Podiums.toggle(p1, world, context.getAbsolutePos(FIRST)) && Podiums.toggle(p2, world, context.getAbsolutePos(SECOND)), "both take a place");
            context.assertTrue(step.isPractice() && step.getLastResults() != null && step.getLastResults().kind() == MiniGameResults.Kind.PRACTICE, "practice results");
            context.assertTrue(practice.state() == ZoneBubble.State.ENDED && ZoneBubbles.ofPlayer(p1) == null, "its results read, the practice round gives its bubble back");
            expectArenaAsBuilt(context);
            context.assertTrue(p1.getInventory().getStack(0).isOf(Items.DIAMOND_SWORD) && p1.getInventory().count(Items.DIAMOND) == 0 && coins(p1) == 0, "inventories back, nothing paid");

            // The vote, from the controller (one of the round's players uses it wherever it stands) and the key
            context.assertTrue(screen.onButtonClick(p1, MiniGameControllerScreenHandler.BUTTON_READY) && MiniGamePartyStep.toggleReady(p2), "everyone is ready");
            ZoneBubble real = ZoneBubbles.ofPlayer(p1);
            context.assertTrue(step.isPlaying() && real != null && real != practice && real.isActive(), "the real round, in a bubble of its own");
            context.assertTrue(p1.getInventory().isEmpty() && occupant(context, FIRST) == null, "from empty inventories and an arena as built");
        } catch (RuntimeException e) {
            cleanUp(context, id, p1, p2);
            throw e;
        }
        // A moment later (a podium is not taken twice in a row by the same player)
        context.waitAndRun(10, () -> {
            try {
                ZoneBubble real = ZoneBubbles.ofPlayer(p1);
                context.assertTrue(real != null && real.isActive() && real.isParticipant(p2.getUuid()), "the real round goes on in its bubble");
                spoil(context, p2);
                context.assertTrue(Podiums.toggle(p2, world, context.getAbsolutePos(FIRST)) && Podiums.toggle(p1, world, context.getAbsolutePos(SECOND)), "both take a place");
                context.assertEquals(step.getPhase(), MiniGamePartyStep.Phase.FINISHED, "the mini-game is over");
                context.assertTrue(real.state() == ZoneBubble.State.ENDED && ZoneBubbles.all().isEmpty(), "and so is its bubble");
                expectArenaAsBuilt(context);
                context.assertTrue(coins(p2) == 10 && coins(p1) == 5, "the real round pays, into what the players own");
                context.assertTrue(p1.getInventory().getStack(0).isOf(Items.DIAMOND_SWORD) && p2.getInventory().count(Items.DIAMOND) == 0, "inventories back, without the round's loot");
            } finally {
                cleanUp(context, id, p1, p2);
            }
            done(context);
        });
    }

    /** In a party, a zone that can't take a round never holds the party up: the round is played without its protection. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_zone_party_refused")
    public void aPartyIsNeverHeldUpByAZone(TestContext context) {
        ServerConfig config = ServerConfig.get();
        int maxSize = config.miniGameBubbleMaxSize;
        ServerPlayerEntity p1 = player(context, "a", 7.5, 1, 1.5), p2 = player(context, "b", 7.5, 1, 2.5);
        UUID id = arena(context, true);
        try {
            alone(context, p1, p2);
            p1.getInventory().setStack(0, new ItemStack(Items.DIAMOND_SWORD));
            config.miniGameBubbleMaxSize = 4;
            PartyControllerEntity controller = party(context, id, p1, p2);
            MiniGamePartyStep step = (MiniGamePartyStep) controller.getPartyData().getCurrentStep();
            step.leaveForMiniGame(controller);
            context.assertTrue(step.isPractice() && step.isAway(p1.getUuid()) && step.isAway(p2.getUuid()), "the practice round is played all the same");
            context.assertTrue(ZoneBubbles.all().isEmpty() && p1.getInventory().getStack(0).isOf(Items.DIAMOND_SWORD), "without a bubble: everyone keeps its inventory");
            context.assertTrue(MiniGamePartyStep.toggleReady(p1) && MiniGamePartyStep.toggleReady(p2) && step.isPlaying(), "and so is the real round");
            context.assertTrue(Podiums.toggle(p1, context.getWorld(), context.getAbsolutePos(FIRST)) && Podiums.toggle(p2, context.getWorld(), context.getAbsolutePos(SECOND)), "to its podiums");
            context.assertTrue(step.getPhase() == MiniGamePartyStep.Phase.FINISHED && coins(p1) == 10 && coins(p2) == 5, "and paid");
        } finally {
            config.miniGameBubbleMaxSize = maxSize;
            cleanUp(context, id, p1, p2);
        }
        done(context);
    }

    /**
     * A player who leaves the server during a round in a zone takes what it owns with it, and only that; when it comes
     * back after the round, it is brought back where it stood before it, and given nothing twice.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_zone_away")
    public void aPlayerAwayAtTheEndOfARoundInAZone(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        ServerPlayerEntity p1 = player(context, "a", 1.5, 1, 2.5), p2 = player(context, "b", 2.5, 1, 1.5);
        UUID id = arena(context, true);
        Vec3d start2 = p2.getPos();
        GameProfile away = p2.getGameProfile();
        ServerPlayerEntity back = null;
        try {
            alone(context, p1, p2);
            p2.getInventory().setStack(0, new ItemStack(Items.COBBLESTONE, 12));
            context.assertEquals(MiniGameTest.start(server, id, p1, 0), MiniGameTest.Status.READY, "played");
            context.assertTrue(ZoneBubbles.ofPlayer(p2) != null && p2.getInventory().isEmpty(), "p2 plays with a session inventory");
            p2.getInventory().insertStack(new ItemStack(Items.DIAMOND, 3));
            TestPlayers.leave(p2);
            context.assertTrue(server.getPlayerManager().getPlayer(away.getId()) == null, "p2 left the server");
            context.assertTrue(p2.getInventory().count(Items.COBBLESTONE) == 12, "it left with what it owns");
            context.assertTrue(p2.getInventory().count(Items.DIAMOND) == 0, "nothing of the round");
            context.assertTrue(!p2.getCommandTags().contains(STASH_TAG), "no mark of a session");
            MiniGameTest.stop(id);
            context.assertTrue(MiniGameReturns.isPending(server, away.getId()), "the round is over: p2 is waited for");
            back = TestPlayers.join(context, away);
            context.assertTrue(back.getPos().distanceTo(start2) < 0.01, "back where it stood before the round");
            context.assertTrue(back.getInventory().count(Items.COBBLESTONE) == 12 && back.getInventory().count(Items.DIAMOND) == 0,
                    "with what it owns, once");
            context.assertTrue(ZoneBubbles.ofPlayer(back) == null && !back.getCommandTags().contains(STASH_TAG), "of no session");
        } finally {
            if (back != null) cleanUp(context, id, back);
            cleanUp(context, id, p1, p2);
        }
        done(context);
    }

    // ------------------------------------------------------------------ deaths and disconnections during a round

    /** As the connection does when its player asks to respawn (the connection then plays the new player). */
    private static ServerPlayerEntity respawn(MinecraftServer server, ServerPlayerEntity dead) {
        ServerPlayerEntity respawned = server.getPlayerManager().respawnPlayer(dead, false, net.minecraft.entity.Entity.RemovalReason.KILLED);
        respawned.networkHandler.player = respawned;
        return respawned;
    }

    /** Restore off, adventure off. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_zone_life_plain", tickLimit = 80)
    public void deathsAndDisconnectionsWithoutOptions(TestContext context) {
        deathsAndDisconnections(context, false, false);
    }

    /** Restore off, adventure on. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_zone_life_adventure", tickLimit = 80)
    public void deathsAndDisconnectionsInAdventure(TestContext context) {
        deathsAndDisconnections(context, false, true);
    }

    /** Restore on, adventure off. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_zone_life_restore", tickLimit = 80)
    public void deathsAndDisconnectionsInABubble(TestContext context) {
        deathsAndDisconnections(context, true, false);
    }

    /** Restore on, adventure on. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_zone_life_both", tickLimit = 80)
    public void deathsAndDisconnectionsInABubbleInAdventure(TestContext context) {
        deathsAndDisconnections(context, true, true);
    }

    /**
     * A round out of a party, with or without « Restaurer » and « Aventure »: a player who dies and respawns during it
     * (at his own spawn: the round has no respawn place of its own), one still on his death screen when it ends, one
     * whose game leaves and comes back during it, and a spectator whose game leaves and comes back after it (the server
     * restarted in between). Each ends the round with his own game mode, no side colour, no session, his own things
     * exactly once (a bubble's session inventory never kept, nothing doubled), and where he stood before it.
     */
    private void deathsAndDisconnections(TestContext context, boolean restore, boolean adventure) {
        MinecraftServer server = context.getWorld().getServer();
        ServerPlayerEntity p1 = player(context, "d", 1.5, 1, 2.5), p2 = player(context, "s", 2.5, 1, 1.5), p3 = player(context, "r", 1.5, 1, 0.5),
                watcher = player(context, "w", 6.5, 1, 2.5);
        UUID id = arena(context, true);
        context.setBlockState(new BlockPos(6, 2, 1), ModBlocks.PIPES[PipeKind.OPAQUE.ordinal()][0].getDefaultState().with(PipeBlock.SOLID, PipeSolid.DOWN));
        MiniGamePages.toggleLink(server, id, global(context, new BlockPos(6, 2, 1)), Direction.UP, MiniGamePipeRole.SPECTATORS);
        MiniGamePages.update(server, MiniGamePages.get(server, id).withRestore(restore).withAdventure(adventure));
        ServerPlayerEntity[] all = {p1, p2, p3, watcher};
        Map<UUID, Vec3d> starts = new HashMap<>();
        for (ServerPlayerEntity player : all) {
            starts.put(player.getUuid(), player.getPos());
            player.getInventory().setStack(0, new ItemStack(Items.GOLDEN_SWORD));
        }
        GameProfile profile3 = p3.getGameProfile(), profileW = watcher.getGameProfile();
        List<ServerPlayerEntity> made = new ArrayList<>(List.of(all));
        try {
            alone(context, all);
            context.assertEquals(MiniGameTest.start(server, id, p1, 0), MiniGameTest.Status.READY, "played");
            MiniGameTest played = MiniGameTest.of(id);
            context.assertTrue(played.participants().containsAll(List.of(p1.getUuid(), p2.getUuid(), p3.getUuid()))
                    && played.spectators().contains(watcher.getUuid()), "three players, a spectator");
            context.assertTrue((ZoneBubbles.ofPlayer(p1) != null) == restore, "a bubble only with « Restaurer »");
            context.assertTrue((p1.interactionManager.getGameMode() == GameMode.ADVENTURE) == adventure, "adventure mode only with « Aventure »");

            // p1 dies and respawns during the round; p2 dies and stays on his death screen
            p1.kill();
            ServerPlayerEntity p1b = respawn(server, p1);
            made.add(p1b);
            context.assertTrue((p1b.interactionManager.getGameMode() == GameMode.ADVENTURE) == adventure, "respawned: still in the round's game mode");
            p2.kill();
            // p3's game leaves and comes back during the round; the spectator's leaves
            TestPlayers.leave(p3);
            ServerPlayerEntity p3b = TestPlayers.join(context, profile3);
            made.add(p3b);
            context.assertTrue(p3b.interactionManager.getGameMode() == GameMode.SURVIVAL && ZoneBubbles.ofPlayer(p3b) == null
                    && !p3b.getCommandTags().contains(STASH_TAG), "back during the round: himself, no session");
            TestPlayers.leave(watcher);

            MiniGameTest.stop(id);
            context.assertTrue(MiniGameReturns.isPending(server, p2.getUuid()), "dead at the end: brought back once respawned");
            context.assertTrue(MiniGameReturns.isPending(server, profileW.getId()), "away at the end: brought back when he comes");
            MiniGameReturns.simulateRestart(server);
            ServerPlayerEntity p2b = respawn(server, p2);
            made.add(p2b);
            ServerPlayerEntity watcherB = TestPlayers.join(context, profileW);
            made.add(watcherB);
            context.waitAndRun(3, () -> {
                try {
                    for (ServerPlayerEntity player : List.of(p1b, p2b, p3b, watcherB)) {
                        String who = player.getGameProfile().getName();
                        context.assertTrue(player.getPos().distanceTo(starts.get(player.getUuid())) < 0.01,
                                who + " is back where he stood: " + context.getRelative(player.getPos()));
                        context.assertTrue(player.interactionManager.getGameMode() == GameMode.SURVIVAL, who + " has his own game mode");
                        context.assertTrue(!MiniGameNameColors.isColoured(player.getUuid()) && !MiniGamePipes.isInParty(player.getUuid()), who + " is out of the round");
                        context.assertTrue(ZoneBubbles.ofPlayer(player) == null && !player.getCommandTags().contains(STASH_TAG)
                                && MiniGameAdventure.own(player) == null, who + " holds nothing of the round");
                        int swords = player.getInventory().count(Items.GOLDEN_SWORD);
                        boolean died = player == p1b || player == p2b;
                        // Without bubble, a death is a vanilla death: what he carried fell where he died
                        context.assertTrue(died && !restore ? swords <= 1 : swords == 1, who + " has his own sword once, not " + swords);
                        context.assertTrue(!MiniGameReturns.isPending(server, player.getUuid()), who + " is waited for no more");
                    }
                } finally {
                    for (ServerPlayerEntity player : made) cleanUp(context, id, player);
                }
                done(context);
            });
        } catch (RuntimeException e) {
            for (ServerPlayerEntity player : made) cleanUp(context, id, player);
            throw e;
        }
    }
}
