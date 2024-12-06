package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;
import java.util.List;

public record TileBehaviorComponent(List<BlockPos> destinations, String tileType, String world) {
    public static final Codec<TileBehaviorComponent> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Codec.list(BlockPos.CODEC).fieldOf("destinations").forGetter(TileBehaviorComponent::destinations),
            Codec.STRING.optionalFieldOf("tileType", "default").forGetter(TileBehaviorComponent::tileType),
            Codec.STRING.optionalFieldOf("world", "").forGetter(TileBehaviorComponent::world)
    ).apply(builder, TileBehaviorComponent::new));

    public static final TileBehaviorComponent DEFAULT_TILE_BEHAVIOR = new TileBehaviorComponent(
            List.of(), // Empty destinations list
            "default", // Default tile type
            "" // No initial world set
    );
}