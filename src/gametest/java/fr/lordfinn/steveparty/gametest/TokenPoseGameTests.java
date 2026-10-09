package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnEntity;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

/**
 * Mob pawn poses, server side: a right click with an empty hand gives the pawn its next pose number (saved, synced);
 * the other clicks keep their use.
 */
public class TokenPoseGameTests implements SteveGameTest {
    private static final BlockPos MOB_POS = new BlockPos(2, 2, 2);

    private static ServerPlayerEntity player(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        TestPlayers.place(context, player, MOB_POS.getX() + 0.5, MOB_POS.getY(), MOB_POS.getZ() - 1.0);
        return player;
    }

    private static <T extends MobEntity> T pawn(TestContext context, EntityType<T> type) {
        T mob = context.spawnMob(type, MOB_POS);
        ((TokenizedEntityInterface) mob).steveparty$setTokenized(true);
        return mob;
    }

    private static int pose(MobEntity mob) {
        return ((TokenizedEntityInterface) mob).steveparty$getTokenPose();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anEmptyHandPosesThePawn(TestContext context) {
        ZombieEntity zombie = pawn(context, EntityType.ZOMBIE);
        ServerPlayerEntity player = player(context);
        try {
            context.assertEquals(pose(zombie), 0, "still at first");
            context.assertTrue(player.interact(zombie, Hand.MAIN_HAND).isAccepted(), "the click is taken");
            context.assertEquals(pose(zombie), 1, "next pose");
            player.interact(zombie, Hand.MAIN_HAND);
            context.assertEquals(pose(zombie), 2, "and the next");
            context.assertTrue(zombie.getDataTracker().isDirty(), "synced to the clients");
            // An item in hand: not a pose (the item does its thing)
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
            player.interact(zombie, Hand.MAIN_HAND);
            context.assertEquals(pose(zombie), 2, "an item does not pose it");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aRegularMobIsNotPosed(TestContext context) {
        ZombieEntity zombie = context.spawnMob(EntityType.ZOMBIE, MOB_POS);
        ServerPlayerEntity player = player(context);
        try {
            player.interact(zombie, Hand.MAIN_HAND);
            context.assertEquals(pose(zombie), 0, "only pawns have poses");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aHorsePawnIsRiddenOrPosedSneaking(TestContext context) {
        HorseEntity horse = pawn(context, EntityType.HORSE);
        ServerPlayerEntity player = player(context);
        try {
            player.interact(horse, Hand.MAIN_HAND);
            context.assertEquals(pose(horse), 0, "an empty hand rides it");
            player.stopRiding();
            player.setSneaking(true);
            player.interact(horse, Hand.MAIN_HAND);
            context.assertEquals(pose(horse), 1, "sneaking poses it");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void thePoseIsSavedAndKeptInAToken(TestContext context) {
        ZombieEntity zombie = pawn(context, EntityType.ZOMBIE);
        ((TokenizedEntityInterface) zombie).steveparty$setTokenPose(5);
        NbtCompound nbt = new NbtCompound();
        zombie.writeNbt(nbt);
        ZombieEntity loaded = EntityType.ZOMBIE.create(context.getWorld());
        context.assertTrue(loaded != null, "created");
        loaded.readNbt(nbt);
        context.assertEquals(pose(loaded), 5, "pose saved");

        ServerPlayerEntity player = player(context);
        try {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.TOKEN));
            context.assertTrue(player.interact(zombie, Hand.MAIN_HAND).isAccepted(), "stored");
            NbtCompound stored = player.getMainHandStack().get(ModComponents.ENTITY_DATA_COMPONENT).entityData();
            context.assertEquals(stored.getInt("TokenPose"), 5, "pose kept in the Token");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aPlayerPawnKeepsItsStatuePoses(TestContext context) {
        PlayerPawnEntity pawn = context.spawnEntity(ModEntities.PLAYER_PAWN, MOB_POS);
        ((TokenizedEntityInterface) pawn).steveparty$setTokenized(true);
        ServerPlayerEntity player = player(context);
        try {
            var before = pawn.getStatuePose();
            player.interact(pawn, Hand.MAIN_HAND);
            context.assertTrue(pawn.getStatuePose() == before.next(), "its statue pose changes");
            context.assertEquals(pose(pawn), 0, "not a mob pose");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }
}
