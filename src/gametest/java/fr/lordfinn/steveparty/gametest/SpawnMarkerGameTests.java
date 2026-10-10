package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.CartridgeTransfers;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyResources;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlock;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerSettings;
import fr.lordfinn.steveparty.service.BoardMobSpots;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.board.BrushLinks;
import fr.lordfinn.steveparty.board.LinkHistory;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeSpawnMarker;
import fr.lordfinn.steveparty.items.custom.cartridges.TrichaudronCartridgeItem;
import fr.lordfinn.steveparty.service.BoardActors;
import fr.lordfinn.steveparty.service.FrousseuxThefts;
import fr.lordfinn.steveparty.service.MarkerResidents;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.dice.DicePrompts;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.gametest.BoardLinkingGameTests.brush;
import static fr.lordfinn.steveparty.gametest.BoardLinkingGameTests.paint;
import static fr.lordfinn.steveparty.gametest.BoardLinkingGameTests.withPlayer;
import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestCleanup.atEnd;
import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * One brush for every position a cartridge stores (the target decides: a board space is a destination, a container a
 * container, a Spawn Marker the mob's spawn point; what the cartridge takes none of is refused), the Spawn Marker
 * (where the mob of a mob space appears, facing its way; beside the space without one; broken, forgotten; « always
 * visible »: its mob lives on it all the party long), and the Party Controller's bank for the spaces with no chest.
 */
public class SpawnMarkerGameTests implements SteveGameTest {
    private static final BlockPos TILE = new BlockPos(3, 1, 3);
    private static final BlockPos MARKER = new BlockPos(6, 1, 5);
    private static final int WHOLE_THEFT = 500;

    /** A Spawn Marker at {@code relative} on stone, facing {@code facing}; its absolute position. */
    private static BlockPos marker(TestContext context, BlockPos relative, Direction facing) {
        context.setBlockState(relative.down(), Blocks.STONE);
        context.setBlockState(relative, ModBlocks.SPAWN_MARKER.getDefaultState().with(SpawnMarkerBlock.FACING, facing));
        return context.getAbsolutePos(relative);
    }

    /** {@code cartridge} of the space at {@code tile} linked to the marker at {@code marker} (absolute). */
    private static void link(TestContext context, BoardSpaceBlockEntity tile, BlockPos marker) {
        CartridgeSpawnMarker.set(tile.getStack(0), context.getWorld(), marker);
        CartridgeSpawnMarker.own(context.getWorld(), marker, tile.getPos());
    }

    /** No running party within reach of {@code pos} (tests of other batches may be playing one nearby). */
    private static boolean noPartyAround(TestContext context, BlockPos pos) {
        return PartyControllerEntity.getPartyOfBoardSpace(context.getWorld(), pos,
                PartyControllerEntity.BOARD_NEARBY_RADIUS, false).isEmpty();
    }

    private static boolean near(Vec3d a, Vec3d b) {
        return Math.abs(a.x - b.x) < 0.05 && Math.abs(a.z - b.z) < 0.05;
    }

    // ---------------------------------------------------------------- the brush: the target decides

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theTargetDecidesWhatIsLinked(TestContext context) {
        BoardSpaceBlockEntity mob = tile(context, TILE, new ItemStack(ModItems.FROUSSEUX_CARTRIDGE));
        BoardSpaceBlockEntity inventory = tile(context, new BlockPos(3, 1, 7), new ItemStack(ModItems.INVENTORY_CARTRIDGE));
        BoardSpaceBlockEntity next = tile(context, new BlockPos(1, 1, 3), plain());
        BlockPos marker = marker(context, MARKER, Direction.EAST);
        context.setBlockState(new BlockPos(1, 1, 7), Blocks.CHEST);
        BlockPos chest = context.getAbsolutePos(new BlockPos(1, 1, 7));
        withPlayer(context, true, player -> {
            ItemStack brush = brush(player);
            // a mob space: a Spawn Marker is its spawn point, a chest is refused, a space is a destination
            paint(player, brush, context, mob.getPos(), marker);
            context.assertEquals(CartridgeSpawnMarker.linked(mob.getStack(0), context.getWorld()), marker, "the marker is its spawn point");
            context.assertTrue(context.getWorld().getBlockEntity(marker) instanceof SpawnMarkerBlockEntity entity
                    && mob.getPos().equals(entity.getOwner()), "the marker knows its space");
            context.assertTrue(BrushLinks.aims(context.getWorld(), mob.getPos(), TileLinkerBrush.POWERED, chest), "the brush aims at the chest...");
            context.assertEquals(BrushLinks.refusal(context.getWorld(), mob.getPos(), TileLinkerBrush.POWERED, chest),
                    "message.steveparty.tile_linker_brush.no_container", "...to say it is refused");
            paint(player, brush, context, mob.getPos(), chest);
            context.assertTrue(CartridgeContainers.isEmpty(mob.getStack(0)), "a Frousseux Cartridge takes no chest");
            paint(player, brush, context, mob.getPos(), next.getPos());
            context.assertEquals(BoardLinks.links(mob.getStack(0)), List.of(next.getPos()), "a space: a destination");
            // a container cartridge: the chest is its container, the marker is refused
            paint(player, brush, context, inventory.getPos(), chest);
            context.assertEquals(CartridgeContainers.in(inventory.getStack(0), context.getWorld()), List.of(chest), "the chest is its container");
            context.assertEquals(BrushLinks.refusal(context.getWorld(), inventory.getPos(), TileLinkerBrush.POWERED, marker),
                    "message.steveparty.tile_linker_brush.no_spawn", "a marker: refused");
            paint(player, brush, context, inventory.getPos(), marker);
            context.assertTrue(!inventory.getStack(0).contains(ModComponents.SPAWN_MARKER), "an Inventory Cartridge summons no mob");
            // undo, redo, painted again: erased
            context.assertTrue(LinkHistory.undo(player, true, brush), "undo");
            context.assertTrue(LinkHistory.undo(player, true, brush), "undo");
            context.assertTrue(LinkHistory.undo(player, true, brush), "undo");
            context.assertTrue(CartridgeSpawnMarker.linked(mob.getStack(0), context.getWorld()) == null, "the marker link undone");
            context.assertTrue(LinkHistory.undo(player, false, brush), "redo");
            context.assertEquals(CartridgeSpawnMarker.linked(mob.getStack(0), context.getWorld()), marker, "redone");
            paint(player, brush, context, mob.getPos(), marker);
            context.assertTrue(CartridgeSpawnMarker.linked(mob.getStack(0), context.getWorld()) == null, "painted again: erased");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aBrokenMarkerIsForgotten(TestContext context) {
        BoardSpaceBlockEntity mob = tile(context, TILE, new ItemStack(ModItems.MISTIGRI_CARTRIDGE));
        BlockPos marker = marker(context, MARKER, Direction.NORTH);
        link(context, mob, marker);
        context.assertEquals(CartridgeSpawnMarker.marker(mob.getStack(0), context.getWorld()), marker, "linked");
        context.setBlockState(MARKER, Blocks.AIR);
        context.assertTrue(!mob.getStack(0).contains(ModComponents.SPAWN_MARKER), "broken: the space forgot it");
        // a link left behind (an old copy): no marker there, nothing used
        CartridgeSpawnMarker.set(mob.getStack(0), context.getWorld(), marker);
        context.assertTrue(CartridgeSpawnMarker.marker(mob.getStack(0), context.getWorld()) == null, "no marker there: none");
        context.complete();
    }

    // ---------------------------------------------------------------- where the mob appears

    private static FrousseuxEntity theft(TestContext context, PartyControllerEntity party, MobEntity token, ServerPlayerEntity sender,
                                         BoardSpaceBlockEntity tile, boolean[] done) {
        FrousseuxThefts.Start start = FrousseuxThefts.start(context.getWorld(), tile.getPos(), token, party, false, 1, () -> done[0] = true);
        context.assertTrue(start == FrousseuxThefts.Start.STARTED, "started");
        DicePrompts.Prompt prompt = DicePrompts.pending(sender);
        if (prompt != null) DicePrompts.answer(sender, prompt.id(), 0);
        return FrousseuxThefts.actor(token);
    }

    private static PartyControllerEntity party(TestContext context, List<MobEntity> tokens, ServerPlayerEntity... players) {
        for (int i = 0; i < players.length; i++) tokens.add(token(context, new BlockPos(1 + 2 * i, 1, 1), players[i].getUuid()));
        PartyControllerEntity controller = DiceTestKit.party(context, players[0].getUuid(), tokens.toArray(MobEntity[]::new));
        controller.setCurrency(PartyCurrency.COIN, new ItemStack(Items.GOLD_INGOT));
        return controller;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE_THEFT + 40, batchId = "spawn_marker_appear")
    public void theMobAppearsOnItsMarkerFacingItsWay(TestContext context) {
        ServerPlayerEntity thief = player(context), victim = player(context);
        InventoryUtils.giveOrDrop(victim, new ItemStack(Items.GOLD_INGOT), 5);
        List<MobEntity> tokens = new ArrayList<>();
        PartyControllerEntity party = party(context, tokens, thief, victim);
        BoardSpaceBlockEntity tile = tile(context, TILE, new ItemStack(ModItems.FROUSSEUX_CARTRIDGE));
        BlockPos marker = marker(context, MARKER, Direction.WEST);
        link(context, tile, marker);
        boolean[] done = {false};
        FrousseuxEntity actor = theft(context, party, tokens.getFirst(), thief, tile, done);
        context.assertTrue(actor != null && near(actor.getPos(), SpawnMarkerBlock.standPos(marker)), "on its marker");
        context.assertTrue(Math.abs(MathHelper.wrapDegrees(actor.getYaw() - Direction.WEST.asRotation())) < 1, "facing the marker's way");
        when(context, () -> done[0], WHOLE_THEFT, "the theft ends", () -> {
            // the marker broken: beside the space again, on the token's right
            context.setBlockState(MARKER, Blocks.AIR);
            boolean[] again = {false};
            MobEntity token = tokens.getFirst();
            FrousseuxEntity second = theft(context, party, token, thief, tile, again);
            Vec3d beside = BoardSpaces.standPos(context.getWorld(), tile.getPos()).add(Vec3d.fromPolar(0, token.getYaw() + 90).multiply(0.9));
            context.assertTrue(second != null && near(second.getPos(), beside), "no marker: beside the space");
            when(context, () -> again[0], WHOLE_THEFT, "the second theft ends", context::complete);
        });
    }

    // ---------------------------------------------------------------- always visible

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE_THEFT + 60, batchId = "spawn_marker_resident")
    public void anAlwaysVisibleMobLivesOnItsMarker(TestContext context) {
        ServerPlayerEntity thief = player(context), victim = player(context);
        InventoryUtils.giveOrDrop(victim, new ItemStack(Items.GOLD_INGOT), 5);
        BoardSpaceBlockEntity tile = tile(context, TILE, new ItemStack(ModItems.FROUSSEUX_CARTRIDGE));
        BlockPos marker = marker(context, MARKER, Direction.SOUTH);
        link(context, tile, marker);
        SpawnMarkerBlockEntity entity = (SpawnMarkerBlockEntity) context.getWorld().getBlockEntity(marker);
        entity.setResident(true);
        atEnd(context, () -> MarkerResidents.remove(context.getWorld(), marker));
        MarkerResidents.update(context.getWorld().getServer());
        if (noPartyAround(context, tile.getPos()))
            context.assertTrue(MarkerResidents.resident(context.getWorld(), marker) == null, "no party: nobody on it");
        List<MobEntity> tokens = new ArrayList<>();
        PartyControllerEntity party = party(context, tokens, thief, victim);
        MarkerResidents.update(context.getWorld().getServer());
        MobEntity resident = MarkerResidents.resident(context.getWorld(), marker);
        context.assertTrue(resident instanceof FrousseuxEntity && near(resident.getPos(), SpawnMarkerBlock.standPos(marker))
                && BoardActors.isBoardActor(resident) && !resident.shouldSave() && resident.isAiDisabled(), "a party: its Frousseux lives on it, a hologram");
        MarkerResidents.update(context.getWorld().getServer());
        context.assertTrue(MarkerResidents.resident(context.getWorld(), marker) == resident, "one mob per marker");
        // reloaded (its chunk, or the server): never saved, made again
        resident.discard();
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.invoker().onUnload(entity, context.getWorld());
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.invoker().onLoad(entity, context.getWorld());
        MarkerResidents.update(context.getWorld().getServer());
        MobEntity again = MarkerResidents.resident(context.getWorld(), marker);
        context.assertTrue(again != null && again != resident && near(again.getPos(), SpawnMarkerBlock.standPos(marker)), "made again after a reload");
        // a token lands: it steps out for the show, and is back after
        boolean[] done = {false};
        FrousseuxEntity actor = theft(context, party, tokens.getFirst(), thief, tile, done);
        context.assertTrue(again.isRemoved() && MarkerResidents.resident(context.getWorld(), marker) == null, "out for the show");
        context.assertTrue(actor != null && near(actor.getPos(), SpawnMarkerBlock.standPos(marker)), "the show starts on the marker");
        when(context, () -> done[0], WHOLE_THEFT, "the theft ends", () -> context.waitAndRun(2, () -> {
            MobEntity back = MarkerResidents.resident(context.getWorld(), marker);
            context.assertTrue(back != null && near(back.getPos(), SpawnMarkerBlock.standPos(marker)), "back on its marker after the show");
            // the party ends: it goes
            party.setPartyData(new PartyData());
            MarkerResidents.update(context.getWorld().getServer());
            if (noPartyAround(context, tile.getPos()))
                context.assertTrue(back.isRemoved() && MarkerResidents.resident(context.getWorld(), marker) == null, "the party over: gone");
            // set back to « on landing »: gone, even in a party
            entity.setResident(false);
            MarkerResidents.refresh(context.getWorld(), marker);
            context.assertTrue(back.isRemoved() && MarkerResidents.resident(context.getWorld(), marker) == null, "on landing again: gone");
            context.complete();
        }));
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "spawn_marker_default")
    public void theDefaultModeShowsNobodyBetweenShows(TestContext context) {
        ServerPlayerEntity a = player(context), b = player(context);
        BoardSpaceBlockEntity tile = tile(context, TILE, new ItemStack(ModItems.GLANDOUILLE_CARTRIDGE));
        BlockPos marker = marker(context, MARKER, Direction.SOUTH);
        link(context, tile, marker);
        party(context, new ArrayList<>(), a, b);
        MarkerResidents.update(context.getWorld().getServer());
        context.assertTrue(MarkerResidents.resident(context.getWorld(), marker) == null, "only on landing: nobody on it");
        context.complete();
    }

    // ---------------------------------------------------------------- no chest: the Party Controller's bank

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_bank_fallback")
    public void noChestTakesFromThePartyBank(TestContext context) {
        BoardSpaceBlockEntity inventory = tile(context, TILE, new ItemStack(ModItems.INVENTORY_CARTRIDGE));
        BoardSpaceBlockEntity trichaudron = tile(context, new BlockPos(5, 1, 3), new ItemStack(ModItems.TRICHAUDRON_CARTRIDGE));
        if (noPartyAround(context, inventory.getPos())) {
            context.assertTrue(CartridgeTransfers.source(context.getWorld(), inventory.getStack(0), inventory.getPos()).isNone(),
                    "no chest, no party: nothing");
            context.assertTrue(TrichaudronCartridgeItem.available(trichaudron.getStack(0), context.getWorld(), trichaudron.getPos()).isEmpty(),
                    "no chest, no party: the Tricauldron has nothing");
        }
        ServerPlayerEntity a = player(context), b = player(context);
        PartyControllerEntity party = party(context, new ArrayList<>(), a, b);
        party.getBankItems().setStack(0, new ItemStack(Items.DIAMOND, 4));
        PartyResources bank = CartridgeTransfers.source(context.getWorld(), inventory.getStack(0), inventory.getPos());
        context.assertTrue(bank.available(new ItemStack(Items.DIAMOND)) == 4, "a party: its controller's bank");
        List<ItemStack> prizes = TrichaudronCartridgeItem.available(trichaudron.getStack(0), context.getWorld(), trichaudron.getPos());
        context.assertTrue(prizes.size() == 1 && prizes.getFirst().isOf(Items.DIAMOND), "the Tricauldron gives from the bank");
        // its own chest linked: that one only
        context.setBlockState(new BlockPos(1, 1, 5), Blocks.CHEST);
        BlockPos chest = context.getAbsolutePos(new BlockPos(1, 1, 5));
        CartridgeContainers.toggle(inventory.getStack(0), context.getWorld(), chest);
        PartyResources own = CartridgeTransfers.source(context.getWorld(), inventory.getStack(0), inventory.getPos());
        context.assertTrue(!own.isNone() && own.available(new ItemStack(Items.DIAMOND)) == 0, "its own chest, not the bank");
        context.complete();
    }

    // ---------------------------------------------------------------- height

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80, batchId = "spawn_marker_lift")
    public void theMobAppearsLiftedAndFloats(TestContext context) {
        BoardSpaceBlockEntity tile = tile(context, TILE, new ItemStack(ModItems.MISTIGRI_CARTRIDGE));
        BlockPos marker = marker(context, MARKER, Direction.NORTH);
        link(context, tile, marker);
        SpawnMarkerBlockEntity entity = (SpawnMarkerBlockEntity) context.getWorld().getBlockEntity(marker);
        // the API every mob space uses: its marker, lifted; none: the show's default
        BoardMobSpots.Spot none = BoardMobSpots.spot(context.getWorld(), context.getAbsolutePos(new BlockPos(1, 1, 1)), Vec3d.ZERO, 90);
        context.assertTrue(!none.onMarker() && none.yaw() == 90, "no marker: the default");
        // its menu: from close enough, clamped
        withMenu(context, player -> {
            context.assertTrue(SpawnMarkerSettings.apply(player, new SpawnMarkerSettings(marker, true, 99)), "applied");
            context.assertEquals(entity.getLiftSteps(), SpawnMarkerBlockEntity.MAX_LIFT, "clamped to +8 blocks");
            context.assertTrue(SpawnMarkerSettings.apply(player, new SpawnMarkerSettings(marker, true, 6)), "applied");
        });
        context.assertTrue(entity.isResident() && entity.getLift() == 3, "always visible, 3 blocks up");
        BoardMobSpots.Spot spot = BoardMobSpots.spot(context.getWorld(), tile.getPos(), Vec3d.ZERO, 0);
        context.assertTrue(spot.onMarker() && spot.lifted() && Math.abs(spot.pos().y - (marker.getY() + 3)) < 1.0E-6
                && spot.yaw() == Direction.NORTH.asRotation(), "on the marker, 3 blocks up, facing its way");
        ServerPlayerEntity a = player(context), b = player(context);
        party(context, new ArrayList<>(), a, b);
        atEnd(context, () -> MarkerResidents.remove(context.getWorld(), marker));
        MarkerResidents.update(context.getWorld().getServer());
        MobEntity resident = MarkerResidents.resident(context.getWorld(), marker);
        context.assertTrue(resident != null && resident.hasNoGravity() && Math.abs(resident.getY() - (marker.getY() + 3)) < 0.01, "it appears 3 blocks up");
        context.waitAndRun(30, () -> {
            context.assertTrue(!resident.isRemoved() && Math.abs(resident.getY() - (marker.getY() + 3)) < 0.01, "and floats there");
            // under the floor: -2 blocks
            entity.setLiftSteps(-4);
            MarkerResidents.refresh(context.getWorld(), marker);
            MobEntity low = MarkerResidents.resident(context.getWorld(), marker);
            context.assertTrue(low != null && Math.abs(low.getY() - (marker.getY() - 2)) < 0.01, "2 blocks under the marker");
            context.complete();
        });
    }

    /** {@code test} with a mock player standing by the marker. */
    private static void withMenu(TestContext context, java.util.function.Consumer<ServerPlayerEntity> test) {
        ServerPlayerEntity player = player(context);
        Vec3d at = context.getAbsolute(new Vec3d(5.5, 1, 5.5));
        player.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        test.accept(player);
        ServerPlayerEntity far = player(context);
        Vec3d away = context.getAbsolute(new Vec3d(5.5, 1, 30));
        far.refreshPositionAndAngles(away.x, away.y, away.z, 0, 0);
        context.assertTrue(!SpawnMarkerSettings.apply(far, new SpawnMarkerSettings(context.getAbsolutePos(MARKER), false, 0)), "too far: refused");
    }
}
