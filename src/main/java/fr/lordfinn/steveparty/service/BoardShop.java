package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyResources;
import fr.lordfinn.steveparty.blocks.custom.PartyController.journal.JournalPlace;
import fr.lordfinn.steveparty.blocks.custom.PartyController.journal.TurnJournal;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The shop of a Shop Cartridge's space, as its summoned merchant sells it (see {@link ShopStops}): the offers set in the
 * cartridge's menu ({@link ShopCartridgeItem#offers}), each sale taken from its stock, each payment put in its till.
 * <ul>
 *     <li>stock: the cartridge's linked containers, or without any the party running on its board
 *     ({@link CartridgeContainers#sourceFor}: its Party Controller's bank, or its infinite storage);</li>
 *     <li>till: the bank of the party running on its board (an infinite storage takes all); outside a party, the
 *     cartridge's containers. What does not fit is dropped at the merchant (never lost).</li>
 * </ul>
 * An offer whose item is not in the stock is sold out (the merchant disables it). Nothing is ever made from nothing.
 * The cartridge is read again at each use (it may be changed while the shop is open).
 */
public final class BoardShop {
    private final ServerWorld world;
    private final BlockPos space;

    public BoardShop(ServerWorld world, BlockPos space) {
        this.world = world;
        this.space = space.toImmutable();
    }

    public BlockPos space() {
        return space;
    }

    /** The Shop Cartridge of its space now, or an empty stack. */
    public ItemStack cartridge() {
        BoardSpaceBlockEntity entity = ABoardSpaceBlock.getBoardSpaceEntity(world, space);
        ItemStack cartridge = entity == null ? null : ShopStops.shopCartridge(entity);
        return cartridge == null ? ItemStack.EMPTY : cartridge;
    }

    /** Its offers now (new ones). */
    public List<TradingStallBlockEntity.ExactTradeOffer> offers() {
        return ShopCartridgeItem.offers(cartridge());
    }

    /** Where its sales are taken from now: its containers, or the party's source (CartridgeContainers.sourceFor). */
    public PartyResources stock() {
        ItemStack cartridge = cartridge();
        return cartridge.isEmpty() ? PartyResources.NONE : CartridgeContainers.sourceFor(cartridge, world, space);
    }

    /** How many of {@code item} (item and components) its stock holds now. */
    public int held(ItemStack item) {
        return stock().available(item);
    }

    /** Whether its stock holds {@code sold} (as many as that). */
    public boolean inStock(ItemStack sold) {
        return sold.isEmpty() || stock().canTake(sold, sold.getCount());
    }

    /** {@code sold} is sold to {@code buyer}: taken from its stock, in order (written in the party's turn journal). */
    public void take(ItemStack sold, @Nullable PlayerEntity buyer) {
        if (sold.isEmpty()) return;
        PartyResources stock = stock();
        int taken = stock.take(sold, sold.getCount());
        if (buyer != null) TurnJournal.moved(party(), JournalPlace.of(stock), JournalPlace.player(buyer.getUuid()), sold, taken);
    }

    /** The party running on its board, whose bank is its till; null outside a party. */
    public @Nullable PartyControllerEntity party() {
        return PartyControllerEntity.getPartyOfBoardSpace(world, space,
                PartyControllerEntity.BOARD_NEARBY_RADIUS, false).orElse(null);
    }

    /** Whether {@code sold} is in the stock it will sell from, party running or not (its tile info). */
    public boolean shownInStock(ItemStack sold) {
        ItemStack cartridge = cartridge();
        return sold.isEmpty() || !cartridge.isEmpty()
                && CartridgeContainers.shownSourceFor(cartridge, world, space).canTake(sold, sold.getCount());
    }

    /**
     * {@code payment} (emptied) of {@code buyer} goes to its till; what does not fit is dropped at {@code dropAt}. In a
     * party, written in its turn journal.
     */
    public void pay(ItemStack payment, Vec3d dropAt, @Nullable PlayerEntity buyer) {
        if (payment.isEmpty()) return;
        PartyControllerEntity party = party();
        ItemStack kind = payment.copyWithCount(1);
        JournalPlace from = buyer == null ? JournalPlace.GONE : JournalPlace.player(buyer.getUuid());
        if (party != null) {
            PartyResources till = PartyResources.of(party);
            int paid = till.give(payment);
            if (buyer != null) TurnJournal.moved(party, from, JournalPlace.of(till), kind, paid);
        } else {
            ItemStack cartridge = cartridge();
            if (!cartridge.isEmpty()) {
                List<Inventory> containers = CartridgeContainers.available(cartridge, world);
                if (CartridgeContainers.insertInOrder(payment, containers) > 0) containers.forEach(Inventory::markDirty);
            }
        }
        if (!payment.isEmpty()) {
            ItemEntity dropped = new ItemEntity(world, dropAt.x, dropAt.y, dropAt.z, payment.copyAndEmpty());
            dropped.setToDefaultPickupDelay();
            world.spawnEntity(dropped);
            if (buyer != null) TurnJournal.moved(party, from, JournalPlace.dropped(dropped), kind, dropped.getStack().getCount());
        }
    }
}
