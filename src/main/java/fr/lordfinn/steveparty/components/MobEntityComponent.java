package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record MobEntityComponent(String entityUUID) {
    public static final Codec<MobEntityComponent> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Codec.STRING.optionalFieldOf("entityUUID", "").forGetter(MobEntityComponent::entityUUID)
    ).apply(builder, MobEntityComponent::new));
}