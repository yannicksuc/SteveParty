package fr.lordfinn.steveparty.gametest;

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
import fr.lordfinn.steveparty.components.ZoneSelection;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.minigame.MiniGameControllers;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import fr.lordfinn.steveparty.minigame.MiniGamePodiumLink;
import fr.lordfinn.steveparty.minigame.MiniGameResults;
import fr.lordfinn.steveparty.minigame.MiniGameTest;
import fr.lordfinn.steveparty.minigame.zone.MiniGameZone;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubble;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubbleConfig;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubbles;
import fr.lordfinn.steveparty.podium.Podiums;
import fr.lordfinn.steveparty.screen_handlers.custom.MiniGameControllerScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.MiniGameControllerScreenHandler.State;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
 * A mini-game whose page has a zone (the Zone Cartridge of its Mini-game Controller) is played, round after round,
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

    private static ItemStack cartridge(TestContext context, BlockBox box) {
        ItemStack cartridge = new ItemStack(ModItems.ZONE_CARTRIDGE);
        cartridge.set(ModComponents.ZONE_SELECTION, new ZoneSelection(context.getWorld().getRegistryKey(), Optional.empty(), Optional.of(box)));
        return cartridge;
    }

    /**
     * The arena: a floor, a players pipe, two podiums, a stone block and a chest of diamonds, and the page of it all.
     *
     * @param zoned its controller holds a Zone Cartridge with the zone of the arena
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
        if (zoned) controller.setCartridge(cartridge(context, zone(context).box()));
        return id;
    }

    /** A connected survival player with a name of its own, standing at a relative position. */
    private static ServerPlayerEntity player(TestContext context, String name, double x, double y, double z) {
        ServerWorld world = context.getWorld();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "a" + SERIAL.incrementAndGet() + name);
        ConnectedClientData data = ConnectedClientData.createDefault(profile, false);
        ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, profile, data.syncedOptions());
        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new EmbeddedChannel(connection);
        world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
        player.changeGameMode(GameMode.SURVIVAL);
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
        for (ZoneBubble left : ZoneBubbles.all()) left.endNow();
        if (context.getBlockState(HOME).isOf(ModBlocks.MINI_GAME_CONTROLLER)) context.removeBlock(HOME);
        for (ServerPlayerEntity player : players) {
            if (player.hasVehicle()) player.stopRiding();
            MiniGamePipes.leaveParty(player.getUuid());
            if (context.getWorld().getServer().getPlayerManager().getPlayer(player.getUuid()) != null)
                context.getWorld().getServer().getPlayerManager().remove(player);
        }
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
     * inventories are given back and the controller, which stands in the zone, still holds its page and cartridge.
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
            context.assertTrue(!screen.onButtonClick(p1, MiniGameControllerScreenHandler.BUTTON_ADVENTURE), "and its option");

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
            context.assertTrue(id.equals(controller.getPageId()) && controller.getZone().isPresent(), "the controller still holds its page and its cartridge");
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
            context.assertTrue(MiniGameControllers.zoneOf(server, id).isEmpty(), "the controller was broken during the round");

            MiniGameTest.stop(id);
            context.assertTrue(MiniGameTest.of(id) == null && ZoneBubbles.all().isEmpty(), "stopped: no round, no bubble");
            expectArenaAsBuilt(context);
            context.assertTrue(p1.getInventory().getStack(0).isOf(Items.DIAMOND_SWORD) && p1.getInventory().count(Items.DIAMOND) == 0, "p1 has what it owns, nothing of the round");
            MiniGameControllerBlockEntity controller = context.getBlockEntity(HOME);
            context.assertTrue(id.equals(controller.getPageId()) && controller.getZone().isPresent(), "the controller is back with its page and its cartridge");
            controller.serverTick(world);
            context.assertTrue(MiniGameControllers.zoneOf(server, id).isPresent(), "and, from its first tick, the home of its page again");

            // Without the cartridge: the mini-game has no zone, and is played as it always was
            controller.setCartridge(ItemStack.EMPTY);
            screen = new MiniGameControllerScreenHandler(1, p1.getInventory(), controller);
            context.assertTrue(screen.onButtonClick(p1, MiniGameControllerScreenHandler.BUTTON_PLAY), "Play, without zone");
            context.assertTrue(MiniGameTest.of(id) != null && ZoneBubbles.all().isEmpty() && ZoneBubbles.ofPlayer(p1) == null, "a round, and no bubble");
            context.assertTrue(p1.getInventory().getStack(0).isOf(Items.DIAMOND_SWORD), "everyone keeps its inventory");
        } finally {
            cleanUp(context, id, p1, p2);
        }
        done(context);
    }

    /** The « adventure mode » option of the controller: the players of a round play in adventure mode, and get their mode back. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_zone_adventure")
    public void theAdventureOptionOfTheController(TestContext context) {
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        ServerPlayerEntity p1 = player(context, "a", 1.5, 1, 2.5), p2 = player(context, "b", 2.5, 1, 1.5);
        UUID id = arena(context, true);
        try {
            alone(context, p1, p2);
            MiniGameControllerBlockEntity controller = context.getBlockEntity(HOME);
            MiniGameControllerScreenHandler screen = new MiniGameControllerScreenHandler(1, p1.getInventory(), controller);
            context.assertTrue(!screen.isAdventure() && !MiniGameControllers.isAdventure(server, id), "off by default");
            context.assertTrue(screen.onButtonClick(p1, MiniGameControllerScreenHandler.BUTTON_ADVENTURE), "its button");
            context.assertTrue(screen.isAdventure() && controller.isAdventure() && MiniGameControllers.isAdventure(server, id), "the option is on, and known of its page");
            MiniGameControllers read = MiniGameControllers.fromNbt(MiniGameControllers.get(server).writeNbt(new NbtCompound(), world.getRegistryManager()), world.getRegistryManager());
            context.assertTrue(read.adventure(id) && read.zone(id).isPresent(), "saved with the controllers");
            MiniGameControllerBlockEntity copy = new MiniGameControllerBlockEntity(controller.getPos(), controller.getCachedState());
            copy.read(controller.createNbt(world.getRegistryManager()), world.getRegistryManager());
            context.assertTrue(copy.isAdventure(), "and with the controller");

            context.assertEquals(MiniGameTest.start(server, id, p1, 0), MiniGameTest.Status.READY, "played");
            context.assertTrue(p1.interactionManager.getGameMode() == GameMode.ADVENTURE && p2.interactionManager.getGameMode() == GameMode.ADVENTURE, "its players are in adventure mode");
            MiniGameTest.stop(id);
            context.assertTrue(p1.interactionManager.getGameMode() == GameMode.SURVIVAL && p2.interactionManager.getGameMode() == GameMode.SURVIVAL, "and get their mode back");
            context.assertTrue(screen.onButtonClick(p1, MiniGameControllerScreenHandler.BUTTON_ADVENTURE) && !MiniGameControllers.isAdventure(server, id), "off again");
        } finally {
            cleanUp(context, id, p1, p2);
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
        ZoneBubbleConfig config = ZoneBubbleConfig.get();
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
        ZoneBubbleConfig config = ZoneBubbleConfig.get();
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
}
