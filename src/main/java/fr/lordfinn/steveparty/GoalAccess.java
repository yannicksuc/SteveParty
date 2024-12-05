package fr.lordfinn.steveparty;

import java.lang.reflect.Field;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;

public class GoalAccess {
    public static void addGoalToMob(MobEntity mob, Goal goal) {
        try {
            // Use reflection to access the protected goalSelector field
            Field field = MobEntity.class.getDeclaredField("goalSelector");
            field.setAccessible(true);
            GoalSelector goalSelector = (GoalSelector) field.get(mob);

            // Add the goal to the mob's goal selector
            goalSelector.add(1, goal);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}