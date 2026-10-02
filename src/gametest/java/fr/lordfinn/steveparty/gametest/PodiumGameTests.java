package fr.lordfinn.steveparty.gametest;

import com.mojang.authlib.GameProfile;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlock;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBlock;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleNetwork;
import fr.lordfinn.steveparty.blocks.custom.PartyController.MiniGameGains;
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
import fr.lordfinn.steveparty.board.WrenchActions;
import fr.lordfinn.steveparty.commands.PodiumCommand;
import fr.lordfinn.steveparty.components.TileStampComponent;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePodiumLink;
import fr.lordfinn.steveparty.minigame.MiniGameResults;
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
import net.minecraft.stat.Stats;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
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
 * The podiums record the places of a mini-game: the heights give the places, players register by sneaking or
 * clicking (one podium per player or team, a held podium can be taken), redstone registers the nearest player or
 * fills the group in order, a per-player goal pole fills it too; everything resets together; the mini-game ends by
 * itself and the party pays the gains of each place.
 */
public class PodiumGameTests implements FabricGameTest {
    private static final AtomicInteger SERIAL = new AtomicInteger();

    // ------------------------------------------------------------------ helpers

    /** Like /setblock: the block takes its place in the column, its neighbours follow. */
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

    private static @Nullable UUID occupant(TestContext context, BlockPos bottom) {
        PodiumOccupant occupant = master(context, bottom).getOccupant();
        return occupant == null ? null : occupant.player();
    }

    private static PodiumGroup group(TestContext context, BlockPos pos) {
        return PodiumGroup.of(context.getWorld(), context.getAbsolutePos(pos));
    }

    /** A connected player with a name of its own (the goal poles count by name), standing at a relative position. */
    private static ServerPlayerEntity player(TestContext context, String name, double x, double y, double z) {
        ServerWorld world = context.getWorld();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "p" + SERIAL.incrementAndGet() + name);
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

