package fr.lordfinn.steveparty.entities.custom;

import fr.lordfinn.steveparty.blocks.custom.pipe.PipeNetworks;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipePose;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeTravel;
import fr.lordfinn.steveparty.entities.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What carries a traveller through a pipe: an invisible entity it rides, going along the pipe at a steady speed. Its
 * path (points along the middle of the pipes) and speed are sent to the clients once: both sides move it the same way
 * from them, so the traveller glides smoothly inside the pipe (no position updates). The server sends the traveller
 * on at the end of the path ({@link PipeTravel#arrive}).
 */
public class PipeCarrierEntity extends Entity {
    private static final TrackedData<NbtCompound> PATH = DataTracker.registerData(PipeCarrierEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);

    /** The carriers of the client world, for the pipe animation (client side only). */
    public static final Set<PipeCarrierEntity> CLIENT_CARRIERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private List<Vec3d> points = List.of();
    /** Distance from the start to each point. */
    private double[] lengths = new double[0];
    private double speed;
    private double travelled;
    /** Where the path ends, and where the traveller came in (server side). */
    private @Nullable PipeNetworks.End target, origin;
    /** Whether it has been sent back once already (warp or exit impossible). */
    private boolean returned;
    /** How many times its way was worked out again because the pipes changed (server side). */
    private int reroutes;
    /** Where the traveller is put down when it gets off (server side). */
    private @Nullable Vec3d dismountAt;

    public PipeCarrierEntity(EntityType<? extends PipeCarrierEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
        this.setInvisible(true);
    }

    public static PipeCarrierEntity create(ServerWorld world, List<Vec3d> points, double speed, PipeNetworks.End target, PipeNetworks.End origin) {
        PipeCarrierEntity carrier = new PipeCarrierEntity(ModEntities.PIPE_CARRIER, world);
        carrier.origin = origin;
        carrier.setLeg(points, speed, target);
        Vec3d start = points.getFirst();
        carrier.refreshPositionAndAngles(start.x, start.y, start.z, 0, 0);
        return carrier;
    }

    /** A new stretch: along {@code points} (from the start) to {@code target}. */
    public void setLeg(List<Vec3d> points, double speed, PipeNetworks.End target) {
        this.target = target;
        NbtCompound path = new NbtCompound();
        NbtList list = new NbtList();
        for (Vec3d point : points) {
            list.add(NbtDouble.of(point.x));
            list.add(NbtDouble.of(point.y));
            list.add(NbtDouble.of(point.z));
        }
        path.put("Points", list);
        path.putDouble("Speed", speed);
        this.dataTracker.set(PATH, path);
        readPath(path);
    }

    private void readPath(NbtCompound path) {
        NbtList list = path.getList("Points", NbtElement.DOUBLE_TYPE);
        List<Vec3d> read = new ArrayList<>();
        for (int i = 0; i + 2 < list.size(); i += 3) read.add(new Vec3d(list.getDouble(i), list.getDouble(i + 1), list.getDouble(i + 2)));
        points = List.copyOf(read);
        lengths = PipePose.lengths(points);
        speed = path.getDouble("Speed");
        travelled = 0;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(PATH, new NbtCompound());
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (PATH.equals(data) && getWorld().isClient) readPath(this.dataTracker.get(PATH));
    }

    public List<Vec3d> points() {
        return points;
    }

    public double length() {
        return lengths.length == 0 ? 0 : lengths[lengths.length - 1];
    }

    /** Distance from the start to each point (not to be changed). */
    public double[] lengths() {
        return lengths;
    }

    /** Distance from the start to point {@code i}. */
    public double lengthTo(int i) {
        return lengths[i];
    }

    public double speed() {
        return speed;
    }

    public double travelled() {
        return travelled;
    }

    public @Nullable PipeNetworks.End target() {
        return target;
    }

    public @Nullable PipeNetworks.End origin() {
        return origin;
    }

    public boolean hasReturned() {
        return returned;
    }

    /** One more new way because the pipes changed: false after a few (it gets out then). */
    public boolean reroute() {
        return ++reroutes <= 3;
    }

    public void markReturned() {
        returned = true;
    }

    public void setDismountAt(@Nullable Vec3d at) {
        this.dismountAt = at;
    }

    /** The point of the path {@code distance} blocks from its start. */
    public Vec3d at(double distance) {
        if (points.isEmpty()) return getPos();
        for (int i = 1; i < points.size(); i++) {
            if (distance <= lengths[i] || i == points.size() - 1) {
                double part = lengths[i] - lengths[i - 1];
                double t = part <= 1.0E-6 ? 1 : MathHelper.clamp((distance - lengths[i - 1]) / part, 0, 1);
                return points.get(i - 1).lerp(points.get(i), t);
            }
        }
        return points.getFirst();
    }

    @Override
    public void tick() {
        if (points.size() < 2) {
            if (!getWorld().isClient) discard();
            return;
        }
        travelled = Math.min(travelled + speed, length());
        Vec3d at = at(travelled);
        setPosition(at.x, at.y, at.z);
        if (getWorld() instanceof ServerWorld world) {
            if (!hasPassengers()) {
                discard();
            } else if (travelled >= length()) {
                PipeTravel.arrive(world, this);
            }
        } else {
            CLIENT_CARRIERS.add(this);
        }
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        CLIENT_CARRIERS.remove(this);
    }

    /** The traveller rides in the middle of the pipe. */
    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
        positionUpdater.accept(passenger, getX(), getY() - passenger.getHeight() / 2, getZ());
    }

    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        return dismountAt != null ? dismountAt : super.updatePassengerForDismount(passenger);
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (getWorld() instanceof ServerWorld) PipeTravel.shrink(passenger);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (getWorld() instanceof ServerWorld) PipeTravel.unshrink(passenger);
    }

    // The client moves it from the path: the server's position updates are not needed
    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
        if (points.size() < 2) super.updateTrackedPositionAndAngles(x, y, z, yaw, pitch, interpolationSteps);
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("Path", NbtElement.COMPOUND_TYPE)) {
            NbtCompound path = nbt.getCompound("Path");
            this.dataTracker.set(PATH, path);
            readPath(path);
        }
        travelled = nbt.getDouble("Travelled");
        target = readEnd(nbt, "Target");
        origin = readEnd(nbt, "Origin");
        returned = nbt.getBoolean("Returned");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.put("Path", this.dataTracker.get(PATH).copy());
        nbt.putDouble("Travelled", travelled);
        writeEnd(nbt, "Target", target);
        writeEnd(nbt, "Origin", origin);
        nbt.putBoolean("Returned", returned);
    }

    private static void writeEnd(NbtCompound nbt, String key, @Nullable PipeNetworks.End end) {
        if (end == null) return;
        NbtCompound tag = new NbtCompound();
        tag.putLong("Pos", end.pos().asLong());
        tag.putInt("Dir", end.dir().getId());
        tag.putBoolean("Capped", end.capped());
        nbt.put(key, tag);
    }

    private static @Nullable PipeNetworks.End readEnd(NbtCompound nbt, String key) {
        if (!nbt.contains(key, NbtElement.COMPOUND_TYPE)) return null;
        NbtCompound tag = nbt.getCompound(key);
        return new PipeNetworks.End(BlockPos.fromLong(tag.getLong("Pos")), Direction.byId(tag.getInt("Dir")), tag.getBoolean("Capped"));
    }
}
