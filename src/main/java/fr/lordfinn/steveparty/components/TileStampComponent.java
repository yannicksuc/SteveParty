package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.steveparty.stencil.StencilPatterns;
import fr.lordfinn.steveparty.stencil.StencilShape;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * A look stamped on a tile with a stencil and a dye (or the Stencil Hammer): the whole face takes the dye's colour,
 * the stencil's pattern drawn on it in a darker shade of that colour, like the tile's own faces.
 * <p>
 * Kept by the tile when it holds no cartridge, else by the cartridge (it travels with it); also the tooltip data
 * that draws it on the cartridge's tooltip.
 */
public record TileStampComponent(List<Byte> shape, DyeColor color) implements TooltipData {
    public static final Codec<TileStampComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BYTE.listOf().fieldOf("shape").forGetter(TileStampComponent::shape),
            DyeColor.CODEC.fieldOf("color").forGetter(TileStampComponent::color)
    ).apply(instance, TileStampComponent::new));

    public TileStampComponent {
        shape = List.copyOf(shape);
    }

    public static TileStampComponent of(byte[] shape, DyeColor color) {
        return new TileStampComponent(StencilShape.toList(StencilShape.sanitize(shape)), color);
    }

    /** @return a copy of the pattern (blank if invalid). */
    public byte[] shapeArray() {
        return StencilShape.sanitize(StencilShape.fromList(shape));
    }

    public boolean sameAs(@Nullable byte[] otherShape, @Nullable DyeColor otherColor) {
        return color == otherColor && otherShape != null && Arrays.equals(shapeArray(), StencilShape.sanitize(otherShape));
    }

    /** The pattern's name (or « custom pattern ») and the colour's, for tooltips. */
    public Text describe() {
        StencilPatterns.Pattern pattern = StencilPatterns.byShape(shapeArray());
        Text symbol = pattern != null ? pattern.name() : Text.translatable("tooltip.steveparty.stencil.custom");
        return Text.translatable("tooltip.steveparty.stamp.look", symbol, Text.translatable("color.minecraft." + color.getName()));
    }
}
