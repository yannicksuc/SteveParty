package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.entities.TokenBase;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityAttachmentType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/** Tokens stand on a base: their hitbox is {@link TokenBase#BASE_HEIGHT} higher, feet on the ground. */
public class TokenBaseGameTests implements FabricGameTest {
    private static final float EPSILON = 1.0E-4F;
    private static final float PIG_HEIGHT = EntityType.PIG.getDimensions().height();
    private static final float PIG_EYE_HEIGHT = EntityType.PIG.getDimensions().eyeHeight();

    private static void assertClose(TestContext context, double actual, double expected, String message) {
        context.assertTrue(Math.abs(actual - expected) < EPSILON, message + ": expected " + expected + ", got " + actual);
    }

    private static PigEntity spawnPig(TestContext context) {
        PigEntity pig = context.spawnMob(EntityType.PIG, new BlockPos(1, 1, 1));
        pig.setAiDisabled(true);
        return pig;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tokenStandsOnItsBaseUntilUntokenized(TestContext context) {
        PigEntity pig = spawnPig(context);
        context.waitAndRun(2, () -> {
            double feetY = pig.getY();
            double nameTagY = pig.getAttachments().getPoint(EntityAttachmentType.NAME_TAG, 0, 0.0F).y;
            TokenizedEntityInterface token = (TokenizedEntityInterface) pig;

            token.steveparty$setTokenized(true);
            assertClose(context, pig.getHeight(), PIG_HEIGHT + TokenBase.BASE_HEIGHT, "token height");
            assertClose(context, TokenBase.getBodyHeight(pig), PIG_HEIGHT, "body height");
            assertClose(context, pig.getStandingEyeHeight(), PIG_EYE_HEIGHT + TokenBase.BASE_HEIGHT, "eye height");
            assertClose(context, pig.getY(), feetY, "feet stay on the ground");
            assertClose(context, pig.getBoundingBox().minY, feetY, "hitbox starts at the bottom of the base");
            assertClose(context, pig.getBoundingBox().getLengthY(), PIG_HEIGHT + TokenBase.BASE_HEIGHT, "hitbox height");
            assertClose(context, pig.getAttachments().getPoint(EntityAttachmentType.NAME_TAG, 0, 0.0F).y,
                    nameTagY + TokenBase.BASE_HEIGHT, "name tag raised");

            token.steveparty$setTokenized(false);
            assertClose(context, pig.getHeight(), PIG_HEIGHT, "height restored");
            assertClose(context, pig.getStandingEyeHeight(), PIG_EYE_HEIGHT, "eye height restored");
            assertClose(context, pig.getBoundingBox().getLengthY(), PIG_HEIGHT, "hitbox restored");
            assertClose(context, pig.getAttachments().getPoint(EntityAttachmentType.NAME_TAG, 0, 0.0F).y,
                    nameTagY, "name tag restored");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void baseDoesNotScaleWithTheMob(TestContext context) {
        PigEntity pig = spawnPig(context);
        ((TokenizedEntityInterface) pig).steveparty$setTokenized(true);
        pig.getAttributeInstance(EntityAttributes.SCALE).setBaseValue(0.5);
        // Dirty attributes (and so the scale) are applied in LivingEntity#tick
        context.waitAndRun(2, () -> {
            assertClose(context, TokenBase.getBodyHeight(pig), PIG_HEIGHT * 0.5F, "scaled body");
            assertClose(context, pig.getHeight(), PIG_HEIGHT * 0.5F + TokenBase.BASE_HEIGHT, "scaled body + full base");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tokenBaseSurvivesSaveAndLoad(TestContext context) {
        PigEntity pig = spawnPig(context);
        ((TokenizedEntityInterface) pig).steveparty$setTokenized(true);
        NbtCompound nbt = new NbtCompound();
        pig.writeNbt(nbt);

        PigEntity reloaded = EntityType.PIG.create(context.getWorld(), SpawnReason.LOAD);
        context.assertTrue(reloaded != null, "pig created");
        reloaded.readNbt(nbt);
        context.assertTrue(((TokenizedEntityInterface) reloaded).steveparty$isTokenized(), "still a token");
        assertClose(context, reloaded.getHeight(), PIG_HEIGHT + TokenBase.BASE_HEIGHT, "reloaded token height");
        assertClose(context, reloaded.getBoundingBox().getLengthY(), PIG_HEIGHT + TokenBase.BASE_HEIGHT, "reloaded hitbox");
        assertClose(context, reloaded.getBoundingBox().minY, reloaded.getY(), "reloaded feet on the ground");
        reloaded.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void regularMobHasNoBase(TestContext context) {
        PigEntity pig = spawnPig(context);
        pig.setInvulnerable(true);
        pig.setCustomNameVisible(true);
        assertClose(context, pig.getHeight(), PIG_HEIGHT, "regular height");
        assertClose(context, pig.getStandingEyeHeight(), PIG_EYE_HEIGHT, "regular eye height");

        NbtCompound nbt = new NbtCompound();
        pig.writeNbt(nbt);
        nbt.putBoolean("Tokenized", false); // written by older versions on every mob
        PigEntity reloaded = EntityType.PIG.create(context.getWorld(), SpawnReason.LOAD);
        context.assertTrue(reloaded != null, "pig created");
        reloaded.readNbt(nbt);
        assertClose(context, reloaded.getHeight(), PIG_HEIGHT, "reloaded regular height");
        assertClose(context, reloaded.getBoundingBox().getLengthY(), PIG_HEIGHT, "reloaded regular hitbox");
        reloaded.discard();
        context.complete();
    }
}
