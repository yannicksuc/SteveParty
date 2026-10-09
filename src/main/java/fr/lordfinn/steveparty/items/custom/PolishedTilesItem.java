package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.blocks.custom.tiles.PolishedTilesBlock;
import fr.lordfinn.steveparty.blocks.custom.tiles.PolishedTilesColor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * The polished tiles as an item: one item per material, the two colours in its {@code block_state} component (what
 * the vanilla block item places), told in its name: « Polished Concrete Tiles (White and Black) ».
 */
public class PolishedTilesItem extends BlockItem {
    public PolishedTilesItem(Block block, Settings settings) {
        // Every item tells its colours, the default one too: it stacks with the ones crafted or mined
        super(block, settings.component(DataComponentTypes.BLOCK_STATE, defaultColors((PolishedTilesBlock) block)));
    }

    private static BlockStateComponent defaultColors(PolishedTilesBlock tiles) {
        return tiles.component(tiles.colors().getFirst(), tiles.colors().getFirst());
    }

    @Override
    public Text getName(ItemStack stack) {
        PolishedTilesBlock tiles = (PolishedTilesBlock) getBlock();
        BlockState state = tiles.state(stack);
        PolishedTilesColor a = state.get(tiles.colorA()), b = state.get(tiles.colorB());
        Text name = Text.translatable(getTranslationKey());
        if (a == b) return Text.translatable("block.steveparty.polished_tiles.one_color", name, a.text());
        return Text.translatable("block.steveparty.polished_tiles.two_colors", name, a.text(), b.text());
    }
}
