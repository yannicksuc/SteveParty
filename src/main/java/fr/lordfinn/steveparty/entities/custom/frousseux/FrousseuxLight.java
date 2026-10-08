package fr.lordfinn.steveparty.entities.custom.frousseux;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LightBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The real light a Frousseux gives off: vanilla has no moving light, so it keeps one invisible {@code minecraft:light}
 * block where it floats, of its flame's level, and moves it with it.
 * <ul>
 *     <li>Only ever placed in air: never over a block, a liquid, or another light (two of them in one block: one
 *     light).</li>
 *     <li>Only ever removed if it is still a light block, back to air.</li>
 *     <li>Moved only when it enters another block or its flame changes: a light update now and then, not every tick.</li>
 *     <li>Gone when it dies, is discarded (despawn, /kill) or leaves the dimension. Unloaded with its chunk, its light
 *     stays saved in the world and the Frousseux remembers it (entity data "Light"): loaded again, it takes it back or
 *     clears it. No stray light is left behind.</li>
 * </ul>
 * Placed without neighbour or shape updates ({@link #FLAGS}): nothing around notices it (observers, redstone).
 */
final class FrousseuxLight {
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    private @Nullable BlockPos pos;
    private int level;

    /** Keeps its light at {@code at}, of {@code level} (0: none). */
    void update(World world, BlockPos at, int level) {
        if (pos != null && !world.getBlockState(pos).isOf(Blocks.LIGHT)) pos = null; // broken by someone meanwhile
        if (pos != null && pos.equals(at) && this.level == level) return;
        if (pos != null && pos.equals(at)) {
            if (level > 0) {
                world.setBlockState(pos, light(level), FLAGS);
                this.level = level;
            } else {
                clear(world);
            }
            return;
        }
        clear(world);
        if (level > 0 && world.getBlockState(at).isAir()) {
            world.setBlockState(at, light(level), FLAGS);
            pos = at.toImmutable();
            this.level = level;
        }
    }

    /** Takes its light away, if it is still there. */
    void clear(World world) {
        if (pos == null) return;
        if (world.isChunkLoaded(pos) && world.getBlockState(pos).isOf(Blocks.LIGHT)) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), FLAGS);
        }
        pos = null;
        level = 0;
    }

    /** Forgets its light without touching the world (an entity copied to another dimension: not its light there). */
    void forget() {
        pos = null;
        level = 0;
    }

    @Nullable BlockPos pos() {
        return pos;
    }

    private static BlockState light(int level) {
        return Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, level);
    }

    void write(NbtCompound nbt) {
        if (pos != null) {
            nbt.put("Light", NbtHelper.fromBlockPos(pos));
            nbt.putInt("LightLevel", level);
        }
    }

    void read(NbtCompound nbt) {
        pos = NbtHelper.toBlockPos(nbt, "Light").orElse(null);
        level = nbt.getInt("LightLevel");
    }
}
