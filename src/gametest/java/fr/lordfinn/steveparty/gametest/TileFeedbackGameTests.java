package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.Kind;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.Landing;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.Played;
import fr.lordfinn.steveparty.components.InventoryComponent;
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
                context.assertEquals(layer.sound().id().getNamespace(), "minecraft", landing + " vanilla sound");
                context.assertTrue(layer.volume() > 0 && layer.volume() <= 0.7F, landing + " not too loud");
            }
            context.assertTrue(landing.noticeKey().startsWith("message.steveparty.tile_landed."), "notice key");
        }
        context.assertTrue(Registries.SOUND_EVENT.containsId(Landing.GOOD.layers().getFirst().sound().id()), "registered sound");
        context.complete();
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
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
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
        stop.onTileReached(halted, controller);
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
