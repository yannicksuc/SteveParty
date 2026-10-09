package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.entities.custom.MulaEscorts;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import net.minecraft.block.Blocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;

/**
 * A benchmark of the Mulas' server side work in a crowd (the playtest of 2026-10-06 had hundreds of them round a few
 * players): 180 Mulas round one player, all active, a few walls round him: 16 of them tamed by him (as many as may
 * follow him), or 60. Each phase ticks every Mula {@code steveparty.mulaPerfRounds} times (300 by default) on top of the world's
 * own ticks and logs the time per Mula tick ({@code [mula perf]} lines) to compare a change against the previous code:
 * the owner standing still, then walking round a circle (the followers fly round the walls), then far from every Mula
 * (all idle). With 16 tamed, every one follows him, with or without the followers' limit (mulaMaxFollowers): the time
 * per tick compares with the previous code; with 60, only 16 follow him now.
 * <p>
 * (Test batches run side by side: these tests change neither the config nor what other tests share.)
 */
public class MulaPerfGameTests implements FabricGameTest {
    private static final int MULAS = 180;
    private static final int ROUNDS = Integer.getInteger("steveparty.mulaPerfRounds", 300);

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "mula_perf")
    public void benchmarkCrowdOfMulas(TestContext context) {
        benchmark(context, 16);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "mula_perf_many_tamed")
    public void benchmarkCrowdOfMulasManyTamed(TestContext context) {
        benchmark(context, 60);
    }

    private static void benchmark(TestContext context, int tamed) {
        ServerWorld world = context.getWorld();
        int maxFollowers = MulaEscorts.max();
        ServerPlayerEntity owner = TestPlayers.mock(context, GameMode.SURVIVAL);
        Vec3d centre = context.getAbsolute(new Vec3d(0.5, 1, 0.5));
        owner.refreshPositionAndAngles(centre.x, centre.y, centre.z, 0, 0);
        // walls on the owner's way round (followers beyond 8 blocks fly round them)
        BlockPos base = BlockPos.ofFloored(centre);
        List<BlockPos> walls = new ArrayList<>();
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3;
            BlockPos at = base.add((int) Math.round(Math.cos(a) * 9), 0, (int) Math.round(Math.sin(a) * 9));
            for (int w = -2; w <= 2; w++) for (int h = 0; h < 4; h++) {
                BlockPos wall = at.add(Math.abs(Math.sin(a)) > 0.5 ? w : 0, h, Math.abs(Math.sin(a)) > 0.5 ? 0 : w);
                world.setBlockState(wall, Blocks.STONE.getDefaultState());
                walls.add(wall);
            }
        }
        List<MulaEntity> mulas = new ArrayList<>();
        List<Vec3d> spawns = new ArrayList<>();
        for (int i = 0; i < MULAS; i++) {
            double a = i * 2.39996, r = 2 + (i % 6);
            MulaEntity mula = ModEntities.MULA_ENTITY.create(world);
            mula.refreshPositionAndAngles(centre.x + Math.cos(a) * r, centre.y + 1 + (i % 4), centre.z + Math.sin(a) * r, 0, 0);
            mula.setVariant(MulaEntity.MulaVariant.values()[i % 6]);
            if (i < tamed) mula.setOwner(owner);
            world.spawnEntity(mula);
            mulas.add(mula);
            spawns.add(mula.getPos());
        }
        context.waitAndRun(40, () -> {
            try {
                measure(world, mulas, ROUNDS, round -> { }); // warms the JIT up
                long still = measure(world, mulas, ROUNDS, round -> { });
                long walking = measure(world, mulas, ROUNDS, round -> {
                    double a = round * 0.02;
                    owner.refreshPositionAndAngles(centre.x + Math.cos(a) * 14, centre.y, centre.z + Math.sin(a) * 14, 0, 0);
                });
                // the followers went round with him; the others stayed where they were
                int followed = 0;
                for (int i = 0; i < tamed; i++) if (mulas.get(i).getPos().squaredDistanceTo(spawns.get(i)) > 6 * 6) followed++;
                owner.refreshPositionAndAngles(centre.x + 200, centre.y, centre.z, 0, 0);
                for (MulaEntity mula : mulas) mula.getMulaBrain().refreshNow();
                long alone = measure(world, mulas, ROUNDS, round -> { });
                Steveparty.LOGGER.info("[mula perf] {} Mulas ({} tamed, at most {} following): owner still {} us/Mula tick, owner walking {} us/Mula tick ({} followed him), no player near {} us/Mula tick",
                        mulas.size(), tamed, maxFollowers, still / 1000.0, walking / 1000.0, followed, alone / 1000.0);
                context.assertTrue(followed <= maxFollowers, "the followers' limit: " + followed + " followed");
                context.assertTrue(followed >= Math.min(tamed, maxFollowers) * 3 / 4, "the followers followed: " + followed);
                context.complete();
            } finally {
                for (MulaEntity mula : mulas) mula.discard();
                for (BlockPos wall : walls) world.setBlockState(wall, Blocks.AIR.getDefaultState());
                world.getServer().getPlayerManager().remove(owner);
            }
        });
    }

    /**
     * The followers' limit: of {@code mulaMaxFollowers} + 2 tamed Mulas, two stay where they are; once a follower sits
     * down, one of them takes the free place and follows.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "mula_followers_limit")
    public void mulasPastTheLimitWaitForAFreePlace(TestContext context) {
        ServerWorld world = context.getWorld();
        int max = MulaEscorts.max();
        ServerPlayerEntity owner = TestPlayers.mock(context, GameMode.SURVIVAL);
        Vec3d at = context.getAbsolute(new Vec3d(0.5, 1, 0.5));
        owner.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        List<MulaEntity> mulas = new ArrayList<>();
        for (int i = 0; i < max + 2; i++) {
            MulaEntity mula = ModEntities.MULA_ENTITY.create(world);
            // (inside the test's area: its chunks are the ones sure to tick)
            mula.refreshPositionAndAngles(at.x + 6.3 + (i % 2) * 0.6, at.y + 2.5 + (i / 6) * 0.6, at.z + ((i / 2) % 3) * 0.9, 0, 0);
            mula.setOwner(owner);
            world.spawnEntity(mula);
            mulas.add(mula);
        }
        Runnable cleanUp = () -> {
            for (MulaEntity mula : mulas) mula.discard();
            world.getServer().getPlayerManager().remove(owner);
        };
        context.waitAndRun(80, () -> {
            try {
                context.assertEquals(MulaEscorts.size(owner.getUuid()), max, "every place taken");
                List<MulaEntity> waiting = mulas.stream().filter(m -> !MulaEscorts.isFollower(owner.getUuid(), m)).toList();
                context.assertEquals(waiting.size(), 2, "two Mulas wait");
                for (MulaEntity mula : waiting) {
                    context.assertTrue(mula.squaredDistanceTo(owner) > 6.5 * 6.5, "a waiting Mula stays where it was: "
                            + Math.sqrt(mula.squaredDistanceTo(owner)));
                }
                for (MulaEntity mula : mulas) {
                    if (MulaEscorts.isFollower(owner.getUuid(), mula)) {
                        context.assertTrue(mula.squaredDistanceTo(owner) <= 6 * 6, "a follower came: "
                                + Math.sqrt(mula.squaredDistanceTo(owner)));
                    }
                }
                mulas.stream().filter(m -> MulaEscorts.isFollower(owner.getUuid(), m)).findFirst().orElseThrow().setSitting(true);
                context.waitAndRun(100, () -> {
                    try {
                        long came = waiting.stream().filter(m -> m.squaredDistanceTo(owner) <= 6 * 6).count();
                        context.assertEquals(came, 1L, "one waiting Mula took the free place");
                        context.complete();
                    } finally {
                        cleanUp.run();
                    }
                });
            } catch (RuntimeException e) {
                cleanUp.run();
                throw e;
            }
        });
    }

    private interface Step {
        void run(int round);
    }

    /** Nanoseconds per Mula tick, over {@code rounds} ticks of every Mula. */
    private static long measure(ServerWorld world, List<MulaEntity> mulas, int rounds, Step step) {
        long start = System.nanoTime();
        for (int round = 0; round < rounds; round++) {
            step.run(round);
            for (MulaEntity mula : mulas) {
                if (!mula.isRemoved()) world.tickEntity(mula);
            }
        }
        return (System.nanoTime() - start) / ((long) rounds * mulas.size());
    }
}
