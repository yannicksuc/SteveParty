package fr.lordfinn.steveparty.items.custom.teleportation_books;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class TeleportingTarget {
    public static final Codec<TeleportingTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Group.CODEC.fieldOf("group").forGetter(t -> t.group),
            Codec.INT.fieldOf("fillCapacity").forGetter(t -> t.fillCapacity),
            Codec.INT.fieldOf("fillPriorityWeight").forGetter(t -> t.fillPriorityWeight)
    ).apply(instance, TeleportingTarget::new));

    public Group group = Group.EVERYONE;
    public int fillCapacity = 0; // 0 = no limit
    public int fillPriorityWeight = 0;

    public TeleportingTarget(Group group, int fillCapacity, int fillPriorityWeight) {
        this.group = group;
        this.fillCapacity = fillCapacity;
        this.fillPriorityWeight = fillPriorityWeight;
    }

    public TeleportingTarget() {}

    public enum Group {
        EVERYONE,
        SPECTATOR,
        PLAYER_A,
        PLAYER_B,
        PLAYER_C,
        PLAYER_D,
        PLAYER_E,
        PLAYER_F;

        public static final Codec<Group> CODEC = Codec.STRING.xmap(Group::valueOf, Group::name);
    }
}
