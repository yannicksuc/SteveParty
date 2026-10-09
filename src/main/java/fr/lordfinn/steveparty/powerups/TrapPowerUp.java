package fr.lordfinn.steveparty.powerups;

import fr.lordfinn.steveparty.components.TrapSetupComponent;
import fr.lordfinn.steveparty.items.custom.TrapPowerUpItem;
import fr.lordfinn.steveparty.powerups.effects.TrapEffect;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * Piège / Trap: a trap, seen by everyone, on the board space its player's pawn stands on ({@link TrapEffect}); the
 * next pawn of another player stopping there springs it. Unsigned, it steals {@link TrapEffect#COINS} coins; signed
 * ({@link TrapSetupComponent}, see {@link TrapPowerUpItem}), it does what its signer chose. Refused (kept) when the
 * pawn stands on no board space, or on a trapped one.
 */
public class TrapPowerUp extends PowerUp {
    public TrapPowerUp() {
        super("trap", 10, Formatting.DARK_RED);
    }

    @Override
    public Item createItem(Item.Settings settings) {
        return new TrapPowerUpItem(this, settings);
    }

    @Override
    public @Nullable Text refusal(PowerUpUse use) {
        return use.tokenEntity() instanceof MobEntity token ? TrapEffect.refusal(use.controller(), token)
                : Text.translatable("message.steveparty.powerup.trap.no_space");
    }

    @Override
    public Result apply(PowerUpUse use) {
        if (!(use.tokenEntity() instanceof MobEntity token))
            return Result.refused(Text.translatable("message.steveparty.powerup.trap.no_space"));
        return switch (TrapEffect.use(use.controller(), token, TrapSetupComponent.effectOf(use.item()))) {
            case SET -> Result.APPLIED;
            case OCCUPIED -> Result.refused(Text.translatable("message.steveparty.powerup.trap.occupied"));
            case NO_SPACE -> Result.refused(Text.translatable("message.steveparty.powerup.trap.no_space"));
            case NO_PLAYER -> Result.refusedSilently();
        };
    }

    /** Told to everyone: the trap is no secret, nor what it does. */
    @Override
    public @Nullable MutableText announcement(PowerUpUse use) {
        return Text.translatable("powerup.steveparty.trap.announce", use.player().getDisplayName(),
                TrapSetupComponent.effectOf(use.item()).describe().formatted(Formatting.RED));
    }
}
