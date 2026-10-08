package fr.lordfinn.steveparty.board;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * One kind of link a cartridge held by a block stores, as the Tile Linker Brush paints it: the board paths of a board
 * space or router, the blocks a Hop Switch switches, the containers of an Inventory Cartridge, the shop of a Shop
 * Cartridge... (see {@link BrushLinks} for who provides them). Painting from the holder to a target writes the target
 * on the cartridge exactly as a click on it with that cartridge would (same component, limits and feedback); painting
 * over it again removes it.
 * <p>
 * {@link #accepts}, {@link #targets} and {@link #linked} give the same answer on both sides (the brush aims and the
 * overlay draws with them); {@link #link} and {@link #unlink} are server side, inside a recorded action (undo).
 */
public interface BrushLinkable {
    /** The block holding the cartridge. */
    BlockPos holder();

    /** The colour its links are drawn in (RGB). */
    int color();

    /** Whether {@code target} can be added (as a click of the cartridge on it would take it). */
    boolean accepts(World world, BlockPos target);

    /** Its targets now, in the holder's world. */
    List<BlockPos> targets(World world);

    /** Whether {@code target} is one of its targets (painting over it removes it). */
    default boolean linked(World world, BlockPos target) {
        return targets(world).contains(target);
    }

    /** Adds {@code target}, the player told as by a click. @return whether it was added */
    boolean link(ServerPlayerEntity player, ServerWorld world, BlockPos target);

    /** Removes {@code target}, the player told as by a click. @return whether it was removed */
    boolean unlink(ServerPlayerEntity player, ServerWorld world, BlockPos target);

    /** Its links are already drawn by the board view (board paths, shops): the brush overlay leaves them out. */
    default boolean drawnByBoardView() {
        return false;
    }
}
