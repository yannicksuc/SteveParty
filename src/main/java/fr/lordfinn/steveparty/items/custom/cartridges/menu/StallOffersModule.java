package fr.lordfinn.steveparty.items.custom.cartridges.menu;

import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Shop Cartridge's offers, drawn as a small Trading Stall: its awning, the items sold over it, an arrow per column
 * with what the shop's storage can still give, and the prices under it. A click opens the stall's own screen bound to
 * the cartridge ({@link fr.lordfinn.steveparty.screen_handlers.custom.ShopStallScreenHandler}), where its slots hold
 * ghost copies. It holds no value: the click opens that screen (see
 * {@link fr.lordfinn.steveparty.payloads.custom.CartridgeSettingPayload}), nothing is written through
 * {@link CartridgeMenus#apply}.
 */
public final class StallOffersModule extends CartridgeModule {
    /**
     * The part of the stall's texture shown (the sold row, the awning, the arrows and the two price rows, its bottom
     * edge redrawn under them), and the scale it is drawn at in a column of the menu.
     */
    public static final int STALL_W = 184, STALL_H = 89;
    public static final float SCALE = 0.7F;
    /** The preview's height in the menu (its label excluded). */
    public static final int PREVIEW_H = Math.round(STALL_H * SCALE) + 1;
    /** The value a click sends: open the stall. */
    public static final int OPEN = 1;

    /** @param labelKey its title, or null (the small stall says what it is) */
    public StallOffersModule(String id, @Nullable String labelKey) {
        super(id, labelKey);
    }

    @Override
    public int height() {
        return labelHeight() + PREVIEW_H;
    }

    /** Clickable (it gets the current), though it writes nothing itself. */
    @Override
    public boolean editable() {
        return true;
    }

    @Override
    public boolean accepts(ItemStack stack, int value) {
        return false;
    }
}
