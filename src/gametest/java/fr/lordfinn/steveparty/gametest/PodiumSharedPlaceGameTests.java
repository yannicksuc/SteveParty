package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.TestBank;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import com.mojang.authlib.GameProfile;
import fr.lordfinn.steveparty.blocks.ModBlocks;
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
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePodiumLink;
import fr.lordfinn.steveparty.podium.PodiumGroup;
import fr.lordfinn.steveparty.podium.PodiumOccupant;
import fr.lordfinn.steveparty.podium.PodiumSignal;
import fr.lordfinn.steveparty.podium.Podiums;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * While a mini-game is played on the page of a podium group, its columns of the same height are one place: taken,
 * left, taken over and emptied as a whole, by a player or by a team whose members stand one per column. Out of a
 * mini-game, and for the podiums linked to no page, every column stays on its own.
 */
public class PodiumSharedPlaceGameTests implements FabricGameTest {
    private static final AtomicInteger SERIAL = new AtomicInteger();
    private static final BlockPos CONTROLLER = new BlockPos(0, 1, 7);

    // ------------------------------------------------------------------ helpers

    private static void place(TestContext context, BlockPos pos, BlockState state) {
        BlockPos abs = context.getAbsolutePos(pos);
        context.getWorld().setBlockState(abs, Block.postProcessState(state, context.getWorld(), abs));
    }

    /** A podium column of {@code halves} half blocks standing at x, z. @return its bottom block (relative) */
    private static BlockPos column(TestContext context, int x, int z, int halves) {
        BlockPos bottom = new BlockPos(x, 1, z);
        int y = 0;
        for (; halves >= 2; halves -= 2) place(context, bottom.up(y++), ModBlocks.PODIUM.getDefaultState().with(PodiumBlock.FULL, true));
        if (halves == 1) place(context, bottom.up(y), ModBlocks.GOLD_PODIUM.getDefaultState());
        return bottom;
    }

    private static PodiumBlockEntity master(TestContext context, BlockPos bottom) {
        return context.getBlockEntity(bottom);
    }

    /** Who holds a column, null for nobody. */
    private static @Nullable UUID holder(TestContext context, BlockPos bottom) {
        PodiumOccupant occupant = master(context, bottom).getOccupant();
        return occupant == null ? null : occupant.player();
    }

    /** Who stands on a column as a figure, null for nobody. */
    private static @Nullable UUID figure(TestContext context, BlockPos bottom) {
        PodiumOccupant occupant = master(context, bottom).getOccupant();
        return occupant == null || occupant.figure() == null ? null : occupant.figure().player();
    }

    private static int comparator(TestContext context, BlockPos bottom) {
        BlockPos abs = context.getAbsolutePos(bottom);
        return context.getWorld().getBlockState(abs).getComparatorOutput(context.getWorld(), abs);
    }

    private static PodiumGroup group(TestContext context, BlockPos pos) {
        return PodiumGroup.of(context.getWorld(), context.getAbsolutePos(pos));
    }

    private static boolean register(TestContext context, BlockPos bottom, ServerPlayerEntity player) {
        PodiumGroup group = group(context, bottom);
        return Podiums.register(group, group.columnAt(context.getWorld(), context.getAbsolutePos(bottom)), player);
    }

    private static ServerPlayerEntity player(TestContext context, String name, double x, double y, double z) {
        ServerWorld world = context.getWorld();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "s" + SERIAL.incrementAndGet() + name);
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

    private static int coins(PartyControllerEntity controller, ServerPlayerEntity player) {
        return InventoryUtils.count(player.getInventory(), controller.getCurrency(PartyCurrency.COIN));
    }

