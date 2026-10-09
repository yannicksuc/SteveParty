package fr.lordfinn.steveparty.minigame;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * The zone of a mini-game: a box of blocks in a dimension, kept by its page ({@link MiniGamePageData#zone()}) and
 * drawn with the page in hand ({@link PageZoneTool}).
 *
 * @param dimension the dimension the box is in
 * @param box       the blocks of the zone, both corners included
 */
public record PageZone(RegistryKey<World> dimension, BlockBox box) {
    /** The most blocks a side of a zone may have. */
    public static final int MAX_SIDE = 128;

    public static final Codec<PageZone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            World.CODEC.fieldOf("dimension").forGetter(PageZone::dimension),
            BlockBox.CODEC.fieldOf("box").forGetter(PageZone::box)
    ).apply(instance, PageZone::new));

    public int sizeX() {
        return box.getBlockCountX();
    }

    public int sizeY() {
        return box.getBlockCountY();
    }

    public int sizeZ() {
        return box.getBlockCountZ();
    }

    /** @return true when a side is longer than {@value #MAX_SIDE} blocks: such a zone is not given to a mini-game. */
    public boolean tooBig() {
        return tooBig(box);
    }

    public static boolean tooBig(BlockBox box) {
        return box.getBlockCountX() > MAX_SIDE || box.getBlockCountY() > MAX_SIDE || box.getBlockCountZ() > MAX_SIDE;
    }

    public void write(PacketByteBuf buf) {
        buf.writeRegistryKey(dimension);
        buf.writeInt(box.getMinX());
        buf.writeInt(box.getMinY());
        buf.writeInt(box.getMinZ());
        buf.writeInt(box.getMaxX());
        buf.writeInt(box.getMaxY());
        buf.writeInt(box.getMaxZ());
    }

    public static PageZone read(PacketByteBuf buf) {
        RegistryKey<World> dimension = buf.readRegistryKey(RegistryKeys.WORLD);
        int minX = buf.readInt(), minY = buf.readInt(), minZ = buf.readInt();
        return new PageZone(dimension, BlockBox.create(new BlockPos(minX, minY, minZ),
                new BlockPos(buf.readInt(), buf.readInt(), buf.readInt())));
    }

    /** The box in world coordinates: from the low corner of its first block to the high corner of its last. */
    public static Box bounds(BlockBox box) {
        return new Box(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX() + 1, box.getMaxY() + 1, box.getMaxZ() + 1);
    }
}
