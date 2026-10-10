package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyResources;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The shop of a Shop Cartridge's space, as its summoned merchant sells it (see {@link ShopStops}): the offers set in the
 * cartridge's menu ({@link ShopCartridgeItem#offers}), each sale taken from its stock, each payment put in its till.
 * <ul>
 *     <li>stock: the cartridge's linked containers, or without any the bank of the party running on its board
 *     ({@link CartridgeContainers#sourceFor}: the party's source, its Party Controller's bank);</li>
 *     <li>till: the bank of the party running on its board; outside a party, the cartridge's containers. What does not
 *     fit is dropped at the merchant (never lost).</li>
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

    /** {@code sold} is sold: taken from its stock, in order. */
    public void take(ItemStack sold) {
        if (!sold.isEmpty()) stock().take(sold, sold.getCount());
    }

    /** The party running on its board, whose bank is its till; null outside a party. */
    public @Nullable PartyControllerEntity party() {
        return PartyControllerEntity.getPartyOfBoardSpace(world, space,
                PartyControllerEntity.BOARD_NEARBY_RADIUS, false).orElse(null);
    }

    /** {@code payment} (emptied) goes to its till; what does not fit is dropped at {@code dropAt}. */
    public void pay(ItemStack payment, Vec3d dropAt) {
        if (payment.isEmpty()) return;
        PartyControllerEntity party = party();
        if (party != null) PartyResources.of(party).give(payment);
        else {
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
        }
    }
}
