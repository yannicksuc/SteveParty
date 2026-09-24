package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.screen_handlers.custom.LootingBoxScreenHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Piggy bank: on each redstone pulse, applies its inventory cartridge (linked to a chest) to a target: the chest
 * gives the cartridge items to the target, and takes the items marked negative from them. With the "all items"
 * selection it is all-or-nothing, so it can sell a star for coins. The comparator gives 15 when the last pulse
 * succeeded, 0 otherwise.
 */
public class PiggyBankBlockEntity extends CartridgeContainerBlockEntity {
    /** Radius in which the "nearest player" target (and the free play fallbacks) look for players. */
    public static final int NEAREST_PLAYER_RANGE = 8;

    public enum Target implements StringIdentifiable {
        /** The owner of the token whose turn it is (free play: the nearest player). */
        TURN_PLAYER("turn_player"),
        /** The winners of the last mini-game. */
        WINNERS("winners"),
        /** Every player of the party (free play: every player nearby). */
        EVERYONE("everyone"),
        /** The nearest player. */
        NEAREST("nearest");

        private final String name;

        Target(String name) {
            this.name = name;
        }

        @Override
        public String asString() {
            return name;
        }

        public Text getText() {
            return Text.translatable("piggy_bank_target.steveparty." + name);
        }

        public Target next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private Target target = Target.TURN_PLAYER;
    private int cycleIndex = 0;
    private boolean lastSucceeded = false;
    private boolean inputPowered = false;

    public PiggyBankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIGGY_BANK_ENTITY, pos, state, 1);
    }

    public Target getTarget() {
        return target;
    }

    public void cycleTarget() {
        target = target.next();
        markDirty();
    }

    public boolean hasLastSucceeded() {
        return lastSucceeded;
    }

    /** Neighbor update: acts on the rising edge of the received power. */
    public void onRedstoneInput(boolean powered) {
        boolean risingEdge = powered && !inputPowered;
        if (powered != inputPowered) {
            inputPowered = powered;
            markDirty();
        }
        if (risingEdge) activate();
    }

    public void initRedstoneInput(boolean powered) {
        this.inputPowered = powered;
    }

    /** Applies the cartridge to each target player. @return true if it succeeded for at least one of them */
    public boolean activate() {
        if (!(this.world instanceof ServerWorld serverWorld)) return false;
        boolean succeeded = false;
        for (ServerPlayerEntity player : findTargets(serverWorld)) {
            succeeded |= CartridgeTransfers.apply(serverWorld, getStack(0), player, () -> cycleIndex, index -> cycleIndex = index);
        }
        lastSucceeded = succeeded;
        markDirty();
        serverWorld.updateComparators(this.pos, getCachedState().getBlock());
        serverWorld.playSound(null, this.pos, succeeded ? SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP : SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                SoundCategory.BLOCKS, 0.8f, succeeded ? 1.2f : 0.6f);
        return succeeded;
    }

    public List<ServerPlayerEntity> findTargets(ServerWorld world) {
        Optional<PartyControllerEntity> party = PartyControllerEntity.getClosestSteppablePartyControllerEntity(
                world, this.pos, PartyBellBlockEntity.RANGE, true);
        List<ServerPlayerEntity> players = new ArrayList<>();
        switch (target) {
            case TURN_PLAYER -> {
                if (party.isEmpty()) {
                    nearest(world).ifPresent(players::add);
                } else if (party.get().getPartyData().getCurrentStep() instanceof TokenTurnPartyStep turn) {
                    UUID owner = party.get().getTokenOwner(turn.getTokenUUID());
                    if (owner != null) addOnline(world, owner, players);
                }
            }
            case WINNERS -> party.ifPresent(controller -> controller.getLastWinners().forEach(uuid -> addOnline(world, uuid, players)));
            case EVERYONE -> {
                if (party.isPresent()) party.get().getPlayersInOrder().forEach(uuid -> addOnline(world, uuid, players));
                else players.addAll(world.getPlayers(player -> !player.isSpectator()
                        && player.squaredDistanceTo(this.pos.toCenterPos()) <= NEAREST_PLAYER_RANGE * NEAREST_PLAYER_RANGE));
            }
            case NEAREST -> nearest(world).ifPresent(players::add);
        }
        return players;
    }

    private Optional<ServerPlayerEntity> nearest(ServerWorld world) {
        PlayerEntity player = world.getClosestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, NEAREST_PLAYER_RANGE, false);
        return player instanceof ServerPlayerEntity serverPlayer && !player.isSpectator() ? Optional.of(serverPlayer) : Optional.empty();
    }

    private static void addOnline(ServerWorld world, UUID uuid, List<ServerPlayerEntity> players) {
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
        if (player != null && !players.contains(player)) players.add(player);
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new LootingBoxScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.steveparty.piggy_bank");
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.writeNbt(nbt, wrapper);
        nbt.putString("Target", target.asString());
        nbt.putInt("CycleIndex", cycleIndex);
        nbt.putBoolean("LastSucceeded", lastSucceeded);
        nbt.putBoolean("InputPowered", inputPowered);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        target = Target.TURN_PLAYER;
        for (Target value : Target.values()) {
            if (value.asString().equals(nbt.getString("Target"))) target = value;
        }
        cycleIndex = nbt.getInt("CycleIndex");
        lastSucceeded = nbt.getBoolean("LastSucceeded");
        inputPowered = nbt.getBoolean("InputPowered");
    }
}
