package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.entities.custom.MulaHome;
import fr.lordfinn.steveparty.entities.custom.MulaRebirths;
import fr.lordfinn.steveparty.entities.custom.MulaStarEntity;
import fr.lordfinn.steveparty.entities.custom.goals.MulaBrain;
import fr.lordfinn.steveparty.entities.custom.goals.SimpleFlyingMoveControl;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * A Dice Forge with its core holds the Mulas around it (MulaHome): they dance, never leave its area, are reborn at it
 * after a burst; without a core it holds none; its core exploding sends them all away. Each test in its own batch: a
 * forge takes in the Mulas of neighbouring tests.
 */
public class MulaHomeGameTests implements FabricGameTest {
    private static final BlockPos FORGE_POS = new BlockPos(4, 1, 4);

    /**
     * Forges left by the tests of earlier batches (some with their core in) would take in this test's Mulas: removed
     * (batches run one after the other, so those tests are over).
     */
    static void removeOtherForges(TestContext context, BlockPos keep) {
        ServerWorld world = context.getWorld();
        BlockPos center = context.getAbsolutePos(keep);
        for (BlockPos p : BlockPos.iterate(center.add(-24, -8, -24), center.add(24, 8, 24))) {
            if (!p.equals(center) && world.getBlockEntity(p) instanceof DiceForgeBlockEntity other) {
                other.clear(); // nothing dropped
                world.setBlockState(p, Blocks.AIR.getDefaultState());
            }
        }
    }

    private static DiceForgeBlockEntity forge(TestContext context, boolean core) {
        removeOtherForges(context, FORGE_POS);
        context.setBlockState(FORGE_POS, ModBlocks.DICE_FORGE.getDefaultState());
        DiceForgeBlockEntity forge = context.getBlockEntity(FORGE_POS);
        if (core) forge.setStack(DiceForgeBlockEntity.CENTER_SLOT, new ItemStack(ModBlocks.GRAVITY_CORE));
        return forge;
    }

