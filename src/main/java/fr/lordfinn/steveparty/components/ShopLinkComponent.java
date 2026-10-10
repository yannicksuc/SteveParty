package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Legacy, never read: the merchant a Shop Cartridge was once linked to with the Tile Linker Brush (a Shop Cartridge's
 * space now summons its own merchant, see ShopStops). Kept so that old cartridges carrying it still load.
 */
public record ShopLinkComponent(UUID trader, BlockPos anchor) {
    public static final Codec<ShopLinkComponent> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Uuids.CODEC.fieldOf("trader").forGetter(ShopLinkComponent::trader),
            BlockPos.CODEC.fieldOf("anchor").forGetter(ShopLinkComponent::anchor)
    ).apply(builder, ShopLinkComponent::new));
}
