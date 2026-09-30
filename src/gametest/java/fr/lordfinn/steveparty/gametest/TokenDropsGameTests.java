package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.TurtleEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/** Tokens are game pieces: they do not lay eggs or drop items on their own (regular mobs still do). */
public class TokenDropsGameTests implements FabricGameTest {
    private static final BlockPos MOB_POS = new BlockPos(2, 2, 2);

    private static <T extends MobEntity> T spawn(TestContext context, EntityType<T> type, boolean token) {
        T mob = context.spawnMob(type, MOB_POS);
        mob.setAiDisabled(true);
        if (token)
            ((TokenizedEntityInterface) mob).steveparty$setTokenized(true);
        return mob;
    }

    private static int droppedCount(TestContext context, MobEntity mob, Item item) {
        return context.getWorld().getEntitiesByClass(ItemEntity.class, mob.getBoundingBox().expand(4),
                entity -> entity.getStack().isOf(item)).size();
    }

    // ---------------------------------------------------------------- chicken eggs

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void tokenChickenDoesNotLayEggs(TestContext context) {
        ChickenEntity chicken = spawn(context, EntityType.CHICKEN, true);
        chicken.eggLayTime = 2;
        context.waitAndRun(10, () -> {
            context.assertTrue(droppedCount(context, chicken, Items.EGG) == 0, "a token chicken laid an egg");
            context.assertTrue(chicken.eggLayTime > 100, "the egg timer is reset (nothing stored up for later)");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void regularChickenStillLaysEggs(TestContext context) {
        ChickenEntity chicken = spawn(context, EntityType.CHICKEN, false);
        chicken.eggLayTime = 2;
        context.waitAndRun(10, () -> {
            context.assertTrue(droppedCount(context, chicken, Items.EGG) == 1, "a regular chicken lays its egg");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void untokenizedChickenLaysEggsAgain(TestContext context) {
        ChickenEntity chicken = spawn(context, EntityType.CHICKEN, true);
        ((TokenizedEntityInterface) chicken).steveparty$setTokenized(false);
        chicken.setAiDisabled(true);
        chicken.eggLayTime = 2;
        context.waitAndRun(10, () -> {
            context.assertTrue(droppedCount(context, chicken, Items.EGG) == 1, "back to a mob, it lays eggs again");
            context.complete();
        });
    }

    // ---------------------------------------------------------------- turtle scute

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void tokenTurtleGrowingUpDropsNoScute(TestContext context) {
        TurtleEntity turtle = spawn(context, EntityType.TURTLE, true);
        turtle.setBreedingAge(-2);
        context.waitAndRun(10, () -> {
            context.assertFalse(turtle.isBaby(), "the turtle grew up");
            context.assertTrue(droppedCount(context, turtle, Items.TURTLE_SCUTE) == 0, "a token turtle dropped a scute");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void regularTurtleGrowingUpDropsScute(TestContext context) {
        TurtleEntity turtle = spawn(context, EntityType.TURTLE, false);
        turtle.setBreedingAge(-2);
        context.waitAndRun(10, () -> {
            context.assertFalse(turtle.isBaby(), "the turtle grew up");
            context.assertTrue(droppedCount(context, turtle, Items.TURTLE_SCUTE) == 1, "a regular turtle drops its scute");
            context.complete();
        });
    }
}
