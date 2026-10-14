package fr.lordfinn.steveparty.items.custom.cartridges.menu;

import fr.lordfinn.steveparty.components.MiniGamePageRef;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * One slot holding a mini-game page (the Mini-game Cartridge's page): a click with a page on the cursor keeps that page
 * (the item stays on the cursor, nothing is taken: only its id is kept), a right click (or a click with nothing on the
 * cursor) empties it, and an empty slot means the catalogue. Drawn as a {@link BlockSlotModule}, with the inventory to
 * pick the page from.
 * <p>
 * Its value: {@link #EMPTY} or {@link #CURSOR} (« the page on the cursor », read by the server from the player's menu,
 * see {@link CartridgeEdit#cursor()}); read back, {@link #CURSOR} when a page is kept.
 */
public final class PageSlotModule extends CartridgeModule {
    public static final int EMPTY = 0, CURSOR = 1;

    private final ComponentType<MiniGamePageRef> component;
    private final String hintKey;

    public PageSlotModule(String id, String labelKey, ComponentType<MiniGamePageRef> component, String hintKey) {
        super(id, labelKey);
        this.component = component;
        this.hintKey = hintKey;
    }

    /** What it does and how to set it (its tooltip). */
    public String hintKey() {
        return hintKey;
    }

    /** The page kept in {@code stack}, null if none. */
    public @Nullable MiniGamePageRef page(ItemStack stack) {
        return stack.get(component);
    }

    /** The page kept, as an item to draw (its title for a name), empty if none. */
    public ItemStack pageStack(ItemStack stack) {
        MiniGamePageRef ref = page(stack);
        if (ref == null) return ItemStack.EMPTY;
        ItemStack page = new ItemStack(ModItems.MINI_GAME_PAGE);
        page.set(ModComponents.MINI_GAME_PAGE, ref);
        return page;
    }

    /** Whether {@code cursor} is a page that can go there: one with content (opened once). */
    public static boolean accepts(ItemStack cursor) {
        return MiniGamePages.idOf(cursor) != null;
    }

    @Override
    public int height() {
        return labelHeight() + BlockSlotModule.SLOT;
    }

    @Override
    public boolean editable() {
        return true;
    }

    @Override
    public int get(ItemStack stack) {
        return page(stack) == null ? EMPTY : CURSOR;
    }

    @Override
    public boolean accepts(ItemStack stack, int value) {
        return value == EMPTY || value == CURSOR;
    }

    @Override
    public void set(CartridgeEdit edit, int value) {
        if (value == EMPTY) {
            edit.stack().remove(component);
            return;
        }
        MiniGamePageRef ref = edit.cursor().get(ModComponents.MINI_GAME_PAGE);
        if (accepts(edit.cursor()) && ref != null) edit.stack().set(component, new MiniGamePageRef(ref.id(), ref.title(), false));
    }
}
