package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.glandouille.AcornCropBlock;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.effect.DazedEffect;
import fr.lordfinn.steveparty.effect.ModEffects;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity.Mood;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleSpawns;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleTowers;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleVariant;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.service.GlandouillePushes;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * The Glandouille: it never hurts (it shoves), stomps flatten then finish it (the mossy one takes one more), towers
 * (spontaneous ones stop at 5, carried ones go higher, they only fall when the bottom one charges into a wall, a
 * flick shoots one out alone, the ones above hopping back down), players dazed by a charge, a slide or a shot, the
 * mossy one anchored and never charging, the frosty one sliding off a wall, the cap never lost under a tower, the
 * planted acorn hatching, and the Glandouille board space.
 */
public class GlandouilleGameTests implements FabricGameTest {

    // ---------------------------------------------------------------- set-up

    /** A stone floor (y 0) over the whole test area: the Glandouilles walk at y 1. */
    private static void floor(TestContext context) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
    }

    private static GlandouilleEntity glandouille(TestContext context, GlandouilleVariant variant, BlockPos at) {
        GlandouilleEntity one = context.spawnEntity(ModEntities.GLANDOUILLE, at);
        one.setVariant(variant);
        one.setHat(true);
        return one;
    }

    /** A tower of {@code count} at {@code at}, bottom first, built by hand (no height limit but the server's). */
    private static List<GlandouilleEntity> tower(TestContext context, GlandouilleVariant bottom, int count, BlockPos at) {
        List<GlandouilleEntity> members = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            GlandouilleEntity one = glandouille(context, i == 0 ? bottom : GlandouilleVariant.CLASSIC, at);
            if (i > 0) context.assertTrue(GlandouilleTowers.climb(one, members.getFirst(), false), "climbs " + i);
            members.add(one);
        }
        return members;
    }

    private static ServerPlayerEntity player(TestContext context, BlockPos at, float yaw) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        BlockPos abs = context.getAbsolutePos(at);
        player.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, yaw, 0);
        atEnd(context, () -> context.getWorld().getServer().getPlayerManager().remove(player));
        return player;
    }

    // ---------------------------------------------------------------- no damage, only shoves

    /** Its charge shoves a pig hard and hurts it not; a blow pushes it but hurts it not either. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void itNeverHurtsItShoves(TestContext context) {
        floor(context);
        GlandouilleEntity glandouille = glandouille(context, GlandouilleVariant.CLASSIC, new BlockPos(1, 1, 3));
        PigEntity pig = context.spawnMob(EntityType.PIG, new BlockPos(4, 1, 3));
        double pigX = pig.getX();
        float pigHealth = pig.getHealth();
        context.assertTrue(glandouille.startCharge(new Vec3d(1, 0, 0)), "charges");
        context.waitAndRun(40, () -> {
            context.assertTrue(pig.getX() - pigX > 0.6, "the pig was shoved: " + (pig.getX() - pigX));
            context.assertEquals(pig.getHealth(), pigHealth, "the pig is not hurt");
            // hit by a player: pushed, not hurt
            ServerPlayerEntity player = player(context, new BlockPos(0, 1, 3), -90f);
            float health = glandouille.getHealth();
            boolean damaged = glandouille.damage(context.getWorld().getDamageSources().playerAttack(player), 5f);
            context.assertFalse(damaged, "a blow does no damage");
            context.assertEquals(glandouille.getHealth(), health, "not hurt by a blow");
            context.assertTrue(glandouille.getVelocity().horizontalLengthSquared() > 0.01, "pushed");
            context.assertTrue(player.getUuid().equals(glandouille.grudge()), "it holds a grudge against the player");
            context.complete();
        });
    }

    // ---------------------------------------------------------------- stomps

    /** A stomp flattens it ("pouic"), the stomper bounces; the second one finishes it, and it drops an acorn. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void twoStompsFinishIt(TestContext context) {
        floor(context);
        GlandouilleEntity glandouille = glandouille(context, GlandouilleVariant.CLASSIC, new BlockPos(3, 1, 3));
        ServerPlayerEntity player = player(context, new BlockPos(3, 3, 3), 0f);
        context.assertTrue(glandouille.squash(player), "stomped");
        context.assertEquals(glandouille.getMood(), Mood.FLAT, "flattened");
        context.assertTrue(player.getVelocity().y > 0.3, "the player bounces");
        context.assertTrue(glandouille.isAlive(), "still alive");
        context.waitAndRun(10, () -> {
            context.assertTrue(glandouille.squash(player), "stomped again");
            context.assertTrue(glandouille.isDead() || glandouille.isRemoved(), "done for");
            context.waitAndRun(2, () -> {
                Box around = new Box(context.getAbsolutePos(BlockPos.ORIGIN)).expand(8);
                context.assertTrue(!context.getWorld().getEntitiesByClass(ItemEntity.class, around,
                        item -> item.getStack().isOf(ModItems.ACORN)).isEmpty(), "an acorn dropped");
                context.complete();
            });
        });
    }

    /** The old mossy one: dented by the first stomp, flattened by the second, done for at the third. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void theMossyOneTakesThreeStomps(TestContext context) {
        floor(context);
        GlandouilleEntity mossy = glandouille(context, GlandouilleVariant.MOSSY, new BlockPos(3, 1, 3));
        ServerPlayerEntity player = player(context, new BlockPos(3, 3, 3), 0f);
        context.assertTrue(mossy.squash(player), "first stomp");
        context.assertTrue(mossy.getMood() != Mood.FLAT && mossy.isAlive(), "only dented");
        context.waitAndRun(10, () -> {
            mossy.squash(player);
            context.assertEquals(mossy.getMood(), Mood.FLAT, "flattened at the second");
            context.assertTrue(mossy.isAlive(), "alive after two");
            context.waitAndRun(10, () -> {
                mossy.squash(player);
                context.assertTrue(mossy.isDead() || mossy.isRemoved(), "done for at the third");
                context.complete();
            });
        });
    }

    /** A player coming down on its cap is seen (no call from the test): flattened. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void aPlayerLandingOnItsCapFlattensIt(TestContext context) {
        floor(context);
        GlandouilleEntity glandouille = glandouille(context, GlandouilleVariant.CLASSIC, new BlockPos(3, 1, 3));
        glandouille.setAiDisabled(true);
        ServerPlayerEntity player = player(context, new BlockPos(3, 1, 3), 0f);
        double top = glandouille.getBoundingBox().maxY;
        player.setPosition(glandouille.getX(), top + 0.1, glandouille.getZ());
        context.waitAndRun(2, () -> {
            player.setPosition(glandouille.getX(), top - 0.2, glandouille.getZ());
            context.waitAndRun(2, () -> {
                context.assertEquals(glandouille.getMood(), Mood.FLAT, "flattened by the landing");
                context.assertEquals(glandouille.stomps(), 1, "one stomp");
                context.complete();
            });
        });
    }

    // ---------------------------------------------------------------- sizes

    /**
     * Each variant its model's hitbox at 60 % (its model drawn as small); in a tower each one stands on the cap of the
     * one below, at its height.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 20)
    public void eachVariantHasItsModelsSize(TestContext context) {
        floor(context);
        GlandouilleEntity classic = glandouille(context, GlandouilleVariant.CLASSIC, new BlockPos(5, 1, 1));
        GlandouilleEntity young = glandouille(context, GlandouilleVariant.YOUNG, new BlockPos(1, 1, 1));
        GlandouilleEntity mossy = glandouille(context, GlandouilleVariant.MOSSY, new BlockPos(5, 1, 5));
        GlandouilleEntity frosty = glandouille(context, GlandouilleVariant.FROSTY, new BlockPos(1, 1, 5));
        for (GlandouilleEntity one : List.of(classic, young, mossy, frosty)) {
            GlandouilleVariant variant = one.getVariant();
            context.assertTrue(Math.abs(one.getWidth() - variant.widthPx / 16f * 0.6f) < 1.0E-4f, variant + " width: " + one.getWidth());
            context.assertTrue(Math.abs(one.getHeight() - variant.heightPx / 16f * 0.6f) < 1.0E-4f, variant + " height: " + one.getHeight());
            context.assertTrue(Math.abs(one.getStandingEyeHeight() - variant.eyePx / 16f * 0.6f) < 1.0E-4f,
                    variant + " eyes: " + one.getStandingEyeHeight());
            context.assertEquals(one.getScaleFactor(), 0.6f, variant + " drawn at 60 %");
        }
        context.assertTrue(Math.abs(classic.getWidth() - 0.45f) < 1.0E-4f && Math.abs(classic.getHeight() - 0.5625f) < 1.0E-4f,
                "the classic one: 0.45 x 0.5625");
        context.assertTrue(Math.abs(ModEntities.GLANDOUILLE.getWidth() - classic.getWidth()) < 1.0E-4f
                && Math.abs(ModEntities.GLANDOUILLE.getHeight() - classic.getHeight()) < 1.0E-4f, "spawn checks at the same size");
        context.assertTrue(young.getHeight() < classic.getHeight() && young.getWidth() < classic.getWidth(), "the young one is small: " + young.getHeight());
        context.assertTrue(mossy.getWidth() > frosty.getWidth(), "the mossy one is wide: " + mossy.getWidth());
        context.assertTrue(frosty.getWidth() > classic.getWidth(), "the frosty one a little bigger: " + frosty.getWidth());
        context.assertTrue(GlandouilleTowers.climb(young, mossy, false), "the young one on the mossy one");
        context.assertTrue(GlandouilleTowers.climb(frosty, mossy, false), "the frosty one on top");
        context.waitAndRun(2, () -> {
            context.assertTrue(Math.abs(young.getY() - (mossy.getY() + mossy.getHeight())) < 0.1, "on the mossy cap");
            context.assertTrue(Math.abs(frosty.getY() - (young.getY() + young.getHeight())) < 0.1, "on the young cap");
            context.complete();
        });
    }

    // ---------------------------------------------------------------- towers

    /** Glandouilles build towers by themselves up to 5, not higher. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void spontaneousTowersStopAtFive(TestContext context) {
        floor(context);
        List<GlandouilleEntity> all = new ArrayList<>();
        for (int i = 0; i < 7; i++) all.add(glandouille(context, GlandouilleVariant.CLASSIC, new BlockPos(1 + i % 4, 1, 2 + i / 4)));
        GlandouilleEntity base = all.getFirst();
        for (int i = 1; i < 7; i++) {
            boolean climbed = GlandouilleTowers.climb(all.get(i), base, true);
            context.assertEquals(climbed, i < GlandouilleTowers.SPONTANEOUS_MAX, "climb " + i);
        }
        context.assertEquals(GlandouilleTowers.height(base), GlandouilleTowers.SPONTANEOUS_MAX, "a tower of 5");
        context.assertEquals(GlandouilleTowers.top(base), all.get(4), "the 5th on top");
        context.complete();
    }

    /** A carried tower put on another one makes it higher than 5; put down on a block, it stands there. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void aCarriedTowerGoesOnTop(TestContext context) {
        floor(context);
        List<GlandouilleEntity> first = tower(context, GlandouilleVariant.CLASSIC, 5, new BlockPos(1, 1, 1));
        List<GlandouilleEntity> second = tower(context, GlandouilleVariant.CLASSIC, 3, new BlockPos(5, 1, 5));
        ServerPlayerEntity player = player(context, new BlockPos(5, 1, 3), 0f);
        context.assertTrue(GlandouilleTowers.pickUp(player, second.getFirst()), "picks up the whole tower");
        context.assertEquals(GlandouilleTowers.carried(player), second.getFirst(), "its bottom one rides the player");
        context.assertTrue(GlandouilleTowers.stackCarriedOn(player, first.get(2)), "stacked on the other tower");
        context.assertEquals(GlandouilleTowers.height(first.getFirst()), 8, "a tower of 8");
        context.assertTrue(GlandouilleTowers.carried(player) == null, "nothing carried any more");
        // and back: picked up again, put down on the ground
        context.assertTrue(GlandouilleTowers.pickUp(player, first.getFirst()), "picks up the tower of 8");
        Vec3d ground = Vec3d.ofBottomCenter(context.getAbsolutePos(new BlockPos(6, 1, 1)));
        context.assertTrue(GlandouilleTowers.putDown(player, ground, 0f), "puts it down");
        context.assertTrue(first.getFirst().getVehicle() == null && first.getFirst().getPos().distanceTo(ground) < 0.01, "standing there");
        context.assertEquals(GlandouilleTowers.height(first.getFirst()), 8, "still 8 high");
        context.complete();
    }

    /**
     * A plain right click with an empty hand on one of a tower picks it up with the ones above it, held in front of the
     * player's chest; the ones below stay standing where they are.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void aRightClickSplitsTheTowerAtTheClickedOne(TestContext context) {
        floor(context);
        List<GlandouilleEntity> members = tower(context, GlandouilleVariant.CLASSIC, 4, new BlockPos(5, 1, 3));
        GlandouilleEntity a = members.get(0), b = members.get(1), c = members.get(2), d = members.get(3);
        a.setAiDisabled(true);
        ServerPlayerEntity player = player(context, new BlockPos(3, 1, 3), -90f); // facing +x
        context.waitAndRun(2, () -> {
            Vec3d aStart = a.getPos();
            context.assertTrue(c.interact(player, Hand.MAIN_HAND).isAccepted(), "a plain right click");
            context.assertEquals(GlandouilleTowers.carried(player), c, "the clicked one is carried");
            context.assertTrue(d.getVehicle() == c, "with the one above it");
            context.assertTrue(b.getVehicle() == a && GlandouilleTowers.height(a) == 2, "the ones below stay a tower of 2");
            context.waitAndRun(2, () -> {
                context.assertTrue(horizontal(a.getPos(), aStart) < 0.05, "they did not move");
                Vec3d held = GlandouilleTowers.heldPos(player, c);
                context.assertTrue(c.getPos().distanceTo(held) < 0.05, "held where it should be: " + c.getPos().distanceTo(held));
                context.assertTrue(c.getX() - player.getX() > 0.4, "in front of the player: " + (c.getX() - player.getX()));
                context.assertTrue(c.getY() < player.getEyeY() - 0.5, "at the chest, not on the head: " + (c.getY() - player.getY()));
                // a lone one is picked up the same way
                GlandouilleEntity lone = glandouille(context, GlandouilleVariant.CLASSIC, new BlockPos(1, 1, 1));
                ServerPlayerEntity other = player(context, new BlockPos(2, 1, 1), 90f);
                context.assertTrue(lone.interact(other, Hand.MAIN_HAND).isAccepted(), "a right click on a lone one");
                context.assertEquals(GlandouilleTowers.carried(other), lone, "carried");
                context.complete();
            });
        });
    }

    /** With a stack in hand, a right click on another tower (any of it) stacks the carried one on top of it. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void aRightClickWithAStackPutsItOnAnotherTower(TestContext context) {
        floor(context);
        List<GlandouilleEntity> carried = tower(context, GlandouilleVariant.CLASSIC, 2, new BlockPos(1, 1, 1));
        List<GlandouilleEntity> target = tower(context, GlandouilleVariant.CLASSIC, 3, new BlockPos(5, 1, 5));
        target.getFirst().setAiDisabled(true);
        ServerPlayerEntity player = player(context, new BlockPos(5, 1, 3), 0f);
        context.assertTrue(carried.getFirst().interact(player, Hand.MAIN_HAND).isAccepted(), "picks up the tower of 2");
        context.waitAndRun(2, () -> {
            context.assertTrue(target.getFirst().interact(player, Hand.MAIN_HAND).isAccepted(), "right click on the other tower");
            context.assertTrue(GlandouilleTowers.carried(player) == null, "nothing carried any more");
            context.assertEquals(GlandouilleTowers.height(target.getFirst()), 5, "a tower of 5");
            context.assertTrue(GlandouilleTowers.top(target.getFirst()) == carried.get(1), "the carried one on top, in order");
            context.complete();
        });
    }

    /** A carrier hit by anyone drops the stack on the ground in front of him, still stacked. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void aHitCarrierDropsTheStack(TestContext context) {
        floor(context);
        List<GlandouilleEntity> members = tower(context, GlandouilleVariant.CLASSIC, 2, new BlockPos(1, 1, 1));
        ServerPlayerEntity player = player(context, new BlockPos(3, 1, 3), -90f); // facing +x
        player.changeGameMode(GameMode.SURVIVAL);
        PigEntity pig = context.spawnMob(EntityType.PIG, new BlockPos(1, 1, 5));
        members.getFirst().setAiDisabled(true);
        context.assertTrue(GlandouilleTowers.pickUp(player, members.getFirst()), "carried");
        context.waitAndRun(2, () -> {
            player.damage(context.getWorld().getDamageSources().mobAttack(pig), 1f);
            context.assertTrue(GlandouilleTowers.carried(player) == null, "dropped");
            GlandouilleEntity bottom = members.getFirst();
            context.assertTrue(bottom.getVehicle() == null, "on its own feet");
            context.assertEquals(GlandouilleTowers.height(bottom), 2, "still stacked");
            context.waitAndRun(10, () -> {
                context.assertTrue(bottom.isOnGround(), "on the ground");
                context.assertTrue(bottom.getX() - player.getX() > 0.3 && horizontal(bottom.getPos(), player.getPos()) < 1.2,
                        "in front of him: " + (bottom.getX() - player.getX()));
                context.complete();
            });
        });
    }

    /** Two in hand, both thrown one after the other: the second (back in the hands after the first went) flies too. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 160)
    public void theOneLeftInHandAfterAThrowFliesToo(TestContext context) {
        floor(context);
        List<GlandouilleEntity> members = tower(context, GlandouilleVariant.CLASSIC, 2, new BlockPos(1, 1, 1));
        GlandouilleEntity second = members.get(1);
        ServerPlayerEntity player = player(context, new BlockPos(1, 1, 3), -90f); // facing +x
        context.assertTrue(GlandouilleTowers.pickUp(player, members.getFirst()), "carried");
        context.waitAndRun(2, () -> {
            context.assertTrue(GlandouilleTowers.throwCarried(player), "first thrown");
            context.waitAndRun(4, () -> {
                double startX = second.getX();
                context.assertTrue(GlandouilleTowers.throwCarried(player), "second thrown");
                context.assertEquals(second.getMood(), Mood.FLYING, "it flies: " + second.getMood());
                context.waitAndRun(5, () -> {
                    context.assertTrue(second.getX() - startX > 2, "flew forward: " + (second.getX() - startX) + " "
                            + second.getMood() + " vehicle " + second.getVehicle() + " noGravity " + second.hasNoGravity());
                    context.waitAndRun(120, () -> {
                        context.assertFalse(second.hasNoGravity(), "falls again");
                        context.complete();
                    });
                });
            });
        });
    }

    /** Thrown the same way after the first one landed (dizzy), the second does not climb on it. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void aSecondThrowDoesNotStackOnTheDizzyFirst(TestContext context) {
        floor(context);
        List<GlandouilleEntity> members = tower(context, GlandouilleVariant.CLASSIC, 2, new BlockPos(1, 1, 1));
        GlandouilleEntity first = members.getFirst(), second = members.get(1);
        ServerPlayerEntity player = player(context, new BlockPos(1, 1, 3), -90f); // facing +x
        context.assertTrue(GlandouilleTowers.pickUp(player, first), "carried");
        context.waitAndRun(2, () -> {
            GlandouilleTowers.throwCarried(player);
            context.waitAndRun(40, () -> {
                context.assertEquals(first.getMood(), Mood.STUNNED, "the first landed dizzy: " + first.getMood());
                // the second right next to it, thrown at it
                second.stopRiding();
                second.refreshPositionAndAngles(first.getX() - 0.6, first.getY() + 0.2, first.getZ(), -90f, 0);
                second.launch(new Vec3d(1, 0, 0));
                context.waitAndRun(6, () -> {
                    context.assertTrue(second.getVehicle() == null && !GlandouilleTowers.hasRider(first), "not stacked on the dizzy one");
                    context.complete();
                });
            });
        });
    }

    /** Picked up in the middle of its flight: it does not keep floating once put down. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void aFlightCutShortGivesGravityBack(TestContext context) {
        floor(context);
        GlandouilleEntity one = tower(context, GlandouilleVariant.CLASSIC, 1, new BlockPos(1, 2, 1)).getFirst();
        ServerPlayerEntity player = player(context, new BlockPos(1, 1, 3), -90f);
        one.launch(new Vec3d(1, 0, 0));
        context.assertTrue(one.hasNoGravity(), "floats while flying");
        context.waitAndRun(2, () -> {
            context.assertTrue(GlandouilleTowers.pickUp(player, one), "caught in flight");
            context.waitAndRun(2, () -> {
                context.assertFalse(one.hasNoGravity(), "gravity back once caught");
                context.complete();
            });
        });
    }

    /** The last one in hand, alone, is thrown like the others: it flies forward, lands, and walks again. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 160)
    public void theLastOneInHandIsThrownToo(TestContext context) {
        floor(context);
        List<GlandouilleEntity> members = tower(context, GlandouilleVariant.CLASSIC, 1, new BlockPos(1, 1, 1));
        GlandouilleEntity one = members.getFirst();
        ServerPlayerEntity player = player(context, new BlockPos(1, 1, 3), -90f); // facing +x
        context.assertTrue(GlandouilleTowers.pickUp(player, one), "carried");
        context.waitAndRun(2, () -> {
            double startX = player.getX();
            context.assertTrue(GlandouilleTowers.throwCarried(player), "thrown");
            context.assertTrue(one.getVehicle() == null, "out of the hands");
            context.assertEquals(one.getMood(), Mood.FLYING, "it flies: " + one.getMood());
            context.waitAndRun(5, () -> {
                context.assertTrue(one.getX() - startX > 2, "flew forward: " + (one.getX() - startX) + " " + one.getMood());
                context.waitAndRun(120, () -> {
                    context.assertFalse(one.hasNoGravity(), "falls again");
                    context.assertTrue(one.getMood() != Mood.FLYING && one.getMood() != Mood.STUNNED, "over it: " + one.getMood());
                    context.complete();
                });
            });
        });
    }

    /** Picked up asleep and thrown: it is woken up in the hands, flies, and lands like any other (never frozen asleep). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 160)
    public void aSleepingOneIsWokenAndThrown(TestContext context) {
        floor(context);
        GlandouilleEntity one = tower(context, GlandouilleVariant.CLASSIC, 1, new BlockPos(1, 1, 1)).getFirst();
        one.fallAsleep(2000);
        context.assertTrue(one.isSleeping(), "asleep");
        ServerPlayerEntity player = player(context, new BlockPos(1, 1, 3), -90f); // facing +x
        context.assertTrue(GlandouilleTowers.pickUp(player, one), "carried");
        context.assertFalse(one.isSleeping(), "woken up by the hands");
        context.waitAndRun(2, () -> {
            one.fallAsleep(2000);
            context.assertFalse(one.isSleeping(), "can't fall asleep in the hands");
            double startX = player.getX();
            context.assertTrue(GlandouilleTowers.throwCarried(player), "thrown");
            context.assertEquals(one.getMood(), Mood.FLYING, "it flies: " + one.getMood());
            context.waitAndRun(5, () -> {
                context.assertTrue(one.getX() - startX > 2, "flew forward: " + (one.getX() - startX) + " " + one.getMood());
                context.complete();
            });
        });
    }

    /** A flying one never drops off in mid-air: it lands, and falls again. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 160)
    public void aThrownOneCanNotFallAsleepInFlight(TestContext context) {
        floor(context);
        GlandouilleEntity one = tower(context, GlandouilleVariant.CLASSIC, 1, new BlockPos(1, 3, 1)).getFirst();
        one.launch(new Vec3d(1, 0, 0));
        one.fallAsleep(2000);
        context.assertEquals(one.getMood(), Mood.FLYING, "still flying: " + one.getMood());
        context.waitAndRun(60, () -> {
            context.assertFalse(one.hasNoGravity(), "falls again");
            context.assertTrue(one.isOnGround(), "landed: y " + one.getY());
            context.complete();
        });
    }

    /** Thrown into a napping one, it lands on top of it and wakes it up: a tower never stays stuck under a sleeper. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void aOneLandingOnASleeperWakesItUp(TestContext context) {
        floor(context);
        GlandouilleEntity sleeper = tower(context, GlandouilleVariant.CLASSIC, 1, new BlockPos(4, 1, 1)).getFirst();
        sleeper.fallAsleep(2000);
        context.assertTrue(sleeper.isSleeping(), "asleep");
        GlandouilleEntity shot = tower(context, GlandouilleVariant.CLASSIC, 1, new BlockPos(1, 1, 1)).getFirst();
        shot.launch(new Vec3d(1, 0, 0));
        context.waitAndRun(10, () -> {
            context.assertEquals(shot.getVehicle(), sleeper, "landed on it");
            context.assertFalse(sleeper.isSleeping(), "woken up: " + sleeper.getMood());
            context.complete();
        });
    }

    /** Put down, it stays awake a while: a handled one does not drop off at once, at night on a bare platform. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void aPutDownOneStaysAwake(TestContext context) {
        floor(context);
        GlandouilleEntity one = tower(context, GlandouilleVariant.CLASSIC, 1, new BlockPos(1, 1, 1)).getFirst();
        ServerPlayerEntity player = player(context, new BlockPos(1, 1, 3), -90f);
        context.assertTrue(GlandouilleTowers.pickUp(player, one), "carried");
        context.waitAndRun(2, () -> {
            context.assertTrue(GlandouilleTowers.drop(player), "put down");
            context.waitAndRun(2, () -> {
                one.fallAsleep(2000);
                context.assertFalse(one.isSleeping(), "stays awake a while");
                context.complete();
            });
        });
    }

    /**
     * A left click with a stack in hand throws its bottom one forward, shot like a flicked one; the rest stays in hand,
     * one shorter, and the thrower is never hit by it.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void aLeftClickThrowsTheBottomOne(TestContext context) {
        floor(context);
        List<GlandouilleEntity> members = tower(context, GlandouilleVariant.CLASSIC, 3, new BlockPos(1, 1, 1));
        ServerPlayerEntity player = player(context, new BlockPos(1, 1, 3), -90f); // facing +x
        context.assertTrue(GlandouilleTowers.pickUp(player, members.getFirst()), "carried");
        context.waitAndRun(2, () -> {
            GlandouilleEntity thrown = members.getFirst();
            double startX = player.getX();
            context.assertTrue(GlandouilleTowers.throwCarried(player), "thrown");
            context.assertEquals(thrown.getMood(), Mood.FLYING, "it flies");
            context.assertEquals(GlandouilleTowers.carried(player), members.get(1), "the next one is now at the bottom of the hands");
            context.assertEquals(GlandouilleTowers.height(members.get(1)), 2, "one shorter");
            context.waitAndRun(5, () -> {
                context.assertTrue(thrown.getX() - startX > 2, "flew forward: " + (thrown.getX() - startX));
                context.assertEquals(GlandouilleTowers.height(members.get(1)), 2, "the rest still in hand");
                context.assertFalse(player.hasStatusEffect(ModEffects.DAZED), "the thrower is not dazed");
                GlandouilleTowers.throwCarried(player);
                GlandouilleTowers.throwCarried(player);
                context.assertTrue(GlandouilleTowers.carried(player) == null, "the last one thrown: empty hands");
                context.complete();
            });
        });
    }

    /** A tower holds while it walks; it falls apart, all dizzy, only when its bottom one charges into a wall. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 140)
    public void aTowerOnlyFallsWhenItsBottomChargesIntoAWall(TestContext context) {
        floor(context);
        for (int z = 0; z < 8; z++) for (int y = 1; y < 4; y++) context.setBlockState(new BlockPos(7, y, z), Blocks.STONE);
        List<GlandouilleEntity> members = tower(context, GlandouilleVariant.CLASSIC, 3, new BlockPos(2, 1, 3));
        GlandouilleEntity bottom = members.getFirst();
        // shoved and walking around: it holds
        bottom.takeKnockback(0.8, 0, 1);
        context.waitAndRun(40, () -> {
            context.assertEquals(GlandouilleTowers.height(bottom), 3, "still a tower of 3");
            BlockPos abs = context.getAbsolutePos(new BlockPos(3, 1, 3));
            bottom.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0, 0);
            context.assertTrue(bottom.startCharge(new Vec3d(1, 0, 0)), "the bottom one charges");
            context.waitAndRun(30, () -> {
                for (GlandouilleEntity one : members) {
                    context.assertTrue(one.getVehicle() == null, "fallen off");
                    context.assertEquals(one.getMood(), Mood.STUNNED, "dizzy");
                }
                context.complete();
            });
        });
    }

    /**
     * A flick on one inside a tower shoots it out along the blow, alone: the ones above hop straight up and come back
     * down onto the one below; the one below is not pushed.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void aFlickShootsOneOutOfTheTower(TestContext context) {
        floor(context);
        List<GlandouilleEntity> members = tower(context, GlandouilleVariant.CLASSIC, 4, new BlockPos(1, 1, 3));
        GlandouilleEntity a = members.get(0), b = members.get(1), c = members.get(2), d = members.get(3);
        a.setAiDisabled(true);
        ServerPlayerEntity player = player(context, new BlockPos(0, 1, 3), -90f); // facing +x
        context.waitAndRun(2, () -> {
            double startX = b.getX();
            Vec3d aStart = a.getPos(), cStart = c.getPos();
            b.onHit(player, player);
            context.assertTrue(b.getVehicle() == null, "b is out");
            context.assertEquals(b.getMood(), Mood.FLYING, "b flies");
            context.assertTrue(c.getVehicle() == null && d.getVehicle() == c, "c (with d on it) let go of b");
            context.assertEquals(c.getMood(), Mood.HOPPING, "c hops");
            context.assertTrue(c.hopOnto() == a, "c comes back down onto a");
            context.assertFalse(GlandouilleTowers.hasRider(a), "nobody on a for now");
            double[] highest = {c.getY()};
            double[] drift = {0};
            context.runAtEveryTick(() -> {
                highest[0] = Math.max(highest[0], c.getY());
                drift[0] = Math.max(drift[0], horizontal(c.getPos(), cStart));
            });
            context.waitAndRun(40, () -> {
                context.assertTrue(b.getX() - startX > 2, "b flew along the blow: " + (b.getX() - startX));
                context.assertEquals(b.getMood(), Mood.STUNNED, "b landed dizzy");
                context.assertTrue(highest[0] - cStart.y > 0.3, "c hopped up: " + (highest[0] - cStart.y));
                context.assertTrue(drift[0] < 0.1, "straight up and down: " + drift[0]);
                context.assertTrue(c.getVehicle() == a && d.getVehicle() == c, "c and d back on a");
                context.assertEquals(GlandouilleTowers.height(a), 3, "a tower of 3 now");
                context.assertTrue(horizontal(a.getPos(), aStart) < 0.05, "a was not pushed: " + horizontal(a.getPos(), aStart));
                context.complete();
            });
        });
    }

    /** The bottom one hit goes alone too: the tower on it hops off and lands on the ground, still stacked. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void aHitBottomOneLeavesItsTowerBehind(TestContext context) {
        floor(context);
        List<GlandouilleEntity> members = tower(context, GlandouilleVariant.FROSTY, 3, new BlockPos(2, 1, 3));
        GlandouilleEntity a = members.get(0), b = members.get(1), c = members.get(2);
        ServerPlayerEntity player = player(context, new BlockPos(1, 1, 3), -90f); // facing +x
        context.waitAndRun(2, () -> {
            Vec3d start = a.getPos();
            a.onHit(player, player);
            context.assertEquals(a.getMood(), Mood.SLIDING, "a slides away");
            context.assertTrue(b.getVehicle() == null && c.getVehicle() == b, "b (with c on it) let go of a");
            context.assertEquals(b.getMood(), Mood.HOPPING, "b hops");
            context.assertTrue(b.hopOnto() == null, "nothing below: b comes down on the ground");
            context.waitAndRun(40, () -> {
                context.assertTrue(a.getX() - start.x > 1.5, "a slid off alone: " + (a.getX() - start.x));
                context.assertTrue(b.getVehicle() == null && b.isOnGround(), "b on the ground");
                context.assertTrue(horizontal(b.getPos(), start) < 0.1, "b where the tower was: " + horizontal(b.getPos(), start));
                context.assertTrue(c.getVehicle() == b, "c still on b");
                context.assertEquals(b.getMood(), Mood.CALM, "b calm again");
                context.assertFalse(GlandouilleTowers.hasRider(a), "nobody on a");
                context.complete();
            });
        });
    }

    // ---------------------------------------------------------------- impacts

    /** One shot out of a tower into another tower lands on top of it: that tower is one higher, and not pushed. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void aShotOneLandsOnTheTowerItHits(TestContext context) {
        floor(context);
        List<GlandouilleEntity> shooterTower = tower(context, GlandouilleVariant.CLASSIC, 2, new BlockPos(1, 1, 3));
        List<GlandouilleEntity> target = tower(context, GlandouilleVariant.CLASSIC, 2, new BlockPos(4, 1, 3));
        shooterTower.getFirst().setAiDisabled(true);
        target.getFirst().setAiDisabled(true);
        GlandouilleEntity shot = shooterTower.get(1);
        ServerPlayerEntity player = player(context, new BlockPos(0, 1, 3), -90f); // facing +x
        context.waitAndRun(2, () -> {
            Vec3d targetStart = target.getFirst().getPos();
            shot.onHit(player, player);
            context.assertEquals(shot.getMood(), Mood.FLYING, "shot out of its tower");
            context.waitAndRun(20, () -> {
                context.assertEquals(GlandouilleTowers.height(target.getFirst()), 3, "the tower it hit is one higher");
                context.assertTrue(GlandouilleTowers.top(target.getFirst()) == shot, "the shot one on top");
                context.assertEquals(shot.getMood(), Mood.CALM, "calm up there");
                context.assertTrue(horizontal(target.getFirst().getPos(), targetStart) < 0.1,
                        "the tower was not pushed: " + horizontal(target.getFirst().getPos(), targetStart));
                context.complete();
            });
        });
    }

    /** A sliding one into a lone one: it carries it on top and slides on, a little slower. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void aSlidingOneCarriesALoneOne(TestContext context) {
        floor(context);
        GlandouilleEntity frosty = glandouille(context, GlandouilleVariant.FROSTY, new BlockPos(1, 1, 3));
        GlandouilleEntity lone = glandouille(context, GlandouilleVariant.CLASSIC, new BlockPos(3, 1, 3));
        lone.setAiDisabled(true);
        frosty.startSlide(new Vec3d(0.4, 0, 0));
        assertCarriedAlong(context, frosty, List.of(lone), 0.4);
    }

    /** A sliding one into a tower: the whole tower climbs on it, in order, and it slides on, slower still. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void aSlidingOneCarriesATower(TestContext context) {
        floor(context);
        GlandouilleEntity frosty = glandouille(context, GlandouilleVariant.FROSTY, new BlockPos(1, 1, 3));
        List<GlandouilleEntity> members = tower(context, GlandouilleVariant.CLASSIC, 2, new BlockPos(3, 1, 3));
        members.getFirst().setAiDisabled(true);
        frosty.startSlide(new Vec3d(0.4, 0, 0));
        assertCarriedAlong(context, frosty, members, 0.4);
    }

    /** {@code carried} (bottom first) ended up stacked on the sliding {@code frosty}, which keeps sliding, slower. */
    private static void assertCarriedAlong(TestContext context, GlandouilleEntity frosty, List<GlandouilleEntity> carried, double speed) {
        context.waitAndRun(10, () -> {
            context.assertEquals(GlandouilleTowers.height(frosty), 1 + carried.size(), "stacked on the sliding one");
            context.assertTrue(GlandouilleTowers.bottom(carried.getFirst()) == frosty, "the sliding one at the bottom");
            for (int i = 0; i < carried.size(); i++) {
                context.assertEquals(GlandouilleTowers.level(carried.get(i)), i + 1, "in order: " + i);
            }
            context.assertEquals(frosty.getMood(), Mood.SLIDING, "still sliding");
            double now = frosty.slideVelocity().horizontalLength();
            double max = speed * Math.pow(GlandouilleEntity.CARRY_SLOWDOWN, carried.size()) + 1.0E-3;
            context.assertTrue(now > 0.05 && now < max, "slower per acorn: " + now + " / " + max);
            double x = frosty.getX();
            context.waitAndRun(3, () -> {
                context.assertTrue(frosty.getX() - x > 0.1, "it keeps moving: " + (frosty.getX() - x));
                context.assertEquals(GlandouilleTowers.height(frosty), 1 + carried.size(), "the tower rides on");
                context.complete();
            });
        });
    }

    /** The old mossy one is too heavy to carry: a sliding one hitting its tower stops and climbs on top of it. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void aSlidingOneStopsOnAMossyTower(TestContext context) {
        floor(context);
        GlandouilleEntity frosty = glandouille(context, GlandouilleVariant.FROSTY, new BlockPos(1, 1, 3));
        List<GlandouilleEntity> members = tower(context, GlandouilleVariant.MOSSY, 2, new BlockPos(4, 1, 3));
        GlandouilleEntity mossy = members.getFirst();
        Vec3d start = mossy.getPos();
        frosty.startSlide(new Vec3d(0.4, 0, 0));
        context.waitAndRun(20, () -> {
            context.assertEquals(GlandouilleTowers.height(mossy), 3, "the slider on the mossy tower");
            context.assertTrue(GlandouilleTowers.top(mossy) == frosty, "on top");
            context.assertTrue(frosty.getMood() != Mood.SLIDING, "no longer sliding");
            context.assertTrue(mossy.getPos().distanceTo(start) < 0.05, "the mossy one did not move: " + mossy.getPos().distanceTo(start));
            context.complete();
        });
    }

    // ---------------------------------------------------------------- dazed players

    /** A charge into a player: dazed on the spot (no walking, no jumping) for about 2 s, not hurt. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
    public void aChargeDazesAPlayer(TestContext context) {
        floor(context);
        GlandouilleEntity glandouille = glandouille(context, GlandouilleVariant.CLASSIC, new BlockPos(1, 1, 3));
        ServerPlayerEntity player = player(context, new BlockPos(4, 1, 3), 90f);
        context.assertTrue(glandouille.startCharge(new Vec3d(1, 0, 0)), "charges");
        assertDazed(context, player);
    }

    /** A frosty one sliding into a player: dazed the same way. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
    public void aSlidingOneDazesAPlayer(TestContext context) {
        floor(context);
        GlandouilleEntity frosty = glandouille(context, GlandouilleVariant.FROSTY, new BlockPos(1, 1, 3));
        ServerPlayerEntity player = player(context, new BlockPos(5, 1, 3), 90f);
        frosty.startSlide(new Vec3d(0.6, 0, 0));
        assertDazed(context, player);
    }

    /** One shot out of a tower flying into a player: dazed the same way. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
    public void aShotOneDazesAPlayer(TestContext context) {
        floor(context);
        List<GlandouilleEntity> members = tower(context, GlandouilleVariant.CLASSIC, 3, new BlockPos(1, 1, 3));
        members.getFirst().setAiDisabled(true);
        ServerPlayerEntity shooter = player(context, new BlockPos(0, 1, 3), -90f); // facing +x
        ServerPlayerEntity player = player(context, new BlockPos(5, 1, 3), 90f);
        context.waitAndRun(2, () -> {
            members.get(1).onHit(shooter, shooter);
            context.assertEquals(members.get(1).getMood(), Mood.FLYING, "shot out of the tower");
            context.assertFalse(shooter.hasStatusEffect(ModEffects.DAZED), "the shooter is not dazed");
            assertDazed(context, player);
        });
    }

    /**
     * Waits for {@code player} to be dazed: its movement speed and jump strength at 0 for {@link DazedEffect#TICKS},
     * not hurt, then free again.
     */
    private static void assertDazed(TestContext context, ServerPlayerEntity player) {
        float health = player.getHealth();
        context.waitAndRun(30, () -> {
            StatusEffectInstance dazed = player.getStatusEffect(ModEffects.DAZED);
            context.assertTrue(dazed != null, "the player is dazed");
            context.assertTrue(dazed.getDuration() > 10 && dazed.getDuration() <= DazedEffect.TICKS, "for about 2 s: " + dazed.getDuration());
            context.assertTrue(player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) < 1.0E-6, "cannot walk");
            context.assertTrue(player.getAttributeValue(EntityAttributes.GENERIC_JUMP_STRENGTH) < 1.0E-6, "cannot jump");
            context.assertTrue(player.getVelocity().horizontalLengthSquared() < 1.0E-6, "rooted to the spot, not thrown");
            context.assertEquals(player.getHealth(), health, "not hurt");
            context.waitAndRun(DazedEffect.TICKS + 5, () -> {
                context.assertFalse(player.hasStatusEffect(ModEffects.DAZED), "free again");
                context.assertTrue(player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) > 0.05, "walks again");
                context.assertEquals(player.getHealth(), health, "never hurt");
                context.complete();
            });
        });
    }

    // ---------------------------------------------------------------- variants

    /** The old mossy one never charges, and a tower on it stays where it is. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 140)
    public void theMossyOneIsAnAnchor(TestContext context) {
        floor(context);
        List<GlandouilleEntity> members = tower(context, GlandouilleVariant.MOSSY, 3, new BlockPos(3, 1, 3));
        GlandouilleEntity mossy = members.getFirst();
        Vec3d start = mossy.getPos();
        context.assertFalse(mossy.startCharge(new Vec3d(1, 0, 0)), "never charges");
        PigEntity pig = context.spawnMob(EntityType.PIG, new BlockPos(5, 1, 3));
        context.assertFalse(mossy.startTelegraph(pig), "never even warns");
        mossy.takeKnockback(1.5, 1, 0);
        context.waitAndRun(100, () -> {
            context.assertTrue(mossy.getPos().distanceTo(start) < 0.05, "it did not move: " + mossy.getPos().distanceTo(start));
            context.assertEquals(GlandouilleTowers.height(mossy), 3, "the tower is there");
            context.complete();
        });
    }

    /** The frosty one, hit, slides like a curling stone, bounces off a wall and goes back the other way. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
    public void theFrostyOneSlidesAndBouncesOffWalls(TestContext context) {
        floor(context);
        for (int z = 0; z < 8; z++) for (int y = 1; y < 3; y++) context.setBlockState(new BlockPos(6, y, z), Blocks.STONE);
        GlandouilleEntity frosty = glandouille(context, GlandouilleVariant.FROSTY, new BlockPos(2, 1, 3));
        ServerPlayerEntity player = player(context, new BlockPos(1, 1, 3), -90f); // facing +x
        double startX = frosty.getX();
        frosty.onHit(player, player);
        context.assertEquals(frosty.getMood(), Mood.SLIDING, "slides");
        double[] farthest = {startX};
        context.runAtEveryTick(() -> farthest[0] = Math.max(farthest[0], frosty.getX()));
        context.waitAndRun(60, () -> {
            context.assertTrue(farthest[0] - startX > 2, "slid to the wall: " + (farthest[0] - startX));
            context.assertTrue(frosty.getX() < farthest[0] - 0.3 || frosty.slideVelocity().x < 0,
                    "went back from the wall: " + frosty.getX() + " / " + farthest[0]);
            context.complete();
        });
    }

    // ---------------------------------------------------------------- hat

    /** Its cap never flies off while it carries a tower; alone, it does (the Acorn Hat drops). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void neverLosesItsCapUnderATower(TestContext context) {
        floor(context);
        List<GlandouilleEntity> members = tower(context, GlandouilleVariant.CLASSIC, 2, new BlockPos(2, 1, 2));
        GlandouilleEntity bottom = members.getFirst();
        GlandouilleEntity alone = glandouille(context, GlandouilleVariant.CLASSIC, new BlockPos(5, 1, 5));
        bottom.popHat();
        alone.popHat();
        context.waitAndRun(20, () -> {
            context.assertTrue(bottom.hasHat(), "kept its cap under the tower");
            context.assertFalse(alone.hasHat(), "lost its cap when alone");
            Box around = new Box(context.getAbsolutePos(BlockPos.ORIGIN)).expand(8);
            context.assertTrue(!context.getWorld().getEntitiesByClass(ItemEntity.class, around,
                    item -> item.getStack().isOf(ModItems.ACORN_HAT)).isEmpty(), "the Acorn Hat dropped");
            context.complete();
        });
    }

    // ---------------------------------------------------------------- the acorn

    /** An acorn planted on farmland grows, and ripe, hatches into a young Glandouille. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void aPlantedAcornHatchesIntoAClassicOne(TestContext context) {
        floor(context);
        biome(context, "minecraft:plains");
        BlockPos farmland = new BlockPos(3, 1, 3), crop = farmland.up();
        context.setBlockState(farmland, Blocks.FARMLAND.getDefaultState().with(Properties.MOISTURE, 7));
        BlockState planted = ModBlocks.ACORN_CROP.getDefaultState();
        context.assertTrue(planted.canPlaceAt(context.getWorld(), context.getAbsolutePos(crop)), "plants on farmland");
        context.assertFalse(planted.canPlaceAt(context.getWorld(), context.getAbsolutePos(new BlockPos(5, 1, 5))), "not on stone");
        context.setBlockState(crop, planted.with(AcornCropBlock.AGE, AcornCropBlock.MAX_AGE));
        BlockPos abs = context.getAbsolutePos(crop);
        // The world only ticks blocks that ask for it: a ripe crop must still ask
        context.assertTrue(context.getWorld().getBlockState(abs).hasRandomTicks(), "ripe, it still ticks");
        for (int i = 0; i < 64 && context.getWorld().getBlockState(abs).isOf(ModBlocks.ACORN_CROP); i++) {
            context.getWorld().getBlockState(abs).randomTick(context.getWorld(), abs, context.getWorld().random);
        }
        context.assertTrue(context.getWorld().getBlockState(abs).isAir(), "the acorn hatched");
        List<GlandouilleEntity> born = context.getWorld().getEntitiesByClass(GlandouilleEntity.class, new Box(abs).expand(1), e -> true);
        context.assertEquals(born.size(), 1, "one Glandouille");
        context.assertEquals(born.getFirst().getVariant(), GlandouilleVariant.CLASSIC, "a classic one");
        context.complete();
    }

    /** A ripe acorn hatches into its biome's kind: frosty where it snows, the old mossy one in taigas and lush caves. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void aPlantedAcornHatchesIntoItsBiomesKind(TestContext context) {
        Map<String, GlandouilleVariant> expected = new java.util.LinkedHashMap<>();
        expected.put("minecraft:plains", GlandouilleVariant.CLASSIC);
        expected.put("minecraft:forest", GlandouilleVariant.CLASSIC);
        expected.put("minecraft:snowy_plains", GlandouilleVariant.FROSTY);
        expected.put("minecraft:snowy_taiga", GlandouilleVariant.FROSTY);
        expected.put("minecraft:grove", GlandouilleVariant.FROSTY);
        expected.put("minecraft:taiga", GlandouilleVariant.MOSSY);
        expected.put("minecraft:old_growth_spruce_taiga", GlandouilleVariant.MOSSY);
        expected.put("minecraft:lush_caves", GlandouilleVariant.MOSSY);
        BlockPos abs = context.getAbsolutePos(new BlockPos(3, 1, 3));
        expected.forEach((biome, variant) -> {
            biome(context, biome);
            context.getWorld().setBlockState(abs, ModBlocks.ACORN_CROP.getDefaultState().with(AcornCropBlock.AGE, AcornCropBlock.MAX_AGE));
            GlandouilleEntity born = AcornCropBlock.hatch(context.getWorld(), abs);
            context.assertTrue(born != null, "hatched in " + biome);
            context.assertEquals(born.getVariant(), variant, "in " + biome);
            born.discard();
        });
        context.complete();
    }

    /** Barely sprouted, an acorn may pop out of the ground at once, as a young one (5 %: forced here). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void aBarelySproutedAcornMayPopOutYoung(TestContext context) {
        floor(context);
        biome(context, "minecraft:snowy_plains");
        BlockPos farmland = new BlockPos(3, 1, 3), crop = farmland.up();
        context.setBlockState(farmland, Blocks.FARMLAND.getDefaultState().with(Properties.MOISTURE, 7));
        BlockPos abs = context.getAbsolutePos(crop);
        var world = context.getWorld();
        float chance = AcornCropBlock.earlyPopChance;
        try {
            AcornCropBlock.earlyPopChance = 0f;
            context.setBlockState(crop, ModBlocks.ACORN_CROP.getDefaultState());
            net.minecraft.item.BoneMealItem.useOnFertilizable(new net.minecraft.item.ItemStack(net.minecraft.item.Items.BONE_MEAL), world, abs);
            context.assertEquals(world.getBlockState(abs).get(AcornCropBlock.AGE), 1, "never: it just grows");
            AcornCropBlock.earlyPopChance = 1f;
            net.minecraft.item.BoneMealItem.useOnFertilizable(new net.minecraft.item.ItemStack(net.minecraft.item.Items.BONE_MEAL), world, abs);
            context.assertEquals(world.getBlockState(abs).get(AcornCropBlock.AGE), 2, "only at its first stage");
            context.setBlockState(crop, ModBlocks.ACORN_CROP.getDefaultState());
            net.minecraft.item.BoneMealItem.useOnFertilizable(new net.minecraft.item.ItemStack(net.minecraft.item.Items.BONE_MEAL), world, abs);
        } finally {
            AcornCropBlock.earlyPopChance = chance;
        }
        context.assertTrue(world.getBlockState(abs).isAir(), "popped out");
        List<GlandouilleEntity> born = world.getEntitiesByClass(GlandouilleEntity.class, new Box(abs).expand(1), e -> true);
        context.assertEquals(born.size(), 1, "one Glandouille");
        context.assertEquals(born.getFirst().getVariant(), GlandouilleVariant.YOUNG, "a young one, whatever the biome");
        context.complete();
    }

    /** The young one never spawns by itself: only from an acorn barely sprouted, its egg or a command. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 20)
    public void theYoungOneNeverSpawnsNaturally(TestContext context) {
        biome(context, "minecraft:forest");
        BlockPos abs = context.getAbsolutePos(new BlockPos(3, 1, 3));
        for (int i = 0; i < 2000; i++) {
            context.assertTrue(GlandouilleSpawns.variantFor(context.getWorld(), abs, context.getWorld().random) != GlandouilleVariant.YOUNG,
                    "never a young one");
        }
        context.complete();
    }

    /** Sets the biome of the whole test area (and a little around it). */
    private static void biome(TestContext context, String biome) {
        BlockPos from = context.getAbsolutePos(new BlockPos(-2, -2, -2)), to = context.getAbsolutePos(new BlockPos(10, 6, 10));
        var server = context.getWorld().getServer();
        server.getCommandManager().executeWithPrefix(server.getCommandSource().withWorld(context.getWorld()).withSilent(),
                "fillbiome " + from.getX() + " " + from.getY() + " " + from.getZ() + " " + to.getX() + " " + to.getY() + " " + to.getZ() + " " + biome);
    }

    /** Bone meal: one stage per dose; ripe, a dose in three hatches it (here, doses until it does). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void boneMealOnARipeAcornHatchesIt(TestContext context) {
        floor(context);
        BlockPos farmland = new BlockPos(3, 1, 3), crop = farmland.up();
        context.setBlockState(farmland, Blocks.FARMLAND.getDefaultState().with(Properties.MOISTURE, 7));
        context.setBlockState(crop, ModBlocks.ACORN_CROP.getDefaultState());
        BlockPos abs = context.getAbsolutePos(crop);
        var world = context.getWorld();
        float chance = AcornCropBlock.earlyPopChance;
        AcornCropBlock.earlyPopChance = 0f; // no early young one here
        try {
            for (int age = 1; age <= AcornCropBlock.MAX_AGE; age++) {
                context.assertTrue(net.minecraft.item.BoneMealItem.useOnFertilizable(new net.minecraft.item.ItemStack(net.minecraft.item.Items.BONE_MEAL), world, abs), "bone meal takes");
                context.assertEquals(world.getBlockState(abs).get(AcornCropBlock.AGE), age, "one stage per dose");
            }
        } finally {
            AcornCropBlock.earlyPopChance = chance;
        }
        for (int i = 0; i < 64 && world.getBlockState(abs).isOf(ModBlocks.ACORN_CROP); i++) {
            net.minecraft.item.BoneMealItem.useOnFertilizable(new net.minecraft.item.ItemStack(net.minecraft.item.Items.BONE_MEAL), world, abs);
        }
        context.assertTrue(world.getBlockState(abs).isAir(), "the acorn hatched");
        context.assertEquals(context.getWorld().getEntitiesByClass(GlandouilleEntity.class, new Box(abs).expand(1), e -> true).size(), 1, "one Glandouille");
        context.complete();
    }

    // ---------------------------------------------------------------- the board space

    private static final List<BlockPos> PATH = List.of(new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1),
            new BlockPos(5, 1, 3));

    /** The path's tiles, each linked to the next; the first holds {@code first}. */
    private static BoardSpaceBlockEntity path(TestContext context, ItemStack first) {
        BoardSpaceBlockEntity start = null;
        for (int i = 0; i < PATH.size(); i++) {
            BlockPos pos = PATH.get(i);
            context.setBlockState(pos.down(), Blocks.STONE);
            context.setBlockState(pos, ModBlocks.TILE);
            ItemStack cartridge = i == 0 ? first : new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
            List<BlockPos> next = new ArrayList<>();
            if (i + 1 < PATH.size()) next.add(context.getAbsolutePos(PATH.get(i + 1)));
            cartridge.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(next, ""));
            BoardSpaceBlockEntity tile = context.getBlockEntity(pos);
            tile.setStack(0, cartridge);
            if (i == 0) start = tile;
        }
        return start;
    }

    private static PigEntity token(TestContext context, BlockPos on) {
        PigEntity pig = context.spawnMob(EntityType.PIG, on);
        TokenizedEntityInterface token = (TokenizedEntityInterface) pig;
        token.steveparty$setTokenized(true);
        token.steveparty$setStatus(TokenStatus.IN_GAME);
        atEnd(context, () -> token.steveparty$setTokenized(false));
        return pig;
    }

    private static ItemStack cartridge(int distance, boolean lone) {
        ItemStack stack = new ItemStack(ModItems.GLANDOUILLE_CARTRIDGE);
        stack.set(ModComponents.GLANDOUILLE_DISTANCE, distance);
        stack.set(ModComponents.GLANDOUILLE_LONE, lone);
        return stack;
    }

    private static List<GlandouilleEntity> spawned(TestContext context) {
        List<GlandouilleEntity> list = new ArrayList<>();
        Consumer<GlandouilleEntity> listener = list::add;
        GlandouillePushes.SPAWN_LISTENERS.add(listener);
        atEnd(context, () -> GlandouillePushes.SPAWN_LISTENERS.remove(listener));
        return list;
    }

    private static List<GlandouilleEntity> around(TestContext context) {
        return context.getWorld().getEntitiesByClass(GlandouilleEntity.class,
                new Box(context.getAbsolutePos(BlockPos.ORIGIN)).expand(10), e -> true);
    }

    /** A cartridge without destination (0 spaces): nothing happens, no Glandouille. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void aSpaceWithoutDestinationDoesNothing(TestContext context) {
        BoardSpaceBlockEntity tile = path(context, cartridge(0, false));
        List<GlandouilleEntity> spawned = spawned(context);
        PigEntity pig = token(context, PATH.getFirst());
        context.waitAndRun(2, () -> {
            Vec3d before = pig.getPos();
            tile.getBoardSpaceBehavior().onDestinationReached(context.getWorld(), tile.getPos(), pig, tile, null);
            context.assertFalse(GlandouillePushes.isRunning(pig), "nothing running");
            context.waitAndRun(10, () -> {
                context.assertTrue(spawned.isEmpty(), "no Glandouille");
                context.assertTrue(pig.getPos().distanceTo(before) < 0.3, "the token did not move");
                context.complete();
            });
        });
    }

    /** With a destination 2 spaces on: the tower pushes the token and the one it meets there; then it is gone. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 140)
    public void theTowerPushesTheTokensToItsDestination(TestContext context) {
        BoardSpaceBlockEntity tile = path(context, cartridge(2, false));
        List<GlandouilleEntity> spawned = spawned(context);
        PigEntity lander = token(context, PATH.get(0));
        PigEntity met = token(context, PATH.get(1));
        PigEntity beyond = token(context, PATH.get(3));
        context.waitAndRun(2, () -> {
            Vec3d beyondBefore = beyond.getPos();
            tile.getBoardSpaceBehavior().onDestinationReached(context.getWorld(), tile.getPos(), lander, tile, null);
            context.assertTrue(GlandouillePushes.isRunning(lander), "the tower is pushing");
            context.assertTrue(tile.getBoardSpaceBehavior().keepsTurn(lander), "the turn waits for it");
            context.assertTrue(spawned.size() > 1 && spawned.stream().allMatch(GlandouilleEntity::isBoardActor), "a tower of board actors");
            context.assertTrue(spawned.stream().allMatch(GlandouilleEntity::isInvulnerable), "invulnerable");
            int ticks = GlandouillePushes.SETUP_TICKS + 2 * GlandouillePushes.STEP_TICKS + 10;
            context.waitAndRun(ticks, () -> {
                Vec3d destination = BoardSpaces.standPos(context.getWorld(), context.getAbsolutePos(PATH.get(2)));
                context.assertFalse(GlandouillePushes.isRunning(lander), "over");
                context.assertTrue(horizontal(lander.getPos(), destination) < 0.5, "the token is on the destination: " + lander.getPos());
                context.assertTrue(horizontal(met.getPos(), destination) < 0.5, "the token met on the way too: " + met.getPos());
                context.assertTrue(beyond.getPos().distanceTo(beyondBefore) < 0.3, "not the one beyond the destination");
                context.assertTrue(spawned.stream().allMatch(GlandouilleEntity::isRemoved), "the Glandouilles are gone");
                context.assertTrue(around(context).isEmpty(), "none left in the world");
                context.complete();
            });
        });
    }

    /** The lone one: it tries, can't, sulks and goes; the token stays. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 140)
    public void theLoneOneCannotPush(TestContext context) {
        BoardSpaceBlockEntity tile = path(context, cartridge(2, true));
        List<GlandouilleEntity> spawned = spawned(context);
        PigEntity lander = token(context, PATH.get(0));
        context.waitAndRun(2, () -> {
            Vec3d before = lander.getPos();
            tile.getBoardSpaceBehavior().onDestinationReached(context.getWorld(), tile.getPos(), lander, tile, null);
            context.assertEquals(spawned.size(), 1, "a lone Glandouille");
            context.waitAndRun(GlandouillePushes.LONE_TICKS + 10, () -> {
                context.assertTrue(lander.getPos().distanceTo(before) < 0.3, "the token stayed");
                context.assertTrue(around(context).isEmpty(), "gone");
                context.complete();
            });
        });
    }

    private static double horizontal(Vec3d a, Vec3d b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** What to undo when the test ends (a test has one final task: they are run together). */
    private static final Map<TestContext, List<Runnable>> AT_END = new WeakHashMap<>();

    private static void atEnd(TestContext context, Runnable task) {
        List<Runnable> tasks = AT_END.get(context);
        if (tasks == null) {
            List<Runnable> created = new ArrayList<>();
            AT_END.put(context, created);
            context.addFinalTask(() -> created.forEach(Runnable::run));
            tasks = created;
        }
        tasks.add(task);
    }
}
