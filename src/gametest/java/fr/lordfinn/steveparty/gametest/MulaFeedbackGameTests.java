package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
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
        return switch (mula.getVariant()) {
            case BLUE -> Items.LAPIS_LAZULI;
            case RED -> Items.RED_DYE;
            case GREEN -> Items.GREEN_DYE;
            case YELLOW -> Items.YELLOW_DYE;
            case PURPLE -> Items.PURPLE_DYE;
            case BLACK -> Items.COAL;
        };
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
            mula.setHunger(99);
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
        MulaEntity reloaded = ModEntities.MULA_ENTITY.create(context.getWorld(), SpawnReason.LOAD);
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
                SpawnReason.SPAWN_ITEM_USE, null);
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
}
