package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.screen_handlers.custom.LootingBoxScreenHandler;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Podium: ends the mini-game being played and names its winners.
 * <ul>
 *     <li>"First arrived" mode (default): the first participant to step on it wins.</li>
 *     <li>"On signal" mode: stepping on it does nothing; a redstone pulse ends the mini-game and the participants
 *     standing on it win (team or "last one standing" games).</li>
 * </ul>
 * In both modes a redstone pulse ends the mini-game with the participants standing on it. The winners receive the
 * items of its inventory cartridge (from the linked chest). Out of a party it is a finish line: a pulse each time a
 * player steps on it; the comparator gives the number of players standing on it.
 */
public class PodiumBlockEntity extends CartridgeContainerBlockEntity implements TickableBlockEntity {
    private static final int CHECK_INTERVAL_TICKS = 2;

    public enum Mode implements StringIdentifiable {
        FIRST_ARRIVED("first_arrived"),
        ON_SIGNAL("on_signal");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String asString() {
            return name;
        }

        public Text getText() {
            return Text.translatable("podium_mode.steveparty." + name);
        }
    }

    private Mode mode = Mode.FIRST_ARRIVED;
    private final Set<UUID> playersOn = new LinkedHashSet<>();
    private boolean inputPowered = false;
    private int cycleIndex = 0;

    public PodiumBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PODIUM_ENTITY, pos, state, 1);
    }

    public Mode getMode() {
        return mode;
    }

    public void cycleMode() {
        mode = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
        markDirty();
    }

    public int getPlayersOnCount() {
        return playersOn.size();
    }

    @Override
    public void tick() {
        if (!(this.world instanceof ServerWorld world) || world.getTime() % CHECK_INTERVAL_TICKS != 0) return;
        Set<UUID> now = new LinkedHashSet<>();
        List<ServerPlayerEntity> arrived = new ArrayList<>();
        for (ServerPlayerEntity player : getPlayersStandingOn(world)) {
            now.add(player.getUuid());
            if (!playersOn.contains(player.getUuid())) arrived.add(player);
        }
        boolean changed = !now.equals(playersOn);
        playersOn.clear();
        playersOn.addAll(now);
        if (changed) world.updateComparators(this.pos, getCachedState().getBlock());
        for (ServerPlayerEntity player : arrived) onArrival(world, player);
    }

    private List<ServerPlayerEntity> getPlayersStandingOn(ServerWorld world) {
        Box above = new Box(pos.getX(), pos.getY() + 0.5, pos.getZ(), pos.getX() + 1, pos.getY() + 1.6, pos.getZ() + 1);
        return world.getEntitiesByClass(ServerPlayerEntity.class, above, player -> !player.isSpectator() && player.isAlive());
    }

    private void onArrival(ServerWorld world, ServerPlayerEntity player) {
        PodiumBlock.pulse(world, pos, getCachedState());
        if (mode != Mode.FIRST_ARRIVED) return;
        findPlayedMiniGame(world, player.getUuid()).ifPresent(controller -> finish(world, controller, List.of(player)));
    }

    /** Neighbor update: a rising edge ends the mini-game, the participants standing on the podium win. */
    public void onRedstoneInput(boolean powered) {
        boolean risingEdge = powered && !inputPowered;
        if (powered != inputPowered) {
            inputPowered = powered;
            markDirty();
        }
        if (!risingEdge || !(this.world instanceof ServerWorld world)) return;
        List<ServerPlayerEntity> standing = getPlayersStandingOn(world);
        findPlayedMiniGame(world, null).ifPresent(controller -> finish(world, controller, standing));
    }

    public void initRedstoneInput(boolean powered) {
        this.inputPowered = powered;
    }

    /**
     * The party whose mini-game is being played: the one of the participant if given, else the closest one of
     * this world (a mini-game arena may be far from its party controller).
     */
    private Optional<PartyControllerEntity> findPlayedMiniGame(ServerWorld world, @Nullable UUID participant) {
        return PartyControllerEntity.getActivePartyControllers().stream()
                .filter(controller -> !controller.isRemoved() && controller.getWorld() == world)
                .filter(controller -> controller.getPartyData().getCurrentStep() instanceof MiniGamePartyStep miniGame
                        && miniGame.isPlaying() && (participant == null || miniGame.getParticipants().contains(participant)))
                .min(Comparator.comparingDouble(controller -> controller.getPos().getSquaredDistance(this.pos)));
    }

    private void finish(ServerWorld world, PartyControllerEntity controller, List<ServerPlayerEntity> standing) {
        if (!(controller.getPartyData().getCurrentStep() instanceof MiniGamePartyStep miniGame)) return;
        List<ServerPlayerEntity> winners = standing.stream()
                .filter(player -> miniGame.getParticipants().contains(player.getUuid())).toList();
        if (!miniGame.finish(controller, winners.stream().map(PlayerEntity::getUuid).toList())) return;
        for (ServerPlayerEntity winner : winners)
            CartridgeTransfers.apply(world, getStack(0), winner, () -> cycleIndex, index -> cycleIndex = index);
        markDirty();
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new LootingBoxScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.steveparty.podium");
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.writeNbt(nbt, wrapper);
        nbt.putString("Mode", mode.asString());
        nbt.putBoolean("InputPowered", inputPowered);
        nbt.putInt("CycleIndex", cycleIndex);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        mode = Mode.FIRST_ARRIVED;
        for (Mode value : Mode.values()) {
            if (value.asString().equals(nbt.getString("Mode"))) mode = value;
        }
        inputPowered = nbt.getBoolean("InputPowered");
        cycleIndex = nbt.getInt("CycleIndex");
    }
}
