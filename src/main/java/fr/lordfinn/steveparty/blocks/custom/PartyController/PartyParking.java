package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pawns set aside by a party on a space that is not their start tile: on an End space while they wait for the others
 * (an End Cartridge set to wait), on the arrival spaces or the podiums of their Party Controller once the party is over
 * (see PartyEnding). A pawn alone stands still in the middle of its space; several on the same space are drawn smaller
 * and turn slowly around its middle, so that every one of them can be seen.
 * <p>
 * Saved with the controller. A pawn leaves when someone takes it more than {@link #LEAVE_DISTANCE} blocks away, when its
 * controller starts a new party (from its start tile: a parked pawn still plays, see PartyBoard), or when it is sent home.
 */
public final class PartyParking {
    /** Smaller when sharing a space: a modifier never saved (put again on load by the tick). */
    static final Identifier SMALL = Steveparty.id("parked_small");
    static final double SMALL_SCALE = 0.5;
    /** The widest a pawn sharing a space gets (blocks). */
    private static final double MAX_WIDTH = 0.45;
    /** Around the middle of the space, the pawns sharing it (blocks): wider the more they are. */
    private static final double RADIUS = 0.5, RADIUS_STEP = 0.06, MAX_RADIUS = 0.8;
    /** A full turn in this many ticks: slow. */
    private static final double TURN_TICKS = 240;
    /** Taken this far from its space, a pawn is no longer parked (blocks). */
    static final double LEAVE_DISTANCE = 2.5;

    /** Each parked pawn's space. */
    private final Map<UUID, BlockPos> parked = new LinkedHashMap<>();

    public Map<UUID, BlockPos> parked() {
        return java.util.Collections.unmodifiableMap(parked);
    }

    public boolean isParked(UUID token) {
        return parked.containsKey(token);
    }

    public @Nullable BlockPos spaceOf(UUID token) {
        return parked.get(token);
    }

    /** {@code token} is set aside on the space at {@code space} (it is put there by the next tick). */
    void park(ServerWorld world, UUID token, BlockPos space) {
        parked.put(token, space.toImmutable());
        if (world.getEntity(token) instanceof MobEntity mob) {
            mob.setVelocity(Vec3d.ZERO);
            place(world, mob, space, 0, 1, world.getTime());
        }
    }

    /** {@code token} is no longer parked: its own size again. */
    void unpark(ServerWorld world, UUID token) {
        if (parked.remove(token) != null && world.getEntity(token) instanceof MobEntity mob) restoreSize(mob);
    }

    /** Every pawn leaves (a new party starts). */
    void unparkAll(ServerWorld world) {
        for (UUID token : List.copyOf(parked.keySet())) unpark(world, token);
    }

    /** Puts the parked pawns in their place. @return true if one left (to save) */
    boolean tick(ServerWorld world) {
        if (parked.isEmpty()) return false;
        boolean changed = false;
        // The loaded pawns of each space, in their order
        Map<BlockPos, List<MobEntity>> spaces = new LinkedHashMap<>();
        Iterator<Map.Entry<UUID, BlockPos>> iterator = parked.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, BlockPos> entry = iterator.next();
            if (!world.isChunkLoaded(entry.getValue())) continue;
            if (!(world.getEntity(entry.getKey()) instanceof MobEntity mob)) continue;
            if (!mob.isAlive() || mob.hasVehicle()
                    || mob.getPos().squaredDistanceTo(BoardSpaces.standPos(world, entry.getValue())) > LEAVE_DISTANCE * LEAVE_DISTANCE) {
                // Taken away: free again
                restoreSize(mob);
                iterator.remove();
                changed = true;
                continue;
            }
            spaces.computeIfAbsent(entry.getValue(), pos -> new ArrayList<>()).add(mob);
        }
        long time = world.getTime();
        spaces.forEach((space, mobs) -> {
            for (int i = 0; i < mobs.size(); i++) place(world, mobs.get(i), space, i, mobs.size(), time);
        });
        return changed;
    }

    /** The {@code index}-th of {@code count} pawns on {@code space}: alone in the middle, else small on a slow ring. */
    private static void place(ServerWorld world, MobEntity mob, BlockPos space, int index, int count, long time) {
        Vec3d stand = BoardSpaces.standPos(world, space);
        if (count <= 1) {
            restoreSize(mob);
            if (mob.getPos().squaredDistanceTo(stand) > 0.0025) mob.requestTeleport(stand.x, stand.y, stand.z);
            mob.setVelocity(Vec3d.ZERO);
            return;
        }
        shrink(mob);
        double angle = 2 * Math.PI * (index / (double) count + time / TURN_TICKS);
        double radius = Math.min(MAX_RADIUS, RADIUS + RADIUS_STEP * (count - 2));
        double x = stand.x + Math.cos(angle) * radius, z = stand.z + Math.sin(angle) * radius;
        // Facing along the ring, as if walking round
        float yaw = (float) Math.toDegrees(angle) + 180f;
        mob.setPosition(x, stand.y, z);
        mob.setVelocity(Vec3d.ZERO);
        mob.setYaw(MathHelper.wrapDegrees(yaw));
        mob.setBodyYaw(mob.getYaw());
        mob.setHeadYaw(mob.getYaw());
    }

    /** Small: half its size, and never wider than {@link #MAX_WIDTH} (a big pawn too fits beside the others). */
    static void shrink(MobEntity mob) {
        EntityAttributeInstance scale = mob.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        if (scale == null || scale.getModifier(SMALL) != null) return;
        double own = mob.getWidth() / Math.max(1.0E-3, mob.getScale());
        double factor = Math.min(SMALL_SCALE, MAX_WIDTH / Math.max(1.0E-3, own));
        scale.addTemporaryModifier(new EntityAttributeModifier(SMALL, factor - 1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        mob.calculateDimensions();
    }

    /** Its own size again (no-op if it was not made smaller). */
    public static void restoreSize(MobEntity mob) {
        EntityAttributeInstance scale = mob.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        if (scale != null && scale.removeModifier(SMALL)) mob.calculateDimensions();
    }

    /** Whether the pawn is drawn smaller for sharing its space. */
    public static boolean isSmall(MobEntity mob) {
        EntityAttributeInstance scale = mob.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        return scale != null && scale.getModifier(SMALL) != null;
    }

    void writeNbt(NbtCompound nbt) {
        if (parked.isEmpty()) return;
        NbtCompound parkedNbt = new NbtCompound();
        parked.forEach((token, space) -> parkedNbt.putLong(token.toString(), space.asLong()));
        nbt.put("Parked", parkedNbt);
    }

    void readNbt(NbtCompound nbt) {
        parked.clear();
        NbtCompound parkedNbt = nbt.getCompound("Parked");
        for (String key : parkedNbt.getKeys()) {
            try {
                parked.put(UUID.fromString(key), BlockPos.fromLong(parkedNbt.getLong(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
