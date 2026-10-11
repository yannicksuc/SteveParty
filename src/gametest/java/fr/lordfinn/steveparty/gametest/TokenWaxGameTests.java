package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenWax;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnEntity;
import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnPose;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

/**
 * Waxed pawns (like waxed copper): a honeycomb waxes a pawn, its pose no longer changes; an axe scrapes the wax off.
 */
public class TokenWaxGameTests implements SteveGameTest {
    private static final BlockPos MOB_POS = new BlockPos(2, 2, 2);

    private static ServerPlayerEntity player(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
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
    public void waxFreezesThePoseAndAnAxeUnwaxes(TestContext context) {
        ZombieEntity zombie = pawn(context, EntityType.ZOMBIE);
        ServerPlayerEntity player = player(context);
        try {
            player.interact(zombie, Hand.MAIN_HAND);
            context.assertEquals(pose(zombie), 1, "posed before the wax");

            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.HONEYCOMB, 2));
            context.assertTrue(player.interact(zombie, Hand.MAIN_HAND).isAccepted(), "the honeycomb is taken");
            context.assertTrue(TokenWax.isWaxed(zombie), "waxed");
            context.assertEquals(player.getMainHandStack().getCount(), 1, "one honeycomb used");
            context.assertTrue(zombie.getDataTracker().isDirty(), "synced to the clients");
            player.interact(zombie, Hand.MAIN_HAND);
            context.assertEquals(player.getMainHandStack().getCount(), 1, "already waxed: nothing used");

            // An empty hand: the click is taken, the pose stays
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            context.assertTrue(player.interact(zombie, Hand.MAIN_HAND).isAccepted(), "the click is taken");
            player.interact(zombie, Hand.MAIN_HAND);
            context.assertEquals(pose(zombie), 1, "waxed: the pose is frozen");

            ItemStack axe = new ItemStack(Items.IRON_AXE);
            player.setStackInHand(Hand.MAIN_HAND, axe);
            context.assertTrue(player.interact(zombie, Hand.MAIN_HAND).isAccepted(), "the axe is taken");
            context.assertFalse(TokenWax.isWaxed(zombie), "unwaxed");
            context.assertEquals(player.getMainHandStack().getDamage(), 1, "the axe is worn by one");
            // A bare pawn: the axe does nothing more
            player.interact(zombie, Hand.MAIN_HAND);
            context.assertEquals(player.getMainHandStack().getDamage(), 1, "nothing to scrape");

            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            player.interact(zombie, Hand.MAIN_HAND);
            context.assertEquals(pose(zombie), 2, "unwaxed: posed again");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aRegularMobIsNotWaxed(TestContext context) {
        ZombieEntity zombie = context.spawnMob(EntityType.ZOMBIE, MOB_POS);
        ServerPlayerEntity player = player(context);
        try {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.HONEYCOMB, 2));
            player.interact(zombie, Hand.MAIN_HAND);
            context.assertFalse(TokenWax.isWaxed(zombie), "only pawns are waxed");
            context.assertEquals(player.getMainHandStack().getCount(), 2, "nothing used");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aWaxedPlayerPawnKeepsItsStatuePose(TestContext context) {
        PlayerPawnEntity pawn = context.spawnEntity(ModEntities.PLAYER_PAWN, MOB_POS);
        ((TokenizedEntityInterface) pawn).steveparty$setTokenized(true);
        ServerPlayerEntity player = player(context);
        try {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.HONEYCOMB));
            player.interact(pawn, Hand.MAIN_HAND);
            context.assertTrue(TokenWax.isWaxed(pawn), "waxed");
            context.assertTrue(pawn.getMainHandStack().isEmpty(), "the honeycomb waxes, it is not handed to the statue");
            PlayerPawnPose before = pawn.getStatuePose();
            player.interact(pawn, Hand.MAIN_HAND);
            context.assertTrue(pawn.getStatuePose() == before, "its statue pose stays");

            // Sneaking, it still takes an item in hand (not a pose)
            player.setSneaking(true);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
            player.interact(pawn, Hand.MAIN_HAND);
            context.assertTrue(pawn.getMainHandStack().isOf(Items.STICK), "the held item still swaps");
            player.setSneaking(false);

            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_AXE));
            player.interact(pawn, Hand.MAIN_HAND);
            context.assertFalse(TokenWax.isWaxed(pawn), "unwaxed");
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            player.interact(pawn, Hand.MAIN_HAND);
            context.assertTrue(pawn.getStatuePose() == before.next(), "posed again");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theWaxIsSavedAndKeptInAToken(TestContext context) {
        ZombieEntity zombie = pawn(context, EntityType.ZOMBIE);
        ((TokenizedEntityInterface) zombie).steveparty$setTokenWaxed(true);
        NbtCompound nbt = new NbtCompound();
        zombie.writeNbt(nbt);
        ZombieEntity loaded = EntityType.ZOMBIE.create(context.getWorld());
        context.assertTrue(loaded != null, "created");
        loaded.readNbt(nbt);
        context.assertTrue(TokenWax.isWaxed(loaded), "wax saved");

        ServerPlayerEntity player = player(context);
        try {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.TOKEN));
            context.assertTrue(player.interact(zombie, Hand.MAIN_HAND).isAccepted(), "stored");
            NbtCompound stored = player.getMainHandStack().get(ModComponents.ENTITY_DATA_COMPONENT).entityData();
            context.assertTrue(stored.getBoolean("TokenWaxed"), "wax kept in the Token");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }
}
