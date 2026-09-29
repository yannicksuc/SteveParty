package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSupport;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileTeleport;
import fr.lordfinn.steveparty.board.BoardGraph;
import fr.lordfinn.steveparty.board.BoardValidator;
import fr.lordfinn.steveparty.board.TeleportLinks;
import fr.lordfinn.steveparty.board.WrenchActions;
import fr.lordfinn.steveparty.board.WrenchMode;
import fr.lordfinn.steveparty.board.WrenchState;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TeleportTargetsComponent;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.events.TileReachedEvent;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.payloads.custom.WrenchActionPayload;
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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock.TILE_TYPE;

/**
 * The Teleport tile: a token landing on it is warped to one of its arrivals (random or in turn), its move ends there
 * without landing (no chains), the board check warns about a teleport tile sending nowhere, the Wrench's Teleport mode
 * links arrivals apart from the paths, and the arrival is where tokens stand on lowered, sloped and large tiles.
 */
public class TileTeleportGameTests implements FabricGameTest {
    private static final int WAIT = TileTeleport.TOTAL_TICKS + 6;

    /** A tile on a stone floor (relative position), its block entity. */
    private static BoardSpaceBlockEntity tile(TestContext context, BlockPos pos) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.TILE);
        return context.getBlockEntity(pos);
    }

    /** A Teleport tile sending to {@code targets} (relative positions). */
    private static BoardSpaceBlockEntity teleportTile(TestContext context, BlockPos pos, boolean cycle, boolean landOnTarget, BlockPos... targets) {
        BoardSpaceBlockEntity tile = tile(context, pos);
        ItemStack cartridge = new ItemStack(ModItems.TELEPORT_CARTRIDGE);
        List<BlockPos> absolute = new ArrayList<>();
        for (BlockPos target : targets) absolute.add(context.getAbsolutePos(target));
        cartridge.set(ModComponents.TELEPORT_TARGETS, new TeleportTargetsComponent(absolute, cycle, landOnTarget));
        tile.setStack(0, cartridge);
        return tile;
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

    private static final java.util.Map<TestContext, List<Runnable>> CLEANUPS = new java.util.WeakHashMap<>();

    /** Runs {@code cleanup} when the test succeeds ({@link #finish}; a final task would end these waiting tests). */
    private static void onEnd(TestContext context, Runnable cleanup) {
        CLEANUPS.computeIfAbsent(context, c -> new ArrayList<>()).add(cleanup);
    }

    private static void finish(TestContext context) {
        List<Runnable> cleanups = CLEANUPS.remove(context);
        if (cleanups != null) cleanups.forEach(Runnable::run);
        context.complete();
    }

    /** Records the teleports of this test. */
    private static List<TileTeleport.Teleported> teleports(TestContext context) {
        List<TileTeleport.Teleported> list = new ArrayList<>();
        Consumer<TileTeleport.Teleported> listener = list::add;
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

    private static BlockBox box(TestContext context) {
        BlockPos min = context.getAbsolutePos(BlockPos.ORIGIN), max = context.getAbsolutePos(new BlockPos(8, 4, 8));
        return BlockBox.create(min, max);
    }

    private static PartyControllerEntity party(TestContext context, MobEntity token) {
        BlockPos pos = new BlockPos(6, 1, 6);
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(pos);
        PartyData data = new PartyData();
        data.addToken(token.getUuid());
        data.addStep(new PartyStep());
        data.addStep(new TokenTurnPartyStep(token.getUuid(), null));
        data.addStep(new TokenTurnPartyStep(token.getUuid(), null));
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(token.getUuid()))));
        controller.setPartyData(data);
        controller.nextStep();
        controller.nextStep();
        onEnd(context, () -> context.removeBlock(pos));
        return controller;
    }

    private static void assertStandsOn(TestContext context, MobEntity token, BlockPos absolute, String what) {
        Vec3d stand = BoardSpaces.standPos(context.getWorld(), absolute);
        context.assertTrue(token.getPos().distanceTo(stand) < 0.05, what + ": stands on the arrival " + stand + ", is at " + token.getPos());
        BoardSpaceBlockEntity on = BoardSpaces.boardSpaceOf(token);
        context.assertTrue(on != null && on.getPos().equals(absolute), what + ": on the arrival's board space, found " + (on == null ? null : on.getPos()));
    }

    /** In a party: warped to the arrival, the turn goes on once it has reappeared, and it doesn't land there. */
    @GameTest(batchId = "tile_teleport", templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void aTokenLandingIsTeleportedAndTheTurnGoesOnAfterwards(TestContext context) {
        BlockPos from = new BlockPos(1, 1, 1), to = new BlockPos(5, 1, 2);
        List<TileTeleport.Teleported> teleports = teleports(context);
        List<TileFeedback.Played> landings = landings(context);
        BoardSpaceBlockEntity arrival = tile(context, to);
        // A bonus space as arrival: its role must not play (the move just ends there)
        arrival.setStack(0, new ItemStack(ModItems.INVENTORY_CARTRIDGE));
        arrival.getStack(0).set(ModComponents.INVENTORY_COMPONENT, new InventoryComponent(List.of(new ItemStack(Items.DIAMOND))));
        BoardSpaceBlockEntity teleport = teleportTile(context, from, false, false, to);
        context.expectBlockProperty(from, TILE_TYPE, fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType.TILE_TELEPORT);
        context.assertEquals(TileFeedback.landingOf(teleport), TileFeedback.Landing.TELEPORT, "teleport landing");
        context.assertEquals(teleport.getBoardSpaceBehavior().comparatorLevel(teleport, teleport.getActiveCartridgeItemStack()),
                fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity.LEVEL_TELEPORT, "a Router's comparator reads 8");
        context.assertEquals(teleport.getActiveCartridgeItemStack().get(ModComponents.COLOR), TileTeleport.COLOR, "purple tile");
        PigEntity pig = token(context, from);
        PartyControllerEntity controller = party(context, pig);
        PartyData data = controller.getPartyData();
        context.assertEquals(data.getStepIndex(), 1, "turn of the token");

        teleport.onDestinationReached(pig, controller);
        context.assertEquals(data.getStepIndex(), 1, "the turn waits for the warp");
        context.assertTrue(TileTeleport.isTeleporting(pig), "warping");
        context.assertTrue(!TokenStatus.hasStatus(((TokenizedEntityInterface) pig).steveparty$getStatus(), TokenStatus.CAN_MOVE),
                "no second move during the warp");
        context.assertEquals(landings.size(), 1, "one landing: the teleport tile's");
        context.assertEquals(landings.getFirst().landing(), TileFeedback.Landing.TELEPORT, "with the teleport jingle");
        context.waitAndRun(8, () -> context.assertTrue(pig.getAttributeValue(EntityAttributes.SCALE) < 0.9 * scaleOf(pig), "shrinking"));
        context.waitAndRun(WAIT, () -> {
            BlockPos target = context.getAbsolutePos(to);
            assertStandsOn(context, pig, target, "party");
            context.assertTrue(!TileTeleport.isTeleporting(pig), "done");
            context.assertTrue(pig.getAttributeInstance(EntityAttributes.SCALE).getModifier(fr.lordfinn.steveparty.Steveparty.id("teleport_shrink")) == null,
                    "back to its size");
            List<TileTeleport.Teleported> own = teleports.stream().filter(t -> t.token().equals(pig.getUuid())).toList();
            context.assertEquals(own.size(), 1, "one teleport");
            context.assertEquals(own.getFirst().to(), target, "to the arrival");
            context.assertEquals(data.getStepIndex(), 2, "the next turn");
            context.assertEquals(landings.size(), 1, "no landing on the arrival");
            finish(context);
        });
    }

    private static float scaleOf(MobEntity mob) {
        return (float) mob.getAttributeBaseValue(EntityAttributes.SCALE);
    }

    /** Several arrivals: random among them (each one comes up), or each in turn; never an arrival that is no space. */
    @GameTest(batchId = "tile_teleport", templateName = EMPTY_STRUCTURE)
    public void severalArrivalsRandomOrInTurn(TestContext context) {
        BlockPos a = new BlockPos(4, 1, 1), b = new BlockPos(4, 1, 3), c = new BlockPos(4, 1, 5), stone = new BlockPos(6, 1, 1);
        tile(context, a);
        tile(context, b);
        tile(context, c);
        context.setBlockState(stone, Blocks.STONE);
        BoardSpaceBlockEntity random = teleportTile(context, new BlockPos(1, 1, 1), false, false, a, b, c, stone);
        Set<BlockPos> picked = new HashSet<>();
        for (int i = 0; i < 80; i++) picked.add(TileTeleport.pick(context.getWorld(), random, random.getActiveCartridgeItemStack()));
        context.assertEquals(picked, Set.of(context.getAbsolutePos(a), context.getAbsolutePos(b), context.getAbsolutePos(c)),
                "each arrival at random, never the stone block");

        BoardSpaceBlockEntity inTurn = teleportTile(context, new BlockPos(1, 1, 4), true, false, a, b, c);
        List<BlockPos> order = new ArrayList<>();
        for (int i = 0; i < 4; i++) order.add(TileTeleport.pick(context.getWorld(), inTurn, inTurn.getActiveCartridgeItemStack()));
        context.assertEquals(order, List.of(context.getAbsolutePos(a), context.getAbsolutePos(b), context.getAbsolutePos(c), context.getAbsolutePos(a)),
                "in turn, then again");
        finish(context);
    }

    /**
     * No chains: an arrival that is itself a Teleport tile keeps the token, even with the "lands there" option; with
     * that option, an ordinary arrival's role plays.
     */
    @GameTest(batchId = "tile_teleport", templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void noTeleportChainButTheOptionLandsOnTheArrival(TestContext context) {
        BlockPos first = new BlockPos(1, 1, 1), second = new BlockPos(4, 1, 1), third = new BlockPos(7, 1, 1);
        BlockPos bonus = new BlockPos(1, 1, 5), other = new BlockPos(4, 1, 5);
        List<TileTeleport.Teleported> teleports = teleports(context);
        List<TileFeedback.Played> landings = landings(context);
        tile(context, third);
        teleportTile(context, second, false, true, third);
        BoardSpaceBlockEntity start = teleportTile(context, first, false, true, second);
        PigEntity pig = token(context, first);
        PartyControllerEntity controller = party(context, pig);
        start.onDestinationReached(pig, controller);

        BoardSpaceBlockEntity bonusTile = tile(context, bonus);
        bonusTile.setStack(0, new ItemStack(ModItems.INVENTORY_CARTRIDGE));
        bonusTile.getStack(0).set(ModComponents.INVENTORY_COMPONENT, new InventoryComponent(List.of(new ItemStack(Items.DIAMOND))));
        BoardSpaceBlockEntity toBonus = teleportTile(context, other, false, true, bonus);
        PigEntity other2 = token(context, other);

        context.waitAndRun(WAIT, () -> {
            assertStandsOn(context, pig, context.getAbsolutePos(second), "chain");
            context.assertEquals(teleports.stream().filter(t -> t.token().equals(pig.getUuid())).count(), 1L, "one teleport only, no chain");
            context.assertTrue(!TileTeleport.isTeleporting(pig), "not sent on");
            context.assertEquals(controller.getPartyData().getStepIndex(), 2, "the turn went on");
            // A token arriving on a teleport tile by any way (free play) isn't sent on either while it stays
            context.assertEquals(landings.stream().filter(l -> l.tile().equals(context.getAbsolutePos(second))).count(), 0L,
                    "a teleport arrival doesn't land");

            toBonus.onDestinationReached(other2, controller);
            context.waitAndRun(WAIT, () -> {
                assertStandsOn(context, other2, context.getAbsolutePos(bonus), "option");
                context.assertTrue(landings.stream().anyMatch(l -> l.tile().equals(context.getAbsolutePos(bonus))
                        && l.landing() == TileFeedback.Landing.GOOD), "the option lands on the bonus arrival");
                finish(context);
            });
        });
    }

    /** The board check warns about a Teleport tile without arrival (or whose arrivals are no spaces). */
    @GameTest(batchId = "tile_teleport", templateName = EMPTY_STRUCTURE)
    public void aTeleportTileWithoutArrivalIsWarned(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1), arrival = new BlockPos(4, 1, 1), stone = new BlockPos(6, 1, 1);
        BoardSpaceBlockEntity teleport = teleportTile(context, pos, false, false);
        tile(context, arrival);
        context.setBlockState(stone, Blocks.STONE);
        BlockPos absolute = context.getAbsolutePos(pos);
        context.assertTrue(warned(context, absolute), "no arrival: warned");
        teleport.getActiveCartridgeItemStack().set(ModComponents.TELEPORT_TARGETS,
                new TeleportTargetsComponent(List.of(context.getAbsolutePos(stone)), false, false));
        context.assertTrue(warned(context, absolute), "an arrival that is no space: warned");
        teleport.getActiveCartridgeItemStack().set(ModComponents.TELEPORT_TARGETS,
                new TeleportTargetsComponent(List.of(context.getAbsolutePos(arrival)), false, false));
        context.assertTrue(!warned(context, absolute), "a real arrival: fine");
        // A start leading to the teleport tile: its arrival is reached too (not unreachable), with no path link to it
        BoardSpaceBlockEntity start = tile(context, new BlockPos(1, 1, 4));
        ItemStack startCartridge = new ItemStack(ModItems.TILE_BEHAVIOR_START);
        startCartridge.set(ModComponents.DESTINATIONS_COMPONENT, new fr.lordfinn.steveparty.components.DestinationsComponent(
                new ArrayList<>(List.of(absolute)), ""));
        start.setStack(0, startCartridge);
        BoardGraph graph = BoardGraph.collect(context.getWorld(), box(context));
        BoardGraph.Node reached = graph.node(context.getAbsolutePos(arrival));
        context.assertTrue(reached != null && !graph.isUnreachable(reached), "the arrival is reached through the teleport");
        context.assertEquals(graph.distance(context.getAbsolutePos(arrival)), graph.distance(absolute), "at the teleport tile's distance");
        context.assertTrue(graph.nodes().stream().noneMatch(node -> node.edges().stream().anyMatch(e -> e.to().equals(context.getAbsolutePos(arrival)))),
                "arrivals are no path links");
        finish(context);
    }

    private static boolean warned(TestContext context, BlockPos absolute) {
        BoardValidator.Report report = BoardValidator.check(BoardGraph.collect(context.getWorld(), box(context)));
        return report.issues().stream().anyMatch(issue -> issue.key().equals("teleport_no_target") && issue.positions().contains(absolute))
                && !report.ok();
    }

    /** The arrival is where tokens stand on it: lowered on a slab, sloped on stairs, the middle of a large tile. */
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

        List<BlockPos> arrivals = List.of(lowered, sloped, large);
        List<PigEntity> pigs = new ArrayList<>();
        for (int i = 0; i < arrivals.size(); i++) {
            BlockPos from = new BlockPos(1 + 2 * i, 1, 1);
            BoardSpaceBlockEntity teleport = teleportTile(context, from, false, false, arrivals.get(i));
            PigEntity pig = token(context, from);
            pigs.add(pig);
            // Free play: a token ending its move there (no party)
            TileReachedEvent.EVENT.invoker().onTileReached(pig, teleport);
            context.assertTrue(TileTeleport.isTeleporting(pig), "free play teleports too (" + i + ")");
        }
        context.waitAndRun(WAIT, () -> {
            for (int i = 0; i < arrivals.size(); i++) {
                assertStandsOn(context, pigs.get(i), context.getAbsolutePos(arrivals.get(i)), List.of("lowered", "sloped", "large").get(i));
            }
            finish(context);
        });
    }

    /** Going over a Teleport tile (steps left) does nothing. */
    @GameTest(batchId = "tile_teleport", templateName = EMPTY_STRUCTURE)
    public void goingOverATeleportTileDoesNothing(TestContext context) {
        BlockPos from = new BlockPos(1, 1, 1), to = new BlockPos(4, 1, 1);
        tile(context, to);
        BoardSpaceBlockEntity teleport = teleportTile(context, from, false, false, to);
        PigEntity pig = token(context, from);
        ((TokenizedEntityInterface) pig).steveparty$setNbSteps(2);
        TileReachedEvent.EVENT.invoker().onTileReached(pig, teleport);
        context.assertTrue(!TileTeleport.isTeleporting(pig), "passing: no teleport");
        ((TokenizedEntityInterface) pig).steveparty$setNbSteps(0);
        finish(context);
    }

    /**
     * The Wrench's Teleport mode: click the Teleport tile, then its arrivals (again: removed); the path links stay
     * untouched; undo restores. A tile without cartridge becomes a Teleport tile in creative.
     */
    @GameTest(batchId = "tile_teleport", templateName = EMPTY_STRUCTURE)
    public void theWrenchLinksArrivalsApartFromThePaths(TestContext context) {
        List<BlockPos> t = BoardLinkingGameTests.tiles(context, ModBlocks.TILE,
                new BlockPos(1, 1, 1), new BlockPos(4, 1, 1), new BlockPos(4, 1, 4), new BlockPos(1, 1, 4));
        BlockPos teleport = t.get(0);
        BoardLinkingGameTests.withPlayer(context, true, player -> {
            ItemStack wrench = BoardLinkingGameTests.wrench(player);
            // A path first: 0 → 1
            BoardLinkingGameTests.click(player, wrench, context, t.get(0));
            BoardLinkingGameTests.click(player, wrench, context, t.get(1));
            WrenchActions.endChain(player, wrench, context.getWorld(), false);
            for (int i = 0; i < 3; i++) WrenchActions.control(player, wrench, WrenchActionPayload.Action.MODE, 1);
            context.assertEquals(WrenchState.of(wrench).mode(), WrenchMode.TELEPORT, "Trace → Edit → Cut → Teleport");

            // Tile 3 has no cartridge: in creative it becomes a Teleport tile... but tile 0 holds a plain cartridge
            BoardLinkingGameTests.click(player, wrench, context, teleport);
            context.assertTrue(WrenchActions.origin(wrench, context.getWorld()) == null, "a plain tile is no Teleport tile");
            BoardLinkingGameTests.click(player, wrench, context, teleport.down()); // not the same space twice in a row (held button)
            player.setStackInHand(Hand.OFF_HAND, new ItemStack(ModItems.TELEPORT_CARTRIDGE));
            BoardLinkingGameTests.click(player, wrench, context, teleport);
            player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            context.assertEquals(WrenchActions.origin(wrench, context.getWorld()), teleport, "picked, its cartridge swapped");
            BoardSpaceBlockEntity tile = BoardLinkingGameTests.boardSpace(context, teleport);
            context.assertTrue(tile.getStack(0).isOf(ModItems.TELEPORT_CARTRIDGE), "a Teleport Cartridge now");
            context.assertEquals(BoardLinkingGameTests.links(context, teleport), List.of(t.get(1)), "its path kept");

            BoardLinkingGameTests.click(player, wrench, context, t.get(2));
            BoardLinkingGameTests.click(player, wrench, context, t.get(3));
            context.assertEquals(TeleportLinks.targets(tile, 0), List.of(t.get(2), t.get(3)), "two arrivals");
            context.assertEquals(BoardLinkingGameTests.links(context, teleport), List.of(t.get(1)), "paths untouched");
            BoardLinkingGameTests.click(player, wrench, context, t.get(2));
            context.assertEquals(TeleportLinks.targets(tile, 0), List.of(t.get(3)), "clicked again: removed");
            WrenchActions.control(player, wrench, WrenchActionPayload.Action.UNDO, 1);
            context.assertEquals(TeleportLinks.targets(tile, 0), List.of(t.get(2), t.get(3)), "undo");
            context.assertTrue(BoardLinkingGameTests.links(context, t.get(2)).isEmpty(), "an arrival gets no link");
            BoardLinkingGameTests.click(player, wrench, context, teleport);
            context.assertTrue(WrenchActions.origin(wrench, context.getWorld()) == null, "the Teleport tile again: unbound");

            // An empty tile in creative becomes a Teleport tile
            BlockPos empty = t.get(3);
            BoardLinkingGameTests.boardSpace(context, empty).setStack(0, ItemStack.EMPTY);
            BoardLinkingGameTests.click(player, wrench, context, empty);
            context.assertTrue(BoardLinkingGameTests.boardSpace(context, empty).getStack(0).isOf(ModItems.TELEPORT_CARTRIDGE), "creative supplies one");
        });
    }
}
