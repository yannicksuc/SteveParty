package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.StarSettingsComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeEdit;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.NumberModule;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The Star Cartridge (« Cartouche Étoile », yellow): its tile or check point is a star space. During a party, the
 * party's star stands on one of its active star spaces, drawn at random when the party starts; the player whose token
 * reaches it may buy it for its price in coins, and it then goes to another star space (see
 * {@link fr.lordfinn.steveparty.service.PartyStars}). Its menu: the price, whether passing tokens may buy it, and
 * whether the star leaves a space that is switched off.
 */
public class StarCartridgeItem extends CartridgeItem {
    /** The star's yellow: the cartridge, its tile and its check point (no other role is yellow). */
    public static final int COLOR = 0xFFD83D;

    private static final String K = MENU_KEY + "star.";

    private static void write(CartridgeEdit edit, StarSettingsComponent settings) {
        if (settings.equals(StarSettingsComponent.DEFAULT)) edit.stack().remove(ModComponents.STAR_SETTINGS);
        else edit.stack().set(ModComponents.STAR_SETTINGS, settings);
    }

    private static final List<CartridgeModule> MODULES = List.of(
            description("star_cartridge", 3),
            new NumberModule("price", K + "price", 0, StarSettingsComponent.MAX_PRICE, stack -> settings(stack).price(),
                    (edit, value) -> write(edit, settings(edit.stack()).withPrice(value)), stack -> COLOR),
            new ChoiceModule("buy", K + "buy",
                    List.of(ChoiceModule.Option.tipped(K + "on_pass"), ChoiceModule.Option.tipped(K + "on_stop")),
                    stack -> settings(stack).onPass() ? 0 : 1,
                    (edit, value) -> write(edit, settings(edit.stack()).withOnPass(value == 0))),
            new ChoiceModule("relocate", K + "relocate",
                    List.of(ChoiceModule.Option.tipped(K + "relocate.leave"), ChoiceModule.Option.tipped(K + "relocate.stay")),
                    stack -> settings(stack).relocate() ? 0 : 1,
                    (edit, value) -> write(edit, settings(edit.stack()).withRelocate(value == 0))));

    public StarCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_STAR;
    }

    public static StarSettingsComponent settings(ItemStack stack) {
        return stack.getOrDefault(ModComponents.STAR_SETTINGS, StarSettingsComponent.DEFAULT);
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int menuColor(ItemStack stack) {
        return COLOR;
    }

    @Override
    protected void appendState(ItemStack stack, Tooltips tips) {
        StarSettingsComponent settings = settings(stack);
        tips.state("tooltip.steveparty.star_cartridge.price", Tooltips.coins(Text.translatable("tooltip.steveparty.coins", settings.price())));
        tips.state(Text.translatable(settings.onPass() ? "tooltip.steveparty.star_cartridge.on_pass"
                : "tooltip.steveparty.star_cartridge.on_stop"));
        tips.state(Text.translatable(settings.relocate() ? "tooltip.steveparty.star_cartridge.relocate"
                : "tooltip.steveparty.star_cartridge.stays"));
    }
}
