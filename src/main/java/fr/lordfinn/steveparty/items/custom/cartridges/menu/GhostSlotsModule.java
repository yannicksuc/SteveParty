package fr.lordfinn.steveparty.items.custom.cartridges.menu;

/**
 * The Inventory Cartridge's 3 × 3 item slots: copies of items (never real ones) with a quantity set with the mouse
 * wheel, given (green) or taken (red). They are real slots of the screen handler, which places them where the layout
 * puts this module; the player's inventory is shown with them (to pick the items from).
 */
public final class GhostSlotsModule extends CartridgeModule {
    public static final int COUNT = 9, COLUMNS = 3, SLOT = 18;

    public GhostSlotsModule(String id, String labelKey) {
        super(id, labelKey);
    }

    @Override
    public int height() {
        return labelHeight() + (COUNT / COLUMNS) * SLOT;
    }

    /** The slot's top-left corner (where its item is drawn) relative to the module. */
    public int slotX(int index) {
        return 1 + (index % COLUMNS) * SLOT;
    }

    public int slotY(int index) {
        return labelHeight() + 1 + (index / COLUMNS) * SLOT;
    }
}
