package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerSoul;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.Items;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.HidingTraderEntity;
import net.minecraft.world.GameMode;

import java.util.List;

/**
 * The villager inside the villager block: a piston squashing a villager keeps all of it (profession, level, trades,
 * experience, name), a sticky piston pulling the block gives back the same villager, and the villager stays inside
 * when the block falls, is pushed, or is broken (by hand, no tool) and placed again.
 */
public class VillagerBlockSoulGameTests implements FabricGameTest {
    private static final BlockPos FEET = new BlockPos(2, 2, 2);
    private static final String NAME = "Hmmbert";

    /** A master-ish farmer called Hmmbert, with his trades, standing still at {@code pos}. */
    private static VillagerEntity hmmbert(TestContext context, BlockPos pos) {
        VillagerEntity villager = context.spawnEntity(EntityType.VILLAGER, pos);
        villager.setAiDisabled(true);
        villager.setVillagerData(villager.getVillagerData().withProfession(VillagerProfession.FARMER).withLevel(3));
        villager.setExperience(42);
        villager.setCustomName(Text.literal(NAME));
        villager.getOffers(); // generates the trades of its levels
        return villager;
    }

    private static void assertIsHmmbert(TestContext context, VillagerEntity villager, int offers, String what) {
        context.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.FARMER, what + ": farmer");
        context.assertEquals(villager.getVillagerData().getLevel(), 3, what + ": level");
        context.assertEquals(villager.getExperience(), 42, what + ": experience");
        context.assertTrue(villager.getCustomName() != null && NAME.equals(villager.getCustomName().getString()), what + ": name");
        context.assertEquals(villager.getOffers().size(), offers, what + ": trades");
    }

    private static void assertSoulIsHmmbert(TestContext context, NbtCompound soul, String what) {
        context.assertTrue(soul != null, what + ": keeps the villager");
        context.assertTrue(soul.getCompound("VillagerData").getString("profession").endsWith("farmer"), what + ": farmer");
        context.assertEquals(soul.getInt("Xp"), 42, what + ": experience");
        context.assertTrue(soul.getString("CustomName").contains(NAME), what + ": name");
        context.assertFalse(soul.containsUuid("UUID"), what + ": no UUID (a copied item can't clone the entity)");
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void squashedByAPistonAndPulledBackOut(TestContext context) {
        context.setBlockState(FEET.down(), Blocks.STONE);
        // sticky piston facing down, its body 2 blocks above the villager's feet (the villager's head right under it)
        BlockPos piston = FEET.up(2);
        context.setBlockState(piston, Blocks.STICKY_PISTON.getDefaultState().with(PistonBlock.FACING, Direction.DOWN));
        VillagerEntity villager = hmmbert(context, FEET);
        int offers = villager.getOffers().size();
        context.assertTrue(offers > 0, "the farmer has trades");
        BlockPos power = piston.east();
        context.setBlockState(power, Blocks.REDSTONE_BLOCK);

        context.waitAndRun(6, () -> {
            context.expectBlock(ModBlocks.VILLAGER_BLOCK, FEET);
            VillagerBlockEntity block = context.getBlockEntity(FEET);
            assertSoulIsHmmbert(context, block.getSoul(), "squashed");
            context.assertTrue(villager.isRemoved(), "the villager is in the block now");
            // the sticky piston lets go: it pulls the villager block... out comes Hmmbert
            context.setBlockState(power, Blocks.AIR);
        });
        context.addInstantFinalTask(() -> {
            context.expectBlock(Blocks.AIR, FEET);
            List<VillagerEntity> out = context.getWorld().getEntitiesByClass(VillagerEntity.class,
                    new Box(context.getAbsolutePos(FEET)).expand(1), v -> v != villager);
            context.assertEquals(out.size(), 1, "one villager back");
            assertIsHmmbert(context, out.getFirst(), offers, "pulled out");
            out.getFirst().discard();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void brokenByHandItKeepsTheVillager(TestContext context) {
        context.setBlockState(FEET.down(), Blocks.STONE);
        context.setBlockState(FEET, ModBlocks.VILLAGER_BLOCK);
        VillagerEntity villager = hmmbert(context, FEET.east(3));
        int offers = villager.getOffers().size();
        VillagerBlockEntity block = context.getBlockEntity(FEET);
        block.setSoul(VillagerSoul.capture(villager));
        villager.discard();

        BlockState state = context.getWorld().getBlockState(context.getAbsolutePos(FEET));
        context.assertFalse(state.isToolRequired(), "no tool needed");
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            player.changeGameMode(GameMode.SURVIVAL);
            player.setOnGround(true); // mining in the air is 5 times slower
            float perTick = state.calcBlockBreakingDelta(player, context.getWorld(), context.getAbsolutePos(FEET));
            context.assertTrue(perTick > 0 && perTick < 1, "by hand, not instant (it has time to plead): " + perTick);
            context.assertTrue(1 / perTick <= 40, "by hand in 2 s at most: " + 1 / perTick + " ticks");

            context.getWorld().breakBlock(context.getAbsolutePos(FEET), true, player);
            List<ItemEntity> drops = context.getWorld().getEntitiesByClass(ItemEntity.class,
                    new Box(context.getAbsolutePos(FEET)).expand(1), e -> e.getStack().isOf(ModBlocks.VILLAGER_BLOCK.asItem()));
            context.assertEquals(drops.size(), 1, "drops the villager block");
            ItemStack stack = drops.getFirst().getStack();
            NbtComponent data = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
            context.assertTrue(data != null && data.contains(VillagerSoul.KEY), "the item keeps the villager");
            assertSoulIsHmmbert(context, data.copyNbt().getCompound(VillagerSoul.KEY), "dropped item");

            // placed again: the same villager inside
            context.setBlockState(FEET, ModBlocks.VILLAGER_BLOCK);
            BlockItem.writeNbtToBlockEntity(context.getWorld(), player, context.getAbsolutePos(FEET), stack);
            VillagerBlockEntity placed = context.getBlockEntity(FEET);
            assertSoulIsHmmbert(context, placed.getSoul(), "placed again");
            drops.getFirst().discard();

            VillagerEntity back = VillagerSoul.release(context.getWorld(), context.getAbsolutePos(FEET.east(2)), placed.getSoul());
            assertIsHmmbert(context, back, offers, "released after all that");
            back.discard();
        } finally {
            context.getWorld().getServer().getPlayerManager().remove(player);
        }
        context.complete();
    }

    /** A survival player about to land from high on the villager block at {@code pos} (on stone). */
    private static ServerPlayerEntity landOn(TestContext context, BlockPos pos) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        BlockPos abs = context.getAbsolutePos(pos.up());
        player.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY() + 0.1, abs.getZ() + 0.5, 0, 0);
        player.setOnGround(false);
        player.fallDistance = 50;
        return player;
    }

    private static HidingTraderEntity traderAround(TestContext context, BlockPos pos) {
        List<HidingTraderEntity> traders = context.getWorld().getEntitiesByClass(HidingTraderEntity.class,
                new Box(context.getAbsolutePos(pos)).expand(3), e -> true);
        context.assertEquals(traders.size(), 1, "one hiding trader");
        return traders.getFirst();
    }

    private static void assertSameOffers(TestContext context, TradeOfferList expected, TradeOfferList actual, String what) {
        context.assertEquals(actual.size(), expected.size(), what + ": as many trades");
        for (int i = 0; i < expected.size(); i++) {
            TradeOffer e = expected.get(i), a = actual.get(i);
            context.assertTrue(ItemStack.areEqual(e.getSellItem(), a.getSellItem())
                    && ItemStack.areEqual(e.getOriginalFirstBuyItem(), a.getOriginalFirstBuyItem())
                    && ItemStack.areEqual(e.getDisplayedSecondBuyItem(), a.getDisplayedSecondBuyItem())
                    && e.getUses() == a.getUses() && e.getMaxUses() == a.getMaxUses()
                    && e.getMerchantExperience() == a.getMerchantExperience(), what + ": same trade " + i);
        }
    }

    /**
     * Landed on from high enough, the villager block becomes a hiding trader: he is the villager that was inside
     * (same trades with their uses, profession, level, experience, name) and trades like it; its pockets are emptied.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void landedOnItBecomesATraderWhoIsTheVillager(TestContext context) {
        VillagerEntity villager = hmmbert(context, new BlockPos(6, 2, 6));
        villager.getOffers().getFirst().use();
        villager.getInventory().addStack(new ItemStack(Items.WHEAT, 5));
        TradeOfferList offers = villager.getOffers().copy();
        context.assertTrue(offers.size() > 1, "the farmer has trades");
        NbtCompound soul = VillagerSoul.capture(villager);
        villager.discard();

        context.setBlockState(FEET.down(), Blocks.STONE);
        context.setBlockState(FEET, ModBlocks.VILLAGER_BLOCK);
        ((VillagerBlockEntity) context.getBlockEntity(FEET)).setSoul(soul);
        ServerPlayerEntity player = landOn(context, FEET);
        context.waitAndRun(3, () -> {
            try {
                context.expectBlock(Blocks.AIR, FEET);
                HidingTraderEntity trader = traderAround(context, FEET);
                assertSameOffers(context, offers, trader.getVillagerOffers(), "trader");
                context.assertTrue(trader.getVillagerData() != null && trader.getVillagerData().getProfession() == VillagerProfession.FARMER
                        && trader.getVillagerData().getLevel() == 3, "farmer of level 3");
                context.assertEquals(trader.getVillagerExperience(), 42, "experience");
                context.assertTrue(trader.getCustomName() != null && NAME.equals(trader.getCustomName().getString()), "name");
                context.assertTrue(trader.isLeveledMerchant(), "shows his level");
                context.assertTrue(!context.getWorld().getEntitiesByClass(ItemEntity.class, trader.getBoundingBox().expand(3),
                        e -> e.getStack().isOf(Items.WHEAT) && e.getStack().getCount() == 5).isEmpty(), "his pockets emptied");

                // He trades them like the villager: no shop needed, uses counted, experience earned
                trader.updateTradeOffers();
                TradeOffer offer = trader.getVillagerOffers().get(1);
                context.assertTrue(trader.getOffers().contains(offer) && !offer.isDisabled(), "the villager's trade is offered");
                int uses = offer.getUses();
                trader.trade(offer);
                context.assertEquals(offer.getUses(), uses + 1, "trade used");
                context.assertEquals(trader.getVillagerExperience(), 42 + offer.getMerchantExperience(), "experience earned");

                // Saved and loaded again
                HidingTraderEntity loaded = ModEntities.HIDING_TRADER_ENTITY.create(context.getWorld(), SpawnReason.LOAD);
                loaded.readNbt(trader.writeNbt(new NbtCompound()));
                assertSameOffers(context, trader.getVillagerOffers(), loaded.getVillagerOffers(), "saved");
                context.assertEquals(loaded.getVillagerExperience(), trader.getVillagerExperience(), "saved experience");
                context.assertTrue(loaded.getVillagerData() != null && loaded.getVillagerData().getLevel() == 3, "saved level");
                trader.discard();
            } finally {
                context.getWorld().getServer().getPlayerManager().remove(player);
            }
            context.complete();
        });
    }

    /** A former villager levels up like one: new trades of his profession. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aFormerVillagerLevelsUp(TestContext context) {
        VillagerEntity villager = hmmbert(context, new BlockPos(6, 2, 6));
        NbtCompound soul = VillagerSoul.capture(villager);
        villager.discard();
        soul.putInt("Xp", VillagerData.getUpperLevelExperience(3) - 1);
        HidingTraderEntity trader = context.spawnEntity(ModEntities.HIDING_TRADER_ENTITY, new BlockPos(1, 2, 1));
        trader.inheritVillager(soul);
        trader.updateTradeOffers();
        int before = trader.getVillagerOffers().size();
        TradeOffer offer = trader.getVillagerOffers().getFirst();
        trader.trade(offer);
        context.assertTrue(trader.getVillagerData().getLevel() == 4, "level 4");
        context.assertTrue(trader.getVillagerOffers().size() > before, "new trades");
        context.assertTrue(trader.getOffers().containsAll(trader.getVillagerOffers()), "offered at once");
        trader.discard();
        context.complete();
    }

    /** A villager block without a villager inside (creative, older worlds) gives a plain trader, as before. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void anEmptyVillagerBlockGivesAPlainTrader(TestContext context) {
        context.setBlockState(FEET.down(), Blocks.STONE);
        context.setBlockState(FEET, ModBlocks.VILLAGER_BLOCK);
        ServerPlayerEntity player = landOn(context, FEET);
        context.waitAndRun(3, () -> {
            try {
                HidingTraderEntity trader = traderAround(context, FEET);
                context.assertTrue(trader.getVillagerOffers().isEmpty() && trader.getVillagerData() == null, "no villager trades");
                context.assertTrue(!trader.isLeveledMerchant() && trader.getCustomName() == null, "a plain trader");
                trader.discard();
            } finally {
                context.getWorld().getServer().getPlayerManager().remove(player);
            }
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void fallingOrPushedItKeepsTheVillager(TestContext context) {
        VillagerEntity villager = hmmbert(context, new BlockPos(6, 2, 6));
        NbtCompound soul = VillagerSoul.capture(villager);
        villager.discard();

        // falls from 3 blocks up
        BlockPos landing = new BlockPos(1, 2, 1);
        context.setBlockState(landing.down(), Blocks.STONE);
        context.setBlockState(landing.up(3), ModBlocks.VILLAGER_BLOCK);
        ((VillagerBlockEntity) context.getBlockEntity(landing.up(3))).setSoul(soul.copy());

        // pushed by a piston
        for (int x = 0; x <= 5; x++) context.setBlockState(new BlockPos(x, 1, 4), Blocks.STONE);
        context.setBlockState(new BlockPos(1, 2, 4), Blocks.PISTON.getDefaultState().with(PistonBlock.FACING, Direction.EAST));
        context.setBlockState(new BlockPos(2, 2, 4), ModBlocks.VILLAGER_BLOCK);
        ((VillagerBlockEntity) context.getBlockEntity(new BlockPos(2, 2, 4))).setSoul(soul.copy());
        context.setBlockState(new BlockPos(0, 2, 4), Blocks.REDSTONE_BLOCK);

        context.addInstantFinalTask(() -> {
            context.expectBlock(ModBlocks.VILLAGER_BLOCK, landing);
            context.expectBlock(ModBlocks.VILLAGER_BLOCK, new BlockPos(3, 2, 4));
            assertSoulIsHmmbert(context, ((VillagerBlockEntity) context.getBlockEntity(landing)).getSoul(), "fell");
            assertSoulIsHmmbert(context, ((VillagerBlockEntity) context.getBlockEntity(new BlockPos(3, 2, 4))).getSoul(), "pushed");
        });
    }
}
