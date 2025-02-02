package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public record DestinationsComponent(List<BlockPos> destinations, String world) {
    public static BlockPos DEFAULT_ORIGIN = new BlockPos(999999999,999999999,999999999);
    public static final Codec<DestinationsComponent> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Codec.list(BlockPos.CODEC).fieldOf("destinations").forGetter(DestinationsComponent::destinations),
            Codec.STRING.optionalFieldOf("world", "").forGetter(DestinationsComponent::world)
    ).apply(builder, DestinationsComponent::new));

    public static final DestinationsComponent DEFAULT = new DestinationsComponent(
            new ArrayList<>(),
            ""
    );
}