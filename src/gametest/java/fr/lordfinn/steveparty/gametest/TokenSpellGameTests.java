package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.components.MobEntityComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.TokenizerFlare;
import fr.lordfinn.steveparty.items.custom.TokenizerWandItem;
import fr.lordfinn.steveparty.items.custom.TokenizerWandItem.SpellResult;
import fr.lordfinn.steveparty.utils.DominantColorPicker;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.text.Text;
import net.minecraft.village.VillagerProfession;
import net.minecraft.util.ActionResult;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.registry.ModGameRules;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.TurtleEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static fr.lordfinn.steveparty.components.ModComponents.MOB_ENTITY_COMPONENT;
import static fr.lordfinn.steveparty.effect.ModEffects.SQUISHED;
import static fr.lordfinn.steveparty.utils.MessageUtils.getColorFromText;

/** Tokenizer Wand spell: size chosen with a slider (validated server side), token colour, no more moving. */
public class TokenSpellGameTests implements SteveGameTest {
    private static final BlockPos MOB_POS = new BlockPos(2, 2, 2);
    private static final int BLUE = 0x3366CC;
    private static final int ORANGE = 0xE08020;

    // ---------------------------------------------------------------- helpers

    /** A player standing next to {@link #MOB_POS}, holding a Tokenizer Wand. */
    private static ServerPlayerEntity wandHolder(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        Vec3d pos = context.getAbsolute(new Vec3d(MOB_POS.getX() + 0.5, MOB_POS.getY(), MOB_POS.getZ() - 1.0));
        player.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.TOKENIZER_WAND));
        return player;
    }

    private static void resetCooldown(ServerPlayerEntity player) {
        ItemStack wand = TokenizerWandItem.heldWand(player);
        player.getItemCooldownManager().remove((wand).getItem());
    }

    private static TokenizedEntityInterface token(MobEntity mob) {
        return (TokenizedEntityInterface) mob;
    }

    private static <T extends MobEntity> T spawn(TestContext context, EntityType<T> type) {
        T mob = context.spawnMob(type, MOB_POS);
        mob.setAiDisabled(true);
        // Sizes are checked against the adults' (a spawn can be a baby)
        mob.setBaby(false);
        return mob;
    }

    /** Body dimensions (a token's hitbox also includes its base). */
    private static EntityDimensions body(MobEntity mob) {
        return mob.getDimensions(EntityPose.STANDING);
    }

    private static float biggest(MobEntity mob) {
        return Math.max(body(mob).width(), body(mob).height());
    }

    private static boolean near(double value, double expected) {
        return Math.abs(value - expected) < 0.02;
    }

    // ---------------------------------------------------------------- bounds

    /** A llama token goes into an empty Token: the llama's own interaction (its chest screen, mounting) doesn't win. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void aLlamaTokenCanBeStoredInAToken(TestContext context) {
        LlamaEntity llama = spawn(context, EntityType.LLAMA);
        llama.setTame(true);
        ServerPlayerEntity player = wandHolder(context);
        try {
            context.assertTrue(TokenizerWandItem.castSpell(player, llama.getId(), 1F, BLUE) == SpellResult.TOKENIZED, "tokenized");
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.TOKEN));
            ActionResult result = player.interact(llama, Hand.MAIN_HAND);
            context.assertTrue(result.isAccepted(), "the Token takes the click: " + result);
            context.assertTrue(!player.hasVehicle(), "not riding the llama");
            context.assertTrue(player.getMainHandStack().get(ModComponents.ENTITY_DATA_COMPONENT) != null,
                    "the llama is stored in the Token");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tokenSizeAndColourAreSanitized(TestContext context) {
        PigEntity pig = spawn(context, EntityType.PIG); // 0.9 blocks: up to 4.5
        float max = TokenizerWandItem.maxTokenSize(pig);
        context.assertTrue(near(max, 4.5), "five times its size: " + max);
        context.assertTrue(TokenizerWandItem.clampTokenSize(pig, 50F) == max, "too big clamped");
        context.assertTrue(TokenizerWandItem.clampTokenSize(pig, 0.01F) == TokenizerWandItem.MIN_TOKEN_SIZE, "too small clamped");
        context.assertTrue(TokenizerWandItem.clampTokenSize(pig, -3F) == TokenizerWandItem.MIN_TOKEN_SIZE, "negative clamped");
        context.assertTrue(TokenizerWandItem.clampTokenSize(pig, Float.NaN) == TokenizerWandItem.DEFAULT_TOKEN_SIZE, "NaN -> default");
        context.assertTrue(TokenizerWandItem.clampTokenSize(pig, Float.POSITIVE_INFINITY) == TokenizerWandItem.DEFAULT_TOKEN_SIZE, "infinity -> default");
        context.assertTrue(TokenizerWandItem.clampTokenSize(pig, 1.3F) == 1.3F, "in bounds kept");
        context.assertEquals(TokenizerWandItem.sanitizeColor(BLUE), BLUE, "valid colour");
        context.assertEquals(TokenizerWandItem.sanitizeColor(0x1000000), TokenizerWandItem.NO_COLOR, "alpha / overflow refused");
        context.assertEquals(TokenizerWandItem.sanitizeColor(-5), TokenizerWandItem.NO_COLOR, "negative refused");
        context.complete();
    }

    // ---------------------------------------------------------------- tokenize

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void spellTokenizesWithChosenSizeAndColour(TestContext context) {
        CowEntity cow = spawn(context, EntityType.COW); // taller than wide: the height is the size
        ServerPlayerEntity player = wandHolder(context);
        try {
            SpellResult result = TokenizerWandItem.castSpell(player, cow.getId(), 0.5F, BLUE);
            context.assertTrue(result == SpellResult.TOKENIZED, "tokenized: " + result);
            context.assertTrue(token(cow).steveparty$isTokenized(), "is a token");
            context.assertTrue(player.getUuid().equals(token(cow).steveparty$getTokenOwner()), "owned by the caster");
            context.assertTrue(token(cow).steveparty$getTokenSize() == 0.5F, "size stored");
            context.assertEquals(token(cow).steveparty$getTokenColor(), BLUE, "colour stored");
            context.assertEquals(getColorFromText(cow.getCustomName()) & 0xFFFFFF, BLUE, "name styled with the colour");
            context.assertEquals(cow.getCustomName().getString(), player.getDisplayName().getString(), "name = player's name");
            // Anti-spam: the wand cools down after a cast
            context.assertTrue(TokenizerWandItem.castSpell(player, cow.getId(), 1F, BLUE) == SpellResult.COOLDOWN, "cooldown");
        } finally {
            TestPlayers.remove(context, player);
        }
        // The hitbox follows on the next entity tick
        context.runAtTick(3, () -> {
            context.assertTrue(near(body(cow).height(), 0.5), "height = chosen size: " + body(cow));
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void wideMobSizeIsItsWidth(TestContext context) {
        TurtleEntity turtle = spawn(context, EntityType.TURTLE); // wider than tall
        ServerPlayerEntity player = wandHolder(context);
        try {
            context.assertTrue(TokenizerWandItem.castSpell(player, turtle.getId(), 1.5F, ORANGE) == SpellResult.TOKENIZED, "tokenized");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.runAtTick(3, () -> {
            context.assertTrue(near(body(turtle).width(), 1.5), "width = chosen size: " + body(turtle));
            context.assertTrue(body(turtle).height() < body(turtle).width(), "proportions kept");
            context.complete();
        });
    }

    /** The biggest token is five times the mob's own size: a chicken pawn smaller than a zombie one. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void theBiggestTokenIsFiveTimesTheMob(TestContext context) {
        ChickenEntity chicken = spawn(context, EntityType.CHICKEN);
        ZombieEntity zombie = spawn(context, EntityType.ZOMBIE);
        chicken.setBaby(false);
        zombie.setBaby(false);
        context.assertTrue(near(TokenizerWandItem.maxTokenSize(chicken), 3.5), "chicken: " + TokenizerWandItem.maxTokenSize(chicken));
        context.assertTrue(near(TokenizerWandItem.maxTokenSize(zombie), 9.75), "zombie: " + TokenizerWandItem.maxTokenSize(zombie));
        ServerPlayerEntity player = wandHolder(context);
        context.assertTrue(near(TokenizerWandItem.maxTokenSize(player), 9.0), "player: " + TokenizerWandItem.maxTokenSize(player));
        try {
            context.assertTrue(TokenizerWandItem.castSpell(player, zombie.getId(), 40F, -1).success(), "big zombie");
            context.assertTrue(near(token(zombie).steveparty$getTokenSize(), 9.75), "clamped to its max");
            // A resized token keeps its own size as the reference, not the size it was given
            context.assertTrue(near(TokenizerWandItem.naturalSize(zombie), 1.95), "natural size kept");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.runAtTick(3, () -> {
            context.assertTrue(near(body(zombie).height(), 9.75), "a 9.75 blocks zombie pawn: " + body(zombie));
            context.assertTrue(near(TokenizerWandItem.maxTokenSize(zombie), 9.75), "still its max once big");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void spellSizeIsClampedByTheServer(TestContext context) {
        PigEntity big = spawn(context, EntityType.PIG);
        PigEntity small = spawn(context, EntityType.PIG);
        PigEntity notANumber = spawn(context, EntityType.PIG);
        ServerPlayerEntity player = wandHolder(context);
        try {
            context.assertTrue(TokenizerWandItem.castSpell(player, big.getId(), 40F, -1).success(), "big cast");
            resetCooldown(player);
            context.assertTrue(TokenizerWandItem.castSpell(player, small.getId(), 0.001F, -1).success(), "small cast");
            resetCooldown(player);
            context.assertTrue(TokenizerWandItem.castSpell(player, notANumber.getId(), Float.NaN, 0x7FFFFFFF).success(), "NaN cast");
            context.assertTrue(near(token(big).steveparty$getTokenSize(), 4.5), "clamped to the pig's max: " + token(big).steveparty$getTokenSize());
            context.assertTrue(token(small).steveparty$getTokenSize() == TokenizerWandItem.MIN_TOKEN_SIZE, "clamped to the min");
            context.assertTrue(token(notANumber).steveparty$getTokenSize() == TokenizerWandItem.DEFAULT_TOKEN_SIZE, "NaN -> default");
            // Invalid colour ignored: previous behaviour (plain player name), no colour stored
            context.assertEquals(token(notANumber).steveparty$getTokenColor(), TokenizerWandItem.NO_COLOR, "invalid colour ignored");
            context.assertEquals(notANumber.getCustomName().getString(), player.getDisplayName().getString(), "still named");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.runAtTick(3, () -> {
            context.assertTrue(near(biggest(big), 4.5), "4.5 blocks: " + body(big));
            context.assertTrue(near(biggest(small), 0.25), "0.25 block: " + body(small));
            context.complete();
        });
    }

    /** Squish without a chosen size (e.g. /effect): amplifier 10 = 1 block of body, the token base excluded. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void amplifierSquishOfATokenIgnoresItsBase(TestContext context) {
        CowEntity cow = spawn(context, EntityType.COW);
        token(cow).steveparty$setTokenized(true);
        cow.addStatusEffect(new StatusEffectInstance(SQUISHED, 20, 10));
        context.assertTrue(token(cow).steveparty$getTokenSize() == 0, "no chosen size");
        context.runAtTick(3, () -> {
            context.assertTrue(near(body(cow).height(), 1.0), "1 block of body: " + body(cow));
            context.complete();
        });
    }

    // ---------------------------------------------------------------- refusals

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void spellRefusals(TestContext context) {
        PigEntity othersToken = spawn(context, EntityType.PIG);
        token(othersToken).steveparty$setTokenized(true);
        token(othersToken).steveparty$setTokenOwner(UUID.randomUUID());
        token(othersToken).steveparty$setTokenSize(1F);
        PigEntity pig = spawn(context, EntityType.PIG);
        WitherEntity wither = spawn(context, EntityType.WITHER);

        ServerPlayerEntity player = wandHolder(context);
        try {
            if (!player.hasPermissionLevel(2)) {
                context.assertTrue(TokenizerWandItem.castSpell(player, othersToken.getId(), 0.5F, BLUE) == SpellResult.NOT_ALLOWED,
                        "another player's token refused");
                context.assertTrue(token(othersToken).steveparty$getTokenSize() == 1F, "size untouched");
            }
            context.assertTrue(TokenizerWandItem.castSpell(player, wither.getId(), 0.5F, BLUE) == SpellResult.BOSS, "boss refused");
            context.assertTrue(!token(wither).steveparty$isTokenized(), "boss not tokenized");
            context.assertTrue(TokenizerWandItem.castSpell(player, player.getId(), 0.5F, BLUE) == SpellResult.INVALID_TARGET, "not a mob");
            context.assertTrue(TokenizerWandItem.castSpell(player, -12345, 0.5F, BLUE) == SpellResult.INVALID_TARGET, "unknown entity");

            // Out of reach beyond MAX_SPELL_DISTANCE (the spell screen does not pause the game)
            Vec3d near = player.getPos();
            Vec3d tooFar = context.getAbsolute(new Vec3d(MOB_POS.getX() + TokenizerWandItem.MAX_SPELL_DISTANCE + 8.5, MOB_POS.getY(), MOB_POS.getZ()));
            player.refreshPositionAndAngles(tooFar.x, tooFar.y, tooFar.z, 0, 0);
            context.assertTrue(TokenizerWandItem.castSpell(player, pig.getId(), 0.5F, BLUE) == SpellResult.OUT_OF_REACH, "out of reach");
            context.assertTrue(!token(pig).steveparty$isTokenized(), "too far: nothing happened");
            player.refreshPositionAndAngles(near.x, near.y, near.z, 0, 0);

            // No wand in hand
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            context.assertTrue(TokenizerWandItem.castSpell(player, pig.getId(), 0.5F, BLUE) == SpellResult.NO_WAND, "no wand");
            context.assertTrue(!token(pig).steveparty$isTokenized(), "nothing happened");

            // Off hand wand is fine
            player.setStackInHand(Hand.OFF_HAND, new ItemStack(ModItems.TOKENIZER_WAND));
            context.assertTrue(TokenizerWandItem.castSpell(player, pig.getId(), 0.5F, BLUE) == SpellResult.TOKENIZED, "off hand wand");
        } finally {
            TestPlayers.remove(context, player);
            wither.discard();
        }
        context.complete();
    }

    /**
     * Bosses allowed by the game rule: the Wither becomes a pawn and hides its boss bar until it is a mob again; the
     * Ender Dragon is still refused, clicked on any of its parts. The rule is only on during this tick's checks (the
     * other tests, run at the same time, expect the default).
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void bossesAllowedByTheGameRule(TestContext context) {
        GameRules.BooleanRule rule = context.getWorld().getGameRules().get(ModGameRules.TOKENIZE_BOSSES);
        boolean before = rule.get();
        rule.set(true, context.getWorld().getServer());
        WitherEntity wither = spawn(context, EntityType.WITHER);
        ServerPlayerEntity player = wandHolder(context);
        try {
            EnderDragonEntity dragon = EntityType.ENDER_DRAGON.create(context.getWorld());
            context.assertTrue(dragon != null, "dragon created");
            context.assertTrue(TokenizerWandItem.isBoss(dragon), "the Ender Dragon is refused even when bosses are allowed");
            context.assertTrue(player.interact(dragon.getBodyParts()[0], Hand.MAIN_HAND) == ActionResult.FAIL,
                    "the wand on a part of the dragon is refused, not ignored");
            context.assertTrue(!token(dragon).steveparty$isTokenized(), "dragon not tokenized");

            context.assertTrue(TokenizerWandItem.castSpell(player, wither.getId(), 0.5F, BLUE) == SpellResult.TOKENIZED,
                    "the Wither is taken when bosses are allowed");
        } finally {
            rule.set(before, context.getWorld().getServer());
            TestPlayers.remove(context, player);
        }
        context.runAtTick(5, () -> {
            context.assertFalse(bossBar(wither).isVisible(), "a Wither token has no boss bar");
            token(wither).steveparty$setTokenized(false);
        });
        context.runAtTick(10, () -> {
            context.assertTrue(bossBar(wither).isVisible(), "the boss bar is back once it is a mob again");
            wither.discard();
            context.complete();
        });
    }

    private static ServerBossBar bossBar(WitherEntity wither) {
        for (Field field : WitherEntity.class.getDeclaredFields()) {
            if (field.getType() != ServerBossBar.class) continue;
            try {
                field.setAccessible(true);
                return (ServerBossBar) field.get(wither);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("no boss bar on the Wither");
    }

    /** A mob that wandered off while the circle was drawn still gets the spell (the client casts it at once). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void spellStillReachesAMobThatWanderedOff(TestContext context) {
        CowEntity cow = spawn(context, EntityType.COW);
        ServerPlayerEntity player = wandHolder(context);
        try {
            Vec3d far = context.getAbsolute(new Vec3d(MOB_POS.getX() + 20.5, MOB_POS.getY(), MOB_POS.getZ()));
            player.refreshPositionAndAngles(far.x, far.y, far.z, 0, 0);
            context.assertTrue(player.squaredDistanceTo(cow) > 10 * 10, "farther than the old limit");
            SpellResult result = TokenizerWandItem.castSpell(player, cow.getId(), 0.75F, BLUE);
            context.assertTrue(result == SpellResult.TOKENIZED, "tokenized from 20 blocks: " + result);
            context.assertTrue(token(cow).steveparty$getTokenSize() == 0.75F, "size stored");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    /**
     * The flare flies straight: it stops on the first mob or block on its way, and only a mob the spell could take
     * opens the spell; nothing behind, nothing through a wall, never a boss.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void flareFliesStraightToTheFirstMobOnItsWay(TestContext context) {
        ServerPlayerEntity player = wandHolder(context);
        try {
            ItemStack wand = player.getMainHandStack();
            // Level with the mobs' bodies, towards +z; a short stretch of the flight (the other tests' areas are further)
            Vec3d from = context.getAbsolute(new Vec3d(MOB_POS.getX() + 0.5, MOB_POS.getY() + 0.4, MOB_POS.getZ() + 0.5));
            Vec3d to = from.add(0, 0, 4.4);
            TokenizerFlare.Hit hit = TokenizerFlare.trace(player.getServerWorld(), player, wand, from, to);
            context.assertTrue(hit.mob() == null && !hit.stops(), "nothing on the way: " + hit);

            PigEntity behind = spawnAt(context, EntityType.PIG, MOB_POS.add(0, 0, -3));
            hit = TokenizerFlare.trace(player.getServerWorld(), player, wand, from, to);
            context.assertTrue(hit.mob() == null && !hit.stops(), "a mob behind is not on the way");

            PigEntity ahead = spawnAt(context, EntityType.PIG, MOB_POS.add(0, 0, 4));
            hit = TokenizerFlare.trace(player.getServerWorld(), player, wand, from, to);
            context.assertTrue(hit.mob() == ahead, "the pig straight ahead: " + hit.mob());

            WitherEntity boss = spawnAt(context, EntityType.WITHER, MOB_POS.add(0, 0, 2));
            try {
                hit = TokenizerFlare.trace(player.getServerWorld(), player, wand, from, to);
                context.assertTrue(hit.mob() == null && hit.stops(), "a boss on the way stops the flare, no spell");
            } finally {
                boss.discard();
            }

            context.setBlockState(MOB_POS.add(0, 0, 2), Blocks.STONE);
            hit = TokenizerFlare.trace(player.getServerWorld(), player, wand, from, to);
            context.assertTrue(hit.mob() == null && hit.stops(), "a wall stops the flare");
            behind.discard();
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    /** With the wand in hand, the wand wins over the mob's own interaction: no trades, no riding, no sitting. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void wandWinsOverMobInteractions(TestContext context) {
        ServerPlayerEntity player = wandHolder(context);
        try {
            VillagerEntity villager = spawnAt(context, EntityType.VILLAGER, MOB_POS);
            villager.setVillagerData(villager.getVillagerData().withProfession(VillagerProfession.FARMER));
            ActionResult result = player.interact(villager, Hand.MAIN_HAND);
            context.assertTrue(result.isAccepted(), "the wand's spell takes the click: " + result);
            context.assertTrue(villager.getCustomer() == null, "no trade opened");

            HorseEntity horse = spawnAt(context, EntityType.HORSE, MOB_POS.add(2, 0, 0));
            player.interact(horse, Hand.MAIN_HAND);
            context.assertTrue(!player.hasVehicle(), "not riding the horse");

            MulaEntity mula = spawnAt(context, ModEntities.MULA_ENTITY, MOB_POS.add(-2, 0, 0));
            mula.setOwner(player);
            mula.setTamed(true, false);
            boolean sitting = mula.isSitting();
            player.interact(mula, Hand.MAIN_HAND);
            context.assertTrue(mula.isSitting() == sitting, "the Mula did not sit / stand up");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    private static <T extends MobEntity> T spawnAt(TestContext context, EntityType<T> type, BlockPos pos) {
        T mob = context.spawnMob(type, pos);
        mob.setAiDisabled(true);
        return mob;
    }

    // ---------------------------------------------------------------- resize

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void resizeKeepsTokenData(TestContext context) {
        PigEntity pig = spawn(context, EntityType.PIG);
        PigEntity legacy = spawn(context, EntityType.PIG);
        ServerPlayerEntity player = wandHolder(context);
        try {
            context.assertTrue(TokenizerWandItem.castSpell(player, pig.getId(), 0.5F, BLUE) == SpellResult.TOKENIZED, "tokenized");
            token(pig).steveparty$setNbSteps(3);
            token(pig).steveparty$setStatus(TokenStatus.IN_GAME);
            resetCooldown(player);

            context.assertTrue(TokenizerWandItem.castSpell(player, pig.getId(), 1.5F, ORANGE) == SpellResult.RESIZED, "resized");
            context.assertTrue(token(pig).steveparty$isTokenized(), "still a token");
            context.assertTrue(player.getUuid().equals(token(pig).steveparty$getTokenOwner()), "owner kept");
            context.assertEquals(token(pig).steveparty$getNbSteps(), 3, "steps kept");
            context.assertTrue(TokenStatus.isInGame(token(pig).steveparty$getStatus()), "status kept");
            context.assertTrue(token(pig).steveparty$getTokenSize() == 1.5F, "new size");
            context.assertEquals(token(pig).steveparty$getTokenColor(), BLUE, "colour kept");
            context.assertEquals(getColorFromText(pig.getCustomName()) & 0xFFFFFF, BLUE, "name colour kept");

            // A token made before colours existed gets one on its first resize
            token(legacy).steveparty$setTokenized(true);
            token(legacy).steveparty$setTokenOwner(player);
            legacy.setCustomName(player.getDisplayName());
            resetCooldown(player);
            context.assertTrue(TokenizerWandItem.castSpell(player, legacy.getId(), 1F, ORANGE) == SpellResult.RESIZED, "legacy resized");
            context.assertEquals(token(legacy).steveparty$getTokenColor(), ORANGE, "legacy colour set");
            context.assertEquals(getColorFromText(legacy.getCustomName()) & 0xFFFFFF, ORANGE, "legacy name coloured");

            // Size and colour survive a save / reload
            NbtCompound nbt = new NbtCompound();
            pig.writeNbt(nbt);
            PigEntity reloaded = EntityType.PIG.create(context.getWorld());
            context.assertTrue(reloaded != null, "reloaded pig");
            reloaded.readNbt(nbt);
            context.assertTrue(token(reloaded).steveparty$getTokenSize() == 1.5F, "size saved");
            context.assertEquals(token(reloaded).steveparty$getTokenColor(), BLUE, "colour saved");
            reloaded.discard();
        } finally {
            TestPlayers.remove(context, player);
        }
        context.runAtTick(3, () -> {
            context.assertTrue(near(biggest(pig), 1.5), "resized: " + body(pig));
            context.complete();
        });
    }

    // ---------------------------------------------------------------- no more moving

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void wandNoLongerMovesTokens(TestContext context) {
        PigEntity pig = spawn(context, EntityType.PIG);
        ServerPlayerEntity player = wandHolder(context);
        token(pig).steveparty$setTokenized(true);
        token(pig).steveparty$setTokenOwner(player);
        ItemStack wand = player.getMainHandStack();
        Vec3d start;
        try {
            // Using the wand on an own token opens the (resize) spell, it does not select the token any more
            context.assertTrue(wand.useOnEntity(player, pig, Hand.MAIN_HAND).isAccepted(), "spell opened");
            context.assertTrue(!wand.contains(MOB_ENTITY_COMPONENT), "no token selected");

            // A wand saved by an older version still carries a selected token: clicking a block does not move it
            wand.set(MOB_ENTITY_COMPONENT, new MobEntityComponent(pig.getUuidAsString()));
            BlockPos target = context.getAbsolutePos(new BlockPos(5, 1, 5));
            wand.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND,
                    new BlockHitResult(Vec3d.ofCenter(target), Direction.UP, target, false)));
            start = pig.getPos();

            // ... and the stale selection is stripped from the wand
            wand.inventoryTick(context.getWorld(), player, 0, true);
            context.assertTrue(!wand.contains(MOB_ENTITY_COMPONENT), "legacy selection removed");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.runAtTick(40, () -> {
            Vec3d now = pig.getPos();
            context.assertTrue(Math.abs(now.x - start.x) < 0.1 && Math.abs(now.z - start.z) < 0.1, "token did not move: " + now);
            context.complete();
        });
    }

    // ---------------------------------------------------------------- colour picker

    private static int[] pixels(Object... colorAndCount) {
        int total = 0;
        for (int i = 1; i < colorAndCount.length; i += 2) total += (int) colorAndCount[i];
        int[] pixels = new int[total];
        int index = 0;
        for (int i = 0; i < colorAndCount.length; i += 2) {
            int argb = (int) colorAndCount[i];
            for (int n = 0; n < (int) colorAndCount[i + 1]; n++) pixels[index++] = argb;
        }
        return pixels;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void dominantColourWins(TestContext context) {
        int[] image = pixels(0xFFD03030, 60, 0xFF3050D0, 30, 0xFF30B040, 10);
        context.assertEquals(DominantColorPicker.pickFromPixels(image, new Random(1)), 0xD03030, "most present colour");
        context.assertEquals(DominantColorPicker.candidates(image).length, 1, "no tie");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tiedDistinctColoursPickOneOfThem(TestContext context) {
        int red = 0xD03030, blue = 0x3050D0, green = 0x30B040;
        int[] halves = pixels(0xFF000000 | red, 50, 0xFF000000 | blue, 48);
        Set<Integer> seen = new HashSet<>();
        Random random = new Random(42);
        for (int i = 0; i < 60; i++) {
            int picked = DominantColorPicker.pickFromPixels(halves, random);
            context.assertTrue(picked == red || picked == blue, "one of the tied colours, never a mix: " + Integer.toHexString(picked));
            seen.add(picked);
        }
        context.assertEquals(seen.size(), 2, "both tied colours can be picked");

        int[] thirds = pixels(0xFF000000 | red, 33, 0xFF000000 | blue, 33, 0xFF000000 | green, 32);
        int[] candidates = DominantColorPicker.candidates(thirds);
        Arrays.sort(candidates);
        int[] expected = {green, blue, red};
        Arrays.sort(expected);
        context.assertTrue(Arrays.equals(candidates, expected), "three-way tie: " + Arrays.toString(candidates));
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void similarShadesMerge(TestContext context) {
        // Two pinks close to each other (35 + 30) beat a single blue (45), and the winner is a real pink, not a mix
        int pink = 0xF0A0A0, darkerPink = 0xE89898, blue = 0x3050D0;
        int[] image = pixels(0xFF000000 | pink, 35, 0xFF000000 | darkerPink, 30, 0xFF000000 | blue, 45);
        context.assertTrue(DominantColorPicker.distance(pink, darkerPink) < 10, "the shades are similar");
        int picked = DominantColorPicker.pickFromPixels(image, new Random(3));
        context.assertEquals(picked, pink, "most present shade of the merged cluster");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void transparentAndOutlinePixelsAreIgnored(TestContext context) {
        int[] image = pixels(0xFF101010, 100, 0x00FFFFFF, 200, 0x40FF00FF, 200, 0xFFE0C020, 20);
        context.assertEquals(DominantColorPicker.pickFromPixels(image, new Random(1)), 0xE0C020, "outline / transparency ignored");
        // A black mob stays black
        context.assertEquals(DominantColorPicker.pickFromPixels(pixels(0xFF101010, 10, 0x00FFFFFF, 50), new Random(1)), 0x101010, "black mob");
        context.assertEquals(DominantColorPicker.pickFromPixels(pixels(0x00FFFFFF, 10), new Random(1)), DominantColorPicker.NO_COLOR, "nothing opaque");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void storedTokenItemTakesTheOwnerNameColour(TestContext context) {
        // The Token item is named "Token of %s" with the coloured name as argument: its icon is tinted with it
        Text name = Text.translatable("item.steveparty.token.of",
                Text.literal("LordFinn").withColor(BLUE));
        context.assertEquals(getColorFromText(name) & 0xFFFFFF, BLUE, "colour read from the translation argument");
        context.complete();
    }
}
