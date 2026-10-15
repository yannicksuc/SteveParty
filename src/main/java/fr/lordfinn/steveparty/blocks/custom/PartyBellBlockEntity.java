package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.SyncedBlockEntity;
import fr.lordfinn.steveparty.board.LinkedParty;
import fr.lordfinn.steveparty.board.PartyLink;
import fr.lordfinn.steveparty.board.PartyLinked;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.utils.ServerMemory;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.util.StringIdentifiable;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Party Antenna (registry id {@code party_bell}): gives a redstone pulse when its moment of the party happens (see
 * {@link PartyMoment}), and remembers the value of that moment for comparators. In waiting mode, the party pauses at
 * that moment until the antenna receives a redstone signal.
 * <p>
 * An antenna is linked with the Tile Linker Brush to an element of a party ({@link #getLink}: the Party Controller, any
 * block of a board, a Mini-game Controller..., see {@link LinkedParty}) and listens to that party (running, or standing
 * on its end); unlinked, it hears nothing. Linked to a board no party runs on, it hears the free play moments (dice
 * rolled, token arrived...) of the tokens within {@link #RANGE} blocks.
 * <p>
 * Its head turns toward what it is linked to; unlinked, it slowly scans around its {@link PartyBellBlock#FACING}. The
 * turn and the lit receiver live on the client only ({@link #yaw}, {@link #ringTicks}).
 */
public class PartyBellBlockEntity extends SyncedBlockEntity implements GeoBlockEntity, TickableBlockEntity, PartyLinked {
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

    /** How an antenna filters the values of its moment: every value, or only those equal to, at least or at most its own. */
    public enum Filter implements StringIdentifiable {
        ANY("any"), EQUAL("equal"), AT_LEAST("at_least"), AT_MOST("at_most");

        private final String id;

        Filter(String id) {
            this.id = id;
        }

        @Override
        public String asString() {
            return id;
        }

        public Filter next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public boolean accepts(int value, int filterValue) {
            int heard = Math.clamp(value, 0, 15);
            return switch (this) {
                case ANY -> true;
                case EQUAL -> heard == filterValue;
                case AT_LEAST -> heard >= filterValue;
                case AT_MOST -> heard <= filterValue;
            };
        }

        public static Filter byId(String id) {
            for (Filter filter : values()) if (filter.id.equals(id)) return filter;
            return ANY;
        }
    }

    /** Value of the last moment heard, read by comparators (0-15). */
    private int value = 0;
    private Filter filter = Filter.ANY;
    private int filterValue = 1;
    /** The last time it heard its moment (world time, -1: never), the value then, and whether its filter let it ring. */
    private long heardAt = -1;
    private int heardValue;
    private boolean heardPassed;
    /** It rang in waiting mode and the party waits for its signal. */
    private boolean holding;
    /** Its test pulse is on: not a moment of the party (see {@link #ringingNear}). */
    private boolean testing;
    /** A party it listens to runs (checked every second, synced). */
    private boolean listening;
    /** Who placed it: the one who may still set it during a party (with creative players and operators). */
    private @Nullable UUID placer;
    /** Last redstone input, to act on rising edges only. */
    private boolean inputPowered = false;
    /** The party element it is linked to with the brush (unlinked, it hears nothing). */
    private final PartyLink link = new PartyLink(this, this::syncToClients);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Client: the head's turn (yaw about Y, 0 = receiver north; pitch), this tick and the last one. */
    public float yaw, lastYaw, pitch, lastPitch;
    private boolean turned;
    /** Client: ticks left of its receiver's light, 0 when dark; of its blink (clicked without the Wrench). */
    public int ringTicks, blinkTicks;
    public static final int BLINK_TICKS = 8;

    public PartyBellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PARTY_BELL_ENTITY, pos, state);
    }

    /** What its head turns to: what it is linked to, in its world; null for none. */
    public @Nullable BlockPos getFaced() {
        return link.inWorld();
    }

    /** The party element it is linked to (see {@link LinkedParty}), null for none. */
    public @Nullable GlobalPos getLink() {
        return link.get();
    }

    /** Links it to a party element (null: none), told to the clients (its head turns to it, the brush draws it). */
    public void setLink(@Nullable GlobalPos link) {
        this.link.set(link);
    }

    @Override
    public PartyLink partyLink() {
        return link;
    }

    @Override
    public void tick() {
        if (this.world instanceof ServerWorld serverWorld) {
            if ((serverWorld.getTime() + this.pos.hashCode()) % FACE_CHECK_TICKS == 0) check(serverWorld);
        } else if (this.world != null) {
            if (ringTicks > 0) ringTicks--;
            if (blinkTicks > 0) blinkTicks--;
            turnHead();
        }
    }

    /** Whether a party it listens to runs, as the server saw it at its last check (what its screen shows on the client). */
    public boolean inPartyOnClient() {
        return listening;
    }

    /** Every second: whether the party it listens to runs (its screen), and whether it still holds it. */
    private void check(ServerWorld world) {
        boolean nowListening = getListenedController(world).isPresent();
        if (nowListening != listening) {
            listening = nowListening;
            syncToClients();
        }
        // The party went on without its signal (hourglass, Party Rewinder, its mode changed): it holds nothing any more
        if (holding && getListenedController(world).map(controller -> controller.getPartyData().getCurrentStep())
                .map(step -> !step.isWaitingForBell()).orElse(true)) {
            holding = false;
            markDirty();
            syncToClients();
        }
    }

    /** Client: one tick of the head's turn toward its target, eased, at most {@link #TURN_SPEED}. */
    private void turnHead() {
        float targetYaw, targetPitch = 0;
        BlockPos faced = getFaced();
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
        if (type == PartyBellBlock.BLINK_EVENT) {
            if (this.world != null && this.world.isClient) blinkTicks = BLINK_TICKS;
            return true;
        }
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
        if (this.world instanceof ServerWorld serverWorld) {
            BELLS.remove(GlobalPos.create(this.world.getRegistryKey(), this.pos), this);
            // Broken or unloaded while holding the party: it lets go
            if (holding) letGo(serverWorld);
        }
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

    /**
     * It hears its moment with {@code value}: rings (a redstone pulse, the value for comparators) if its filter lets
     * it, and tells the clients (its screen shows the last moment heard).
     *
     * @return true if it rang
     */
    public boolean hear(int value) {
        if (!(this.world instanceof ServerWorld serverWorld)) return false;
        heardAt = serverWorld.getTime();
        heardValue = Math.clamp(value, 0, 15);
        heardPassed = filter.accepts(value, filterValue);
        if (heardPassed) {
            this.value = heardValue;
            testing = false;
            PartyBellBlock.pulse(serverWorld, this.pos, getCachedState());
        }
        markDirty();
        syncToClients();
        return heardPassed;
    }

    /** A test pulse from its screen (never during a party): the filter's value, else 15; ignored by {@link #ringingNear}. */
    public void test() {
        if (!(this.world instanceof ServerWorld serverWorld)) return;
        value = filter == Filter.ANY ? 15 : filterValue;
        testing = true;
        markDirty();
        PartyBellBlock.pulse(serverWorld, this.pos, getCachedState());
    }

    /** Its pulse is over. */
    void onPulseEnd() {
        testing = false;
    }

    public Filter getFilter() {
        return filter;
    }

    public int getFilterValue() {
        return filterValue;
    }

    public long getHeardAt() {
        return heardAt;
    }

    public int getHeardValue() {
        return heardValue;
    }

    public boolean heardPassed() {
        return heardPassed;
    }

    /** It holds the party: it rang in waiting mode and waits for its signal (synced: its receiver blinks). */
    public boolean isHolding() {
        return holding;
    }

    public void setFilter(Filter filter, int filterValue) {
        this.filter = filter;
        this.filterValue = Math.clamp(filterValue, 0, 15);
        markDirty();
        syncToClients();
    }

    public void setPlacer(@Nullable UUID placer) {
        this.placer = placer;
        markDirty();
    }

    /** During a party, only who placed it, creative players and operators may set it; out of a party, anyone. */
    public boolean maySet(PlayerEntity player) {
        if (player.isCreative() || player.hasPermissionLevel(2) || placer == null || placer.equals(player.getUuid())) return true;
        return !inParty();
    }

    /** Whether a party it listens to runs (its test pulse is off then). */
    public boolean inParty() {
        return this.world instanceof ServerWorld serverWorld && getListenedController(serverWorld).isPresent();
    }

    /**
     * Its settings as the Wrench copies them onto other antennas: its moment, filter and link. Never the waiting mode:
     * ten antennas holding the party without showing it would be the worst trap.
     */
    public NbtCompound copySettings() {
        NbtCompound settings = new NbtCompound();
        settings.putString("Moment", getMoment().asString());
        settings.putString("Filter", filter.asString());
        settings.putInt("FilterValue", filterValue);
        link.write(settings);
        return settings;
    }

    /** Takes the settings of {@link #copySettings} (its waiting mode kept, off if the new moment never waits). */
    public void pasteSettings(NbtCompound settings) {
        if (this.world == null) return;
        PartyMoment moment = PartyMoment.byName(settings.getString("Moment"), getMoment());
        setFilter(Filter.byId(settings.getString("Filter")), settings.getInt("FilterValue"));
        if (settings.contains("LinkPos")) {
            PartyLink copied = new PartyLink(this, () -> { });
            copied.read(settings);
            setLink(copied.get());
        }
        BlockState state = getCachedState().with(PartyBellBlock.MOMENT, moment);
        if (!moment.canWait()) state = state.with(PartyBellBlock.WAITING, false);
        if (state != getCachedState()) this.world.setBlockState(this.pos, state, Block.NOTIFY_ALL);
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
        if (!risingEdge || ownPulse || !holding || !(this.world instanceof ServerWorld serverWorld)) return;
        letGo(serverWorld);
    }

    /** It stops holding the party; the last of its controller's antennas holding the moment lets the party go on. */
    private void letGo(ServerWorld world) {
        holding = false;
        markDirty();
        syncToClients();
        getListenedController(world).ifPresent(controller -> {
            if (!isHeld(controller, getMoment())) controller.releaseMoment(getMoment());
        });
    }

    public void initRedstoneInput(boolean powered) {
        this.inputPowered = powered;
    }

    /** The party of its link, running or standing on its end; empty when unlinked. */
    private Optional<PartyControllerEntity> getListenedController(ServerWorld world) {
        return Optional.ofNullable(link.runningParty(world, true));
    }

    private boolean listensTo(PartyControllerEntity controller) {
        if (this.isRemoved()) return false;
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
            if (!bell.hear(value) || !bell.isWaiting()) continue;
            bell.holding = true;
            bell.syncToClients();
            waiting = true;
        }
        return waiting;
    }

    /** @return true if an antenna of {@code controller} listens to one of {@code moments} (worth watching them) */
    public static boolean isListened(PartyControllerEntity controller, List<PartyMoment> moments) {
        for (PartyBellBlockEntity bell : bells()) {
            if (moments.contains(bell.getMoment()) && bell.listensTo(controller)) return true;
        }
        return false;
    }

    /** @return true if an antenna of {@code controller} still holds the party at {@code moment} (waits for its signal) */
    public static boolean isHeld(PartyControllerEntity controller, PartyMoment moment) {
        for (PartyBellBlockEntity bell : bells()) {
            if (bell.holding && bell.getMoment() == moment && bell.isWaiting() && bell.listensTo(controller)) return true;
        }
        return false;
    }

    /** The party went on at {@code moment} (signals, the Party Rewinder's Next, an hourglass): no antenna holds it any more. */
    public static void released(PartyControllerEntity controller, PartyMoment moment) {
        for (PartyBellBlockEntity bell : bells()) {
            if (!bell.holding || bell.getMoment() != moment || !bell.listensTo(controller)) continue;
            bell.holding = false;
            bell.markDirty();
            bell.syncToClients();
        }
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
            if (bell.isRemoved() || bell.world != world || bell.testing || !bell.getCachedState().get(PartyBellBlock.POWERED)) continue;
            double distance = bell.pos.getSquaredDistance(pos);
            if (distance > best || !bell.listensTo(controller)) continue;
            best = distance;
            nearest = bell;
        }
        return nearest == null ? null : nearest.getMoment();
    }

    /** @return true if an antenna of the controller in waiting mode would ring at {@code moment} with {@code value} */
    public static boolean hasWaitingBell(PartyControllerEntity controller, PartyMoment moment, int value) {
        if (!moment.canWait()) return false;
        for (PartyBellBlockEntity bell : bells()) {
            if (bell.getMoment() == moment && bell.isWaiting() && bell.filter.accepts(value, bell.filterValue)
                    && bell.listensTo(controller)) return true;
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
            if (bell.link.get() == null || bell.pos.getSquaredDistance(pos) >= (double) RANGE * RANGE) continue;
            if (bell.getListenedController(world).isPresent()) continue;
            bell.hear(value);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("Value", value);
        nbt.putBoolean("InputPowered", inputPowered);
        link.write(nbt);
        nbt.putString("Filter", filter.asString());
        nbt.putInt("FilterValue", filterValue);
        nbt.putLong("HeardAt", heardAt);
        nbt.putInt("HeardValue", heardValue);
        nbt.putBoolean("HeardPassed", heardPassed);
        nbt.putBoolean("Holding", holding);
        nbt.putBoolean("Listening", listening);
        if (placer != null) nbt.putUuid("Placer", placer);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        value = Math.clamp(nbt.getInt("Value"), 0, 15);
        inputPowered = nbt.getBoolean("InputPowered");
        link.read(nbt);
        filter = Filter.byId(nbt.getString("Filter"));
        filterValue = nbt.contains("FilterValue") ? Math.clamp(nbt.getInt("FilterValue"), 0, 15) : 1;
        heardAt = nbt.contains("HeardAt") ? nbt.getLong("HeardAt") : -1;
        heardValue = nbt.getInt("HeardValue");
        heardPassed = nbt.getBoolean("HeardPassed");
        holding = nbt.getBoolean("Holding");
        listening = nbt.getBoolean("Listening");
        placer = nbt.containsUuid("Placer") ? nbt.getUuid("Placer") : null;
    }
}
