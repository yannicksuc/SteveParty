package fr.lordfinn.steveparty.dice;

import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.components.DiceFacesComponent.Kind;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * What a roll does, once every die thrown together (one die, or the linked dice of a Double / Triple Dice) has
 * stopped. The faces combine like this:
 * <ul>
 *     <li>the numbers (normal, premium, cursed, 0, blank side, plain dice) add up into the {@link #steps};</li>
 *     <li>the coin and debt faces add up into one change of {@link #coins} (+ gained, − lost);</li>
 *     <li>swap faces give one {@link #swap}, however many were rolled.</li>
 * </ul>
 * The coins are applied first, then the swap, then the token walks its steps (negative: backward, the Reversed
 * module). Without steps to walk, a roll holding a
 * coin, debt or swap face ends the turn where the token stands, without landing on its tile; a roll of numbers only
 * that holds a face 0 lands on the tile it stands on again; a roll of blank sides does nothing, as before.
 *
 * @param steps    steps the token walks
 * @param coins    coins the roller gains (negative: loses)
 * @param coinFace a coin or debt face was rolled (their sum may be 0)
 * @param swap     the token swaps places with another one
 * @param zero     a face 0 was rolled
 */
public record DiceOutcome(int steps, int coins, boolean coinFace, boolean swap, boolean zero) {
    public static final DiceOutcome NONE = new DiceOutcome(0, 0, false, false, false);

    /** The outcome of a plain number. */
    public static DiceOutcome ofSteps(int steps) {
        return new DiceOutcome(steps, 0, false, false, false);
    }

    public static DiceOutcome of(List<DiceFace> faces) {
        int steps = 0, coins = 0;
        boolean coinFace = false, swap = false, zero = false;
        for (DiceFace face : faces) {
            steps += face.steps();
            coins += face.coins();
            coinFace |= face.kind() == Kind.COIN || face.kind() == Kind.DEBT;
            swap |= face.kind() == Kind.SWAP;
            zero |= face.isZero();
        }
        return new DiceOutcome(steps, coins, coinFace, swap, zero);
    }

    /** The Reversed module: the steps are walked backward, the coins gained are lost (and the reverse). */
    public DiceOutcome reversed() {
        return new DiceOutcome(-steps, -coins, coinFace, swap, zero);
    }

    /** A coin, debt or swap face was rolled: the roll is not (only) a number of steps. */
    public boolean isSpecial() {
        return coinFace || swap;
    }

    /** Nothing to walk and no special face, but a face 0: the token lands again on the tile it stands on. */
    public boolean landsInPlace() {
        return steps == 0 && !isSpecial() && zero;
    }

    /** The coins as shown to the players: "+5", "−3" (a real minus sign), "0". */
    public static String signed(int coins) {
        return coins > 0 ? "+" + coins : coins < 0 ? "−" + -coins : "0";
    }

    /** What was rolled, for the chat: "7", "+5 coins", "Swap", "3, −2 coins"... */
    public Text describe() {
        MutableText text = Text.empty();
        boolean first = true;
        if (steps != 0 || !isSpecial()) {
            if (steps < 0) text.append(Text.translatable("message.steveparty.dice.back", -steps).formatted(Formatting.RED));
            else text.append(Text.literal(Integer.toString(steps)));
            first = false;
        }
        if (coinFace) {
            if (!first) text.append(", ");
            text.append(Text.translatable(Math.abs(coins) == 1 ? "message.steveparty.dice.coins.one" : "message.steveparty.dice.coins",
                    signed(coins)).formatted(coins < 0 ? Formatting.RED : Formatting.YELLOW));
            first = false;
        }
        if (swap) {
            if (!first) text.append(", ");
            text.append(Text.translatable("message.steveparty.dice.swap").formatted(Formatting.LIGHT_PURPLE));
        }
        return text;
    }
}
