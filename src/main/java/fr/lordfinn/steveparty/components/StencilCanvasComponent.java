package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.steveparty.stencil.StencilShape;
import net.minecraft.util.DyeColor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * What was painted on a stencil sign, kept on its item when the sign is broken so that placing it again gives
 * the same sign. {@code color} empty means engraved (not painted).
 */
public record StencilCanvasComponent(List<Byte> shape, Optional<DyeColor> color, boolean glowing, int fade) {
    public static final Codec<StencilCanvasComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BYTE.listOf().fieldOf("shape").forGetter(StencilCanvasComponent::shape),
            DyeColor.CODEC.optionalFieldOf("color").forGetter(StencilCanvasComponent::color),
            Codec.BOOL.optionalFieldOf("glowing", false).forGetter(StencilCanvasComponent::glowing),
            Codec.INT.optionalFieldOf("fade", 0).forGetter(StencilCanvasComponent::fade)
    ).apply(instance, StencilCanvasComponent::new));

    public StencilCanvasComponent {
        shape = List.copyOf(shape);
    }

    public static StencilCanvasComponent of(byte[] shape, @Nullable DyeColor color, boolean glowing, int fade) {
        return new StencilCanvasComponent(StencilShape.toList(shape), Optional.ofNullable(color), glowing, fade);
    }

    /** @return the shape, or null if it is not a valid 16x16 stencil. */
    public @Nullable byte[] shapeArray() {
        byte[] array = StencilShape.fromList(shape);
        return StencilShape.isValid(array) ? StencilShape.sanitize(array) : null;
    }
}
