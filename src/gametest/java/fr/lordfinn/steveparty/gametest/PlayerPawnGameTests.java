package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.pawn.PawnPossessions;
import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnEntity;
import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnPose;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.TokenizerWandItem;
import fr.lordfinn.steveparty.items.custom.TokenizerWandItem.SpellResult;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameRules;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Player pawns: the wand's spell on a player makes a pawn with the player inside; whatever ends it (sneak, the pawn
 * stored or removed, the player leaving or dying) the player comes out at their size and the pawn stays. The statue's
 * held item is never copied nor lost.
 */
public class PlayerPawnGameTests implements SteveGameTest {
    private static final BlockPos TARGET_POS = new BlockPos(3, 2, 3);
    private static final int BLUE = 0x3366CC;
    /** First tick at which the pawn exists and holds its player, camera included. */
    private static final int POSSESSED_TICK = TokenizerWandItem.TRANSFORM_DURATION + 6;

    // ---------------------------------------------------------------- helpers

    private static ServerPlayerEntity playerAt(TestContext context, double x, double z) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        TestPlayers.place(context, player, x, TARGET_POS.getY(), z);
        return player;
    }

    private static void leave(TestContext context, ServerPlayerEntity... players) {
        for (ServerPlayerEntity player : players) {
            if (context.getWorld().getServer().getPlayerManager().getPlayer(player.getUuid()) != null) {
                TestPlayers.remove(context, player);
            }
        }
    }

    /** The pawns made of {@code player} (the tests run side by side: other tests have their own). */
    private static List<PlayerPawnEntity> pawns(TestContext context, ServerPlayerEntity player) {
        Box area = new Box(context.getAbsolutePos(BlockPos.ORIGIN)).expand(16);
        return context.getWorld().getEntitiesByClass(PlayerPawnEntity.class, area,
                pawn -> pawn.isAlive() && player.getUuid().equals(pawn.getSkinOwner()));
    }

    /**
     * The caster's spell turns the target into a pawn; once the target is inside it (camera included), {@code then}
     * runs with the target and the pawn. Both players leave at the end.
     */
    private static void withPossessedPawn(TestContext context, BiConsumer<ServerPlayerEntity, PlayerPawnEntity> then, Runnable checks, int checkTick) {
        ServerPlayerEntity caster = playerAt(context, 1.5, 1.5);
        caster.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.TOKENIZER_WAND));
        ServerPlayerEntity target = playerAt(context, TARGET_POS.getX() + 0.5, TARGET_POS.getZ() + 0.5);
        SpellResult result = TokenizerWandItem.castSpell(caster, target.getId(), 1F, BLUE);
        context.assertTrue(result == SpellResult.TOKENIZED, "the spell takes the player: " + result);
        context.runAtTick(POSSESSED_TICK, () -> {
            List<PlayerPawnEntity> pawns = pawns(context, target);
            context.assertTrue(pawns.size() == 1, "one pawn: " + pawns);
            then.accept(target, pawns.getFirst());
        });
        context.runAtTick(checkTick, () -> {
            try {
                checks.run();
            } finally {
                leave(context, caster, target);
            }
            context.complete();
        });
    }

    private static boolean normal(ServerPlayerEntity player) {
        return Math.abs(player.getScale() - 1.0F) < 1.0E-3 && !player.isInvisible() && player.getCameraEntity() == player
                && !PawnPossessions.isInsideAPawn(player);
    }

    // ---------------------------------------------------------------- the spell

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void theSpellPutsThePlayerInsideAPawn(TestContext context) {
        ServerPlayerEntity caster = playerAt(context, 1.5, 1.5);
        caster.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.TOKENIZER_WAND));
        ServerPlayerEntity target = playerAt(context, TARGET_POS.getX() + 0.5, TARGET_POS.getZ() + 0.5);
        SpellResult result = TokenizerWandItem.castSpell(caster, target.getId(), 1F, BLUE);
        context.assertTrue(result == SpellResult.TOKENIZED, "tokenized: " + result);
        context.assertTrue(PawnPossessions.isUnderSpell(target), "under the spell");
        context.assertTrue(Math.abs(target.getScale() - 1F / PlayerPawnEntity.HEIGHT) < 1.0E-3, "shrunk to the pawn's size: " + target.getScale());
        context.assertTrue(target.getMovementSpeed() <= 1.0E-6, "can't move: " + target.getMovementSpeed());
        // One pawn per player: not twice under the spell
        caster.getItemCooldownManager().remove(ModItems.TOKENIZER_WAND);
        context.assertTrue(TokenizerWandItem.castSpell(caster, target.getId(), 1F, BLUE) == SpellResult.NOT_ALLOWED, "not twice");
        context.runAtTick(POSSESSED_TICK, () -> {
            try {
                List<PlayerPawnEntity> pawns = pawns(context, target);
                context.assertTrue(pawns.size() == 1, "one pawn: " + pawns);
                PlayerPawnEntity pawn = pawns.getFirst();
                TokenizedEntityInterface token = (TokenizedEntityInterface) pawn;
                context.assertTrue(token.steveparty$isTokenized(), "the pawn is a token");
                context.assertTrue(caster.getUuid().equals(token.steveparty$getTokenOwner()), "owned by the caster");
                context.assertTrue(token.steveparty$getTokenSize() == 1F, "chosen size");
                context.assertEquals(token.steveparty$getTokenColor(), BLUE, "chosen colour");
                context.assertTrue(target.getUuid().equals(pawn.getSkinOwner()), "wears the target's skin");
                context.assertTrue(target.getUuid().equals(pawn.getPossessor()), "the target is inside");
                context.assertTrue(PawnPossessions.isInsideAPawn(target), "inside a pawn");
                context.assertTrue(target.isInvisible(), "invisible");
                context.assertTrue(target.getCameraEntity() == pawn, "sees from the pawn");
                context.assertTrue(target.getBoundingBox().minY >= pawn.getBoundingBox().minY - 1.0E-3
                        && pawn.getBoundingBox().contains(target.getBoundingBox().getCenter()), "hitbox inside the pawn's");
                caster.getItemCooldownManager().remove(ModItems.TOKENIZER_WAND);
                context.assertTrue(TokenizerWandItem.castSpell(caster, target.getId(), 1F, BLUE) == SpellResult.NOT_ALLOWED,
                        "not a second pawn while in one");
            } finally {
                leave(context, caster, target);
            }
            context.complete();
        });
    }

    /** The spell on a player: up to five times a player's size (9 blocks), clamped by the server. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void aPlayerPawnIsAtMostFiveTimesAPlayer(TestContext context) {
        ServerPlayerEntity caster = playerAt(context, 1.5, 1.5);
        caster.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.TOKENIZER_WAND));
        ServerPlayerEntity target = playerAt(context, TARGET_POS.getX() + 0.5, TARGET_POS.getZ() + 0.5);
        context.assertTrue(TokenizerWandItem.castSpell(caster, target.getId(), 50F, BLUE) == SpellResult.TOKENIZED, "tokenized");
        context.runAtTick(POSSESSED_TICK, () -> {
            try {
                List<PlayerPawnEntity> pawns = pawns(context, target);
                context.assertTrue(pawns.size() == 1, "one pawn");
                float size = ((TokenizedEntityInterface) pawns.getFirst()).steveparty$getTokenSize();
                context.assertTrue(Math.abs(size - 9.0F) < 0.02F, "9 blocks: " + size);
            } finally {
                leave(context, caster, target);
            }
            context.complete();
        });
    }

    // ---------------------------------------------------------------- getting out

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void sneakingGetsOutAndThePawnStays(TestContext context) {
        PlayerPawnEntity[] pawn = new PlayerPawnEntity[1];
        ServerPlayerEntity[] target = new ServerPlayerEntity[1];
        withPossessedPawn(context, (player, found) -> {
            pawn[0] = found;
            target[0] = player;
            player.setSneaking(true);
        }, () -> {
            context.assertTrue(normal(target[0]), "back to normal");
            context.assertTrue(pawn[0].isAlive() && !pawn[0].isPossessed(), "the pawn stays, empty");
            context.assertTrue(target[0].squaredDistanceTo(pawn[0]) < 3 * 3, "next to the pawn");
        }, POSSESSED_TICK + 3);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void storingThePawnInATokenGetsThePlayerOut(TestContext context) {
        ServerPlayerEntity[] target = new ServerPlayerEntity[1];
        ServerPlayerEntity holder = playerAt(context, 5.5, 3.5);
        ItemStack[] token = new ItemStack[1];
        withPossessedPawn(context, (player, pawn) -> {
            target[0] = player;
            pawn.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND, 3));
            // Its owner (the caster) or a creative player can store it: the holder is creative
            holder.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.TOKEN));
            ActionResult result = holder.interact(pawn, Hand.MAIN_HAND);
            context.assertTrue(result.isAccepted(), "stored: " + result);
            token[0] = holder.getMainHandStack();
            context.assertTrue(pawn.isRemoved(), "the pawn is in the Token");
            context.assertTrue(normal(player), "out at once");
        }, () -> {
            try {
                context.assertTrue(normal(target[0]), "still out");
                NbtCompound data = token[0].get(ModComponents.ENTITY_DATA_COMPONENT).entityData();
                context.assertTrue(data.getString("SkinName").equals(target[0].getGameProfile().getName()), "the statue is in the Token");
                context.assertTrue(data.getList("HandItems", 10).getCompound(0).getInt("count") == 3, "with its diamonds: " + data.get("HandItems"));
            } finally {
                leave(context, holder);
            }
        }, POSSESSED_TICK + 2);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void aRemovedPawnLetsThePlayerOut(TestContext context) {
        ServerPlayerEntity[] target = new ServerPlayerEntity[1];
        withPossessedPawn(context, (player, pawn) -> {
            target[0] = player;
            pawn.discard();
            context.assertTrue(normal(player), "out at once");
        }, () -> context.assertTrue(normal(target[0]), "still out"), POSSESSED_TICK + 2);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void leavingTheServerGetsOutCleanly(TestContext context) {
        PlayerPawnEntity[] pawn = new PlayerPawnEntity[1];
        ServerPlayerEntity[] back = new ServerPlayerEntity[1];
        withPossessedPawn(context, (player, found) -> {
            pawn[0] = found;
            TestPlayers.leave(player);
            context.assertTrue(!PawnPossessions.isInsideAPawn(player), "out when leaving");
            context.assertTrue(found.isAlive() && !found.isPossessed(), "the pawn stays, empty");
            back[0] = TestPlayers.join(context, player.getGameProfile());
        }, () -> {
            try {
                context.assertTrue(normal(back[0]), "joins back at their size, visible: scale " + back[0].getScale());
                context.assertTrue(!pawn[0].isPossessed(), "not back in the pawn");
            } finally {
                leave(context, back[0]);
            }
        }, POSSESSED_TICK + 2);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void dyingGetsOut(TestContext context) {
        PlayerPawnEntity[] pawn = new PlayerPawnEntity[1];
        ServerPlayerEntity[] target = new ServerPlayerEntity[1];
        withPossessedPawn(context, (player, found) -> {
            pawn[0] = found;
            target[0] = player;
            // Inside, only what bypasses invulnerability hurts
            context.assertTrue(!player.damage(player.getDamageSources().generic(), 5), "safe inside the pawn");
            player.kill();
        }, () -> {
            context.assertTrue(!PawnPossessions.isInsideAPawn(target[0]), "out when dying");
            context.assertTrue(pawn[0].isAlive() && !pawn[0].isPossessed(), "the pawn stays, empty");
        }, POSSESSED_TICK + 2);
    }

    // ---------------------------------------------------------------- the statue

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void theStatueHoldsAnItemWithoutCopyingIt(TestContext context) {
        PlayerPawnEntity pawn = context.spawnEntity(ModEntities.PLAYER_PAWN, TARGET_POS);
        ServerPlayerEntity player = playerAt(context, 1.5, 1.5);
        try {
            player.setSneaking(true);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DIAMOND, 5));
            context.assertTrue(player.interact(pawn, Hand.MAIN_HAND).isAccepted(), "given");
            context.assertTrue(pawn.getHeldItem().isOf(Items.DIAMOND) && pawn.getHeldItem().getCount() == 5, "holds the 5 diamonds");
            context.assertTrue(player.getMainHandStack().isEmpty(), "out of the hand");

            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.EMERALD, 2));
            context.assertTrue(player.interact(pawn, Hand.MAIN_HAND).isAccepted(), "swapped");
            context.assertTrue(pawn.getHeldItem().isOf(Items.EMERALD) && pawn.getHeldItem().getCount() == 2, "holds the emeralds");
            context.assertTrue(player.getMainHandStack().isOf(Items.DIAMOND) && player.getMainHandStack().getCount() == 5, "diamonds back");

            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            context.assertTrue(player.interact(pawn, Hand.MAIN_HAND).isAccepted(), "taken back");
            context.assertTrue(pawn.getHeldItem().isEmpty(), "empty-handed statue");
            context.assertTrue(player.getMainHandStack().isOf(Items.EMERALD) && player.getMainHandStack().getCount() == 2, "emeralds back");

            // Not sneaking: the next pose, the item stays where it is
            player.setSneaking(false);
            PlayerPawnPose before = pawn.getStatuePose();
            context.assertTrue(player.interact(pawn, Hand.MAIN_HAND).isAccepted(), "posed");
            context.assertTrue(pawn.getStatuePose() == before.next(), "next pose");
            context.assertTrue(player.getMainHandStack().getCount() == 2 && pawn.getHeldItem().isEmpty(), "nothing moved");
        } finally {
            leave(context, player);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void aKilledStatueDropsItsItemOnce(TestContext context) {
        PlayerPawnEntity pawn = context.spawnEntity(ModEntities.PLAYER_PAWN, TARGET_POS);
        pawn.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND, 4));
        GameRules.BooleanRule mobLoot = context.getWorld().getGameRules().get(GameRules.DO_MOB_LOOT);
        boolean wasOn = mobLoot.get();
        // Even without mob loot: the item is the player's
        mobLoot.set(false, context.getWorld().getServer());
        try {
            pawn.kill();
        } finally {
            mobLoot.set(wasOn, context.getWorld().getServer());
        }
        context.runAtTick(3, () -> {
            Box area = new Box(context.getAbsolutePos(TARGET_POS)).expand(3);
            int diamonds = context.getWorld().getEntitiesByClass(ItemEntity.class, area, item -> item.getStack().isOf(Items.DIAMOND))
                    .stream().mapToInt(item -> item.getStack().getCount()).sum();
            context.assertEquals(diamonds, 4, "the 4 diamonds dropped, once");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void theStatueIsSavedWithItsPoseSkinAndItem(TestContext context) {
        PlayerPawnEntity pawn = context.spawnEntity(ModEntities.PLAYER_PAWN, TARGET_POS);
        UUID skin = UUID.randomUUID();
        pawn.setSkin(skin, "Someone");
        pawn.setStatuePose(PlayerPawnPose.DAB);
        pawn.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_APPLE, 2));
        NbtCompound nbt = new NbtCompound();
        pawn.writeNbt(nbt);
        pawn.discard();
        PlayerPawnEntity loaded = ModEntities.PLAYER_PAWN.create(context.getWorld());
        context.assertTrue(loaded != null, "created");
        loaded.readNbt(nbt);
        context.assertTrue(loaded.getStatuePose() == PlayerPawnPose.DAB, "pose kept");
        context.assertTrue(skin.equals(loaded.getSkinOwner()) && loaded.getSkinName().equals("Someone"), "skin kept");
        context.assertTrue(loaded.getHeldItem().isOf(Items.GOLDEN_APPLE) && loaded.getHeldItem().getCount() == 2, "item kept");
        context.assertTrue(!loaded.isPossessed(), "nobody inside after a reload");
        context.complete();
    }
}
