package fr.lordfinn.steveparty.blocks.custom.PartyController;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;

/**
 * A pipe mouth the players of a party come out of (with its dimension) and the side it opens on: the pipe of an End
 * party card, once the party is over (see PartyEnding); the pipes of an event, for its time out (see EventTimeOut).
 */
public record EndPipe(GlobalPos mouth, Direction opening) {
    public static final Codec<EndPipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GlobalPos.CODEC.fieldOf("mouth").forGetter(EndPipe::mouth),
            Direction.CODEC.fieldOf("opening").forGetter(EndPipe::opening)
    ).apply(instance, EndPipe::new));
}
