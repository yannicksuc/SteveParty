package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mulas died of falls (coming down from the night sky, pulled down by a lead) and were solid boxes shoving each other:
 * now nothing of their own flying life hurts them, they pass through each other, and a lead pulls them gently.
 */
public class MulaSafetyGameTests implements FabricGameTest {

    /** Falls, crashes, walls, water, fire, lava, hot floors, cramming, another Mula: 0 damage. A player still hurts. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void immuneToItsOwnFlyingLife(TestContext context) {
        ServerWorld world = context.getWorld();
        DamageSources sources = world.getDamageSources();
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 3, 1));
        MulaEntity other = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(3, 3, 1));
        mula.setAiDisabled(true);
        Map<String, DamageSource> harmless = Map.of("fall", sources.fall(), "fly_into_wall", sources.flyIntoWall(),
                "in_wall", sources.inWall(), "cramming", sources.cramming(), "drown", sources.drown(),
                "in_fire", sources.inFire(), "on_fire", sources.onFire(), "lava", sources.lava(),
                "hot_floor", sources.hotFloor(), "another Mula", sources.mobAttack(other));
        harmless.forEach((name, source) -> {
            float before = mula.getHealth();
            mula.damage(source, 10f);
            context.assertTrue(mula.getHealth() == before, name + " deals 0: " + (before - mula.getHealth()));
        });
        context.assertTrue(!mula.handleFallDamage(40f, 1f, sources.fall()), "no fall damage from a long fall");
        context.assertTrue(mula.getType().isFireImmune(), "fire immune");
        ServerPlayerEntity player = TestPlayers.mock(context);
        BlockPos at = context.getAbsolutePos(new BlockPos(0, 2, 0));
        player.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        try {
            float before = mula.getHealth();
            mula.timeUntilRegen = 0;
            mula.damage(sources.playerAttack(player), 2f);
            context.assertTrue(mula.getHealth() < before, "a player can still hit it");
        } finally {
            world.getServer().getPlayerManager().remove(player);
        }
        context.complete();
    }

    /** Not a solid box: nothing stands on it, Mulas neither block nor push each other. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mulasAreNotSolid(TestContext context) {
        MulaEntity a = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 3, 1));
        MulaEntity b = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 3, 1));
        context.assertTrue(!a.isCollidable(), "not a solid box");
        context.assertTrue(!a.collidesWith(b) && !b.collidesWith(a), "Mulas pass through each other");
        b.setVelocity(Vec3d.ZERO);
        a.pushAwayFrom(b);
        context.assertTrue(b.getVelocity().lengthSquared() == 0 && a.getVelocity().lengthSquared() == 0,
                "and don't push each other");
        context.complete();
    }

    /** Eight Mulas packed in one spot, a player watching (flock, play...), for 10 s: no damage, none in a wall. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 260)
    public void packedMulasTakeNoDamage(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        BlockPos at = context.getAbsolutePos(new BlockPos(1, 2, 1));
        player.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        List<MulaEntity> mulas = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            MulaEntity m = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(3, 2, 3));
            m.setHunger(i * 5); // big ones too
            mulas.add(m);
        }
        context.waitAndRun(200, () -> {
            try {
                for (MulaEntity m : mulas) {
                    context.assertTrue(m.isAlive() && m.getHealth() == m.getMaxHealth(), "no damage: " + m.getHealth());
                    context.assertTrue(!m.isInsideWall(), "not in a wall: " + m.getPos());
                }
            } finally {
                TestPlayers.remove(context, player);
                mulas.forEach(MulaEntity::discard);
            }
            context.complete();
        });
    }

    /**
     * On a lead tied to a fence, at night (it wants to rise to the sky), high above the knot then pulled: gently, no
     * damage, never in the ground. Its own batch: it sets the time.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 260, batchId = "mula_leash_night")
    public void leashedAtNightTakesNoDamage(TestContext context) {
        ServerWorld world = context.getWorld();
        long time = world.getTimeOfDay();
        world.setTimeOfDay(14000);
        ServerPlayerEntity player = TestPlayers.mock(context);
        BlockPos at = context.getAbsolutePos(new BlockPos(0, 2, 0));
        player.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        BlockPos fence = new BlockPos(2, 2, 2);
        context.setBlockState(fence.down(), Blocks.STONE);
        context.setBlockState(fence, Blocks.OAK_FENCE);
        // uneven ground round it
        context.setBlockState(new BlockPos(3, 2, 2), Blocks.STONE);
        context.setBlockState(new BlockPos(3, 3, 2), Blocks.STONE);
        LeashKnotEntity knot = LeashKnotEntity.getOrCreate(world, context.getAbsolutePos(fence));
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(3, 9, 2));
        mula.setHunger(30);
        mula.attachLeash(knot, true);
        context.waitAndRun(200, () -> {
            try {
                context.assertTrue(mula.isAlive() && mula.getHealth() == mula.getMaxHealth(), "no damage: " + mula.getHealth());
                context.assertTrue(!mula.isInsideWall() && world.isSpaceEmpty(mula), "not in the ground: " + mula.getPos());
            } finally {
                world.setTimeOfDay(time);
                world.getServer().getPlayerManager().remove(player);
                mula.discard();
                knot.discard();
            }
            context.complete();
        });
    }
}
