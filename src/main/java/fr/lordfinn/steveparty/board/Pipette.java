package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileContents;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.screen_handlers.custom.BoardSpaceScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.CartridgeContainerScreenHandler;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The pipette of the board spaces' and routers' screens: it copies the destinations (links) of one cartridge and pastes
 * them on others, in place of theirs. Only the destinations: never the role, the colour, the settings or the kind of
 * the cartridge, so any cartridge goes with any other. The first cartridge clicked is the source, every next one a
 * target. A cartridge is one in a slot of the space, one in the inventory, or a Tile item (its cartridge); an Advanced
 * Tile item is not one (open the placed tile and click its cartridges).
 * <p>
 * Server side: {@link #apply}, from the client's {@code PipettePayload}. Nothing is created or used up.
 */
public final class Pipette {
    private Pipette() {
    }

    /** The cartridge {@code stack} stands for: itself, a Tile item's; empty if none. */
    public static ItemStack cartridgeOf(ItemStack stack) {
        if (stack.getItem() instanceof CartridgeItem) return stack;
        if (stack.isOf(ModBlocks.TILE.asItem())) {
            List<TileContents.Slot> held = TileContents.cartridges(stack);
            return held.isEmpty() ? ItemStack.EMPTY : held.getFirst().cartridge();
        }
        return ItemStack.EMPTY;
    }

    /** The destinations the pipette copies from {@code stack}; null if it is no cartridge. */
    public static @Nullable List<BlockPos> copyOf(ItemStack stack) {
        ItemStack cartridge = cartridgeOf(stack);
        return cartridge.isEmpty() ? null : List.copyOf(BoardLinks.links(cartridge));
    }

    /** A copy of {@code target} with {@code links} pasted on its cartridge; null if it is no cartridge. */
    public static @Nullable ItemStack pasted(ItemStack target, List<BlockPos> links, World world) {
        if (target.getItem() instanceof CartridgeItem) {
            ItemStack result = target.copy();
            BoardLinks.setLinks(result, links, world);
            return result;
        }
        ItemStack cartridge = cartridgeOf(target);
        if (cartridge.isEmpty()) return null;
        ItemStack edited = cartridge.copy();
        BoardLinks.setLinks(edited, links, world);
        ItemStack result = target.copy();
        result.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(edited)));
        return result;
    }

    /**
     * Pastes on slot {@code target} of the player's open cartridge screen (a board space's or a router's) the
     * destinations of the cartridge in slot {@code source} (a slot of the space or one of the player's, never a ghost
     * slot).
     *
     * @return whether they were pasted
     */
    public static boolean apply(ServerPlayerEntity player, int syncId, int source, int target) {
        if (!(player.currentScreenHandler instanceof CartridgeContainerScreenHandler handler) || handler.syncId != syncId
                || !handler.canUse(player) || source == target || !isItemSlot(handler, source) || !isItemSlot(handler, target)) return false;
        List<BlockPos> links = copyOf(handler.slots.get(source).getStack());
        Slot slot = handler.slots.get(target);
        if (links == null) {
            player.sendMessage(Text.translatable("message.steveparty.pipette.no_source"), true);
            return false;
        }
        ItemStack result = pasted(slot.getStack(), links, player.getWorld());
        if (result == null) {
            player.sendMessage(Text.translatable("message.steveparty.pipette.no_target"), true);
            return false;
        }
        slot.setStack(result);
        slot.markDirty();
        if (slot.inventory instanceof CartridgeContainerBlockEntity container) BoardLinks.sync(container);
        handler.sendContentUpdates();
        player.sendMessage(Text.translatable("message.steveparty.pipette.pasted", cartridgeOf(result).getName(), links.size()), true);
        player.playSoundToPlayer(ModSounds.SELECT_SOUND_EVENT, SoundCategory.PLAYERS, 0.35f, 1.7f);
        return true;
    }

    /** Whether slot {@code index} of {@code handler} holds a real item (not a ghost slot). */
    public static boolean isItemSlot(CartridgeContainerScreenHandler handler, int index) {
        if (handler instanceof BoardSpaceScreenHandler boardSpace) return boardSpace.isItemSlot(index);
        return index >= 0 && index < handler.slots.size();
    }
}
