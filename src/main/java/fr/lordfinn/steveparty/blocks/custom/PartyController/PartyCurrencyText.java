package fr.lordfinn.steveparty.blocks.custom.PartyController;

import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * How a party's currencies are named in its texts: never « coins » or « stars » written out, but the name of the item
 * the party counts as such ({@link PartyControllerEntity#getCurrency}), the mod's Coin and Party Star outside a party.
 * The name stays a translatable text (or the item's custom name) until a client shows it, in its own language.
 * <p>
 * An amount always reads « 5 × Coin » ({@value #AMOUNT_KEY}): an item name has no plural of its own.
 */
public final class PartyCurrencyText {
    /** « %s × %s »: a number, then the currency's name. */
    public static final String AMOUNT_KEY = "text.steveparty.currency.amount";

    /** On a client, the currencies of the party it plays in (empty: none), set by the client's party HUD. */
    private static Function<PartyCurrency, ItemStack> clientParty = currency -> ItemStack.EMPTY;

    private PartyCurrencyText() {
    }

    /** The item counted as {@code currency} by {@code party}, the mod's default without a party. */
    public static ItemStack item(@Nullable PartyControllerEntity party, PartyCurrency currency) {
        return party == null ? currency.defaultStack() : orDefault(party.getCurrency(currency), currency);
    }

    /** {@code stack}, or {@code currency}'s default item if empty. */
    public static ItemStack orDefault(ItemStack stack, PartyCurrency currency) {
        return stack == null || stack.isEmpty() ? currency.defaultStack() : stack;
    }

    /** The name of {@code item} as a currency: its custom name, else its translatable name. */
    public static MutableText name(ItemStack item) {
        return item.getName().copy();
    }

    /** The name of {@code party}'s {@code currency}, the default item's without a party. */
    public static MutableText name(@Nullable PartyControllerEntity party, PartyCurrency currency) {
        return name(item(party, currency));
    }

    /** « {@code count} × name of {@code item} ». {@code count} may be a styled text. */
    public static MutableText amount(ItemStack item, Object count) {
        return Text.translatable(AMOUNT_KEY, count, name(item));
    }

    /** « {@code count} × name of {@code party}'s {@code currency} ». */
    public static MutableText amount(@Nullable PartyControllerEntity party, PartyCurrency currency, Object count) {
        return amount(item(party, currency), count);
    }

    // ---------------------------------------------------------------- texts built where no party is at hand

    /**
     * Where a text is built with no party at hand (an item's tooltip, a menu): on a client playing a party, that
     * party's {@code currency}; else (a server, no party) the mod's default.
     */
    public static ItemStack contextual(PartyCurrency currency) {
        return orDefault(clientParty.apply(currency), currency);
    }

    /** {@link #amount} in the {@link #contextual} currency. */
    public static MutableText contextualAmount(PartyCurrency currency, Object count) {
        return amount(contextual(currency), count);
    }

    /** {@link #name} of the {@link #contextual} currency. */
    public static MutableText contextualName(PartyCurrency currency) {
        return name(contextual(currency));
    }

    /** Client only: where the currencies of the party the client plays in are read. */
    public static void setClientParty(Function<PartyCurrency, ItemStack> currencies) {
        clientParty = currencies;
    }
}
