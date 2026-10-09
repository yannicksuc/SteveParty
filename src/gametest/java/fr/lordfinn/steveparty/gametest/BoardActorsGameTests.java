package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity;
import fr.lordfinn.steveparty.service.BoardActors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestCleanup.atEnd;

/**
 * The mobs board spaces summon (BoardActors): nothing hurts them but commands and the void, a creative player
 * included; they go when their sequence ends; a stray one (tagged, unknown) is removed as it loads.
 */
public class BoardActorsGameTests implements FabricGameTest {
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
}
