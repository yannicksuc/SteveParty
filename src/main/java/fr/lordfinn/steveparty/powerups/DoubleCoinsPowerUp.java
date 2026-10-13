package fr.lordfinn.steveparty.powerups;

import org.jetbrains.annotations.Nullable;
import net.minecraft.text.MutableText;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrencyText;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Double pièces / Double Coins: every coin of the party its player gains until the end of the turn is doubled (a coin
 * face, an item space giving the party's coin...), the extra taken from where the coins come from (the party's bank,
 * the space's chests): never made from nothing. The losses are not.
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

    /** Names the party's coin. */
    @Override
    public @Nullable MutableText announcement(PowerUpUse use) {
        return Text.translatable(translationKey() + ".announce", use.player().getDisplayName(),
                PartyCurrencyText.name(use.controller(), PartyCurrency.COIN));
    }

    @Override
    public List<Text> effectLines() {
        return List.of(
                Text.translatable("powerup.steveparty.double_coins.desc", PartyCurrencyText.contextualName(PartyCurrency.COIN),
                        keyword(Text.translatable("powerup.steveparty.double_coins.desc.gained")),
                        keyword(Text.translatable("powerup.steveparty.double_coins.desc.doubled"))).formatted(Formatting.GRAY),
                Text.translatable("powerup.steveparty.double_coins.desc.bank").formatted(Formatting.DARK_GRAY),
                Text.translatable("powerup.steveparty.double_coins.desc.losses").formatted(Formatting.DARK_GRAY));
    }
}
