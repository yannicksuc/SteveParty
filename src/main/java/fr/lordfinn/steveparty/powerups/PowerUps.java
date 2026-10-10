package fr.lordfinn.steveparty.powerups;

import fr.lordfinn.steveparty.api.StevePartyRegistries;
import fr.lordfinn.steveparty.api.registry.ContentKeys;
import fr.lordfinn.steveparty.powerups.effects.ThiefBellEffect;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * The registry of the power-ups ({@link PowerUp}): the one place where a power-up is plugged in. Registering one here
 * (an addon's under its own namespace, from its entrypoint) is all it takes for its item to exist ({@code ModItems}
 * registers one {@code <namespace>:powerup_<path>} per power-up, in the
 * creative tab), to be counted as a power-up by the party HUD, to be sold at its default price by a Trading Stall, and
 * to be used during a turn.
 */
public final class PowerUps {
    /** Registers the item of a power-up (set by ModItems once it registers items: the later ones get theirs at once). */
    private static @Nullable java.util.function.Consumer<PowerUp> itemRegistrar;

    /** +3 to the next roll of the turn, whatever the die. */
    public static final PowerUp MUSHROOM = register(new MushroomPowerUp());
    /** The coins gained during the turn are doubled (not the losses). */
    public static final PowerUp DOUBLE_COINS = register(new DoubleCoinsPowerUp());
    /** Protects its player against one Thief Bell or Trap of the others, until their next turn. */
    public static final PowerUp PADLOCK = register(new PadlockPowerUp());
    /** A hidden trap on its pawn's space: the next other player stopping there gives 10 coins. */
    public static final PowerUp TRAP = register(new TrapPowerUp());
    /** Steals 5 to 15 coins from a player picked in a list. */
    public static final PowerUp THIEF_BELL = register(new ThiefBellPowerUp(ThiefBellEffect.Variant.THIEF, 12, Formatting.DARK_PURPLE));
    /** Sends the Star to another active Star space. */
    public static final PowerUp STAR_WHISTLE = register(new StarWhistlePowerUp());
    /** Warps its pawn to the space just before the Star; the roll comes once it has arrived. */
    public static final PowerUp GOLDEN_PIPE = register(new GoldenPipePowerUp());
    /** Steals one star from a player picked in a list. */
    public static final PowerUp GOLDEN_THIEF_BELL = register(new ThiefBellPowerUp(ThiefBellEffect.Variant.GOLDEN, 40, Formatting.GOLD));

    /**
     * Stands for a saved power-up that no longer exists: the turn counts it as used, it does nothing. Not registered
     * (no item).
     */
    static final PowerUp SPENT = new PowerUp("spent", 1, Formatting.GRAY) {
    };
    /**
     * Stands for a die carrying the Power-up module, thrown this turn: the power-up of the turn, it does nothing more
     * (its roll is the die's). Not registered (no item; saved, it reads back as {@link #SPENT}).
     */
    static final PowerUp DIE = new PowerUp("die", 1, Formatting.LIGHT_PURPLE) {
    };

    private PowerUps() {
    }

    public static <T extends PowerUp> T register(T powerUp) {
        StevePartyRegistries.POWER_UPS.register(powerUp.identifier(), powerUp);
        if (itemRegistrar != null) itemRegistrar.accept(powerUp);
        return powerUp;
    }

    /** Every power-up, in registration order. */
    public static Collection<PowerUp> all() {
        return StevePartyRegistries.POWER_UPS.values();
    }

    /**
     * Registers the item of every power-up, now and of those registered later (an addon's, from its entrypoint).
     * Called once by ModItems.
     */
    public static void registerItems(java.util.function.Consumer<PowerUp> registrar) {
        itemRegistrar = registrar;
        List.copyOf(all()).forEach(registrar);
    }

    /** The power-up of a saved key ({@link PowerUp#id()}), null for none. */
    public static @Nullable PowerUp byId(String id) {
        return StevePartyRegistries.POWER_UPS.get(ContentKeys.id(id));
    }
}
