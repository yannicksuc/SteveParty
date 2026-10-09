package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.custom.cartridges.FrousseuxCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.GlandouilleCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.KeyGateCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.MistigriCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.TrichaudronCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.PotCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.StarCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.ThresholdCartridgeItem;
import fr.lordfinn.steveparty.service.TokenMovementService;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.Kind;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.Landing;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.Played;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileTeleport;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.InventoryInteractorTileBehavior;
import fr.lordfinn.steveparty.items.custom.cartridges.ReplayCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.StopCartridgeItem;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.AdvanceBackCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.events.TileReachedEvent;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Tile feedback: a pop when passing, a themed landing where a token stops, only a soft pop outside a game. */
public class TileFeedbackGameTests implements FabricGameTest {

    private static BoardSpaceBlockEntity placeTile(TestContext context, BlockPos pos) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.ADVANCED_TILE);
        return context.getBlockEntity(pos);
    }

    private static PigEntity spawnToken(TestContext context, BlockPos pos, int steps) {
        PigEntity pig = context.spawnMob(EntityType.PIG, pos.up());
        TokenizedEntityInterface token = (TokenizedEntityInterface) pig;
        token.steveparty$setTokenized(true);
        token.steveparty$setNbSteps(steps);
        return pig;
    }

    /** Records the feedback played on the given (relative) positions of this test, until the test ends. */
    private static List<Played> record(TestContext context, BlockPos... relative) {
        List<BlockPos> absolute = new ArrayList<>();
        for (BlockPos pos : relative) absolute.add(context.getAbsolutePos(pos));
        List<Played> played = new ArrayList<>();
        Consumer<Played> listener = event -> {
            if (absolute.contains(event.tile())) played.add(event);
        };
        TileFeedback.LISTENERS.add(listener);
        context.addFinalTask(() -> TileFeedback.LISTENERS.remove(listener));
        return played;
    }

    private static long count(List<Played> played, Kind kind) {
        return played.stream().filter(event -> event.kind() == kind).count();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void landingFollowsTheTileRole(TestContext context) {
        BoardSpaceBlockEntity tile = placeTile(context, new BlockPos(2, 1, 2));
        context.assertEquals(TileFeedback.landingOf(tile), Landing.DEFAULT, "no cartridge");
        tile.setStack(0, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
        context.assertEquals(TileFeedback.landingOf(tile), Landing.DEFAULT, "plain cartridge");
        tile.setStack(0, new ItemStack(ModItems.TILE_BEHAVIOR_START));
        context.assertEquals(TileFeedback.landingOf(tile), Landing.START, "start");
        tile.setStack(0, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP));
        context.assertEquals(TileFeedback.landingOf(tile), Landing.STOP, "stop");

        tile.setStack(0, new ItemStack(ModItems.INVENTORY_CARTRIDGE));
        context.assertEquals(TileFeedback.landingOf(tile), Landing.ITEM, "empty item cartridge");
        tile.getStack(0).set(ModComponents.INVENTORY_COMPONENT, new InventoryComponent(List.of(new ItemStack(Items.DIAMOND))));
        context.assertEquals(TileFeedback.landingOf(tile), Landing.GOOD, "item given");
        ItemStack taken = new ItemStack(Items.DIAMOND);
        taken.set(ModComponents.IS_NEGATIVE, true);
        tile.getStack(0).set(ModComponents.INVENTORY_COMPONENT, new InventoryComponent(List.of(taken)));
        context.assertEquals(TileFeedback.landingOf(tile), Landing.BAD, "item taken");

        // Vanilla sounds only, a jingle for every kind, and a notice in both languages (see LangGameTests)
        for (Landing landing : Landing.values()) {
            context.assertTrue(!landing.layers().isEmpty(), landing + " has a jingle");
            for (TileFeedback.Layer layer : landing.layers()) {
                context.assertEquals(layer.sound().getId().getNamespace(), "minecraft", landing + " vanilla sound");
                context.assertTrue(layer.volume() > 0 && layer.volume() <= 0.7F, landing + " not too loud");
            }
            context.assertTrue(landing.noticeKey().startsWith("message.steveparty.tile_landed."), "notice key");
        }
        context.assertTrue(Registries.SOUND_EVENT.containsId(Landing.GOOD.layers().getFirst().sound().getId()), "registered sound");
        context.complete();
    }

    /**
     * Every role has its own default colour (face, particles, notice), all far apart; the Stop tile is anthracite until
     * its cartridge is dyed.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void eachRoleHasItsOwnColour(TestContext context) {
        BoardSpaceBlockEntity tile = placeTile(context, new BlockPos(2, 1, 2));
        tile.setStack(0, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP));
        context.assertEquals(TileFeedback.tileColor(tile), StopCartridgeItem.COLOR, "an anthracite Stop tile");
        ItemStack dyed = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP);
        dyed.set(ModComponents.COLOR, 0xB02E26);
        tile.setStack(0, dyed);
        context.assertEquals(TileFeedback.tileColor(tile), 0xB02E26, "a dyed Stop cartridge keeps its dye");
        tile.setStack(0, new ItemStack(ModItems.INVENTORY_CARTRIDGE));
        context.assertEquals(TileFeedback.tileColor(tile), InventoryInteractorTileBehavior.NEUTRAL_COLOR, "an orange item tile");

        List<Integer> colours = List.of(0xFFFFFF, InventoryInteractorTileBehavior.GOOD_COLOR, InventoryInteractorTileBehavior.BAD_COLOR,
                InventoryInteractorTileBehavior.NEUTRAL_COLOR, ShopCartridgeItem.COLOR, StopCartridgeItem.COLOR,
                AdvanceBackCartridgeItem.FORWARD_COLOR, AdvanceBackCartridgeItem.BACK_COLOR, ReplayCartridgeItem.COLOR,
                TileTeleport.COLOR, StarCartridgeItem.COLOR,
                GlandouilleCartridgeItem.COLOR,
                FrousseuxCartridgeItem.COLOR,
                MistigriCartridgeItem.COLOR,
                ThresholdCartridgeItem.COLOR,
                PotCartridgeItem.COLOR,
                KeyGateCartridgeItem.COLOR,
                TrichaudronCartridgeItem.COLOR);
        for (int i = 0; i < colours.size(); i++) {
            for (int j = i + 1; j < colours.size(); j++) {
                context.assertTrue(distance(colours.get(i), colours.get(j)) > 40,
                        String.format("#%06X and #%06X far apart", colours.get(i), colours.get(j)));
            }
        }
        // The landing particles and notices use the same colours as the faces
        context.assertEquals(Landing.BAD.accent(), InventoryInteractorTileBehavior.BAD_COLOR, "malus");
        context.assertEquals(Landing.ITEM.accent(), InventoryInteractorTileBehavior.NEUTRAL_COLOR, "item");
        context.assertEquals(Landing.STOP.accent(), StopCartridgeItem.COLOR, "stop");
        context.assertEquals(Landing.ADVANCE.accent(), AdvanceBackCartridgeItem.FORWARD_COLOR, "advance");
        context.assertEquals(Landing.BACK.accent(), AdvanceBackCartridgeItem.BACK_COLOR, "back");
        context.assertEquals(Landing.REPLAY.accent(), ReplayCartridgeItem.COLOR, "replay");
        context.assertEquals(Landing.SHOP.accent(), ShopCartridgeItem.COLOR, "shop");
        context.assertEquals(Landing.STAR.accent(), StarCartridgeItem.COLOR, "star");
        context.assertEquals(Landing.GLANDOUILLE.accent(), GlandouilleCartridgeItem.COLOR, "glandouille");
        context.complete();
    }

    /** Distance between two colours (RGB, weighted like the eye: green counts most). */
    private static double distance(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF), dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF), db = (a & 0xFF) - (b & 0xFF);
        return Math.sqrt(2 * dr * dr + 4 * dg * dg + 3 * db * db) / 3;
    }

    /** In a game: passing pops, the destination lands (themed, with a notice to the party), a stop tile halts. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void passingPopsAndLandingNotifiesTheParty(TestContext context) {
        BlockPos passPos = new BlockPos(1, 1, 1), landPos = new BlockPos(3, 1, 1), stopPos = new BlockPos(5, 1, 1);
        BlockPos controllerPos = new BlockPos(3, 1, 4);
        List<Played> played = record(context, passPos, landPos, stopPos);
        context.setBlockState(controllerPos.down(), Blocks.STONE);
        context.setBlockState(controllerPos, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(controllerPos);
        ServerPlayerEntity player = TestPlayers.mock(context);
        BlockPos near = context.getAbsolutePos(controllerPos.east(2));
        player.refreshPositionAndAngles(near.getX() + 0.5, near.getY(), near.getZ() + 0.5, 0, 0);
        context.assertTrue(controller.getPartyAudience().contains(player), "the player nearby follows the party");

        BoardSpaceBlockEntity pass = placeTile(context, passPos);
        MobEntity walker = spawnToken(context, passPos, 3);
        pass.onTileReached(walker, controller);
        context.assertEquals(count(played, Kind.PASS), 1L, "a pop when going over a tile");
        context.assertEquals(count(played, Kind.LAND), 0L, "no landing while it goes on");

        BoardSpaceBlockEntity land = placeTile(context, landPos);
        land.setStack(0, new ItemStack(ModItems.INVENTORY_CARTRIDGE));
        land.getStack(0).set(ModComponents.INVENTORY_COMPONENT, new InventoryComponent(List.of(new ItemStack(Items.DIAMOND))));
        MobEntity lander = spawnToken(context, landPos, 0);
        land.onTileReached(lander, controller);
        context.assertEquals(count(played, Kind.PASS), 1L, "no pop where it stops");
        context.assertEquals(count(played, Kind.LAND), 0L, "the landing is the destination's (onDestinationReached)");
        land.getBoardSpaceBehavior().onDestinationReached(context.getWorld(), land.getPos(), lander, land, controller);
        Played landing = played.stream().filter(event -> event.kind() == Kind.LAND).findFirst().orElseThrow();
        context.assertEquals(landing.landing(), Landing.GOOD, "a bonus tile's landing");
        context.assertTrue(landing.noticeRecipients() >= 1, "notice sent to the party's players");

        BoardSpaceBlockEntity stop = placeTile(context, stopPos);
        stop.setStack(0, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP));
        MobEntity halted = spawnToken(context, stopPos, 2);
        context.assertTrue(TokenMovementService.endMoveIfForcedStop(halted, stop), "a stop tile ends the move there");
        context.assertEquals(((TokenizedEntityInterface) halted).steveparty$getNbSteps(), 0, "its steps left are lost");
        stop.onTileReached(halted, controller);
        context.assertEquals(count(played, Kind.LAND), 1L, "the landing is the destination's (onDestinationReached)");
        stop.getBoardSpaceBehavior().onDestinationReached(context.getWorld(), stop.getPos(), halted, stop, controller);
        context.assertTrue(played.stream().anyMatch(event -> event.kind() == Kind.LAND && event.landing() == Landing.STOP
                && event.tile().equals(context.getAbsolutePos(stopPos))), "a stop tile halting a token lands");
        context.assertEquals(count(played, Kind.PASS), 1L, "and does not pop");
        context.removeBlock(controllerPos);
        context.complete();
    }

    /** Outside a game: a token reaching a tile only pops, no landing, no notice. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void outsideAGameATokenOnlyPops(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        List<Played> played = record(context, pos);
        BoardSpaceBlockEntity tile = placeTile(context, pos);
        tile.setStack(0, new ItemStack(ModItems.TILE_BEHAVIOR_START));
        MobEntity token = spawnToken(context, pos, 0);
        context.assertTrue(!TileFeedback.isInRunningParty(token.getUuid()), "in no party");
        TileReachedEvent.EVENT.invoker().onTileReached(token, tile);
        context.assertEquals(count(played, Kind.AMBIENT), 1L, "a soft pop");
        context.assertEquals(count(played, Kind.LAND), 0L, "no landing outside a game");
        context.assertTrue(played.stream().allMatch(event -> event.noticeRecipients() == 0), "no notice");
        TileReachedEvent.EVENT.invoker().onTileReached(token, tile);
        context.assertEquals(count(played, Kind.AMBIENT), 1L, "the same tile again: no new pop");
        context.complete();
    }

    /** Walking pops once per new tile, never more often than the cooldown. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void walkingPopsOncePerNewTileWithACooldown(TestContext context) {
        BlockPos a = new BlockPos(1, 1, 2), b = new BlockPos(2, 1, 2), c = new BlockPos(3, 1, 2);
        placeTile(context, a);
        placeTile(context, b);
        placeTile(context, c);
        PigEntity walker = context.spawnMob(EntityType.PIG, a.up());
        var world = context.getWorld();
        BlockPos absA = context.getAbsolutePos(a), absB = context.getAbsolutePos(b), absC = context.getAbsolutePos(c);

        context.assertTrue(TileFeedback.ambientStep(world, walker, absA), "stepping onto a tile pops");
        context.assertTrue(!TileFeedback.ambientStep(world, walker, absA), "standing on it does not");
        context.assertTrue(!TileFeedback.ambientStep(world, walker, absB), "the next tile right away: cooldown");
        context.waitAndRun(TileFeedback.AMBIENT_COOLDOWN_TICKS + 1, () -> {
            context.assertTrue(!TileFeedback.ambientStep(world, walker, absB), "still on the tile of the cooldown: no pop");
            context.assertTrue(TileFeedback.ambientStep(world, walker, absC), "a new tile after the cooldown pops");
            context.assertTrue(!TileFeedback.ambientStep(world, walker, null), "off the board: nothing");
            context.complete();
        });
    }
}
