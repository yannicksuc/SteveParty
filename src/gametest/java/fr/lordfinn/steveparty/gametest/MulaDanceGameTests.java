package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.MulaDances;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The Mulas' dances round a Dice Forge: formulas, rotation of the dances, the forge conducting its dancers. */
public class MulaDanceGameTests implements FabricGameTest {

    private static final BlockPos FORGE_POS = new BlockPos(4, 1, 4);

    /** Every figure, for 1 to 8 dancers, stays round the forge, leaps over the core, and never jumps. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void figuresStayRoundTheForgeAndFlow(TestContext context) {
        double[] out = new double[4], prev = new double[4];
        double dt = 1 / 20.0;
        for (int dance = 0; dance < MulaDances.COUNT; dance++) {
            for (int count = 1; count <= 8; count++) {
                for (int slot = 0; slot < count; slot++) {
                    MulaDances.offset(dance, slot, count, 0, prev);
                    for (double t = dt; t < MulaDances.DANCE_TICKS / 20.0; t += dt) {
                        MulaDances.offset(dance, slot, count, t, out);
                        double r = Math.sqrt(out[0] * out[0] + out[2] * out[2]);
                        String where = MulaDances.NAMES[dance] + " " + slot + "/" + count + " at " + t + " s";
                        context.assertTrue(r <= 3.6, "stays within 3.6 blocks: " + r + ", " + where);
                        context.assertTrue(out[1] >= -1.0 && out[1] <= 2.6, "height in [-1, 2.6]: " + out[1] + ", " + where);
                        context.assertTrue(r >= 0.6 || out[1] >= 0.4, "over the core, not through it: " + where);
                        double step = Math.sqrt(sq(out[0] - prev[0]) + sq(out[1] - prev[1]) + sq(out[2] - prev[2]));
                        context.assertTrue(step < 0.35, "no jump from one tick to the next: " + step + ", " + where);
                        System.arraycopy(out, 0, prev, 0, 4);
                    }
                }
            }
        }
        context.complete();
    }

    /** Changes of dance, and dancers joining (the slots change), are blended: still no jump. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void changesOfDanceAndOfDancersAreBlended(TestContext context) {
        BlockPos forge = new BlockPos(12, 64, -7);
        double[] out = new double[4], prev = new double[4], tmp = new double[4];
        for (int count = 1; count <= 8; count++) {
            for (int slot = 0; slot < count; slot++) {
                // someone joins at tick 700: count -> count + 1 (this one keeps its slot, the figure re-spreads)
                long join = 700;
                boolean joined = false;
                for (long time = 0; time < 3 * MulaDances.DANCE_TICKS; time++) {
                    int n = time >= join ? Math.min(8, count + 1) : count;
                    MulaDances.position(forge, slot, n, slot, count, join, time, 0f, out, tmp);
                    if (time > 0) {
                        double step = Math.sqrt(sq(out[0] - prev[0]) + sq(out[1] - prev[1]) + sq(out[2] - prev[2]));
                        context.assertTrue(step < 0.5, "blended: " + step + " at " + time + " (" + slot + "/" + n + ")");
                    }
                    System.arraycopy(out, 0, prev, 0, 4);
                    joined |= n != count;
                }
                context.assertTrue(joined || count == 8, "the join was tested");
            }
        }
        context.complete();
    }

    /** A forge plays all ten dances in turn; neighbouring forges don't dance the same one. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void dancesRotate(TestContext context) {
        BlockPos forge = new BlockPos(3, 70, 9);
        Set<Integer> seen = new HashSet<>();
        for (int period = 0; period < MulaDances.COUNT; period++) {
            seen.add(MulaDances.danceAt(period * (long) MulaDances.DANCE_TICKS + 5, forge));
        }
        context.assertTrue(seen.size() == MulaDances.COUNT, "all ten dances in ten periods: " + seen);
        context.assertTrue(MulaDances.danceAt(1000, forge) != MulaDances.danceAt(1000, forge.east()),
                "neighbouring forges dance different dances");
        context.assertTrue(MulaDances.NAMES.length == MulaDances.COUNT && MulaDances.STYLE.length == MulaDances.COUNT,
                "a name and an expression per dance");
        context.complete();
    }

    /**
     * Mulas near a forge are counted as its dancers (distinct slots, the right count, a sitting one left out), then glide
     * onto their place and are moved by the formula.
     */
    // its own batch: the forge would also conduct the Mulas of the neighbouring tests
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "mula_forge_dance")
    public void forgeConductsItsDancers(TestContext context) {
        context.setBlockState(FORGE_POS, ModBlocks.DICE_FORGE.getDefaultState());
        List<MulaEntity> dancers = new ArrayList<>();
        for (int i = 0; i < 3; i++) dancers.add(context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1 + 3 * i, 3, 1)));
        MulaEntity sitter = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(7, 3, 7));
        sitter.setSitting(true);
        context.runAtTick(45, () -> {
            Set<Integer> slots = new HashSet<>();
            for (MulaEntity mula : dancers) {
                context.assertTrue(mula.isDancing(), "counted as a dancer");
                context.assertTrue(context.getAbsolutePos(FORGE_POS).equals(mula.danceForge()), "round this forge");
                slots.add(mula.danceSlot());
                context.assertTrue(mula.danceCount() == 3, "3 dancers: " + mula.danceCount());
            }
            context.assertTrue(slots.size() == 3, "distinct slots: " + slots);
            context.assertTrue(!sitter.isDancing(), "a sitting Mula stays sitting");
        });
        context.runAtTick(200, () -> {
            double[] out = new double[4];
            for (MulaEntity mula : dancers) {
                context.assertTrue(mula.isDanceLocked(), "on its place in the figure");
                mula.dancePosition(0f, out);
                context.assertTrue(mula.squaredDistanceTo(out[0], out[1], out[2]) < 0.5 * 0.5,
                        "moved by the formula: " + mula.getPos() + " vs " + out[0] + ", " + out[1] + ", " + out[2]);
            }
            dancers.forEach(MulaEntity::discard);
            sitter.discard();
            context.complete();
        });
    }

    /** A risen core's gravity doesn't pull the Mulas (they dance round it instead). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mulasAreNotPulledByTheCore(TestContext context) {
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(2, 3, 2));
        mula.setAiDisabled(true);
        mula.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
        net.minecraft.util.math.Vec3d core = mula.getPos().add(3, 1, 0);
        fr.lordfinn.steveparty.utils.GravityPull.pullAround(context.getWorld(), core, 10, 1, false, 0);
        context.assertTrue(mula.getVelocity().lengthSquared() == 0, "not pulled: " + mula.getVelocity());
        context.complete();
    }

    private static double sq(double v) {
        return v * v;
    }
}
