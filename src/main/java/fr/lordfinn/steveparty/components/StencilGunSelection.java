package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Which loaded stencil and which loaded dye a stencil gun sprays (slot indexes in its own inventory).
 * {@code dye == }{@link #ENGRAVE} sprays no paint: the stencil is only engraved.
 */
public record StencilGunSelection(int stencil, int dye) {
    public static final int ENGRAVE = -1;
    public static final StencilGunSelection DEFAULT = new StencilGunSelection(0, 0);
    public static final Codec<StencilGunSelection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("stencil", 0).forGetter(StencilGunSelection::stencil),
            Codec.INT.optionalFieldOf("dye", 0).forGetter(StencilGunSelection::dye)
    ).apply(instance, StencilGunSelection::new));
}
