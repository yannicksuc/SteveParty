package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The settings of a Star Cartridge, edited in its menu (see
 * {@link fr.lordfinn.steveparty.items.custom.cartridges.StarCartridgeItem}).
 *
 * @param price    coins the star costs ({@value #DEFAULT_PRICE} by default)
 * @param onPass   a token passing over the space may buy the star (true, the default), or only one ending its move
 *                 there (false). A token never ends its move on a check point: a star check point always sells it to
 *                 the tokens passing
 * @param relocate the space is switched off (redstone, its cartridge taken out) while it holds the star: the star goes
 *                 to another star space (true, the default), or waits there until the space is switched on again
 */
public record StarSettingsComponent(int price, boolean onPass, boolean relocate) {
    public static final int DEFAULT_PRICE = 20, MAX_PRICE = 999;
    public static final StarSettingsComponent DEFAULT = new StarSettingsComponent(DEFAULT_PRICE, true, true);

    public static final Codec<StarSettingsComponent> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Codec.intRange(0, MAX_PRICE).optionalFieldOf("price", DEFAULT_PRICE).forGetter(StarSettingsComponent::price),
            Codec.BOOL.optionalFieldOf("on_pass", true).forGetter(StarSettingsComponent::onPass),
            Codec.BOOL.optionalFieldOf("relocate", true).forGetter(StarSettingsComponent::relocate)
    ).apply(builder, StarSettingsComponent::new));

    public StarSettingsComponent withPrice(int price) {
        return new StarSettingsComponent(Math.clamp(price, 0, MAX_PRICE), onPass, relocate);
    }

    public StarSettingsComponent withOnPass(boolean onPass) {
        return new StarSettingsComponent(price, onPass, relocate);
    }

    public StarSettingsComponent withRelocate(boolean relocate) {
        return new StarSettingsComponent(price, onPass, relocate);
    }
}