    /** Standing on the top of a column. */
    private static void standOn(TestContext context, ServerPlayerEntity player, BlockPos bottom) {
        BlockPos abs = context.getAbsolutePos(bottom);
        double height = PodiumBlock.heightOf(context.getWorld(), abs) / 2.0;
        player.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY() + height, abs.getZ() + 0.5, 0, 0);
    }

    private static void remove(TestContext context, ServerPlayerEntity... players) {
        for (ServerPlayerEntity player : players) context.getWorld().getServer().getPlayerManager().remove(player);
    }

    private static int coins(PartyControllerEntity controller, ServerPlayerEntity player) {
        return PartyCurrency.count(player.getInventory(), controller.getCurrency(PartyCurrency.COIN));
    }

    private static int stars(PartyControllerEntity controller, ServerPlayerEntity player) {
        return PartyCurrency.count(player.getInventory(), controller.getCurrency(PartyCurrency.STAR));
    }

    private record Played(PartyControllerEntity controller, MiniGamePartyStep step, PartyData data, ItemStack page, UUID pageId) {
    }

    private static final BlockPos CONTROLLER = new BlockPos(0, 1, 7);

    /** A mini-game page with the podiums linked to it. */
    private static ItemStack page(TestContext context, BlockPos... podiums) {
        MinecraftServer server = context.getWorld().getServer();
        ItemStack stack = new ItemStack(ModItems.MINI_GAME_PAGE);
        UUID id = MiniGamePages.ensureId(stack);
        MiniGamePages.update(server, MiniGamePages.get(server, id).withTexts("Podium test " + SERIAL.incrementAndGet(), ""));
        for (BlockPos podium : podiums) {
            MiniGamePages.addPodiumLink(server, id, new MiniGamePodiumLink(
                    GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(podium)), MiniGamePodiumLink.Kind.PODIUM));
        }
        return stack;
    }

    /** A party controller whose mini-game, on {@code pageStack}, is being played by {@code participants}. */
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
        return new Played(controller, step, data, pageStack, MiniGamePages.idOf(pageStack));
    }

    private static void cleanUp(TestContext context, ServerPlayerEntity... players) {
        remove(context, players);
        if (context.getBlockState(CONTROLLER).isOf(ModBlocks.PARTY_CONTROLLER)) context.removeBlock(CONTROLLER);
    }

    private static Set<UUID> set(ServerPlayerEntity... players) {
        Set<UUID> set = new LinkedHashSet<>();
        for (ServerPlayerEntity player : players) set.add(player.getUuid());
        return set;
    }

    // ------------------------------------------------------------------ the column

    /** Stacked podiums make one column: the bottom block keeps the settings and the banner, the comparator says taken. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void podiumColumn(TestContext context) {
        BlockPos bottom = new BlockPos(2, 1, 2);
        context.setBlockState(bottom, ModBlocks.GOLD_PODIUM.getDefaultState().with(PodiumBlock.FACING, Direction.EAST).with(PodiumBlock.FULL, true));
        context.assertTrue(context.getBlockState(bottom).get(PodiumBlock.TOP), "alone: its own top");
        context.setBlockState(bottom.up(), ModBlocks.GOLD_PODIUM.getDefaultState().with(PodiumBlock.FACING, Direction.EAST));
        context.assertFalse(context.getBlockState(bottom).get(PodiumBlock.TOP), "covered: no longer the top");
        context.assertTrue(context.getBlockState(bottom.up()).get(PodiumBlock.TOP), "the slab is the top");
        BlockPos absBottom = context.getAbsolutePos(bottom);
        context.assertEquals(PodiumBlock.topOf(context.getWorld(), absBottom), absBottom.up(), "top of the column");
        context.assertEquals(PodiumBlock.heightOf(context.getWorld(), absBottom.up()), 3, "a block and a slab: three half blocks");
        PodiumBlockEntity master = context.getBlockEntity(bottom);
        context.assertTrue(PodiumBlock.master(context.getWorld(), absBottom.up()) == master, "the bottom block keeps the settings");
        TileStampComponent stamp = TileStampComponent.of(new byte[256], DyeColor.BLUE);
        master.setBannerStamp(stamp);
        context.assertEquals(PodiumBlock.master(context.getWorld(), absBottom.up()).getBannerStamp(), stamp, "banner of the column");

        // The comparator of any block of the column: 15 while someone is registered on it
        context.assertEquals(context.getBlockState(bottom.up()).getComparatorOutput(context.getWorld(), absBottom.up()), 0, "nobody");
        master.setOccupant(new PodiumOccupant(UUID.randomUUID(), "Alex", -1, 1, 0));
        context.assertEquals(context.getBlockState(bottom.up()).getComparatorOutput(context.getWorld(), absBottom.up()), 15, "taken: top");
        context.assertEquals(context.getBlockState(bottom).getComparatorOutput(context.getWorld(), absBottom), 15, "taken: bottom");

        // Saved and loaded with the column
        NbtCompound saved = master.createNbt(context.getWorld().getRegistryManager());
        master.setOccupant(null);
        master.setSignal(PodiumSignal.FILL);
        master.read(saved, context.getWorld().getRegistryManager());
        context.assertTrue(master.getOccupant() != null && master.getOccupant().name().equals("Alex"), "the registered player is saved");
        context.assertEquals(master.getSignal(), PodiumSignal.REGISTER, "what a signal does is saved");
        context.removeBlock(bottom.up());
        context.assertTrue(context.getBlockState(bottom).get(PodiumBlock.TOP), "uncovered: the top again");
        context.complete();
    }

    /**
     * A column looks like one piece: the plinth at its foot only, and its banner hanging over one block of height from
     * its top (across the top slab and the block under it), whatever it is made of; a slab alone has a label.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void podiumColumnBanner(TestContext context) {
        BlockPos bottom = new BlockPos(2, 1, 2);
        BlockState slab = ModBlocks.GOLD_PODIUM.getDefaultState();
        BlockState full = ModBlocks.SILVER_PODIUM.getDefaultState().with(PodiumBlock.FULL, true);

        place(context, bottom, slab);
        context.assertFalse(PodiumBlock.bannerHangs(context.getBlockState(bottom)), "a slab alone: a label");
        place(context, bottom, full);
        context.assertTrue(PodiumBlock.bannerHangs(context.getBlockState(bottom)), "a full block: the banner hangs");
        context.assertTrue(context.getBlockState(bottom).get(PodiumBlock.BASE), "alone: its plinth");

        place(context, bottom.up(), slab);
        BlockState top = context.getBlockState(bottom.up());
        context.assertTrue(top.get(PodiumBlock.TOP) && !top.get(PodiumBlock.BASE), "the slab rests on the column");
        context.assertTrue(PodiumBlock.bannerHangs(top), "a slab on a full block: the banner hangs");
        context.assertEquals(context.getBlockState(bottom).get(PodiumBlock.BANNER_TAIL), PodiumBlock.BannerTail.GOLD, "lower half of the gold banner");
        context.assertTrue(context.getBlockState(bottom).get(PodiumBlock.BASE), "the foot of the column keeps its plinth");

        place(context, bottom.up(), full);
        context.assertEquals(context.getBlockState(bottom).get(PodiumBlock.BANNER_TAIL), PodiumBlock.BannerTail.NONE, "no banner under a full block");
        context.assertFalse(context.getBlockState(bottom.up()).get(PodiumBlock.BASE), "no plinth on a block resting on a podium");

        place(context, bottom.up(2), slab);
        context.assertEquals(context.getBlockState(bottom.up()).get(PodiumBlock.BANNER_TAIL), PodiumBlock.BannerTail.GOLD, "under the top slab");
        context.assertEquals(context.getBlockState(bottom).get(PodiumBlock.BANNER_TAIL), PodiumBlock.BannerTail.NONE, "not lower");
        context.assertFalse(context.getBlockState(bottom.up()).get(PodiumBlock.TOP), "covered");
        context.removeBlock(bottom.up(2));
        context.assertEquals(context.getBlockState(bottom.up()).get(PodiumBlock.BANNER_TAIL), PodiumBlock.BannerTail.NONE, "slab removed");
        context.assertTrue(context.getBlockState(bottom.up()).get(PodiumBlock.TOP), "the top again");

        // A slab under another podium leaves a gap: the one above stands on its own
        place(context, bottom.up(), slab);
        place(context, bottom.up(2), slab);
        context.assertTrue(context.getBlockState(bottom.up(2)).get(PodiumBlock.BASE), "nothing full under it");
        context.assertFalse(PodiumBlock.bannerHangs(context.getBlockState(bottom.up(2))), "a label");
        context.complete();
    }

    // ------------------------------------------------------------------ groups and places

    /**
     * The columns touching each other form a group; the tallest is the 1st place, the next height the 2nd, equal
     * heights share the place. The columns linked to the same page join the group, however far.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void heightsGiveThePlaces(TestContext context) {
        BlockPos tall = column(context, 1, 2, 4), mid1 = column(context, 2, 2, 2), mid2 = column(context, 1, 3, 2),
                low = column(context, 3, 2, 1), apart = column(context, 6, 6, 6);
        PodiumGroup group = group(context, mid1.up(0));
        context.assertEquals(group.columns().size(), 4, "the four columns touching each other");
        context.assertEquals(group.placeCount(), 3, "three heights: three places");
        context.assertEquals(group.columns().getFirst().bottom(), context.getAbsolutePos(tall), "the tallest first");
        List<Integer> places = new ArrayList<>();
        for (BlockPos bottom : List.of(tall, mid1, mid2, low)) {
            PodiumGroup.Column column = group.columnAt(context.getWorld(), context.getAbsolutePos(bottom));
            places.add(column == null ? -1 : group.placeOf(column));
        }
        context.assertEquals(places, List.of(1, 2, 2, 3), "tallest 1st, the two equal ones 2nd, the slab 3rd");
        context.assertTrue(group.columnAt(context.getWorld(), context.getAbsolutePos(apart)) == null, "a column apart is not of the group");
        context.assertEquals(group(context, tall.up()).columns().size(), 4, "from any block of any column");
        context.assertTrue(group(context, new BlockPos(5, 1, 5)).isEmpty(), "no podium: no group");
        context.assertTrue(group.pages().isEmpty(), "linked to no page");

        // Linked to the same page: one group, and the column apart is now the tallest
        ItemStack pageStack = page(context, apart, low);
        MiniGamePageData page = MiniGamePages.of(context.getWorld().getServer(), pageStack);
        PodiumGroup linked = PodiumGroup.ofPage(context.getWorld().getServer(), page);
        context.assertTrue(linked != null && linked.columns().size() == 5, "the linked columns and those touching them");
        context.assertEquals(linked.columns().getFirst().bottom(), context.getAbsolutePos(apart), "the tallest of all first");
        context.assertEquals(linked.placeCount(), 4, "four heights");
        context.assertEquals(group(context, mid2).columns().size(), 5, "found from a column that only touches a linked one");
        context.assertEquals(linked.pages(), Set.of(page.id()), "the group knows its page");
        context.assertTrue(page.hasPodium() && !MiniGamePageData.empty(UUID.randomUUID()).hasPodium(), "the page says whether it has podiums");
        context.complete();
    }

    /** A page in hand links a podium by its column (again: unlinks it) and a goal pole base as a counter. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aPageLinksPodiumsAndCounters(TestContext context) {
        ServerPlayerEntity player = player(context, "link", 0.5, 1, 0.5);
        try {
            MinecraftServer server = context.getWorld().getServer();
            BlockPos podium = column(context, 2, 2, 4), basePos = new BlockPos(5, 1, 5);
            context.setBlockState(basePos, ModBlocks.GOAL_POLE_BASE.getDefaultState().with(GoalPoleBaseBlock.FACING, Direction.NORTH));
            GoalPoleNetwork.processPending();
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.MINI_GAME_PAGE));
            ServerWorld world = context.getWorld();
            context.assertTrue(Podiums.clickLink(player, Hand.MAIN_HAND, world, context.getAbsolutePos(podium.up()), MiniGamePodiumLink.Kind.PODIUM), "linked");
            UUID id = MiniGamePages.idOf(player.getMainHandStack());
            context.assertTrue(id != null, "the page got its id");
            MiniGamePageData page = MiniGamePages.get(server, id);
            context.assertEquals(page.podiumLinks(), List.of(new MiniGamePodiumLink(
                    GlobalPos.create(world.getRegistryKey(), context.getAbsolutePos(podium)), MiniGamePodiumLink.Kind.PODIUM)), "linked by its bottom block");
            context.assertTrue(Podiums.clickLink(player, Hand.MAIN_HAND, world, context.getAbsolutePos(basePos), MiniGamePodiumLink.Kind.COUNTER), "counter linked");
            GoalPoleBaseBlockEntity base = context.getBlockEntity(basePos);
            List<PodiumGroup> fed = Podiums.groupsOf(base);
            context.assertTrue(fed.size() == 1 && fed.getFirst().columns().size() == 1, "the base is linked to the page's podiums");
            context.assertEquals(Podiums.basesOf(group(context, podium)), Set.of(base), "and the podiums to the base");
            // Reading survives saving
            MiniGamePageData read = MiniGamePageData.fromNbt(MiniGamePages.get(server, id).toNbt());
            context.assertEquals(read, MiniGamePages.get(server, id), "podium links are saved");
            // Adventure: nothing changes
            player.changeGameMode(GameMode.ADVENTURE);
            context.assertTrue(!Podiums.clickLink(player, Hand.MAIN_HAND, world, context.getAbsolutePos(podium), MiniGamePodiumLink.Kind.PODIUM), "not allowed");
            player.changeGameMode(GameMode.CREATIVE);
            context.assertTrue(Podiums.clickLink(player, Hand.MAIN_HAND, world, context.getAbsolutePos(podium), MiniGamePodiumLink.Kind.PODIUM), "clicked again");
            context.assertTrue(!MiniGamePages.get(server, id).hasPodium(), "unlinked, whatever block of the column was clicked");
            context.assertEquals(MiniGamePages.get(server, id).podiumLinks().size(), 1, "the counter stays");
            context.setBlockState(basePos, Blocks.AIR);
        } finally {
            remove(context, player);
        }
        context.complete();
    }

    // ------------------------------------------------------------------ registering

    /** A click registers, another one unregisters; a podium held by someone else is taken; one podium per player. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void clickRegistersTogglesAndTakesOver(TestContext context) {
        ServerPlayerEntity alex = player(context, "alex", 0.5, 1, 0.5), sam = player(context, "sam", 0.5, 1, 1.5);
        BlockPos first = column(context, 3, 3, 2), second = column(context, 4, 3, 1);
        ServerWorld world = context.getWorld();
        BlockPos abs = context.getAbsolutePos(first);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(abs), Direction.UP, abs, false);
        context.assertTrue(context.getBlockState(first).onUse(world, alex, hit).isAccepted(), "a right click on the podium");
        context.assertEquals(occupant(context, first), alex.getUuid(), "registered");
        context.assertEquals(master(context, first).getOccupant().place(), 1, "on the first place");
        context.assertTrue(!Podiums.toggle(alex, world, abs), "the same gesture twice in a moment is one gesture");
        context.waitAndRun(8, () -> {
            try {
                context.assertTrue(Podiums.toggle(alex, world, abs), "clicked again");
                context.assertTrue(occupant(context, first) == null, "unregistered");
                context.assertTrue(Podiums.toggle(sam, world, abs), "another player");
                context.assertEquals(occupant(context, first), sam.getUuid(), "sam holds it");
            } catch (RuntimeException e) {
                remove(context, alex, sam);
                throw e;
            }
            context.waitAndRun(8, () -> {
                try {
                    context.assertTrue(Podiums.toggle(alex, world, abs), "a held podium can be taken");
                    context.assertEquals(occupant(context, first), alex.getUuid(), "alex took it");
                    // One podium per player: registering on another one of the group leaves the first
                    PodiumGroup group = group(context, first);
                    context.assertTrue(Podiums.register(group, group.columnAt(world, context.getAbsolutePos(second)), alex), "on the other column");
                    context.assertTrue(occupant(context, first) == null, "left the first");
                    context.assertEquals(occupant(context, second), alex.getUuid(), "on the second");
                    context.assertEquals(master(context, second).getOccupant().place(), 2, "the slab is the second place");
                } finally {
                    remove(context, alex, sam);
                }
                context.complete();
            });
        });
    }

    /** Sneaking on the top of a podium registers; sneaking again unregisters; staying crouched changes nothing. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void sneakingOnItRegisters(TestContext context) {
        ServerPlayerEntity player = player(context, "sneak", 0.5, 1, 0.5);
        BlockPos podium = column(context, 3, 3, 3);
        standOn(context, player, podium);
        context.waitAndRun(6, () -> {
            try {
                context.assertTrue(occupant(context, podium) == null, "standing on it is not registering");
                player.setSneaking(true);
            } catch (RuntimeException e) {
                remove(context, player);
                throw e;
            }
            context.waitAndRun(10, () -> {
                try {
                    context.assertEquals(occupant(context, podium), player.getUuid(), "registered by sneaking, and still crouched");
                    player.setSneaking(false);
                } catch (RuntimeException e) {
                    remove(context, player);
                    throw e;
                }
                context.waitAndRun(6, () -> {
                    player.setSneaking(true);
                    context.waitAndRun(6, () -> {
                        try {
                            context.assertTrue(occupant(context, podium) == null, "sneaking again unregisters");
                        } finally {
                            remove(context, player);
                        }
                        context.complete();
                    });
                });
            });
        });
    }

    /**
     * In a team mini-game a podium stands for the team: a team-mate registering elsewhere moves the team. Only the
     * players of the mini-game register on its podiums.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "podium_party_teams", tickLimit = 100)
    public void onePodiumPerTeam(TestContext context) {
        ServerPlayerEntity a1 = player(context, "a1", 0.5, 1, 0.5), a2 = player(context, "a2", 1.5, 1, 0.5),
                b1 = player(context, "b1", 2.5, 1, 0.5), watcher = player(context, "w", 3.5, 1, 0.5);
        try {
            BlockPos first = column(context, 3, 3, 3), second = column(context, 4, 3, 2), third = column(context, 5, 3, 1);
            Played played = played(context, page(context, first), new TeamDisposition(set(a1, a2), set(b1)), MiniGamePartyStep.Phase.PLAYING, a1, a2, b1);
            ServerWorld world = context.getWorld();
            PodiumGroup group = group(context, first);
            context.assertTrue(Podiums.played(group) != null && Podiums.played(group).miniGame() == played.step(), "the group knows its mini-game");
            context.assertTrue(!Podiums.toggle(watcher, world, context.getAbsolutePos(first)), "not a player of the mini-game");
            context.assertTrue(occupant(context, first) == null, "refused");
            context.assertTrue(Podiums.register(group, group.columnAt(world, context.getAbsolutePos(second)), a1), "team A on the second place");
            context.assertEquals(master(context, second).getOccupant().team(), 0, "the podium shows the team");
            context.assertTrue(Podiums.register(group, group.columnAt(world, context.getAbsolutePos(third)), a2), "a team-mate on another podium");
            context.assertTrue(occupant(context, second) == null, "the team left its podium");
            context.assertEquals(occupant(context, third), a2.getUuid(), "and stands on the new one");
            context.assertTrue(played.step().isPlaying(), "one team out of two: the mini-game goes on");
            context.assertEquals(played.step().getEndSeconds(), 0, "the first place is free: no countdown");
            // The other team: every team has a place, the mini-game ends
            context.assertTrue(Podiums.register(group, group.columnAt(world, context.getAbsolutePos(first)), b1), "team B on the first place");
            context.assertEquals(played.step().getPhase(), MiniGamePartyStep.Phase.FINISHED, "every team placed: over");
            context.assertEquals(played.step().getPlaces(), Map.of(a1.getUuid(), 3, a2.getUuid(), 3, b1.getUuid(), 1), "the place of a team is its members'");
            context.assertEquals(played.step().getWinners(), List.of(b1.getUuid()), "the winners are the first place");
            context.assertEquals(coins(played.controller(), b1), 10, "1st: 10 coins");
            context.assertEquals(coins(played.controller(), a1), 3, "3rd: 3 coins, for each member");
            context.assertEquals(coins(played.controller(), a2), 3, "3rd: 3 coins, for each member");
        } finally {
            cleanUp(context, a1, a2, b1, watcher);
        }
        context.complete();
    }

    // ------------------------------------------------------------------ redstone

    /**
     * A pulse into any block of a column does what the Wrench set: registers the nearest player of the mini-game on
     * it, empties it, or resets the group. A podium next to it is not a signal.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "podium_party_redstone", tickLimit = 100)
    public void aPulseRegistersTheNearestPlayer(TestContext context) {
        BlockPos first = column(context, 3, 3, 4), second = column(context, 4, 3, 2), third = column(context, 5, 3, 1);
        ServerPlayerEntity near = player(context, "near", 3.5, 1, 5.5), far = player(context, "far", 7.5, 1, 7.5),
                other = player(context, "other", 0.5, 1, 0.5), watcher = player(context, "w", 3.5, 3, 3.5);
        try {
            Played played = played(context, page(context, second), null, MiniGamePartyStep.Phase.PLAYING, near, far, other);
            // Into the upper block of the tallest column
            context.setBlockState(first.up().north(), Blocks.REDSTONE_BLOCK);
            context.assertEquals(occupant(context, first), near.getUuid(), "the nearest player of the mini-game (not the watcher standing on it)");
            context.assertTrue(occupant(context, second) == null && occupant(context, third) == null, "the podiums next to it received nothing");
            context.assertEquals(played.step().getEndSeconds(), MiniGamePartyStep.END_DELAY_SECONDS, "the first place is taken: the end is counted down");
            context.setBlockState(first.up().north(), Blocks.AIR);
            // The Wrench: what a pulse does
            context.assertEquals(master(context, first).getSignal(), PodiumSignal.REGISTER, "registers by default");
            Podiums.cycleSignal(near, context.getWorld(), context.getAbsolutePos(first.up()));
            Podiums.cycleSignal(near, context.getWorld(), context.getAbsolutePos(first));
            context.assertEquals(master(context, first).getSignal(), PodiumSignal.CLEAR, "register, fill, then empty");
            context.setBlockState(first.north(), Blocks.REDSTONE_BLOCK);
            context.assertTrue(occupant(context, first) == null, "a pulse empties this podium");
            context.assertEquals(played.step().getEndSeconds(), 0, "the first place is free again: the countdown is called off");
            context.setBlockState(first.north(), Blocks.AIR);
            // Reset the whole group
            master(context, second).setOccupant(new PodiumOccupant(far.getUuid(), "far", -1, 2, 0));
            master(context, third).setOccupant(new PodiumOccupant(other.getUuid(), "other", -1, 3, 0));
            master(context, first).setSignal(PodiumSignal.RESET);
            context.setBlockState(first.north(), Blocks.REDSTONE_BLOCK);
            context.assertTrue(occupant(context, second) == null && occupant(context, third) == null, "a pulse resets the group");
            context.assertTrue(played.step().isPlaying(), "the mini-game goes on");
        } finally {
            cleanUp(context, near, far, other, watcher);
        }
        context.complete();
    }

    /** Out of a party a pulse registers the nearest player around, whoever it is. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aPulseRegistersAnyoneOutOfAParty(TestContext context) {
        BlockPos podium = column(context, 3, 3, 2);
        ServerPlayerEntity player = player(context, "free", 3.5, 2, 3.5);
        try {
            PodiumGroup group = group(context, podium);
            ServerPlayerEntity expected = Podiums.nearest(group, group.columns().getFirst(), false);
            context.assertTrue(expected != null, "someone is near");
            context.setBlockState(podium.west(), Blocks.REDSTONE_BLOCK);
            context.assertEquals(occupant(context, podium), expected.getUuid(), "the nearest player is registered");
        } finally {
            remove(context, player);
        }
        context.complete();
    }

    /** « Fill » columns: each pulse gives the nearest player without a place the highest free place, in order. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "podium_party_fill", tickLimit = 100)
    public void pulsesFillTheGroupInOrder(TestContext context) {
        BlockPos first = column(context, 3, 3, 3), second = column(context, 4, 3, 2), third = column(context, 5, 3, 1);
        ServerPlayerEntity p1 = player(context, "one", 5.5, 1, 4.5), p2 = player(context, "two", 5.5, 1, 6.5),
                p3 = player(context, "three", 1.5, 1, 7.5), p4 = player(context, "four", 0.5, 1, 0.5);
        try {
            Played played = played(context, page(context, first), null, MiniGamePartyStep.Phase.PLAYING, p1, p2, p3, p4);
            // The entry is the lowest column: it stays a place like the others
            master(context, third).setSignal(PodiumSignal.FILL);
            BlockPos source = third.east();
            context.setBlockState(source, Blocks.REDSTONE_BLOCK);
            context.assertEquals(occupant(context, first), p1.getUuid(), "the nearest takes the highest place");
            context.assertTrue(occupant(context, second) == null && occupant(context, third) == null, "one pulse, one winner");
            context.setBlockState(source, Blocks.AIR);
            context.setBlockState(source, Blocks.REDSTONE_BLOCK);
            context.assertEquals(occupant(context, second), p2.getUuid(), "the next one takes the next place (p1 is already placed)");
            context.assertTrue(played.step().isPlaying(), "a place and a player are left");
            context.setBlockState(source, Blocks.AIR);
            context.setBlockState(source, Blocks.REDSTONE_BLOCK);
            context.assertEquals(occupant(context, third), p3.getUuid(), "then the third");
            context.assertEquals(played.step().getPhase(), MiniGamePartyStep.Phase.FINISHED, "every place is taken: the mini-game is over");
            context.assertEquals(played.step().getPlaces(), Map.of(p1.getUuid(), 1, p2.getUuid(), 2, p3.getUuid(), 3, p4.getUuid(), 0),
                    "first come, best place; the last one is a participant");
        } finally {
            cleanUp(context, p1, p2, p3, p4);
        }
        context.complete();
    }

    // ------------------------------------------------------------------ goal pole

    private static final BlockPos BASE = new BlockPos(2, 1, 2);

    /** A goal pole base counting jumps, with a pole whose goal is each player's own: 10. */
    private static GoalPoleBaseBlockEntity jumpCounter(TestContext context) {
        context.setBlockState(BASE, ModBlocks.GOAL_POLE_BASE.getDefaultState().with(GoalPoleBaseBlock.FACING, Direction.NORTH));
        context.setBlockState(BASE.up(), ModBlocks.GOAL_POLE.getDefaultState().with(GoalPoleBlock.ON_BASE, true).with(GoalPoleBlock.TOP, true));
        GoalPoleNetwork.processPending();
        GoalPoleBaseBlockEntity base = context.getBlockEntity(BASE);
        base.setPlayers(GoalPoleBaseBlockEntity.Players.ALL, 16);
        base.setSource(GoalPoleBaseBlockEntity.Source.CRITERION, "minecraft.custom:minecraft.jump");
        GoalPoleBlockEntity pole = context.getBlockEntity(BASE.up());
        pole.applyGoal(GoalPoleBlockEntity.Comparator.GREATER_OR_EQUAL, 10, false);
        pole.applyPerPlayer(true);
        return base;
    }

    private static void jump(ServerPlayerEntity player, int times) {
        for (int i = 0; i < times; i++) player.incrementStat(Stats.JUMP);
    }

    /**
     * « The first player to reach 10 jumps is 1st, the second 2nd, the third 3rd »: a base counting jumps, a pole with
     * a per-player goal, and a podium staircase touching the base.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void tenJumpsGiveThePlacesInOrder(TestContext context) {
        ServerPlayerEntity j1 = player(context, "j1", 0.5, 1, 0.5), j2 = player(context, "j2", 1.5, 1, 0.5), j3 = player(context, "j3", 2.5, 1, 0.5);
        try {
            GoalPoleBaseBlockEntity base = jumpCounter(context);
            BlockPos first = column(context, 3, 2, 3), second = column(context, 4, 2, 2), third = column(context, 5, 2, 1);
            GoalPoleBlockEntity pole = context.getBlockEntity(BASE.up());
            context.assertTrue(pole.isPerPlayer() && base.getSource() == GoalPoleBaseBlockEntity.Source.CRITERION, "a per-player goal on a jump counter");
            context.assertEquals(Podiums.groupsOf(base).size(), 1, "the base touches the staircase: linked to its group");

            jump(j2, 9);
            jump(j1, 9);
            context.assertEquals(base.getTotal(), 18L, "18 jumps counted");
            context.assertTrue(occupant(context, first) == null && pole.getReached().isEmpty(), "18 jumps in all, but nobody has 10 of his own");
            context.assertEquals(pole.getRedstoneOutput(), 0, "no signal");
            jump(j2, 1);
            context.assertEquals(occupant(context, first), j2.getUuid(), "the first to reach 10 jumps is 1st");
            context.assertEquals(pole.getRedstoneOutput(), 15, "the pole fires: a comparator pulse");
            jump(j2, 5);
            context.assertTrue(occupant(context, second) == null, "a player already placed is not placed again");
            jump(j1, 1);
            context.assertEquals(occupant(context, second), j1.getUuid(), "the second to reach 10 is 2nd");
            jump(j3, 12);
            context.assertEquals(occupant(context, third), j3.getUuid(), "the third is 3rd");
            context.assertEquals(pole.getReached().size(), 3, "fired once per player");

            // Resetting the base empties the podiums; the goals can be reached again
            NbtCompound saved = pole.createNbt(context.getWorld().getRegistryManager());
            context.assertTrue(saved.getBoolean("PerPlayer") && saved.getList("Reached", 8).size() == 3, "the per-player goal is saved");
            base.reset();
            context.assertEquals(base.getTotal(), 0L, "points back to 0");
            context.assertTrue(occupant(context, first) == null && occupant(context, second) == null && occupant(context, third) == null,
                    "the podiums linked to the base are emptied with it");
            context.assertTrue(pole.getReached().isEmpty(), "the goals can be reached again");
            jump(j3, 10);
            context.assertEquals(occupant(context, first), j3.getUuid(), "a new race: j3 is first this time");
        } catch (RuntimeException e) {
            context.setBlockState(BASE, Blocks.AIR);
            remove(context, j1, j2, j3);
            throw e;
        }
        context.waitAndRun(GoalPoleBlockEntity.PLAYER_GOAL_PULSE_TICKS + 2, () -> {
            try {
                context.assertEquals(((GoalPoleBlockEntity) context.getBlockEntity(BASE.up())).getRedstoneOutput(), 0, "the pulse is over");
            } finally {
                context.setBlockState(BASE, Blocks.AIR);
                remove(context, j1, j2, j3);
            }
            context.complete();
        });
    }

    // ------------------------------------------------------------------ resets

    /**
     * The reset of a group empties its columns and puts its goal pole bases back to 0: with the Wrench (sneak + right
     * click), with the command, with the « reset » redstone mode of the base. The coins are not touched.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void resetsEmptyThePodiumsAndTheirCounters(TestContext context) {
        ServerPlayerEntity player = player(context, "reset", 0.5, 1, 0.5);
        try {
            GoalPoleBaseBlockEntity base = jumpCounter(context);
            BlockPos first = column(context, 3, 2, 2), second = column(context, 4, 2, 1);
            ServerWorld world = context.getWorld();
            player.getInventory().insertStack(new ItemStack(Items.EMERALD, 7));
            Runnable fill = () -> {
                master(context, first).setOccupant(new PodiumOccupant(player.getUuid(), "reset", -1, 1, 0));
                master(context, second).setOccupant(new PodiumOccupant(UUID.randomUUID(), "ghost", -1, 2, 0));
                base.credit("ghost", 4, null);
            };
            Runnable assertReset = () -> {
                context.assertTrue(occupant(context, first) == null && occupant(context, second) == null, "the columns are emptied");
                context.assertEquals(base.getTotal(), 0L, "the linked base is back to 0");
            };

            // The Wrench: sneak + right click
            fill.run();
            player.setSneaking(true);
            WrenchActions.useOnBlock(player, new ItemStack(ModItems.WRENCH), world, context.getAbsolutePos(second));
            assertReset.run();
            player.setSneaking(false);

            // Adventure: the Wrench resets nothing
            fill.run();
            player.changeGameMode(GameMode.ADVENTURE);
            Podiums.wrenchReset(player, world, context.getAbsolutePos(first));
            context.assertTrue(occupant(context, first) != null, "not allowed to reset");
            player.changeGameMode(GameMode.CREATIVE);

            // The command, run next to the podiums
            int columns = PodiumCommand.reset(world.getServer().getCommandSource().withWorld(world)
                    .withPosition(Vec3d.ofCenter(context.getAbsolutePos(first))));
            context.assertEquals(columns, 2, "the command resets the nearest group");
            assertReset.run();

            // The base's redstone mode: a pulse at its back resets it, and the podiums with it
            fill.run();
            base.setRedstoneMode(GoalPoleBaseBlockEntity.RedstoneMode.RESET_WHEN_POWERED);
            context.assertTrue(base.isActive(), "it always counts");
            context.setBlockState(BASE.south(), Blocks.REDSTONE_BLOCK);
            assertReset.run();
            context.assertTrue(base.isActive() && base.credit("ghost", 1, null), "it still counts while powered");
            context.setBlockState(BASE.south(), Blocks.AIR);
            context.assertEquals(base.getTotal(), 1L, "the falling edge resets nothing");

            context.assertEquals(player.getInventory().count(Items.EMERALD), 7, "resetting never touches the coins");
            context.setBlockState(BASE, Blocks.AIR);
        } finally {
            remove(context, player);
        }
        context.complete();
    }

    /** At the start of a mini-game its podiums are emptied and its counters go back to 0. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "podium_party_start", tickLimit = 100)
    public void aMiniGameStartsWithEmptyPodiums(TestContext context) {
        ServerPlayerEntity player = player(context, "start", 0.5, 1, 0.5);
        try {
            GoalPoleBaseBlockEntity base = jumpCounter(context);
            BlockPos first = column(context, 4, 4, 2), second = column(context, 5, 4, 1);
            ItemStack pageStack = page(context, first);
            MiniGamePages.addPodiumLink(context.getWorld().getServer(), MiniGamePages.idOf(pageStack), new MiniGamePodiumLink(
                    GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(BASE)), MiniGamePodiumLink.Kind.COUNTER));
            Played played = played(context, pageStack, null, MiniGamePartyStep.Phase.COUNTDOWN, player);
            master(context, first).setOccupant(new PodiumOccupant(UUID.randomUUID(), "old", -1, 1, 0));
            master(context, second).setOccupant(new PodiumOccupant(UUID.randomUUID(), "older", -1, 2, 0));
            base.credit("old", 6, null);
            played.step().depart(played.controller());
            context.assertTrue(played.step().isPlaying(), "the mini-game is being played");
            context.assertTrue(occupant(context, first) == null && occupant(context, second) == null, "last game's places are gone");
            context.assertEquals(base.getTotal(), 0L, "the page's counter is back to 0");
            context.setBlockState(BASE, Blocks.AIR);
        } finally {
            cleanUp(context, player);
        }
        context.complete();
    }

    // ------------------------------------------------------------------ the end of the mini-game

    /** Every player placed: the mini-game ends at once, even with places left. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "podium_party_all", tickLimit = 200)
    public void endsWhenEveryPlayerIsPlaced(TestContext context) {
        ServerPlayerEntity p1 = player(context, "e1", 0.5, 1, 0.5), p2 = player(context, "e2", 1.5, 1, 0.5);
        BlockPos first = column(context, 3, 3, 3), second = column(context, 4, 3, 2), third = column(context, 5, 3, 1);
        Played played;
        try {
            played = played(context, page(context, first), null, MiniGamePartyStep.Phase.PLAYING, p1, p2);
            ServerWorld world = context.getWorld();
            context.assertTrue(Podiums.toggle(p1, world, context.getAbsolutePos(third)), "p1 on the 3rd place");
            context.assertTrue(played.step().isPlaying() && played.step().getEndSeconds() == 0, "one player left, the first place free: it goes on");
            context.assertTrue(Podiums.toggle(p2, world, context.getAbsolutePos(second)), "p2 on the 2nd place");
            context.assertEquals(played.step().getPhase(), MiniGamePartyStep.Phase.FINISHED, "everyone has a place: over");
            context.assertEquals(played.step().getPlaces(), Map.of(p1.getUuid(), 3, p2.getUuid(), 2), "the places of the podiums");
            context.assertTrue(played.step().getWinners().isEmpty() && played.controller().getLastWinners().isEmpty(), "nobody took the first place: no winner");
            context.assertEquals(coins(played.controller(), p1), 3, "3rd place: 3 coins");
            context.assertEquals(coins(played.controller(), p2), 5, "2nd place: 5 coins");
            MiniGameResults results = played.step().getLastResults();
            context.assertTrue(results != null && results.rows().size() == 2 && results.rows().getFirst().place() == 2
                    && results.rows().getFirst().coins() == 5 && results.rows().getFirst().names().equals(List.of(p2.getGameProfile().getName())),
                    "the results card: the best place first, with what was paid");
        } catch (RuntimeException e) {
            cleanUp(context, p1, p2);
            throw e;
        }
        // The players go back and the party goes on by itself
        context.waitAndRun(MiniGamePartyStep.RETURN_DELAY_TICKS + 10, () -> {
            try {
                context.assertTrue(played.data().isAtEnd(), "the party went on");
            } finally {
                cleanUp(context, p1, p2);
            }
            context.complete();
        });
    }

    /**
     * The first place taken starts the countdown of the end; leaving it calls it off; taken again, the mini-game ends
     * when the countdown does, and those without a place are participants.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "podium_party_countdown", tickLimit = 300)
    public void endsFiveSecondsAfterTheFirstPlace(TestContext context) {
        ServerPlayerEntity p1 = player(context, "c1", 0.5, 1, 0.5), p2 = player(context, "c2", 1.5, 1, 0.5), p3 = player(context, "c3", 2.5, 1, 0.5);
        BlockPos first = column(context, 3, 3, 2), second = column(context, 4, 3, 1);
        Played played;
        ServerWorld world = context.getWorld();
        try {
            played = played(context, page(context, first), null, MiniGamePartyStep.Phase.PLAYING, p1, p2, p3);
            played.controller().setGains(played.controller().getGains().with(PartyCurrency.COIN, MiniGameGains.PARTICIPANTS, 2)
                    .with(PartyCurrency.STAR, 0, 1));
            context.assertTrue(Podiums.toggle(p1, world, context.getAbsolutePos(first)), "p1 takes the first place");
            context.assertEquals(played.step().getEndSeconds(), MiniGamePartyStep.END_DELAY_SECONDS, "the countdown starts");
            context.assertTrue(played.step().isPlaying(), "the others can still take a place");
        } catch (RuntimeException e) {
            cleanUp(context, p1, p2, p3);
            throw e;
        }
        context.waitAndRun(30, () -> {
            try {
                context.assertTrue(played.step().getEndSeconds() < MiniGamePartyStep.END_DELAY_SECONDS && played.step().getEndSeconds() > 0, "counting down");
                context.assertTrue(Podiums.toggle(p1, world, context.getAbsolutePos(first)), "p1 leaves the first place");
                context.assertEquals(played.step().getEndSeconds(), 0, "called off");
            } catch (RuntimeException e) {
                cleanUp(context, p1, p2, p3);
                throw e;
            }
            context.waitAndRun(MiniGamePartyStep.END_DELAY_SECONDS * 20 + 10, () -> {
                try {
                    context.assertTrue(played.step().isPlaying(), "no first place: the mini-game goes on");
                    context.assertTrue(Podiums.toggle(p2, world, context.getAbsolutePos(first)), "p2 takes the first place");
                    context.assertEquals(played.step().getEndSeconds(), MiniGamePartyStep.END_DELAY_SECONDS, "a new countdown");
                } catch (RuntimeException e) {
                    cleanUp(context, p1, p2, p3);
                    throw e;
                }
                context.waitAndRun(MiniGamePartyStep.END_DELAY_SECONDS * 20 - 15, () -> {
                    try {
                        context.assertTrue(played.step().isPlaying(), "not over before the 5 seconds");
                    } catch (RuntimeException e) {
                        cleanUp(context, p1, p2, p3);
                        throw e;
                    }
                    context.waitAndRun(30, () -> {
                        try {
                            context.assertEquals(played.step().getPhase(), MiniGamePartyStep.Phase.FINISHED, "over 5 seconds after the first place");
                            context.assertEquals(played.step().getPlaces(), Map.of(p1.getUuid(), 0, p2.getUuid(), 1, p3.getUuid(), 0), "the others are participants");
                            context.assertEquals(played.controller().getLastWinners(), List.of(p2.getUuid()), "the winner is kept for the bells and the piggy banks");
                            context.assertEquals(coins(played.controller(), p2), 10, "1st: 10 coins");
                            context.assertEquals(stars(played.controller(), p2), 1, "and the star set on the Gains page");
                            context.assertEquals(coins(played.controller(), p1), 2, "participants get their gain");
                            context.assertEquals(coins(played.controller(), p3), 2, "participants get their gain");
                            context.assertEquals(stars(played.controller(), p1), 0, "no star for them");
                        } finally {
                            cleanUp(context, p1, p2, p3);
                        }
                        context.complete();
                    });
                });
            });
        });
    }

    /**
     * A step controller going on ends the mini-game: the places on its podiums count all the same. A page without
     * podium: everyone is a participant.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "podium_party_step", tickLimit = 100)
    public void theStepControllerEndsItWithThePodiumsAsTheyAre(TestContext context) {
        ServerPlayerEntity p1 = player(context, "s1", 0.5, 1, 0.5), p2 = player(context, "s2", 1.5, 1, 0.5), p3 = player(context, "s3", 2.5, 1, 0.5);
        try {
            BlockPos first = column(context, 3, 3, 3), second = column(context, 4, 3, 2), third = column(context, 5, 3, 1);
            Played played = played(context, page(context, first), null, MiniGamePartyStep.Phase.PLAYING, p1, p2, p3);
            context.assertTrue(Podiums.toggle(p3, context.getWorld(), context.getAbsolutePos(second)), "p3 on the 2nd place");
            context.assertTrue(played.step().isPlaying(), "being played");
            played.controller().nextStep();
            context.assertEquals(played.step().getPhase(), MiniGamePartyStep.Phase.FINISHED, "ended by the step controller");
            context.assertEquals(played.step().getPlaces(), Map.of(p1.getUuid(), 0, p2.getUuid(), 0, p3.getUuid(), 2), "the podiums as they were");
            context.assertEquals(coins(played.controller(), p3), 5, "2nd: 5 coins");
            context.assertEquals(coins(played.controller(), p1), 0, "participants: nothing by default");
            context.assertTrue(played.data().isAtEnd(), "the party went on");
            context.removeBlock(CONTROLLER);

            // No podium on the page: nothing ends it but the step controller, and everyone is a participant
            Played plain = played(context, page(context), null, MiniGamePartyStep.Phase.PLAYING, p1, p2);
            plain.controller().setGains(plain.controller().getGains().with(PartyCurrency.COIN, MiniGameGains.PARTICIPANTS, 4));
            context.assertTrue(MiniGamePages.of(context.getWorld().getServer(), plain.page()).isPlayable() == false
                    && !MiniGamePages.of(context.getWorld().getServer(), plain.page()).hasPodium(), "a page without podium (nor pipes here)");
            plain.controller().nextStep();
            context.assertEquals(plain.step().getPlaces(), Map.of(p1.getUuid(), 0, p2.getUuid(), 0), "everyone is a participant");
            context.assertEquals(coins(plain.controller(), p1), 4, "paid as participants");
            context.assertTrue(plain.step().getWinners().isEmpty(), "no winner");

            // A finish naming its winners (whatever the podiums say)
            context.removeBlock(CONTROLLER);
            Played named = played(context, page(context), null, MiniGamePartyStep.Phase.PLAYING, p1, p2);
            context.assertTrue(named.step().finish(named.controller(), List.of(p2.getUuid())), "named winners");
            context.assertEquals(named.step().getPlaces(), Map.of(p1.getUuid(), 0, p2.getUuid(), 1), "they take the first place");
            context.assertEquals(named.controller().getLastWinners(), List.of(p2.getUuid()), "winners kept");
        } finally {
            cleanUp(context, p1, p2, p3);
        }
        context.complete();
    }

    // ------------------------------------------------------------------ places and gains

    private static void occupy(TestContext context, BlockPos bottom, UUID player) {
        PodiumGroup group = group(context, bottom);
        master(context, bottom).setOccupant(new PodiumOccupant(player, "x", -1,
                group.placeOf(group.columnAt(context.getWorld(), context.getAbsolutePos(bottom))), 0));
    }

    /**
     * The places read on the podiums: free for all (ties share a place, the others are participants), two teams (1 v
     * 3), three and four teams, fewer players than places; and the gain of each place.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placesAndGainsByPlace(TestContext context) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID(), e = UUID.randomUUID();
        // Two columns of the first place, then a second, a third and a fourth place
        BlockPos top1 = column(context, 1, 3, 4), top2 = column(context, 2, 3, 4), second = column(context, 3, 3, 3),
                third = column(context, 4, 3, 2), fourth = column(context, 5, 3, 1);
        PodiumGroup group = group(context, top1);
        context.assertEquals(group.placeCount(), 4, "four places, the first one shared");

        // Free for all, a tie for the first place
        occupy(context, top1, a);
        occupy(context, top2, b);
        occupy(context, third, c);
        Map<UUID, Integer> places = MiniGameResults.places(List.of(a, b, c, d), null, group(context, top1));
        context.assertEquals(places, Map.of(a, 1, b, 1, c, 3, d, 0), "a tie for the first place, a third, a participant");
        MiniGameGains gains = MiniGameGains.DEFAULT;
        context.assertEquals(List.of(gains.forPlace(PartyCurrency.COIN, 1), gains.forPlace(PartyCurrency.COIN, 2), gains.forPlace(PartyCurrency.COIN, 3),
                gains.forPlace(PartyCurrency.COIN, 4), gains.forPlace(PartyCurrency.COIN, 0), gains.forPlace(PartyCurrency.COIN, 5)),
                List.of(10, 5, 3, 1, 0, 0), "default coins: 10, 5, 3, 1; nothing for the participants (a 5th place is one)");
        context.assertEquals(gains.forPlace(PartyCurrency.STAR, 1), 0, "no star by default");
        MiniGameResults results = MiniGameResults.of("Test", ItemStack.EMPTY, ItemStack.EMPTY, gains, places, null, uuid -> uuid.toString().substring(0, 4));
        context.assertEquals(results.rows().stream().map(MiniGameResults.Row::place).toList(), List.of(1, 1, 3, 0), "the best first, participants last");
        context.assertEquals(results.rows().stream().map(MiniGameResults.Row::coins).toList(), List.of(10, 10, 3, 0), "tied players get the same gain");

        // Fewer players than places
        context.assertEquals(MiniGameResults.places(List.of(c), null, group(context, top1)), Map.of(c, 3), "a single player, on the third place");
        context.assertEquals(MiniGameResults.places(List.of(a, b), null, null), Map.of(a, 0, b, 0), "no podium: participants");

        // Two teams, 1 v 3: the place of a team is the place of the column one of its members took
        master(context, top2).setOccupant(null);
        TeamDisposition oneVsThree = new TeamDisposition(Set.of(a), Set.of(b, c, d));
        places = MiniGameResults.places(List.of(a, b, c, d), oneVsThree, group(context, top1));
        context.assertEquals(places, Map.of(a, 1, b, 3, c, 3, d, 3), "the lone player first, his three opponents third together");
        results = MiniGameResults.of("Test", ItemStack.EMPTY, ItemStack.EMPTY, gains, places, oneVsThree, uuid -> uuid.toString().substring(0, 4));
        context.assertEquals(results.rows().size(), 2, "a line per team");
        context.assertTrue(results.rows().get(0).team() == 0 && results.rows().get(0).coins() == 10 && results.rows().get(1).team() == 1
                && results.rows().get(1).names().size() == 3 && results.rows().get(1).coins() == 3, "each member of a team gets the gain of its place");

        // Three teams, then four: one place each, the team without podium is a participant
        occupy(context, second, d);
        TeamDisposition three = new TeamDisposition(Set.of(a), Set.of(c), Set.of(d, e), Set.of());
        context.assertEquals(MiniGameResults.places(List.of(a, c, d, e), three, group(context, top1)), Map.of(a, 1, c, 3, d, 2, e, 2), "three teams");
        TeamDisposition four = new TeamDisposition(Set.of(a), Set.of(b), Set.of(c), Set.of(d));
        occupy(context, fourth, b);
        context.assertEquals(MiniGameResults.places(List.of(a, b, c, d, e), four, group(context, top1)), Map.of(a, 1, b, 4, c, 3, d, 2, e, 0),
                "four teams, and a player in no team");

        // The results travel to the clients as they are
        net.minecraft.network.RegistryByteBuf buf = new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(), context.getWorld().getRegistryManager());
        MiniGameResults sent = MiniGameResults.of("Course", new ItemStack(Items.EMERALD), new ItemStack(Items.NETHER_STAR), gains, places, oneVsThree, UUID::toString);
        MiniGameResults.PACKET_CODEC.encode(buf, sent);
        MiniGameResults received = MiniGameResults.PACKET_CODEC.decode(buf);
        buf.release();
        context.assertTrue(received.title().equals("Course") && received.rows().equals(sent.rows()) && received.coinItem().isOf(Items.EMERALD), "results sent and read back");
        context.complete();
    }
}
