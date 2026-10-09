package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.dice.CursedRolls;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriEntity;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriGoals;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriBadLuck;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriSummoning;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.CatVariant;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
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
 * he sits on, what he knocks off, the Loaded Die's curse, and saving him.
 */
public class MistigriGameTests implements FabricGameTest {
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
        context.assertFalse(player.hasStatusEffect(StatusEffects.UNLUCK), "standing in front of him is fine");
        moveTo(context, player, new Vec3d(5.0, 1, 4.0)); // across his line
        MistigriBadLuck.tickCrossings(context.getWorld(), mistigri);
        context.assertTrue(player.hasStatusEffect(StatusEffects.UNLUCK), "crossing his path: Bad Luck");
        context.assertTrue(player.getStatusEffect(StatusEffects.UNLUCK).getDuration() > MistigriBadLuck.UNLUCK_TICKS - 5, "a whole minute");
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
        context.assertFalse(player.hasStatusEffect(StatusEffects.UNLUCK), "behind him: no bad luck");
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
        context.assertFalse(owner.hasStatusEffect(StatusEffects.UNLUCK), "his owner: no bad luck");
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
        context.assertTrue(player.hasStatusEffect(StatusEffects.UNLUCK), "whoever hit him: Bad Luck");
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
        net.minecraft.entity.passive.PigEntity victim = context.spawnMob(EntityType.PIG, new BlockPos(5, 1, 5));
        victim.setAiDisabled(true);
        net.minecraft.entity.mob.ZombieEntity zombie = context.spawnMob(EntityType.ZOMBIE, new BlockPos(6, 1, 5));
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

    // ---------------------------------------------------------------- the Loaded Die

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theLoadedDieCursesTheNextRoll(TestContext context) {
        TestBoards.floor(context, 8);
        ServerPlayerEntity user = survival(context), victim = survival(context);
        atEnd(context, () -> victim.removeCommandTag(CursedRolls.TAG));
        ItemStack loaded = new ItemStack(ModItems.LOADED_DIE, 2);
        user.setStackInHand(Hand.MAIN_HAND, loaded);
        loaded.useOnEntity(user, victim, Hand.MAIN_HAND);
        context.assertTrue(CursedRolls.isCursed(victim), "cursed");
        context.assertTrue(loaded.getCount() == 1, "spent");
        DiceEntity dice = thrown(context, victim, die("dice_face_9", "dice_face_10"), new BlockPos(3, 1, 3));
        hit(context, dice, victim);
        context.waitAndRun(1, () -> {
            int steps = dice.getOutcome().steps();
            context.assertTrue(steps >= 1 && steps <= 3, "a 9-10 die rolled " + steps);
            context.assertFalse(CursedRolls.isCursed(victim), "one roll and it's gone");
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
