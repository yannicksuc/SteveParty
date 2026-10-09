package fr.lordfinn.steveparty.entities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A tamed mob of ours that goes wherever its owner goes, unlike a vanilla wolf: through any teleport (commands,
 * ender pearls, chorus fruit...), any distance, and into other dimensions ({@link PetTeleports}). Implemented by an
 * {@link Entity}; it only says whose it is, whether it goes along right now, and where it lands.
 */
public interface FollowsOwnerAnywhere {
    /** Its owner, or null (wild). */
    @Nullable UUID followedOwner();

    /**
     * Whether it goes along with {@code owner} right now: following them (not sitting or staying, not leashed or
     * riding, not busy with something else such as a board role). Asked on the server just before it is moved.
     */
    boolean goesWithOwner(ServerPlayerEntity owner);

    /** Where it lands by its owner (its feet), in the owner's world: by default a free spot next to them. */
    default Vec3d arrivalSpot(ServerPlayerEntity owner) {
        return PetTeleports.spotNear((Entity) this, owner);
    }

    /**
     * The part of {@link #goesWithOwner} every pet shares: alive, {@code owner}'s, neither on a lead nor riding (its
     * own sitting and busy states are up to it).
     */
    default boolean followsFreely(ServerPlayerEntity owner) {
        MobEntity self = (MobEntity) this;
        return self.isAlive() && owner.getUuid().equals(followedOwner()) && !self.isLeashed() && !self.hasVehicle();
    }

    /**
     * From its server tick: once a second, a tamed one ({@code tamed}) tells {@link PetTeleports} where it is (its
     * greatest health may change too: effects, attributes).
     */
    default void tickFollow(boolean tamed) {
        Entity self = (Entity) this;
        if (self.age % 20 == 3 && tamed) PetTeleports.remember(self);
    }

    /**
     * Vanilla's catch-up with its owner ({@code tryTeleportToOwner}) for a pet far behind in the same world: it is
     * recreated by them ({@link PetTeleports#bring}) rather than moved in place, which could leave it unseen by the
     * clients. True when handled here (vanilla's catch-up must not run then).
     */
    default boolean catchUpFar(@Nullable Entity owner) {
        Entity self = (Entity) this;
        if (!(owner instanceof ServerPlayerEntity player) || player.getWorld() != self.getWorld()
                || self.squaredDistanceTo(player) <= PetTeleports.NEAR * PetTeleports.NEAR) return false;
        if (goesWithOwner(player)) PetTeleports.bring(self, player.getServerWorld(), arrivalSpot(player), self.getYaw());
        return true;
    }
}
