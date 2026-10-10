package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.dice.CursedRolls;
import fr.lordfinn.steveparty.effect.ModEffects;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriEntity;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriGoals;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriBadLuck;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriPlay;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriSummoning;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.CatVariant;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.List;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestCleanup.atEnd;

/**
 * The Mistigri out of the board: his summoning (a die bound to a black cat by a witch hut, landing on a 1 of any colour
 * or a 0), the Bad Luck of crossing his path, taming him with fish, his owner's Luck, the monsters missing, the chests
 * he sits on, what he knocks off, his die (cursed 1 to 3 only), and saving him.
 */
public class MistigriGameTests implements SteveGameTest {
    private static final String HUT = "mistigri_hut", NO_HUT = "mistigri_no_hut";
    private static final BlockPos CAT = new BlockPos(3, 1, 3);

    /** The test's area counts as a witch hut. */
    private static void hut(TestContext context) {
        Box box = new Box(Vec3d.of(context.getAbsolutePos(BlockPos.ORIGIN)), Vec3d.of(context.getAbsolutePos(new BlockPos(8, 4, 8))));
        MistigriSummoning.TEST_HUTS.add(box);
        atEnd(context, () -> MistigriSummoning.TEST_HUTS.remove(box));
    }

    private static CatEntity cat(TestContext context, RegistryKey<CatVariant> variant) {
        CatEntity cat = context.spawnMob(EntityType.CAT, CAT);
        cat.setVariant(context.getWorld().getRegistryManager().get(RegistryKeys.CAT_VARIANT).getEntry(variant).orElseThrow());
        cat.setAiDisabled(true);
        return cat;
    }

    private static MistigriEntity mistigri(TestContext context, BlockPos at) {
        MistigriEntity mistigri = context.spawnEntity(ModEntities.MISTIGRI, at);
        mistigri.setAiDisabled(true);
        return mistigri;
    }

    private static ServerPlayerEntity survival(TestContext context) {
        ServerPlayerEntity player = player(context);
        player.changeGameMode(GameMode.SURVIVAL);
        return player;
    }

    private static void moveTo(TestContext context, ServerPlayerEntity player, Vec3d relative) {
        Vec3d at = context.getAbsolute(relative);
        player.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
    }

    private static List<MistigriEntity> mistigris(TestContext context) {
        return context.getWorld().getEntitiesByClass(MistigriEntity.class,
                new Box(context.getAbsolutePos(BlockPos.ORIGIN)).expand(10), MistigriEntity::isAlive);
    }

