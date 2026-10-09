package fr.lordfinn.steveparty.entities.custom;

import fr.lordfinn.steveparty.particles.MulaSparkleEffect;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * A burst Mula flying away as a shooting star: a bright little star with a sparkling trail in its colour, on a parabola
 * (the higher the arc, the farther it goes: {@link #distanceFor}), towards where it will be reborn (MulaRebirths).
 * <p>
 * Purely a show: its path is a formula of its start, direction, distance, apex and flight time (synced once), computed
 * on each side from its own age, so no position packets; it is never saved (a restart mid-flight loses the show, not
 * the Mula: the rebirth is recorded apart), and it vanishes at the end of its flight.
 */
public class MulaStarEntity extends Entity {
    private static final TrackedData<Integer> VARIANT = DataTracker.registerData(MulaStarEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> DIR_X = DataTracker.registerData(MulaStarEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> DIR_Z = DataTracker.registerData(MulaStarEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> DISTANCE = DataTracker.registerData(MulaStarEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> APEX = DataTracker.registerData(MulaStarEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> FLIGHT = DataTracker.registerData(MulaStarEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Height of its landing point above its start (blocks): 0 far away, the forge's height for a short loop home. */
    private static final TrackedData<Float> END_DY = DataTracker.registerData(MulaStarEntity.class, TrackedDataHandlerRegistry.FLOAT);

    /** Arc heights (blocks above the start) and the distances they give. */
    public static final double MIN_APEX = 25, MAX_APEX = 90, MIN_DISTANCE = 100, MAX_DISTANCE = 400;
    /** Black Mulas, more fearful and full of magic, fly much farther: 500 to 2000 blocks. */
    public static final double BLACK_MIN_DISTANCE = 500, BLACK_MAX_DISTANCE = 2000;
    private static final MulaSparkleEffect WHITE_TWINKLE = new MulaSparkleEffect(0xFFFFFF, 1.4f, MulaSparkleEffect.TWINKLE);

    private double startX, startY, startZ;
    private int flightAge;
    /** Client: where it was over its last TAIL ticks (newest first), for the comet tail drawn behind it. */
    public static final int TAIL = 14;
    private final double[] tail = new double[TAIL * 3];
    private int tailCount;

    public MulaStarEntity(EntityType<? extends MulaStarEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        setNoGravity(true);
    }

    /** Horizontal distance of the rebirth for an arc this high: 100 blocks for the lowest, 400 for the highest. */
    public static double distanceFor(double apex) {
        double u = MathHelper.clamp((apex - MIN_APEX) / (MAX_APEX - MIN_APEX), 0, 1);
        return MIN_DISTANCE + (MAX_DISTANCE - MIN_DISTANCE) * u;
    }

    /** The same for a Mula of this colour (black ones go 500 to 2000 blocks). */
    public static double distanceFor(double apex, MulaEntity.MulaVariant variant) {
        if (variant != MulaEntity.MulaVariant.BLACK) return distanceFor(apex);
        double u = MathHelper.clamp((apex - MIN_APEX) / (MAX_APEX - MIN_APEX), 0, 1);
        return BLACK_MIN_DISTANCE + (BLACK_MAX_DISTANCE - BLACK_MIN_DISTANCE) * u;
    }

    /** Flight time (ticks): about 4 s for the nearest rebirth, 8 s at 400 blocks, 10.5 s at most (black ones fly faster). */
    public static int flightTicksFor(double distance) {
        return (int) (60 + Math.min(distance, 600) / 4);
    }

    /** Server: sets its path (before it is spawned). */
    public void launch(double x, double y, double z, MulaEntity.MulaVariant variant, double dirX, double dirZ,
                       double distance, double apex, double endDy) {
        this.setPosition(x, y, z);
        this.startX = x;
        this.startY = y;
        this.startZ = z;
        this.dataTracker.set(VARIANT, variant.getId());
        this.dataTracker.set(DIR_X, (float) dirX);
        this.dataTracker.set(DIR_Z, (float) dirZ);
        this.dataTracker.set(DISTANCE, (float) distance);
        this.dataTracker.set(APEX, (float) apex);
        this.dataTracker.set(FLIGHT, flightTicksFor(distance));
        this.dataTracker.set(END_DY, (float) endDy);
    }

    @Override
    public void onSpawnPacket(EntitySpawnS2CPacket packet) {
        super.onSpawnPacket(packet);
        this.startX = packet.getX();
        this.startY = packet.getY();
        this.startZ = packet.getZ();
    }

    public MulaEntity.MulaVariant getVariant() {
        return MulaEntity.MulaVariant.byId(this.dataTracker.get(VARIANT));
    }

    public int flightTicks() {
        return Math.max(1, this.dataTracker.get(FLIGHT));
    }

    /** 0..1 along its flight. */
    public float progress(float partialTick) {
        return MathHelper.clamp((flightAge + partialTick) / flightTicks(), 0f, 1f);
    }

    @Override
    public void tick() {
        super.tick();
        flightAge++;
        double u = Math.min(1.0, flightAge / (double) flightTicks());
        double d = this.dataTracker.get(DISTANCE) * u;
        // parabola: up to its apex half way, down to its landing height at the end
        double y = startY + 4 * this.dataTracker.get(APEX) * u * (1 - u) + this.dataTracker.get(END_DY) * u;
        this.setPosition(startX + this.dataTracker.get(DIR_X) * d, y, startZ + this.dataTracker.get(DIR_Z) * d);
        if (this.getWorld().isClient) {
            System.arraycopy(tail, 0, tail, 3, (TAIL - 1) * 3);
            tail[0] = getX();
            tail[1] = getY();
            tail[2] = getZ();
            tailCount = Math.min(TAIL, tailCount + 1);
            trail();
        } else if (flightAge > flightTicks()) {
            this.discard();
        }
    }

    /** Client: how many tail points there are, and the i-th (0 = newest) into out (x, y, z). */
    public int tailCount() {
        return tailCount;
    }

    public void tailPoint(int i, double[] out) {
        out[0] = tail[i * 3];
        out[1] = tail[i * 3 + 1];
        out[2] = tail[i * 3 + 2];
    }

    /**
     * Client: sparkles all along the stretch it just flew (it moves a few blocks a tick), in its colour and white, seen
     * from far away (forced particles: at most 9 a tick, for a few seconds).
     */
    private void trail() {
        World world = this.getWorld();
        double dx = getX() - prevX, dy = getY() - prevY, dz = getZ() - prevZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int n = Math.min(8, 1 + (int) (length / 0.5));
        MulaSparkleEffect twinkle = getVariant().getTwinkle();
        for (int i = 0; i < n; i++) {
            double k = i / (double) n;
            double j = 0.15;
            world.addImportantParticle((i & 1) == 0 ? twinkle : WHITE_TWINKLE, true,
                    prevX + dx * k + (random.nextDouble() - 0.5) * j, prevY + dy * k + (random.nextDouble() - 0.5) * j,
                    prevZ + dz * k + (random.nextDouble() - 0.5) * j, 0, -0.01, 0);
        }
        if ((flightAge & 1) == 0) {
            world.addImportantParticle(getVariant().getStarDust(), true, prevX, prevY, prevZ, 0, -0.02, 0);
        }
    }

    /** Client: its place comes from the formula, not from position packets. */
    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps) {
    }

    @Override
    public boolean shouldRender(double distance) {
        // a shooting star is seen from far away
        return distance < 256 * 256;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(VARIANT, 0);
        builder.add(DIR_X, 1f);
        builder.add(DIR_Z, 0f);
        builder.add(DISTANCE, (float) MIN_DISTANCE);
        builder.add(APEX, (float) MIN_APEX);
        builder.add(FLIGHT, 100);
        builder.add(END_DY, 0f);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }
}
