package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record MobEntityComponent(String entityUUID) {
    public static final Codec<MobEntityComponent> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Codec.STRING.optionalFieldOf("entityUUID", "").forGetter(MobEntityComponent::entityUUID)
    ).apply(builder, MobEntityComponent::new));

    /** @return the parsed UUID, or null if missing/invalid (the default value is ""). */
    @Nullable
    public UUID getUuid() {
        if (entityUUID == null || entityUUID.isEmpty()) return null;
        try {
            return UUID.fromString(entityUUID);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