    /** {@code face} thrown by a player at the cat, bound to it, and stopped at once: does a Mistigri come out? */
    private static void rollAtCat(TestContext context, CatEntity cat, String face, boolean expected) {
        ServerPlayerEntity player = player(context);
        DiceEntity dice = thrown(context, player, die(face), CAT);
        dice.setTargetEntity(cat);
        hit(context, dice, player);
        context.waitAndRun(2, () -> {
            boolean turned = cat.isRemoved() && !mistigris(context).isEmpty();
            context.assertTrue(turned == expected, face + (expected ? " turns the black cat into a Mistigri" : " leaves the cat a cat"));
            if (expected) {
                MistigriEntity mistigri = mistigris(context).getFirst();
                context.assertTrue(mistigri.getAction() == MistigriEntity.Action.SUMMON, "his summon animation plays");
                context.assertTrue(mistigri.isPersistent(), "he stays");
            }
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = HUT)
    public void aOneSummonsHim(TestContext context) {
        TestBoards.floor(context, 8);
        hut(context);
        rollAtCat(context, cat(context, CatVariant.ALL_BLACK), "dice_face_1", true);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = HUT)
    public void aPremiumOneSummonsHim(TestContext context) {
        TestBoards.floor(context, 8);
        hut(context);
        rollAtCat(context, cat(context, CatVariant.ALL_BLACK), "premium_dice_face_1", true);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = HUT)
    public void aCursedOneSummonsHim(TestContext context) {
        TestBoards.floor(context, 8);
        hut(context);
        rollAtCat(context, cat(context, CatVariant.ALL_BLACK), "cursed_dice_face_1", true);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = HUT)
    public void aZeroSummonsHim(TestContext context) {
        TestBoards.floor(context, 8);
        hut(context);
        rollAtCat(context, cat(context, CatVariant.ALL_BLACK), "dice_face_0", true);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = HUT)
    public void aFiveDoesNot(TestContext context) {
        TestBoards.floor(context, 8);
        hut(context);
        rollAtCat(context, cat(context, CatVariant.ALL_BLACK), "dice_face_5", false);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = HUT)
    public void onlyABlackCat(TestContext context) {
        TestBoards.floor(context, 8);
        hut(context);
        rollAtCat(context, cat(context, CatVariant.TABBY), "dice_face_1", false);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = NO_HUT)
    public void onlyByAWitchHut(TestContext context) {
        TestBoards.floor(context, 8);
        rollAtCat(context, cat(context, CatVariant.ALL_BLACK), "dice_face_1", false);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = HUT)
    public void aTamedBlackCatStaysItsOwners(TestContext context) {
        TestBoards.floor(context, 8);
        hut(context);
        CatEntity cat = cat(context, CatVariant.ALL_BLACK);
        ServerPlayerEntity owner = player(context);
        cat.setOwner(owner);
        MistigriEntity mistigri = MistigriSummoning.transform(context.getWorld(), cat);
        context.assertTrue(mistigri != null && mistigri.isTamed() && mistigri.isOwner(owner), "still theirs");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void unluckyFaces(TestContext context) {
        context.assertTrue(MistigriSummoning.unlucky(List.of(new DiceFacesComponent.DiceFace(
                DiceFacesComponent.Kind.NORMAL, 0), new DiceFacesComponent.DiceFace(
                DiceFacesComponent.Kind.CURSED, 1))), "a double die: 0 and 1");
        context.assertFalse(MistigriSummoning.unlucky(List.of(new DiceFacesComponent.DiceFace(
                DiceFacesComponent.Kind.COIN, 1))), "a coin face is no 1");
        context.assertFalse(MistigriSummoning.unlucky(List.of(new DiceFacesComponent.DiceFace(
                DiceFacesComponent.Kind.BLANK, 0))), "a blank side is no 0");
        context.complete();
    }

    // ---------------------------------------------------------------- bad luck

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void crossingHisPathGivesBadLuck(TestContext context) {
        TestBoards.floor(context, 8);
        MistigriEntity mistigri = mistigri(context, new BlockPos(3, 1, 1));
        mistigri.setYaw(0);
        mistigri.setBodyYaw(0); // facing +z
        ServerPlayerEntity player = survival(context);
        moveTo(context, player, new Vec3d(2.0, 1, 4.0)); // ahead, on one side
        MistigriBadLuck.tickCrossings(context.getWorld(), mistigri);
        context.assertFalse(player.hasStatusEffect(ModEffects.BAD_LUCK), "standing in front of him is fine");
        moveTo(context, player, new Vec3d(5.0, 1, 4.0)); // across his line
        MistigriBadLuck.tickCrossings(context.getWorld(), mistigri);
        context.assertTrue(player.hasStatusEffect(ModEffects.BAD_LUCK), "crossing his path: Bad Luck");
        context.assertTrue(player.getStatusEffect(ModEffects.BAD_LUCK).getDuration() > MistigriBadLuck.UNLUCK_TICKS - 5, "a whole minute");
        context.assertFalse(player.hasStatusEffect(StatusEffects.UNLUCK), "the mod's Bad Luck (a black cat icon), not vanilla's");
        context.assertTrue(player.getAttributeValue(EntityAttributes.GENERIC_LUCK) == -1.0, "luck -1, as vanilla's Bad Luck");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void passingBehindHimIsFine(TestContext context) {
        TestBoards.floor(context, 8);
        MistigriEntity mistigri = mistigri(context, new BlockPos(3, 1, 6));
        mistigri.setYaw(0);
        mistigri.setBodyYaw(0);
        ServerPlayerEntity player = survival(context);
        moveTo(context, player, new Vec3d(2.0, 1, 4.0)); // behind him
        MistigriBadLuck.tickCrossings(context.getWorld(), mistigri);
        moveTo(context, player, new Vec3d(5.0, 1, 4.0));
        MistigriBadLuck.tickCrossings(context.getWorld(), mistigri);
        context.assertFalse(player.hasStatusEffect(ModEffects.BAD_LUCK), "behind him: no bad luck");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void hisOwnerNeverCrossesHim(TestContext context) {
        TestBoards.floor(context, 8);
        MistigriEntity mistigri = mistigri(context, new BlockPos(3, 1, 1));
        mistigri.setYaw(0);
        mistigri.setBodyYaw(0);
        ServerPlayerEntity owner = survival(context);
        mistigri.tame(owner);
        moveTo(context, owner, new Vec3d(2.0, 1, 4.0));
        MistigriBadLuck.tickCrossings(context.getWorld(), mistigri);
        moveTo(context, owner, new Vec3d(5.0, 1, 4.0));
        MistigriBadLuck.tickCrossings(context.getWorld(), mistigri);
        context.assertFalse(owner.hasStatusEffect(ModEffects.BAD_LUCK), "his owner: no bad luck");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void hittingHimMakesHimAngryAndUnlucky(TestContext context) {
        TestBoards.floor(context, 8);
        MistigriEntity mistigri = mistigri(context, CAT);
        ServerPlayerEntity player = survival(context);
        moveTo(context, player, new Vec3d(3.5, 1, 5.5));
        mistigri.damage(context.getWorld().getDamageSources().playerAttack(player), 1f);
        context.assertTrue(mistigri.isAngry(), "angry");
        context.assertTrue(player.hasStatusEffect(ModEffects.BAD_LUCK), "whoever hit him: Bad Luck");
        ItemStack fish = new ItemStack(Items.COD, 3);
        player.setStackInHand(Hand.MAIN_HAND, fish);
        mistigri.interactMob(player, Hand.MAIN_HAND);
        context.assertFalse(mistigri.isAngry(), "a raw fish calms him");
        context.assertTrue(fish.getCount() == 2, "eaten");
        context.complete();
    }

    // ---------------------------------------------------------------- taming, luck, monsters

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void lotsOfFishTameHim(TestContext context) {
        TestBoards.floor(context, 8);
        MistigriEntity mistigri = mistigri(context, CAT);
        ServerPlayerEntity player = survival(context);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SALMON, 64));
        int needed = mistigri.fishToTame();
        context.assertTrue(needed >= MistigriEntity.FISH_TO_TAME_MIN && needed <= MistigriEntity.FISH_TO_TAME_MAX, "lots of fish");
        for (int i = 1; i < needed; i++) mistigri.interactMob(player, Hand.MAIN_HAND);
        context.assertFalse(mistigri.isTamed(), "not yet");
        context.assertTrue(mistigri.fishFed() == needed - 1, "each fish counted");
        mistigri.interactMob(player, Hand.MAIN_HAND);
        context.assertTrue(mistigri.isTamed() && mistigri.isOwner(player), "tamed by the last one");
        context.assertTrue(player.getMainHandStack().getCount() == 64 - needed, "every fish eaten");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void heGivesHisOwnerLuck(TestContext context) {
        TestBoards.floor(context, 8);
        MistigriEntity mistigri = mistigri(context, CAT);
        ServerPlayerEntity owner = survival(context), other = survival(context);
        moveTo(context, owner, new Vec3d(3.5, 1, 5.5));
        moveTo(context, other, new Vec3d(4.5, 1, 5.5));
        MistigriBadLuck.giveLuck(context.getWorld(), mistigri);
        context.assertFalse(owner.hasStatusEffect(StatusEffects.LUCK), "a wild one gives none");
        mistigri.tame(owner);
        MistigriBadLuck.giveLuck(context.getWorld(), mistigri);
        context.assertTrue(owner.hasStatusEffect(StatusEffects.LUCK), "his owner: Luck");
        context.assertFalse(other.hasStatusEffect(StatusEffects.LUCK), "only his owner");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void monstersNearATamedOneMissSometimes(TestContext context) {
        TestBoards.floor(context, 8);
        MistigriEntity mistigri = mistigri(context, CAT);
        Vec3d near = context.getAbsolute(new Vec3d(5, 1, 5));
        context.assertFalse(MistigriBadLuck.nearTamed(context.getWorld(), near), "a wild one brings no monster bad luck");
        mistigri.tame(player(context));
        context.assertTrue(MistigriBadLuck.nearTamed(context.getWorld(), near), "near a tamed one");
        context.assertFalse(MistigriBadLuck.nearTamed(context.getWorld(), near.add(30, 0, 0)), "far from him");
        // some of a zombie's blows miss (one in three: a hundred blows can't all land)
        PigEntity victim = context.spawnMob(EntityType.PIG, new BlockPos(5, 1, 5));
        victim.setAiDisabled(true);
        ZombieEntity zombie = context.spawnMob(EntityType.ZOMBIE, new BlockPos(6, 1, 5));
        zombie.setAiDisabled(true);
        int missed = 0;
        for (int i = 0; i < 100; i++) {
            victim.setHealth(victim.getMaxHealth());
            victim.timeUntilRegen = 0;
            victim.hurtTime = 0;
            if (!victim.damage(context.getWorld().getDamageSources().mobAttack(zombie), 1f)) missed++;
        }
        context.assertTrue(missed > 5 && missed < 95, "some blows missed: " + missed);
        context.complete();
    }

    // ---------------------------------------------------------------- a cat's life

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aChestHeSleepsOnWontOpen(TestContext context) {
        TestBoards.floor(context, 8);
        BlockPos chest = new BlockPos(3, 1, 3);
        context.setBlockState(chest, Blocks.CHEST);
        MistigriEntity mistigri = mistigri(context, chest.up());
        context.assertTrue(MistigriBadLuck.sitter(context.getWorld(), context.getAbsolutePos(chest)) == null, "standing on it is not sitting");
        mistigri.loafOn(context.getAbsolutePos(chest));
        context.assertTrue(MistigriBadLuck.sitter(context.getWorld(), context.getAbsolutePos(chest)) == mistigri, "he sits on it");
        context.assertTrue(MistigriBadLuck.isSeat(Blocks.BARREL) && MistigriBadLuck.isSeat(Blocks.ENDER_CHEST), "barrels and ender chests too");
        context.assertTrue(mistigri.isAsleepOnChest() && !mistigri.isFree(), "asleep on it, until woken");
        mistigri.feed(context.getWorld(), player(context));
        context.assertFalse(mistigri.isAsleepOnChest(), "a raw fish wakes him");
        context.assertTrue(MistigriBadLuck.sitter(context.getWorld(), context.getAbsolutePos(chest)) == null, "the chest opens again");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 600)
    public void heKnocksOffAFrameOnTheFloor(TestContext context) {
        knocksOff(context, new BlockPos(3, 1, 6), Direction.UP);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 600)
    public void heKnocksOffAFrameLowOnAWall(TestContext context) {
        wall(context, 3, 6, 2);
        knocksOff(context, new BlockPos(3, 1, 5), Direction.NORTH);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 600)
    public void heKnocksOffAFrameHighOnAWall(TestContext context) {
        wall(context, 3, 6, 4);
        knocksOff(context, new BlockPos(3, 3, 5), Direction.NORTH);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 600)
    public void heKnocksOffAFrameOnALowCeiling(TestContext context) {
        context.setBlockState(new BlockPos(3, 4, 5), Blocks.STONE);
        knocksOff(context, new BlockPos(3, 3, 5), Direction.DOWN);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 600)
    public void heHopsOnABlockForAFrameOnAHigherCeiling(TestContext context) {
        context.setBlockState(new BlockPos(3, 5, 5), Blocks.STONE);
        context.setBlockState(new BlockPos(4, 1, 5), Blocks.STONE); // the block he hops on
        knocksOff(context, new BlockPos(3, 4, 5), Direction.DOWN);
    }

    /** A stone wall column at ({@code x}, 1..{@code height}, {@code z}). */
    private static void wall(TestContext context, int x, int z, int height) {
        for (int y = 1; y <= height; y++) context.setBlockState(new BlockPos(x, y, z), Blocks.STONE);
    }

    /** A frame holding a diamond in {@code cell}, facing {@code facing}: a wild Mistigri knocks it off, frame unbroken. */
    private static void knocksOff(TestContext context, BlockPos cell, Direction facing) {
        TestBoards.floor(context, 8);
        ServerWorld world = context.getWorld();
        ItemFrameEntity frame = new ItemFrameEntity(world, context.getAbsolutePos(cell), facing);
        frame.setHeldItemStack(new ItemStack(Items.DIAMOND));
        world.spawnEntity(frame);
        MistigriEntity mistigri = context.spawnEntity(ModEntities.MISTIGRI, new BlockPos(3, 1, 2));
        context.assertTrue(MistigriGoals.standSpot(world, frame) != null, "somewhere he reaches it from");
        context.assertTrue(MistigriGoals.findFrame(world, mistigri) == frame, "he spots it");
        boolean[] done = {false};
        context.runAtEveryTick(() -> {
            if (done[0] || !frame.getHeldItemStack().isEmpty()) return;
            done[0] = true;
            context.assertTrue(frame.isAlive(), "the frame is never broken");
            context.assertTrue(!world.getEntitiesByClass(ItemEntity.class, frame.getBoundingBox().expand(4),
                    item -> item.getStack().isOf(Items.DIAMOND)).isEmpty(), "the diamond fell");
            frame.discard();
            context.complete();
        });
    }

    // ---------------------------------------------------------------- play

    private static ItemEntity acorn(TestContext context, BlockPos at) {
        BlockPos abs = context.getAbsolutePos(at);
        ItemEntity acorn = new ItemEntity(context.getWorld(), abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5,
                new ItemStack(ModItems.ACORN));
        acorn.setVelocity(Vec3d.ZERO);
        context.getWorld().spawnEntity(acorn);
        return acorn;
    }

    // each its own batch: an acorn or a Glandouille next door would catch the other tests' cats' eyes
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "mistigri_acorn", tickLimit = 600)
    public void aThrownAcornDistractsHimAndHePlaysWithIt(TestContext context) {
        TestBoards.floor(context, 8);
        MistigriEntity mistigri = context.spawnEntity(ModEntities.MISTIGRI, new BlockPos(1, 1, 1));
        mistigri.setAngry(400); // even angry
        ItemEntity acorn = acorn(context, new BlockPos(6, 1, 6));
        Vec3d[] reached = {null};
        context.runAtEveryTick(() -> {
            context.assertTrue(acorn.isAlive(), "he never takes the acorn");
            if (reached[0] == null) {
                if (mistigri.isPlaying() && acorn.squaredDistanceTo(mistigri) < 2.5 * 2.5) {
                    context.assertFalse(mistigri.isAngry(), "the acorn made him forget his anger");
                    reached[0] = acorn.getPos();
                }
                return;
            }
            if (acorn.getPos().squaredDistanceTo(reached[0]) > 0.6 * 0.6) {
                acorn.discard();
                context.complete();
            }
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "mistigri_acorn_picked_up", tickLimit = 400)
    public void heStopsPlayingWhenTheAcornIsPickedUp(TestContext context) {
        TestBoards.floor(context, 8);
        MistigriEntity mistigri = context.spawnEntity(ModEntities.MISTIGRI, new BlockPos(2, 1, 2));
        ItemEntity acorn = acorn(context, new BlockPos(4, 1, 2));
        int[] pickedAt = {-1};
        int[] tick = {0};
        context.runAtEveryTick(() -> {
            tick[0]++;
            if (pickedAt[0] < 0) {
                if (mistigri.isPlaying()) {
                    acorn.discard(); // as a player picking it up
                    pickedAt[0] = tick[0];
                }
                return;
            }
            if (tick[0] - pickedAt[0] >= 3) {
                context.assertFalse(mistigri.isPlaying(), "the game ends with the acorn gone");
                context.complete();
            }
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "mistigri_cat_and_mouse", tickLimit = 1200)
    public void hePlaysCatAndMouseWithAGlandouilleWithoutHurtingIt(TestContext context) {
        TestBoards.floor(context, 8);
        GlandouilleEntity glandouille = context.spawnEntity(ModEntities.GLANDOUILLE, new BlockPos(6, 1, 6));
        float health = glandouille.getHealth();
        MistigriEntity mistigri = context.spawnEntity(ModEntities.MISTIGRI, new BlockPos(1, 1, 1));
        context.assertTrue(MistigriPlay.findPrey(context.getWorld(), mistigri) == glandouille, "he spots it");
        boolean[] chased = {false};
        context.runAtEveryTick(() -> {
            // kept awake: a nap (likely at night, which other tests set the world to) ends the game, and he rests long after
            glandouille.handled();
            context.assertTrue(glandouille.isAlive() && glandouille.getHealth() >= health, "never hurt");
            if (!chased[0] && glandouille.scaredOf() == mistigri) chased[0] = true;
            if (chased[0] && glandouille.getNavigation().isFollowingPath()) {
                glandouille.discard();
                context.complete(); // pounced on, it runs away
            }
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "mistigri_board_glandouilles")
    public void heLeavesBoardAndCarriedGlandouillesAlone(TestContext context) {
        TestBoards.floor(context, 8);
        MistigriEntity mistigri = mistigri(context, new BlockPos(2, 1, 2));
        GlandouilleEntity onTheBoard = context.spawnEntity(ModEntities.GLANDOUILLE, new BlockPos(4, 1, 2));
        onTheBoard.setBoardActor();
        GlandouilleEntity ofATile = context.spawnEntity(ModEntities.GLANDOUILLE, new BlockPos(5, 1, 3));
        ofATile.setInvulnerable(true); // a board space's mob
        GlandouilleEntity carried = context.spawnEntity(ModEntities.GLANDOUILLE, new BlockPos(3, 1, 4));
        ServerPlayerEntity player = player(context);
        player.refreshPositionAndAngles(carried.getX(), carried.getY(), carried.getZ(), 0, 0);
        carried.startRiding(player, true);
        context.assertFalse(MistigriPlay.isPrey(onTheBoard), "not the board's");
        context.assertFalse(MistigriPlay.isPrey(ofATile), "not a board space's");
        context.assertFalse(MistigriPlay.isPrey(carried), "not one in a player's hands");
        context.assertTrue(MistigriPlay.findPrey(context.getWorld(), mistigri) == null, "none to play with");
        onTheBoard.scare(mistigri, 100);
        ofATile.scare(mistigri, 100);
        context.assertTrue(onTheBoard.scaredOf() == null && ofATile.scaredOf() == null, "a board's one never runs from him");
        carried.stopRiding();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void heLiesOnTheLidCentredAlongTheChest(TestContext context) {
        TestBoards.floor(context, 8);
        // a single chest, he landed off-centre
        BlockPos single = context.getAbsolutePos(new BlockPos(2, 1, 2));
        context.setBlockState(new BlockPos(2, 1, 2), Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH));
        MistigriEntity onSingle = mistigri(context, new BlockPos(2, 2, 2));
        onSingle.setPosition(onSingle.getX() + 0.3, single.getY() + 0.875, onSingle.getZ() - 0.2);
        onSingle.napOn(single);
        context.assertTrue(near(onSingle.getX(), single.getX() + 0.5) && near(onSingle.getZ(), single.getZ() + 0.5), "centred on the chest");
        context.assertTrue(near(onSingle.getY(), single.getY() + 14 / 16.0), "on its lid");
        context.assertTrue(Direction.fromRotation(onSingle.getYaw()).getAxis() == Direction.Axis.X, "lengthwise along its front");
        // a double chest: in the middle of both halves
        BlockPos half = new BlockPos(5, 1, 2);
        BlockState left = Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH).with(ChestBlock.CHEST_TYPE, ChestType.LEFT);
        BlockPos otherHalf = half.offset(ChestBlock.getFacing(left));
        context.setBlockState(half, left);
        context.setBlockState(otherHalf, left.with(ChestBlock.CHEST_TYPE, ChestType.RIGHT));
        BlockPos a = context.getAbsolutePos(half), b = context.getAbsolutePos(otherHalf);
        MistigriEntity onDouble = mistigri(context, half.up());
        onDouble.napOn(a);
        context.assertTrue(near(onDouble.getX(), (a.getX() + b.getX()) / 2.0 + 0.5) && near(onDouble.getZ(), a.getZ() + 0.5), "in the middle of the double chest");
        context.assertTrue(Direction.fromRotation(onDouble.getYaw()).getAxis() == Direction.Axis.X, "along both halves");
        // a barrel: a full block
        BlockPos barrel = context.getAbsolutePos(new BlockPos(2, 1, 5));
        context.setBlockState(new BlockPos(2, 1, 5), Blocks.BARREL);
        MistigriEntity onBarrel = mistigri(context, new BlockPos(2, 2, 5));
        onBarrel.napOn(barrel);
        context.assertTrue(near(onBarrel.getY(), barrel.getY() + 1.0), "on the barrel's top");
        context.waitAndRun(10, () -> {
            context.assertTrue(onSingle.chest() != null && onDouble.chest() != null && onBarrel.chest() != null, "still asleep there");
            context.assertTrue(near(onSingle.getY(), single.getY() + 14 / 16.0), "still on the lid");
            context.assertTrue(MistigriBadLuck.sitter(context.getWorld(), single) == onSingle, "the chest won't open");
            context.assertTrue(MistigriBadLuck.sitter(context.getWorld(), a) == onDouble
                    && MistigriBadLuck.sitter(context.getWorld(), b) == onDouble, "neither half of the double chest opens");
            context.complete();
        });
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-4;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void heKnocksItemsOffFrames(TestContext context) {
        TestBoards.floor(context, 8);
        context.setBlockState(new BlockPos(3, 1, 5), Blocks.STONE);
        context.setBlockState(new BlockPos(3, 2, 5), Blocks.STONE);
        BlockPos at = context.getAbsolutePos(new BlockPos(3, 2, 4));
        ItemFrameEntity frame = new ItemFrameEntity(context.getWorld(), at, Direction.NORTH);
        frame.setHeldItemStack(new ItemStack(Items.DIAMOND));
        context.getWorld().spawnEntity(frame);
        MistigriEntity mistigri = mistigri(context, new BlockPos(3, 1, 3));
        context.assertTrue(MistigriGoals.findFrame(context.getWorld(), mistigri) == frame, "he spots it");
        MistigriGoals.knock(context.getWorld(), frame);
        context.assertTrue(frame.getHeldItemStack().isEmpty(), "the frame is empty");
        context.assertTrue(!context.getWorld().getEntitiesByClass(ItemEntity.class, frame.getBoundingBox().expand(2),
                item -> item.getStack().isOf(Items.DIAMOND)).isEmpty(), "the diamond fell");
        frame.discard();
        context.complete();
    }

    // ---------------------------------------------------------------- the Mistigri's Die

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void hisDieOnlyRollsTheCursedOneToThree(TestContext context) {
        TestBoards.floor(context, 8);
        ServerPlayerEntity player = survival(context);
        DiceEntity dice = thrown(context, player, CursedRolls.mistigriDie(), new BlockPos(3, 1, 3));
        hit(context, dice, player);
        context.waitAndRun(1, () -> {
            int steps = dice.getOutcome().steps();
            context.assertTrue(steps >= 1 && steps <= 3, "the Mistigri's Die rolled " + steps);
            context.complete();
        });
    }

    // ---------------------------------------------------------------- save

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void heIsSavedAndLoaded(TestContext context) {
        TestBoards.floor(context, 8);
        MistigriEntity mistigri = mistigri(context, CAT);
        ServerPlayerEntity owner = player(context);
        mistigri.fishToTame();
        mistigri.feed(context.getWorld(), owner);
        mistigri.feed(context.getWorld(), owner);
        mistigri.setAngry(80);
        BlockPos chest = context.getAbsolutePos(new BlockPos(1, 1, 1));
        mistigri.loafOn(chest);
        NbtCompound nbt = mistigri.writeNbt(new NbtCompound());
        MistigriEntity loaded = ModEntities.MISTIGRI.create(context.getWorld());
        loaded.readNbt(nbt);
        context.assertTrue(loaded.fishFed() == 2 && loaded.fishToTame() == mistigri.fishToTame(), "his fish count");
        context.assertTrue(loaded.isAngry(), "still angry");
        context.assertTrue(chest.equals(loaded.chest()), "on his chest");
        mistigri.tame(owner);
        mistigri.setSitting(true);
        MistigriEntity tamed = ModEntities.MISTIGRI.create(context.getWorld());
        tamed.readNbt(mistigri.writeNbt(new NbtCompound()));
        context.assertTrue(tamed.isTamed() && owner.getUuid().equals(tamed.getOwnerUuid()) && tamed.isSitting(), "his owner, sitting");
        context.assertTrue(mistigri.shouldSave(), "a wild or tamed one is saved");
        context.complete();
    }
}
