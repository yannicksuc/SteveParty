package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public record BoardSpaceBehaviorComponent(List<BlockPos> destinations, BlockPos origin, String tileType, String world) {
    public static BlockPos DEFAULT_ORIGIN = new BlockPos(999999999,999999999,999999999);
    public static final Codec<BoardSpaceBehaviorComponent> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Codec.list(BlockPos.CODEC).fieldOf("destinations").forGetter(BoardSpaceBehaviorComponent::destinations),
            BlockPos.CODEC.optionalFieldOf("origin", DEFAULT_ORIGIN).forGetter(BoardSpaceBehaviorComponent::origin),
            Codec.STRING.optionalFieldOf("tileType", "default").forGetter(BoardSpaceBehaviorComponent::tileType),
            Codec.STRING.optionalFieldOf("world", "").forGetter(BoardSpaceBehaviorComponent::world)
    ).apply(builder, BoardSpaceBehaviorComponent::new));

    public static final BoardSpaceBehaviorComponent DEFAULT_BOARD_SPACE_BEHAVIOR = new BoardSpaceBehaviorComponent(
            new ArrayList<>(), // Empty destinations list
            DEFAULT_ORIGIN,
            "default", // Default tile type
            "" // No initial world set
    );

    public boolean isOriginSet() {
        return BoardSpaceBehaviorComponent.DEFAULT_ORIGIN != origin();
    }
}