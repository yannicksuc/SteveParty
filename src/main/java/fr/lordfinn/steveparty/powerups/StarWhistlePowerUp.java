package fr.lordfinn.steveparty.powerups;

import fr.lordfinn.steveparty.powerups.effects.PowerUpStar;
import fr.lordfinn.steveparty.powerups.effects.StarWhistleEffect;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Sifflet d'étoile / Star Whistle: the Star flies off to another active Star space, at random
 * ({@link StarWhistleEffect}, the Star read and moved through {@link PowerUpStar}). Refused (kept) with no Star on the
 * board, or no other active Star space.
 */
public class StarWhistlePowerUp extends PowerUp {
    public StarWhistlePowerUp() {
        super("star_whistle", 15, Formatting.YELLOW);
    }

    @Override
    public Result apply(PowerUpUse use) {
        StarWhistleEffect.Result result = new StarWhistleEffect(PowerUpStar.relocator())
                .use(use.controller(), use.tokenEntity(), use.player(), use.world().getRandom());
        return result.consumed() ? Result.APPLIED : Result.refusedSilently();
    }

    /** The whistle announces itself (« X whistles: the Star flies off to another space! »). */
    @Override
    public @Nullable MutableText announcement(PowerUpUse use) {
        return null;
    }

    @Override
    public List<Text> effectLines() {
        return List.of(
                Text.translatable("powerup.steveparty.star_whistle.desc",
                        keyword(Text.translatable("powerup.steveparty.star_whistle.desc.other_space"))).formatted(Formatting.GRAY),
                Text.translatable("powerup.steveparty.star.desc.no_star").formatted(Formatting.DARK_GRAY));
    }
}
