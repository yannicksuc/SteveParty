package fr.lordfinn.steveparty.items.custom.cartridges.menu;

import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * One setting (or piece of information) of a cartridge, shown as one block of its menu, the « cartridge shell » (see
 * {@link fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem#modules()}). A cartridge declares the list of its
 * modules; the menu lays them out (see {@link CartridgeLayout}), draws them by kind and sends a change as
 * {@code (module id, int value)}, which the server checks with {@link #accepts} then applies with {@link #set}.
 * <p>
 * Kinds: {@link ChoiceModule} (buttons or colour swatches), {@link NumberModule} (a − / + stepper),
 * {@link ColorModule} (the dyes), {@link GhostSlotsModule} (the Inventory Cartridge's item slots) and
 * {@link InfoModule} (read-only lines). A new cartridge only declares modules of these kinds.
 */
public abstract class CartridgeModule {
    /** The height of a module's label line. */
    public static final int LABEL_H = 10;

    private final String id;
    private final @Nullable String labelKey;

    protected CartridgeModule(String id, @Nullable String labelKey) {
        this.id = id;
        this.labelKey = labelKey;
    }

    /** Unique within its cartridge: the key of a change sent to the server. */
    public String id() {
        return id;
    }

    /** The translation key of its label, or null (no label line). */
    public @Nullable String labelKey() {
        return labelKey;
    }

    /** Its height in the menu, label included (the same on both sides: the layout places slots with it). */
    public abstract int height();

    /** Whether it holds a value the player can change. */
    public boolean editable() {
        return false;
    }

    /** Whether it can be changed now (e.g. « the next space triggers » only when the token moves on). */
    public boolean enabled(ItemStack stack) {
        return true;
    }

    /** Its value in {@code stack}. */
    public int get(ItemStack stack) {
        return 0;
    }

    /** Whether {@code value} is a valid value for it (server side check of a change). */
    public boolean accepts(ItemStack stack, int value) {
        return false;
    }

    /** Writes {@code value} in the edited cartridge (checked with {@link #accepts} before). */
    public void set(CartridgeEdit edit, int value) {
    }

    protected int labelHeight() {
        return labelKey == null ? 0 : LABEL_H;
    }
}
