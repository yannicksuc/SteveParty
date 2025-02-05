package fr.lordfinn.steveparty.items.custom.teleportation_books;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

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

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
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

    public enum Group {
        EVERYONE,
        PLAYERS,
        SPECTATORS,
        PLAYER_TEAM_A,
        PLAYER_TEAM_B;

        public static final Codec<Group> CODEC = Codec.STRING.xmap(Group::valueOf, Group::name);
    }
}
