package fr.lordfinn.steveparty.items.custom.cartridges.menu;

import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
    private boolean half;
    private int panelSize;
    private List<String> tooltipKeys = List.of();

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

    /**
     * Whether it is drawn inside the previous module's box, on its right (the Shop's disguise beside its small stall):
     * it takes no height nor number of its own, its title goes on the right of the previous module's title line.
     */
    public boolean inline() {
        return false;
    }

    /**
     * Half a column wide: two half modules in a row sit side by side, each with its own number and label (a column too
     * narrow for two puts them one under the other). For number and button modules.
     */
    public CartridgeModule half() {
        half = true;
        return this;
    }

    public boolean isHalf() {
        return half;
    }

    /**
     * A row of tabs (a choice): the {@code modules} declared after it are the panel of the tab chosen, drawn inside a
     * frame joined to that tab, without numbers of their own. For a choice whose value picks what they edit.
     */
    public CartridgeModule tabs(int modules) {
        panelSize = modules;
        return this;
    }

    /** How many modules after it make its panel (0: no tab row). */
    public int panelSize() {
        return panelSize;
    }

    /** Its label shows a tooltip under the mouse: its label key + {@code .tooltip}. */
    public CartridgeModule tipped() {
        return labelKey == null ? this : tipped(labelKey + ".tooltip");
    }

    /** Its label shows a tooltip under the mouse: these lines (translation keys). */
    public CartridgeModule tipped(String... keys) {
        tooltipKeys = List.of(keys);
        return this;
    }

    /** The translation keys of its label's tooltip lines (none: no tooltip). */
    public List<String> tooltipKeys() {
        return tooltipKeys;
    }

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
