package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.SyncedBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllers;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.utils.ServerMemory;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Party Antenna (registry id {@code party_bell}): gives a redstone pulse when its moment of the party happens (see
 * {@link PartyMoment}), and remembers the value of that moment for comparators. In waiting mode, the party pauses at
 * that moment until the antenna receives a redstone signal.
 * <p>
 * An antenna listens to the closest party controller within {@link #RANGE} blocks (running party, or party standing
 * on its end). Without a party controller around, it hears the free play moments (dice rolled, token arrived) of the
 * tokens within {@link #RANGE} blocks.
 * <p>
 * Its head turns toward the Party Controller it listens to, else the closest one within {@link #RANGE} blocks
 * (checked every second, synced: {@link #getFaced}); with none, it slowly scans around its
 * {@link PartyBellBlock#FACING}. The turn and the lit receiver live on the client only ({@link #yaw},
 * {@link #ringTicks}).
 */
public class PartyBellBlockEntity extends SyncedBlockEntity implements GeoBlockEntity, TickableBlockEntity {
    public static final int RANGE = 64;
    /** Loaded server-side antennas, keyed by dimension + position. */
    private static final Map<GlobalPos, PartyBellBlockEntity> BELLS = ServerMemory.forgetOnStop(new LinkedHashMap<>());
    private static final int FACE_CHECK_TICKS = 20;
    /** How long its receiver stays lit after it caught a moment (ticks). */
    public static final int RING_TICKS = 30;
    /** The head's highest turn speed (radians per tick), and how far it nods up or down at most. */
    private static final float TURN_SPEED = 0.2f, MAX_PITCH = 25 * MathHelper.RADIANS_PER_DEGREE;
    /** Scanning (no Party Controller around): how far it sweeps each side of its rest direction, in how long (ticks). */
    private static final float SCAN_AMPLITUDE = 60 * MathHelper.RADIANS_PER_DEGREE;
    private static final int SCAN_TICKS = 200;
    /** Its head's pivot (the ball joint) above the block's bottom. */
    private static final double HEAD_HEIGHT = 12.5 / 16;

    /** Value of the last moment heard, read by comparators (0-15). */
    private int value = 0;
    /** Last redstone input, to act on rising edges only. */
    private boolean inputPowered = false;
    /** The Party Controller its head turns to, null for none. */
    private @Nullable BlockPos faced;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Client: the head's turn (yaw about Y, 0 = receiver north; pitch), this tick and the last one. */
    public float yaw, lastYaw, pitch, lastPitch;
    private boolean turned;
    /** Client: ticks left of its receiver's light, 0 when dark. */
    public int ringTicks;

    public PartyBellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PARTY_BELL_ENTITY, pos, state);
    }

    /** The Party Controller its head turns to, null for none. */
    public @Nullable BlockPos getFaced() {
        return faced;
    }

    @Override
    public void tick() {
        if (this.world instanceof ServerWorld serverWorld) {
            if ((serverWorld.getTime() + this.pos.hashCode()) % FACE_CHECK_TICKS == 0) checkFaced(serverWorld);
        } else if (this.world != null) {
            if (ringTicks > 0) ringTicks--;
            turnHead();
        }
    }

    private void checkFaced(ServerWorld world) {
        BlockPos now = getListenedController(world)
                .or(() -> PartyControllers.all().stream()
                        .filter(controller -> !controller.isRemoved() && controller.getWorld() == world)
                        .filter(controller -> controller.getPos().getSquaredDistance(this.pos) < (double) RANGE * RANGE)
                        .min(Comparator.comparingDouble(controller -> controller.getPos().getSquaredDistance(this.pos))))
                .map(PartyControllerEntity::getPos).orElse(null);
        if (Objects.equals(now, faced)) return;
        faced = now;
        markDirty();
        syncToClients();
    }

    /** Client: one tick of the head's turn toward its target, eased, at most {@link #TURN_SPEED}. */
    private void turnHead() {
        float targetYaw, targetPitch = 0;
        if (faced != null) {
            double dx = faced.getX() - this.pos.getX(), dz = faced.getZ() - this.pos.getZ();
            double dy = faced.getY() + 0.75 - (this.pos.getY() + HEAD_HEIGHT);
            targetYaw = yawToward(dx, dz);
            targetPitch = MathHelper.clamp((float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)), -MAX_PITCH, MAX_PITCH);
        } else {
            Direction rest = getCachedState().get(PartyBellBlock.FACING);
            long time = this.world.getTime() + Math.floorMod(this.pos.hashCode(), SCAN_TICKS);
            targetYaw = yawToward(rest.getOffsetX(), rest.getOffsetZ())
                    + SCAN_AMPLITUDE * MathHelper.sin(MathHelper.TAU * (time % SCAN_TICKS) / SCAN_TICKS);
        }
        if (!turned) {
            yaw = lastYaw = targetYaw;
            pitch = lastPitch = targetPitch;
            turned = true;
            return;
        }
        lastYaw = yaw;
        lastPitch = pitch;
        float yawLeft = MathHelper.wrapDegrees((targetYaw - yaw) * MathHelper.DEGREES_PER_RADIAN) * MathHelper.RADIANS_PER_DEGREE;
        yaw += MathHelper.clamp(yawLeft * 0.25f, -TURN_SPEED, TURN_SPEED);
        pitch += MathHelper.clamp((targetPitch - pitch) * 0.25f, -TURN_SPEED, TURN_SPEED);
    }

    /** The head's yaw (receiver at -z when 0) pointing along (dx, dz). */
    public static float yawToward(double dx, double dz) {
        return (float) Math.atan2(-dx, -dz);
    }

    /** {@link PartyBellBlock#RING_EVENT}: on the client, its receiver lights. */
    @Override
    public boolean onSyncedBlockEvent(int type, int data) {
        if (type != PartyBellBlock.RING_EVENT) return super.onSyncedBlockEvent(type, data);
        if (this.world != null && this.world.isClient) ringTicks = RING_TICKS;
        return true;
    }

    /** Its head is turned by its renderer: no keyframe animation. */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        register();
    }

    @Override
    public void cancelRemoval() {
        super.cancelRemoval();
        register();
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        if (this.world instanceof ServerWorld)
            BELLS.remove(GlobalPos.create(this.world.getRegistryKey(), this.pos), this);
    }

    private void register() {
        if (this.world instanceof ServerWorld && !this.isRemoved())
            BELLS.put(GlobalPos.create(this.world.getRegistryKey(), this.pos), this);
    }

    public int getValue() {
        return value;
    }

    public PartyMoment getMoment() {
        return getCachedState().get(PartyBellBlock.MOMENT);
    }

    public boolean isWaiting() {
        return getCachedState().get(PartyBellBlock.WAITING) && getMoment().canWait();
    }

    /** Rings: a redstone pulse, and the moment value for comparators. */
    public void ring(int value) {
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        this.value = Math.clamp(value, 0, 15);
        markDirty();
        PartyBellBlock.pulse(serverWorld, this.pos, getCachedState());
    }

    /**
     * Called on each neighbor update with the received redstone power. A rising edge on a waiting bell lets the
     * party go on. The bell's own pulse (fed back through a wire) is ignored.
     */
    public void onRedstoneInput(boolean powered, boolean ownPulse) {
        boolean risingEdge = powered && !inputPowered;
        if (powered != inputPowered) {
            inputPowered = powered;
            markDirty();
        }
        if (!risingEdge || ownPulse || !isWaiting() || !(this.world instanceof ServerWorld serverWorld)) return;
        getListenedController(serverWorld).ifPresent(controller -> controller.releaseMoment(getMoment()));
    }

    public void initRedstoneInput(boolean powered) {
        this.inputPowered = powered;
    }

    private Optional<PartyControllerEntity> getListenedController(ServerWorld world) {
        return PartyControllerEntity.getClosestSteppablePartyControllerEntity(world, this.pos, RANGE, true);
    }

    private boolean listensTo(PartyControllerEntity controller) {
        if (this.isRemoved() || this.world != controller.getWorld()) return false;
        if (this.pos.getSquaredDistance(controller.getPos()) >= (double) RANGE * RANGE) return false;
        return this.world instanceof ServerWorld serverWorld
                && getListenedController(serverWorld).map(listened -> listened == controller).orElse(false);
    }

    private static List<PartyBellBlockEntity> bells() {
        return List.copyOf(BELLS.values());
    }

    /**
     * Rings the bells of the controller listening to {@code moment}.
     *
     * @return true if one of them is waiting: the party must pause at this moment
     */
    public static boolean ring(PartyControllerEntity controller, PartyMoment moment, int value) {
        boolean waiting = false;
        for (PartyBellBlockEntity bell : bells()) {
            if (bell.getMoment() != moment || !bell.listensTo(controller)) continue;
            bell.ring(value);
            waiting |= bell.isWaiting();
        }
        return waiting;
    }

    /**
     * The moment of the bell of {@code controller} ringing right now (its pulse on) nearest to {@code pos}, within
     * {@code radius} blocks: what a redstone pulse arriving there most likely comes from (a Piggy Bank paying the
     * player whose turn just ended, rather than the one starting). Null for none.
     */
    public static @Nullable PartyMoment ringingNear(ServerWorld world, BlockPos pos, PartyControllerEntity controller, int radius) {
        PartyBellBlockEntity nearest = null;
        double best = (double) radius * radius;
        for (PartyBellBlockEntity bell : bells()) {
            if (bell.isRemoved() || bell.world != world || !bell.getCachedState().get(PartyBellBlock.POWERED)) continue;
            double distance = bell.pos.getSquaredDistance(pos);
            if (distance > best || !bell.listensTo(controller)) continue;
            best = distance;
            nearest = bell;
        }
        return nearest == null ? null : nearest.getMoment();
    }

    /** @return true if a bell of the controller waits at {@code moment}. */
    public static boolean hasWaitingBell(PartyControllerEntity controller, PartyMoment moment) {
        if (!moment.canWait()) return false;
        for (PartyBellBlockEntity bell : bells()) {
            if (bell.getMoment() == moment && bell.isWaiting() && bell.listensTo(controller)) return true;
        }
        return false;
    }

    /**
     * Free play: rings the bells listening to {@code moment} within {@link #RANGE} blocks of {@code pos} that have
     * no party controller around (those follow their party instead).
     */
    public static void ringFreePlay(ServerWorld world, BlockPos pos, PartyMoment moment, int value) {
        for (PartyBellBlockEntity bell : bells()) {
            if (bell.isRemoved() || bell.world != world || bell.getMoment() != moment) continue;
            if (bell.pos.getSquaredDistance(pos) >= (double) RANGE * RANGE) continue;
            if (bell.getListenedController(world).isPresent()) continue;
            bell.ring(value);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("Value", value);
        nbt.putBoolean("InputPowered", inputPowered);
        if (faced != null) nbt.put("Faced", NbtHelper.fromBlockPos(faced));
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        value = Math.clamp(nbt.getInt("Value"), 0, 15);
        inputPowered = nbt.getBoolean("InputPowered");
        faced = NbtHelper.toBlockPos(nbt, "Faced").orElse(null);
    }
}
