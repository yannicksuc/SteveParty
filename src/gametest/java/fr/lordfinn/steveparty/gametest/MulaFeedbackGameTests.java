package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.entities.custom.MulaFood;
import fr.lordfinn.steveparty.entities.custom.MulaMotion;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.potion.Potions;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;

/**
 * The server side of the Mula's feedback: what it ate (drawn in its belly by the clients) and its meal counter (shown
 * as the food flying into its mouth), the belly emptied by the burst, "just spawned", the forced taming outcome and
 * the /mula operator command. The rules themselves (food, hunger, taming, sitting) are covered by
 * GameplayRulesGameTests and DecisionsGameTests.
 */
public class MulaFeedbackGameTests implements FabricGameTest {

    private static Item foodOf(MulaEntity mula) {
        return MulaFood.foodsOf(mula.getVariant()).iterator().next();
    }

    private static void disconnect(TestContext context, ServerPlayerEntity player) {
        context.getWorld().getServer().getPlayerManager().remove(player);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void mulaRemembersWhatItAteAndCountsItsMeals(TestContext context) {
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 3, 1));
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        Item food = foodOf(mula);
        try {
            context.assertTrue(mula.getLastFood().isEmpty(), "nothing eaten yet");
            double width = mula.getWidth();
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(food, 4));
            mula.interactMob(player, Hand.MAIN_HAND);
            context.assertTrue(mula.getWidth() > width + 0.01, "its hitbox grows with its hunger at once: "
                    + width + " -> " + mula.getWidth());
            context.assertTrue(mula.getLastFood().isOf(food), "remembers " + food + ", got " + mula.getLastFood());
            context.assertEquals(mula.getLastFood().getCount(), 1, "one item in its belly");
            context.assertEquals(mula.getFeedCount(), 1, "one meal");
            context.assertEquals(player.getMainHandStack().getCount(), 3, "still eats one item");
        } catch (RuntimeException e) {
            disconnect(context, player);
            throw e;
        }
        // after the 1 s cooldown: something it doesn't eat is refused and doesn't change its belly
        context.waitAndRun(25, () -> {
            try {
                player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
                mula.interactMob(player, Hand.MAIN_HAND);
                context.assertTrue(mula.getLastFood().isOf(food), "refused item not swallowed");
                context.assertEquals(mula.getFeedCount(), 1, "a refusal is not a meal");
                context.assertEquals(player.getMainHandStack().getCount(), 1, "refused item kept");
            } finally {
                disconnect(context, player);
            }
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void burstEmptiesTheBellyWithThePop(TestContext context) {
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 3, 1));
        mula.setAiDisabled(true);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        try {
            mula.setHunger(MulaEntity.MAX_HUNGER - 1);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(foodOf(mula), 1));
            mula.interactMob(player, Hand.MAIN_HAND);
            context.assertEquals(mula.getHunger(), 0, "burst: hunger reset");
            context.assertTrue(!mula.getLastFood().isEmpty(), "the food stays until the pop");
        } finally {
            disconnect(context, player);
        }
        context.waitAndRun(30, () -> {
            context.assertTrue(mula.getLastFood().isEmpty(), "belly empty after the pop");
            Box around = mula.getBoundingBox().expand(4);
            int fragments = context.getWorld().getEntitiesByClass(ItemEntity.class, around, e -> true).stream()
                    .mapToInt(e -> e.getStack().getCount()).sum();
            context.assertEquals(fragments, 64, "64 star fragments dropped as before");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void lastFoodIsSavedWithTheMula(TestContext context) {
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 3, 1));
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(foodOf(mula), 1));
            mula.interactMob(player, Hand.MAIN_HAND);
        } finally {
            disconnect(context, player);
        }
        NbtCompound nbt = new NbtCompound();
        mula.writeNbt(nbt);
        MulaEntity reloaded = ModEntities.MULA_ENTITY.create(context.getWorld());
        context.assertTrue(reloaded != null, "entity created");
        nbt.putBoolean("NoGravity", false); // like /summon with any NBT
        reloaded.readNbt(nbt);
        context.assertTrue(reloaded.getLastFood().isOf(foodOf(mula)), "belly saved: " + reloaded.getLastFood());
        context.assertTrue(reloaded.hasNoGravity(), "a Mula always floats");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void newMulaIsFreshForTwoSeconds(TestContext context) {
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 3, 1));
        context.assertTrue(!mula.isFresh(), "an entity that was not initialized (loaded) is not fresh");
        mula.initialize(context.getWorld(), context.getWorld().getLocalDifficulty(mula.getBlockPos()),
                SpawnReason.SPAWN_EGG, null);
        context.assertTrue(mula.isFresh(), "just hatched from its egg");
        context.waitAndRun(45, () -> {
            context.assertTrue(!mula.isFresh(), "no longer fresh");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void forcedTamingOutcome(TestContext context) {
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 3, 1));
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            mula.tameAttempt(player, false);
            context.assertTrue(!mula.isTamed(), "a failed attempt doesn't tame");
            mula.tameAttempt(player, true);
            context.assertTrue(mula.isTamed() && mula.isOwner(player), "a successful attempt tames");
        } finally {
            disconnect(context, player);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mulaCommandPlaysAnimationsAndChecksNames(TestContext context) {
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 3, 1));
        mula.addCommandTag("cmd_test");
        ServerCommandSource source = context.getWorld().getServer().getCommandSource().withLevel(2).withSilent();
        var manager = context.getWorld().getServer().getCommandManager();
        for (String animation : MulaEntity.animationNames()) {
            manager.executeWithPrefix(source, "mula @e[type=steveparty:mula,tag=cmd_test] play " + animation);
        }
        context.assertTrue(MulaEntity.animationNames().contains("star_orbit"), "new animations are listed");
        context.assertTrue(MulaEntity.animationNames().contains("tame_joy"), "feature animations are listed");
        context.assertTrue(!mula.isTamed() && !mula.isSitting(), "playing animations changes nothing else");
        context.complete();
    }

    // ---------------------------------------------------------------- feeding rules

    /** Of its colour but not edible (the old dyes, lapis...): refused. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nonFoodOfItsColourIsRefused(TestContext context) {
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 3, 1));
        mula.setVariant(MulaEntity.MulaVariant.BLUE);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            player.changeGameMode(GameMode.SURVIVAL);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.LAPIS_LAZULI, 4));
            mula.interactMob(player, Hand.MAIN_HAND);
            context.assertEquals(mula.getHunger(), 0, "lapis is blue but not food");
            context.assertEquals(player.getMainHandStack().getCount(), 4, "kept");
            context.assertTrue(!mula.isMulaFood(new ItemStack(Items.APPLE)), "an apple is not blue");
        } finally {
            disconnect(context, player);
        }
        context.complete();
    }

    /** A food of its colour gives its nutrition. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void foodGivesItsNutrition(TestContext context) {
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 3, 1));
        mula.setVariant(MulaEntity.MulaVariant.BLUE);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            player.changeGameMode(GameMode.SURVIVAL);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.COOKED_COD, 2));
            mula.interactMob(player, Hand.MAIN_HAND);
            int nutrition = new ItemStack(Items.COOKED_COD).get(DataComponentTypes.FOOD).nutrition();
            context.assertEquals(mula.getHunger(), nutrition, "cooked cod gives its nutrition");
        } finally {
            disconnect(context, player);
        }
        context.complete();
    }

    /** Potions: minutes x (level x 2) per effect; a Strength II lasting 3:00 would give 12. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void potionValueFormula(TestContext context) {
        context.assertEquals(MulaFood.potionValue(List.of(new StatusEffectInstance(StatusEffects.STRENGTH, 3600, 1))), 12,
                "Strength II 3:00");
        context.assertEquals(MulaFood.potionValue(List.of(new StatusEffectInstance(StatusEffects.SPEED, 9600, 0),
                new StatusEffectInstance(StatusEffects.REGENERATION, 2400, 1))), 8 * 2 + 2 * 4, "two effects add up");
        context.complete();
    }

    /** A potion of its colour feeds it and the empty bottle goes back to the player; another colour is refused. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void potionOfItsColourFeedsAndGivesTheBottleBack(TestContext context) {
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 3, 1));
        ItemStack strength = PotionContentsComponent.createStack(Items.POTION, Potions.STRENGTH);
        int colour = strength.get(DataComponentTypes.POTION_CONTENTS).getColor();
        mula.setVariant(MulaFood.colourOf(colour));
        context.assertTrue(mula.getVariant() == MulaEntity.MulaVariant.YELLOW, "strength is yellow: " + mula.getVariant());
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            player.changeGameMode(GameMode.SURVIVAL);
            player.setStackInHand(Hand.MAIN_HAND, strength);
            mula.interactMob(player, Hand.MAIN_HAND);
            context.assertEquals(mula.getHunger(), 3 * (1 * 2), "Strength I 3:00 gives 6");
            context.assertTrue(player.getMainHandStack().isOf(Items.GLASS_BOTTLE), "the bottle goes back: "
                    + player.getMainHandStack());
            MulaEntity blue = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(2, 3, 2));
            blue.setVariant(MulaEntity.MulaVariant.BLUE);
            context.assertTrue(!blue.isMulaFood(PotionContentsComponent.createStack(Items.POTION, Potions.STRENGTH)),
                    "a yellow potion is not for a blue Mula");
        } finally {
            disconnect(context, player);
        }
        context.complete();
    }

    /** Seeds count as food (the one non-edible exception): of its colour, a melon slice's worth; else refused. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void seedsOfItsColourGiveAMelonSlice(TestContext context) {
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 3, 1));
        mula.setVariant(MulaEntity.MulaVariant.GREEN);
        mula.setAiDisabled(true);
        int melon = Items.MELON_SLICE.getComponents().get(net.minecraft.component.DataComponentTypes.FOOD).nutrition();
        context.assertEquals(MulaFood.seedValue(), melon, "a seed is worth a melon slice");
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        try {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS, 4));
            mula.interactMob(player, Hand.MAIN_HAND);
            context.assertEquals(mula.getHunger(), melon, "wheat seeds: green, a melon slice's worth");
            context.assertEquals(player.getMainHandStack().getCount(), 3, "one seed eaten");
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.PUMPKIN_SEEDS, 4));
            mula.interactMob(player, Hand.MAIN_HAND);
            context.assertEquals(mula.getHunger(), melon, "pumpkin seeds (yellow): refused");
            context.assertEquals(player.getMainHandStack().getCount(), 4, "nothing used");
            context.assertTrue(MulaFood.value(MulaEntity.MulaVariant.BLACK, new ItemStack(Items.MELON_SEEDS)) == melon
                    && MulaFood.value(MulaEntity.MulaVariant.YELLOW, new ItemStack(Items.PUMPKIN_SEEDS)) == melon
                    && MulaFood.value(MulaEntity.MulaVariant.RED, new ItemStack(Items.BEETROOT_SEEDS)) == melon
                    && MulaFood.value(MulaEntity.MulaVariant.RED, new ItemStack(Items.PITCHER_POD)) == melon
                    && MulaFood.value(MulaEntity.MulaVariant.GREEN, new ItemStack(Items.TORCHFLOWER_SEEDS)) == melon,
                    "each seed to the colour it looks like");
            // dried kelp looks black: the black Mula's, not the green one's
            context.assertTrue(MulaFood.value(MulaEntity.MulaVariant.BLACK, new ItemStack(Items.DRIED_KELP)) > 0
                    && MulaFood.value(MulaEntity.MulaVariant.GREEN, new ItemStack(Items.DRIED_KELP)) == 0, "dried kelp is black");
        } finally {
            disconnect(context, player);
            mula.discard();
        }
        context.complete();
    }

    /** Puts the player {@code dx} blocks from the Mula's centre, level with it. */
    private static void placeBeside(ServerPlayerEntity player, MulaEntity mula, double dx) {
        player.refreshPositionAndAngles(mula.getX() + dx, mula.getY(), mula.getZ(), 0, 0);
    }

    /** A step of a test with a mock player, disconnected if the step fails (or after the {@code last} one). */
    private static void step(TestContext context, ServerPlayerEntity player, long tick, boolean last, Runnable check) {
        context.runAtTick(tick, () -> {
            try {
                check.run();
            } catch (RuntimeException e) {
                disconnect(context, player);
                throw e;
            }
            if (last) {
                disconnect(context, player);
                context.complete();
            }
        });
    }

    /**
     * A nearly full Mula no longer trembles all the time: only while a player is very close (within SHAKE_NEAR of its
     * body) or for SHAKE_AFTER_MEAL_TICKS after a meal; a Mula that is not nearly full never does.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void nearlyFullMulaShakesOnlyWhenApproachedOrJustFed(TestContext context) {
        MulaEntity mula = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1, 3, 1));
        mula.setAiDisabled(true);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        // a small meal (a seed), so it is nearly full before and after it, still one meal away from the burst
        mula.setVariant(MulaEntity.MulaVariant.GREEN);
        Item food = Items.WHEAT_SEEDS;
        int value = MulaFood.value(mula.getVariant(), new ItemStack(food));
        int nearlyFull = MulaEntity.MAX_HUNGER - 1 - value;
        if (value <= 0 || (float) (nearlyFull - value) / MulaEntity.MAX_HUNGER <= MulaMotion.TREMBLE_FROM) {
            disconnect(context, player);
            throw new AssertionError("unexpected seed value: " + value);
        }
        mula.setHunger(nearlyFull);
        placeBeside(player, mula, 12);
        step(context, player, 5, false, () -> {
            context.assertFalse(mula.isShaking(), "nearly full, nobody around: calm");
            placeBeside(player, mula, 1.2);
        });
        step(context, player, 9, false, () -> {
            context.assertTrue(mula.isShaking(), "a player right beside it: it trembles (" + player.distanceTo(mula) + ")");
            placeBeside(player, mula, mula.getWidth() / 2 + MulaEntity.SHAKE_NEAR + 1.5);
        });
        step(context, player, 13, false, () -> {
            context.assertFalse(mula.isShaking(), "the player stepped back (" + player.distanceTo(mula) + "): calm again");
            mula.setHunger(10);
            placeBeside(player, mula, 1.2);
        });
        step(context, player, 17, false, () -> {
            context.assertFalse(mula.isShaking(), "not nearly full: never trembles, even up close");
            // nearly full already before the meal, and a player well away: only the meal can make it tremble
            mula.setHunger(nearlyFull - value);
            placeBeside(player, mula, 12);
        });
        step(context, player, 21, false, () -> {
            context.assertFalse(mula.isShaking(), "nearly full, nobody around, before the meal: calm");
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(food, 1));
            mula.interactMob(player, Hand.MAIN_HAND);
            context.assertEquals(mula.getHunger(), nearlyFull, "fed");
            context.assertTrue(mula.isShaking(), "a meal just taken: it trembles at once, even with nobody close");
        });
        step(context, player, 21 + MulaEntity.SHAKE_AFTER_MEAL_TICKS - 6, false,
                () -> context.assertTrue(mula.isShaking(), "still trembling a little after the meal"));
        step(context, player, 21 + MulaEntity.SHAKE_AFTER_MEAL_TICKS + 4, true,
                () -> context.assertFalse(mula.isShaking(), "calm again a few seconds after the meal"));
    }
}
