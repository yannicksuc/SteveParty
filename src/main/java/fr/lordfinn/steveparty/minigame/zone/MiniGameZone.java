package fr.lordfinn.steveparty.minigame.zone;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The place a mini-game is played in: a box of blocks (both corners included) in a dimension. What is "in the zone"
 * is always decided by a block position: an entity is in the zone when the block its feet are in is.
 */
public record MiniGameZone(RegistryKey<World> dimension, BlockBox box) {

    public static MiniGameZone of(RegistryKey<World> dimension, BlockPos corner, BlockPos opposite) {
        return new MiniGameZone(dimension, BlockBox.create(corner, opposite));
    }

    public boolean contains(int x, int y, int z) {
        return x >= box.getMinX() && x <= box.getMaxX() && y >= box.getMinY() && y <= box.getMaxY()
                && z >= box.getMinZ() && z <= box.getMaxZ();
    }

    public boolean contains(BlockPos pos) {
        return contains(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean isIn(World world) {
        return world.getRegistryKey().equals(dimension);
    }

    public int sizeX() {
        return box.getBlockCountX();
    }

    public int sizeY() {
        return box.getBlockCountY();
    }

    public int sizeZ() {
        return box.getBlockCountZ();
    }

    /** The zone as a box of the world (the far faces of its last blocks included). */
    public Box bounds() {
        return new Box(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX() + 1, box.getMaxY() + 1, box.getMaxZ() + 1);
    }

    public boolean intersects(MiniGameZone other) {
        return dimension.equals(other.dimension) && box.intersects(other.box);
    }

    public int minChunkX() {
        return ChunkSectionPos.getSectionCoord(box.getMinX());
    }

    public int maxChunkX() {
        return ChunkSectionPos.getSectionCoord(box.getMaxX());
    }

    public int minChunkZ() {
        return ChunkSectionPos.getSectionCoord(box.getMinZ());
    }

    public int maxChunkZ() {
        return ChunkSectionPos.getSectionCoord(box.getMaxZ());
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Dimension", dimension.getValue().toString());
        nbt.putIntArray("Box", new int[]{box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ()});
        return nbt;
    }

    public static @Nullable MiniGameZone fromNbt(NbtCompound nbt) {
        Identifier id = Identifier.tryParse(nbt.getString("Dimension"));
        int[] box = nbt.getIntArray("Box");
        if (id == null || box.length != 6) return null;
        return new MiniGameZone(RegistryKey.of(RegistryKeys.WORLD, id), new BlockBox(box[0], box[1], box[2], box[3], box[4], box[5]));
    }
}
