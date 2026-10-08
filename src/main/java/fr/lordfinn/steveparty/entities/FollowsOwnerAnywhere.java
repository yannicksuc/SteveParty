package fr.lordfinn.steveparty.entities;

import net.minecraft.entity.Entity;
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
}
