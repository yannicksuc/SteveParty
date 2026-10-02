package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.steveparty.minigame.PageZone;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Optional;

/**
 * What a Zone Cartridge carries: the box being drawn with it, in a dimension.
 *
 * @param dimension the dimension of the corner and of the box
 * @param corner    the first corner of a box being drawn (a second click on a block makes the box), empty otherwise
 * @param box       the box drawn, empty until its second corner is set; kept while a new one is being drawn
 */
public record ZoneSelection(RegistryKey<World> dimension, Optional<BlockPos> corner, Optional<BlockBox> box) {
    public static final Codec<ZoneSelection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            World.CODEC.fieldOf("dimension").forGetter(ZoneSelection::dimension),
            BlockPos.CODEC.optionalFieldOf("corner").forGetter(ZoneSelection::corner),
            BlockBox.CODEC.optionalFieldOf("box").forGetter(ZoneSelection::box)
    ).apply(instance, ZoneSelection::new));

    /** The zone drawn, empty while the cartridge has no box. */
    public Optional<PageZone> zone() {
        return box.map(drawn -> new PageZone(dimension, drawn));
    }
}
