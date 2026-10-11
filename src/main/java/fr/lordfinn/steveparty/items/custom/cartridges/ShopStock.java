package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyResources;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.components.InventoryComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_COMPONENT;

/**
 * The stock of a Shop Cartridge's offers: for each column of its stall, how many times its storage can still give the
 * item sold (its linked containers; none linked, the bank of its board's Party Controller, « ∞ » for an infinite bank).
 * Computed on the server, sent to the menus as screen properties ({@link Synced}): a property is a short, so a count is
 * capped at {@link #MAX} and the special values are small.
 */
public final class ShopStock {
    /** No offer in that column. */
    public static final int NO_OFFER = -1;
    /** An offer, but no storage to take it from (nothing linked, no Party Controller). */
    public static final int NO_STORAGE = -2;
    /** An infinite bank: never runs out. */
    public static final int INFINITE = 30000;
    /** The most counted (a short's room, below {@link #INFINITE}). */
    public static final int MAX = 29999;
    /** How often the stock is counted again while a menu is open (ticks). */
    private static final int REFRESH_TICKS = 10;

    private ShopStock() {
    }

    /** For each of the {@link ShopCartridgeItem#OFFERS} columns of {@code cartridge} held at {@code holder} (null: in hand). */
    public static int[] of(ItemStack cartridge, World world, @Nullable BlockPos holder) {
        int[] stock = new int[ShopCartridgeItem.OFFERS];
        Arrays.fill(stock, NO_OFFER);
        if (!(cartridge.getItem() instanceof ShopCartridgeItem) || !cartridge.contains(INVENTORY_COMPONENT)) return stock;
        PartyResources source = source(cartridge, world, holder);
        List<ItemStack> slots = ShopCartridgeItem.stallSlots(cartridge);
        for (int column = 0; column < stock.length; column++) {
            TradingStallBlockEntity.ExactTradeOffer offer = ShopCartridgeItem.offer(slots, column);
            if (offer == null) continue;
            stock[column] = source.isNone() ? NO_STORAGE : sales(source, offer.getSellItem());
        }
        return stock;
    }

    /**
     * Where its sales come from: its containers, or none linked the bank of its board's Party Controller (even before a
     * party starts: what that bank holds now).
     */
    private static PartyResources source(ItemStack cartridge, World world, @Nullable BlockPos holder) {
        if (!CartridgeContainers.isEmpty(cartridge) || holder == null) return CartridgeContainers.sourceFor(cartridge, world, holder);
        return PartyResources.of(PartyControllerEntity.getControllerOfBoardSpace(world, holder,
                PartyControllerEntity.BOARD_NEARBY_RADIUS).orElse(null));
    }

    /** How many times {@code source} can give {@code sold} (as many as that each time). */
    private static int sales(PartyResources source, ItemStack sold) {
        int held = source.available(sold);
        if (held == Integer.MAX_VALUE) return INFINITE;
        return Math.min(MAX, held / Math.max(1, sold.getCount()));
    }

    /** One column's stock as shown: « ∞ », the count (« 1k » from a thousand), or null (nothing to show). */
    public static @Nullable String label(int stock) {
        if (stock == INFINITE) return "∞";
        if (stock < 0) return null;
        return stock < 1000 ? Integer.toString(stock) : stock / 1000 + "k";
    }

    /**
     * The stock of the shop a screen shows, as {@link ShopCartridgeItem#OFFERS} properties of its handler: counted on the
     * server every {@value #REFRESH_TICKS} ticks and at once when the offers change, read on the client.
     */
    public static final class Synced {
        private final Property[] columns = new Property[ShopCartridgeItem.OFFERS];
        private int ticks;
        private @Nullable InventoryComponent counted;

        /** @param addProperty the handler's {@code addProperty} */
        public Synced(Consumer<Property> addProperty) {
            for (int i = 0; i < columns.length; i++) {
                columns[i] = Property.create();
                columns[i].set(NO_OFFER);
                addProperty.accept(columns[i]);
            }
        }

        /** Column {@code column}'s stock (see {@link ShopStock#of}). */
        public int get(int column) {
            return column >= 0 && column < columns.length ? columns[column].get() : NO_OFFER;
        }

        /** Server side, before the properties are sent: counts again when it is time or the offers changed. */
        public void update(ItemStack cartridge, World world, @Nullable BlockPos holder) {
            if (world.isClient) return;
            InventoryComponent offers = cartridge.get(INVENTORY_COMPONENT);
            if (--ticks > 0 && offers == counted) return;
            ticks = REFRESH_TICKS;
            counted = offers;
            int[] stock = of(cartridge, world, holder);
            for (int i = 0; i < columns.length; i++) columns[i].set(stock[i]);
        }
    }
}
