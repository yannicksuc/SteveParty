package fr.lordfinn.steveparty.items;

import fr.lordfinn.steveparty.board.LinkTargets;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.items.custom.MiniGamePageItem;
import fr.lordfinn.steveparty.items.custom.MiniGameRemoteItem;
import fr.lordfinn.steveparty.items.custom.ShopkeeperKeyItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The two link tags of the tooltips, added in one place by the client's tooltip callback (how to link:
 * {@code /steveparty links}):
 * <ul>
 *     <li>« [Linker] » ({@link Tooltips.Tag#LINKER}), the items that make links by clicking: the Tile Linker Brush,
 *     every Cartridge (its destinations, its containers, its Spawn Bubble), the Mini-game Page (its pipes, podiums,
 *     goal pole bases and step controllers), the Shopkeeper Key (its trader and the blocks of his shop), the Mini-game
 *     Remote (its Mini-game Controller);</li>
 *     <li>« [Linkable] » ({@link Tooltips.Tag#LINKABLE}), the blocks they link to: see {@link LinkTargets} (a vanilla
 *     chest or barrel as well as the mod's tiles).</li>
 * </ul>
 */
public final class LinkTags {
    private LinkTags() {
    }

    /** Whether {@code stack} makes links by clicking. */
    public static boolean isLinker(ItemStack stack) {
        return stack.getItem() instanceof CartridgeItem || stack.getItem() instanceof MiniGamePageItem
                || stack.getItem() instanceof ShopkeeperKeyItem || stack.getItem() instanceof MiniGameRemoteItem
                || TileLinkerBrush.isBrush(stack);
    }

    /** Whether {@code stack} places a block something can be linked to. */
    public static boolean isLinkable(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && LinkTargets.isTarget(item.getBlock());
    }

    /** Adds its link tag to the tooltip {@code lines} of {@code stack}, if it has one. */
    public static void tag(ItemStack stack, List<Text> lines) {
        if (isLinker(stack)) Tooltips.tag(lines, Tooltips.Tag.LINKER);
        else if (isLinkable(stack)) Tooltips.tag(lines, Tooltips.Tag.LINKABLE);
    }
}
