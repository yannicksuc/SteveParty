package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

/**
 * The settings a Wrench holds (see {@code WrenchCopy}): copied from a block, pasted on others of its family.
 *
 * @param block    the block they were copied from (its name in the tooltip, the block-only settings)
 * @param family   which blocks they paste on ({@code BlockSettings#family})
 * @param settings what the block's {@code BlockSettings} wrote
 * @param label    what they are, as the player sees it (« Cartouche Boutique », « Podium »...)
 */
public record WrenchCopyComponent(Identifier block, String family, NbtCompound settings, Text label) {
    public static final Codec<WrenchCopyComponent> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Identifier.CODEC.fieldOf("block").forGetter(WrenchCopyComponent::block),
            Codec.STRING.fieldOf("family").forGetter(WrenchCopyComponent::family),
            NbtCompound.CODEC.fieldOf("settings").forGetter(WrenchCopyComponent::settings),
            TextCodecs.CODEC.fieldOf("label").forGetter(WrenchCopyComponent::label)
    ).apply(builder, WrenchCopyComponent::new));
}
