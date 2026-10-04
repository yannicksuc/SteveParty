package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.blocks.custom.pipe.MiniGamePipeBlock;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeDestinationProvider;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeNetworks;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeShape;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeTravel;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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
import java.util.Random;
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
 *     <li><b>The mini-game pipe</b>, out of a round: programmed with the page, it is the way into the
 *     mini-game. A player whose trip through the pipes ends in it (a capped end) comes out of the mini-game's pipes of
 *     the role of the colour of the mouth it went in by: by a green mouth out of a players pipe, by a blue one out of
 *     a team A pipe... A player who goes in the mini-game pipe's own mouth, or by a colour the page has no pipe for,
 *     comes out of the default arrival: the first role with a pipe among {@link #DEFAULT_ARRIVALS} (the entry pipes
 *     first). Each role's pipes in turn, or at random if the page says so. As far as its tier sends.</li>
 *     <li><b>During a round</b> (a party's, its practice round, « Jouer » or « Tester »), the pipes linked to its page
 *     are closed to its players and its spectators: going in one does nothing (« Pas de sortie pendant le
 *     mini-jeu », once every few seconds at most). The round brings them back where they stood when it ends.</li>
 *     <li><b>Out of a round</b>, a pipe linked to a page takes whoever goes in it out of the mini-game, by a mini-game
 *     pipe programmed with that page ({@link #wayOut}):
 *     <ol>
 *         <li>the last mini-game pipe he came in by, if it is still programmed with this page and he is within its
 *         range (copper: {@value PipeNetworks#WARP_RADIUS} blocks, iron: its dimension, golden: anywhere);</li>
 *         <li>else the nearest mini-game pipe programmed with the page he is within the range of
 *         ({@link MiniGamePipeIndex}: their chunks need not be loaded);</li>
 *         <li>else none: the linked pipe is a pipe like any other (a warp to a pipe of its colour, or back out).</li>
 *     </ol>
 *     He comes out of a mouth of that mini-game pipe's pipes of the colour of the linked pipe he took (a green
 *     linked pipe, out of its green mouth); without one, out of the mouth he went in by when it is that pipe, else
 *     out of the mini-game pipe's own mouth, else the first mouth of its pipes. The mouth he comes out of stays closed
 *     to him until he moves away (no bounce back in).</li>
 * </ul>
 * Only players are concerned: mobs and items travel through these pipes like through any other. Nothing is scanned:
 * the pages are asked when a player goes in a mouth ({@link PipeTravel.Gate}).
 */
public final class MiniGamePipes {
    /**
     * Where those who come by a mini-game pipe without a role of their own arrive: the first of these roles the page has
     * a pipe of. The entry pipes are there for that; then where one watches from, then where one plays.
     */
    public static final List<MiniGamePipeRole> DEFAULT_ARRIVALS = List.of(MiniGamePipeRole.ENTRY, MiniGamePipeRole.SPECTATORS,
            MiniGamePipeRole.PLAYERS, MiniGamePipeRole.TEAM_A, MiniGamePipeRole.TEAM_B, MiniGamePipeRole.TEAM_C, MiniGamePipeRole.TEAM_D);
    /** Ticks between two « Pas de sortie pendant le mini-jeu » to the same player. */
    private static final int CLOSED_WARNING_TICKS = 60;

    /**
     * The last mini-game pipe a player came in by: its page, where it is, and the mouth he went in by (null: the
     * mini-game pipe's own mouth was not it, or is unknown).
     */
    private record CameBy(UUID page, GlobalPos pipe, @Nullable GlobalPos mouth, @Nullable Direction opening) {
    }

    private static final Map<UUID, CameBy> CAME_BY = new HashMap<>();
    /** A player in a round: its page, and whether that round is still on. */
    private record Seat(UUID page, BooleanSupplier stillOn) {
    }

    private static final Map<UUID, Seat> IN_PARTY = new HashMap<>();
    /** The next pipe of each role a mini-game pipe sends to, by page. */
    private static final Map<UUID, Map<MiniGamePipeRole, Integer>> NEXT_ARRIVAL = new HashMap<>();
    /** When each player was last told a linked pipe is closed during his round (server ticks). */
    private static final Map<UUID, Integer> WARNED = new HashMap<>();

    private MiniGamePipes() {
    }

    public static void initialize() {
        PipeTravel.registerGate(MiniGamePipes::passage);
        // A page in a pipe's slot (a mini-game pipe): where its capped end sends
        PipeDestinationProvider.register(ModItems.MINI_GAME_PAGE, PageRoute::new);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CAME_BY.clear();
            IN_PARTY.clear();
            NEXT_ARRIVAL.clear();
            WARNED.clear();
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
        MiniGamePipeLink link = MiniGamePages.toggleLink(server, id, MiniGamePipeLink.of(mouth, opening, state));
        // The feedback of a cartridge picking a destination: its sounds, its message in the action bar
        if (link != null) {
            player.sendMessage(Text.translatable("message.steveparty.mini_game_page.pipe.linked", pos.getX(), pos.getY(), pos.getZ(),
                    link.role().text()), true);
            ModSounds.playSelect(world, pos);
        } else if (wasLinked) {
            player.sendMessage(Text.translatable("message.steveparty.mini_game_page.pipe.unlinked", pos.getX(), pos.getY(), pos.getZ()), true);
            ModSounds.playCancel(world, pos);
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
     * turn: the pipes in the order they were linked to the page, the players in the order given (the turn order);
     * or, for the roles the page says so ({@link MiniGamePageData#isRandom}), each into a pipe of the role picked at
     * random. Those whose role has no pipe use the players pipes; they are left out if there is none either.
     *
     * @param players    the players of the mini-game, in turn order
     * @param spectators those who watch (they use the spectators pipes, and are left out without any)
     * @param random     picks the pipes of the random roles
     * @return the pipe of each one, in the order they leave
     */
    public static Map<UUID, MiniGamePipeLink> distribute(MiniGamePageData page, @Nullable TeamDisposition teams,
                                                         List<UUID> players, List<UUID> spectators, Random random) {
        Map<UUID, MiniGamePipeLink> pipes = new LinkedHashMap<>();
        Map<MiniGamePipeRole, Integer> next = new HashMap<>();
        for (UUID player : players) {
            MiniGamePipeRole role = roleOf(teams, player);
            if (page.pipes(role).isEmpty()) role = MiniGamePipeRole.PLAYERS;
            List<MiniGamePipeLink> ofRole = page.pipes(role);
            if (ofRole.isEmpty()) continue;
            pipes.put(player, pick(page, role, ofRole, next, random));
        }
        List<MiniGamePipeLink> watching = page.pipes(MiniGamePipeRole.SPECTATORS);
        for (int i = 0; i < spectators.size() && !watching.isEmpty(); i++) {
            if (!pipes.containsKey(spectators.get(i))) pipes.put(spectators.get(i), pick(page, MiniGamePipeRole.SPECTATORS, watching, next, random));
        }
        return pipes;
    }

    /** The next pipe of a role: each in turn, or any of them for a role the page sends at random. */
    private static MiniGamePipeLink pick(MiniGamePageData page, MiniGamePipeRole role, List<MiniGamePipeLink> ofRole,
                                         Map<MiniGamePipeRole, Integer> next, Random random) {
        if (page.isRandom(role)) return ofRole.get(random.nextInt(ofRole.size()));
        return ofRole.get((next.merge(role, 1, Integer::sum) - 1) % ofRole.size());
    }

    /**
     * {@code player} comes out of the linked pipe (its chunk is loaded for it; another dimension is fine).
     *
     * @return false if the pipe is no more, or has no mouth
     */
    public static boolean emerge(MinecraftServer server, MiniGamePipeLink link, ServerPlayerEntity player) {
        ServerWorld world = server.getWorld(link.mouth().dimension());
        if (world == null) return false;
        Direction opening = openingOf(world, link);
        return opening != null && PipeTravel.emerge(world, new PipeNetworks.End(link.mouth().pos(), opening, false), player, PipeTravel.BASE_SPEED);
    }

    /**
     * A round sends its player out of the pipe of his side ({@link #emerge}): his name takes the colour of that pipe
     * until he leaves the round ({@link MiniGameNameColors}).
     */
    public static boolean emergeInRound(MinecraftServer server, MiniGamePipeLink link, ServerPlayerEntity player, @Nullable UUID page) {
        boolean out = emerge(server, link, player);
        if (out && page != null) MiniGameNameColors.apply(player, link.role(), page);
        return out;
    }

    // ------------------------------------------------------------------ party

    /**
     * {@code player} is in a round of the mini-game of {@code page} (a player or a spectator): it travels for free, and
     * the pipes linked to the page are closed to it (see the class) until {@link #leaveParty}, or until {@code stillOn}
     * says the round is over (a party controller broken while it was played).
     */
    public static void enterParty(UUID player, UUID page, BooleanSupplier stillOn) {
        IN_PARTY.put(player, new Seat(page, stillOn));
    }

    public static void leaveParty(UUID player) {
        IN_PARTY.remove(player);
        MiniGameNameColors.restore(player);
    }

    public static boolean isInParty(UUID player) {
        Seat seat = IN_PARTY.get(player);
        if (seat != null && !seat.stillOn().getAsBoolean()) {
            IN_PARTY.remove(player);
            MiniGameNameColors.restore(player);
            return false;
        }
        return seat != null;
    }

    /** The page of the round {@code player} is in, null for none. */
    public static @Nullable UUID roundOf(UUID player) {
        Seat seat = isInParty(player) ? IN_PARTY.get(player) : null;
        return seat == null ? null : seat.page();
    }

    // ------------------------------------------------------------------ the mini-game pipe

    /** The mouth a linked pipe opens on now (its chunk is loaded to look), null if it has none any more. */
    private static @Nullable Direction openingOf(ServerWorld world, MiniGamePipeLink link) {
        BlockState state = world.getBlockState(link.mouth().pos());
        return PipeShape.mouth(state, link.opening()) != null ? link.opening() : mouthOf(state, link.mouth().pos(), net.minecraft.util.math.Vec3d.ofCenter(link.mouth().pos()));
    }

    /**
     * The pipe of the mini-game a player coming by a mini-game pipe comes out of.
     *
     * @param enteredBy the pipe of the mouth it went in by, null for the mini-game pipe's own mouth
     * @param inReach   the pipes the mini-game pipe sends as far as (see {@link MiniGamePipeBlock.Reach})
     * @param random    picks among the pipes of a role the page sends at random
     * @return null if the page has no pipe to arrive by, or none of its role in reach
     */
    public static @Nullable MiniGamePipeLink pipeArrival(MiniGamePageData page, @Nullable BlockState enteredBy,
                                                         Predicate<MiniGamePipeLink> inReach, Random random) {
        MiniGamePipeRole role = enteredBy == null ? null : MiniGamePipeRole.ofPipe(enteredBy);
        if (role == null || !role.isArrival() || page.pipes(role).isEmpty()) {
            role = null;
            for (MiniGamePipeRole fallback : DEFAULT_ARRIVALS) {
                if (!page.pipes(fallback).isEmpty()) {
                    role = fallback;
                    break;
                }
            }
        }
        if (role == null) return null;
        List<MiniGamePipeLink> reachable = page.pipes(role).stream().filter(inReach).toList();
        if (reachable.isEmpty()) return null;
        return pick(page, role, reachable, NEXT_ARRIVAL.computeIfAbsent(page.id(), id -> new HashMap<>()), random);
    }

    /**
     * The pipe a player coming by the mini-game pipe at {@code pipePos} comes out of; when the mini-game is too far for
     * that pipe, the player is told which mini-game pipe it takes.
     */
    private static @Nullable MiniGamePipeLink arrivalFrom(ServerWorld world, BlockPos pipePos, MiniGamePageData page, @Nullable BlockState enteredBy,
                                                         ServerPlayerEntity player) {
        MiniGamePipeBlock.Reach reach = MiniGamePipeBlock.reachOf(world.getBlockState(pipePos));
        MiniGamePipeLink link = pipeArrival(page, enteredBy, pipe -> MiniGamePipeBlock.reaches(reach, world, pipePos, pipe.mouth()), new Random());
        if (link == null && pipeArrival(page, enteredBy, pipe -> true, new Random()) != null) {
            // There are pipes to arrive by, but none this pipe reaches
            boolean elsewhere = page.pipeLinks().stream().noneMatch(pipe -> pipe.mouth().dimension().equals(world.getRegistryKey()));
            player.sendMessage(Text.translatable("message.steveparty.minigame_pipe.too_far."
                    + (reach == MiniGamePipeBlock.Reach.DIMENSION || elsewhere ? "copper" : "iron")).formatted(Formatting.RED), true);
        }
        return link;
    }

    /** Where the capped end of a mini-game pipe programmed with a page sends a player: see the class. */
    private record PageRoute(ItemStack page) implements PipeDestinationProvider {
        @Override
        public @Nullable Exit destination(ServerWorld world, BlockPos cappedEnd, Entity traveller) {
            return destination(world, cappedEnd, traveller, null);
        }

        @Override
        public @Nullable Exit destination(ServerWorld world, BlockPos cappedEnd, Entity traveller, PipeNetworks.@Nullable End enteredBy) {
            if (!(traveller instanceof ServerPlayerEntity player) || isInParty(player.getUuid())) return null;
            MinecraftServer server = world.getServer();
            MiniGamePageData data = MiniGamePages.of(server, page);
            if (data == null) return null;
            // By the mini-game pipe's own mouth: no colour to go by
            BlockState entered = enteredBy == null || enteredBy.pos().equals(cappedEnd) ? null : world.getBlockState(enteredBy.pos());
            MiniGamePipeLink link = arrivalFrom(world, cappedEnd, data, entered, player);
            ServerWorld there = link == null ? null : server.getWorld(link.mouth().dimension());
            Direction opening = there == null ? null : openingOf(there, link);
            if (opening == null) return null;
            CAME_BY.put(player.getUuid(), new CameBy(data.id(), GlobalPos.create(world.getRegistryKey(), cappedEnd.toImmutable()),
                    enteredBy == null ? null : GlobalPos.create(world.getRegistryKey(), enteredBy.pos()), enteredBy == null ? null : enteredBy.dir()));
            return new Exit(link.mouth().dimension(), link.mouth().pos(), opening);
        }
    }

    /**
     * What a mouth does with a player going in it: the own mouth of a programmed mini-game pipe sends to the default
     * arrival of its mini-game; a pipe linked to a page is closed to the players of its round, and out of a round takes
     * whoever goes in out by a mini-game pipe of the page (see the class).
     */
    private static PipeTravel.@Nullable Passage passage(ServerWorld world, BlockPos mouth, Direction opening, ServerPlayerEntity player) {
        MinecraftServer server = world.getServer();
        UUID id = player.getUuid();
        GlobalPos here = GlobalPos.create(world.getRegistryKey(), mouth);
        boolean inRound = isInParty(id);
        MiniGamePageData programmed = MiniGamePages.of(server, MiniGamePipeBlock.pageAt(world, mouth));
        if (programmed != null && !inRound && DEFAULT_ARRIVALS.stream().anyMatch(role -> !programmed.pipes(role).isEmpty())) {
            return new PipeTravel.Passage(traveller -> {
                MiniGamePipeLink link = arrivalFrom(world, mouth, programmed, null, traveller);
                if (link == null || !emerge(server, link, traveller)) return false;
                CAME_BY.put(traveller.getUuid(), new CameBy(programmed.id(), here, here, opening));
                return true;
            });
        }
        List<MiniGamePagesState.Linked> links = MiniGamePages.linksAt(server, here);
        if (links.isEmpty()) return null;
        if (inRound) {
            UUID round = roundOf(id);
            for (MiniGamePagesState.Linked linked : links) {
                if (!linked.page().id().equals(round)) continue;
                warnClosed(server, player);
                return PipeTravel.Passage.CLOSED;
            }
            // The pipes of another page are pipes like any other for a round's player
            return null;
        }
        BlockState colour = world.getBlockState(mouth);
        GlobalPos from = GlobalPos.create(world.getRegistryKey(), player.getBlockPos());
        for (MiniGamePagesState.Linked linked : links) {
            UUID page = linked.page().id();
            GlobalPos pipe = wayOutPipe(server, id, page, from);
            if (pipe != null) return new PipeTravel.Passage(traveller -> outThrough(server, pipe, colour, CAME_BY.get(traveller.getUuid()), traveller));
        }
        // No mini-game pipe of the page in range: a pipe like any other
        return null;
    }

    /** « Pas de sortie pendant le mini-jeu », once every few seconds at most. */
    private static void warnClosed(MinecraftServer server, ServerPlayerEntity player) {
        Integer last = WARNED.get(player.getUuid());
        int now = server.getTicks();
        if (last != null && now - last >= 0 && now - last < CLOSED_WARNING_TICKS) return;
        WARNED.put(player.getUuid(), now);
        player.sendMessage(Text.translatable("message.steveparty.minigame_pipe.closed").formatted(Formatting.RED), true);
    }

    /**
     * The mini-game pipe a player at {@code from} going in a pipe linked to {@code page} out of a round leaves by: the
     * last one he came in by if it is programmed with the page and he is within its range, else the nearest in range
     * of him; null for none.
     */
    public static @Nullable GlobalPos wayOutPipe(MinecraftServer server, UUID player, UUID page, GlobalPos from) {
        CameBy last = CAME_BY.get(player);
        if (last != null && last.page().equals(page)) {
            MiniGamePipeIndex.Entry entry = MiniGamePipeIndex.at(server, last.pipe());
            if (entry != null && entry.page().equals(page) && MiniGamePipeIndex.inRange(entry.reach(), last.pipe(), from)) return last.pipe();
        }
        return MiniGamePipeIndex.nearestInRange(server, page, from);
    }

    /**
     * {@code player}, out of a round, went in a pipe linked to {@code page} (its block {@code colour}): he comes out
     * by a mini-game pipe of the page (see the class).
     *
     * @return false if there is none in range, or it could not take him (he comes back out of the mouth he went in)
     */
    public static boolean wayOut(MinecraftServer server, ServerPlayerEntity player, UUID page, BlockState colour) {
        GlobalPos pipe = wayOutPipe(server, player.getUuid(), page, GlobalPos.create(player.getWorld().getRegistryKey(), player.getBlockPos()));
        return pipe != null && outThrough(server, pipe, colour, CAME_BY.get(player.getUuid()), player);
    }

    /** {@code player} came by the mini-game pipe at {@code pipe} programmed with {@code page}, in by {@code mouth} (null: unknown). */
    public static void cameBy(UUID player, UUID page, GlobalPos pipe, @Nullable GlobalPos mouth, @Nullable Direction opening) {
        CAME_BY.put(player, new CameBy(page, pipe, mouth, opening));
    }

    /**
     * {@code player} comes out by the mini-game pipe at {@code pipe}: out of the mouth of its pipes of the colour of
     * {@code colour}; else the mouth he went in by ({@code last}) when it is of these pipes; else the mini-game pipe's
     * own mouth; else the first mouth of its pipes. The chunks around it are loaded to look.
     */
    private static boolean outThrough(MinecraftServer server, GlobalPos pipe, BlockState colour, @Nullable CameBy last, ServerPlayerEntity player) {
        ServerWorld world = server.getWorld(pipe.dimension());
        if (world == null) return false;
        int cx = pipe.pos().getX() >> 4, cz = pipe.pos().getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) world.getChunk(cx + dx, cz + dz);
        if (!(world.getBlockState(pipe.pos()).getBlock() instanceof MiniGamePipeBlock)) return false;
        PipeNetworks.Network network = PipeNetworks.of(world).network(pipe.pos());
        if (network == null) return false;
        List<PipeNetworks.End> mouths = network.ends().stream()
                .filter(end -> !end.capped() && PipeShape.mouth(world.getBlockState(end.pos()), end.dir()) != null).toList();
        if (mouths.isEmpty()) return false;
        PipeNetworks.End chosen = null;
        for (PipeNetworks.End end : mouths) {
            if (sameColour(world.getBlockState(end.pos()), colour)) {
                chosen = end;
                break;
            }
        }
        if (chosen == null && last != null && last.pipe().equals(pipe) && last.mouth() != null) {
            for (PipeNetworks.End end : mouths) if (end.pos().equals(last.mouth().pos()) && end.dir() == last.opening()) chosen = end;
        }
        if (chosen == null) {
            for (PipeNetworks.End end : mouths) if (end.pos().equals(pipe.pos())) chosen = end;
        }
        if (chosen == null) chosen = mouths.getFirst();
        return PipeTravel.emerge(world, chosen, player, PipeTravel.BASE_SPEED);
    }

    /** @return true if two pipes are of the same colour: the same dye (plastic and stained glass alike), else the same kind. */
    public static boolean sameColour(BlockState a, BlockState b) {
        if (!(a.getBlock() instanceof fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlock first)
                || !(b.getBlock() instanceof fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlock second)) return false;
        if (first.kind().colored && second.kind().colored) return first.color() == second.color();
        return first.kind() == second.kind();
    }
}
