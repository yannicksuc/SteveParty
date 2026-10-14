package fr.lordfinn.steveparty.items.custom.cartridges.menu;

import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Item slots of a cartridge (the Inventory Cartridge's 3 × 3, the Trichaudron Cartridge's prizes): copies of items
 * (never real ones) with a quantity set with the mouse wheel, kept in its {@code INVENTORY_COMPONENT}. They are real
 * slots of the screen handler, which places them where the layout puts this module (always a cartridge's first
 * module); the player's inventory is shown with them (to pick the items from). {@link #signed} slots give (green) or
 * take (red); the others only give, and their help says what they are ({@link #helpKey}).
 */
public final class GhostSlotsModule extends CartridgeModule {
    /**
     * The most slots (the screen handlers make this many, the ones past {@link #count} hidden), and their step: 20, so
     * that their rings (and the chosen one's) keep a gap between them. The same for every cartridge: a tile's
     * interface places the slots once for all of them.
     */
    public static final int COUNT = 9, COLUMNS = 3, SLOT = 20;
    /** Where what is beside the slots starts (the chosen slot's editor, or the help), from the module's left. */
    public static final int SIDE_X = COLUMNS * SLOT + 4;

    private final int count;
    private final boolean signed;
    private final @Nullable String helpKey;

    /** The Inventory Cartridge's: 9 slots, each given or taken. */
    public GhostSlotsModule(String id, String labelKey) {
        this(id, labelKey, COUNT, true, null);
    }

    /** {@code count} slots of items given only, {@code helpKey} saying what they are and how to set them. */
    public GhostSlotsModule(String id, String labelKey, int count, String helpKey) {
        this(id, labelKey, count, false, helpKey);
    }

    private GhostSlotsModule(String id, String labelKey, int count, boolean signed, @Nullable String helpKey) {
        super(id, labelKey);
        this.count = Math.clamp(count, 1, COUNT);
        this.signed = signed;
        this.helpKey = helpKey;
    }

    /** How many slots it shows. */
    public int count() {
        return count;
    }

    /** Whether an item may be taken (red) as well as given. */
    public boolean signed() {
        return signed;
    }

    /** Its own help (null: the Inventory Cartridge's give / take / wheel help). */
    public @Nullable String helpKey() {
        return helpKey;
    }

    public int rows() {
        return (count + COLUMNS - 1) / COLUMNS;
    }

    /**
     * The height of its slots: their rows, less the last row's gap (only the chosen slot's white ring reaches 1 px
     * into the gap under the module).
     */
    public int slotsHeight() {
        return rows() * SLOT - 1;
    }

    /** The ghost slots of {@code stack}'s cartridge (its first module), null if it has none. */
    public static @Nullable GhostSlotsModule of(ItemStack stack) {
        if (!(stack.getItem() instanceof CartridgeItem cartridge) || cartridge.modules().isEmpty()) return null;
        return cartridge.modules().getFirst() instanceof GhostSlotsModule ghosts ? ghosts : null;
    }

    /** How many ghost slots {@code stack}'s cartridge shows (0: none). */
    public static int countOf(ItemStack stack) {
        GhostSlotsModule ghosts = of(stack);
        return ghosts == null ? 0 : ghosts.count;
    }

    @Override
    public int height() {
        return labelHeight() + slotsHeight();
    }

    /** The slot's top-left corner (where its item is drawn) relative to the module. */
    public int slotX(int index) {
        return 1 + (index % COLUMNS) * SLOT;
    }

    public int slotY(int index) {
        // 2 under the title: the chosen slot's ring (2 around it) keeps a gap under the title's number
        return labelHeight() + 2 + (index / COLUMNS) * SLOT;
    }
}
