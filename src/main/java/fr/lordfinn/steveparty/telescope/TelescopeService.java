package fr.lordfinn.steveparty.telescope;

import fr.lordfinn.steveparty.entities.custom.MulaSpawnSites;
import fr.lordfinn.steveparty.payloads.ModPayloads;
import fr.lordfinn.steveparty.payloads.custom.TelescopePayloads;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The server's side of the Telescope, which is very little, on purpose: the replay of a night and the guide stars are
 * drawn by the player's own client. The server only
 * <ul>
 *   <li>answers a click on a telescope with the past nights that player can replay there ({@link #nights}: the sites
 *   within {@value #RANGE} blocks he has neither found nor been to, read from the list in memory);</li>
 *   <li>records that he found one ({@link #found}) and that he has been to one ({@link #visits}), per player, in
 *   MulaSpawnSites, and tells him (only him) his guide stars.</li>
 * </ul>
 * It never changes the time or the weather, spawns nothing, loads no chunk and makes no Mula appear early.
 */
public final class TelescopeService {
    private TelescopeService() {
    }

    /** A telescope shows the nights whose Mulas came down within this distance (blocks, horizontally). */
    public static final double RANGE = 1000;
    /** A player this close to a site (blocks, horizontally) has been there: its guide star goes out. */
    public static final double REACH = 24;
    /** At most one answer per player every this many ticks, and the players' places are checked this often. */
    public static final int QUERY_COOLDOWN = 10, VISIT_PERIOD = 40;

    private static final Map<UUID, Integer> LAST_QUERY = new HashMap<>();
    /** The nights last shown to each player: only those can be reported found. */
    private static final Map<UUID, Set<Integer>> OFFERED = new HashMap<>();

    public static void initialize() {
        TelescopePayloads.register();
        ServerPlayNetworking.registerGlobalReceiver(TelescopePayloads.Found.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ModPayloads.runInPacketOrder(player, () -> found(player, payload.site()));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncGuides(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUuid()));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> syncGuides(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> syncGuides(newPlayer));
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % VISIT_PERIOD == 7) visits(world);
        });
    }

    public static void forget(UUID player) {
        LAST_QUERY.remove(player);
        OFFERED.remove(player);
    }

    /** A night sky, clear, seen from the telescope: a dimension with a sky, at night, no rain, nothing over it. */
    public static boolean canWatch(ServerWorld world, BlockPos pos) {
        return world.getDimension().hasSkyLight() && !world.getDimension().hasCeiling()
                && TelescopeMath.isNightTime(world.getTimeOfDay()) && !world.isRaining()
                && world.isSkyVisible(pos.up());
    }

    /** The past nights a player can replay at a telescope there, the latest first. */
    public static List<TelescopePayloads.Night> nights(ServerPlayerEntity player, BlockPos pos) {
        MulaSpawnSites sites = MulaSpawnSites.peek(player.getServerWorld());
        if (sites == null) return List.of();
        List<TelescopePayloads.Night> nights = new ArrayList<>();
        for (MulaSpawnSites.Site site : sites.unfoundNear(player.getUuid(), pos, RANGE, TelescopePayloads.MAX_NIGHTS)) {
            double dx = site.pos.getX() - pos.getX(), dz = site.pos.getZ() - pos.getZ();
            double length = Math.sqrt(dx * dx + dz * dz);
            // right on it: any way will do
            float dirX = length < 1 ? 1 : (float) (dx / length), dirZ = length < 1 ? 0 : (float) (dz / length);
            int[] colours = site.colours.length > TelescopePayloads.MAX_COLOURS
                    ? java.util.Arrays.copyOf(site.colours, TelescopePayloads.MAX_COLOURS) : site.colours.clone();
            nights.add(new TelescopePayloads.Night(site.id, site.day, dirX, dirZ, colours));
        }
        return nights;
    }

    /**
     * A player clicks a telescope: told why not, or sent the nights to replay (to him alone).
     *
     * @return true if he may look through it
     */
    public static boolean use(ServerPlayerEntity player, BlockPos pos) {
        ServerWorld world = player.getServerWorld();
        if (!canWatch(world, pos)) {
            player.sendMessage(Text.translatable("message.steveparty.telescope.need_night_sky"), true);
            return false;
        }
        int now = player.server.getTicks();
        Integer last = LAST_QUERY.get(player.getUuid());
        if (last != null && now - last >= 0 && now - last < QUERY_COOLDOWN) return false;
        LAST_QUERY.put(player.getUuid(), now);
        answer(player, pos);
        return true;
    }

    /** Sends a player (only him) the nights he can replay at a telescope there, and remembers which were shown. */
    public static List<TelescopePayloads.Night> answer(ServerPlayerEntity player, BlockPos pos) {
        List<TelescopePayloads.Night> nights = nights(player, pos);
        Set<Integer> offered = new HashSet<>();
        for (TelescopePayloads.Night night : nights) offered.add(night.id());
        OFFERED.put(player.getUuid(), offered);
        if (ServerPlayNetworking.canSend(player, TelescopePayloads.Open.ID)) {
            ServerPlayNetworking.send(player,
                    new TelescopePayloads.Open(pos, player.getServerWorld().getTimeOfDay() / 24000L, nights));
        }
        return nights;
    }

    /**
     * The player followed a star of that night to the end: the site is found, for him, and its guide star lights up
     * in his sky. Only a night his telescope showed him, still in range.
     */
    public static boolean found(ServerPlayerEntity player, int id) {
        Set<Integer> offered = OFFERED.get(player.getUuid());
        if (offered == null || !offered.remove(id)) return false;
        MulaSpawnSites sites = MulaSpawnSites.peek(player.getServerWorld());
        MulaSpawnSites.Site site = sites == null ? null : sites.byId(id);
        if (site == null) return false;
        double dx = site.pos.getX() - player.getX(), dz = site.pos.getZ() - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance > RANGE + 64 || !sites.markFound(player.getUuid(), id)) return false;
        syncGuides(player);
        player.sendMessage(guideText(dx, dz), true);
        return true;
    }

    /** "Guide star: north-east, far". */
    public static Text guideText(double dx, double dz) {
        return Text.translatable("message.steveparty.telescope.guide",
                Text.translatable("telescope.steveparty.direction." + TelescopeMath.CARDINAL_KEYS[TelescopeMath.cardinal(dx, dz)]),
                Text.translatable("telescope.steveparty.distance." + TelescopeMath.BAND_KEYS[TelescopeMath.band(Math.sqrt(dx * dx + dz * dz))]));
    }

    /** The guide stars of a player in the dimension he is in. */
    public static List<TelescopePayloads.Guide> guides(ServerPlayerEntity player) {
        MulaSpawnSites sites = MulaSpawnSites.peek(player.getServerWorld());
        if (sites == null) return List.of();
        List<TelescopePayloads.Guide> guides = new ArrayList<>();
        for (MulaSpawnSites.Site site : sites.guides(player.getUuid())) {
            if (guides.size() >= TelescopePayloads.MAX_GUIDES) break;
            guides.add(new TelescopePayloads.Guide(site.id, site.pos.getX(), site.pos.getZ()));
        }
        return guides;
    }

    /** Tells a player (only him) his guide stars. */
    public static void syncGuides(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, TelescopePayloads.Guides.ID)) return;
        ServerPlayNetworking.send(player, new TelescopePayloads.Guides(guides(player)));
    }

    /** The players standing at a site have been there: their guide star for it goes out. */
    public static void visits(ServerWorld world) {
        MulaSpawnSites sites = MulaSpawnSites.peek(world);
        if (sites == null || sites.siteCount() == 0) return;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) continue;
            if (sites.visitAround(player.getUuid(), player.getBlockPos(), REACH)) syncGuides(player);
        }
    }
}
