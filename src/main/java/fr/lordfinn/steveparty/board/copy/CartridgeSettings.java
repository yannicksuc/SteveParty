package fr.lordfinn.steveparty.board.copy;

import fr.lordfinn.steveparty.components.ModComponents;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * The settings of a cartridge, as the Wrench copies them (see {@link WrenchCopy}): what its menu sets, ghost copies of
 * items included (they are no items). The other half of the {@link fr.lordfinn.steveparty.board.Pipette}, which copies
 * its destinations only. Never copied: where it leads or what it is linked to (destinations, chests, Spawn Bubble, nest,
 * pipes), what a party left in it (the pot's coins, a sleeping Trichaudron), nor a look paid with an item (stamp, glow ink).
 */
public final class CartridgeSettings {
    public static final List<ComponentType<?>> SETTINGS = List.of(
            ModComponents.COLOR,
            ModComponents.ADVANCE_BACK_STEPS,
            ModComponents.TELEPORT_SETTINGS,
            ModComponents.STAR_SETTINGS,
            ModComponents.GLANDOUILLE_DISTANCE,
            ModComponents.GLANDOUILLE_TOWER,
            ModComponents.GLANDOUILLE_LONE,
            ModComponents.FROUSSEUX_STARS,
            ModComponents.FROUSSEUX_COINS,
            ModComponents.FROUSSEUX_STAR_COUNT,
            ModComponents.MISTIGRI_OUTCOMES,
            ModComponents.INVENTORY_COMPONENT,
            ModComponents.IS_NEGATIVE,
            ModComponents.SELECTION_STATE,
            ModComponents.BOX_BLOCK,
            ModComponents.SHOP_PURCHASES,
            ModComponents.TRICHAUDRON_SAME_PRIZES,
            ModComponents.TRICHAUDRON_TRUE_CHOICE,
            ModComponents.BOARD_CARTRIDGE_SETTINGS,
            ModComponents.CARTRIDGE_MINI_GAME_PAGE);

    private CartridgeSettings() {
    }

    /** A cartridge of the kind of {@code cartridge} with its settings only (one, no link): what a creative paste puts in. */
    public static ItemStack settingsOnly(ItemStack cartridge) {
        ItemStack copy = new ItemStack(cartridge.getItem());
        apply(cartridge, copy);
        return copy;
    }

    /**
     * {@code target} (a cartridge of the same kind) takes the settings of {@code source}; its links stay.
     *
     * @return whether it changed
     */
    public static boolean apply(ItemStack source, ItemStack target) {
        boolean changed = false;
        for (ComponentType<?> type : SETTINGS) changed |= copy(source, target, type);
        return changed;
    }

    private static <T> boolean copy(ItemStack from, ItemStack to, ComponentType<T> type) {
        T value = from.get(type);
        if (Objects.equals(value, to.get(type))) return false;
        to.set(type, value);
        return true;
    }
}
