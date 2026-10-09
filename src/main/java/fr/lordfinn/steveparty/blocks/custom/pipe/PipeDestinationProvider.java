package fr.lordfinn.steveparty.blocks.custom.pipe;

import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Where a pipe warps to: asked when a traveller reaches a capped end (a warp) holding a cartridge. Without one (or
 * when it answers null) the traveller comes out of the nearest mouth of the same colour in another pipe network
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

    /**
     * As {@link #destination(ServerWorld, BlockPos, Entity)}, knowing the mouth the traveller went in by (the mini-game
     * pipes send by the colour of that mouth).
     *
     * @param enteredBy the mouth it went in by, null when not known
     */
    default @Nullable Exit destination(ServerWorld world, BlockPos cappedEnd, Entity traveller, PipeNetworks.@Nullable End enteredBy) {
        return destination(world, cappedEnd, traveller);
    }

    /**
     * A mouth: the pipe block and the side it opens on.
     *
     * @param dimension its dimension, null for the one of the capped end
     */
    record Exit(@Nullable RegistryKey<World> dimension, BlockPos pos, Direction opening) {
        public Exit(BlockPos pos, Direction opening) {
            this(null, pos, opening);
        }
    }

    Map<Item, Function<ItemStack, PipeDestinationProvider>> BY_ITEM = new HashMap<>();

    /** How a cartridge item sends travellers on, when it sits in a capped end. */
    static void register(Item cartridge, Function<ItemStack, PipeDestinationProvider> provider) {
        BY_ITEM.put(cartridge, provider);
    }

    static @Nullable PipeDestinationProvider of(ItemStack cartridge) {
        if (cartridge.isEmpty()) return null;
        Function<ItemStack, PipeDestinationProvider> provider = BY_ITEM.get(cartridge.getItem());
        return provider == null ? null : provider.apply(cartridge);
    }
}
