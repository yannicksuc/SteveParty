package fr.lordfinn.steveparty.items.custom.cartridges.menu;

import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One ghost slot holding a block (the Shop Cartridge's merchant disguise): a click with a block item on the cursor puts
 * a copy of it there (the item stays on the cursor, nothing is taken), a right click (or a click with nothing on the
 * cursor) empties it, and an empty slot means the default ({@link #defaultBlock}). Only blocks {@link #valid} for it are
 * accepted. Its value, sent as {@code (module id, int value)} like the others, is the raw id of the block's item
 * ({@link #EMPTY}: none), the block kept being its default state.
 * <p>
 * Not a real slot of the screen handler: the menu draws it and reads the cursor itself, so that it may sit anywhere
 * in the layout (the real ghost slots are always a cartridge's first module, see {@link GhostSlotsModule}). Shown on
 * its own (the slot, the block's name and the hint beside it) or {@link #inline} (the slot alone, on the right of the
 * previous module, its title over it: the hint goes in its tooltip).
 */
public final class BlockSlotModule extends CartridgeModule {
    /** The value of an empty slot (the raw id of air). */
    public static final int EMPTY = 0;
    /** The slot's size, and its height in the menu (its label excluded). */
    public static final int SLOT = 18;

    private final Function<ItemStack, BlockState> getter;
    private final BiConsumer<CartridgeEdit, BlockState> setter;
    private final Predicate<BlockState> valid;
    private final BlockState defaultBlock;
    private final String hintKey;
    private final boolean inline;

    /**
     * @param getter       the block stored in a cartridge (null: none)
     * @param setter       writes the block chosen (null: empties it)
     * @param valid        whether a block may be chosen (its default state)
     * @param defaultBlock what an empty slot means (drawn faded in it)
     * @param hintKey      what it does (beside the slot, or in its tooltip when inline)
     * @param inline       drawn on the right of the previous module (see {@link CartridgeModule#inline()})
     */
    public BlockSlotModule(String id, String labelKey, Function<ItemStack, BlockState> getter,
                           BiConsumer<CartridgeEdit, BlockState> setter, Predicate<BlockState> valid,
                           BlockState defaultBlock, String hintKey, boolean inline) {
        super(id, labelKey);
        this.getter = getter;
        this.setter = setter;
        this.valid = valid;
        this.defaultBlock = defaultBlock;
        this.hintKey = hintKey;
        this.inline = inline;
    }

    @Override
    public boolean inline() {
        return inline;
    }

    /** What an empty slot means. */
    public BlockState defaultBlock() {
        return defaultBlock;
    }

    public String hintKey() {
        return hintKey;
    }

    /** The block of {@code item} that could go in the slot, or null (not a block item, or a block it refuses). */
    public @Nullable BlockState blockOf(Item item) {
        if (!(item instanceof BlockItem blockItem)) return null;
        BlockState state = blockItem.getBlock().getDefaultState();
        return valid.test(state) ? state : null;
    }

    /** The block stored in {@code stack}, null if none (or one it would refuse now). */
    public @Nullable BlockState block(ItemStack stack) {
        BlockState state = getter.apply(stack);
        return state != null && valid.test(state) ? state : null;
    }

    /** The value a click with {@code cursor} sends: its item's raw id, {@link #EMPTY} for nothing. */
    public static int valueOf(ItemStack cursor) {
        return cursor.isEmpty() ? EMPTY : Registries.ITEM.getRawId(cursor.getItem());
    }

    /** Inline: none (it sits in the previous module's box). */
    @Override
    public int height() {
        return inline ? 0 : labelHeight() + SLOT;
    }

    @Override
    public boolean editable() {
        return true;
    }

    @Override
    public int get(ItemStack stack) {
        BlockState state = block(stack);
        return state == null || state.getBlock().asItem() == null ? EMPTY : Registries.ITEM.getRawId(state.getBlock().asItem());
    }

    @Override
    public boolean accepts(ItemStack stack, int value) {
        return value == EMPTY || blockOf(Registries.ITEM.get(value)) != null;
    }

    @Override
    public void set(CartridgeEdit edit, int value) {
        setter.accept(edit, value == EMPTY ? null : blockOf(Registries.ITEM.get(value)));
    }
}
