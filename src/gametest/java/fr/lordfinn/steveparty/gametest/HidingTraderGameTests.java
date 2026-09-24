package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.HidingTraderEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.BandanaItem;
import fr.lordfinn.steveparty.persistent_state.VendorLinkPersistentState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.List;

/** Hiding Trader: bandana theft and give-back, wearable Bandana, who he pays attention to, wandering, grid alignment. */
public class HidingTraderGameTests implements FabricGameTest {

    private static void floor(TestContext context) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static HidingTraderEntity trader(TestContext context) {
        return context.spawnEntity(ModEntities.HIDING_TRADER_ENTITY, new BlockPos(3, 1, 3));
    }

    /** A survival player right next to the trader (so he comes out). */
    private static ServerPlayerEntity playerNear(TestContext context, HidingTraderEntity trader) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        player.setPosition(trader.getPos().add(0, 0, 2));
        return player;
    }

    private static void disconnect(TestContext context, ServerPlayerEntity player) {
        context.getWorld().getServer().getPlayerManager().remove(player);
    }

    /** Runs the checks, always disconnecting the mock player (a stray player would open other tests' traders). */
    private static void checks(TestContext context, ServerPlayerEntity player, Runnable checks) {
        try {
            checks.run();
        } finally {
            disconnect(context, player);
        }
        context.complete();
    }

    private static List<ItemEntity> droppedBandanas(TestContext context, HidingTraderEntity trader) {
        return context.getWorld().getEntitiesByClass(ItemEntity.class, trader.getBoundingBox().expand(3),
                item -> item.getStack().isOf(ModItems.BANDANA));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shearsStealTheBandanaOfAnOpenMerchant(TestContext context) {
        floor(context);
        HidingTraderEntity trader = trader(context);
        ServerPlayerEntity player = playerNear(context, trader);
        context.waitAndRun(3, () -> {
            try {
                context.assertFalse(trader.isHidden(), "open with a player next to him");
                int color = trader.getBandanaColor();
                player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SHEARS));
                trader.interact(player, Hand.MAIN_HAND);
                context.assertFalse(trader.hasBandana(), "bald after the theft");
                context.assertEquals(player.getMainHandStack().getDamage(), 1, "shears took 1 damage");
                List<ItemEntity> drops = droppedBandanas(context, trader);
                context.assertEquals(drops.size(), 1, "one bandana dropped");
                context.assertEquals(BandanaItem.getColor(drops.getFirst().getStack()), color, "in the merchant's colour");
            } catch (RuntimeException e) {
                disconnect(context, player);
                throw e;
            }
            context.waitAndRun(HidingTraderEntity.SHOCK_TICKS + 3, () -> checks(context, player, () -> {
                context.assertTrue(trader.isHidden(), "closed after the shock, although the player is still next to him");
                context.assertTrue(trader.isTheftHidden(), "theft cooldown running");
            }));
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shearsDoNothingOnAClosedOrBaldMerchant(TestContext context) {
        floor(context);
        HidingTraderEntity closed = trader(context);
        HidingTraderEntity bald = context.spawnEntity(ModEntities.HIDING_TRADER_ENTITY, new BlockPos(5, 1, 3));
        ServerPlayerEntity player = playerNear(context, closed);
        closed.startTheftHiding(0);
        bald.setHasBandana(false);
        context.waitAndRun(3, () -> checks(context, player, () -> {
            context.assertTrue(closed.isHidden(), "closed");
            context.assertFalse(bald.isHidden(), "the bald one is open");
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SHEARS));
            closed.interact(player, Hand.MAIN_HAND);
            context.assertTrue(closed.hasBandana(), "closed: keeps his bandana");
            bald.interact(player, Hand.MAIN_HAND);
            context.assertFalse(bald.hasBandana(), "bald: still bald");
            context.assertEquals(player.getMainHandStack().getDamage(), 0, "shears not used");
            context.assertTrue(droppedBandanas(context, closed).isEmpty() && droppedBandanas(context, bald).isEmpty(), "nothing dropped");
        }));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aBandanaCanBeGivenBackToABaldMerchant(TestContext context) {
        floor(context);
        HidingTraderEntity trader = trader(context);
        trader.setHasBandana(false);
        trader.setBandanaColor(0);
        ServerPlayerEntity player = playerNear(context, trader);
        context.waitAndRun(3, () -> checks(context, player, () -> {
            player.setStackInHand(Hand.MAIN_HAND, BandanaItem.create(3));
            trader.interact(player, Hand.MAIN_HAND);
            context.assertTrue(trader.hasBandana(), "wears a bandana again");
            context.assertEquals(trader.getBandanaColor(), 3, "the given bandana's colour");
            context.assertTrue(player.getMainHandStack().isEmpty(), "bandana used up");
        }));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bandanaStateIsSaved(TestContext context) {
        HidingTraderEntity trader = context.spawnEntity(ModEntities.HIDING_TRADER_ENTITY, new BlockPos(1, 1, 1));
        trader.setBandanaColor(2);
        trader.setHasBandana(false);
        trader.startTheftHiding(0);
        NbtCompound saved = trader.writeNbt(new NbtCompound());
        HidingTraderEntity loaded = ModEntities.HIDING_TRADER_ENTITY.create(context.getWorld(), SpawnReason.LOAD);
        loaded.readNbt(saved);
        context.assertFalse(loaded.hasBandana(), "still bald");
        context.assertEquals(loaded.getBandanaColor(), 2, "colour kept");
        context.assertTrue(loaded.isTheftHidden(), "theft cooldown kept");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aSneakingPlayerMakesHimComeOut(TestContext context) {
        floor(context);
        HidingTraderEntity trader = trader(context);
        ServerPlayerEntity player = playerNear(context, trader);
        player.setSneaking(true);
        context.waitAndRun(3, () -> checks(context, player, () ->
                context.assertFalse(trader.isHidden(), "open: sneaking no longer matters")));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void heIgnoresPlayersWearingABandana(TestContext context) {
        floor(context);
        HidingTraderEntity trader = trader(context);
        ServerPlayerEntity wearer = playerNear(context, trader);
        wearer.equipStack(EquipmentSlot.HEAD, BandanaItem.create(1));
        context.waitAndRun(3, () -> checks(context, wearer, () -> {
            context.assertFalse(trader.isHidden(), "a Bandana wearer still makes him come out");
            context.assertFalse(HidingTraderEntity.isAttentionTarget(wearer), "but he doesn't pay attention to him");
            context.assertTrue(trader.findAttentionTarget(HidingTraderEntity.OPEN_RANGE) != wearer, "never his look target");
            wearer.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
            context.assertTrue(HidingTraderEntity.isAttentionTarget(wearer), "without it, he does");
        }));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theBandanaIsWornOnTheHead(TestContext context) {
        ItemStack bandana = BandanaItem.create(2);
        EquippableComponent equippable = bandana.get(DataComponentTypes.EQUIPPABLE);
        context.assertTrue(equippable != null && equippable.slot() == EquipmentSlot.HEAD, "equippable in the head slot");
        context.assertTrue(equippable.model().isPresent() && equippable.model().get().equals(Steveparty.id("bandana_pink")), "pink equipment model");
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        checks(context, player, () -> {
            context.assertEquals(player.getPreferredEquipmentSlot(bandana), EquipmentSlot.HEAD, "goes to the head");
            context.assertTrue(player.canEquip(bandana, EquipmentSlot.HEAD), "the head slot accepts it");
            player.setStackInHand(Hand.MAIN_HAND, bandana.copy());
            player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
            context.assertTrue(player.getEquippedStack(EquipmentSlot.HEAD).isOf(ModItems.BANDANA), "right click equips it");
            ArmorStandEntity stand = context.spawnEntity(net.minecraft.entity.EntityType.ARMOR_STAND, new BlockPos(1, 1, 1));
            context.assertTrue(stand.canEquip(bandana, EquipmentSlot.HEAD), "armour stands can wear it");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 700)
    public void anUnassignedMerchantWandersAround(TestContext context) {
        floor(context);
        HidingTraderEntity trader = trader(context);
        ServerPlayerEntity player = playerNear(context, trader);
        Vec3d start = trader.getPos();
        context.runAtEveryTick(() -> {
            if (trader.getPos().subtract(start).horizontalLength() > 0.75) {
                checks(context, player, () -> context.assertFalse(trader.isAssigned(), "unassigned"));
            }
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void anAssignedMerchantStaysPut(TestContext context) {
        floor(context);
        HidingTraderEntity trader = trader(context);
        VendorLinkPersistentState.get(context.getWorld().getServer()).linkBlock(trader.getUuid(),
                GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(new BlockPos(0, 1, 0))));
        ServerPlayerEntity player = playerNear(context, trader);
        Vec3d start = trader.getPos();
        context.waitAndRun(320, () -> checks(context, player, () -> {
            context.assertTrue(trader.isAssigned(), "assigned");
            context.assertTrue(trader.getPos().subtract(start).horizontalLength() < 0.3, "did not wander");
        }));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aClosedMerchantIsAlignedWithTheGrid(TestContext context) {
        floor(context);
        HidingTraderEntity trader = context.spawnEntity(ModEntities.HIDING_TRADER_ENTITY, new Vec3d(3.3, 1, 2.8));
        trader.setYaw(37);
        trader.setBodyYaw(37);
        trader.setHeadYaw(37);
        trader.startTheftHiding(0);
        context.waitAndRun(3, () -> {
            context.assertTrue(trader.isHidden(), "closed");
            context.assertTrue(Math.abs(MathHelper.fractionalPart(trader.getX()) - 0.5) < 1e-6
                    && Math.abs(MathHelper.fractionalPart(trader.getZ()) - 0.5) < 1e-6, "on a block centre: " + trader.getPos());
            context.assertTrue(Math.abs(MathHelper.wrapDegrees(trader.getYaw()) % 90) < 1e-3, "yaw on a quarter turn: " + trader.getYaw());
            context.assertTrue(Math.abs(MathHelper.wrapDegrees(trader.getBodyYaw()) % 90) < 1e-3, "body yaw on a quarter turn: " + trader.getBodyYaw());
            context.complete();
        });
    }
}
