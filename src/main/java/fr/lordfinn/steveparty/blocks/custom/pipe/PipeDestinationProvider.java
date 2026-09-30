package fr.lordfinn.steveparty.blocks.custom.pipe;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Where a pipe warps to: asked when a traveller reaches a capped end (a warp) holding a cartridge. Without one (or
 * when it answers null) the traveller comes out of the nearest mouth of another pipe network
 * ({@link PipeNetworks#nearestMouth}).
 * <p>
 * The hook for the cartridges: a cartridge item registers how it picks the destination with {@link #register}; the
 * pipe's block entity keeps the cartridge in its slot {@link PipeBlockEntity#CARTRIDGE_SLOT}.
 */
@FunctionalInterface
public interface PipeDestinationProvider {
    /**
     * @param cappedEnd the capped end reached (its block entity holds the cartridge)
     * @return the mouth the traveller comes out of (it must be a pipe mouth), or null for the default
     */
    @Nullable Exit destination(ServerWorld world, BlockPos cappedEnd, Entity traveller);

    /** A mouth: the pipe block and the side it opens on. */
    record Exit(BlockPos pos, Direction opening) {}

    Map<net.minecraft.item.Item, Function<net.minecraft.item.ItemStack, PipeDestinationProvider>> BY_ITEM = new HashMap<>();

    /** How a cartridge item sends travellers on, when it sits in a capped end. */
    static void register(net.minecraft.item.Item cartridge, Function<net.minecraft.item.ItemStack, PipeDestinationProvider> provider) {
        BY_ITEM.put(cartridge, provider);
    }

    static @Nullable PipeDestinationProvider of(net.minecraft.item.ItemStack cartridge) {
        if (cartridge.isEmpty()) return null;
        Function<net.minecraft.item.ItemStack, PipeDestinationProvider> provider = BY_ITEM.get(cartridge.getItem());
        return provider == null ? null : provider.apply(cartridge);
    }
}
