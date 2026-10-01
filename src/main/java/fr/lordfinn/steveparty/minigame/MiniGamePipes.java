package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeNetworks;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeShape;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeTravel;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * The pipes of the mini-games: the mouths linked to a page ({@link MiniGamePipeLink}).
 * <ul>
 *     <li><b>Linking</b>: a click on a mouth with the page in hand links it, with the role of its colour
 *     ({@link MiniGamePipeRole#ofPipe}); another click unlinks it ({@link #click}).</li>
 *     <li><b>Arrivals</b>: the players of a mini-game come out of the pipes of their role, one after the other in
 *     each pipe in turn ({@link #distribute}, {@link #emerge}).</li>
 *     <li><b>Entry</b>, out of a party: a player going in an entry pipe comes out of the page's players pipes, each in
 *     turn.</li>
 *     <li><b>Exit</b>: a player going in an exit pipe goes back where it came from: in a party, where it stood before
 *     the mini-game; out of a party, out of the entry pipe it came by (else the page's first entry pipe).</li>
 * </ul>
 * Only players are concerned: mobs and items travel through these pipes like through any other. Nothing is scanned:
 * the pages are asked when a player goes in a mouth ({@link PipeTravel.Gate}).
 */
public final class MiniGamePipes {
    /** A player in a mini-game out of a party: the page, and the entry pipe it came by. */
    private record Visit(UUID page, GlobalPos mouth, Direction opening) {
    }

    private static final Map<UUID, Visit> VISITS = new HashMap<>();
    /** A player in a party's mini-game: how it leaves by an exit pipe, and whether that mini-game is still on. */
    private record Seat(Predicate<ServerPlayerEntity> leave, BooleanSupplier stillOn) {
    }

    private static final Map<UUID, Seat> IN_PARTY = new HashMap<>();
    /** The next players pipe an entry sends to, by page. */
    private static final Map<UUID, Integer> NEXT_ARRIVAL = new HashMap<>();

    private MiniGamePipes() {
    }

    public static void initialize() {
        PipeTravel.registerGate(MiniGamePipes::passage);
        // The players of a party's mini-game travel for free
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            VISITS.clear();
            IN_PARTY.clear();
            NEXT_ARRIVAL.clear();
        });
    }

    // ------------------------------------------------------------------ linking

    /** The mouth of the pipe at {@code pos} a player looking from {@code eye} means: the one facing it, else its first one. */
    public static @Nullable Direction mouthOf(BlockState state, BlockPos pos, net.minecraft.util.math.Vec3d eye) {
        Direction facing = PipeTravel.mouthFacing(state, pos, eye);
        if (facing != null) return facing;
        for (PipeShape.End end : PipeShape.ends(state)) if (!end.capped()) return end.dir();
        return null;
    }

    /**
     * A click on the pipe at {@code pos} with the page held in {@code hand}: the mouth is linked to the page with the
     * role of the pipe's colour, or unlinked if it was linked. The player is told what happened.
     *
     * @return true if the page changed
     */
    public static boolean click(ServerPlayerEntity player, Hand hand, ServerWorld world, BlockPos pos) {
        ItemStack stack = player.getStackInHand(hand);
        if (!MiniGamePages.isPage(stack)) return false;
        if (!MiniGamePages.canEdit(player)) {
            player.sendMessage(Text.translatable("gui.steveparty.mini_game_page.status.not_allowed").formatted(Formatting.RED), true);
            return false;
        }
        BlockState state = world.getBlockState(pos);
        Direction opening = mouthOf(state, pos, player.getEyePos());
        if (opening == null) {
            player.sendMessage(Text.translatable("message.steveparty.mini_game_page.pipe.no_mouth").formatted(Formatting.RED), true);
            return false;
        }
        UUID id = MiniGamePageNetworking.ensureSinglePage(player, hand);
        MinecraftServer server = player.server;
        GlobalPos mouth = GlobalPos.create(world.getRegistryKey(), pos.toImmutable());
        boolean wasLinked = MiniGamePages.get(server, id).linkIndex(mouth) >= 0;
        MiniGamePipeLink link = MiniGamePages.toggleLink(server, id, mouth, opening, MiniGamePipeRole.ofPipe(state));
        if (link != null) {
            player.sendMessage(Text.translatable("message.steveparty.mini_game_page.pipe.linked", link.role().text(),
                    MiniGamePages.get(server, id).pipes(link.role()).size()).formatted(Formatting.GREEN), true);
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0F, 1.3F);
        } else if (wasLinked) {
            player.sendMessage(Text.translatable("message.steveparty.mini_game_page.pipe.unlinked").formatted(Formatting.YELLOW), true);
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.BLOCKS, 0.8F, 0.9F);
        } else {
            player.sendMessage(Text.translatable("message.steveparty.mini_game_page.pipe.full", MiniGamePageData.MAX_PIPE_LINKS)
                    .formatted(Formatting.RED), true);
            return false;
        }
        MiniGamePages.refresh(server, player.getStackInHand(hand));
        return true;
    }

    // ------------------------------------------------------------------ arrivals

    /** The role a player comes out by: the pipes of its team, the players pipes without teams. */
    public static MiniGamePipeRole roleOf(@Nullable TeamDisposition teams, UUID player) {
        if (teams == null || teams.isFreeForAll()) return MiniGamePipeRole.PLAYERS;
        int team = teams.teamOf(player);
        return team < 0 ? MiniGamePipeRole.PLAYERS : MiniGamePipeRole.ofTeam(team);
    }

    /**
     * Which pipe each one comes out of. The players of a role go one after the other into each pipe of that role in
     * turn: the pipes in the order they were linked to the page, the players in the order given (the turn order).
     * Never random. Those whose role has no pipe use the players pipes; they are left out if there is none either.
     *
     * @param players    the players of the mini-game, in turn order
     * @param spectators those who watch (they use the spectators pipes, and are left out without any)
     * @return the pipe of each one, in the order they leave
     */
    public static Map<UUID, MiniGamePipeLink> distribute(MiniGamePageData page, @Nullable TeamDisposition teams,
                                                         List<UUID> players, List<UUID> spectators) {
        Map<UUID, MiniGamePipeLink> pipes = new LinkedHashMap<>();
        Map<MiniGamePipeRole, Integer> next = new HashMap<>();
        for (UUID player : players) {
            MiniGamePipeRole role = roleOf(teams, player);
            if (page.pipes(role).isEmpty()) role = MiniGamePipeRole.PLAYERS;
            List<MiniGamePipeLink> ofRole = page.pipes(role);
            if (ofRole.isEmpty()) continue;
            pipes.put(player, ofRole.get((next.merge(role, 1, Integer::sum) - 1) % ofRole.size()));
        }
        List<MiniGamePipeLink> watching = page.pipes(MiniGamePipeRole.SPECTATORS);
        for (int i = 0; i < spectators.size() && !watching.isEmpty(); i++) {
            if (!pipes.containsKey(spectators.get(i))) pipes.put(spectators.get(i), watching.get(i % watching.size()));
        }
        return pipes;
    }

    /**
     * {@code player} comes out of the linked pipe (its chunk is loaded for it; another dimension is fine).
     *
     * @return false if the pipe is no more, or has no mouth
     */
    public static boolean emerge(MinecraftServer server, MiniGamePipeLink link, ServerPlayerEntity player) {
        ServerWorld world = server.getWorld(link.mouth().dimension());
        if (world == null) return false;
        BlockPos pos = link.mouth().pos();
        BlockState state = world.getBlockState(pos);
        Direction opening = PipeShape.mouth(state, link.opening()) != null ? link.opening() : mouthOf(state, pos, net.minecraft.util.math.Vec3d.ofCenter(pos));
        return opening != null && PipeTravel.emerge(world, new PipeNetworks.End(pos, opening, false), player, PipeTravel.BASE_SPEED);
    }

    // ------------------------------------------------------------------ party

    /**
     * {@code player} is in a party's mini-game: it travels for free, and an exit pipe makes it leave with {@code leave}
     * (false if it can't) until {@link #leaveParty}, or until {@code stillOn} says the mini-game is over (a party
     * controller broken while it was played).
     */
    public static void enterParty(UUID player, Predicate<ServerPlayerEntity> leave, BooleanSupplier stillOn) {
        IN_PARTY.put(player, new Seat(leave, stillOn));
        VISITS.remove(player);
    }

    public static void leaveParty(UUID player) {
        IN_PARTY.remove(player);
    }

    public static boolean isInParty(UUID player) {
        Seat seat = IN_PARTY.get(player);
        if (seat != null && !seat.stillOn().getAsBoolean()) {
            IN_PARTY.remove(player);
            return false;
        }
        return seat != null;
    }

    // ------------------------------------------------------------------ entry and exit

    /** What a linked mouth does with a player going in it: see the class. */
    private static PipeTravel.@Nullable Passage passage(ServerWorld world, BlockPos mouth, Direction opening, ServerPlayerEntity player) {
        MinecraftServer server = world.getServer();
        GlobalPos here = GlobalPos.create(world.getRegistryKey(), mouth);
        List<MiniGamePagesState.Linked> links = MiniGamePages.linksAt(server, here);
        if (links.isEmpty()) return null;
        UUID id = player.getUuid();
        for (MiniGamePagesState.Linked linked : links) {
            MiniGamePageData page = linked.page();
            if (linked.link().role() == MiniGamePipeRole.EXIT) {
                if (isInParty(id)) return new PipeTravel.Passage(IN_PARTY.get(id).leave());
                Visit visit = VISITS.get(id);
                MiniGamePipeLink back = visit != null && visit.page().equals(page.id())
                        ? new MiniGamePipeLink(visit.mouth(), visit.opening(), MiniGamePipeRole.ENTRY)
                        : page.pipes(MiniGamePipeRole.ENTRY).stream().findFirst().orElse(null);
                if (back == null) continue;
                return new PipeTravel.Passage(traveller -> {
                    if (!emerge(server, back, traveller)) return false;
                    VISITS.remove(traveller.getUuid());
                    return true;
                });
            }
            if (linked.link().role() == MiniGamePipeRole.ENTRY && !isInParty(id)) {
                List<MiniGamePipeLink> arrivals = page.pipes(MiniGamePipeRole.PLAYERS);
                if (arrivals.isEmpty()) continue;
                return new PipeTravel.Passage(traveller -> {
                    // The players pipes each in turn; a pipe that is no more is skipped
                    int first = NEXT_ARRIVAL.getOrDefault(page.id(), 0);
                    for (int i = 0; i < arrivals.size(); i++) {
                        int index = (first + i) % arrivals.size();
                        if (!emerge(server, arrivals.get(index), traveller)) continue;
                        NEXT_ARRIVAL.put(page.id(), index + 1);
                        VISITS.put(traveller.getUuid(), new Visit(page.id(), here, opening));
                        return true;
                    }
                    return false;
                });
            }
        }
        return null;
    }
}
