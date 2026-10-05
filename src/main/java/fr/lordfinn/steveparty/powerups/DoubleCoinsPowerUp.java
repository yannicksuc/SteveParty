package fr.lordfinn.steveparty.powerups;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Double pièces / Double Coins: every coin of the party its player gains until the end of the turn is doubled (a coin
 * face, an item space giving the party's coin...). The losses are not.
 */
public class DoubleCoinsPowerUp extends PowerUp {
    public static final int FACTOR = 2;

    public DoubleCoinsPowerUp() {
        super("double_coins", 8, Formatting.GOLD);
    }

    @Override
    public int modifyCoinsGained(int coins, PowerUpTurn turn) {
        return coins * FACTOR;
    }

    @Override
    public List<Text> effectLines() {
        return List.of(
                Text.translatable("powerup.steveparty.double_coins.desc",
                        keyword(Text.translatable("powerup.steveparty.double_coins.desc.gained")),
                        keyword(Text.translatable("powerup.steveparty.double_coins.desc.doubled"))).formatted(Formatting.GRAY),
                Text.translatable("powerup.steveparty.double_coins.desc.losses").formatted(Formatting.DARK_GRAY));
    }
}
