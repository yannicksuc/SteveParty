package fr.lordfinn.steveparty.items.custom.cartridges.menu;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Where an edited cartridge is: in one of the player's hands ({@code pos} empty, {@code index} the hand), or in slot
 * {@code index} of the block entity at {@code pos} (a tile, a check point, or any other block holding cartridges in
 * an {@link Inventory}).
 */
public record CartridgeRef(Optional<BlockPos> pos, int index) {
    public static final PacketCodec<RegistryByteBuf, CartridgeRef> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.optional(BlockPos.PACKET_CODEC), CartridgeRef::pos,
            PacketCodecs.VAR_INT, CartridgeRef::index,
            CartridgeRef::new);

    public static CartridgeRef hand(Hand hand) {
        return new CartridgeRef(Optional.empty(), hand.ordinal());
    }

    public static CartridgeRef slot(BlockPos pos, int slot) {
        return new CartridgeRef(Optional.of(pos.toImmutable()), slot);
    }

    public boolean inHand() {
        return pos.isEmpty();
    }

    public @Nullable Hand hand() {
        if (pos.isPresent() || index < 0 || index >= Hand.values().length) return null;
        return Hand.values()[index];
    }

    /** The block holding it, or null (in a hand, or no inventory there). */
    public @Nullable BlockEntity holder(World world) {
        if (pos.isEmpty() || !world.isChunkLoaded(pos.get())) return null;
        BlockEntity blockEntity = world.getBlockEntity(pos.get());
        return blockEntity instanceof Inventory inventory && index >= 0 && index < inventory.size() ? blockEntity : null;
    }

    /** The cartridge as {@code player}'s side knows it (the live stack, not a copy), or empty. */
    public ItemStack resolve(PlayerEntity player) {
        if (pos.isEmpty()) {
            Hand hand = hand();
            return hand == null ? ItemStack.EMPTY : player.getStackInHand(hand);
        }
        return holder(player.getWorld()) instanceof Inventory inventory ? inventory.getStack(index) : ItemStack.EMPTY;
    }

    /**
     * May {@code player} change it? Not a spectator; on a block: allowed to change blocks there (not in adventure mode,
     * not in a protected area) and in reach, like the other board edits.
     */
    public boolean mayEdit(PlayerEntity player) {
        if (player.isSpectator()) return false;
        if (pos.isEmpty()) return hand() != null;
        BlockPos at = pos.get();
        return player.canModifyBlocks() && player.getWorld().canPlayerModifyAt(player, at)
                && ScreenHandlerChecks.isInReach(player, at) && holder(player.getWorld()) != null;
    }

    /** May {@code player} keep a menu on it open (the block still there and in reach)? */
    public boolean inReach(PlayerEntity player) {
        return pos.isEmpty() || (holder(player.getWorld()) != null && ScreenHandlerChecks.isInReach(player, pos.get()));
    }

    /** After a change of its cartridge: the block is saved and sent to the players around (server side). */
    public void commit(World world) {
        if (world.isClient) return;
        BlockEntity holder = holder(world);
        if (holder instanceof CartridgeContainerBlockEntity container) {
            BoardLinks.sync(container);
        } else if (holder != null) {
            holder.markDirty();
            BlockState state = holder.getCachedState();
            world.updateListeners(holder.getPos(), state, state, Block.NOTIFY_ALL);
        }
    }
}
