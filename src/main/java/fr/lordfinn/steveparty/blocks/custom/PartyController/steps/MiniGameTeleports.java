package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.items.custom.teleportation_books.HereWeComeBookItem;
import fr.lordfinn.steveparty.items.custom.teleportation_books.TeleportingTarget;
import fr.lordfinn.steveparty.persistent_state.TeleportationPadBooksStorage;
import fr.lordfinn.steveparty.persistent_state.TeleportationPadStorageManager;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static fr.lordfinn.steveparty.components.ModComponents.DESTINATIONS_COMPONENT;
import static fr.lordfinn.steveparty.components.ModComponents.TP_TARGETS;

/**
 * Sends the players of a mini-game onto its arrival pads: the pads listed by the mini-game page, each holding a
 * "Here we come" book whose targets say who may arrive there (team A, team B, any player) and how many.
 */
public final class MiniGameTeleports {
    private MiniGameTeleports() {}

    /** One target of one arrival pad, with its remaining capacity. */
    private static final class Seat {
        final BlockPos pad;
        final TeleportingTarget.Group group;
        final int weight;
        final int order;
        long remaining;

        Seat(BlockPos pad, TeleportingTarget target, int order) {
            this.pad = pad;
            this.group = target.getGroup();
            this.weight = target.getFillPriorityWeight();
            this.order = order;
            this.remaining = target.getCheckedFillCapacity();
        }
    }

    /** The arrival pads of a mini-game page: pad position -> targets of its book (pads without book are skipped). */
    public static Map<BlockPos, List<TeleportingTarget>> getArrivalPads(ServerWorld world, ItemStack miniGamePage) {
        Map<BlockPos, List<TeleportingTarget>> pads = new LinkedHashMap<>();
        TeleportationPadBooksStorage storage = TeleportationPadStorageManager.getBooksStorage(world);
        for (BlockPos pos : miniGamePage.getOrDefault(DESTINATIONS_COMPONENT, DestinationsComponent.DEFAULT).destinations()) {
            ItemStack book = storage.getTeleportationPadBook(pos);
            if (book == null || book.isEmpty() || !(book.getItem() instanceof HereWeComeBookItem)) continue;
            List<TeleportingTarget> targets = new ArrayList<>();
            for (TeleportingTarget target : book.getOrDefault(TP_TARGETS, List.<TeleportingTarget>of())) {
                if (target != null) targets.add(target.copy());
            }
            pads.put(pos.toImmutable(), targets);
        }
        return pads;
    }

    /**
     * Assigns an arrival pad to each player not assigned yet, keeping the existing assignments (their seats are
     * taken first). Team A of the disposition is the smaller team: it goes to the pads group with the smaller
     * capacity, like when the mini-game was chosen. A player gets a seat of their team first, then a seat for any
     * player; within a group the highest priority weight comes first.
     *
     * @return the assignments (existing ones included); a player without any free seat is left out
     */
    public static Map<UUID, BlockPos> assign(Map<BlockPos, List<TeleportingTarget>> pads, @Nullable TeamDisposition disposition,
                                             List<UUID> players, Map<UUID, BlockPos> existing) {
        List<Seat> seats = new ArrayList<>();
        long capacityA = 0, capacityB = 0;
        int order = 0;
        for (Map.Entry<BlockPos, List<TeleportingTarget>> pad : pads.entrySet()) {
            for (TeleportingTarget target : pad.getValue()) {
                if (target.getGroup() == TeleportingTarget.Group.SPECTATORS) continue;
                Seat seat = new Seat(pad.getKey(), target, order++);
                seats.add(seat);
                if (seat.group == TeleportingTarget.Group.PLAYER_TEAM_A) capacityA += seat.remaining;
                if (seat.group == TeleportingTarget.Group.PLAYER_TEAM_B) capacityB += seat.remaining;
            }
        }
        boolean swapped = capacityA > capacityB;

        Map<UUID, BlockPos> assignments = new LinkedHashMap<>(existing);
        // Seats already taken
        for (Map.Entry<UUID, BlockPos> assignment : existing.entrySet()) {
            TeleportingTarget.Group group = groupOf(assignment.getKey(), disposition, swapped);
            candidates(seats, group).stream().filter(seat -> seat.pad.equals(assignment.getValue()) && seat.remaining > 0)
                    .findFirst().ifPresent(seat -> seat.remaining--);
        }
        for (UUID player : players) {
            if (assignments.containsKey(player)) continue;
            for (Seat seat : candidates(seats, groupOf(player, disposition, swapped))) {
                if (seat.remaining <= 0) continue;
                seat.remaining--;
                assignments.put(player, seat.pad);
                break;
            }
        }
        return assignments;
    }

    private static TeleportingTarget.Group groupOf(UUID player, @Nullable TeamDisposition disposition, boolean swapped) {
        if (disposition == null) return TeleportingTarget.Group.PLAYERS;
        if (disposition.teamA.contains(player))
            return swapped ? TeleportingTarget.Group.PLAYER_TEAM_B : TeleportingTarget.Group.PLAYER_TEAM_A;
        if (disposition.teamB.contains(player))
            return swapped ? TeleportingTarget.Group.PLAYER_TEAM_A : TeleportingTarget.Group.PLAYER_TEAM_B;
        return TeleportingTarget.Group.PLAYERS;
    }

    /** Seats a player of {@code group} may take, best first: own team, then any player. */
    private static List<Seat> candidates(List<Seat> seats, TeleportingTarget.Group group) {
        List<Seat> result = new ArrayList<>();
        Comparator<Seat> best = Comparator.<Seat>comparingInt(seat -> -seat.weight).thenComparingInt(seat -> seat.order);
        if (group == TeleportingTarget.Group.PLAYER_TEAM_A || group == TeleportingTarget.Group.PLAYER_TEAM_B)
            seats.stream().filter(seat -> seat.group == group).sorted(best).forEach(result::add);
        seats.stream().filter(seat -> seat.group == TeleportingTarget.Group.PLAYERS || seat.group == TeleportingTarget.Group.EVERYONE)
                .sorted(best).forEach(result::add);
        return result;
    }

    /** Position standing on top of the block at {@code pos} (a pad is lower than a full block). */
    public static Vec3d standingPos(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(world, pos);
        double height = shape.isEmpty() ? 0 : shape.getMax(Direction.Axis.Y);
        return new Vec3d(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5);
    }

    public static void teleport(ServerPlayerEntity player, ServerWorld world, Vec3d target, float yaw, float pitch) {
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_TELEPORT, SoundCategory.PLAYERS, 0.6f, 1.0f);
        player.teleport(world, target.x, target.y, target.z, Set.of(), yaw, pitch, true);
        player.fallDistance = 0;
        world.playSound(null, BlockPos.ofFloored(target), SoundEvents.ENTITY_PLAYER_TELEPORT, SoundCategory.PLAYERS, 0.6f, 1.2f);
    }
}
