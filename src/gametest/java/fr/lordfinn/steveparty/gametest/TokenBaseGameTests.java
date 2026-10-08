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

/** Tokens stand on a base: their hitbox is {@link TokenBase#baseHeight} higher, feet on the ground. */
public class TokenBaseGameTests implements FabricGameTest {
    private static final float EPSILON = 1.0E-4F;
    private static final float PIG_HEIGHT = EntityType.PIG.getDimensions().height();
    private static final float PIG_EYE_HEIGHT = EntityType.PIG.getDimensions().eyeHeight();
    /** Base under a pig token of its natural size: the octagon around its 0.9 block wide hitbox, 3/8 as high. */
    private static final float PIG_BASE = EntityType.PIG.getDimensions().width() / (float) Math.sqrt(2) * 3 / 8;

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
            assertClose(context, pig.getHeight(), PIG_HEIGHT + PIG_BASE, "token height");
            assertClose(context, TokenBase.getBodyHeight(pig), PIG_HEIGHT, "body height");
            assertClose(context, pig.getStandingEyeHeight(), PIG_EYE_HEIGHT + PIG_BASE, "eye height");
            assertClose(context, pig.getY(), feetY, "feet stay on the ground");
            assertClose(context, pig.getBoundingBox().minY, feetY, "hitbox starts at the bottom of the base");
            assertClose(context, pig.getBoundingBox().getLengthY(), PIG_HEIGHT + PIG_BASE, "hitbox height");
            assertClose(context, pig.getAttachments().getPoint(EntityAttachmentType.NAME_TAG, 0, 0.0F).y,
                    nameTagY + PIG_BASE, "name tag raised");

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
    public void baseFollowsTheSizeOfThePawn(TestContext context) {
        PigEntity half = spawnPig(context), big = spawnPig(context), tiny = spawnPig(context);
        for (PigEntity pig : new PigEntity[]{half, big, tiny}) ((TokenizedEntityInterface) pig).steveparty$setTokenized(true);
        half.getAttributeInstance(EntityAttributes.GENERIC_SCALE).setBaseValue(0.5);
        big.getAttributeInstance(EntityAttributes.GENERIC_SCALE).setBaseValue(2.0);
        tiny.getAttributeInstance(EntityAttributes.GENERIC_SCALE).setBaseValue(0.1);
        // Dirty attributes (and so the scale) are applied in LivingEntity#tick
        context.waitAndRun(2, () -> {
            assertClose(context, TokenBase.getBodyHeight(half), PIG_HEIGHT * 0.5F, "scaled body");
            assertClose(context, half.getHeight(), PIG_HEIGHT * 0.5F + PIG_BASE * 0.5F, "half the pawn, half the base");
            assertClose(context, big.getHeight(), PIG_HEIGHT * 2.0F + PIG_BASE * 2.0F, "twice the pawn, twice the base");
            assertClose(context, tiny.getHeight() - TokenBase.getBodyHeight(tiny), PIG_BASE * 0.1F,
                    "a tiny pawn, a tiny base (same proportions)");
            assertClose(context, big.getBoundingBox().minY, big.getY(), "feet on the ground");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tokenBaseSurvivesSaveAndLoad(TestContext context) {
        PigEntity pig = spawnPig(context);
        ((TokenizedEntityInterface) pig).steveparty$setTokenized(true);
        NbtCompound nbt = new NbtCompound();
        pig.writeNbt(nbt);

        PigEntity reloaded = EntityType.PIG.create(context.getWorld());
        context.assertTrue(reloaded != null, "pig created");
        reloaded.readNbt(nbt);
        context.assertTrue(((TokenizedEntityInterface) reloaded).steveparty$isTokenized(), "still a token");
        assertClose(context, reloaded.getHeight(), PIG_HEIGHT + PIG_BASE, "reloaded token height");
        assertClose(context, reloaded.getBoundingBox().getLengthY(), PIG_HEIGHT + PIG_BASE, "reloaded hitbox");
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
        PigEntity reloaded = EntityType.PIG.create(context.getWorld());
        context.assertTrue(reloaded != null, "pig created");
        reloaded.readNbt(nbt);
        assertClose(context, reloaded.getHeight(), PIG_HEIGHT, "reloaded regular height");
        assertClose(context, reloaded.getBoundingBox().getLengthY(), PIG_HEIGHT, "reloaded regular hitbox");
        reloaded.discard();
        context.complete();
    }

    /**
     * The base holds the whole hitbox (its square, corners included, inside the octagon) and keeps its proportions
     * (height over apothem) at every size.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theBaseHoldsTheHitboxInTheSameProportions(TestContext context) {
        for (float width : new float[]{0.1F, 0.6F, 0.9F, 1.4F, 4.5F, 9.75F}) {
            float apothem = TokenBase.baseApothem(width);
            float half = width / 2;
            // The square's corner (half, half) against the octagon's diagonal side: (x + z) / sqrt(2) <= apothem
            context.assertTrue((half + half) / Math.sqrt(2) <= apothem + EPSILON, "corner inside, width " + width);
            context.assertTrue(half <= apothem, "side inside, width " + width);
            float height = TokenBase.baseHeight(net.minecraft.entity.EntityDimensions.fixed(width, 1.0F));
            assertClose(context, height / apothem, 3.0 / 8.0, "same proportions, width " + width);
        }
        context.complete();
    }
}
