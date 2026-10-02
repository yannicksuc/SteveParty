package fr.lordfinn.steveparty.entities.custom;

import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import fr.lordfinn.steveparty.entities.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ephemeride: a night of shooting stars. Rolled once per night per dimension (with a sky, no ceiling) at dusk: the
 * closer the moon to full, the likelier ({@link #chance}); guaranteed at full moon when a player is by a Dice Forge at
 * its highest level ({@link DiceForgeBlockEntity#isMaxLevel}).
 * <p>
 * During the night, {@value #WAVES} waves, one every {@value #WAVE_TICKS} ticks: over each player (at most
 * {@value #MAX_PLAYERS}), {@value #STARS_PER_WAVE} shooting stars cross the sky in a straight line, low over the ground
 * with sparkling trails (MulaStarEntity, flat: its path is a formula from a few synced numbers, particles only on the
 * clients near it). After a player's first wave, a group of Mulas is recorded where the stars went, 150 to 600 blocks
 * on (MulaSpawnSites), appearing when that place is loaded: at most one group per player per event, and at most
 * MulaSpawnSites#maxSites sites in the dimension (one more retires the oldest, with its wild Mulas).
 */
public final class MulaEphemeride {
    private MulaEphemeride() {
    }

    /** Chance at full moon, and the curve: chance = MAX_CHANCE x fullness^CURVE (fullness 0 at new moon, 1 at full). */
    public static final double MAX_CHANCE = 0.25, CURVE = 2;
    /** Time of day it is rolled at (dusk), and the length of the waves. */
    public static final long DUSK = 13000;
    public static final int WAVES = 6, WAVE_TICKS = 900, STARS_PER_WAVE = 5, MAX_PLAYERS = 4;
    /** A max-level forge within this distance of a player guarantees it at full moon (blocks). */
    public static final double FORGE_RANGE = 16;
    /** Where the Mulas come down: this far along the stars' way (blocks); how many in a group. */
    public static final int MIN_SITE = 150, MAX_SITE = 600, MIN_GROUP = 3, MAX_GROUP = 5;

    private static final class Event {
        long day;
        int waves;
        long nextWave;
        final java.util.Set<java.util.UUID> sited = new java.util.HashSet<>();
    }

    private static final Map<RegistryKey<World>, Long> ROLLED = new HashMap<>();
    private static final Map<RegistryKey<World>, Event> EVENTS = new HashMap<>();

    public static void initialize() {
        ServerTickEvents.END_WORLD_TICK.register(MulaEphemeride::tick);
    }

    /** How full the moon is, 0 (new) to 1 (full), from the vanilla phase (0 full ... 4 new ... 7). */
    public static double moonFullness(int moonPhase) {
        int p = Math.floorMod(moonPhase, 8);
        return 1 - Math.min(p, 8 - p) / 4.0;
    }

    public static double chance(int moonPhase) {
        return MAX_CHANCE * Math.pow(moonFullness(moonPhase), CURVE);
    }

    /** Full moon and a player by a max-level Dice Forge. */
    public static boolean guaranteed(ServerWorld world, int moonPhase, List<? extends ServerPlayerEntity> players) {
        if (Math.floorMod(moonPhase, 8) != 0) return false;
        int r = (int) FORGE_RANGE;
        for (ServerPlayerEntity player : players) {
            BlockPos at = player.getBlockPos();
            for (BlockPos p : BlockPos.iterate(at.add(-r, -8, -r), at.add(r, 8, r))) {
                if (world.getBlockEntity(p) instanceof DiceForgeBlockEntity forge && forge.isMaxLevel()) return true;
            }
        }
        return false;
    }

    public static boolean isActive(ServerWorld world) {
        return EVENTS.containsKey(world.getRegistryKey());
    }

    /** Starts one now (the operator command, or the dusk roll). */
    public static void start(ServerWorld world) {
        Event e = new Event();
        e.day = world.getTimeOfDay() / 24000L;
        e.nextWave = world.getTime() + 60;
        EVENTS.put(world.getRegistryKey(), e);
        fr.lordfinn.steveparty.Steveparty.LOGGER.info("An ephemeride begins over {}", world.getRegistryKey().getValue());
    }

    private static void tick(ServerWorld world) {
        if (!world.getDimension().hasSkyLight() || world.getDimension().hasCeiling()) return;
        // the pending Mula groups: once a second
        if (world.getTime() % 20 == 11) {
            MulaSpawnSites sites = MulaSpawnSites.peek(world);
            if (sites != null && sites.pendingCount() > 0) sites.tick(world);
        }
        RegistryKey<World> key = world.getRegistryKey();
        long day = world.getTimeOfDay() / 24000L;
        if (Math.floorMod(world.getTimeOfDay(), 24000L) >= DUSK && world.isNight()
                && !Long.valueOf(day).equals(ROLLED.get(key))) {
            ROLLED.put(key, day);
            int phase = world.getMoonPhase();
            if (!isActive(world) && (guaranteed(world, phase, world.getPlayers())
                    || world.random.nextDouble() < chance(phase))) {
                start(world);
            }
        }
        Event e = EVENTS.get(key);
        if (e == null) return;
        if (!world.isNight() || e.waves >= WAVES) {
            EVENTS.remove(key);
            return;
        }
        if (world.getTime() < e.nextWave) return;
        e.nextWave = world.getTime() + WAVE_TICKS;
        e.waves++;
        List<ServerPlayerEntity> players = world.getPlayers(p -> !p.isSpectator());
        for (int i = 0; i < Math.min(MAX_PLAYERS, players.size()); i++) wave(world, players.get(i), e);
    }

    /** Stars crossing over a player, all the same way; after the first, where the Mulas will come down. */
    private static void wave(ServerWorld world, ServerPlayerEntity player, Event e) {
        Random random = world.random;
        double angle = random.nextDouble() * MathHelper.TAU;
        double dirX = Math.cos(angle), dirZ = Math.sin(angle);
        double sideX = -dirZ, sideZ = dirX;
        double ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ());
        for (int i = 0; i < STARS_PER_WAVE; i++) {
            double side = (random.nextDouble() - 0.5) * 24, back = 60 + random.nextDouble() * 30;
            double x = player.getX() - dirX * back + sideX * side, z = player.getZ() - dirZ * back + sideZ * side;
            double y = ground + 5 + random.nextDouble() * 5;
            MulaStarEntity star = new MulaStarEntity(ModEntities.MULA_STAR, world);
            // flat: a straight, low, grazing flight across and past the player
            star.launch(x, y, z, MulaEntity.MulaVariant.byId(random.nextInt(6)), dirX, dirZ,
                    150 + random.nextDouble() * 40, 0, 0);
            world.spawnEntity(star);
        }
        if (e.sited.add(player.getUuid())) {
            double d = MIN_SITE + random.nextDouble() * (MAX_SITE - MIN_SITE);
            BlockPos site = BlockPos.ofFloored(player.getX() + dirX * d, ground, player.getZ() + dirZ * d);
            MulaSpawnSites.get(world).add(site, world.getTime(), e.day, group(random));
        }
    }

    /** A little flock: mostly one colour, sometimes a friend of another (black stays rare). */
    public static int[] group(Random random) {
        int n = MIN_GROUP + random.nextInt(MAX_GROUP - MIN_GROUP + 1);
        int main = random.nextInt(5);
        int[] colours = new int[n];
        for (int i = 0; i < n; i++) colours[i] = main;
        if (random.nextInt(3) == 0) colours[n - 1] = random.nextInt(40) == 0 ? 5 : (main + 1 + random.nextInt(4)) % 5;
        return colours;
    }
}
