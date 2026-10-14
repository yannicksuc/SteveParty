package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.api.event.MiniGameEvents;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyStats;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyStatsCollector;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.events.TileReachedEvent;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.gametest.kit.TestCleanup;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The statistics of a party (for a future end infographic and bonus awards): collected while it plays, kept after its
 * end, saved with it, cleared when a new one starts.
 */
public class PartyStatsGameTests implements SteveGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(1, 1, 5), STAR = new BlockPos(3, 1, 1);

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aPartyRecordsWhatEachPawnDidAndKeepsItSaved(TestContext context) {
        TestBoards.floor(context, 7);
        UUID ownerA = UUID.randomUUID(), ownerB = UUID.randomUUID();
        PigEntity a = TestBoards.token(context, new BlockPos(1, 1, 1), ownerA);
        PigEntity b = TestBoards.token(context, new BlockPos(5, 1, 1), ownerB);
        context.setBlockState(STAR, ModBlocks.TILE);
        BoardSpaceBlockEntity star = context.getBlockEntity(STAR);
        star.setStack(0, new ItemStack(ModItems.STAR_CARTRIDGE));
        PartyControllerEntity controller = TestBoards.party(context, CONTROLLER, 2, a.getUuid(), b.getUuid());
        PartyStats stats = controller.getPartyData().getStats();

        // The first round started: a point of the timeline, a line per pawn
        context.assertTrue(stats.points().stream().anyMatch(point -> point.kind() == PartyStats.Kind.ROUND && point.round() == 1),
                "the first round's start is a point of the timeline: " + stats.points());
        context.assertTrue(stats.players().keySet().containsAll(List.of(a.getUuid(), b.getUuid())), "a line per pawn");
        context.assertEquals(stats.of(a.getUuid()).owner(), ownerA, "the pawn's player");

        // a walks two spaces, stops on the star space, rolls a 6 then a 2
        TileReachedEvent.EVENT.invoker().onTileReached(a, star);
        TileReachedEvent.EVENT.invoker().onTileReached(a, star);
        // (each reach without steps left is a landing too)
        int landings = stats.of(a.getUuid()).landings("steveparty:tile_star");
        PartyStatsCollector.landed(controller, a, star);
        PartyStatsCollector.rolled(controller, a.getUuid(), 6);
        PartyStatsCollector.rolled(controller, a.getUuid(), 2);
        PartyStatsCollector.used(controller, a.getUuid(), new ItemStack(ModItems.STAR_CARTRIDGE));
        // b wins a mini-game, a comes last
        MiniGameEvents.ENDED.invoker().onMiniGameEnded(controller, null, Map.of(ownerB, 1, ownerA, 2), List.of(ownerB));

        PartyStats.Player statsA = stats.of(a.getUuid()), statsB = stats.of(b.getUuid());
        context.assertEquals(statsA.spacesWalked(), 2, "spaces walked");
        context.assertEquals(statsA.landings("steveparty:tile_star"), landings + 1, "star space landings: " + statsA.landings());
        context.assertEquals(statsA.diceRolls(), 2, "dice rolls");
        context.assertEquals(statsA.rollTotal(), 8, "roll total");
        context.assertEquals(statsA.bestRoll(), 6, "best roll");
        context.assertEquals(statsA.itemsUsed().get("steveparty:star_cartridge"), 1, "items used");
        context.assertTrue(statsB.miniGamesWon() == 1 && statsB.miniGamesLost() == 0, "b won the mini-game");
        context.assertTrue(statsA.miniGamesWon() == 0 && statsA.miniGamesLost() == 1 && statsA.miniGamesPlayed() == 1, "a lost it");
        context.assertTrue(stats.points().getLast().kind() == PartyStats.Kind.MINI_GAME, "the mini-game's end is a point");
        int points = stats.points().size();
        context.assertTrue(statsA.coins().size() == points && statsB.stars().size() == points, "one value per point for each pawn");

        // Saved and read again: the same
        NbtCompound nbt = controller.getPartyData().toNbt(new NbtCompound());
        PartyStats read = new PartyData(nbt).getStats();
        context.assertEquals(read.points(), stats.points(), "points saved");
        PartyStats.Player readA = read.of(a.getUuid());
        context.assertTrue(readA != null && readA.spacesWalked() == 2 && readA.rollTotal() == 8 && readA.miniGamesLost() == 1
                && readA.landings().equals(statsA.landings()) && readA.coins().equals(statsA.coins()), "a's line saved");

        // The party ends: a last point, the statistics stay until the next party
        var data = controller.getPartyData();
        data.setStepIndex(data.getSteps().size() - 2);
        controller.nextStep();
        context.assertTrue(data.isAtEnd(), "the party is over");
        context.assertTrue(stats.points().getLast().kind() == PartyStats.Kind.END, "its end is the last point");
        context.assertTrue(stats.of(a.getUuid()) != null, "kept after the end");

        ((TokenizedEntityInterface) a).steveparty$setTokenized(false);
        ((TokenizedEntityInterface) b).steveparty$setTokenized(false);
        TestCleanup.removeIf(context, CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        context.complete();
    }
}
