package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.service.TokenMovementService;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * A token is a static pawn: no AI (no wandering, no looking around, no behaviours), silent, and it only turns to face
 * where the board moves it. Back to a mob, it gets its AI and its voice back.
 */
public class TokenPawnGameTests implements FabricGameTest {
    private static final int STILL_TICKS = 100;

    private static TokenizedEntityInterface token(MobEntity mob) {
        return (TokenizedEntityInterface) mob;
    }

    private static void floor(TestContext context) {
        for (int x = 0; x < 6; x++)
            for (int z = 0; z < 6; z++)
                context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE.getDefaultState());
    }

    private static void assertYaw(TestContext context, float actual, float expected, String message) {
        context.assertTrue(Math.abs(MathHelper.wrapDegrees(actual - expected)) < 0.01F,
                message + ": expected " + expected + ", got " + actual);
    }

    /** Head and body in line with its facing. */
    private static void assertAligned(TestContext context, MobEntity mob, float yaw, String what) {
        assertYaw(context, mob.getYaw(), yaw, what + " yaw");
        assertYaw(context, mob.getHeadYaw(), yaw, what + " head");
        assertYaw(context, mob.getBodyYaw(), yaw, what + " body");
        context.assertTrue(mob.getPitch() == 0, what + " looks straight ahead, pitch " + mob.getPitch());
    }

    /**
     * Tokenizes {@code mob} facing {@code yaw}, with a player right next to it (a live mob would look at them, follow
     * them, get curious...), then checks it stays put, facing the same way, silent, for {@link #STILL_TICKS}.
     */
    private static void assertStaysStill(TestContext context, MobEntity mob, float yaw) {
        assertStaysStill(context, mob, yaw, 2);
    }

    /** @param settleTicks ticks for it to come down onto the ground before it is watched (tokens fall like today) */
    private static void assertStaysStill(TestContext context, MobEntity mob, float yaw, int settleTicks) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.setPosition(mob.getX() + 2, mob.getY(), mob.getZ() + 1);
        TokenMovementService.faceYaw(mob, yaw);
        token(mob).steveparty$setTokenized(true);
        context.waitAndRun(settleTicks, () -> {
            Vec3d start = mob.getPos();
            context.waitAndRun(STILL_TICKS, () -> {
                String what = mob.getType().getUntranslatedName();
                Vec3d now = mob.getPos();
                double moved = Math.sqrt(now.subtract(start).horizontalLengthSquared());
                context.assertTrue(moved < 1.0E-3, what + " token wandered " + moved + " blocks");
                context.assertTrue(Math.abs(now.y - start.y) < 1.0E-3, what + " token drifted up / down");
                assertAligned(context, mob, yaw, what + " token");
                context.assertTrue(mob.isSilent(), what + " token is silent (no ambient, step, hurt sounds)");
                context.assertTrue(mob.isAiDisabled(), what + " token has no AI");
                context.assertTrue(mob.getNavigation().isIdle(), what + " token has no path");
                context.assertTrue(mob.getTarget() == null, what + " token targets nothing");
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = STILL_TICKS + 40)
    public void cowTokenStaysStill(TestContext context) {
        floor(context);
        assertStaysStill(context, context.spawnEntity(EntityType.COW, new BlockPos(2, 2, 2)), 37f);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = STILL_TICKS + 40)
    public void villagerTokenStaysStill(TestContext context) {
        floor(context);
        assertStaysStill(context, context.spawnEntity(EntityType.VILLAGER, new BlockPos(2, 2, 2)), -120f);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = STILL_TICKS + 80)
    public void mulaTokenStaysStill(TestContext context) {
        floor(context);
        // a flying Mula: as a token it comes down onto the board (the tokens' gravity), then stays still
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(2, 3, 2));
        assertStaysStill(context, mula, 75f, 40);
        context.waitAndRun(STILL_TICKS, () -> {
            context.assertFalse(mula.isDancing(), "a Mula token does not dance");
            context.assertFalse(mula.isShaking(), "a Mula token does not tremble");
            context.assertFalse(mula.isResting(), "a Mula token does not go resting");
        });
    }

    /** Along a little L-shaped path: it slides to each place and faces where it goes, head and body together. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
    public void tokenFacesWhereTheBoardMovesIt(TestContext context) {
        floor(context);
        CowEntity cow = context.spawnEntity(EntityType.COW, new BlockPos(1, 2, 1));
        token(cow).steveparty$setTokenized(true);
        BlockPos east = context.getAbsolutePos(new BlockPos(4, 2, 1));
        BlockPos south = context.getAbsolutePos(new BlockPos(4, 2, 4));
        context.waitAndRun(2, () -> {
            TokenMovementService.moveEntity(cow, east);
            context.waitAndRun(20, () -> {
                context.assertTrue(cow.getPos().distanceTo(Vec3d.ofBottomCenter(east)) < 0.05,
                        "reached the first place: " + cow.getPos());
                assertAligned(context, cow, -90f, "going east");
                TokenMovementService.moveEntity(cow, south);
                context.waitAndRun(20, () -> {
                    context.assertTrue(cow.getPos().distanceTo(Vec3d.ofBottomCenter(south)) < 0.05,
                            "reached the second place: " + cow.getPos());
                    assertAligned(context, cow, 0f, "going south");
                    context.waitAndRun(40, () -> {
                        context.assertTrue(cow.getPos().distanceTo(Vec3d.ofBottomCenter(south)) < 0.05,
                                "stays on its place: " + cow.getPos());
                        assertAligned(context, cow, 0f, "keeps facing south");
                        context.complete();
                    });
                });
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tokenDoesNotEatNorFallInLove(TestContext context) {
        floor(context);
        CowEntity cow = context.spawnEntity(EntityType.COW, new BlockPos(2, 2, 2));
        token(cow).steveparty$setTokenized(true);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WHEAT));
        context.assertTrue(cow.interact(player, Hand.MAIN_HAND) == ActionResult.PASS, "wheat does nothing to a token");
        context.assertFalse(cow.isInLove(), "a token does not fall in love");
        VillagerEntity villager = context.spawnEntity(EntityType.VILLAGER, new BlockPos(4, 2, 2));
        token(villager).steveparty$setTokenized(true);
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        context.assertTrue(villager.interact(player, Hand.MAIN_HAND) == ActionResult.PASS, "a villager token does not trade");
        context.assertFalse(villager.hasCustomer(), "no trade screen");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void untokenizedMobGetsItsLifeBack(TestContext context) {
        floor(context);
        CowEntity cow = context.spawnEntity(EntityType.COW, new BlockPos(1, 2, 1));
        CowEntity quiet = context.spawnEntity(EntityType.COW, new BlockPos(3, 2, 1));
        quiet.setSilent(true);
        quiet.setAiDisabled(true);
        for (CowEntity c : new CowEntity[]{cow, quiet}) {
            token(c).steveparty$setTokenized(true);
            context.assertTrue(c.isSilent() && c.isAiDisabled() && !c.isPushable(), "a token is a silent, still pawn");
            token(c).steveparty$setTokenized(false);
            context.assertTrue(c.isPushable(), "back to a mob, it can be pushed again");
        }
        context.assertFalse(cow.isSilent(), "back to a mob, it moos again");
        context.assertFalse(cow.isAiDisabled(), "back to a mob, it has its AI again");
        context.assertTrue(quiet.isSilent(), "a mob silenced before becoming a token stays silent");
        context.assertTrue(quiet.isAiDisabled(), "a mob without AI before becoming a token stays without AI");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pawnStateSurvivesSaveAndLoad(TestContext context) {
        CowEntity cow = context.spawnEntity(EntityType.COW, new BlockPos(1, 2, 1));
        token(cow).steveparty$setTokenized(true);
        NbtCompound nbt = new NbtCompound();
        cow.writeNbt(nbt);
        context.assertTrue(nbt.getBoolean("Silent"), "saved silent");
        context.assertFalse(nbt.getCompound("PreTokenState").getBoolean("Silent"), "saved its own voice setting");

        CowEntity reloaded = EntityType.COW.create(context.getWorld(), SpawnReason.LOAD);
        context.assertTrue(reloaded != null, "cow created");
        reloaded.readNbt(nbt);
        context.assertTrue(token(reloaded).steveparty$isTokenized() && reloaded.isSilent() && reloaded.isAiDisabled(),
                "still a silent pawn after a reload");
        token(reloaded).steveparty$setTokenized(false);
        context.assertFalse(reloaded.isSilent(), "reloaded then untokenized: its voice is back");
        context.assertFalse(reloaded.isAiDisabled(), "reloaded then untokenized: its AI is back");
        reloaded.discard();

        // A token saved before tokens were silenced (no Silent in its pre-token state, not silent itself)
        nbt.putBoolean("Silent", false);
        nbt.getCompound("PreTokenState").remove("Silent");
        CowEntity legacy = EntityType.COW.create(context.getWorld(), SpawnReason.LOAD);
        context.assertTrue(legacy != null, "cow created");
        legacy.readNbt(nbt);
        context.assertTrue(legacy.isSilent(), "an old token is silenced when loaded");
        token(legacy).steveparty$setTokenized(false);
        context.assertFalse(legacy.isSilent(), "an old token gets its voice back when untokenized");
        legacy.discard();
        context.complete();
    }
}
