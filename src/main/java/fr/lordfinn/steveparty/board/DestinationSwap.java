package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileContents;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.recipes.TileCartridgeRecipe;
import fr.lordfinn.steveparty.screen_handlers.custom.CartridgeCustomSlot;
import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.utils.ServerMemory;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ClickType;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A cartridge on the cursor left-clicked on another cartridge (or on a tile item) in any inventory screen: the items
 * swap places as usual, and their destinations (links) swap too, so that the destinations stay with the place. In a
 * tile's screen, a Shop Cartridge put over the plain cartridge in place goes in with the plain one's links, and the
 * plain one comes back to the cursor with the Shop Cartridge's (often none).
 * <ul>
 *     <li>On a Tile item: its cartridge is replaced by the cursor's, which takes the Tile's links; the old one comes to
 *     the cursor with the cursor's former links (an empty Tile just takes the cartridge, without links).</li>
 *     <li>On an Advanced Tile item: the cartridge goes in its first free slot (0, 15, 14... 1), without links (the
 *     slot had none); a full one: the usual click.</li>
 *     <li>Only one item on each side: with a stack on the cursor or in the slot, the usual click (half a stack
 *     cannot carry the other half's links).</li>
 * </ul>
 * A choice of each player (see {@link Preference}): the first time, the client asks (nothing happens), then the
 * click follows the answer, also kept in the client options and sent to the server.
 */
public final class DestinationSwap {
    /** What a player wants of the gesture. */
    public enum Preference {
        /** Never asked: the first gesture asks (client), nothing happens. */
        UNSET,
        /** Destinations swapped with the items. */
        ON,
        /** The usual click. */
        OFF;

        public static Preference of(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : OFF;
        }
    }

    /** The choice of each player, sent by their client (unknown: the usual click). Server side. */
    private static final Map<UUID, Preference> PREFERENCES = ServerMemory.forgetOnStop(new HashMap<>());

    /** Client side: the player's choice (set by the client). */
    public static Supplier<Preference> clientPreference = () -> Preference.OFF;
    /** Client side: asks the player what the gesture should do (set by the client). */
    public static Runnable askClient = () -> {
    };

    private DestinationSwap() {
    }

    public static void setPreference(PlayerEntity player, Preference preference) {
        PREFERENCES.put(player.getUuid(), preference);
    }

    public static Preference preference(PlayerEntity player) {
        if (player.getWorld().isClient) return clientPreference.get();
        return PREFERENCES.getOrDefault(player.getUuid(), Preference.OFF);
    }

    /**
     * The cartridge {@code cursor} (on the cursor) left-clicked on {@code slot} (see Item#onStackClicked, called on
     * both sides with the same answer).
     *
     * @return true if handled here (the usual click does not happen)
     */
    public static boolean onCartridgeClicked(ItemStack cursor, Slot slot, ClickType click, PlayerEntity player) {
        if (click != ClickType.LEFT || !(cursor.getItem() instanceof CartridgeItem)) return false;
        ItemStack target = slot.getStack();
        boolean cartridge = target.getItem() instanceof CartridgeItem;
        boolean tile = target.isOf(ModBlocks.TILE.asItem());
        boolean advanced = target.isOf(ModBlocks.ADVANCED_TILE.asItem());
        if (!cartridge && !tile && !advanced) return false;
        if (cursor.getCount() != 1 || target.getCount() != 1 || !slot.canTakeItems(player)) return false;
        if (cartridge && !accepts(slot, cursor)) return false;
        if ((tile || advanced) && !slot.canInsert(target)) return false;
        if (advanced && TileCartridgeRecipe.firstFreeSlot(target) < 0) return false;
        if (cartridge && ItemStack.areItemsAndComponentsEqual(cursor, target)) return false;
        switch (preference(player)) {
            case OFF -> {
                return false;
            }
            case UNSET -> {
                // Asked first: nothing happens until the player chose
                if (player.getWorld().isClient) askClient.run();
                return true;
            }
            default -> {
            }
        }
        if (player.getWorld().isClient) return true; // the server's result comes back to the screen
        Text message;
        if (cartridge) {
            List<BlockPos> slotLinks = BoardLinks.links(target);
            ItemStack toSlot = cursor.copy();
            BoardLinks.setLinks(toSlot, slotLinks, player.getWorld());
            ItemStack toCursor = target.copy();
            BoardLinks.setLinks(toCursor, BoardLinks.links(cursor), player.getWorld());
            slot.setStack(toSlot);
            player.currentScreenHandler.setCursorStack(toCursor);
            message = Text.translatable("message.steveparty.destination_swap.cartridge", toSlot.getName(), slotLinks.size());
        } else if (tile) {
            List<TileContents.Slot> held = TileContents.cartridges(target);
            ItemStack old = held.isEmpty() ? ItemStack.EMPTY : held.getFirst().cartridge().copy();
            List<BlockPos> tileLinks = BoardLinks.links(old);
            ItemStack inTile = cursor.copy();
            BoardLinks.setLinks(inTile, tileLinks, player.getWorld());
            if (!old.isEmpty()) BoardLinks.setLinks(old, BoardLinks.links(cursor), player.getWorld());
            ItemStack newTile = target.copy();
            newTile.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(inTile)));
            slot.setStack(newTile);
            player.currentScreenHandler.setCursorStack(old);
            message = Text.translatable("message.steveparty.destination_swap.tile", inTile.getName(), tileLinks.size());
        } else {
            ItemStack inTile = cursor.copy();
            inTile.remove(ModComponents.DESTINATIONS_COMPONENT);
            int free = TileCartridgeRecipe.firstFreeSlot(target);
            DefaultedList<ItemStack> slots = DefaultedList.ofSize(16, ItemStack.EMPTY);
            ContainerComponent container = target.get(DataComponentTypes.CONTAINER);
            if (container != null) container.copyTo(slots);
            slots.set(free, inTile);
            ItemStack newTile = target.copy();
            newTile.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(slots));
            slot.setStack(newTile);
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            message = Text.translatable("message.steveparty.destination_swap.advanced", inTile.getName(), free);
        }
        slot.markDirty();
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(message, true);
            serverPlayer.playSoundToPlayer(ModSounds.SELECT_SOUND_EVENT, SoundCategory.PLAYERS, 0.35f, 1.5f);
        }
        return true;
    }

    /** Whether {@code slot} takes {@code stack} in place of its own (a tile's slot refuses anything while it is full). */
    private static boolean accepts(Slot slot, ItemStack stack) {
        if (slot instanceof CartridgeCustomSlot cartridgeSlot) return cartridgeSlot.accepts(stack);
        return slot.canInsert(stack);
    }
}
