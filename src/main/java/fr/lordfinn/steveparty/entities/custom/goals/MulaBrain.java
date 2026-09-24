package fr.lordfinn.steveparty.entities.custom.goals;

import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlock;
import fr.lordfinn.steveparty.blocks.custom.GravityCoreBlock;
import fr.lordfinn.steveparty.blocks.custom.StarFragmentsBlock;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server-side memory of a Mula, shared by its behaviour goals: what is around it, refreshed rarely and staggered
 * between Mulas so no tick does a scan for all of them at once.
 * <ul>
 *   <li>every {@value #REFRESH_TICKS} ticks: is a player within {@value #ACTIVE_RANGE} blocks (a Mula far from every
 *   player does nothing more than hover), the player it is curious about, how still its owner stands;</li>
 *   <li>every {@value #NEIGHBOUR_TICKS} ticks: its {@value #MAX_NEIGHBOURS} nearest Mulas (the only ones its flock,
 *   play and dance look at);</li>
 *   <li>every {@value #SHINY_TICKS} ticks: a shiny thing nearby (its food or star fragments dropped, a star fragment
 *   block, a Dice Forge, a gravity core), with a few random block samples.</li>
 * </ul>
 * Fixed-size arrays and plain fields: nothing is allocated per tick (the rare entity queries return a list).
 */
public final class MulaBrain {
    public static final int REFRESH_TICKS = 10, NEIGHBOUR_TICKS = 20, SHINY_TICKS = 40;
    public static final double ACTIVE_RANGE = 48;
    public static final int MAX_NEIGHBOURS = 3;
    private static final double NEIGHBOUR_RANGE = 10;
    /** Curious about a player holding star fragments or its food within this range (blocks). */
    private static final double CURIOUS_RANGE = 10;
    private static final double SHINY_RANGE = 7;
    private static final int SHINY_BLOCK_SAMPLES = 10;
    /** An owner that moved less than this between two refreshes stands still (blocks). */
    private static final double STILL = 0.3;

    private final MulaEntity mula;
    private boolean active;

    private final MulaEntity[] neighbours = new MulaEntity[MAX_NEIGHBOURS];
    private int neighbourCount;

    private @Nullable PlayerEntity curiousPlayer;
    private double curiousLastX, curiousLastZ;
    private int curiousStillTicks;
    /** How fast the curious player comes towards the Mula (blocks per tick, positive = closer). */
    private double curiousApproach;

    private int ownerStillTicks;
    private double ownerLastX, ownerLastY, ownerLastZ;

    private @Nullable Vec3d shiny;
    private @Nullable Vec3d lastInspected;
    private int inspectCooldown;

    // flock: the leader's wander target, followed by the others
    private Vec3d wanderTarget;
    private int wanderVersion;

    // play: partner and shared circle
    private @Nullable MulaEntity playPartner;
    private int playTicks;
    private double playCenterX, playCenterY, playCenterZ;
    private float playPhase, playDirection;

    // shyness after a hit
    private int shyTicks;
    private @Nullable Vec3d shyTarget;
    private @Nullable Vec3d shyThreat;

    public MulaBrain(MulaEntity mula) {
        this.mula = mula;
    }

    /** Server, every tick of the Mula: counters, and the staggered refreshes. */
    public void tick() {
        if (shyTicks > 0) shyTicks--;
        if (playTicks > 0 && --playTicks == 0) playPartner = null;
        if (inspectCooldown > 0) inspectCooldown--;
        int phase = mula.age + mula.getId();
        if (phase % REFRESH_TICKS == 0) refresh();
        if (!active) return;
        if (phase % NEIGHBOUR_TICKS == 0) refreshNeighbours();
        if (phase % SHINY_TICKS == 0 && !mula.isTamed()) refreshShiny();
    }

