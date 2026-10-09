package fr.lordfinn.steveparty.client.screens.partycontroller;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.Page;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** What the dashboard's pages see of its screen: its handler and data, where it is, its widgets and its actions. */
public interface Dashboard {
    PartyControllerScreenHandler handler();

    /** The dashboard's state, from the server; null until it arrives. */
    @Nullable PartyDashboardData data();

    Page page();

    TextRenderer font();

    /** The panel's corner on the screen. */
    int left();

    int top();

    <T extends Element & Drawable & Selectable> T add(T widget);

    /** Clicks a button of the handler {@code times} times (on the server). */
    void click(int button, int times);

    default void click(int button) {
        click(button, 1);
    }

    /** Builds the widgets again (a state they show changed). */
    void rebuild();

    void showPage(Page page);

    void close();

    /** The item standing for the currency (the slot of the Gains tab). */
    default ItemStack currency(PartyCurrency currency) {
        return handler().getSlot(currency == PartyCurrency.STAR ? PartyControllerScreenHandler.SLOT_STAR : PartyControllerScreenHandler.SLOT_COIN).getStack();
    }
}
