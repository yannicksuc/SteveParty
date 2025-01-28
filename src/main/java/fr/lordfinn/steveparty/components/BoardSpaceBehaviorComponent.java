package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public record BoardSpaceBehaviorComponent(List<BlockPos> destinations, String world) {
    public static BlockPos DEFAULT_ORIGIN = new BlockPos(999999999,999999999,999999999);
    public static final Codec<BoardSpaceBehaviorComponent> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Codec.list(BlockPos.CODEC).fieldOf("destinations").forGetter(BoardSpaceBehaviorComponent::destinations),
            Codec.STRING.optionalFieldOf("world", "").forGetter(BoardSpaceBehaviorComponent::world)
    ).apply(builder, BoardSpaceBehaviorComponent::new));

    public static final BoardSpaceBehaviorComponent DEFAULT_BOARD_SPACE_BEHAVIOR = new BoardSpaceBehaviorComponent(
            new ArrayList<>(),
            ""
    );
}