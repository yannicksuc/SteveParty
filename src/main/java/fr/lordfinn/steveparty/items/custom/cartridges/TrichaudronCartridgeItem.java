package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.GhostSlotsModule;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_COMPONENT;
import static fr.lordfinn.steveparty.components.ModComponents.IS_NEGATIVE;

/**
 * The Trichaudron Cartridge (« Cartouche Trichaudron »): a stock of up to {@link #PRIZES} prizes, set in its menu as
 * ghost items (an item and how many), shown circling over its space. A token stopping on it meets the Trichaudron: its
 * three heads dive into its tank and each comes out with a hidden prize drawn from the stock (empty past it), a slow
 * die under them picks a head, whose prize goes to the token's player and leaves the stock (see TrichaudronPrizes).
 * Out of prizes the space sleeps until its stock is set again. Its tile is the dark red of the beast's crust.
 */
public class TrichaudronCartridgeItem extends CartridgeItem {
    /** Its tile's colour: a dark magma crust. */
    public static final int COLOR = 0x64200C;
    /** The most prizes in stock (its menu's slots). */
    public static final int PRIZES = 5;

    private static final String K = MENU_KEY + "trichaudron.";
    private static final List<CartridgeModule> MODULES = List.of(
            new GhostSlotsModule("prizes", K + "prizes", PRIZES, K + "wheel"),
            description("trichaudron_cartridge", 3));

    public TrichaudronCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_TRICHAUDRON;
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int tileColor() {
        return COLOR;
    }

    /** The prizes in stock, in their slots' order (copies; empty slots left out): an item and how many. */
    public static List<ItemStack> prizes(ItemStack stack) {
        List<ItemStack> prizes = new ArrayList<>();
        InventoryComponent stock = stack == null ? null : stack.get(INVENTORY_COMPONENT);
        if (stock == null) return prizes;
        for (int i = 0; i < Math.min(PRIZES, stock.size()); i++) {
            ItemStack prize = stock.getStack(i);
            if (prize.isEmpty()) continue;
            prize = prize.copy();
            prize.remove(IS_NEGATIVE); // a prize is given, never taken
            prizes.add(prize);
        }
        return prizes;
    }

    /** Sets the stock to {@code prizes} (the first {@link #PRIZES}; none: no stock). */
    public static void setPrizes(ItemStack stack, List<ItemStack> prizes) {
        SimpleInventory slots = new SimpleInventory(GhostSlotsModule.COUNT);
        int slot = 0;
        for (ItemStack prize : prizes) {
            if (prize.isEmpty()) continue;
            if (slot >= PRIZES) break;
            slots.setStack(slot++, prize.copy());
        }
        if (slot == 0) stack.remove(INVENTORY_COMPONENT);
        else InventoryComponent.writeToStack(stack, slots);
    }

    /**
     * Takes the prize {@code prize} (one of {@link #prizes}, compared item, components and count) out of the stock:
     * the first one matching. False if it was no longer there.
     */
    public static boolean take(ItemStack stack, ItemStack prize) {
        List<ItemStack> prizes = prizes(stack);
        for (int i = 0; i < prizes.size(); i++) {
            ItemStack one = prizes.get(i);
            if (one.getCount() == prize.getCount() && ItemStack.areItemsAndComponentsEqual(one, prize)) {
                prizes.remove(i);
                setPrizes(stack, prizes);
                return true;
            }
        }
        return false;
    }

    /** No prize left: its space sleeps. */
    public static boolean isEmpty(ItemStack stack) {
        return prizes(stack).isEmpty();
    }

    @Override
    protected void appendState(ItemStack stack, Tooltips tips) {
        int left = prizes(stack).size();
        if (left == 0) tips.warn(Text.translatable("tooltip.steveparty.trichaudron_cartridge.empty"));
        else tips.state("tooltip.steveparty.trichaudron_cartridge.prizes", Tooltips.value(left));
    }

    @Override
    protected void appendMore(ItemStack stack, Tooltips.More more) {
        more.note("tooltip.steveparty.cartridge.trichaudron_cartridge.rules");
    }
}