    private void refresh() {
        World world = mula.getWorld();
        active = world.getClosestPlayer(mula.getX(), mula.getY(), mula.getZ(), ACTIVE_RANGE,
                net.minecraft.predicate.entity.EntityPredicates.EXCEPT_SPECTATOR) != null;
        if (!active) {
            neighbourCount = 0;
            curiousPlayer = null;
            return;
        }
        // the owner standing still
        if (mula.getOwner() instanceof PlayerEntity owner) {
            double moved = owner.squaredDistanceTo(ownerLastX, ownerLastY, ownerLastZ);
            ownerStillTicks = moved < STILL * STILL ? ownerStillTicks + REFRESH_TICKS : 0;
            ownerLastX = owner.getX();
            ownerLastY = owner.getY();
            ownerLastZ = owner.getZ();
        } else {
            ownerStillTicks = 0;
        }
        // the nearest player holding star fragments or its food
        PlayerEntity best = null;
        double bestSq = CURIOUS_RANGE * CURIOUS_RANGE;
        for (PlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || !holdsSomethingNice(player)) continue;
            double d = player.squaredDistanceTo(mula);
            if (d < bestSq) {
                bestSq = d;
                best = player;
            }
        }
        if (best != curiousPlayer) {
            curiousStillTicks = 0;
            curiousApproach = 0;
        } else if (best != null) {
            double dx = best.getX() - curiousLastX, dz = best.getZ() - curiousLastZ;
            double moved = Math.sqrt(dx * dx + dz * dz);
            curiousStillTicks = moved < 0.15 ? curiousStillTicks + REFRESH_TICKS : 0;
            // approach speed: how much closer it got, per tick
            double before = Math.sqrt(sq(curiousLastX - mula.getX()) + sq(curiousLastZ - mula.getZ()));
            double now = Math.sqrt(sq(best.getX() - mula.getX()) + sq(best.getZ() - mula.getZ()));
            curiousApproach = (before - now) / REFRESH_TICKS;
        }
        curiousPlayer = best;
        if (best != null) {
            curiousLastX = best.getX();
            curiousLastZ = best.getZ();
        }
    }

    private static double sq(double v) {
        return v * v;
    }

    private boolean holdsSomethingNice(PlayerEntity player) {
        return isNice(player.getMainHandStack()) || isNice(player.getOffHandStack());
    }

    private boolean isNice(ItemStack stack) {
        return !stack.isEmpty() && (isStarFragment(stack.getItem()) || mula.isMulaFood(stack));
    }

    public static boolean isStarFragment(Item item) {
        return item == ModItems.BLUE_STAR_FRAGMENT || item == ModItems.RED_STAR_FRAGMENT
                || item == ModItems.GREEN_STAR_FRAGMENT || item == ModItems.YELLOW_STAR_FRAGMENT
                || item == ModItems.PURPLE_STAR_FRAGMENT || item == ModItems.BLACK_STAR_FRAGMENT;
    }

    private void refreshNeighbours() {
        List<MulaEntity> around = mula.getWorld().getEntitiesByClass(MulaEntity.class,
                mula.getBoundingBox().expand(NEIGHBOUR_RANGE), other -> other != mula && other.isAlive());
        neighbourCount = 0;
        // keep the MAX_NEIGHBOURS nearest (insertion into a tiny sorted array)
        for (MulaEntity other : around) {
            double d = other.squaredDistanceTo(mula);
            int slot = neighbourCount;
            while (slot > 0 && neighbours[slot - 1].squaredDistanceTo(mula) > d) {
                if (slot < MAX_NEIGHBOURS) neighbours[slot] = neighbours[slot - 1];
                slot--;
            }
            if (slot < MAX_NEIGHBOURS) {
                neighbours[slot] = other;
                if (neighbourCount < MAX_NEIGHBOURS) neighbourCount++;
            }
        }
        for (int i = neighbourCount; i < MAX_NEIGHBOURS; i++) neighbours[i] = null;
    }

    private void refreshShiny() {
        if (shiny != null || inspectCooldown > 0) return;
        World world = mula.getWorld();
        // dropped: its food or star fragments (it only looks: it never picks anything up)
        List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, mula.getBoundingBox().expand(SHINY_RANGE),
                item -> isStarFragment(item.getStack().getItem()) || mula.isMulaFood(item.getStack()));
        if (!items.isEmpty()) {
            setShiny(items.get(0).getPos().add(0, 0.3, 0));
            return;
        }
        // a few random blocks around: star fragment blocks, a Dice Forge, a gravity core
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int i = 0; i < SHINY_BLOCK_SAMPLES; i++) {
            pos.set(mula.getBlockX() + mula.getRandom().nextBetween(-6, 6), mula.getBlockY() + mula.getRandom().nextBetween(-4, 2),
                    mula.getBlockZ() + mula.getRandom().nextBetween(-6, 6));
            if (!world.isChunkLoaded(pos)) continue;
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof StarFragmentsBlock || state.getBlock() instanceof DiceForgeBlock
                    || state.getBlock() instanceof GravityCoreBlock) {
                setShiny(Vec3d.ofCenter(pos).add(0, 0.9, 0));
                return;
            }
        }
    }

    private void setShiny(Vec3d where) {
        if (lastInspected != null && lastInspected.squaredDistanceTo(where) < 4) return;
        if (!mula.isInHome(where)) return; // out of its forge's area: not for it
        shiny = where;
    }

    /** The shiny thing has been looked at: leave it alone for a minute. */
    public void doneInspecting() {
        lastInspected = shiny;
        shiny = null;
        inspectCooldown = 1200;
    }

    // ------------------------------------------------------------------------------------------ flock

    /** The Mula it follows in its flock: the one with the smallest id among itself and its neighbours (wild, not
     * sitting), preferring its own colour. Null when it leads. */
    public @Nullable MulaEntity flockLeader() {
        MulaEntity leader = null;
        boolean onlySameColour = false;
        for (int pass = 0; pass < 2 && leader == null; pass++) {
            onlySameColour = pass == 0;
            for (int i = 0; i < neighbourCount; i++) {
                MulaEntity other = neighbours[i];
                if (other == null || other.isTamed() || other.isInSittingPose() || !other.isAlive()
                        || other.squaredDistanceTo(mula) > 64) continue;
                if (onlySameColour && other.getVariant() != mula.getVariant()) continue;
                if (other.getId() < mula.getId() && (leader == null || other.getId() < leader.getId())) leader = other;
            }
        }
        return leader;
    }

    /** True while an orbit goal circles the owner (for the tests and the effects). */
    public boolean orbiting;

    public void setWanderTarget(Vec3d target) {
        wanderTarget = target;
        wanderVersion++;
    }

    public @Nullable Vec3d wanderTarget() {
        return wanderTarget;
    }

    public int wanderVersion() {
        return wanderVersion;
    }

    /** Nearest neighbour, or null. */
    public @Nullable MulaEntity nearestNeighbour() {
        return neighbourCount > 0 ? neighbours[0] : null;
    }

    public int neighbourCount() {
        return neighbourCount;
    }

    public MulaEntity neighbour(int i) {
        return neighbours[i];
    }

    // ------------------------------------------------------------------------------------------ play

    /** Starts a little chase with a nearby idle wild Mula, sometimes. */
    public boolean tryStartPlay() {
        if (playTicks > 0 || neighbourCount == 0 || mula.getRandom().nextInt(40) != 0) return false;
        MulaEntity other = neighbours[0];
        if (other == null || other.isTamed() || other.isInSittingPose() || other.squaredDistanceTo(mula) > 25) return false;
        MulaBrain friend = other.getMulaBrain();
        if (friend.playTicks > 0 || friend.shyTicks > 0) return false;
        int ticks = 90 + mula.getRandom().nextInt(50);
        float direction = mula.getRandom().nextBoolean() ? 1f : -1f;
        Vec3d c = mula.keepHome(new Vec3d((mula.getX() + other.getX()) / 2, (mula.getY() + other.getY()) / 2 + 0.5,
                (mula.getZ() + other.getZ()) / 2));
        double cx = c.x, cy = c.y, cz = c.z;
        this.startPlay(other, ticks, cx, cy, cz, 0f, direction);
        friend.startPlay(mula, ticks, cx, cy, cz, MathHelper.PI, direction);
        return true;
    }

    private void startPlay(MulaEntity partner, int ticks, double cx, double cy, double cz, float phase, float direction) {
        playPartner = partner;
        playTicks = ticks;
        playCenterX = cx;
        playCenterY = cy;
        playCenterZ = cz;
        playPhase = phase;
        playDirection = direction;
    }

    public boolean isPlaying() {
        return playTicks > 0 && playPartner != null && playPartner.isAlive();
    }

    public int playTicks() { return playTicks; }
    public double playCenterX() { return playCenterX; }
    public double playCenterY() { return playCenterY; }
    public double playCenterZ() { return playCenterZ; }
    public float playPhase() { return playPhase; }
    public float playDirection() { return playDirection; }

    public void stopPlay() {
        playTicks = 0;
        playPartner = null;
    }

    // ------------------------------------------------------------------------------------------ shyness

    /**
     * Hit: it will flee a short way and hide, then peek out and come back. Tamed, it hides behind its owner (on the
     * far side from what hit it); wild, it looks for leaves or a block between it and the threat, else just flies
     * off away from it. A few samples and raycasts once per hit.
     */
    public void onHurt(@Nullable Entity attacker) {
        Vec3d threat = attacker != null ? attacker.getEyePos() : mula.getPos().add(mula.getRotationVector().multiply(-2));
        // a far attacker (an arrow from afar...): seen from its direction, 8 blocks away (the raycasts stay short)
        Vec3d toThreat = threat.subtract(mula.getPos());
        if (toThreat.lengthSquared() > 8 * 8) threat = mula.getPos().add(toThreat.normalize().multiply(8));
        shyThreat = threat;
        shyTicks = SHY_TICKS;
        // a flee stays inside its forge's area
        shyTarget = mula.keepHome(hideout(threat));
    }

    public static final int SHY_TICKS = 120, SHY_FLEE_TICKS = 40, SHY_PEEK_TICKS = 75;

    private Vec3d hideout(Vec3d threat) {
        if (mula.getOwner() instanceof PlayerEntity owner && owner != null && owner.squaredDistanceTo(mula) < 16 * 16
                && owner.getEyePos().squaredDistanceTo(threat) > 0.25) {
            return behindOwner(owner.getPos(), threat, owner.getHeight());
        }
        World world = mula.getWorld();
        Vec3d here = mula.getPos();
        Vec3d away = flatAway(here, threat);
        Vec3d best = null;
        int bestScore = -1;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int i = 0; i < 10; i++) {
            double angle = Math.atan2(away.z, away.x) + (mula.getRandom().nextDouble() - 0.5) * 2.1;
            double dist = 4 + mula.getRandom().nextDouble() * 4;
            Vec3d candidate = here.add(Math.cos(angle) * dist, mula.getRandom().nextDouble() * 2 - 0.5, Math.sin(angle) * dist);
            pos.set(candidate.x, candidate.y, candidate.z);
            if (!world.isChunkLoaded(pos) || !world.getBlockState(pos).isAir()) continue;
            int score = 0;
            for (var dir : net.minecraft.util.math.Direction.values()) {
                if (world.getBlockState(pos.offset(dir)).isIn(BlockTags.LEAVES)) {
                    score += 2;
                    break;
                }
            }
            HitResult hit = world.raycast(new RaycastContext(threat, candidate, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, mula));
            if (hit.getType() == HitResult.Type.BLOCK) score += 1;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best != null ? best : here.add(away.multiply(6)).add(0, 1, 0);
    }

    /** Behind the owner seen from the threat, at shoulder height. */
    public static Vec3d behindOwner(Vec3d ownerFeet, Vec3d threat, double ownerHeight) {
        Vec3d away = flatAway(ownerFeet, threat);
        return ownerFeet.add(away.multiply(1.4)).add(0, ownerHeight * 0.8, 0);
    }

    /** Horizontal unit vector from the threat to here. */
    public static Vec3d flatAway(Vec3d here, Vec3d threat) {
        double dx = here.x - threat.x, dz = here.z - threat.z;
        double l = Math.sqrt(dx * dx + dz * dz);
        return l < 1.0E-4 ? new Vec3d(1, 0, 0) : new Vec3d(dx / l, 0, dz / l);
    }

    public boolean isShy() {
        return shyTicks > 0 && shyTarget != null;
    }

    public int shyTicks() { return shyTicks; }
    public @Nullable Vec3d shyTarget() { return shyTarget; }
    public @Nullable Vec3d shyThreat() { return shyThreat; }

    // ------------------------------------------------------------------------------------------ getters

    public boolean isActive() {
        return active;
    }

    public int ownerStillTicks() {
        return ownerStillTicks;
    }

    public @Nullable PlayerEntity curiousPlayer() {
        return curiousPlayer;
    }

    public int curiousStillTicks() {
        return curiousStillTicks;
    }

    public double curiousApproach() {
        return curiousApproach;
    }

    public @Nullable Vec3d shiny() {
        return shiny;
    }

    // ------------------------------------------------------------------------------------------ rules (tested)

    /** Comfortable distance (blocks, from the player's eyes) of a curious Mula. */
    public static double curiousDistance(int stillTicks, boolean sprinting, double approachSpeed) {
        if (sprinting || approachSpeed > 0.18) return 4.5; // startled: backs off
        if (stillTicks >= 40) return 1.8;                 // the player stays still: inches closer
        return 2.6;                                        // shy, but within reach to be fed
    }

    /** Height a wild Mula rises to at night: 8 to 20 blocks above the ground, below the build limit. */
    public static double nightAltitude(double groundY, int topY, int seed) {
        double above = 10 + Math.floorMod(seed * 7, 9);
        return Math.min(groundY + above, topY - 2);
    }

    /** Morning (when wild Mulas perch on flowers): from dawn to mid-morning. */
    public static boolean isMorning(long timeOfDay) {
        long t = Math.floorMod(timeOfDay, 24000L);
        return t >= 23000 || t < 3500;
    }

    /** For tests: forces an immediate refresh of everything. */
    public void refreshNow() {
        refresh();
        if (active) {
            refreshNeighbours();
            refreshShiny();
        }
    }
}
