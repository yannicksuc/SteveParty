package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.MulaDances;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.utils.GravityPull;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Mulas' dances round a Dice Forge: formulas (growing with the dancers, rings), rotation of the dances, turns
 * beyond {@link MulaDances#CAP} dancers, spectators, the forge conducting its dancers.
 */
public class MulaDanceGameTests implements FabricGameTest {

    private static final BlockPos FORGE_POS = new BlockPos(4, 1, 4);
    /** Closest two dancers may come (centre to centre, blocks): a Mula is 9/16 wide. */
    private static final double MIN_GAP = 0.85;

    /** Every figure, for 1 to {@link MulaDances#CAP} dancers, stays within its extent, leaps over the core, never jumps. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void figuresStayRoundTheForgeAndFlow(TestContext context) {
        double[] out = new double[4], prev = new double[4];
        double dt = 1 / 20.0;
        for (int dance = 0; dance < MulaDances.COUNT; dance++) {
            for (int count = 1; count <= MulaDances.CAP; count++) {
                double extent = MulaDances.extent(dance, count);
                context.assertTrue(extent <= MulaDances.SPECTATOR_RADIUS - 2, "spectators clear of the dance");
                for (int slot = 0; slot < count; slot++) {
                    MulaDances.offset(dance, slot, count, 0, prev);
                    for (double t = dt; t < MulaDances.DANCE_TICKS / 20.0; t += dt) {
                        MulaDances.offset(dance, slot, count, t, out);
                        double r = Math.sqrt(out[0] * out[0] + out[2] * out[2]);
                        String where = MulaDances.IDS[dance] + " " + slot + "/" + count + " at " + t + " s";
                        context.assertTrue(r <= extent + 1e-6, "stays within " + extent + " blocks: " + r + ", " + where);
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

    /**
     * No two dancers closer than {@value #MIN_GAP} block, in every dance, for 5, 12, 20 and 40 Mulas (40:
     * {@link MulaDances#CAP} of them dance), sampled every 2 ticks over the whole dance.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void dancersNeverCollide(TestContext context) {
        for (int mulas : new int[]{5, 12, 20, 40}) {
            int count = Math.min(MulaDances.CAP, mulas);
            double[][] at = new double[count][4];
            for (int dance = 0; dance < MulaDances.COUNT; dance++) {
                double closest = Double.MAX_VALUE;
                for (int tick = 0; tick < MulaDances.DANCE_TICKS; tick += 2) {
                    for (int slot = 0; slot < count; slot++) MulaDances.offset(dance, slot, count, tick / 20.0, at[slot]);
                    for (int i = 0; i < count; i++) {
                        for (int j = i + 1; j < count; j++) {
                            closest = Math.min(closest, Math.sqrt(sq(at[i][0] - at[j][0]) + sq(at[i][1] - at[j][1])
                                    + sq(at[i][2] - at[j][2])));
                        }
                    }
                }
                context.assertTrue(closest >= MIN_GAP,
                        MulaDances.IDS[dance] + " with " + count + " dancers: two come " + closest + " apart");
            }
        }
        context.complete();
    }

    /** Up to {@value MulaDances#SINGLE} on one figure, then 2 and 3 rings, each dancer on one, outer rings fuller. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ringsShareTheDancers(TestContext context) {
        for (int count = 1; count <= MulaDances.CAP; count++) {
            int rings = MulaDances.rings(count), total = 0;
            context.assertTrue(rings == (count <= 12 ? 1 : count <= 24 ? 2 : 3), count + " dancers: " + rings + " rings");
            for (int ring = 0; ring < rings; ring++) {
                int size = MulaDances.ringSize(count, ring);
                context.assertTrue(size >= 1 && size <= 14, count + " dancers, ring " + ring + ": " + size);
                if (ring > 0) context.assertTrue(size >= MulaDances.ringSize(count, ring - 1), "outer rings hold more");
                total += size;
            }
            context.assertTrue(total == count, count + " dancers, " + total + " on the rings");
            int[] seen = new int[rings];
            for (int slot = 0; slot < count; slot++) seen[MulaDances.ringOf(slot, count)]++;
            for (int ring = 0; ring < rings; ring++) {
                context.assertTrue(seen[ring] == MulaDances.ringSize(count, ring), "slots fill ring " + ring);
            }
        }
        context.complete();
    }

    /**
     * Beyond {@link MulaDances#CAP} Mulas, the turns: {@link MulaDances#CAP} dance each time, nobody waits more than
     * ceil(eligible / CAP) dances, and over a whole cycle everyone dances exactly as often.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void turnsAreFair(TestContext context) {
        int cap = MulaDances.CAP;
        context.assertTrue(MulaDances.turnStart(12345, 20) == 0 && MulaDances.turnStart(999999, cap) == 0,
                "no turns while everyone dances");
        for (int eligible : new int[]{33, 40, 47, 64, 100}) {
            int cycle = eligible / gcd(eligible, cap);
            int within = (eligible + cap - 1) / cap;
            int[] danced = new int[eligible], lastDanced = new int[eligible];
            Arrays.fill(lastDanced, -1);
            for (int period = 0; period < 3 * cycle; period++) {
                long time = (7L + period) * MulaDances.DANCE_TICKS + 3;
                int start = MulaDances.turnStart(time, eligible);
                context.assertTrue(MulaDances.turnStart(time + MulaDances.DANCE_TICKS - 10, eligible) == start,
                        "a turn lasts the whole dance");
                int dancing = 0;
                for (int rank = 0; rank < eligible; rank++) {
                    if (Math.floorMod(rank - start, eligible) >= cap) continue; // as the forge hands out the places
                    dancing++;
                    if (period < cycle) danced[rank]++;
                    if (period >= within) {
                        context.assertTrue(period - lastDanced[rank] <= within, eligible + " Mulas: rank " + rank
                                + " waited " + (period - lastDanced[rank]) + " dances");
                    }
                    lastDanced[rank] = period;
                }
                context.assertTrue(dancing == cap, cap + " dance at once: " + dancing);
            }
            for (int rank = 0; rank < eligible; rank++) {
                context.assertTrue(danced[rank] == cap * cycle / eligible,
                        eligible + " Mulas: rank " + rank + " danced " + danced[rank] + " times in " + cycle + " dances");
                context.assertTrue(lastDanced[rank] >= 3 * cycle - within, "everyone keeps dancing");
            }
        }
        context.complete();
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    /** Spectator spots: round the dance, out of its way, a block apart or more, terraces for a crowd. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void spectatorsWatchFromRoundTheDance(TestContext context) {
        double[] a = new double[3], b = new double[3];
        for (int count : new int[]{1, 8, 40, MulaDances.SPECTATORS_PER_ROW + 5}) {
            for (int i = 0; i < count; i++) {
                MulaDances.spectatorSpot(i, count, a);
                double r = Math.sqrt(a[0] * a[0] + a[2] * a[2]);
                context.assertTrue(Math.abs(r - MulaDances.SPECTATOR_RADIUS) < 1e-9 && r < 11, "round the dance: " + r);
                for (int j = i + 1; j < count; j++) {
                    MulaDances.spectatorSpot(j, count, b);
                    double d = Math.sqrt(sq(a[0] - b[0]) + sq(a[1] - b[1]) + sq(a[2] - b[2]));
                    context.assertTrue(d >= 1.0, count + " spectators: " + i + " and " + j + " " + d + " apart");
                }
            }
        }
        context.complete();
    }

    /** Changes of dance, dancers joining (slots change, a ring appears) and new turns are blended: no jump. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void changesOfDanceAndOfDancersAreBlended(TestContext context) {
        BlockPos forge = new BlockPos(12, 64, -7);
        double[] out = new double[4], prev = new double[4], tmp = new double[8];
        for (int count : new int[]{1, 2, 3, 5, 8, 11, 12, 13, 20, 24, 25, 31}) {
            for (int slot = 0; slot < count; slot++) {
                // someone joins at tick 700: count -> count + 1 (this one keeps its slot, the figure re-spreads); just
                // after the next change of dance a new turn gives it another slot (both blends at once)
                long join = 700, turn = 2L * MulaDances.DANCE_TICKS + 7;
                int n = count + 1, newSlot = (slot + 5) % n;
                for (long time = 0; time < 3 * MulaDances.DANCE_TICKS; time++) {
                    if (time < join) {
                        MulaDances.position(forge, slot, count, 0, 0, 0, time, 0f, out, tmp);
                    } else if (time < turn) {
                        MulaDances.position(forge, slot, n, slot, count, join, time, 0f, out, tmp);
                    } else {
                        MulaDances.position(forge, newSlot, n, slot, n, turn, time, 0f, out, tmp);
                    }
                    if (time > 0) {
                        double step = Math.sqrt(sq(out[0] - prev[0]) + sq(out[1] - prev[1]) + sq(out[2] - prev[2]));
                        context.assertTrue(step < 0.5, "blended: " + step + " at " + time + " (" + slot + "/" + count + ")");
                    }
                    System.arraycopy(out, 0, prev, 0, 4);
                }
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
        context.assertTrue(MulaDances.IDS.length == MulaDances.COUNT && MulaDances.STYLE.length == MulaDances.COUNT,
                "a name and an expression per dance");
        context.complete();
    }

    /**
     * Mulas near a forge are counted as its dancers (distinct slots, the right count, a sitting one left out), then glide
     * onto their place and are moved by the formula: the server's place is the shared formula's (the clients use it).
     */
    // its own batch: the forge would also conduct the Mulas of the neighbouring tests
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 320, batchId = "mula_forge_dance")
    public void forgeConductsItsDancers(TestContext context) {
        MulaHomeGameTests.removeOtherForges(context, FORGE_POS);
        placeForge(context);
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
            context.assertTrue(!sitter.isDancing() && !sitter.isSpectating(), "a sitting Mula stays sitting");
        });
        context.runAtTick(280, () -> {
            double[] out = new double[4], expected = new double[4];
            BlockPos forge = context.getAbsolutePos(FORGE_POS);
            long time = context.getWorld().getTime();
            // the test area is walled: a dancer whose place is in the wall leaves the figure and rejoins (by design),
            // so at least two of the three are on their place at any time
            int locked = 0;
            for (MulaEntity mula : dancers) {
                context.assertTrue(forge.equals(mula.homeForge()), "still at home");
                if (!mula.isDanceLocked()) continue;
                locked++;
                mula.dancePosition(0f, out);
                context.assertTrue(mula.squaredDistanceTo(out[0], out[1], out[2]) < 0.5 * 0.5,
                        "moved by the formula: " + mula.getPos() + " vs " + out[0] + ", " + out[1] + ", " + out[2]);
                if (time - MulaDances.danceStart(time) >= MulaDances.BLEND_TICKS
                        && time - mula.danceChangeTick() >= MulaDances.BLEND_TICKS) {
                    // no blend going on: exactly the shared figure round the core
                    MulaDances.offset(MulaDances.danceAt(time, forge), mula.danceSlot(), mula.danceCount(),
                            (time - MulaDances.danceStart(time)) / 20.0, expected);
                    double cy = Math.max(forge.getY() + 2.4, context.<DiceForgeBlockEntity>getBlockEntity(FORGE_POS).getCoreCenter().y);
                    double dy = cy - mula.getHeight() * MulaEntity.CENTER;
                    context.assertTrue(Math.abs(out[0] - forge.getX() - 0.5 - expected[0]) < 1e-9
                                    && Math.abs(out[1] - dy - expected[1]) < 1e-9 && Math.abs(out[2] - forge.getZ() - 0.5 - expected[2]) < 1e-9,
                            "the server's place is the shared formula's");
                }
            }
            context.assertTrue(locked >= 2, "on their place in the figure: " + locked);
            dancers.forEach(MulaEntity::discard);
            sitter.discard();
            context.complete();
        });
    }

    /**
     * The forge with 5, 12, 20 then 40 Mulas round it: each gets a slot (one figure, then rings) and, beyond
     * {@link MulaDances#CAP}, the others a spectator spot each, all distinct.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 260, batchId = "mula_forge_turns")
    public void forgeGivesEveryMulaAPlaceOrASeat(TestContext context) {
        MulaHomeGameTests.removeOtherForges(context, FORGE_POS);
        placeForge(context);
        List<MulaEntity> mulas = new ArrayList<>();
        int[] steps = {5, 12, 20, 40};
        for (int s = 0; s < steps.length; s++) {
            int target = steps[s];
            context.runAtTick(1 + 50 * s, () -> {
                while (mulas.size() < target) {
                    int i = mulas.size();
                    MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1 + i % 6, 2 + i / 36, 1 + (i / 6) % 6));
                    mula.setAiDisabled(true); // stays where it is: only the forge's assignment is checked here
                    mulas.add(mula);
                }
            });
            context.runAtTick(45 + 50 * s, () -> {
                BlockPos forge = context.getAbsolutePos(FORGE_POS);
                int count = Math.min(MulaDances.CAP, target), watching = target - count;
                Set<Integer> slots = new HashSet<>(), seats = new HashSet<>();
                for (MulaEntity mula : mulas) {
                    context.assertTrue(forge.equals(mula.danceForge()), "round this forge");
                    if (mula.isDancing()) {
                        context.assertTrue(mula.danceCount() == count, target + " Mulas: " + mula.danceCount() + " dancers");
                        slots.add(mula.danceSlot());
                    } else {
                        context.assertTrue(mula.isSpectating(), target + " Mulas: a dancer or a spectator");
                        context.assertTrue(mula.danceCount() == watching, "spectators: " + mula.danceCount());
                        seats.add(mula.danceSlot());
                    }
                }
                context.assertTrue(slots.size() == count && slots.stream().allMatch(i -> i < count),
                        target + " Mulas: distinct slots " + slots);
                context.assertTrue(seats.size() == watching && seats.stream().allMatch(i -> i < watching),
                        target + " Mulas: distinct spectator spots " + seats);
                if (target == steps[steps.length - 1]) {
                    mulas.forEach(MulaEntity::discard);
                    context.complete();
                }
            });
        }
    }

    private static void placeForge(TestContext context) {
        context.setBlockState(FORGE_POS, ModBlocks.DICE_FORGE.getDefaultState());
        // a forge holds Mulas only with its core in
        DiceForgeBlockEntity forge = context.getBlockEntity(FORGE_POS);
        forge.setStack(DiceForgeBlockEntity.CENTER_SLOT,
                new ItemStack(ModBlocks.GRAVITY_CORE));
    }

    /** A risen core's gravity doesn't pull the Mulas (they dance round it instead). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mulasAreNotPulledByTheCore(TestContext context) {
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(2, 3, 2));
        mula.setAiDisabled(true);
        mula.setVelocity(Vec3d.ZERO);
        Vec3d core = mula.getPos().add(3, 1, 0);
        GravityPull.pullAround(context.getWorld(), core, 10, 1, false, 0);
        context.assertTrue(mula.getVelocity().lengthSquared() == 0, "not pulled: " + mula.getVelocity());
        context.complete();
    }

    private static double sq(double v) {
        return v * v;
    }
}