    private static List<MulaEntity> mulas(TestContext context, int n) {
        List<MulaEntity> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1 + 3 * i, 3, 1)));
        return list;
    }

    /** Without its core a forge holds no Mula (no dance, no home); with it, they live and dance there. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = "mula_home_core")
    public void onlyAForgeWithItsCoreHoldsMulas(TestContext context) {
        DiceForgeBlockEntity forge = forge(context, false);
        List<MulaEntity> mulas = mulas(context, 3);
        context.runAtTick(30, () -> {
            for (MulaEntity m : mulas) {
                context.assertTrue(!m.isDancing() && m.homeForge() == null, "no core: no dance, no home");
            }
            forge.setStack(DiceForgeBlockEntity.CENTER_SLOT, new ItemStack(ModBlocks.GRAVITY_CORE));
        });
        context.runAtTick(60, () -> {
            for (MulaEntity m : mulas) {
                context.assertTrue(m.isDancing(), "with its core: they dance");
                context.assertTrue(context.getAbsolutePos(FORGE_POS).equals(m.homeForge()), "and live there");
            }
            mulas.forEach(MulaEntity::discard);
            context.complete();
        });
    }

    /** Taking the core out releases them: the dance stops and they no longer live there. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 140, batchId = "mula_home_release")
    public void removingTheCoreReleasesTheMulas(TestContext context) {
        DiceForgeBlockEntity forge = forge(context, true);
        List<MulaEntity> mulas = mulas(context, 3);
        context.runAtTick(DiceForgeBlockEntity.CORE_INSERT_TICKS + 20, () -> {
            for (MulaEntity m : mulas) context.assertTrue(m.homeForge() != null, "at home");
            context.assertTrue(forge.removeCore(null), "core taken out");
        });
        context.runAtTick(DiceForgeBlockEntity.CORE_INSERT_TICKS + 50, () -> {
            for (MulaEntity m : mulas) {
                context.assertTrue(m.homeForge() == null && !m.isDancing(), "released: no home, no dance");
            }
            mulas.forEach(MulaEntity::discard);
            context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(context.getAbsolutePos(FORGE_POS)).expand(4),
                    e -> true).forEach(ItemEntity::discard);
            context.complete();
        });
    }

    /** A wild Mula at home is never sent out of the area: flights, flights, hideouts. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "mula_home_targets")
    public void aMulaAtHomeNeverGetsTargetsOutside(TestContext context) {
        forge(context, true);
        BlockPos home = context.getAbsolutePos(FORGE_POS);
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(2, 3, 2));
        mula.setAiDisabled(true);
        mula.setHomeForge(home);
        SimpleFlyingMoveControl move = (SimpleFlyingMoveControl) mula.getMoveControl();
        double[][] far = {{400, 90, 0}, {-60, -30, 25}, {0, 200, -3000}, {15, 0, 15}};
        for (double[] d : far) {
            move.moveTo(home.getX() + d[0], home.getY() + d[1], home.getZ() + d[2], 0.3);
            context.assertTrue(MulaHome.contains(home, move.targetX(), move.targetY(), move.targetZ()),
                    "flight target inside: " + move.targetX() + " " + move.targetY() + " " + move.targetZ());
            move.moveThrough(home.getX() + d[0], home.getY() + d[1], home.getZ() + d[2], 0.3);
            context.assertTrue(MulaHome.contains(home, move.targetX(), move.targetY(), move.targetZ()), "arc point inside");
        }
        // hit from outside: it hides, inside
        for (int i = 0; i < 10; i++) {
            mula.getMulaBrain().onHurt(null);
            Vec3d hide = mula.getMulaBrain().shyTarget();
            context.assertTrue(MulaHome.contains(home, hide.x, hide.y, hide.z), "hideout inside: " + hide);
        }
        Vec3d kept = mula.keepHome(new Vec3d(home.getX() + 100, home.getY() + 50, home.getZ()));
        context.assertTrue(MulaHome.contains(home, kept.x, kept.y, kept.z), "wander / play / flock spots inside");
        mula.discard();
        context.complete();
    }

    /** Its home is saved with it; it is released when the forge is broken. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "mula_home_save")
    public void homeIsSavedAndReleasedWhenTheForgeIsBroken(TestContext context) {
        ServerWorld world = context.getWorld();
        forge(context, true);
        BlockPos home = context.getAbsolutePos(FORGE_POS);
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(2, 3, 2));
        mula.setAiDisabled(true);
        mula.setHomeForge(home);
        NbtCompound saved = new NbtCompound();
        mula.saveSelfNbt(saved);
        MulaEntity reloaded = ModEntities.MULA_ENTITY.create(world);
        reloaded.readNbt(saved);
        context.assertTrue(home.equals(reloaded.homeForge()), "home read back: " + reloaded.homeForge());
        mula.checkHome();
        context.assertTrue(home.equals(mula.homeForge()), "kept while the forge holds");
        context.setBlockState(FORGE_POS, Blocks.AIR);
        mula.checkHome();
        context.assertTrue(mula.homeForge() == null, "released when the forge is broken");
        mula.discard();
        context.complete();
    }

    /** At home, a burst loops up and falls back by the forge: reborn a few blocks from it, still at home. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "mula_home_burst")
    public void aBurstAtHomeIsRebornByTheForge(TestContext context) {
        ServerWorld world = context.getWorld();
        forge(context, true);
        BlockPos home = context.getAbsolutePos(FORGE_POS);
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 3, 7));
        mula.setHomeForge(home);
        java.util.UUID id = mula.getUuid();
        mula.burstIntoStar();
        MulaRebirths rebirths = MulaRebirths.get(world);
        MulaRebirths.Entry entry = rebirths.entries().stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
        context.assertTrue(entry != null, "rebirth recorded");
        double d = Math.hypot(entry.x() + 0.5 - (home.getX() + 0.5), entry.z() + 0.5 - (home.getZ() + 0.5));
        context.assertTrue(d <= 5, "lands by the forge, not 100-400 blocks away: " + d);
        rebirths.add(new MulaRebirths.Entry(id, entry.x(), entry.z(), entry.y(), world.getTime(), entry.mula()));
        rebirths.tick(world);
        context.assertTrue(world.getEntity(id) instanceof MulaEntity m && home.equals(m.homeForge())
                && m.squaredDistanceTo(home.getX() + 0.5, m.getY(), home.getZ() + 0.5) <= 36, "reborn by its forge, at home");
        world.getEntitiesByClass(MulaStarEntity.class,
                new Box(home).expand(20), e -> true).forEach(net.minecraft.entity.Entity::discard);
        if (world.getEntity(id) != null) world.getEntity(id).discard();
        context.complete();
    }

    /**
     * The core blows up: its three Mulas burst in a chain reaction, fly away round the compass (far: the forge can't
     * hold them), and drop no fragments.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 160, batchId = "mula_home_core_blast")
    public void theCoreBlowingUpSendsItsMulasAway(TestContext context) {
        ServerWorld world = context.getWorld();
        DiceForgeBlockEntity forge = forge(context, true);
        BlockPos home = context.getAbsolutePos(FORGE_POS);
        List<MulaEntity> mulas = mulas(context, 3);
        List<java.util.UUID> ids = mulas.stream().map(net.minecraft.entity.Entity::getUuid).toList();
        for (MulaEntity m : mulas) {
            m.setHomeForge(home);
            m.setAiDisabled(true);
        }
        context.runAtTick(DiceForgeBlockEntity.CORE_INSERT_TICKS + 5, () -> {
            world.getEntitiesByClass(ItemEntity.class, new Box(home).expand(24), e -> true).forEach(ItemEntity::discard);
            forge.explodeCore(null);
        });
        context.runAtTick(DiceForgeBlockEntity.CORE_INSERT_TICKS + 110, () -> {
            MulaRebirths rebirths = MulaRebirths.get(world);
            List<MulaRebirths.Entry> entries = rebirths.entries().stream().filter(e -> ids.contains(e.id())).toList();
            context.assertTrue(entries.size() == 3, "3 rebirths recorded: " + entries.size());
            List<Double> angles = new ArrayList<>();
            for (MulaRebirths.Entry e : entries) {
                double dx = e.x() + 0.5 - (home.getX() + 0.5), dz = e.z() + 0.5 - (home.getZ() + 0.5);
                context.assertTrue(Math.hypot(dx, dz) > 90, "sent far away: " + Math.hypot(dx, dz));
                angles.add(Math.atan2(dz, dx));
            }
            for (int i = 0; i < angles.size(); i++) {
                for (int j = i + 1; j < angles.size(); j++) {
                    double diff = Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(Math.toDegrees(angles.get(i) - angles.get(j))));
                    context.assertTrue(diff > 40, "different directions: " + diff);
                }
            }
            long fragments = world.getEntitiesByClass(ItemEntity.class, new Box(home).expand(24),
                    e -> MulaBrain.isStarFragment(e.getStack().getItem())).size();
            context.assertTrue(fragments == 0, "no fragments dropped: " + fragments);
            entries.forEach(e -> rebirths.remove(e.id()));
            world.getEntitiesByClass(MulaStarEntity.class,
                    new Box(home).expand(40), e -> true).forEach(net.minecraft.entity.Entity::discard);
            context.complete();
        });
    }
}
