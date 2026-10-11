package fr.lordfinn.steveparty.items;

import fr.lordfinn.steveparty.items.custom.MiniGamePageItem;
import fr.lordfinn.steveparty.items.custom.MiniGameRemoteItem;
import fr.lordfinn.steveparty.items.custom.ShopkeeperKeyItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The items whose stacks hold links, tagged « [Linkable] » in their tooltip (one place, by the client's tooltip
 * callback; how to link: {@code /steveparty links}):
 * <ul>
 *     <li>every Cartridge: its destinations (board paths, switched blocks), its containers (Inventory, Tricauldron,
 *     Shop) and its Spawn Marker (a mob space's);</li>
 *     <li>the Mini-game Page: its pipes, podiums, goal pole bases and step controllers;</li>
 *     <li>the Shopkeeper Key: its trader and the blocks of his shop;</li>
 *     <li>the Mini-game Remote: its Mini-game Controller.</li>
 * </ul>
 * The Tile Linker Brush makes links but keeps none (only its level and cartridge): not tagged.
 */
public final class LinkableItems {
    private LinkableItems() {
    }

    public static boolean holdsLinks(ItemStack stack) {
        return stack.getItem() instanceof CartridgeItem || stack.getItem() instanceof MiniGamePageItem
                || stack.getItem() instanceof ShopkeeperKeyItem || stack.getItem() instanceof MiniGameRemoteItem;
    }

    /** Adds the tag to the tooltip {@code lines} of {@code stack} if it holds links. */
    public static void tag(ItemStack stack, List<Text> lines) {
        if (holdsLinks(stack)) Tooltips.tag(lines, Tooltips.Tag.LINKABLE);
    }
}
