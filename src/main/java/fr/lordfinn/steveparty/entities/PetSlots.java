package fr.lordfinn.steveparty.entities;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Several pets of one owner following them never pile on one spot: each takes its own place among its mates (the
 * owner's pets of its kind following them now), in a fixed order (by UUID, so each keeps its place while the group
 * stays the same), and they keep a little apart besides ({@link #pushApart}). The pets' goals ask now and then
 * ({@link Group#refresh}): a box search around the pet.
 */
public final class PetSlots {
    private PetSlots() {
    }

    /** A pet's place among its mates, refreshed now and then. */
    public static final class Group {
        private final Entity pet;
        private final List<Entity> mates = new ArrayList<>();
        private int index, count = 1, refreshIn;

        public Group(Entity pet) {
            this.pet = pet;
        }

        /**
         * Looks for its mates once every {@code every} ticks: the entities of its own class within {@code range}
         * blocks that {@code follows} (itself among them).
         */
        public void refresh(double range, int every, Predicate<Entity> follows) {
            if (--refreshIn > 0) return;
            refreshIn = every;
            mates.clear();
            mates.addAll(pet.getWorld().getEntitiesByClass(pet.getClass(), pet.getBoundingBox().expand(range),
                    entity -> entity == pet || entity.isAlive() && follows.test(entity)));
            if (!mates.contains(pet)) mates.add(pet);
            mates.sort(Comparator.comparing(Entity::getUuid));
            index = mates.indexOf(pet);
            count = mates.size();
        }

        /** Its place among its mates (0 the first). */
        public int index() {
            return index;
        }

        /** How many they are (itself included). */
        public int count() {
            return count;
        }

        /**
         * {@code target} (a centre) pushed out of the {@code spacing} of its mates' centres (in the horizontal plane),
         * so they never go through one another; two exactly on one spot part along a way of their own.
         */
        public Vec3d pushApart(Vec3d target, double spacing) {
            double x = target.x, z = target.z;
            for (Entity mate : mates) {
                if (mate == pet || mate.isRemoved()) continue;
                Vec3d at = mate.getBoundingBox().getCenter();
                if (Math.abs(at.y - target.y) >= spacing) continue;
                double dx = x - at.x, dz = z - at.z, distance = Math.sqrt(dx * dx + dz * dz);
                if (distance >= spacing) continue;
                if (distance < 1.0E-3) {
                    double angle = index * 2.399963; // the golden angle: each its own way
                    dx = Math.cos(angle);
                    dz = Math.sin(angle);
                    distance = 1;
                }
                x += dx / distance * (spacing - distance) * 0.5;
                z += dz / distance * (spacing - distance) * 0.5;
            }
            return new Vec3d(x, target.y, z);
        }
    }
}
