package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileLayout;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * The item of the Tile and the Advanced Tile: its size ({@link TileSize}) shows in its name. A large tile is put on the
 * aimed block and spreads over 3 more (downhill on a slope, toward the aimed side on level ground): all must be free.
 */
public class TileBlockItem extends BlockItem {
    public TileBlockItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public @Nullable ItemPlacementContext getPlacementContext(ItemPlacementContext context) {
        ItemPlacementContext placement = super.getPlacementContext(context);
        if (placement == null || TileSize.of(context.getStack()) != TileSize.LARGE) return placement;
        TileLayout layout = ATileBlock.layoutFor(placement);
        return ATileBlock.partsFree(placement.getWorld(), placement.getBlockPos(), layout) ? placement : null;
    }

    @Override
    public Text getName(ItemStack stack) {
        TileSize size = TileSize.of(stack);
        Text name = super.getName(stack);
        if (size == TileSize.STANDARD) return name;
        return Text.translatable("block.steveparty.tile.sized", name, Text.translatable("tooltip.steveparty.tile.size." + size.asString()));
    }
}
