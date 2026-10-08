package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.frousseux.FrousseuxCandleHolderBlock;
import fr.lordfinn.steveparty.blocks.custom.frousseux.FrousseuxCandleHolderBlockEntity;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxCompanion;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * The Frousseux's second step: what it steals (the shiny tag, one item of one stack), its melee dodge, taming it
 * (what it stole given back), what it drops, and the cobwebs its owner walks through.
 */
public class FrousseuxGameTests implements FabricGameTest {

    private static void floor(TestContext context) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
    }

    private static FrousseuxEntity frousseux(TestContext context, BlockPos at) {
        FrousseuxEntity one = context.spawnEntity(ModEntities.FROUSSEUX, at);
        one.setAiDisabled(true);
        return one;
    }

    private static ServerPlayerEntity player(TestContext context, BlockPos at) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        Vec3d pos = context.getAbsolute(Vec3d.ofBottomCenter(at));
        player.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
        player.getInventory().clear();
        return player;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itsShinyThingsAreTagged(TestContext context) {
        for (var item : new net.minecraft.item.Item[]{Items.IRON_INGOT, Items.GOLD_NUGGET, Items.RAW_COPPER, Items.DIAMOND,
                Items.EMERALD, Items.LAPIS_LAZULI, Items.REDSTONE, Items.AMETHYST_SHARD}) {
            context.assertTrue(new ItemStack(item).isIn(FrousseuxEntity.SHINY), item + " is shiny");
        }
        context.assertFalse(new ItemStack(Items.COBBLESTONE).isIn(FrousseuxEntity.SHINY), "cobblestone is not");
        context.assertFalse(new ItemStack(Items.IRON_SWORD).isIn(FrousseuxEntity.SHINY), "an iron sword is not");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itStealsOneItemNeverTheStack(TestContext context) {
        floor(context);
        FrousseuxEntity frousseux = frousseux(context, new BlockPos(3, 1, 3));
        ServerPlayerEntity player = player(context, new BlockPos(3, 1, 4));
        player.getInventory().setStack(0, new ItemStack(Items.COBBLESTONE, 10));
        player.getInventory().setStack(5, new ItemStack(Items.DIAMOND, 5));
        context.assertTrue(frousseux.stealFrom(player), "it steals");
        context.assertTrue(player.getInventory().getStack(5).getCount() == 4, "one diamond gone, four left");
        context.assertTrue(player.getInventory().getStack(0).getCount() == 10, "the cobblestone left alone");
        context.assertTrue(frousseux.getStolen().isOf(Items.DIAMOND) && frousseux.getStolen().getCount() == 1, "it carries one");
        context.assertTrue(frousseux.isFleeing(), "it flees");
        context.assertFalse(frousseux.stealFrom(player), "one stolen thing at a time");
        context.assertTrue(player.getInventory().getStack(5).getCount() == 4, "still four");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nothingShinyNothingStolen(TestContext context) {
        floor(context);
        FrousseuxEntity frousseux = frousseux(context, new BlockPos(3, 1, 3));
        ServerPlayerEntity player = player(context, new BlockPos(3, 1, 4));
        player.getInventory().setStack(0, new ItemStack(Items.COBBLESTONE, 10));
        context.assertFalse(frousseux.stealFrom(player), "nothing to steal");
        context.assertTrue(frousseux.getStolen().isEmpty(), "carries nothing");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void meleeHitsAreDodged(TestContext context) {
        floor(context);
        FrousseuxEntity frousseux = frousseux(context, new BlockPos(3, 1, 3));
        ServerPlayerEntity player = player(context, new BlockPos(3, 1, 4));
        Vec3d before = frousseux.getPos();
        float health = frousseux.getHealth();
        boolean hurt = frousseux.damage(frousseux.getDamageSources().playerAttack(player), 6f);
        context.assertFalse(hurt, "no damage taken");
        context.assertTrue(frousseux.getHealth() == health, "health unchanged");
        context.assertTrue(frousseux.getPos().distanceTo(before) >= 2.0, "it slipped a few blocks away");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tamingGivesTheItemBack(TestContext context) {
        floor(context);
        FrousseuxEntity frousseux = frousseux(context, new BlockPos(3, 1, 3));
        ServerPlayerEntity player = player(context, new BlockPos(3, 1, 4));
        player.getInventory().setStack(2, new ItemStack(Items.GOLD_INGOT, 3));
        frousseux.stealFrom(player);
        frousseux.tame(player);
        context.assertTrue(frousseux.isTamed() && frousseux.isOwner(player), "tamed, theirs");
        context.assertTrue(frousseux.getStolen().isEmpty(), "carries nothing");
        context.assertTrue(player.getInventory().count(Items.GOLD_INGOT) == 3, "the gold ingot back");
        context.assertFalse(frousseux.stealFrom(player), "a tamed one never steals");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void flintAndSteelFirstGetsTheItemBack(TestContext context) {
        floor(context);
        FrousseuxEntity frousseux = frousseux(context, new BlockPos(3, 1, 3));
        ServerPlayerEntity player = player(context, new BlockPos(3, 1, 4));
        player.getInventory().setStack(2, new ItemStack(Items.EMERALD, 1));
        frousseux.stealFrom(player);
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        player.interact(frousseux, net.minecraft.util.Hand.MAIN_HAND);
        context.assertTrue(player.getInventory().count(Items.EMERALD) == 1, "the emerald back");
        context.assertFalse(frousseux.isTamed(), "not tamed by that strike");
        context.assertTrue(player.getMainHandStack().getDamage() == 1, "the flint and steel wears");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void killedItDropsWhatItStole(TestContext context) {
        floor(context);
        FrousseuxEntity frousseux = frousseux(context, new BlockPos(3, 1, 3));
        ServerPlayerEntity player = player(context, new BlockPos(3, 1, 4));
        player.getInventory().setStack(2, new ItemStack(Items.REDSTONE, 4));
        frousseux.stealFrom(player);
        frousseux.kill();
        context.runAtTick(context.getTick() + 2, () -> {
            boolean dropped = !context.getWorld().getEntitiesByClass(ItemEntity.class,
                    new Box(context.getAbsolutePos(BlockPos.ORIGIN)).expand(10),
                    item -> item.getStack().isOf(Items.REDSTONE)).isEmpty();
            context.assertTrue(dropped, "the redstone dropped");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cobwebsLetItsOwnerThrough(TestContext context) {
        floor(context);
        FrousseuxEntity frousseux = frousseux(context, new BlockPos(1, 1, 1));
        ServerPlayerEntity owner = player(context, new BlockPos(4, 1, 4));
        ServerPlayerEntity other = player(context, new BlockPos(4, 1, 5));
        context.assertFalse(FrousseuxCompanion.shieldsFromWebs(owner), "a wild one shields nobody");
        frousseux.tame(owner);
        context.assertTrue(FrousseuxCompanion.shieldsFromWebs(owner), "its owner, near, walks through");
        context.assertFalse(FrousseuxCompanion.shieldsFromWebs(other), "not someone else");
        Vec3d far = frousseux.getPos().add(9, 0, 0);
        owner.refreshPositionAndAngles(far.x, far.y, far.z, 0, 0);
        context.assertFalse(FrousseuxCompanion.shieldsFromWebs(owner), "too far from it: slowed again");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theCandleHolderKeepsTheSameFrousseux(TestContext context) {
        floor(context);
        FrousseuxEntity frousseux = frousseux(context, new BlockPos(3, 1, 3));
        ServerPlayerEntity owner = player(context, new BlockPos(3, 1, 5));
        frousseux.tame(owner);
        frousseux.setHealth(3f);
        frousseux.setCustomName(net.minecraft.text.Text.literal("Mèche"));
        frousseux.setColor(fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxColor.PURPLE);
        java.util.UUID uuid = frousseux.getUuid();
        BlockPos at = BlockPos.ofFloored(frousseux.getPos().add(0, 0.1, 0));
        context.assertTrue(FrousseuxCandleHolderBlock.fallAsleep(frousseux, context.getWorld(), owner), "it falls asleep");
        context.assertTrue(frousseux.isRemoved(), "the ghost is gone");
        var state = context.getWorld().getBlockState(at);
        context.assertTrue(state.isOf(ModBlocks.FROUSSEUX_CANDLE_HOLDER), "a candle holder where it floated");
        context.assertTrue(state.get(FrousseuxCandleHolderBlock.LIGHT) == FrousseuxEntity.Flame.LOW.light,
                "its light is its weak flame's: " + state.get(FrousseuxCandleHolderBlock.LIGHT));
        var holder = (FrousseuxCandleHolderBlockEntity) context.getWorld().getBlockEntity(at);
        ItemStack item = FrousseuxCandleHolderBlock.itemOf(holder);
        context.assertTrue(item.contains(net.minecraft.component.DataComponentTypes.BLOCK_ENTITY_DATA), "its item keeps it");
        context.assertTrue(FrousseuxCandleHolderBlock.keptIn(item).getUuid("UUID").equals(uuid), "the same one in the item");
        FrousseuxEntity awake = FrousseuxCandleHolderBlock.wakeUp(context.getWorld(), at);
        context.assertTrue(awake != null && awake.getUuid().equals(uuid), "it wakes up, the same one");
        context.assertTrue(context.getWorld().getBlockState(at).isAir(), "the block is gone");
        context.assertTrue(awake.getHealth() == 3f, "the same health");
        context.assertTrue(awake.isOwner(owner) && !awake.isSitting(), "its owner's, following");
        context.assertTrue("Mèche".equals(awake.getCustomName().getString()), "its name");
        context.assertTrue(awake.getColor() == fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxColor.PURPLE, "its colour");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itsFlameStageIsSyncedFromTheStart(TestContext context) {
        FrousseuxEntity frousseux = frousseux(context, new BlockPos(3, 1, 3));
        frousseux.setHealth(1f);
        context.assertTrue(frousseux.getFlame() == FrousseuxEntity.Flame.EMBER, "an ember at once");
        frousseux.setHealth(8f);
        context.assertTrue(frousseux.getFlame() == FrousseuxEntity.Flame.FULL, "full again");
        context.complete();
    }
}
