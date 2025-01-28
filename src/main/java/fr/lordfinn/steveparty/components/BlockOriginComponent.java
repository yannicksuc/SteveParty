package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;

public record BlockOriginComponent(BlockPos origin, String world) {
    public static final BlockPos DEFAULT_ORIGIN = new BlockPos(999999999, 999999999, 999999999);
    public static final Codec<BlockOriginComponent> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            BlockPos.CODEC.fieldOf("origin").forGetter(BlockOriginComponent::origin),
            Codec.STRING.optionalFieldOf("world", "").forGetter(BlockOriginComponent::world)
    ).apply(builder, BlockOriginComponent::new));

    public static final BlockOriginComponent DEFAULT_ORIGIN_COMPONENT = new BlockOriginComponent(
            DEFAULT_ORIGIN,
            ""
    );
}

