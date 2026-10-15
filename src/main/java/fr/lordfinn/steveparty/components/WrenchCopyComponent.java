package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

/**
 * The settings a Wrench holds (see {@code WrenchCopy}): copied from a block, pasted on others of its family.
 *
 * @param block    the block they were copied from (its name in the tooltip, the block-only settings)
 * @param family   which blocks they paste on ({@code BlockSettings#family})
 * @param settings what the block's {@code BlockSettings} wrote
 */
public record WrenchCopyComponent(Identifier block, String family, NbtCompound settings) {
    public static final Codec<WrenchCopyComponent> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Identifier.CODEC.fieldOf("block").forGetter(WrenchCopyComponent::block),
            Codec.STRING.fieldOf("family").forGetter(WrenchCopyComponent::family),
            NbtCompound.CODEC.fieldOf("settings").forGetter(WrenchCopyComponent::settings)
    ).apply(builder, WrenchCopyComponent::new));
}
