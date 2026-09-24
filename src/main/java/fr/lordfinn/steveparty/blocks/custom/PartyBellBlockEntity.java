package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Party bell: gives a redstone pulse when its moment of the party happens (see {@link PartyMoment}), and remembers
 * the value of that moment for comparators. In waiting mode, the party pauses at that moment until the bell
 * receives a redstone signal.
 * <p>
 * A bell listens to the closest party controller within {@link #RANGE} blocks (running party, or party standing on
 * its end). Without a party controller around, it hears the free play moments (dice rolled, token arrived) of the
 * tokens within {@link #RANGE} blocks.
 */
public class PartyBellBlockEntity extends BlockEntity {
    public static final int RANGE = 64;
    /** Loaded server-side bells, keyed by dimension + position. */
    private static final Map<GlobalPos, PartyBellBlockEntity> BELLS = new LinkedHashMap<>();

    static {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BELLS.clear());
    }

    /** Value of the last moment heard, read by comparators (0-15). */
    private int value = 0;
    /** Last redstone input, to act on rising edges only. */
    private boolean inputPowered = false;

    public PartyBellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PARTY_BELL_ENTITY, pos, state);
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
        if (this.world != null)
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

    private java.util.Optional<PartyControllerEntity> getListenedController(ServerWorld world) {
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
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        value = Math.clamp(nbt.getInt("Value"), 0, 15);
        inputPowered = nbt.getBoolean("InputPowered");
    }
}
