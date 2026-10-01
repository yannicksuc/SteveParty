package fr.lordfinn.steveparty.blocks.custom.tiles;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Decorative tiling (« Polished Concrete Tiles », « Polished Terracotta Tiles »; nothing to do with the board's
 * Tile): each face is a checker of 2x2 small tiles in two colours, {@code color_a} top-left and bottom-right,
 * {@code color_b} top-right and bottom-left, as in its recipe. The pattern is one block wide, so neighbouring blocks
 * carry the checker on, around the corners too.
 * <p>
 * One block per material, the two colours in its state: every pair has its own plain model (a cube with one texture),
 * so it is drawn like stone. No block entity, no tint. The item keeps the colours in its {@code block_state}
 * component: placing, dropping and picking keep them.
 */
public abstract class PolishedTilesBlock extends Block {
    protected PolishedTilesBlock(Settings settings) {
        super(settings);
        setDefaultState(with(colors().getFirst(), colors().getFirst()));
    }

    /** Tiles of the 16 concrete colours. */
    public static class Concrete extends PolishedTilesBlock {
        public static final EnumProperty<PolishedTilesColor> COLOR_A = EnumProperty.of("color_a", PolishedTilesColor.class, PolishedTilesColor.DYES);
        public static final EnumProperty<PolishedTilesColor> COLOR_B = EnumProperty.of("color_b", PolishedTilesColor.class, PolishedTilesColor.DYES);

        public Concrete(Settings settings) {
            super(settings);
        }

        @Override public EnumProperty<PolishedTilesColor> colorA() { return COLOR_A; }
        @Override public EnumProperty<PolishedTilesColor> colorB() { return COLOR_B; }
        @Override public List<PolishedTilesColor> colors() { return PolishedTilesColor.DYES; }
        @Override public String material() { return "concrete"; }

        @Override
        public Block polished(PolishedTilesColor color) {
            return ModBlocks.POLISHED_CONCRETE_BLOCKS[color.ordinal() - 1];
        }
    }

    /** Tiles of the 17 terracotta colours (the plain one included). */
    public static class Terracotta extends PolishedTilesBlock {
        public static final EnumProperty<PolishedTilesColor> COLOR_A = EnumProperty.of("color_a", PolishedTilesColor.class, PolishedTilesColor.ALL);
        public static final EnumProperty<PolishedTilesColor> COLOR_B = EnumProperty.of("color_b", PolishedTilesColor.class, PolishedTilesColor.ALL);

        public Terracotta(Settings settings) {
            super(settings);
        }

        @Override public EnumProperty<PolishedTilesColor> colorA() { return COLOR_A; }
        @Override public EnumProperty<PolishedTilesColor> colorB() { return COLOR_B; }
        @Override public List<PolishedTilesColor> colors() { return PolishedTilesColor.ALL; }
        @Override public String material() { return "terracotta"; }

        @Override
        public Block polished(PolishedTilesColor color) {
            return ModBlocks.POLISHED_TERRACOTTA_BLOCKS[color.ordinal()];
        }
    }

    /** Colour of the top-left and bottom-right tiles of each face. */
    public abstract EnumProperty<PolishedTilesColor> colorA();

    /** Colour of the top-right and bottom-left tiles of each face. */
    public abstract EnumProperty<PolishedTilesColor> colorB();

    /** The colours this material comes in. */
    public abstract List<PolishedTilesColor> colors();

    /** "concrete" or "terracotta". */
    public abstract String material();

    /** The polished block of a colour: what these tiles are made from. */
    public abstract Block polished(PolishedTilesColor color);

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(colorA(), colorB());
    }

    public BlockState with(PolishedTilesColor a, PolishedTilesColor b) {
        return getStateManager().getDefaultState().with(colorA(), a).with(colorB(), b);
    }

    /** The map shows the first colour, like the polished block of that colour. */
    public static MapColor mapColor(BlockState state) {
        PolishedTilesBlock block = (PolishedTilesBlock) state.getBlock();
        return block.polished(state.get(block.colorA())).getDefaultMapColor();
    }

    /** {@code count} items of the tiles in colours (a, b). */
    public ItemStack stack(PolishedTilesColor a, PolishedTilesColor b, int count) {
        ItemStack stack = new ItemStack(this, count);
        stack.set(DataComponentTypes.BLOCK_STATE, component(a, b));
        return stack;
    }

    public ItemStack stack(BlockState state) {
        return stack(state.get(colorA()), state.get(colorB()), 1);
    }

    /** The {@code block_state} component of an item in colours (a, b): what the block's loot table writes. */
    public BlockStateComponent component(PolishedTilesColor a, PolishedTilesColor b) {
        return BlockStateComponent.DEFAULT.with(colorA(), a).with(colorB(), b);
    }

    /** @return the state an item places (the default colour for one it doesn't tell or that doesn't exist). */
    public BlockState state(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.BLOCK_STATE, BlockStateComponent.DEFAULT).applyToState(getDefaultState());
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
        return stack(state);
    }

    /** @return the colour whose polished block {@code stack} is, null if it isn't one of this material. */
    public @Nullable PolishedTilesColor polishedColor(ItemStack stack) {
        for (PolishedTilesColor color : colors()) {
            if (stack.isOf(polished(color).asItem())) return color;
        }
        return null;
    }

    /**
     * The creative tab's entries: every single-colour tiling, then a few two-colour ones (any pair can be crafted;
     * all {@code colors x colors} of them would flood the tab).
     */
    public List<ItemStack> creativeStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (PolishedTilesColor color : colors()) stacks.add(stack(color, color, 1));
        PolishedTilesColor first = colors().getFirst();
        PolishedTilesColor[][] pairs = {
                {first, PolishedTilesColor.BLACK}, {PolishedTilesColor.WHITE, PolishedTilesColor.LIGHT_GRAY},
                {PolishedTilesColor.WHITE, PolishedTilesColor.RED}, {PolishedTilesColor.WHITE, PolishedTilesColor.BLUE},
                {PolishedTilesColor.YELLOW, PolishedTilesColor.BLACK}, {PolishedTilesColor.ORANGE, PolishedTilesColor.BROWN},
                {PolishedTilesColor.LIGHT_BLUE, PolishedTilesColor.CYAN}, {PolishedTilesColor.LIME, PolishedTilesColor.GREEN}};
        for (PolishedTilesColor[] pair : pairs) stacks.add(stack(pair[0], pair[1], 1));
        return stacks;
    }
}
