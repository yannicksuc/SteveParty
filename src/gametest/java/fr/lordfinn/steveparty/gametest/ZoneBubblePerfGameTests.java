package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.minigame.zone.MiniGameZone;
import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubble;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubbles;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * What the mini-game bubble costs the server, hook by hook, and at scale. The figures go to the log
 * ({@code [zone-bubble-perf]}); the test only fails on a result an order of magnitude off.
 * <ul>
 *   <li>each hook, in nanoseconds per call: no session, one session elsewhere, ten sessions elsewhere (ten pages
 *   played at once), and in a zone in session;</li>
 *   <li>journaling 103 823 changes (a 47-block cube): per change, memory, bytes written to disk;</li>
 *   <li>restoring them: whole, and the slice one tick costs;</li>
 *   <li>beginning (and ending) a session at the caps: 1024 full chests and 1024 entities.</li>
 * </ul>
 */
public class ZoneBubblePerfGameTests implements FabricGameTest {
    private static final int ROUNDS = 4;
    private static final int CALLS = 1_000_000;

    private static BlockPos at(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    /** The best of a few rounds, in ns per call (the machine does other things meanwhile). */
    private static double time(int calls, IntConsumer call) {
        double best = Double.MAX_VALUE;
        for (int round = 0; round < ROUNDS; round++) {
            long t = System.nanoTime();
            for (int i = 0; i < calls; i++) call.accept(i);
            best = Math.min(best, (System.nanoTime() - t) / (double) calls);
        }
        return best;
    }

    private static long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        for (int i = 0; i < 3; i++) System.gc();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static long size(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.size(file) : -1;
        } catch (IOException e) {
            return -1;
        }
    }

    private static ZoneBubble begin(MinecraftServer server, MiniGameZone zone) {
        return ZoneBubbles.begin(server, UUID.randomUUID(), zone, List.of(), List.of(), ZoneBubble.Options.DEFAULT);
    }

