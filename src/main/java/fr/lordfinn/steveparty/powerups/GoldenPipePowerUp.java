package fr.lordfinn.steveparty.powerups;

import fr.lordfinn.steveparty.powerups.effects.GoldenPipeEffect;
import fr.lordfinn.steveparty.powerups.effects.PowerUpStar;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Tuyau doré / Golden Pipe: its player's pawn warps to the board space just before the Star ({@link GoldenPipeEffect},
 * the Star read from {@link PowerUpStar}). The turn is held during the warp: the roll comes once the pawn has arrived.
 * Refused (kept) with no Star on the board, or when the pawn already stands before it.
 */
public class GoldenPipePowerUp extends PowerUp {
    /** The longest the turn waits for the warp (it takes about 28 ticks: {@code TileTeleport.TOTAL_TICKS}). */
    private static final int WARP_HOLD_TICKS = 200;

    public GoldenPipePowerUp() {
        super("golden_pipe", 25, Formatting.GOLD);
    }

    @Override
    public Result apply(PowerUpUse use) {
        if (!(use.tokenEntity() instanceof MobEntity token))
            return Result.refused(Text.translatable("message.steveparty.powerup.not_your_turn"));
        // The turn goes on (the roll) once the pawn has arrived
        Runnable resume = use.holdTurn(WARP_HOLD_TICKS);
        GoldenPipeEffect.Result result = new GoldenPipeEffect(PowerUpStar.relocator()).use(use.controller(), token, use.player(), resume);
        if (result.consumed()) return Result.APPLIED;
        resume.run();
        return Result.refusedSilently();
    }

    /** The warp announces itself (« X takes the Golden Pipe! »). */
    @Override
    public @Nullable MutableText announcement(PowerUpUse use) {
        return null;
    }

    @Override
    public List<Text> effectLines() {
        return List.of(
                Text.translatable("powerup.steveparty.golden_pipe.desc",
                        keyword(Text.translatable("powerup.steveparty.golden_pipe.desc.before_star"))).formatted(Formatting.GRAY),
                Text.translatable("powerup.steveparty.star.desc.no_star").formatted(Formatting.DARK_GRAY));
    }
}
