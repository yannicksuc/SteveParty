package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.entities.custom.goals.MulaBrain;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** The Mula's behaviours: orbiting an idle owner, flock neighbour cache, curiosity distances, night altitude, shyness. */
public class MulaBehaviourGameTests implements FabricGameTest {

    private static void disconnect(TestContext context, ServerPlayerEntity player) {
        context.getWorld().getServer().getPlayerManager().remove(player);
    }

    /** A tamed Mula starts circling its owner after the owner has stood still for a few seconds. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void orbitStartsWhenTheOwnerStandsStill(TestContext context) {
        ServerPlayerEntity owner = context.createMockCreativeServerPlayerInWorld();
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
                disconnect(context, owner);
            }
            context.complete();
        });
    }

    /** Each Mula only ever looks at its 3 nearest. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void flockNeighbourCacheIsBounded(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
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
            disconnect(context, player);
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

    /** At night it rises 8 to 20 blocks above the ground, never above the build limit. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nightAltitudeIsBounded(TestContext context) {
        for (int seed = 0; seed < 50; seed++) {
            double y = MulaBrain.nightAltitude(64, 320, seed);
            context.assertTrue(y >= 64 + 8 && y <= 64 + 20, "8 to 20 blocks above the ground: " + y);
            context.assertTrue(MulaBrain.nightAltitude(315, 320, seed) <= 318, "below the build limit");
        }
        context.assertTrue(MulaBrain.isMorning(0) && MulaBrain.isMorning(23500) && !MulaBrain.isMorning(6000)
                && !MulaBrain.isMorning(18000), "morning window");
        context.complete();
    }

    /** Hit, a wild Mula flees away from what hit it; a tamed one hides on the far side of its owner. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shyFleesAwayOrBehindItsOwner(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
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
            disconnect(context, player);
        }
        context.complete();
    }
}