    /** A mini-game page with the podiums linked to it. */
    private static ItemStack page(TestContext context, BlockPos... podiums) {
        MinecraftServer server = context.getWorld().getServer();
        ItemStack stack = new ItemStack(ModItems.MINI_GAME_PAGE);
        UUID id = MiniGamePages.ensureId(stack);
        MiniGamePages.update(server, MiniGamePages.get(server, id).withTexts("Place test " + SERIAL.incrementAndGet(), ""));
        for (BlockPos podium : podiums) {
            MiniGamePages.addPodiumLink(server, id, new MiniGamePodiumLink(
                    GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(podium)), MiniGamePodiumLink.Kind.PODIUM));
        }
        return stack;
    }

    private record Played(PartyControllerEntity controller, MiniGamePartyStep step) {
    }

    /** A party controller whose mini-game, on {@code pageStack}, is at {@code phase} with {@code participants}. */
    private static Played played(TestContext context, ItemStack pageStack, @Nullable TeamDisposition teams,
                                 MiniGamePartyStep.Phase phase, ServerPlayerEntity... participants) {
        context.setBlockState(CONTROLLER.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
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
        MiniGamePartyStep step = new MiniGamePartyStep(stepNbt);
        UUID token = UUID.randomUUID();
        PartyData data = new PartyData();
        data.addToken(token);
        data.addStep(new PartyStep());
        data.addStep(step);
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(token))));
        data.setStepIndex(1);
        controller.setPartyData(data);
        ItemStack catalogue = new ItemStack(ModItems.MINI_GAMES_CATALOGUE);
        MiniGamesCatalogueItem.setCurrentMiniGamePage(catalogue, pageStack);
        MiniGamesCatalogueItem.setCurrentMiniGameTeamDisposition(catalogue, teams == null ? TeamDisposition.freeForAll(uuids) : teams);
        controller.catalogue = catalogue;
        // A well stocked bank: the gains are taken from it
        TestBank.stock(context, controller, CONTROLLER.up(), 640, 64);
        return new Played(controller, step);
    }

    private static void cleanUp(TestContext context, ServerPlayerEntity... players) {
        for (ServerPlayerEntity player : players) TestPlayers.remove(context, player);
        if (context.getBlockState(CONTROLLER).isOf(ModBlocks.PARTY_CONTROLLER)) context.removeBlock(CONTROLLER);
    }

    private static Set<UUID> set(ServerPlayerEntity... players) {
        Set<UUID> set = new LinkedHashSet<>();
        for (ServerPlayerEntity player : players) set.add(player.getUuid());
        return set;
    }

    // ------------------------------------------------------------------ free for all

    /**
     * Free for all: taking a column takes its whole place (the figure on the column taken, the others say who holds
     * them and read 15 too); the gesture again on any of its columns frees it all; taking it over from any column
     * takes it all; one place per player; the mini-game ends when every place is taken, without ties.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "podium_shared_ffa", tickLimit = 100)
    public void aPlayerTakesTheWholePlace(TestContext context) {
        BlockPos a1 = column(context, 1, 3, 4), a2 = column(context, 2, 3, 4), a3 = column(context, 3, 3, 4),
                b1 = column(context, 4, 3, 2), b2 = column(context, 5, 3, 2), c = column(context, 6, 3, 1);
        ServerPlayerEntity p1 = player(context, "one", 1.5, 1, 5.5), p2 = player(context, "two", 3.5, 1, 5.5), p3 = player(context, "three", 6.5, 1, 5.5);
        ServerWorld world = context.getWorld();
        Played played;
        try {
            played = played(context, page(context, a1), null, MiniGamePartyStep.Phase.PLAYING, p1, p2, p3);
            PodiumGroup group = group(context, a1);
            context.assertTrue(Podiums.sharesPlaces(group) && group.placeCount() == 3, "a mini-game on its page: three places");
            context.assertTrue(Podiums.toggle(p1, world, context.getAbsolutePos(a2)), "p1 takes the middle column of the first place");
            context.assertTrue(p1.getUuid().equals(holder(context, a1)) && p1.getUuid().equals(holder(context, a2)) && p1.getUuid().equals(holder(context, a3)),
                    "he holds the whole place");
            context.assertTrue(p1.getUuid().equals(figure(context, a2)) && figure(context, a1) == null && figure(context, a3) == null,
                    "his figure on the column he took, none on the others");
            context.assertTrue(comparator(context, a1) == 15 && comparator(context, a3) == 15 && comparator(context, b1) == 0,
                    "a comparator reads 15 on every column of the place");
            context.assertTrue(master(context, a1).getOccupant().place() == 1 && holder(context, b1) == null, "the first place, the others free");
            context.assertTrue(played.step().isPlaying(), "the mini-game goes on");
        } catch (RuntimeException e) {
            cleanUp(context, p1, p2, p3);
            throw e;
        }
        // The same gesture again (a moment later: the click and the sneak of one gesture count once)
        context.waitAndRun(10, () -> {
            try {
                context.assertTrue(Podiums.toggle(p1, world, context.getAbsolutePos(a3)), "the gesture again, on another column of his place");
                context.assertTrue(holder(context, a1) == null && holder(context, a2) == null && holder(context, a3) == null, "the whole place is free");

                // Taken over from any of its columns
                context.assertTrue(register(context, a1, p1), "p1 takes the first place again");
                context.assertTrue(!register(context, a3, p1), "already his: nothing changes");
                context.assertTrue(register(context, a3, p2), "p2 takes it over from another column");
                context.assertTrue(p2.getUuid().equals(holder(context, a1)) && p2.getUuid().equals(holder(context, a2)) && p2.getUuid().equals(figure(context, a3))
                        && figure(context, a1) == null, "the whole place is p2's, his figure where he took it");
                context.assertTrue(group(context, a1).columnOf(p1.getUuid(), -1) == null, "p1 holds nothing any more");

                // One place per player
                context.assertTrue(register(context, b2, p1), "p1 takes the second place");
                context.assertTrue(p1.getUuid().equals(holder(context, b1)) && p1.getUuid().equals(figure(context, b2)), "both its columns");
                context.assertTrue(register(context, b1, p2), "p2 moves to the second place");
                context.assertTrue(holder(context, a1) == null && holder(context, a2) == null && holder(context, a3) == null, "he left the first place, all of it");
                context.assertTrue(p2.getUuid().equals(holder(context, b1)) && p2.getUuid().equals(holder(context, b2)), "and holds the second one, all of it");

                // Every place taken: over, one holder per place (no tie)
                context.assertTrue(register(context, a2, p1), "p1 takes the first place");
                context.assertTrue(played.step().isPlaying(), "the third place is free and p3 has none: it goes on");
                context.assertTrue(Podiums.fill(group(context, a1), p3), "p3 takes the best free place");
                context.assertEquals(holder(context, c), p3.getUuid(), "the third one");
                context.assertEquals(played.step().getPhase(), MiniGamePartyStep.Phase.FINISHED, "every place is taken: the mini-game is over");
                context.assertEquals(played.step().getPlaces(), Map.of(p1.getUuid(), 1, p2.getUuid(), 2, p3.getUuid(), 3), "one holder per place");
                context.assertTrue(coins(played.controller(), p1) == 10 && coins(played.controller(), p2) == 5 && coins(played.controller(), p3) == 3,
                        "the gains of each place");
            } finally {
                cleanUp(context, p1, p2, p3);
            }
            context.complete();
        });
    }

    // ------------------------------------------------------------------ teams

    /**
     * Teams: the team holds the place and its members stand one per column, who registered on the column he took. Fewer
     * members than columns: the columns left say the team, without figure. More: the others are named on the label of
     * the last column. A team-mate's gesture on its team's place changes nothing.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "podium_shared_teams", tickLimit = 100)
    public void aTeamStandsOnItsPlaceOneMemberPerColumn(TestContext context) {
        BlockPos a1 = column(context, 1, 3, 4), a2 = column(context, 2, 3, 4), a3 = column(context, 3, 3, 4),
                b1 = column(context, 4, 3, 2), b2 = column(context, 5, 3, 2);
        ServerPlayerEntity r1 = player(context, "r1", 0.5, 1, 0.5), r2 = player(context, "r2", 1.5, 1, 0.5),
                g1 = player(context, "g1", 2.5, 1, 0.5), g2 = player(context, "g2", 3.5, 1, 0.5), g3 = player(context, "g3", 4.5, 1, 0.5),
                g4 = player(context, "g4", 5.5, 1, 0.5);
        try {
            Played played = played(context, page(context, a1), new TeamDisposition(set(r1, r2), set(g1, g2, g3, g4)),
                    MiniGamePartyStep.Phase.PLAYING, r1, r2, g1, g2, g3, g4);
            // Two members, three columns
            context.assertTrue(register(context, a2, r2), "r2 takes the first place for team A");
            PodiumOccupant taken = master(context, a2).getOccupant();
            context.assertTrue(taken.team() == 0 && taken.player().equals(r2.getUuid()), "held by the team, taken by r2");
            context.assertEquals(figure(context, a2), r2.getUuid(), "who registered stands on the column he took");
            context.assertEquals(figure(context, a1), r1.getUuid(), "his team-mate on the next column");
            context.assertTrue(figure(context, a3) == null && master(context, a3).getOccupant().team() == 0 && r2.getUuid().equals(holder(context, a3)),
                    "the column left says the team, without figure");
            context.assertTrue(!register(context, a3, r1) && r1.getUuid().equals(figure(context, a1)) && r2.getUuid().equals(holder(context, a3)),
                    "a team-mate's gesture on its team's place changes nothing");
            context.assertTrue(!Podiums.fill(group(context, a1), r1), "a team already placed takes no other place");
            PodiumOccupant shown = master(context, a1).getOccupant();
            context.assertEquals(PodiumOccupant.fromNbt(shown.toNbt()), shown, "what a column shows is saved");
            context.assertTrue(played.step().isPlaying(), "team B has no place: it goes on");

            // Four members, two columns
            context.assertTrue(register(context, b2, g3), "g3 takes the second place for team B");
            context.assertEquals(figure(context, b2), g3.getUuid(), "g3 on the column he took");
            context.assertEquals(figure(context, b1), g1.getUuid(), "then the first of the others, in the team's order");
            context.assertEquals(master(context, b1).getOccupant().more(), List.of(g2.getGameProfile().getName(), g4.getGameProfile().getName()),
                    "those without a column are named on the label of the last one");
            context.assertTrue(master(context, b2).getOccupant().more().isEmpty(), "and only there");
            context.assertEquals(PodiumOccupant.fromNbt(master(context, b1).getOccupant().toNbt()), master(context, b1).getOccupant(), "saved too");
            context.assertEquals(played.step().getPhase(), MiniGamePartyStep.Phase.FINISHED, "every team placed: over");
            context.assertEquals(played.step().getPlaces(), Map.of(r1.getUuid(), 1, r2.getUuid(), 1, g1.getUuid(), 2, g2.getUuid(), 2, g3.getUuid(), 2, g4.getUuid(), 2),
                    "the place of a team is its members'");
        } finally {
            cleanUp(context, r1, r2, g1, g2, g3, g4);
        }
        context.complete();
    }

    // ------------------------------------------------------------------ redstone

    /**
     * The redstone modes act on the place: « register the nearest » and « highest free place » fill it whole, « empty
     * this podium » empties it whole; a place is free when none of its columns is taken.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "podium_shared_redstone", tickLimit = 100)
    public void redstoneActsOnTheWholePlace(TestContext context) {
        BlockPos a1 = column(context, 1, 3, 3), a2 = column(context, 2, 3, 3), b1 = column(context, 3, 3, 2), b2 = column(context, 4, 3, 2),
                c = column(context, 5, 3, 1);
        ServerPlayerEntity p1 = player(context, "one", 2.5, 1, 5.5), p2 = player(context, "two", 5.5, 1, 5.5), p3 = player(context, "three", 7.5, 1, 7.5),
                p4 = player(context, "four", 0.5, 1, 0.5);
        try {
            Played played = played(context, page(context, a1), null, MiniGamePartyStep.Phase.PLAYING, p1, p2, p3, p4);
            // Register the nearest, into the second column of the first place
            context.setBlockState(a2.north(), Blocks.REDSTONE_BLOCK);
            context.assertTrue(p1.getUuid().equals(holder(context, a1)) && p1.getUuid().equals(holder(context, a2)), "the nearest player takes the whole place");
            context.assertEquals(figure(context, a2), p1.getUuid(), "his figure on the column that received the pulse");
            context.setBlockState(a2.north(), Blocks.AIR);

            // Highest free place, from the lowest column
            master(context, c).setSignal(PodiumSignal.FILL);
            context.setBlockState(c.east(), Blocks.REDSTONE_BLOCK);
            context.assertTrue(p2.getUuid().equals(holder(context, b1)) && p2.getUuid().equals(holder(context, b2)),
                    "the nearest player without a place takes the best free place, both its columns");
            context.assertTrue(holder(context, c) == null, "one pulse, one place");
            context.setBlockState(c.east(), Blocks.AIR);

            // Empty this podium
            master(context, b2).setSignal(PodiumSignal.CLEAR);
            context.setBlockState(b2.north(), Blocks.REDSTONE_BLOCK);
            context.assertTrue(holder(context, b1) == null && holder(context, b2) == null, "a pulse into one column empties the whole place");
            context.assertTrue(p1.getUuid().equals(holder(context, a1)), "the other places are kept");
            context.setBlockState(b2.north(), Blocks.AIR);

            // A place with a column taken is not free (what a per-player goal pole asks for too)
            master(context, b1).setOccupant(new PodiumOccupant(UUID.randomUUID(), "stale", -1, 2, 0));
            PodiumGroup group = group(context, a1);
            context.assertEquals(group.highestFreePlace().bottom(), context.getAbsolutePos(c), "the best place none of whose columns is taken");
            context.assertTrue(Podiums.fill(group, p3) && p3.getUuid().equals(holder(context, c)), "a winner arriving takes it");
            context.assertTrue(group(context, a1).highestFreePlace() == null, "no free place left");
            context.assertTrue(played.step().isPlaying() || played.step().getPhase() == MiniGamePartyStep.Phase.FINISHED, "still the mini-game's");
        } finally {
            cleanUp(context, p1, p2, p3, p4);
        }
        context.complete();
    }

    // ------------------------------------------------------------------ what does not change

    /**
     * Out of a mini-game, the columns of the same height of a linked group stay on their own; so do, during a
     * mini-game, the columns of a group linked to no page. A practice round counts as a mini-game.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "podium_shared_unchanged", tickLimit = 100)
    public void outOfAMiniGameEveryColumnIsOnItsOwn(TestContext context) {
        BlockPos a1 = column(context, 1, 3, 2), a2 = column(context, 2, 3, 2), low = column(context, 3, 3, 1);
        // Another group, linked to no page
        BlockPos free1 = column(context, 6, 6, 2), free2 = column(context, 7, 6, 2);
        ServerPlayerEntity p1 = player(context, "one", 1.5, 1, 5.5), p2 = player(context, "two", 2.5, 1, 5.5);
        try {
            ItemStack page = page(context, a1);
            PodiumGroup group = group(context, a1);
            context.assertTrue(!Podiums.sharesPlaces(group), "linked to a page nobody plays: every column on its own");
            context.assertTrue(register(context, a1, p1), "p1 takes a column");
            context.assertTrue(holder(context, a2) == null && p1.getUuid().equals(figure(context, a1)), "only that column, with his figure");
            context.assertTrue(register(context, a2, p2), "p2 takes the other column of the same height");
            context.assertTrue(p1.getUuid().equals(holder(context, a1)) && p2.getUuid().equals(holder(context, a2)), "two winners of the same place");
            context.assertEquals(group(context, a1).highestFree().bottom(), context.getAbsolutePos(low), "the next free column");
            master(context, a2).setSignal(PodiumSignal.CLEAR);
            context.setBlockState(a2.north(), Blocks.REDSTONE_BLOCK);
            context.assertTrue(holder(context, a2) == null && p1.getUuid().equals(holder(context, a1)), "« empty this podium » empties that column only");
            context.setBlockState(a2.north(), Blocks.AIR);
            master(context, a2).setSignal(PodiumSignal.REGISTER);

            // A practice round on the page: one place
            Played played = played(context, page, null, MiniGamePartyStep.Phase.PRACTICE, p1, p2);
            context.assertTrue(Podiums.sharesPlaces(group(context, a1)), "a practice round is a mini-game");
            context.assertTrue(register(context, a2, p2), "p2 takes the place");
            context.assertTrue(p2.getUuid().equals(holder(context, a1)) && p2.getUuid().equals(holder(context, a2)), "the whole place, p1 out of it");

            // Meanwhile a group linked to no page is unchanged
            context.assertTrue(!Podiums.sharesPlaces(group(context, free1)), "no page: no shared place");
            context.assertTrue(register(context, free1, p1) && holder(context, free2) == null, "a column on its own");
            context.assertTrue(register(context, free2, p2) && p1.getUuid().equals(holder(context, free1)), "its neighbour of the same height too");
            context.assertTrue(played.step().isPractice(), "still the practice round");
        } finally {
            cleanUp(context, p1, p2);
        }
        context.complete();
    }
}
