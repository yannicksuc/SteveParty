package fr.lordfinn.steveparty.powerups;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.powerups.effects.TrapEffect;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * Piège / Trap: a hidden trap on the board space its player's pawn stands on ({@link TrapEffect}); the next pawn of
 * another player stopping there gives them {@value TrapEffect#COINS} coins. Secret: only its player is told (no
 * announcement to the party). Refused (kept) when the pawn stands on no board space.
 */
public class TrapPowerUp extends PowerUp {
    public TrapPowerUp() {
        super("trap", 10, Formatting.DARK_RED);
    }

    @Override
    public @Nullable Text refusal(PowerUpUse use) {
        return use.tokenEntity() instanceof MobEntity token && BoardSpaces.boardSpaceOf(token) != null
                ? null : Text.translatable("message.steveparty.powerup.trap.no_space");
    }

    @Override
    public Result apply(PowerUpUse use) {
        if (!(use.tokenEntity() instanceof MobEntity token))
            return Result.refused(Text.translatable("message.steveparty.powerup.trap.no_space"));
        return switch (TrapEffect.use(use.controller(), token)) {
            case SET, REPLACED -> Result.APPLIED;
            case NO_SPACE, NO_PLAYER -> Result.refusedSilently();
        };
    }

    /** Secret: the party is not told (it would show where the trap is). */
    @Override
    public @Nullable MutableText announcement(PowerUpUse use) {
        return null;
    }

    @Override
    protected Object[] descriptionArgs() {
        return new Object[]{TrapEffect.COINS};
    }
}
