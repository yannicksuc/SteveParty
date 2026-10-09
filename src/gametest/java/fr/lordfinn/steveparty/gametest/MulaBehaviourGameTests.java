package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.entities.custom.goals.MulaBrain;
import fr.lordfinn.steveparty.entities.custom.goals.MulaGoals;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/** The Mula's behaviours: orbiting an idle owner, flock neighbour cache, curiosity distances, night altitude, shyness. */
public class MulaBehaviourGameTests implements SteveGameTest {

    /** A tamed Mula starts circling its owner after the owner has stood still for a few seconds. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void orbitStartsWhenTheOwnerStandsStill(TestContext context) {
        ServerPlayerEntity owner = TestPlayers.mock(context);
        BlockPos at = context.getAbsolutePos(new BlockPos(1, 2, 1));
        owner.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(2, 4, 1));
        mula.setOwner(owner);
        context.assertTrue(!mula.getMulaBrain().orbiting, "not orbiting at once");
        context.waitAndRun(150, () -> {
            try {
                context.assertTrue(mula.getMulaBrain().orbiting, "orbits its still owner (still for "
                        + mula.getMulaBrain().ownerStillTicks() + " ticks)");
            } finally {
                TestPlayers.remove(context, owner);
            }
            context.complete();
        });
    }

    /** Each Mula only ever looks at its 3 nearest. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void flockNeighbourCacheIsBounded(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        // an audience: Mulas far from every player don't look around
        BlockPos at = context.getAbsolutePos(new BlockPos(1, 2, 1));
        player.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        try {
            List<MulaEntity> mulas = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                mulas.add(context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1 + i % 3, 3 + i / 3, 1 + i % 2)));
            }
            for (MulaEntity mula : mulas) {
                mula.getMulaBrain().refreshNow();
                int n = mula.getMulaBrain().neighbourCount();
                context.assertTrue(n >= 1 && n <= MulaBrain.MAX_NEIGHBOURS, "neighbours in [1, 3]: " + n);
                for (int i = 0; i < n; i++) {
                    context.assertTrue(mula.getMulaBrain().neighbour(i) != mula, "not itself");
                    if (i > 0) {
                        context.assertTrue(mula.getMulaBrain().neighbour(i - 1).squaredDistanceTo(mula)
                                <= mula.getMulaBrain().neighbour(i).squaredDistanceTo(mula), "nearest first");
                    }
                }
            }
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    /** Shy but feedable: within reach normally, closer if the player stays still, backs off if they rush. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void curiosityDistances(TestContext context) {
        double normal = MulaBrain.curiousDistance(0, false, 0);
        context.assertTrue(normal < 3.0, "within the 3-block reach: " + normal);
        context.assertTrue(MulaBrain.curiousDistance(60, false, 0) < normal, "closer when the player stays still");
        context.assertTrue(MulaBrain.curiousDistance(60, true, 0) > normal, "backs off from a sprinting player");
        context.assertTrue(MulaBrain.curiousDistance(0, false, 0.3) > normal, "backs off from a rushing player");
        context.complete();
    }

    /** At night it rises 10 to 18 blocks above the ground, never above the build limit. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nightAltitudeIsBounded(TestContext context) {
        for (int seed = 0; seed < 50; seed++) {
            double y = MulaBrain.nightAltitude(64, 320, seed);
            context.assertTrue(y >= 64 + 10 && y <= 64 + 18, "10 to 18 blocks above the ground: " + y);
            context.assertTrue(MulaBrain.nightAltitude(315, 320, seed) <= 318, "below the build limit");
        }
        context.assertTrue(MulaBrain.isMorning(0) && MulaBrain.isMorning(23500) && !MulaBrain.isMorning(6000)
                && !MulaBrain.isMorning(18000), "morning window");
        context.complete();
    }

    /** In the morning it finds the flowers to perch on from its night altitude (it used to look only 10 blocks down). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void dawnPerchIsFoundFromTheNightSky(TestContext context) {
        // on the roof of the test area (it has a barrier ceiling): room for 18 blocks of sky above; removed afterwards
        ServerWorld world = context.getWorld();
        BlockPos base = context.getAbsolutePos(new BlockPos(0, 10, 0));
        List<BlockPos> placed = new ArrayList<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                BlockPos ground = base.add(x, 0, z);
                world.setBlockState(ground, Blocks.GRASS_BLOCK.getDefaultState());
                world.setBlockState(ground.up(), Blocks.POPPY.getDefaultState());
                placed.add(ground);
            }
        }
        BlockPos high = base.add(2, 1 + 18, 2);
        Random random = Random.create(7);
        BlockPos.Mutable probe = new BlockPos.Mutable();
        BlockPos found = null;
        for (int attempt = 0; attempt < 40 && found == null; attempt++) {
            found = MulaGoals.Sky.findPerch(world, high, random, probe);
        }
        boolean flower = found != null && world.getBlockState(found).isOf(Blocks.POPPY);
        for (BlockPos p : placed) {
            world.setBlockState(p.up(), Blocks.AIR.getDefaultState());
            world.setBlockState(p, Blocks.AIR.getDefaultState());
        }
        context.assertTrue(flower, "a flower found 18 blocks below: " + found);
        context.complete();
    }

    /**
     * The hitbox is the model's body cube at every size (it was 2 model pixels lower and smaller than the model, and
     * the gap grew with its size), with the eyes at the model's eyes.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void hitboxMatchesTheModelAtEverySize(TestContext context) {
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 2, 1));
        for (int hunger : new int[]{0, 10, 20, 30, MulaEntity.MAX_HUNGER - 1}) {
            mula.setHunger(hunger);
            mula.calculateDimensions();
            float scale = mula.getScaleFactor();
            double size = MulaEntity.MODEL_SIZE * scale;
            context.assertTrue(Math.abs(mula.getHeight() - size) < 1.0E-4 && Math.abs(mula.getWidth() - size) < 1.0E-4,
                    "hitbox = body cube x " + scale + ": " + mula.getWidth() + " x " + mula.getHeight());
            context.assertTrue(Math.abs(mula.getBoundingBox().minY - mula.getY()) < 1.0E-4
                            && Math.abs(mula.getBoundingBox().maxY - (mula.getY() + size)) < 1.0E-4,
                    "from its feet to the top of its body");
            context.assertTrue(Math.abs(mula.getStandingEyeHeight() - MulaEntity.MODEL_EYE_HEIGHT * scale) < 1.0E-4,
                    "eyes at the model's eyes: " + mula.getStandingEyeHeight());
        }
        context.complete();
    }

    /** Hit, a wild Mula flees away from what hit it; a tamed one hides on the far side of its owner. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shyFleesAwayOrBehindItsOwner(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            PigEntity threat = context.spawnMob(EntityType.PIG, new BlockPos(0, 2, 1));
            threat.setAiDisabled(true);
            MulaEntity wild = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(2, 3, 1));
            wild.getMulaBrain().onHurt(threat);
            Vec3d hide = wild.getMulaBrain().shyTarget();
            context.assertTrue(hide != null && hide.squaredDistanceTo(threat.getEyePos())
                    > wild.getPos().squaredDistanceTo(threat.getEyePos()), "flees away from the threat: " + hide);

            BlockPos ownerAt = context.getAbsolutePos(new BlockPos(3, 2, 3));
            player.refreshPositionAndAngles(ownerAt.getX() + 0.5, ownerAt.getY(), ownerAt.getZ() + 0.5, 0, 0);
            MulaEntity tamed = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(2, 3, 3));
            tamed.setOwner(player);
            tamed.getMulaBrain().onHurt(threat);
            Vec3d behind = tamed.getMulaBrain().shyTarget();
            Vec3d ownerToTarget = behind.subtract(player.getPos());
            Vec3d threatToOwner = player.getPos().subtract(threat.getEyePos());
            context.assertTrue(ownerToTarget.x * threatToOwner.x + ownerToTarget.z * threatToOwner.z > 0,
                    "hides on the far side of its owner: " + behind);
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }
}
