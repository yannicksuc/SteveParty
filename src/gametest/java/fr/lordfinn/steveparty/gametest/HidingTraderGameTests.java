package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.HidingTraderEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.BandanaItem;
import fr.lordfinn.steveparty.items.custom.BoxCostumeItem;
import fr.lordfinn.steveparty.persistent_state.VendorLinkPersistentState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.ZombieEntity;
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

/** Hiding Trader: bandana and box theft, Box Costume, wearable Bandana, who he pays attention to, wandering, grid alignment. */
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

    private static List<ItemEntity> droppedCostumes(TestContext context, HidingTraderEntity trader) {
        return context.getWorld().getEntitiesByClass(ItemEntity.class, trader.getBoundingBox().expand(3),
                item -> item.getStack().isOf(ModItems.BOX_COSTUME));
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
    public void shearsDoNothingOnAClosedOrBoxlessMerchant(TestContext context) {
        floor(context);
        HidingTraderEntity closed = trader(context);
        HidingTraderEntity boxless = context.spawnEntity(ModEntities.HIDING_TRADER_ENTITY, new BlockPos(5, 1, 3));
        ServerPlayerEntity player = playerNear(context, closed);
        closed.startTheftHiding(0);
        boxless.setHasBandana(false);
        boxless.setHasBox(false);
        context.waitAndRun(3, () -> checks(context, player, () -> {
            context.assertTrue(closed.isHidden(), "closed");
            context.assertFalse(boxless.isHidden(), "the boxless one is open");
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SHEARS));
            closed.interact(player, Hand.MAIN_HAND);
            context.assertTrue(closed.hasBandana() && closed.hasBox(), "closed: keeps his bandana and his box");
            boxless.interact(player, Hand.MAIN_HAND);
            context.assertFalse(boxless.hasBandana() || boxless.hasBox(), "boxless: still bald and boxless");
            context.assertEquals(player.getMainHandStack().getDamage(), 0, "shears not used");
            context.assertTrue(droppedBandanas(context, closed).isEmpty() && droppedBandanas(context, boxless).isEmpty()
                    && droppedCostumes(context, closed).isEmpty(), "nothing dropped");
        }));
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void shearingTwiceTakesTheBandanaThenTheBox(TestContext context) {
        floor(context);
        HidingTraderEntity trader = trader(context);
        trader.setBlockState(Blocks.OAK_PLANKS.getDefaultState());
        ServerPlayerEntity player = playerNear(context, trader);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SHEARS));
        context.waitAndRun(3, () -> {
            try {
                trader.interact(player, Hand.MAIN_HAND);
                context.assertFalse(trader.hasBandana(), "first shear: the bandana");
                context.assertTrue(trader.hasBox(), "keeps his box");
            } catch (RuntimeException e) {
                disconnect(context, player);
                throw e;
            }
            context.waitAndRun(HidingTraderEntity.SHOCK_TICKS + 3, () -> {
                try {
                    context.assertTrue(trader.isHidden(), "hidden after the theft");
                    // Shears on the closed box: nothing
                    trader.interact(player, Hand.MAIN_HAND);
                    context.assertTrue(trader.hasBox(), "closed: keeps his box");
                    // The theft cooldown is over: he comes out again
                    trader.startTheftHiding(-HidingTraderEntity.THEFT_HIDE_TICKS - 1);
                } catch (RuntimeException e) {
                    disconnect(context, player);
                    throw e;
                }
                context.waitAndRun(2, () -> checks(context, player, () -> {
                    context.assertFalse(trader.isHidden(), "out again");
                    trader.interact(player, Hand.MAIN_HAND);
                    context.assertFalse(trader.hasBox(), "second shear: the box");
                    List<ItemEntity> costumes = droppedCostumes(context, trader);
                    context.assertEquals(costumes.size(), 1, "one costume dropped");
                    context.assertTrue(BoxCostumeItem.getBlock(costumes.getFirst().getStack()).isOf(Blocks.OAK_PLANKS), "of his block");
                    // Nothing on him went out of range: still a valid block look and bandana colour
                    context.assertTrue(HidingTraderEntity.isValidBoxBlock(trader.getBlockState()) && trader.getBlockState().isOf(Blocks.OAK_PLANKS),
                            "block look kept: " + trader.getBlockState());
                    context.assertTrue(trader.getBandanaColor() >= 0 && trader.getBandanaColor() < HidingTraderEntity.BANDANA_COLORS, "bandana colour in range");
                    context.assertEquals(player.getMainHandStack().getDamage(), 2, "shears used twice");
                    trader.interact(player, Hand.MAIN_HAND);
                    context.assertEquals(droppedCostumes(context, trader).size(), 1, "a third shear takes nothing more");
                    context.assertEquals(player.getMainHandStack().getDamage(), 2, "nor uses the shears");
                }));
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aBoxlessMerchantCannotHideAndBuildsANewBox(TestContext context) {
        floor(context);
        HidingTraderEntity trader = trader(context);
        trader.setHasBandana(false);
        trader.setHasBox(false);
        // Forced to hide (like after a theft), but nothing to hide in
        trader.startTheftHiding(0);
        context.waitAndRun(3, () -> {
            context.assertFalse(trader.isHidden(), "no box: can't hide");
            trader.regrowBox();
            context.assertTrue(trader.hasBox(), "a new box");
            context.assertTrue(trader.getBlockState().isOf(Blocks.STONE), "made of the block under his feet: " + trader.getBlockState());
            context.waitAndRun(2, () -> {
                context.assertTrue(trader.isHidden(), "hides in it again");
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aBoxCanNeverLookLikeAirOrAnInvisibleBlock(TestContext context) {
        HidingTraderEntity trader = context.spawnEntity(ModEntities.HIDING_TRADER_ENTITY, new BlockPos(1, 1, 1));
        trader.setBlockState(Blocks.AIR.getDefaultState());
        trader.setBlockState(Blocks.BARRIER.getDefaultState());
        context.assertTrue(trader.getBlockState().isOf(Blocks.GOLD_BLOCK), "air and barrier refused: " + trader.getBlockState());
        NbtCompound saved = trader.writeNbt(new NbtCompound());
        saved.putString("blockState", "{\"Name\":\"minecraft:air\"}");
        HidingTraderEntity loaded = ModEntities.HIDING_TRADER_ENTITY.create(context.getWorld(), SpawnReason.LOAD);
        loaded.readNbt(saved);
        context.assertTrue(loaded.getBlockState().isOf(Blocks.GOLD_BLOCK), "air from NBT refused");
        context.assertTrue(BoxCostumeItem.getBlock(BoxCostumeItem.create(Blocks.AIR.getDefaultState())).isOf(Blocks.GOLD_BLOCK), "costume of air: gold");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void boxStateIsSaved(TestContext context) {
        HidingTraderEntity trader = context.spawnEntity(ModEntities.HIDING_TRADER_ENTITY, new BlockPos(1, 1, 1));
        trader.setBlockState(Blocks.BRICKS.getDefaultState());
        trader.setHasBox(false);
        NbtCompound saved = trader.writeNbt(new NbtCompound());
        HidingTraderEntity loaded = ModEntities.HIDING_TRADER_ENTITY.create(context.getWorld(), SpawnReason.LOAD);
        loaded.readNbt(saved);
        context.assertFalse(loaded.hasBox(), "still boxless");
        context.assertTrue(loaded.getBlockState().isOf(Blocks.BRICKS), "block look kept");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theBoxCostumeIsWornOnTheChest(TestContext context) {
        ItemStack costume = BoxCostumeItem.create(Blocks.OAK_PLANKS.getDefaultState());
        EquippableComponent equippable = costume.get(DataComponentTypes.EQUIPPABLE);
        context.assertTrue(equippable != null && equippable.slot() == EquipmentSlot.CHEST, "equippable in the chest slot");
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        checks(context, player, () -> {
            context.assertEquals(player.getPreferredEquipmentSlot(costume), EquipmentSlot.CHEST, "goes to the chest");
            player.setStackInHand(Hand.MAIN_HAND, costume.copy());
            player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
            ItemStack worn = BoxCostumeItem.getWorn(player);
            context.assertFalse(worn.isEmpty(), "right click puts it on");
            context.assertTrue(BoxCostumeItem.getBlock(worn).isOf(Blocks.OAK_PLANKS), "keeps its block");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sneakingHidesTheWearerInTheBox(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        checks(context, player, () -> {
            player.changeGameMode(GameMode.SURVIVAL);
            player.setSneaking(true);
            context.assertFalse(BoxCostumeItem.isHiddenInBox(player), "sneaking without the costume");
            player.equipStack(EquipmentSlot.CHEST, BoxCostumeItem.create(Blocks.GOLD_BLOCK.getDefaultState()));
            context.assertTrue(BoxCostumeItem.isHiddenInBox(player), "sneaking with it: hidden");
            ZombieEntity zombie = context.spawnEntity(net.minecraft.entity.EntityType.ZOMBIE, new BlockPos(1, 1, 1));
            context.assertTrue(player.getAttackDistanceScalingFactor(zombie) <= BoxCostumeItem.HIDDEN_DETECTION_FACTOR + 1e-6, "monsters hardly notice him");
            player.setSneaking(false);
            context.assertFalse(BoxCostumeItem.isHiddenInBox(player), "standing: out of the box");
            context.assertTrue(player.getAttackDistanceScalingFactor(zombie) > 0.5, "noticed again");
            player.setSneaking(true);
            player.getAbilities().flying = true;
            context.assertFalse(BoxCostumeItem.isHiddenInBox(player), "flying down: not hidden");
        });
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
    public void heStaysHiddenFromASneakingPlayer(TestContext context) {
        // Checked on the rule itself: a neighbouring test's player within 15 blocks would open a real trader
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        checks(context, player, () -> {
            player.changeGameMode(GameMode.SURVIVAL);
            context.assertTrue(HidingTraderEntity.drawsHimOut(player), "a player standing makes him come out");
            player.setSneaking(true);
            context.assertFalse(HidingTraderEntity.drawsHimOut(player), "a sneaking player doesn't");
            player.equipStack(EquipmentSlot.HEAD, BandanaItem.create(1));
            context.assertTrue(HidingTraderEntity.drawsHimOut(player), "a sneaking Bandana wearer does");
        });
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
