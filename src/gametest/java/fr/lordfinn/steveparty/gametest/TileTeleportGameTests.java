package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSupport;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileTeleport;
import fr.lordfinn.steveparty.board.BoardGraph;
import fr.lordfinn.steveparty.board.BoardValidator;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TeleportNetwork;
import fr.lordfinn.steveparty.components.TeleportSettingsComponent;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.DirectionDisplayEntity;
import fr.lordfinn.steveparty.events.TileReachedEvent;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.TeleportCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenus;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeRef;
import fr.lordfinn.steveparty.service.TokenMovementService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock.TILE_TYPE;
import static fr.lordfinn.steveparty.gametest.kit.TestAsserts.assertOn;
import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * The Teleport tile and its networks: a token landing on it is warped to another Teleport tile of the same colour on
 * the same board (random or in turn), never across networks nor boards; alone in its network it sends no one (and the
 * board check warns); on arrival the token stays, or is pushed one space on (the owner chooses at a fork), that space
 * triggering its effect or not; no chains; the settings survive saving and are only changed by a player allowed to.
 */
public class TileTeleportGameTests implements FabricGameTest {
    private static final int WAIT = TileTeleport.TOTAL_TICKS + 6;
    private static final BlockPos CONTROLLER = new BlockPos(7, 1, 7);
    /** Tiles 2 blocks apart: along x on z = 1, then back along x on z = 3. */
    private static final List<BlockPos> PATH = List.of(
            new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1), new BlockPos(7, 1, 1),
            new BlockPos(7, 1, 3), new BlockPos(5, 1, 3), new BlockPos(3, 1, 3), new BlockPos(1, 1, 3));

    // ---------------------------------------------------------------- board

    /** A tile on a stone floor holding {@code cartridge}, linked to {@code to} (relative positions). */
    private static BoardSpaceBlockEntity tile(TestContext context, BlockPos pos, ItemStack cartridge, BlockPos... to) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.TILE);
        return put(context, pos, cartridge, to);
    }

    private static BoardSpaceBlockEntity put(TestContext context, BlockPos pos, ItemStack cartridge, BlockPos... to) {
        List<BlockPos> destinations = new ArrayList<>();
        for (BlockPos next : to) destinations.add(context.getAbsolutePos(next));
        if (!destinations.isEmpty()) cartridge.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(destinations, ""));
        BoardSpaceBlockEntity tile = context.getBlockEntity(pos);
        tile.setStack(0, cartridge);
        return tile;
    }

    /**
     * The cartridge's menu (see CartridgeMenus#apply) sets all of {@code settings}, module by module, in the tile's
     * cartridge at {@code pos} or (null) the one in the main hand.
     *
     * @return true if every change was accepted
     */
    private static boolean menu(ServerPlayerEntity player, BlockPos pos, TeleportSettingsComponent settings) {
        CartridgeRef ref = pos == null
                ? CartridgeRef.hand(Hand.MAIN_HAND)
                : CartridgeRef.slot(pos, 0);
        boolean ok = CartridgeMenus.apply(player, ref, "network", settings.network().ordinal());
        ok &= CartridgeMenus.apply(player, ref, "arrival", settings.push() ? 1 : 0);
        if (settings.push()) ok &= CartridgeMenus.apply(player, ref, "triggers", settings.pushTriggers() ? 0 : 1);
        ok &= CartridgeMenus.apply(player, ref, "pick", settings.cycle() ? 1 : 0);
        return ok;
    }

    private static ItemStack plain() {
        return new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
    }

    private static ItemStack bonus() {
        ItemStack cartridge = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
        cartridge.set(ModComponents.INVENTORY_COMPONENT, new InventoryComponent(List.of(new ItemStack(Items.DIAMOND))));
        return cartridge;
    }

    private static ItemStack teleport(TeleportSettingsComponent settings) {
        ItemStack cartridge = new ItemStack(ModItems.TELEPORT_CARTRIDGE);
        cartridge.set(ModComponents.TELEPORT_SETTINGS, settings);
        return cartridge;
    }

    private static ItemStack teleport(TeleportNetwork network) {
        return teleport(TeleportSettingsComponent.DEFAULT.withNetwork(network));
    }

    /** The first {@code cartridges.length} tiles of {@link #PATH}, each linked to the next. */
    private static List<BoardSpaceBlockEntity> path(TestContext context, ItemStack... cartridges) {
        List<BoardSpaceBlockEntity> tiles = new ArrayList<>();
        for (int i = 0; i < cartridges.length; i++) {
            BlockPos[] next = i + 1 < cartridges.length ? new BlockPos[]{PATH.get(i + 1)} : new BlockPos[0];
            tiles.add(tile(context, PATH.get(i), cartridges[i], next));
        }
        return tiles;
    }

    private static PigEntity token(TestContext context, BlockPos tile) {
        PigEntity pig = context.spawnMob(EntityType.PIG, tile);
        TokenizedEntityInterface token = (TokenizedEntityInterface) pig;
        token.steveparty$setTokenized(true);
        token.steveparty$setStatus(TokenStatus.IN_GAME);
        Vec3d stand = BoardSpaces.standPos(context.getWorld(), context.getAbsolutePos(tile));
        pig.refreshPositionAndAngles(stand.x, stand.y, stand.z, 0, 0);
        return pig;
    }

    private static final Map<TestContext, List<Runnable>> CLEANUPS = new WeakHashMap<>();

    /** Runs {@code cleanup} when the test succeeds ({@link #finish}; a final task would end these waiting tests). */
    private static void onEnd(TestContext context, Runnable cleanup) {
        CLEANUPS.computeIfAbsent(context, c -> new ArrayList<>()).add(cleanup);
    }

    private static void finish(TestContext context) {
        List<Runnable> cleanups = CLEANUPS.remove(context);
        if (cleanups != null) cleanups.forEach(Runnable::run);
        context.complete();
    }

    /** Records the teleports of this test's token. */
    private static List<TileTeleport.Teleported> teleports(TestContext context, MobEntity token) {
        List<TileTeleport.Teleported> list = new ArrayList<>();
        Consumer<TileTeleport.Teleported> listener = teleported -> {
            if (teleported.token().equals(token.getUuid())) list.add(teleported);
        };
        TileTeleport.LISTENERS.add(listener);
        onEnd(context, () -> TileTeleport.LISTENERS.remove(listener));
        return list;
    }

    private static List<TileFeedback.Played> landings(TestContext context) {
        List<TileFeedback.Played> list = new ArrayList<>();
        BlockBox box = box(context);
        Consumer<TileFeedback.Played> listener = played -> {
            if (played.kind() == TileFeedback.Kind.LAND && box.contains(played.tile())) list.add(played);
        };
        TileFeedback.LISTENERS.add(listener);
        onEnd(context, () -> TileFeedback.LISTENERS.remove(listener));
        return list;
    }

    private static boolean landed(List<TileFeedback.Played> landings, TestContext context, BlockPos at, TileFeedback.Landing landing) {
        BlockPos absolute = context.getAbsolutePos(at);
        return landings.stream().anyMatch(played -> played.tile().equals(absolute) && played.landing() == landing);
    }

    private static BlockBox box(TestContext context) {
        BlockPos min = context.getAbsolutePos(BlockPos.ORIGIN), max = context.getAbsolutePos(new BlockPos(8, 4, 8));
        return BlockBox.create(min, max);
    }

    /** A party of this one token, at its turn (step 1); the next turn is step 2. */
    private static PartyControllerEntity party(TestContext context, MobEntity token) {
        context.setBlockState(CONTROLLER.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        PartyData data = new PartyData();
        data.addToken(token.getUuid());
        data.addStep(new PartyStep());
        data.addStep(new TokenTurnPartyStep(token.getUuid(), null));
        data.addStep(new TokenTurnPartyStep(token.getUuid(), null));
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(token.getUuid()))));
        controller.setPartyData(data);
        controller.nextStep();
        controller.nextStep();
        context.assertEquals(data.getStepIndex(), 1, "the token's turn");
        onEnd(context, () -> {
            ((TokenizedEntityInterface) token).steveparty$setTokenized(false);
            context.removeBlock(CONTROLLER);
        });
        return controller;
    }

    private static void assertStandsOn(TestContext context, MobEntity token, BlockPos absolute, String what) {
        Vec3d stand = BoardSpaces.standPos(context.getWorld(), absolute);
        context.assertTrue(token.getPos().distanceTo(stand) < 0.05, what + ": stands on " + stand + ", is at " + token.getPos());
        assertOn(context, token, absolute, what);
    }

    private static void assertOn(TestContext context, MobEntity token, BlockPos absolute, String what) {
        BoardSpaceBlockEntity on = BoardSpaces.boardSpaceOf(token);
        context.assertTrue(on != null && on.getPos().equals(absolute), what + ": on " + absolute + ", found " + (on == null ? null : on.getPos()));
    }

    private static BooleanSupplier turnEnded(PartyControllerEntity controller) {
        return () -> controller.getPartyData().getStepIndex() >= 2;
    }

    private static float scaleOf(MobEntity mob) {
        return (float) mob.getAttributeBaseValue(EntityAttributes.GENERIC_SCALE);
    }

    // ---------------------------------------------------------------- tests

    /**
     * In a party: warped to the other violet tile of the board, the turn goes on once it has reappeared; it stays on
     * that tile, which doesn't send it on (no chain) nor lands.
     */
    @GameTest(batchId = "tile_teleport", templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void aTokenIsSentToTheOtherTileOfItsNetworkAndStays(TestContext context) {
        List<BoardSpaceBlockEntity> tiles = path(context, teleport(TeleportNetwork.VIOLET), plain(), teleport(TeleportNetwork.VIOLET), bonus());
        BoardSpaceBlockEntity from = tiles.get(0);
        BlockPos to = context.getAbsolutePos(PATH.get(2));
        context.expectBlockProperty(PATH.get(0), TILE_TYPE, BoardSpaceType.TILE_TELEPORT);
        context.assertEquals(TileFeedback.landingOf(from), TileFeedback.Landing.TELEPORT, "teleport landing");
        context.assertEquals(from.getBoardSpaceBehavior().comparatorLevel(from, from.getActiveCartridgeItemStack()),
                BoardSpaceRedstoneRouterBlockEntity.LEVEL_TELEPORT, "a Router's comparator reads 8");
        context.assertEquals(from.getActiveCartridgeItemStack().get(ModComponents.COLOR), TeleportNetwork.VIOLET.color(), "violet tile");
        PigEntity pig = token(context, PATH.get(0));
        List<TileTeleport.Teleported> teleports = teleports(context, pig);
        List<TileFeedback.Played> landings = landings(context);
        PartyControllerEntity controller = party(context, pig);
        PartyData data = controller.getPartyData();

        from.onDestinationReached(pig, controller);
        context.assertEquals(data.getStepIndex(), 1, "the turn waits for the warp");
        context.assertTrue(TileTeleport.isTeleporting(pig), "warping");
        context.assertTrue(!TokenStatus.hasStatus(((TokenizedEntityInterface) pig).steveparty$getStatus(), TokenStatus.CAN_MOVE),
                "no second move during the warp");
        context.assertEquals(landings.size(), 1, "one landing: the teleport tile's");
        context.assertEquals(landings.getFirst().landing(), TileFeedback.Landing.TELEPORT, "with the teleport jingle");
        context.waitAndRun(8, () -> context.assertTrue(pig.getAttributeValue(EntityAttributes.GENERIC_SCALE) < 0.9 * scaleOf(pig), "shrinking"));
        context.waitAndRun(WAIT, () -> {
            assertStandsOn(context, pig, to, "stays on the other tile");
            context.assertTrue(!TileTeleport.isTeleporting(pig), "done");
            context.assertTrue(pig.getAttributeInstance(EntityAttributes.GENERIC_SCALE).getModifier(Steveparty.id("teleport_shrink")) == null,
                    "back to its size");
            context.assertEquals(teleports.size(), 1, "one teleport, no chain");
            context.assertEquals(teleports.getFirst().to(), to, "to the other violet tile");
            context.assertEquals(data.getStepIndex(), 2, "the next turn");
            context.assertEquals(landings.size(), 1, "no landing on the arrival tile");
            finish(context);
        });
    }

    /**
     * Networks never mix: a violet tile sends to violet tiles only, a green one to green ones; a violet tile of another
     * board (no path joins it) is never picked. Several tiles: each at random, or in turn.
     */
    @GameTest(batchId = "tile_teleport", templateName = EMPTY_STRUCTURE)
    public void networksAndBoardsDoNotMix(TestContext context) {
        List<BoardSpaceBlockEntity> tiles = path(context, teleport(TeleportNetwork.VIOLET), teleport(TeleportNetwork.GREEN),
                teleport(TeleportNetwork.VIOLET), teleport(TeleportNetwork.GREEN), teleport(TeleportNetwork.VIOLET), plain());
        // Another board: a violet tile no path joins
        BoardSpaceBlockEntity elsewhere = tile(context, new BlockPos(1, 1, 6), teleport(TeleportNetwork.VIOLET));
        BlockPos v2 = context.getAbsolutePos(PATH.get(2)), v4 = context.getAbsolutePos(PATH.get(4)), g3 = context.getAbsolutePos(PATH.get(3));
        Set<BlockPos> violet = new HashSet<>(), green = new HashSet<>();
        for (int i = 0; i < 60; i++) {
            violet.add(TileTeleport.pick(context.getWorld(), tiles.get(0), tiles.get(0).getActiveCartridgeItemStack()));
            green.add(TileTeleport.pick(context.getWorld(), tiles.get(1), tiles.get(1).getActiveCartridgeItemStack()));
        }
        context.assertEquals(violet, Set.of(v2, v4), "violet: each other violet tile of the board at random, never green nor the other board");
        context.assertEquals(green, Set.of(g3), "green: the other green tile only");
        context.assertTrue(TileTeleport.pick(context.getWorld(), elsewhere, elsewhere.getActiveCartridgeItemStack()) == null,
                "the violet tile of the other board is alone in its network");

        put(context, PATH.get(0), teleport(TeleportSettingsComponent.DEFAULT.withCycle(true)), PATH.get(1));
        List<BlockPos> order = new ArrayList<>();
        for (int i = 0; i < 3; i++) order.add(TileTeleport.pick(context.getWorld(), tiles.get(0), tiles.get(0).getActiveCartridgeItemStack()));
        context.assertEquals(order, List.of(v2, v4, v2), "in turn, then again");

        BoardGraph graph = BoardGraph.collect(context.getWorld(), box(context));
        context.assertEquals(graph.teleportPartners(context.getAbsolutePos(PATH.get(0))), List.of(v2, v4), "the board's violet tiles");
        context.assertTrue(graph.isTeleportAlone(graph.node(context.getAbsolutePos(new BlockPos(1, 1, 6)))), "the other board's one is alone");
        finish(context);
    }

    /** Alone in its network: a plain landing (no teleport, the turn goes on at once), and the board check warns. */
    @GameTest(batchId = "tile_teleport", templateName = EMPTY_STRUCTURE)
    public void aTileAloneInItsNetworkSendsNoOneAndIsWarned(TestContext context) {
        ItemStack start = new ItemStack(ModItems.TILE_BEHAVIOR_START);
        List<BoardSpaceBlockEntity> tiles = path(context, start, teleport(TeleportNetwork.VIOLET), teleport(TeleportNetwork.GREEN), plain(), plain());
        BlockPos alone = context.getAbsolutePos(PATH.get(1)), green = context.getAbsolutePos(PATH.get(2));
        context.assertTrue(warned(context, alone) && warned(context, green), "both alone in their network: warned");
        PigEntity pig = token(context, PATH.get(1));
        List<TileTeleport.Teleported> teleports = teleports(context, pig);
        List<TileFeedback.Played> landings = landings(context);
        PartyControllerEntity controller = party(context, pig);
        tiles.get(1).onDestinationReached(pig, controller);
        context.assertTrue(!TileTeleport.isTeleporting(pig), "no teleport");
        context.assertEquals(controller.getPartyData().getStepIndex(), 2, "the turn goes on at once");
        context.assertTrue(landed(landings, context, PATH.get(1), TileFeedback.Landing.DEFAULT), "a plain landing");

        // A second violet tile on the board: no warning for the violet ones, the arrival is reached through the network
        put(context, PATH.get(4), teleport(TeleportNetwork.VIOLET));
        BlockPos second = context.getAbsolutePos(PATH.get(4));
        context.assertTrue(!warned(context, alone) && !warned(context, second), "a pair: fine");
        context.assertTrue(warned(context, green), "the green one still alone");
        BoardGraph graph = BoardGraph.collect(context.getWorld(), box(context));
        context.assertEquals(graph.distance(second), graph.distance(alone), "reached at the first violet tile's distance");
        context.assertTrue(teleports.isEmpty(), "never teleported");
        finish(context);
    }

    private static boolean warned(TestContext context, BlockPos absolute) {
        BoardValidator.Report report = BoardValidator.check(BoardGraph.collect(context.getWorld(), box(context)));
        return report.issues().stream().anyMatch(issue -> issue.key().equals("teleport_alone") && issue.positions().contains(absolute));
    }

    /** « Avancer d'une case », the next space triggers its effect: warped, then one space on, landing on the bonus. */
    @GameTest(batchId = "tile_teleport", templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void pushedOneSpaceOnTheNextSpaceTriggersItsEffect(TestContext context) {
        pushed(context, true);
    }

    /** « Avancer d'une case », the next space does not trigger its effect: a plain landing on the bonus space. */
    @GameTest(batchId = "tile_teleport", templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void pushedOneSpaceOnTheNextSpaceDoesNotTriggerItsEffect(TestContext context) {
        pushed(context, false);
    }

    private static void pushed(TestContext context, boolean triggers) {
        TeleportSettingsComponent settings = TeleportSettingsComponent.DEFAULT.withPush(true).withPushTriggers(triggers);
        List<BoardSpaceBlockEntity> tiles = path(context, teleport(settings), plain(), teleport(TeleportNetwork.VIOLET), bonus(), plain());
        PigEntity pig = token(context, PATH.get(0));
        List<TileTeleport.Teleported> teleports = teleports(context, pig);
        List<TileFeedback.Played> landings = landings(context);
        PartyControllerEntity controller = party(context, pig);
        tiles.get(0).onDestinationReached(pig, controller);
        context.waitAndRun(WAIT, () -> {
            context.assertEquals(teleports.size(), 1, "warped first");
            context.assertEquals(controller.getPartyData().getStepIndex(), 1, "still its turn: pushed on");
            context.assertTrue(TileTeleport.isPushed(pig), "being pushed");
            when(context, turnEnded(controller), 200, "turn ends", () -> {
                assertOn(context, pig, context.getAbsolutePos(PATH.get(3)), "one space after the arrival tile");
                context.assertTrue(!TileTeleport.isPushed(pig), "the push is over");
                context.assertEquals(landed(landings, context, PATH.get(3), TileFeedback.Landing.GOOD), triggers, "the bonus plays: " + triggers);
                context.assertEquals(landed(landings, context, PATH.get(3), TileFeedback.Landing.DEFAULT), !triggers, "a plain landing: " + !triggers);
                context.assertEquals(teleports.size(), 1, "one teleport");
                finish(context);
            });
        });
    }

    /** Pushed onto a Teleport tile of the network: it stops there (no chain), a plain landing. */
    @GameTest(batchId = "tile_teleport", templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void pushedOntoATeleportTileIsNoChain(TestContext context) {
        // In turn: the first tile after it (PATH 2), then pushed onto PATH 3, violet too
        TeleportSettingsComponent settings = TeleportSettingsComponent.DEFAULT.withPush(true).withCycle(true);
        List<BoardSpaceBlockEntity> tiles = path(context, teleport(settings), plain(), teleport(TeleportNetwork.VIOLET),
                teleport(TeleportNetwork.VIOLET), plain());
        PigEntity pig = token(context, PATH.get(0));
        List<TileTeleport.Teleported> teleports = teleports(context, pig);
        List<TileFeedback.Played> landings = landings(context);
        PartyControllerEntity controller = party(context, pig);
        tiles.get(0).onDestinationReached(pig, controller);
        when(context, turnEnded(controller), 250, "turn ends", () -> {
            context.waitAndRun(WAIT, () -> {
                assertOn(context, pig, context.getAbsolutePos(PATH.get(3)), "pushed onto the next Teleport tile");
                context.assertEquals(teleports.size(), 1, "never teleported again");
                context.assertEquals(teleports.getFirst().to(), context.getAbsolutePos(PATH.get(2)), "in turn: the first one");
                context.assertTrue(!TileTeleport.isTeleporting(pig), "not warping");
                context.assertTrue(landed(landings, context, PATH.get(3), TileFeedback.Landing.DEFAULT), "a plain landing");
                context.assertTrue(!landed(landings, context, PATH.get(3), TileFeedback.Landing.TELEPORT), "not the teleport one");
                finish(context);
            });
        });
    }

    /** Pushed from an arrival tile at a fork: the owner chooses, like any move. */
    @GameTest(batchId = "tile_teleport", templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void pushedAtAForkTheOwnerChooses(TestContext context) {
        BlockPos a = new BlockPos(5, 1, 3), b = new BlockPos(5, 1, 5);
        tile(context, PATH.get(0), teleport(TeleportSettingsComponent.DEFAULT.withPush(true)), PATH.get(1));
        tile(context, PATH.get(1), plain(), PATH.get(2));
        tile(context, PATH.get(2), teleport(TeleportNetwork.VIOLET), a, b);
        tile(context, a, plain());
        tile(context, b, plain());
        PigEntity pig = token(context, PATH.get(0));
        PartyControllerEntity controller = party(context, pig);
        BoardSpaceBlockEntity from = context.getBlockEntity(PATH.get(0));
        from.onDestinationReached(pig, controller);
        Box around = new Box(context.getAbsolutePos(PATH.get(2))).expand(4);
        when(context, () -> !context.getWorld().getEntitiesByClass(DirectionDisplayEntity.class, around, e -> true).isEmpty(),
                150, "the ways are shown", () -> {
                    assertOn(context, pig, context.getAbsolutePos(PATH.get(2)), "waits on the arrival tile");
                    context.assertEquals(controller.getPartyData().getStepIndex(), 1, "still its turn");
                    TokenMovementService.moveEntityOnTileToDestination(context.getWorld(), context.getAbsolutePos(PATH.get(2)),
                            new BoardSpaceDestination(context.getAbsolutePos(b), true), pig.getUuid());
                    when(context, turnEnded(controller), 150, "turn ends", () -> {
                        assertOn(context, pig, context.getAbsolutePos(b), "the chosen way");
                        finish(context);
                    });
                });
    }

    /**
     * The settings: saved and sent as they are (a cartridge saved with the former arrival lists loads in the violet
     * network), changed from the menu only by a player allowed to edit the tile (not a spectator nor in adventure, in
     * reach), the tile taking its network's colour; a network's dye switches the tile.
     */
    @GameTest(batchId = "tile_teleport", templateName = EMPTY_STRUCTURE)
    public void settingsRoundTripAndPermissions(TestContext context) throws Exception {
        TeleportSettingsComponent settings = new TeleportSettingsComponent(TeleportNetwork.ORANGE, true, true, false);
        NbtElement nbt = TeleportSettingsComponent.CODEC.encodeStart(NbtOps.INSTANCE, settings).getOrThrow();
        context.assertEquals(TeleportSettingsComponent.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow(), settings, "saved and loaded");
        ByteBuf buf = Unpooled.buffer();
        TeleportSettingsComponent.PACKET_CODEC.encode(buf, settings);
        context.assertEquals(TeleportSettingsComponent.PACKET_CODEC.decode(buf), settings, "sent");
        NbtCompound former = StringNbtReader.parse("{targets:[[I;1,2,3]],cycle:1b,land_on_target:1b}");
        context.assertEquals(TeleportSettingsComponent.CODEC.parse(NbtOps.INSTANCE, former).getOrThrow(),
                new TeleportSettingsComponent(TeleportNetwork.VIOLET, true, false, true), "a former cartridge loads");

        BoardSpaceBlockEntity tile = tile(context, new BlockPos(2, 1, 2), teleport(TeleportNetwork.VIOLET));
        BlockPos pos = tile.getPos();
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            Vec3d near = pos.toCenterPos().add(1.5, 0.5, 0);
            player.refreshPositionAndAngles(near.x, near.y, near.z, 0, 0);
            context.assertTrue(menu(player, pos, settings), "allowed");
            context.assertEquals(TileTeleport.settings(tile.getActiveCartridgeItemStack()), settings, "written in the tile's cartridge");
            context.assertEquals(tile.getActiveCartridgeItemStack().get(ModComponents.COLOR), TeleportNetwork.ORANGE.color(), "orange tile now");

            TeleportSettingsComponent other = settings.withNetwork(TeleportNetwork.BLUE);
            player.changeGameMode(GameMode.ADVENTURE);
            context.assertTrue(!menu(player, pos, other), "adventure: refused");
            player.changeGameMode(GameMode.SPECTATOR);
            context.assertTrue(!menu(player, pos, other), "spectator: refused");
            player.changeGameMode(GameMode.SURVIVAL);
            Vec3d far = pos.toCenterPos().add(20, 0.5, 0);
            player.refreshPositionAndAngles(far.x, far.y, far.z, 0, 0);
            context.assertTrue(!menu(player, pos, other), "out of reach: refused");
            context.assertEquals(TileTeleport.settings(tile.getActiveCartridgeItemStack()), settings, "unchanged");

            // The cartridge in hand
            ItemStack inHand = new ItemStack(ModItems.TELEPORT_CARTRIDGE);
            player.setStackInHand(Hand.MAIN_HAND, inHand);
            context.assertTrue(menu(player, null, other), "in hand");
            context.assertEquals(TeleportCartridgeItem.settings(player.getMainHandStack()), other, "written in the held cartridge");
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            context.assertTrue(!menu(player, null, other), "nothing in hand");

            // A lime dye on the tile: the green network
            player.refreshPositionAndAngles(near.x, near.y, near.z, 0, 0);
            ItemStack dye = new ItemStack(Items.LIME_DYE);
            tile.getBoardSpaceBehavior().onUseWithItem(dye, tile.getCachedState(), context.getWorld(), pos, player,
                    new BlockHitResult(pos.toCenterPos(), Direction.UP, pos, false));
            context.assertEquals(TileTeleport.settings(tile.getActiveCartridgeItemStack()).network(), TeleportNetwork.GREEN, "dyed green");
            context.assertEquals(tile.getActiveCartridgeItemStack().get(ModComponents.COLOR), TeleportNetwork.GREEN.color(), "green tile");
            context.assertEquals(dye.getCount(), 1, "the dye is not used up");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    /** Free play: the arrival is where tokens stand on it: lowered on a slab, sloped on stairs, the middle of a large tile. */
    @GameTest(batchId = "tile_teleport", templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void arrivalsOnLoweredSlopedAndLargeTiles(TestContext context) {
        BlockPos lowered = new BlockPos(1, 2, 5), sloped = new BlockPos(3, 2, 5), large = new BlockPos(5, 2, 5);
        context.setBlockState(lowered.down(2), Blocks.STONE);
        context.setBlockState(lowered.down(), Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
        context.setBlockState(lowered, ModBlocks.TILE);
        context.setBlockState(sloped.down(), Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH));
        context.setBlockState(sloped, ModBlocks.TILE);
        for (int x = 5; x <= 7; x++) for (int z = 5; z <= 7; z++) context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
        PlayerEntity player = context.createMockPlayer(GameMode.CREATIVE);
        ItemStack item = TileSize.with(new ItemStack(ModBlocks.TILE), TileSize.LARGE);
        player.setStackInHand(Hand.MAIN_HAND, item);
        context.useStackOnBlock(player, item, large.down(), Direction.UP);
        context.assertTrue(context.getBlockState(lowered).get(ATileBlock.SUPPORT) == TileSupport.DROP_8, "lowered tile");
        context.assertTrue(context.getBlockState(sloped).get(ATileBlock.SUPPORT).isSloped(), "sloped tile");
        context.assertTrue(context.getBlockState(large).get(ATileBlock.SIZE).size() == TileSize.LARGE, "large tile");

        // Three boards of two tiles each (a path joins them), both in the network
        List<BlockPos> arrivals = List.of(lowered, sloped, large);
        List<PigEntity> pigs = new ArrayList<>();
        for (int i = 0; i < arrivals.size(); i++) {
            BlockPos from = new BlockPos(1 + 2 * i, 1, 1);
            BoardSpaceBlockEntity teleport = tile(context, from, teleport(TeleportNetwork.BLUE), arrivals.get(i));
            put(context, arrivals.get(i), teleport(TeleportNetwork.BLUE));
            PigEntity pig = token(context, from);
            pigs.add(pig);
            // Free play: a token ending its move there (no party)
            TileReachedEvent.EVENT.invoker().onTileReached(pig, teleport);
            context.assertTrue(TileTeleport.isTeleporting(pig), "free play teleports too (" + i + ")");
        }
        context.waitAndRun(WAIT, () -> {
            for (int i = 0; i < arrivals.size(); i++) {
                assertStandsOn(context, pigs.get(i), context.getAbsolutePos(arrivals.get(i)), List.of("lowered", "sloped", "large").get(i));
                context.assertTrue(!TileTeleport.isTeleporting(pigs.get(i)), "not sent back (" + i + ")");
            }
            finish(context);
        });
    }

    /** Going over a Teleport tile (steps left) does nothing. */
    @GameTest(batchId = "tile_teleport", templateName = EMPTY_STRUCTURE)
    public void goingOverATeleportTileDoesNothing(TestContext context) {
        List<BoardSpaceBlockEntity> tiles = path(context, teleport(TeleportNetwork.VIOLET), teleport(TeleportNetwork.VIOLET));
        PigEntity pig = token(context, PATH.get(0));
        ((TokenizedEntityInterface) pig).steveparty$setNbSteps(2);
        TileReachedEvent.EVENT.invoker().onTileReached(pig, tiles.get(0));
        context.assertTrue(!TileTeleport.isTeleporting(pig), "passing: no teleport");
        ((TokenizedEntityInterface) pig).steveparty$setNbSteps(0);
        pig.discard();
        finish(context);
    }
}
