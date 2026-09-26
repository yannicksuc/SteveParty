package fr.lordfinn.steveparty.items.custom.cartridges;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.items.custom.AbstractDestinationsSelectorItem;
import fr.lordfinn.steveparty.items.custom.CartridgeContainerOpener;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TileStampComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Optional;

/**
 * A cartridge gives a role to the board space it is inserted in.
 * Each cartridge item declares that role: adding a new kind of board space is a new cartridge item
 * returning a new {@link BoardSpaceType}, plus its behavior in the behavior factory.
 */
public class CartridgeItem extends AbstractDestinationsSelectorItem implements CartridgeContainerOpener {
    public CartridgeItem(Settings settings) {
        super(settings);
    }

    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.DEFAULT;
    }

    /** A stamped cartridge shows its look (drawn by the client's tooltip component). */
    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        TileStampComponent stamp = stack.get(ModComponents.TILE_STAMP);
        return stamp == null ? Optional.empty() : Optional.of(stamp);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        TileStampComponent stamp = stack.get(ModComponents.TILE_STAMP);
        if (stamp != null) {
            tooltip.add(Text.translatable("tooltip.steveparty.stamped").formatted(Formatting.LIGHT_PURPLE));
            tooltip.add(stamp.describe().copy().formatted(Formatting.GRAY));
        }
    }
}
