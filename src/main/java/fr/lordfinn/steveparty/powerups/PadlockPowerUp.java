package fr.lordfinn.steveparty.powerups;

import fr.lordfinn.steveparty.powerups.effects.PowerUpProtection;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * Cadenas / Padlock: its player is protected until the start of their next turn against one attack of the others, a
 * Thief Bell or a Trap ({@link PowerUpProtection}). Refused (kept) while they are already protected.
 */
public class PadlockPowerUp extends PowerUp {
    public PadlockPowerUp() {
        super("padlock", 8, Formatting.AQUA);
    }

    @Override
    public @Nullable Text refusal(PowerUpUse use) {
        return PowerUpProtection.isProtected(use.controller(), use.token())
                ? Text.translatable("message.steveparty.powerup.padlock.already") : null;
    }

    @Override
    public Result apply(PowerUpUse use) {
        return PowerUpProtection.protect(use.controller(), use.token())
                ? Result.APPLIED : Result.refused(Text.translatable("message.steveparty.powerup.padlock.already"));
    }

    /** The protection announces itself (« X is protected by a Padlock until their next turn! »). */
    @Override
    public @Nullable MutableText announcement(PowerUpUse use) {
        return null;
    }

    @Override
    protected Object[] descriptionArgs() {
        return new Object[]{Text.translatable("powerup.steveparty.padlock.desc.attack")};
    }
}
