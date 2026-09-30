package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TileStampComponent;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Util;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * What a tile item keeps of its tile: its cartridges, every slot with all their components (vanilla
 * {@code minecraft:container}), its own stamped look ({@code steveparty:tile-stamp}) and its size
 * ({@code steveparty:tile-size}). Taken by Silk Touch (the tile moves: its links go with it, as absolute positions) and
 * by the creative pick block with Ctrl or Shift (a copy: placed, it comes without its links, see
 * WrenchActions#dropCopiedLinks). Placing the item gives them back (BoardSpaceBlockEntity#readComponents).
 */
public final class TileContents {
    /** How long the preview of a tile holding several cartridges shows each one. */
    public static final long CYCLE_MS = 1000;
    /** At most 16 slots (the Advanced Tile). */
    private static final int MAX_SLOTS = 16;

    /** Client side, set by the client: whether the creative pick block copies the tile with its contents (Ctrl or Shift held). */
    public static BooleanSupplier pickWithContents = () -> false;

    private TileContents() {
    }

    /** A cartridge held by a tile item, and its slot in the tile. */
    public record Slot(int slot, ItemStack cartridge) {
    }

    /** The cartridges held by a tile item, in slot order (empty slots skipped). */
    public static List<Slot> cartridges(ItemStack tile) {
        ContainerComponent container = tile.get(DataComponentTypes.CONTAINER);
        if (container == null) return List.of();
        DefaultedList<ItemStack> slots = DefaultedList.ofSize(MAX_SLOTS, ItemStack.EMPTY);
        container.copyTo(slots);
        List<Slot> cartridges = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) if (!slots.get(i).isEmpty()) cartridges.add(new Slot(i, slots.get(i)));
        return cartridges;
    }

    /** A copy of the tile item {@code tile} (one) holding {@code cartridge} (one) in its first slot, as a tile taken with it. */
    public static ItemStack holding(ItemStack tile, ItemStack cartridge) {
        ItemStack result = tile.copyWithCount(1);
        result.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(cartridge.copyWithCount(1))));
        return result;
    }

    /** The one of {@code count} cartridges shown now: each in turn, {@link #CYCLE_MS} each. */
    public static int previewedIndex(int count) {
        return count <= 1 ? 0 : (int) ((Util.getMeasuringTimeMs() / CYCLE_MS) % count);
    }

    /** The look stamped on the tile itself (shown while it holds no cartridge). */
    public static @Nullable TileStampComponent ownStamp(ItemStack tile) {
        return tile.get(ModComponents.TILE_STAMP);
    }

    /** Whether a tile item carries anything of a placed tile (cartridges, a stamped look). */
    public static boolean holdsContents(ItemStack tile) {
        ContainerComponent container = tile.get(DataComponentTypes.CONTAINER);
        return (container != null && container.iterateNonEmpty().iterator().hasNext()) || tile.contains(ModComponents.TILE_STAMP);
    }

    /** Whether {@code tool} has Silk Touch. */
    public static boolean hasSilkTouch(WorldView world, ItemStack tool) {
        if (tool.isEmpty()) return false;
        return world.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT)
                .flatMap(registry -> registry.getOptional(Enchantments.SILK_TOUCH))
                .map(silkTouch -> EnchantmentHelper.getLevel(silkTouch, tool) > 0)
                .orElse(false);
    }

    /**
     * Makes {@code stack} a copy of the tile of {@code blockEntity}, as the vanilla creative pick block with Ctrl does:
     * its contents as components, and its block entity data (the mark of a copy).
     */
    public static ItemStack copyOf(ItemStack stack, BlockEntity blockEntity, WorldView world) {
        NbtCompound nbt = blockEntity.createComponentlessNbtWithIdentifyingData(world.getRegistryManager());
        blockEntity.removeFromCopiedStackNbt(nbt);
        BlockItem.setBlockEntityData(stack, blockEntity.getType(), nbt);
        stack.applyComponentsFrom(blockEntity.createComponentMap());
        return stack;
    }
}