    /** What every hook costs, by case: the calls of the game itself, and the checks reached once a session runs. */
    private static Map<String, Double> hooks(TestContext context, BlockPos place, ArmorStandEntity walker, HopperBlockEntity hopper, boolean idle) {
        ServerWorld world = context.getWorld();
        BlockState stone = Blocks.STONE.getDefaultState(), air = Blocks.AIR.getDefaultState();
        Vec3d start = walker.getPos();
        DamageSource damage = world.getDamageSources().generic();
        BlockPos other = place.add(1, 0, 0);
        List<BlockPos> blast = List.of(place, other, place.up());
        Vec3d center = Vec3d.ofCenter(place);
        BlockHitResult hit = new BlockHitResult(center, Direction.UP, other, false);
        Map<String, Double> costs = new LinkedHashMap<>();
        costs.put("World.setBlockState", time(20_000, i -> world.setBlockState(place, (i & 1) == 0 ? stone : air, Block.NOTIFY_LISTENERS)));
        world.setBlockState(place, air, Block.NOTIFY_LISTENERS);
        costs.put("Entity.setPos (new block)", time(CALLS, i -> walker.setPos(start.x + (i & 1), start.y, start.z)));
        costs.put("randomTick", time(CALLS, i -> stone.randomTick(world, place.down(), world.random)));
        costs.put("hopper extract", time(100_000, i -> HopperBlockEntity.extract(world, hopper)));
        if (idle) return costs;
        // reached only once a session runs (the mixins read ZoneBorder.ACTIVE first)
        costs.put("enter+exit (ticks)", time(CALLS, i -> {
            ZoneBorder.enter(world, place);
            ZoneBorder.exit(world);
        }));
        costs.put("across (merge/hopper/dispenser)", time(CALLS, i -> ZoneBorder.across(world, place, other)));
        costs.put("blocksSpawn", time(CALLS, i -> ZoneBorder.blocksSpawn(world, walker)));
        costs.put("blocksDamage", time(CALLS, i -> ZoneBorder.blocksDamage(walker, damage)));
        costs.put("blocksHit", time(CALLS, i -> ZoneBorder.blocksHit(walker, hit)));
        costs.put("explosionBlocks (3)", time(CALLS, i -> ZoneBorder.explosionBlocks(world, center, blast)));
        return costs;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zone_bubble_perf", tickLimit = 600)
    public void hooksAndScale(TestContext context) {
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        context.assertTrue(ZoneBubbles.all().isEmpty(), "no session left by other tests");
        BlockPos base = context.getAbsolutePos(at(0, 0, 0));
        // where the events happen when they are out of every zone
        BlockPos outside = base.add(2, 2, 2);
        ArmorStandEntity walker = context.spawnEntity(EntityType.ARMOR_STAND, at(2, 1, 6));
        walker.setNoGravity(true);
        BlockPos hopperPos = base.add(5, 1, 5);
        world.setBlockState(hopperPos, Blocks.HOPPER.getDefaultState());
        HopperBlockEntity hopper = (HopperBlockEntity) world.getBlockEntity(hopperPos);

        // ten zones of 16 blocks, side by side away from the test: ten pages played at once
        List<MiniGameZone> zones = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            BlockPos corner = base.add(40 + (i % 5) * 20, 4, (i / 5) * 20);
            zones.add(MiniGameZone.of(world.getRegistryKey(), corner, corner.add(15, 15, 15)));
        }

        Map<String, Double> idle = hooks(context, outside, walker, hopper, true);
        List<ZoneBubble> bubbles = new ArrayList<>();
        bubbles.add(begin(server, zones.get(0)));
        Map<String, Double> one = hooks(context, outside, walker, hopper, false);
        for (int i = 1; i < 10; i++) bubbles.add(begin(server, zones.get(i)));
        for (ZoneBubble bubble : bubbles) context.assertTrue(bubble.isActive(), "the ten sessions begin");
        Map<String, Double> ten = hooks(context, outside, walker, hopper, false);

        // in a zone in session: the same calls, there (the block already journaled: a later change)
        BlockPos inside = zones.get(9).box().getCenter();
        ArmorStandEntity insider = new ArmorStandEntity(world, inside.getX() + 0.5, inside.getY() + 2, inside.getZ() + 0.5);
        insider.setNoGravity(true);
        world.spawnEntity(insider);
        BlockPos innerHopper = inside.add(-3, 0, -3);
        world.setBlockState(innerHopper, Blocks.HOPPER.getDefaultState());
        Map<String, Double> here = hooks(context, inside, insider, (HopperBlockEntity) world.getBlockEntity(innerHopper), false);
        for (ZoneBubble bubble : bubbles) bubble.endNow();
        context.assertTrue(ZoneBubbles.all().isEmpty() && !ZoneBorder.ACTIVE, "the ten sessions are over");
        context.assertTrue(world.getBlockState(innerHopper).isAir(), "the zone is put back");

        StringBuilder table = new StringBuilder("\n[zone-bubble-perf] hook (ns/call)                     idle   1 elsewhere  10 elsewhere  here");
        for (String hook : ten.keySet()) {
            table.append(String.format("%n[zone-bubble-perf] %-34s %7s %10.1f %13.1f %8.1f", hook,
                    idle.containsKey(hook) ? String.format("%.1f", idle.get(hook)) : "-", one.get(hook), ten.get(hook), here.get(hook)));
        }
        Steveparty.LOGGER.info(table.toString());

        // ------------------------------------------------------------------ journaling 103 823 changes
        int side = 47;
        BlockPos corner = base.add(0, 30, 40);
        MiniGameZone big = MiniGameZone.of(world.getRegistryKey(), corner, corner.add(side - 1, side - 1, side - 1));
        List<BlockPos> cells = new ArrayList<>(side * side * side);
        for (BlockPos pos : BlockPos.iterate(corner, corner.add(side - 1, side - 1, side - 1))) cells.add(pos.toImmutable());
        BlockState stone = Blocks.STONE.getDefaultState();
        ZoneBubble journaled = begin(server, big);
        context.assertTrue(journaled.isActive(), "the big session begins");
        long before = usedMemory();
        long t = System.nanoTime();
        for (BlockPos pos : cells) world.setBlockState(pos, stone, Block.NOTIFY_LISTENERS);
        double perChange = (System.nanoTime() - t) / (double) cells.size();
        long memory = usedMemory() - before;
        context.assertTrue(journaled.journalSize() == cells.size(), "every change is journaled");
        Path directory = server.getSavePath(WorldSavePath.ROOT).resolve("steveparty").resolve("zone_bubbles").resolve("sessions")
                .resolve(journaled.sessionId().toString());

        context.waitAndRun(20, () -> {
            long journalBytes = size(directory.resolve("journal.log"));
            long sessionBytes = size(directory.resolve("session.dat"));
            long t2 = System.nanoTime();
            journaled.endNow();
            double restoreMs = (System.nanoTime() - t2) / 1.0e6;
            for (int i = 0; i < cells.size(); i += 997) context.assertTrue(world.getBlockState(cells.get(i)).isAir(), "the big zone is air again");

            // one tick's slice of a restoration (the default budget), what each bubble restoring costs a tick
            ZoneBubble sliced = begin(server, big);
            for (BlockPos pos : cells) world.setBlockState(pos, stone, Block.NOTIFY_LISTENERS);
            long t3 = System.nanoTime();
            sliced.end();
            double sliceMs = (System.nanoTime() - t3) / 1.0e6;
            context.assertTrue(sliced.isRestoring(), "a big restoration takes several ticks");
            sliced.endNow();

            // a session at the caps: 1024 full chests, 1024 entities
            List<BlockPos> chests = cells.subList(0, 1024);
            for (BlockPos pos : chests) {
                world.setBlockState(pos, Blocks.CHEST.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                ChestBlockEntity chest = (ChestBlockEntity) world.getBlockEntity(pos);
                for (int slot = 0; slot < chest.size(); slot++) chest.setStack(slot, new ItemStack(Items.DIAMOND, 1 + slot));
            }
            for (int i = 0; i < 1024; i++) {
                ArmorStandEntity stand = new ArmorStandEntity(world, corner.getX() + 10.5 + (i % 32), corner.getY() + 20, corner.getZ() + 10.5 + (i / 32));
                stand.setNoGravity(true);
                world.spawnEntity(stand);
            }
            long t4 = System.nanoTime();
            ZoneBubble crowded = begin(server, big);
            double beginMs = (System.nanoTime() - t4) / 1.0e6;
            context.assertTrue(crowded.isActive(), "a session begins at the caps");
            for (BlockPos pos : chests) ((ChestBlockEntity) world.getBlockEntity(pos)).clear();
            long t5 = System.nanoTime();
            crowded.endNow();
            double endMs = (System.nanoTime() - t5) / 1.0e6;
            context.assertTrue(((ChestBlockEntity) world.getBlockEntity(chests.get(0))).getStack(26).getCount() == 27, "the chests are full again");
            for (BlockPos pos : chests) {
                ((ChestBlockEntity) world.getBlockEntity(pos)).clear();
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            }
            for (Entity entity : world.getEntitiesByType(EntityType.ARMOR_STAND, big.bounds(), e -> true)) entity.discard();
            walker.discard();
            insider.discard();
            world.setBlockState(hopperPos, Blocks.AIR.getDefaultState());

            Steveparty.LOGGER.info("[zone-bubble-perf] journal: {} changes, first change {} ns, memory ~{} B/change (map + unflushed buffer), disk {} B/change (journal.log {} B, session.dat {} B)",
                    cells.size(), Math.round(perChange), Math.round(memory / (double) cells.size()),
                    String.format("%.1f", journalBytes / (double) cells.size()), journalBytes, sessionBytes);
            Steveparty.LOGGER.info("[zone-bubble-perf] restore: {} blocks in {} ms ({} blocks/ms); one tick's slice (default budget) {} ms",
                    cells.size(), String.format("%.1f", restoreMs), Math.round(cells.size() / restoreMs), String.format("%.2f", sliceMs));
            Steveparty.LOGGER.info("[zone-bubble-perf] caps: begin with 1024 full chests and 1024 entities {} ms, end {} ms",
                    String.format("%.1f", beginMs), String.format("%.1f", endMs));
            context.assertTrue(restoreMs < 10_000, "restoring took " + restoreMs + " ms");
            context.assertTrue(beginMs < 2_000, "beginning took " + beginMs + " ms");
            context.complete();
        });
    }
}
