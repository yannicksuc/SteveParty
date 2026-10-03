package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.GlobalPos;

import java.util.Optional;

/**
 * A Mini-game Page in zone mode: right-clicks on blocks draw the zone of its page (see {@code PageZoneTool}).
 *
 * @param corner the first corner of a box being drawn (a second click on a block makes the zone), empty otherwise
 */
public record PageZoneMode(Optional<GlobalPos> corner) {
    public static final PageZoneMode START = new PageZoneMode(Optional.empty());

    public static final Codec<PageZoneMode> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GlobalPos.CODEC.optionalFieldOf("corner").forGetter(PageZoneMode::corner)
    ).apply(instance, PageZoneMode::new));
}
