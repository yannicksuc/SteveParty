package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.blocks.custom.CartridgeTransfers;
import fr.lordfinn.steveparty.blocks.custom.PiggyBankBlockEntity;
import fr.lordfinn.steveparty.client.board.LinkedPanels.Divider;
import fr.lordfinn.steveparty.client.board.LinkedPanels.Icon;
import fr.lordfinn.steveparty.client.board.LinkedPanels.Label;
import fr.lordfinn.steveparty.client.board.LinkedPanels.Panel;
import fr.lordfinn.steveparty.client.board.LinkedPanels.Part;
import fr.lordfinn.steveparty.client.board.LinkedPanels.Space;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_COMPONENT;

/**
 * What the Piggy Bank looked at does, on its {@link LinkedPanels} panel: its target's icon (the dice: the player of the
 * turn, the gold podium: the winners, the party: everyone, the compass: the nearest), then each item of its cartridge
 * with what it moves, green « +5 » given, red « -20 » taken; a « / » between them when one of them only moves at a
 * time (random or in turn). Linked to no party (it does nothing then), the Tile Linker Brush crossed out in red comes
 * first. No words.
 */
public final class PiggyBankInfoClient {
    private static final int GIVE = 0xFF1E7A1E, TAKE = 0xFFB3202A;
    /** Above the pig (its head reaches 15/16 of the block). */
    private static final double ABOVE = 1.1;

    private PiggyBankInfoClient() {
    }

    public static void initialize() {
        LinkedPanels.register(blockEntity -> blockEntity instanceof PiggyBankBlockEntity bank ? panel(bank) : null);
    }

    private static Panel panel(PiggyBankBlockEntity bank) {
        List<Part> parts = new ArrayList<>();
        if (!bank.isPartyLinked()) LinkedPanels.notLinked(parts);
        parts.add(new Icon(bank.getTarget().getIcon()));
        ItemStack cartridge = bank.getStack(0);
        InventoryComponent content = cartridge.getOrDefault(INVENTORY_COMPONENT, null);
        List<ItemStack> items = new ArrayList<>();
        if (content != null) for (ItemStack stack : content.getItems()) if (!stack.isEmpty()) items.add(stack);
        boolean oneOf = items.size() > 1 && InventoryCartridgeItem.getSelectionState(cartridge) != InventoryCartridgeItem.ALL;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (i == 0) {
                parts.add(new Divider());
            } else if (oneOf) {
                parts.add(new Space(LinkedPanels.TEXT_SLASH));
                parts.add(new Label("/", LinkedPanels.SOFT));
                parts.add(new Space(LinkedPanels.SLASH_ICON));
            } else {
                parts.add(new Space(LinkedPanels.ITEM_GAP));
            }
            boolean taken = CartridgeTransfers.isNegative(stack);
            parts.add(new Icon(CartridgeTransfers.pattern(stack)));
            parts.add(new Space(LinkedPanels.ICON_TEXT));
            parts.add(new Label((taken ? "-" : "+") + stack.getCount(), taken ? TAKE : GIVE));
        }
        return new Panel(parts, ABOVE);
    }
}
