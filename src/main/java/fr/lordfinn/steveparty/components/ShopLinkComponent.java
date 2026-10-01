package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * The shop chosen for a Shop Cartridge with the Wrench (without it, the nearest merchant is the shop).
 *
 * @param trader the Boxed Trader selling there
 * @param anchor what was clicked to choose it (the trader's position then, a trading stall, a cash register): where
 *               the board view draws the link
 */
public record ShopLinkComponent(UUID trader, BlockPos anchor) {
    public static final Codec<ShopLinkComponent> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Uuids.CODEC.fieldOf("trader").forGetter(ShopLinkComponent::trader),
            BlockPos.CODEC.fieldOf("anchor").forGetter(ShopLinkComponent::anchor)
    ).apply(builder, ShopLinkComponent::new));
}
