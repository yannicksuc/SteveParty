package fr.lordfinn.steveparty.powerups;

import fr.lordfinn.steveparty.dice.DiceOutcome;
import net.minecraft.util.Formatting;

/**
 * Champignon / Mushroom: +{@value #BONUS} to the next roll of the turn, on top of the die (plain, double, triple or
 * forged), after its modules (a Reversed die going back 4 then goes back 1). A blank roll is rolled again and keeps the
 * bonus for the next one; a roll of coins or a swap only also walks the {@value #BONUS} steps, after its effect.
 */
public class MushroomPowerUp extends PowerUp {
    public static final int BONUS = 3;
    /** In the turn's data: the bonus was given to a roll. */
    private static final String SPENT = "RollBonusSpent";

    public MushroomPowerUp() {
        super("mushroom", 5, Formatting.RED);
    }

    @Override
    public DiceOutcome modifyRoll(DiceOutcome outcome, PowerUpTurn turn) {
        if (turn.data().getBoolean(SPENT)) return outcome;
        turn.data().putBoolean(SPENT, true);
        return new DiceOutcome(outcome.steps() + BONUS, outcome.coins(), outcome.coinFace(), outcome.swap(), outcome.zero());
    }

    @Override
    protected Object[] descriptionArgs() {
        return new Object[]{"+" + BONUS};
    }
}
