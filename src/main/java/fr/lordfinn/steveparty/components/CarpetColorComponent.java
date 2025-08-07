package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.DyeColor;

public record CarpetColorComponent(DyeColor color1, DyeColor color2) {

    public static final CarpetColorComponent DEFAULT = new CarpetColorComponent(DyeColor.WHITE, DyeColor.WHITE);

    public static final Codec<CarpetColorComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DyeColor.CODEC.fieldOf("color1").forGetter(CarpetColorComponent::color1),
            DyeColor.CODEC.fieldOf("color2").forGetter(CarpetColorComponent::color2)
    ).apply(instance, CarpetColorComponent::new));
}
