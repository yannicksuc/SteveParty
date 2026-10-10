package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlock;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.ModEntities;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Spawn Marker of a cartridge whose space summons a mob ({@link MobSpawnCartridge}): one at most, saved on the
 * cartridge ({@link ModComponents#SPAWN_MARKER}), linked with the Tile Linker Brush. None, or one that is no Spawn
 * Marker any more: the mob appears beside its space, as always.
 */
public final class CartridgeSpawnMarker {
    private CartridgeSpawnMarker() {
    }

    /** Whether {@code stack} is a cartridge whose space summons a mob. */
    public static boolean spawnsMobs(@Nullable ItemStack stack) {
        return stack != null && stack.getItem() instanceof MobSpawnCartridge;
    }

    /** The kind of mob the space of {@code stack} summons (for the previews), null for none. */
    public static @Nullable EntityType<?> mobOf(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof FrousseuxCartridgeItem) return ModEntities.FROUSSEUX;
        if (item instanceof GlandouilleCartridgeItem) return ModEntities.GLANDOUILLE;
        if (item instanceof MistigriCartridgeItem) return ModEntities.MISTIGRI;
        if (item instanceof TrichaudronCartridgeItem) return ModEntities.TRICHAUDRON;
        if (item instanceof ShopCartridgeItem) return ModEntities.BOXED_TRADER_ENTITY;
        return null;
    }

    /** Whether the block at {@code pos} is a Spawn Marker. */
    public static boolean accepts(World world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof SpawnMarkerBlock;
    }

    /** Its marker's position in {@code world}'s dimension, linked (whatever stands there now), null for none. */
    public static @Nullable BlockPos linked(ItemStack stack, World world) {
        GlobalPos pos = stack.get(ModComponents.SPAWN_MARKER);
        return pos == null || !pos.dimension().equals(world.getRegistryKey()) ? null : pos.pos();
    }

    /** Its marker if it is one now (loaded, still a Spawn Marker), null otherwise: its mob appears beside the space then. */
    @SuppressWarnings("deprecation") // isChunkLoaded(BlockPos): an unloaded marker is not loaded for this
    public static @Nullable BlockPos marker(ItemStack stack, World world) {
        BlockPos pos = linked(stack, world);
        if (pos == null || !world.isChunkLoaded(pos) || !accepts(world, pos)) return null;
        return pos;
    }

    /** Links the marker at {@code pos} (the one before, if any, is forgotten); null: none. */
    public static void set(ItemStack stack, World world, @Nullable BlockPos pos) {
        if (pos == null) stack.remove(ModComponents.SPAWN_MARKER);
        else stack.set(ModComponents.SPAWN_MARKER, GlobalPos.create(world.getRegistryKey(), pos.toImmutable()));
    }

    /** The marker at {@code marker} is told the space at {@code tile} links it (breaking it unlinks it). */
    public static void own(World world, BlockPos marker, BlockPos tile) {
        if (world.getBlockEntity(marker) instanceof SpawnMarkerBlockEntity entity) entity.setOwner(tile);
    }

    /** The marker at {@code marker} is gone: the cartridges of the space at {@code tile} linked to it forget it. */
    public static void forget(World world, BlockPos tile, BlockPos marker) {
        CartridgeContainerBlockEntity container = BoardLinks.container(world, tile);
        if (container == null) return;
        boolean changed = false;
        for (int slot = 0; slot < container.size(); slot++) {
            ItemStack cartridge = container.getStack(slot);
            if (marker.equals(linked(cartridge, world))) {
                set(cartridge, world, null);
                changed = true;
            }
        }
        if (changed) BoardLinks.sync(container);
    }
}
