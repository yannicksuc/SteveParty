package fr.lordfinn.steveparty.service;

import com.mojang.authlib.GameProfile;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.payloads.Payloads;
import fr.lordfinn.steveparty.payloads.custom.TileInfoPayloads;
import fr.lordfinn.steveparty.powerups.effects.TrapEffect;
import fr.lordfinn.steveparty.powerups.effects.TrapState;
import fr.lordfinn.steveparty.utils.ServerMemory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The board spaces' game info ({@link TileInfo}), server side. Nothing ticks: a client asks for the spaces it shows
 * (the one looked at, its token's destination, those around with the Explorer's Helmet), at most twice a second, and
 * gets back only those whose info changed since it last got them. A token moving tells its player where it is heading
 * ({@link #heading}), once per step and only when that changed.
 */
public final class TileInfos {
    /** At most this many spaces per request. */
    public static final int MAX_ASKED = 12;
    /** Spaces farther than this from the player are not answered. */
    private static final double MAX_DISTANCE_SQ = 64 * 64;
    /** Past this many spaces remembered for a player, the memory starts over (they are sent again). */
    private static final int MAX_REMEMBERED = 512;

    private static final class Seen {
        RegistryKey<World> world;
        final Map<BlockPos, TileInfo> sent = new HashMap<>();
        List<BlockPos> focus = List.of();
        /** The token {@link #focus} is for. */
        UUID focusToken;
    }

    private static final Map<UUID, Seen> SEEN = ServerMemory.forgetOnStop(new HashMap<>());

    private TileInfos() {
    }

    public static void initialize() {
        Payloads.c2s(TileInfoPayloads.Request.ID, TileInfoPayloads.Request.CODEC);
        Payloads.s2c(TileInfoPayloads.Info.ID, TileInfoPayloads.Info.CODEC);
        Payloads.s2c(TileInfoPayloads.Focus.ID, TileInfoPayloads.Focus.CODEC);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SEEN.remove(handler.player.getUuid()));
        // A token gone in the middle of its move (killed, picked up, its chunk unloaded): nothing to point at any more
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof MobEntity token && token instanceof TokenizedEntityInterface tokenized
                    && tokenized.steveparty$getTokenOwner() != null) {
                Seen seen = SEEN.get(tokenized.steveparty$getTokenOwner());
                if (seen != null && token.getUuid().equals(seen.focusToken)) focus(world, token, List.of());
            }
        });
    }

    // ---------------------------------------------------------------- what a space tells

    /** The game info of {@code space}: its role's ({@code describe}), then a Trap set on it. */
    public static TileInfo of(BoardSpaceBlockEntity space) {
        if (!(space.getWorld() instanceof ServerWorld world)) return TileInfo.EMPTY;
        TileInfo.Builder info = TileInfo.builder();
        ItemStack cartridge = space.getActiveCartridgeItemStack();
        if (cartridge.getItem() instanceof CartridgeItem item) {
            info.title(cartridge.getName(), item.menuColor(cartridge));
            space.getBoardSpaceBehavior(cartridge).describe(world, space, cartridge, info);
        }
        // A role with nothing to tell (a plain space): a trap set on it is what it is
        boolean told = !info.isEmpty();
        trap(world, space, info, told);
        return info.build();
    }

    /** A Trap set on the space: what it does, and who set it (in their colour). */
    private static void trap(ServerWorld world, BoardSpaceBlockEntity space, TileInfo.Builder info, boolean told) {
        if (space.getTrapMark() == null) return;
        for (PartyControllerEntity party : PartyControllerEntity.getActivePartyControllers()) {
            if (party.getWorld() != world) continue;
            TrapState.Trap trap = TrapEffect.trapAt(party, space.getPos());
            if (trap == null) continue;
            Text setter = world.getServer().getUserCache() == null ? null : world.getServer().getUserCache().getByUuid(trap.placer())
                    .map(GameProfile::getName).<Text>map(Text::literal).orElse(null);
            // Titled « Trap » on a space with nothing else to tell: its effect alone, else « Trap: its effect »
            if (!told) info.title(TileInfo.line("trap.title"), trap.color());
            info.line(told ? TileInfo.line("trap", TileInfo.bad(trap.effect().describe())) : TileInfo.bad(trap.effect().describe()));
            if (setter != null) info.line(TileInfo.line("trap.setter", TileInfo.rgb(setter, darker(trap.color()))));
            return;
        }
    }

    /** A player's colour made readable on the light plates. */
    private static int darker(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        float luminance = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
        if (luminance < 0.55f) return rgb & 0xFFFFFF;
        float k = 0.55f / luminance;
        return ((int) (r * k) << 16) | ((int) (g * k) << 8) | (int) (b * k);
    }

    // ---------------------------------------------------------------- answering the clients

    /** {@code player}'s client shows these spaces: their info, those that changed since last sent. */
    public static void onRequest(ServerPlayerEntity player, List<BlockPos> spaces) {
        ServerWorld world = player.getServerWorld();
        Seen seen = SEEN.computeIfAbsent(player.getUuid(), uuid -> new Seen());
        if (seen.world != world.getRegistryKey() || seen.sent.size() > MAX_REMEMBERED) {
            seen.world = world.getRegistryKey();
            seen.sent.clear();
        }
        int asked = 0;
        for (BlockPos pos : spaces) {
            if (++asked > MAX_ASKED) break;
            if (pos.getSquaredDistance(player.getPos()) > MAX_DISTANCE_SQ || !world.isChunkLoaded(pos)) continue;
            TileInfo info = world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity space ? of(space) : TileInfo.EMPTY;
            TileInfo last = seen.sent.get(pos);
            if (last != null && last.sameAs(info)) continue;
            seen.sent.put(pos.toImmutable(), info);
            ServerPlayNetworking.send(player, new TileInfoPayloads.Info(pos.toImmutable(), info));
        }
    }

    /** The client forgot what it was sent (it joined, or changed world): everything is sent again. */
    public static void forget(ServerPlayerEntity player) {
        Seen seen = SEEN.get(player.getUuid());
        if (seen != null) seen.sent.clear();
    }

    // ---------------------------------------------------------------- where a token is heading

    /**
     * {@code token} sets off from {@code from} with {@code steps} steps toward {@code exits} (one: its way; several: a
     * fork, its player chooses): tells its player where each way ends (the space it will stop on, a Stop space, or the
     * next fork). Nothing: its move is over ({@link #arrived}).
     */
    public static void heading(ServerWorld world, MobEntity token, List<BoardSpaceDestination> exits, int steps) {
        List<BlockPos> ends = new ArrayList<>(exits.size());
        for (BoardSpaceDestination exit : exits) {
            BlockPos end = walk(world, exit.position(), steps);
            if (!ends.contains(end)) ends.add(end);
            if (ends.size() >= MAX_ASKED) break;
        }
        focus(world, token, ends);
    }

    /** {@code token}'s move is over: nothing to point at any more. */
    public static void arrived(ServerWorld world, MobEntity token) {
        focus(world, token, List.of());
    }

    /**
     * Where a token reaching {@code first} with {@code steps} steps (that space's own included) stops: following the
     * single way of each space, until its steps are walked (check points cost none), a Stop space, a fork or a dead
     * end. Rules played on the way (a Threshold, a Move Forward...) are not foreseen.
     */
    public static BlockPos walk(World world, BlockPos first, int steps) {
        BlockPos pos = first.toImmutable();
        BoardSpaceBlockEntity space = ABoardSpaceBlock.getBoardSpaceEntity(world, pos);
        if (space == null) return pos;
        int left = steps - (ABoardSpaceBlock.countsAsStep(space.getCachedState().getBlock()) ? 1 : 0);
        for (int guard = 0; guard < 256; guard++) {
            if (left <= 0 || TokenMovementService.isForcedStop(world, space)) return pos;
            BlockPos next = null;
            int ways = 0;
            for (BoardSpaceDestination destination : space.getStockedDestinations()) {
                if (!destination.isTile()) continue;
                ways++;
                next = destination.position();
            }
            if (ways != 1) return pos;
            BoardSpaceBlockEntity nextSpace = ABoardSpaceBlock.getBoardSpaceEntity(world, next);
            if (nextSpace == null) return pos;
            pos = next.toImmutable();
            space = nextSpace;
            if (ABoardSpaceBlock.countsAsStep(space.getCachedState().getBlock())) left--;
        }
        return pos;
    }

    private static void focus(ServerWorld world, MobEntity token, List<BlockPos> spaces) {
        UUID owner = token instanceof TokenizedEntityInterface tokenized ? tokenized.steveparty$getTokenOwner() : null;
        ServerPlayerEntity player = owner == null ? null : world.getServer().getPlayerManager().getPlayer(owner);
        if (player == null) return;
        Seen seen = SEEN.computeIfAbsent(player.getUuid(), uuid -> new Seen());
        if (Objects.equals(seen.focus, spaces)) return;
        seen.focus = List.copyOf(spaces);
        seen.focusToken = spaces.isEmpty() ? null : token.getUuid();
        ServerPlayNetworking.send(player, new TileInfoPayloads.Focus(seen.focus));
    }
}
