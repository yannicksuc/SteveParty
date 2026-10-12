package fr.lordfinn.steveparty.effect;

import fr.lordfinn.steveparty.entities.custom.bubble.BubbleEntity;
import fr.lordfinn.steveparty.entities.custom.bubble.BubbleRiding;
import fr.lordfinn.steveparty.service.BoardActors;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

/**
 * Bubbled: while it lasts, the entity floats in a soap bubble ({@link BubbleEntity#encloseByEffect}) that rises a few
 * blocks then hovers, like a Bubbler's (a player inside steers it: movement keys, charged puff). The effect lasts as
 * long as the bubble: its pop (a hit, a projectile from outside, a wall, a mob, a puff too many, left still 15 s, the
 * rider punching out, a mini-game zone's border) ends it, and its rider falls slowly. Given for a time (a command), its
 * end pops the bubble too.
 * <p>
 * Given by a projectile shot from inside a bubble (see ProjectileEntityBubbleMixin), {@link #PROJECTILE_TICKS}. Never on
 * a boss ({@code c:bosses}: the ender dragon, the wither), a board actor, or a mob too big for a bubble
 * ({@link #canBubble}). Its amplifier is unused. No potion yet: a brewing recipe would register in
 * {@link ModEffects#initialize}.
 */
public class BubbledEffect extends StatusEffect {
    /** How long a bubbled projectile's hit keeps its target in a bubble: until the bubble pops. */
    public static final int PROJECTILE_TICKS = StatusEffectInstance.INFINITE;
    /** The film's pale iridescent blue (its particles' colour). */
    private static final int COLOR = 0xA8D8F0;

    public BubbledEffect() {
        super(StatusEffectCategory.NEUTRAL, COLOR);
    }

    /** Whether {@code entity} can be bubbled: alive, small enough for a bubble, no boss, no board actor. */
    public static boolean canBubble(LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator() || !BubbleRiding.fits(entity)) return false;
        if (entity.getType().isIn(ConventionalEntityTypeTags.BOSSES)) return false;
        return !entity.getCommandTags().contains(BoardActors.TAG);
    }

    /** Bubbles {@code entity} for {@code ticks} (server): false if it can't be. */
    public static boolean bubble(LivingEntity entity, int ticks, @Nullable LivingEntity source) {
        if (entity.getWorld().isClient || !canBubble(entity)) return false;
        return entity.addStatusEffect(new StatusEffectInstance(ModEffects.BUBBLED, ticks, 0), source);
    }

    /** Bubbled: into a bubble at once. */
    @Override
    public void onApplied(LivingEntity entity, int amplifier) {
        if (entity.getWorld() instanceof ServerWorld world && !(entity.getVehicle() instanceof BubbleEntity) && canBubble(entity)) {
            BubbleEntity.encloseByEffect(world, entity);
        }
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true;
    }

    /** Out of a bubble (just bubbled, or taken out of one but by its pop): into a new one. */
    @Override
    public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (!(entity.getWorld() instanceof ServerWorld world) || entity.getVehicle() instanceof BubbleEntity) return true;
        if (!canBubble(entity)) return false;
        return BubbleEntity.encloseByEffect(world, entity) != null;
    }
}
