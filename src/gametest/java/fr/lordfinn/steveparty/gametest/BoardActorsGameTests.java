package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity;
import fr.lordfinn.steveparty.service.BoardActors;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.Vec3d;

import fr.lordfinn.steveparty.entities.BoardActor;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import fr.lordfinn.steveparty.entities.custom.magpie.MagpieEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import fr.lordfinn.steveparty.mixin.MobEntityGoalsAccessor;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestCleanup.atEnd;

/**
 * The mobs board spaces summon (BoardActors): nothing hurts them but commands and the void, a creative player
 * included; they go when their sequence ends; a stray one (tagged, unknown) is removed as it loads; they are never
 * aimed at but while their show wants it.
 */
public class BoardActorsGameTests implements SteveGameTest {
    private static final String BATCH = "board_actors";

    private static <T extends Entity> T spawn(TestContext context, T entity, UUID sequence) {
        Vec3d at = context.getAbsolute(new Vec3d(2.5, 2, 2.5));
        entity.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        BoardActors.join(sequence, entity);
        context.getWorld().spawnEntity(entity);
        atEnd(context, () -> BoardActors.end(sequence));
        return entity;
    }

    private static void assertUnhurt(TestContext context, LivingEntity actor, ServerPlayerEntity player, String what) {
        float health = actor.getHealth();
        actor.damage(player.getDamageSources().playerAttack(player), 50f);
        actor.damage(actor.getDamageSources().generic(), 50f);
        actor.damage(actor.getDamageSources().inFire(), 50f);
        actor.damage(actor.getDamageSources().magic(), 50f);
        context.assertTrue(actor.isAlive() && actor.getHealth() == health, what + " is not hurt");
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void nothingHurtsThemButCommands(TestContext context) {
        ServerPlayerEntity creative = player(context);
        UUID sequence = UUID.randomUUID();
        FrousseuxEntity frousseux = ModEntities.FROUSSEUX.create(context.getWorld());
        frousseux.makeBoardActor();
        spawn(context, frousseux, sequence);
        GlandouilleEntity glandouille = ModEntities.GLANDOUILLE.create(context.getWorld());
        glandouille.makeBoardActor();
        spawn(context, glandouille, sequence);
        PigEntity pig = spawn(context, EntityType.PIG.create(context.getWorld()), sequence);
        assertUnhurt(context, frousseux, creative, "the Frousseux");
        assertUnhurt(context, glandouille, creative, "the Glandouille");
        assertUnhurt(context, pig, creative, "any mob");
        context.assertTrue(!frousseux.shouldSave() && !glandouille.shouldSave(), "never saved");
        // /kill still works
        frousseux.kill();
        pig.kill();
        context.assertTrue(!frousseux.isAlive() && !pig.isAlive(), "a command kills them");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void removedWhenTheirSequenceEnds(TestContext context) {
        UUID sequence = UUID.randomUUID(), other = UUID.randomUUID();
        PigEntity a = spawn(context, EntityType.PIG.create(context.getWorld()), sequence);
        PigEntity b = spawn(context, EntityType.PIG.create(context.getWorld()), sequence);
        PigEntity c = spawn(context, EntityType.PIG.create(context.getWorld()), other);
        context.assertEquals(BoardActors.alive(sequence), 2, "two actors");
        BoardActors.end(sequence);
        context.assertTrue(a.isRemoved() && b.isRemoved(), "its actors are gone");
        context.assertTrue(!c.isRemoved(), "not another sequence's");
        context.assertEquals(BoardActors.alive(sequence), 0, "none left");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void aStrayOneGoesAsItLoads(TestContext context) {
        PigEntity stray = EntityType.PIG.create(context.getWorld());
        Vec3d at = context.getAbsolute(new Vec3d(2.5, 2, 2.5));
        stray.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        stray.addCommandTag(BoardActors.TAG); // as read from a save: tagged, but no running show knows it
        context.getWorld().spawnEntity(stray);
        context.assertTrue(stray.isRemoved(), "removed on load");
        context.complete();
    }

    /**
     * Every mob a space summons is a hologram (central rules, BoardActor*Mixin): no blow, arrow, lava, fire or
     * explosion hurts it, no use does anything (name tag, lead), it neither pushes nor is pushed nor knocked back, has
     * no goal, picks nothing up, and killed by a command leaves no loot and no experience.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 120)
    public void theyAreHolograms(TestContext context) {
        ServerPlayerEntity player = player(context);
        UUID sequence = UUID.randomUUID();
        List<MobEntity> mobs = new ArrayList<>();
        for (EntityType<? extends MobEntity> type : List.of(ModEntities.FROUSSEUX, ModEntities.GLANDOUILLE,
                ModEntities.MISTIGRI, ModEntities.TRICHAUDRON)) {
            MobEntity mob = type.create(context.getWorld());
            ((BoardActor) mob).makeBoardActor();
            mobs.add(spawn(context, mob, sequence));
        }
        MagpieEntity magpie = ModEntities.MAGPIE.create(context.getWorld());
        BoardActors.mark(magpie);
        spawn(context, magpie, sequence);
        ArrowEntity arrow = new ArrowEntity(EntityType.ARROW, context.getWorld());
        arrow.setOwner(player);
        for (MobEntity mob : mobs) {
            String what = mob.getType().getUntranslatedName();
            float health = mob.getHealth();
            mob.damage(player.getDamageSources().playerAttack(player), 50f);
            mob.damage(mob.getDamageSources().arrow(arrow, player), 50f);
            mob.damage(mob.getDamageSources().lava(), 50f);
            mob.damage(mob.getDamageSources().onFire(), 50f);
            mob.damage(mob.getDamageSources().explosion(null, null), 50f);
            context.assertTrue(mob.isAlive() && mob.getHealth() == health, what + ": nothing hurts it");
            context.assertTrue(mob.isFireImmune() && mob.isImmuneToExplosion(null), what + ": no fire, no explosion");
            // no use of any kind
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.NAME_TAG));
            player.getMainHandStack().set(DataComponentTypes.CUSTOM_NAME, Text.literal("Bob"));
            context.assertTrue(player.interact(mob, Hand.MAIN_HAND) == ActionResult.PASS && mob.getCustomName() == null,
                    what + ": no name tag");
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.LEAD));
            player.interact(mob, Hand.MAIN_HAND);
            context.assertTrue(!mob.isLeashed(), what + ": no lead");
            // neither pushed nor knocked back, pushing no one
            mob.setVelocity(Vec3d.ZERO);
            player.setVelocity(Vec3d.ZERO);
            mob.pushAwayFrom(player);
            player.pushAwayFrom(mob);
            mob.takeKnockback(2, 1, 1);
            context.assertTrue(mob.getVelocity().equals(Vec3d.ZERO) && player.getVelocity().equals(Vec3d.ZERO),
                    what + ": no push, no knockback");
            context.assertTrue(!mob.canPickUpLoot() && !mob.isPushedByFluids() && !mob.shouldSave(), what + ": picks nothing up, never saved");
        }
        context.assertTrue(magpie.isFireImmune() && !magpie.shouldSave() && !magpie.damage(magpie.getDamageSources().lava(), 50f)
                && player.interact(magpie, Hand.MAIN_HAND) == ActionResult.PASS, "the Pie too");
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        Vec3d at = context.getAbsolute(new Vec3d(2.5, 2, 2.5));
        context.runAtTick(context.getTick() + 10, () -> {
            for (MobEntity mob : mobs) {
                long goals = ((MobEntityGoalsAccessor) mob).steveparty$goals().getGoals().size()
                        + ((MobEntityGoalsAccessor) mob).steveparty$targets().getGoals().size();
                context.assertTrue(goals == 0, mob.getType().getUntranslatedName() + ": no goal at all");
            }
            for (MobEntity mob : mobs) mob.kill();
        });
        context.runAtTick(context.getTick() + 45, () -> {
            Box box = new Box(at, at).expand(6);
            context.assertTrue(context.getWorld().getEntitiesByClass(ItemEntity.class, box, e -> true).isEmpty(), "no loot");
            context.assertTrue(context.getWorld().getEntitiesByClass(ExperienceOrbEntity.class, box, e -> true).isEmpty(), "no experience");
            context.complete();
        });
    }

    /**
     * A hologram is never aimed at (canHit, what the crosshair and projectiles ask): the crosshair goes through it to
     * the block behind, and a block can be placed in its box; only while its show wants blows or clicks on it
     * (BoardActors#setTouchable) can it be aimed at. A mob that is no actor is aimed at and stops blocks as usual.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void theyAreNeverAimedAt(TestContext context) {
        ServerPlayerEntity player = player(context);
        UUID sequence = UUID.randomUUID();
        BoxedTraderEntity merchant = ModEntities.BOXED_TRADER_ENTITY.create(context.getWorld());
        merchant.makeBoardActor();
        merchant.setNoGravity(true);
        spawn(context, merchant, sequence);
        PigEntity wild = EntityType.PIG.create(context.getWorld());
        Vec3d wildAt = context.getAbsolute(new Vec3d(5.5, 2, 5.5));
        wild.refreshPositionAndAngles(wildAt.x, wildAt.y, wildAt.z, 0, 0);
        wild.setNoGravity(true);
        wild.setAiDisabled(true);
        context.getWorld().spawnEntity(wild);
        atEnd(context, wild::discard);

        context.assertFalse(merchant.canHit(), "a board actor is not aimed at");
        context.assertTrue(wild.canHit(), "a mob that is no actor is");
        // the crosshair: through the merchant, onto the wild pig
        Vec3d eye = context.getAbsolute(new Vec3d(2.5, 2.5, 0.5));
        context.assertTrue(aimed(player, eye, merchant) == null, "the crosshair goes through it");
        context.assertTrue(aimed(player, context.getAbsolute(new Vec3d(5.5, 2.5, 3.5)), wild) == wild, "not through a wild mob");
        // a block in its box
        BlockState stone = Blocks.STONE.getDefaultState();
        context.assertTrue(context.getWorld().canPlace(stone, merchant.getBlockPos(), ShapeContext.absent()),
                "a block can be placed in its box");
        context.assertFalse(context.getWorld().canPlace(stone, wild.getBlockPos(), ShapeContext.absent()),
                "not in a wild mob's");
        // its show wants clicks: aimed at, then a hologram again
        BoardActors.setTouchable(merchant, true);
        context.assertTrue(merchant.canHit() && aimed(player, eye, merchant) == merchant, "aimed at while its show wants it");
        BoardActors.setTouchable(merchant, false);
        context.assertFalse(merchant.canHit(), "then no more");
        context.complete();
    }

    /** What a crosshair from {@code eye} toward {@code target} picks among the entities (as the client does), or null. */
    private static Entity aimed(ServerPlayerEntity player, Vec3d eye, Entity target) {
        Vec3d end = eye.add(target.getBoundingBox().getCenter().subtract(eye).normalize().multiply(8));
        EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, new Box(eye, end).expand(1),
                e -> !e.isSpectator() && e.canHit(), eye.squaredDistanceTo(end));
        return hit == null ? null : hit.getEntity();
    }
}
