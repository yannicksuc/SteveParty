package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlock;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeSpawnMarker;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Where the mob of a mob space appears, for every space that summons one (Frousseux, Glandouille, Mistigri,
 * Trichaudron, and any other: {@link #spot}): on the Spawn Marker its cartridge links, facing the marker's way, lifted
 * as the marker says; without one (or one broken), where the show puts it by default (beside the space, on the
 * token's side). The rules of the mobs themselves do not change (BoardActors: holograms).
 */
public final class BoardMobSpots {
    /**
     * Where a mob appears: its position (lift included), the way it faces (a yaw), how high above its marker
     * ({@code lift}, blocks; it floats there: see {@link #hold}), and the marker ({@code null}: the show's default).
     */
    public record Spot(@Nullable BlockPos marker, Vec3d pos, float yaw, double lift) {
        public boolean onMarker() {
            return marker != null;
        }

        /** Lifted above (or below) its marker: it must not fall. */
        public boolean lifted() {
            return lift != 0;
        }
    }

    private BoardMobSpots() {
    }

    /**
     * Where the mob of the space at {@code tile} appears: on its Spawn Marker if it has one, else at
     * {@code defaultPos} facing {@code defaultYaw}.
     */
    public static Spot spot(ServerWorld world, BlockPos tile, Vec3d defaultPos, float defaultYaw) {
        Spot marker = marker(world, tile);
        return marker != null ? marker : new Spot(null, defaultPos, defaultYaw, 0);
    }

    /** The Spawn Marker of the active cartridge of the space at {@code tile}, as a spot; null for none. */
    public static @Nullable Spot marker(ServerWorld world, BlockPos tile) {
        BoardSpaceBlockEntity space = ABoardSpaceBlock.getBoardSpaceEntity(world, tile);
        if (space == null) return null;
        ItemStack cartridge = space.getActiveCartridgeItemStack();
        if (!CartridgeSpawnMarker.spawnsMobs(cartridge)) return null;
        BlockPos marker = CartridgeSpawnMarker.marker(cartridge, world);
        if (marker == null) return null;
        return at(world, marker);
    }

    /** The spot of the Spawn Marker at {@code marker}. */
    public static Spot at(ServerWorld world, BlockPos marker) {
        double lift = world.getBlockEntity(marker) instanceof SpawnMarkerBlockEntity entity ? entity.getLift() : 0;
        return new Spot(marker, SpawnMarkerBlock.standPos(marker).add(0, lift, 0), SpawnMarkerBlock.yaw(world.getBlockState(marker)), lift);
    }

    /** {@code mob} (before it is spawned) appearing at {@code spot}: lifted, it floats there (a hologram, no gravity). */
    public static void hold(Entity mob, @Nullable Spot spot) {
        if (spot != null && spot.lifted()) mob.setNoGravity(true);
    }

    /**
     * The show {@code sequence} of a space starts at {@code spot}: the mob living on its marker (« always visible »,
     * see {@link MarkerResidents}) steps out for it, and is back once the show is over.
     */
    public static void showStarts(ServerWorld world, @Nullable Spot spot, UUID sequence) {
        if (spot != null && spot.marker() != null) MarkerResidents.showStarts(world, spot.marker(), sequence);
    }
}
