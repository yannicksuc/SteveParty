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
import fr.lordfinn.steveparty.blocks.custom.TelescopeBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;

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
 *   MulaSpawnSites, and tells him (only him) his guide stars;</li>
 *   <li>knows who looks through which telescope (one player at a time), so that the others see him at its eyepiece.
 *   Only that is shared: what he sees in it is not.</li>
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

    /** A player coming to the eyepiece stands at least this far from the telescope's centre (blocks). */
    private static final double MIN_STANCE = 0.45;

    private static final Map<UUID, Integer> LAST_QUERY = new HashMap<>();
    /** The telescope each player is looking through. */
    private static final Map<UUID, GlobalPos> WATCHING = new HashMap<>();
    /** The nights last shown to each player: only those can be reported found. */
    private static final Map<UUID, Set<Integer>> OFFERED = new HashMap<>();

    public static void initialize() {
        TelescopePayloads.register();
        ServerPlayNetworking.registerGlobalReceiver(TelescopePayloads.Found.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ModPayloads.runInPacketOrder(player, () -> found(player, payload.site()));
        });
        ServerPlayNetworking.registerGlobalReceiver(TelescopePayloads.Leave.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ModPayloads.runInPacketOrder(player, () -> leave(player));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncGuides(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            leave(handler.getPlayer());
            forget(handler.getPlayer().getUuid());
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> syncGuides(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> syncGuides(newPlayer));
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % VISIT_PERIOD == 7) visits(world);
        });
    }

    public static void forget(UUID player) {
        LAST_QUERY.remove(player);
        OFFERED.remove(player);
        WATCHING.remove(player);
    }

    // ---------------------------------------------------------------- the watcher at the eyepiece

    /** The telescope a player is looking through (null: none). */
    public static @Nullable GlobalPos watching(UUID player) {
        return WATCHING.get(player);
    }

    /**
     * Is that player still at this telescope, under a night sky? Checked by the telescope itself
     * (TelescopeBlockEntity), so a watcher who vanished never keeps it.
     */
    public static boolean stillWatching(ServerWorld world, BlockPos pos, UUID watcher) {
        return canWatch(world, pos) && isAt(world, pos, watcher);
    }

    /** Is that player at this telescope? Online, alive, in its world, close to it. */
    public static boolean isAt(ServerWorld world, BlockPos pos, UUID watcher) {
        if (!(world.getPlayerByUuid(watcher) instanceof ServerPlayerEntity player)) return false;
        if (!player.isAlive() || player.isSpectator()) return false;
        double dx = player.getX() - (pos.getX() + 0.5), dz = player.getZ() - (pos.getZ() + 0.5);
        return dx * dx + dz * dz <= TelescopeMath.WATCH_DISTANCE * TelescopeMath.WATCH_DISTANCE
                && Math.abs(player.getY() - pos.getY()) <= TelescopeMath.WATCH_DISTANCE;
    }

    /** The telescope let its watcher go. */
    public static void released(UUID watcher, ServerWorld world, BlockPos pos) {
        WATCHING.remove(watcher, GlobalPos.create(world.getRegistryKey(), pos));
    }

    /** The player takes his eye off the telescope he was looking through. */
    public static void leave(ServerPlayerEntity player) {
        GlobalPos at = WATCHING.remove(player.getUuid());
        if (at == null) return;
        ServerWorld world = player.server.getWorld(at.dimension());
        if (world != null && world.isChunkLoaded(at.pos()) && world.getBlockEntity(at.pos()) instanceof TelescopeBlockEntity telescope
                && player.getUuid().equals(telescope.getWatcher())) {
            telescope.setWatcher(null);
        }
    }

    /**
     * The player comes to the eyepiece, unless another player is at it (one eyepiece: one player at a time): he is
     * the telescope's watcher (the others see him there), and steps to where his eye meets it, if he can stand there.
     *
     * @return false if the telescope is taken
     */
    public static boolean comeToEyepiece(ServerPlayerEntity player, BlockPos pos, TelescopeBlockEntity telescope) {
        UUID watcher = telescope.getWatcher();
        if (watcher != null && !watcher.equals(player.getUuid()) && isAt(player.getServerWorld(), pos, watcher)) return false;
        GlobalPos at = GlobalPos.create(player.getServerWorld().getRegistryKey(), pos);
        if (!at.equals(WATCHING.get(player.getUuid()))) leave(player);
        WATCHING.put(player.getUuid(), at);
        telescope.setWatcher(player.getUuid());
        if (Math.abs(player.getY() - pos.getY()) > 0.6) return true;
        double[] neck = new double[3];
        TelescopeMath.neck(player.getYaw(), player.getPitch(), neck);
        // not into the tripod (a steep aim puts the eyepiece over it)
        double flat = Math.sqrt(neck[0] * neck[0] + neck[2] * neck[2]);
        if (flat < MIN_STANCE) {
            double yaw = Math.toRadians(player.getYaw());
            neck[0] = Math.sin(yaw) * MIN_STANCE;
            neck[2] = -Math.cos(yaw) * MIN_STANCE;
        }
        double x = pos.getX() + 0.5 + neck[0], z = pos.getZ() + 0.5 + neck[2];
        Box box = player.getBoundingBox().offset(x - player.getX(), 0, z - player.getZ());
        ServerWorld world = player.getServerWorld();
        // room to stand, on something
        if (!world.isSpaceEmpty(player, box.contract(1e-3)) || world.isSpaceEmpty(player, box.offset(0, -0.6, 0))) return true;
        player.networkHandler.requestTeleport(x, player.getY(), z, player.getYaw(), player.getPitch());
        return true;
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
        if (world.getBlockEntity(pos) instanceof TelescopeBlockEntity telescope && !comeToEyepiece(player, pos, telescope)) {
            player.sendMessage(Text.translatable("message.steveparty.telescope.busy"), true);
            return false;
        }
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
