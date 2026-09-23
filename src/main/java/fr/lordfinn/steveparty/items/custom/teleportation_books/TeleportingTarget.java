package fr.lordfinn.steveparty.items.custom.teleportation_books;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;

/**
 * Teleportation condition of a "Here we come" book.
 * <p>
 * Instances stored in a data component must never be mutated: use {@link #copy()} or the {@code with*} methods
 * to derive a modified value. Setters are kept for screen-side editing of local copies.
 */
public class TeleportingTarget {
    public static final Codec<TeleportingTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Group.CODEC.fieldOf("group").forGetter(TeleportingTarget::getGroup),
            Codec.INT.fieldOf("fillCapacity").forGetter(TeleportingTarget::getFillCapacity),
            Codec.INT.fieldOf("fillPriorityWeight").forGetter(TeleportingTarget::getFillPriorityWeight)
    ).apply(instance, TeleportingTarget::new));

    private Group group = Group.EVERYONE;
    private int fillCapacity = 0; // 0 = no limit
    private int fillPriorityWeight = 0;

    public TeleportingTarget(Group group, int fillCapacity, int fillPriorityWeight) {
        this.setGroup(group);
        this.setFillCapacity(fillCapacity);
        this.setFillPriorityWeight(fillPriorityWeight);
    }

    public TeleportingTarget() {}

    /** @return an independent copy of this target. */
    public TeleportingTarget copy() {
        return new TeleportingTarget(group, fillCapacity, fillPriorityWeight);
    }

    public TeleportingTarget withGroup(Group group) {
        return new TeleportingTarget(group, fillCapacity, fillPriorityWeight);
    }

    public TeleportingTarget withFillCapacity(int fillCapacity) {
        return new TeleportingTarget(group, fillCapacity, fillPriorityWeight);
    }

    public TeleportingTarget withFillPriorityWeight(int fillPriorityWeight) {
        return new TeleportingTarget(group, fillCapacity, fillPriorityWeight);
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group == null ? Group.EVERYONE : group;
    }

    public int getFillCapacity() {
        return fillCapacity;
    }

    public int getCheckedFillCapacity() {
        return fillCapacity <= 0 ? Integer.MAX_VALUE : fillCapacity;
    }

    public void setFillCapacity(int fillCapacity) {
        this.fillCapacity = fillCapacity;
    }

    public int getFillPriorityWeight() {
        return fillPriorityWeight;
    }

    public void setFillPriorityWeight(int fillPriorityWeight) {
        this.fillPriorityWeight = fillPriorityWeight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeleportingTarget that)) return false;
        return fillCapacity == that.fillCapacity && fillPriorityWeight == that.fillPriorityWeight && group == that.group;
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, fillCapacity, fillPriorityWeight);
    }

    @Override
    public String toString() {
        return "TeleportingTarget{group=" + group + ", fillCapacity=" + fillCapacity + ", fillPriorityWeight=" + fillPriorityWeight + '}';
    }

    public enum Group {
        EVERYONE,
        PLAYERS,
        SPECTATORS,
        PLAYER_TEAM_A,
        PLAYER_TEAM_B;

        /** Same serialized form as before (enum name), but invalid names give a DataResult error instead of throwing. */
        public static final Codec<Group> CODEC = Codec.STRING.comapFlatMap(Group::parse, Group::name);

        private static DataResult<Group> parse(String name) {
            for (Group group : values()) {
                if (group.name().equals(name)) return DataResult.success(group);
            }
            return DataResult.error(() -> "Unknown teleporting target group: " + name);
        }
    }
}
