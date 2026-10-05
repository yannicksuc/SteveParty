package fr.lordfinn.steveparty.powerups;

import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.powerups.effects.ThiefBellEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Cloche voleuse / Thief Bell ({@value ThiefBellEffect#MIN_COINS} to {@value ThiefBellEffect#MAX_COINS} coins) and
 * Cloche voleuse dorée / Golden Thief Bell (one star): its player picks another player of the party (a list showing
 * what each holds, up to 15 s; unanswered: the richest) and steals from them ({@link ThiefBellEffect}).
 * <ul>
 *     <li>A target with nothing of the kind to steal is refused: the bell is kept.</li>
 *     <li>A target protected by a Padlock parries it: the Padlock and the bell are both used up.</li>
 * </ul>
 */
public class ThiefBellPowerUp extends PowerUp {
    private final ThiefBellEffect.Variant variant;

    public ThiefBellPowerUp(ThiefBellEffect.Variant variant, int defaultPrice, Formatting color) {
        super(variant.itemId().substring("powerup_".length()), defaultPrice, color);
        this.variant = variant;
    }

    public ThiefBellEffect.Variant variant() {
        return variant;
    }

    @Override
    public Target target() {
        return Target.PLAYER;
    }

    /** The bell's own list: what each player holds, the richest picked when unanswered. */
    @Override
    public void pickPlayer(PowerUpUse use, List<ServerPlayerEntity> others, Consumer<ServerPlayerEntity> pick) {
        List<ServerPlayerEntity> candidates = ThiefBellEffect.candidates(use.controller(), use.player());
        ThiefBellEffect.ask(use.controller(), use.player(), variant, candidates.isEmpty() ? others : candidates,
                DicePrompts.TIMEOUT_TICKS, pick);
    }

    @Override
    public @Nullable Text refusal(PowerUpUse use) {
        ServerPlayerEntity target = use.targetPlayerEntity();
        if (target == null) return Text.translatable("message.steveparty.powerup.no_player");
        if (ThiefBellEffect.holdings(use.controller(), target, variant.currency()) <= 0)
            return Text.translatable("message.steveparty.powerup.thief_bell.nothing_to_steal", target.getDisplayName(),
                    use.controller().getCurrency(variant.currency()).getName());
        return null;
    }

    /** Used up when something was stolen, or when a Padlock parried it (the thief chose a protected target). */
    @Override
    public Result apply(PowerUpUse use) {
        ThiefBellEffect.Result result = ThiefBellEffect.steal(use.controller(), use.player(), use.targetPlayerEntity(),
                variant, use.world().getRandom(), ThiefBellEffect.Protection.padlock(use.controller()));
        return switch (result.outcome()) {
            case STOLEN, PROTECTED -> Result.APPLIED;
            case NOTHING, NO_TARGET -> Result.refusedSilently();
        };
    }

    /** The theft announces itself (who, from whom, how much), with the bell's sound. */
    @Override
    public @Nullable MutableText announcement(PowerUpUse use) {
        return null;
    }

    @Override
    protected Object[] descriptionArgs() {
        return variant == ThiefBellEffect.Variant.GOLDEN
                ? new Object[]{Text.translatable("powerup.steveparty.golden_thief_bell.desc.star")}
                : new Object[]{ThiefBellEffect.MIN_COINS + "-" + ThiefBellEffect.MAX_COINS};
    }

    @Override
    public boolean hasGlint() {
        return variant == ThiefBellEffect.Variant.GOLDEN;
    }
}
