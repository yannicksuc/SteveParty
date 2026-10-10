package fr.lordfinn.steveparty.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A mob's goals, for those who empty them (a board actor has none). */
@Mixin(MobEntity.class)
public interface MobEntityGoalsAccessor {
    @Accessor("goalSelector")
    GoalSelector steveparty$goals();

    @Accessor("targetSelector")
    GoalSelector steveparty$targets();
}
