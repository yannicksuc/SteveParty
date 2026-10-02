package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.components.TileStampComponent;
import fr.lordfinn.steveparty.podium.PodiumOccupant;
import fr.lordfinn.steveparty.podium.PodiumSignal;
import fr.lordfinn.steveparty.podium.Podiums;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A block of a podium column. The bottom block of the column keeps what the column is: who is registered on it
 * ({@link PodiumOccupant}, shown to everyone as a small figure on its top), what a redstone pulse does
 * ({@link PodiumSignal}) and the pattern stamped on its banner. The top block watches the players sneaking on it.
 * What registering means, the groups and the places are in {@link Podiums}.
 */
public class PodiumBlockEntity extends BlockEntity implements TickableBlockEntity {
    private static final int CHECK_INTERVAL_TICKS = 2;

    // --- Kept by the bottom block of the column
    private PodiumSignal signal = PodiumSignal.REGISTER;
    private @Nullable PodiumOccupant occupant;
    private boolean inputPowered = false;
    /** The look stamped on the banner (drawn on the top block). */
    private @Nullable TileStampComponent bannerStamp;

    // --- Top block of the column, not saved
    /** The players sneaking on the top at the last check: only a new sneak registers. */
    private final Set<UUID> sneaking = new HashSet<>();

    public PodiumBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PODIUM_ENTITY, pos, state);
    }

    // ---------------------------------------------------------------- the column's settings

    public PodiumSignal getSignal() {
        return signal;
    }

    public void setSignal(PodiumSignal signal) {
        if (signal == this.signal) return;
        this.signal = signal;
        sync();
    }

    public @Nullable PodiumOccupant getOccupant() {
        return occupant;
    }

    /** Who is registered on the column (null: nobody). Told to the clients and to the comparators of the column. */
    public void setOccupant(@Nullable PodiumOccupant occupant) {
        if (Objects.equals(this.occupant, occupant)) return;
        boolean wasEmpty = this.occupant == null;
        this.occupant = occupant;
        sync();
        if (world != null && !world.isClient && wasEmpty != (occupant == null)) {
            BlockPos top = PodiumBlock.topOf(world, pos);
            for (BlockPos block = pos; block.getY() <= top.getY(); block = block.up()) {
                world.updateComparators(block, world.getBlockState(block).getBlock());
            }
        }
    }

    public @Nullable TileStampComponent getBannerStamp() {
        return bannerStamp;
    }

    public void setBannerStamp(@Nullable TileStampComponent stamp) {
        if (Objects.equals(bannerStamp, stamp)) return;
        bannerStamp = stamp;
        sync();
    }

    private void sync() {
        markDirty();
        if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_ALL);
    }

    // ---------------------------------------------------------------- sneaking on its top

    @Override
    public void tick() {
        if (!(this.world instanceof ServerWorld world) || world.getTime() % CHECK_INTERVAL_TICKS != 0) return;
        List<ServerPlayerEntity> standing = getPlayersStandingOn(world);
        if (standing.isEmpty()) {
            sneaking.clear();
            return;
        }
        Set<UUID> now = new HashSet<>();
        for (ServerPlayerEntity player : standing) {
            if (!player.isSneaking()) continue;
            now.add(player.getUuid());
            if (!sneaking.contains(player.getUuid())) Podiums.toggle(player, world, pos);
        }
        sneaking.clear();
        sneaking.addAll(now);
    }

    /** Players standing on the top of the column this block belongs to. */
    private List<ServerPlayerEntity> getPlayersStandingOn(ServerWorld world) {
        BlockPos top = PodiumBlock.topOf(world, pos);
        BlockState state = world.getBlockState(top);
        double surface = top.getY() + (PodiumBlock.isPodium(state) && state.get(PodiumBlock.FULL) ? 1.0 : 0.5);
        Box above = new Box(top.getX(), surface - 0.1, top.getZ(), top.getX() + 1, surface + 0.6, top.getZ() + 1);
        // Feet on the top (not a player standing against the column, nor one jumping over it)
        return world.getEntitiesByClass(ServerPlayerEntity.class, above,
                player -> !player.isSpectator() && player.isAlive() && player.getY() >= surface - 0.1 && player.getY() <= surface + 0.6);
    }

    // ---------------------------------------------------------------- redstone

    /** A neighbour of the column changed: a rising edge is a pulse, the column does what its signal setting says. */
    public void onRedstoneInput(boolean powered) {
        boolean risingEdge = powered && !inputPowered;
        if (powered != inputPowered) {
            inputPowered = powered;
            markDirty();
        }
        if (risingEdge && this.world instanceof ServerWorld world) Podiums.onSignal(world, pos);
    }

    public void initRedstoneInput(boolean powered) {
        this.inputPowered = powered;
    }

    // ---------------------------------------------------------------- saving and sync

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.writeNbt(nbt, wrapper);
        nbt.putString("Signal", signal.name());
        nbt.putBoolean("InputPowered", inputPowered);
        if (occupant != null) nbt.put("Occupant", occupant.toNbt());
        if (bannerStamp != null)
            TileStampComponent.CODEC.encodeStart(wrapper.getOps(NbtOps.INSTANCE), bannerStamp)
                    .ifSuccess(element -> nbt.put("BannerStamp", element));
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        signal = PodiumSignal.byName(nbt.getString("Signal"));
        inputPowered = nbt.getBoolean("InputPowered");
        occupant = nbt.contains("Occupant", NbtElement.COMPOUND_TYPE) ? PodiumOccupant.fromNbt(nbt.getCompound("Occupant")) : null;
        bannerStamp = nbt.contains("BannerStamp")
                ? TileStampComponent.CODEC.parse(wrapper.getOps(NbtOps.INSTANCE), nbt.get("BannerStamp")).result().orElse(null)
                : null;
    }
}
