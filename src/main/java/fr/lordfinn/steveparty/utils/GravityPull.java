package fr.lordfinn.steveparty.utils;

import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.ForgeCoreEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The pull of a gravity core, like a small planet: the entities around it are drawn to it, the way the dice float to
 * their owner ({@code AttractionSimulation}): heading for it faster the farther they are, slowing down as they get there
 * instead of shooting through it. Once there, they orbit it: they circle it at its height, a few blocks away. Used by
 * the core of the dice forge and by the gravity core block.
 * <p>
 * Heavy entities resist it: the more armour and toughness, the less an entity is pulled (full netherite, 20 + 12, not at
 * all), and the bigger its hitbox beyond a player's, the less too (its volume acting as its mass). The pull only changes an entity's velocity by a limited amount each tick (weaker far
 * away), so a strong enough force (elytra, knockback...) breaks free.
 * <p>
 * Players move themselves: their pull is computed on their own client (the local player), like gravity, with no packet
 * each tick; the server pulls every other entity.
 */
public final class GravityPull {
    /** Armour + toughness of an entity the pull no longer moves: full netherite. */
    public static final double IMMUNE_WEIGHT = 32;
    /** Hitbox volume up to which an entity is pulled fully: a player's (0.6 x 1.8 x 0.6); bigger ones less. */
    public static final double FULL_PULL_VOLUME = 0.6 * 1.8 * 0.6;
    /** Speed an entity heads for its orbit at: this share of its distance per tick, at most MAX_SPEED (blocks/tick). */
    private static final double ARRIVE_GAIN = 0.15, MAX_SPEED = 1.5;
    /** Speed along the orbit (blocks per tick), counterclockwise seen from above. */
    private static final double ORBIT_SPEED = 0.3;

    private GravityPull() {
    }

    /**
     * Pulls the entities within {@code range} of {@code core} whose movement is computed on this side.
     *
     * @param strength how much the pull may change the velocity each tick (blocks per tick²), without armour: the
     *                 entity is steered toward the core within that limit, so a strong enough force breaks free
     * @param falloff  true: the pull weakens with the distance, down to nothing at {@code range}
     * @param orbit    radius of the orbit the entities end up circling on (blocks)
     */
    public static void pullAround(World world, Vec3d core, double range, double strength, boolean falloff, double orbit) {
        if (range <= 0 || strength <= 0) return;
        Box area = new Box(core, core).expand(range);
        for (Entity entity : world.getOtherEntities(null, area, e -> isPulledHere(e, world.isClient))) {
            pull(entity, core, range, strength, falloff, orbit);
        }
    }

    private static boolean isPulledHere(Entity entity, boolean client) {
        if (entity.isSpectator() || entity.hasVehicle() || entity instanceof DisplayEntity || entity instanceof ForgeCoreEntity) {
            return false;
        }
        // Board tokens are moved by the board only
        if (entity instanceof TokenizedEntityInterface token && token.steveparty$isTokenized()) return false;
        if (entity instanceof PlayerEntity player) return client && player.isMainPlayer();
        return !client;
    }

    private static void pull(Entity entity, Vec3d core, double range, double strength, boolean falloff, double orbit) {
        Vec3d toCore = core.subtract(entity.getBoundingBox().getCenter());
        double distance = toCore.length();
        if (distance > range) return;
        double free = 1 - resistance(entity);
        if (free <= 0) return;
        double near = 1 - distance / range; // 1 at the core, 0 at the edge of its reach
        double k = strength * free * (falloff ? near : 1);
        Vec3d velocity = entity.getVelocity();
        // Steered toward the nearest point of its orbit (around the core, at its height), faster the farther it is,
        // slowing down on arrival (no overshoot), and sent along the orbit as it gets close to it
        double flat = Math.sqrt(toCore.x * toCore.x + toCore.z * toCore.z);
        Vec3d outward = flat < 1.0E-4 ? new Vec3d(1, 0, 0) : new Vec3d(-toCore.x / flat, 0, -toCore.z / flat);
        Vec3d toOrbit = core.add(outward.multiply(orbit)).subtract(entity.getBoundingBox().getCenter());
        double gap = toOrbit.length();
        Vec3d wanted = gap < 1.0E-4 ? Vec3d.ZERO : toOrbit.multiply(Math.min(MAX_SPEED, gap * ARRIVE_GAIN) / gap);
        double onOrbit = MathHelper.clamp(1 - gap / orbit, 0, 1);
        wanted = wanted.add(new Vec3d(-outward.z, 0, outward.x).multiply(ORBIT_SPEED * onOrbit));
        Vec3d accel = wanted.subtract(velocity);
        double change = accel.length();
        if (change > k) accel = accel.multiply(k / change);
        // It holds them up against the world's gravity, fully at the core
        if (!entity.hasNoGravity()) accel = accel.add(0, entity.getFinalGravity() * near * free, 0);
        entity.setVelocity(velocity.add(accel));
        // Floating in its field is not falling
        if (near * free > 0.5) entity.fallDistance = 0;
        if (!entity.getWorld().isClient) entity.velocityModified = true;
    }

    /**
     * @return how much the entity resists the pull: 0 (fully pulled) to 1 (not pulled), from its armour and its size
     */
    public static double resistance(Entity entity) {
        double armour = 0;
        if (entity instanceof LivingEntity living) {
            double weight = attribute(living, EntityAttributes.ARMOR) + attribute(living, EntityAttributes.ARMOR_TOUGHNESS);
            armour = MathHelper.clamp(weight / IMMUNE_WEIGHT, 0, 1);
        }
        Box box = entity.getBoundingBox();
        double volume = box.getLengthX() * box.getLengthY() * box.getLengthZ();
        double size = volume <= FULL_PULL_VOLUME ? 1 : FULL_PULL_VOLUME / volume;
        return 1 - (1 - armour) * size;
    }

    private static double attribute(LivingEntity living, RegistryEntry<EntityAttribute> attribute) {
        return living.getAttributes().hasAttribute(attribute) ? living.getAttributeValue(attribute) : 0;
    }
}
