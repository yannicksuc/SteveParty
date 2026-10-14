package fr.lordfinn.steveparty.blocks.custom.PartyController;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;

/**
 * The pipe an End party card is linked to: its mouth (with its dimension) and the side it opens on. The players come
 * out of it once the party is over (see PartyEnding).
 */
public record EndPipe(GlobalPos mouth, Direction opening) {
    public static final Codec<EndPipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GlobalPos.CODEC.fieldOf("mouth").forGetter(EndPipe::mouth),
            Direction.CODEC.fieldOf("opening").forGetter(EndPipe::opening)
    ).apply(instance, EndPipe::new));
}
