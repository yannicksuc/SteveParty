package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileContents;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileLayout;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import net.minecraft.block.Block;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import fr.lordfinn.steveparty.components.TileStampComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

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

    /** A tile carrying its own stamped look shows it (drawn by the client's tooltip component). */
    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        TileStampComponent stamp = TileContents.ownStamp(stack);
        return stamp == null ? Optional.empty() : Optional.of(stamp);
    }

    /**
     * Named after the role of the cartridge in its first slot (« Tuile Mistigri », « Tuile avancée Étoile »), and its
     * size when not the standard one. A plain cartridge, or none there: the tile's own name.
     */
    @Override
    public Text getName(ItemStack stack) {
        TileSize size = TileSize.of(stack);
        Text name = super.getName(stack);
        Text role = firstSlotRole(stack);
        if (role != null) name = Text.translatable("block.steveparty.tile.holding", name, role);
        if (size == TileSize.STANDARD) return name;
        return Text.translatable("block.steveparty.tile.sized", name, Text.translatable("tooltip.steveparty.tile.size." + size.asString()));
    }

    /** The name of the role the cartridge in the first slot gives (its info panel's title), null for none or a plain one. */
    private static @Nullable Text firstSlotRole(ItemStack stack) {
        for (TileContents.Slot slot : TileContents.cartridges(stack)) {
            if (slot.slot() != 0 || !(slot.cartridge().getItem() instanceof CartridgeItem cartridge)) continue;
            Identifier role = cartridge.getBoardSpaceRole();
            if (role.equals(BoardSpaceType.DEFAULT.id())) return null;
            return Text.translatable("hud." + role.getNamespace() + ".tile_info.role." + role.getPath());
        }
        return null;
    }
}
