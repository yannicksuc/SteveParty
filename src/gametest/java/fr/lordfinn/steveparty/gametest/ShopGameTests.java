package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.CashRegisterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.HidingTraderEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.ShopkeeperKeyItem;
import fr.lordfinn.steveparty.persistent_state.VendorLinkPersistentState;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ShopGameTests implements FabricGameTest {

    /** A plain price only accepts a plain item: a named or enchanted one (extra components) is refused. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void plainPriceRefusesItemsWithExtraComponents(TestContext context) {
        ItemStack price = new ItemStack(Items.DIAMOND_SWORD);
        TradeOffer offer = new TradingStallBlockEntity.ExactTradeOffer(price, ItemStack.EMPTY, new ItemStack(Items.EMERALD));

        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Excalibur"));
        ItemStack enchanted = new ItemStack(Items.DIAMOND_SWORD);
        enchanted.addEnchantment(sharpness(context), 1);

        context.assertTrue(offer.matchesBuyItems(new ItemStack(Items.DIAMOND_SWORD), ItemStack.EMPTY), "plain sword accepted");
        context.assertFalse(offer.matchesBuyItems(named, ItemStack.EMPTY), "named sword refused");
        context.assertFalse(offer.matchesBuyItems(enchanted, ItemStack.EMPTY), "enchanted sword refused");
        context.complete();
    }

    /** A price with components (enchantment) is displayed with them and only accepts that exact item. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void enchantedPriceRequiresTheExactItem(TestContext context) {
        ItemStack price = new ItemStack(Items.DIAMOND_SWORD);
        price.addEnchantment(sharpness(context), 2);
        TradeOffer offer = new TradingStallBlockEntity.ExactTradeOffer(price, ItemStack.EMPTY, new ItemStack(Items.EMERALD));

        context.assertTrue(ItemStack.areItemsAndComponentsEqual(offer.getDisplayedFirstBuyItem(), price),
                "merchant screen displays the enchanted price");

        ItemStack sameEnchant = new ItemStack(Items.DIAMOND_SWORD);
        sameEnchant.addEnchantment(sharpness(context), 2);
        ItemStack otherLevel = new ItemStack(Items.DIAMOND_SWORD);
        otherLevel.addEnchantment(sharpness(context), 1);
        ItemStack enchantedAndNamed = sameEnchant.copy();
        enchantedAndNamed.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Excalibur"));

        context.assertFalse(offer.matchesBuyItems(new ItemStack(Items.DIAMOND_SWORD), ItemStack.EMPTY), "plain sword refused");
        context.assertFalse(offer.matchesBuyItems(otherLevel, ItemStack.EMPTY), "other enchantment level refused");
        context.assertFalse(offer.matchesBuyItems(enchantedAndNamed, ItemStack.EMPTY), "extra custom name refused");
        context.assertTrue(offer.matchesBuyItems(sameEnchant, ItemStack.EMPTY), "exact enchanted sword accepted");
        context.complete();
    }

    /** The trade takes the exact price and remembers the stacks really paid (for the cash registers). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tradeKeepsTheActualPaidStacks(TestContext context) {
        ItemStack coin = new ItemStack(Items.GOLD_NUGGET, 3);
        coin.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Coin"));
        ItemStack ticket = new ItemStack(Items.PAPER, 1);
        ticket.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Ticket"));
        TradingStallBlockEntity.ExactTradeOffer offer =
                new TradingStallBlockEntity.ExactTradeOffer(coin, ticket, new ItemStack(Items.DIAMOND));

        ItemStack plainNuggets = new ItemStack(Items.GOLD_NUGGET, 10);
        ItemStack paidTicket = ticket.copyWithCount(2);
        context.assertFalse(offer.depleteBuyItems(plainNuggets, paidTicket), "unnamed nuggets are not coins");
        context.assertEquals(plainNuggets.getCount(), 10, "nothing taken on a refused payment");

        ItemStack paidCoins = coin.copyWithCount(5);
        context.assertTrue(offer.depleteBuyItems(paidCoins, paidTicket), "exact payment accepted");
        context.assertEquals(paidCoins.getCount(), 2, "3 coins taken");
        context.assertEquals(paidTicket.getCount(), 1, "1 ticket taken");

        List<ItemStack> payment = offer.takeLastPayment();
        context.assertEquals(payment.size(), 2, "two paid stacks");
        context.assertTrue(ItemStack.areEqual(payment.get(0), coin), "paid coins keep their name");
        context.assertTrue(ItemStack.areEqual(payment.get(1), ticket), "paid ticket keeps its name");
        context.assertTrue(offer.takeLastPayment().isEmpty(), "payment forgotten once taken");
        context.complete();
    }

    /** Links are stored with their dimension; old dimension-less saves still load and get pinned to the trader's world. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void vendorLinksStoreTheDimensionAndReadLegacyData(TestContext context) {
        UUID vendor = UUID.randomUUID();
        BlockPos pos = new BlockPos(10, 64, -3);

        NbtCompound legacy = new NbtCompound();
        NbtList vendors = new NbtList();
        NbtCompound vendorTag = new NbtCompound();
        vendorTag.putUuid("VendorId", vendor);
        NbtList positions = new NbtList();
        NbtCompound posTag = new NbtCompound();
        posTag.putInt("X", pos.getX());
        posTag.putInt("Y", pos.getY());
        posTag.putInt("Z", pos.getZ());
        positions.add(posTag);
        vendorTag.put("Positions", positions);
        vendors.add(vendorTag);
        legacy.put("Vendors", vendors);

        VendorLinkPersistentState state = VendorLinkPersistentState.fromNbt(legacy);
        context.assertTrue(state.isBlockLinkedToVendor(vendor, GlobalPos.create(World.NETHER, pos)), "legacy link matches any dimension");
        context.assertEquals(state.getVendorLinks(vendor, World.NETHER), Set.of(pos), "legacy link resolved by the trader");
        context.assertFalse(state.isBlockLinkedToVendor(vendor, GlobalPos.create(World.OVERWORLD, pos)), "legacy link pinned to the trader's dimension");

        NbtCompound saved = state.writeNbt(new NbtCompound(), context.getWorld().getRegistryManager());
        NbtCompound savedPos = saved.getList("Vendors", NbtElement.COMPOUND_TYPE).getCompound(0)
                .getList("Positions", NbtElement.COMPOUND_TYPE).getCompound(0);
        context.assertEquals(savedPos.getString("Dimension"), World.NETHER.getValue().toString(), "dimension saved");

        VendorLinkPersistentState reloaded = VendorLinkPersistentState.fromNbt(saved);
        context.assertTrue(reloaded.isBlockLinkedToVendor(vendor, GlobalPos.create(World.NETHER, pos)), "reloaded link");
        context.assertFalse(reloaded.toggleLink(vendor, GlobalPos.create(World.NETHER, pos)), "toggle unlinks");
        context.assertTrue(reloaded.getVendorLinks(vendor).isEmpty(), "no link left");
        context.complete();
    }

    /** Two keys of the same trader always display the persistent links (no more diverging destinations). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shopkeeperKeysDisplayThePersistentLinks(TestContext context) {
        UUID vendor = UUID.randomUUID();
        BlockPos pos = context.getAbsolutePos(new BlockPos(1, 1, 1));
        GlobalPos globalPos = GlobalPos.create(context.getWorld().getRegistryKey(), pos);
        VendorLinkPersistentState state = VendorLinkPersistentState.get(context.getWorld().getServer());

        ItemStack keyA = linkedKey(vendor);
        ItemStack keyB = linkedKey(vendor);
        state.linkBlock(vendor, globalPos);
        ShopkeeperKeyItem.refreshDestinations(keyA, context.getWorld());
        ShopkeeperKeyItem.refreshDestinations(keyB, context.getWorld());
        context.assertEquals(keyA.get(ModComponents.DESTINATIONS_COMPONENT).destinations(), List.of(pos), "key A shows the link");
        context.assertEquals(keyB.get(ModComponents.DESTINATIONS_COMPONENT).destinations(), List.of(pos), "key B shows the link");

        state.unlinkBlock(vendor, globalPos);
        ShopkeeperKeyItem.refreshDestinations(keyB, context.getWorld());
        context.assertTrue(keyB.getOrDefault(ModComponents.DESTINATIONS_COMPONENT, DestinationsComponent.DEFAULT).destinations().isEmpty(),
                "key B follows the unlink");
        context.complete();
    }

    /** Stall GUI: reserved to a key linked to the stall's trader; creative bypasses; unlinked stall opens with any key. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tradingStallAccessIsReservedToTheShopkeeper(TestContext context) {
        BlockPos relative = new BlockPos(1, 1, 1);
        context.setBlockState(relative, ModBlocks.TRADING_STALL);
        BlockPos pos = context.getAbsolutePos(relative);
        GlobalPos globalPos = GlobalPos.create(context.getWorld().getRegistryKey(), pos);
        VendorLinkPersistentState state = VendorLinkPersistentState.get(context.getWorld().getServer());
        UUID owner = UUID.randomUUID();

        PlayerEntity survival = context.createMockPlayer(GameMode.SURVIVAL);
        PlayerEntity creative = context.createMockPlayer(GameMode.CREATIVE);

        // Not linked to any trader yet: only with a key (or in creative)
        context.assertFalse(ShopkeeperKeyItem.canOpenShopBlock(survival, context.getWorld(), pos), "no key, no access");
        context.assertTrue(ShopkeeperKeyItem.canOpenShopBlock(creative, context.getWorld(), pos), "creative access");
        survival.setStackInHand(Hand.OFF_HAND, new ItemStack(ModItems.SHOPKEEPER_KEY));
        context.assertTrue(ShopkeeperKeyItem.canOpenShopBlock(survival, context.getWorld(), pos), "any key sets up an unlinked shop");

        state.linkBlock(owner, globalPos);
        try {
            context.assertFalse(ShopkeeperKeyItem.canOpenShopBlock(survival, context.getWorld(), pos), "unlinked key refused");
            survival.setStackInHand(Hand.OFF_HAND, linkedKey(UUID.randomUUID()));
            context.assertFalse(ShopkeeperKeyItem.canOpenShopBlock(survival, context.getWorld(), pos), "other trader's key refused");
            survival.setStackInHand(Hand.OFF_HAND, linkedKey(owner));
            context.assertTrue(ShopkeeperKeyItem.canOpenShopBlock(survival, context.getWorld(), pos), "owner's key (off hand) accepted");
            context.assertTrue(ShopkeeperKeyItem.canOpenShopBlock(creative, context.getWorld(), pos), "creative still has access");
        } finally {
            state.unlinkBlock(owner, globalPos);
        }
        context.complete();
    }

    /** ONE_SALE_PER_SIGNAL: locked by default, a rising edge gives one credit (max 1), a sale consumes it. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oneSalePerSignalConsumesTheCredit(TestContext context) {
        BlockPos stallPos = new BlockPos(1, 1, 1);
        context.setBlockState(stallPos, ModBlocks.TRADING_STALL);
        TradingStallBlockEntity stall = context.getBlockEntity(stallPos);
        context.assertTrue(stall.isSaleAllowed(), "FREE mode by default");
        stall.setSaleMode(TradingStallBlockEntity.SaleMode.ONE_SALE_PER_SIGNAL);
        TradingStallBlockEntity.ExactTradeOffer offer = new TradingStallBlockEntity.ExactTradeOffer(
                new ItemStack(Items.EMERALD), ItemStack.EMPTY, new ItemStack(Items.DIAMOND), stall);
        context.assertFalse(offer.isSaleAllowed(), "locked until a signal");

        context.setBlockState(stallPos.east(), Blocks.REDSTONE_BLOCK);
        context.assertTrue(offer.isSaleAllowed(), "a redstone pulse unlocks one sale");
        stall.onRedstonePower(true);
        stall.onSale();
        context.assertFalse(offer.isSaleAllowed(), "the sale consumed the credit (a steady signal is not a new pulse)");

        context.setBlockState(stallPos.east(), Blocks.AIR);
        context.setBlockState(stallPos.east(), Blocks.REDSTONE_BLOCK);
        context.assertTrue(stall.hasSaleCredit(), "next rising edge gives a new credit");
        stall.onRedstonePower(false);
        stall.onRedstonePower(true);
        stall.onSale();
        context.assertFalse(stall.hasSaleCredit(), "credits do not accumulate beyond 1");

        NbtCompound saved = stall.createNbtWithIdentifyingData(context.getWorld().getRegistryManager());
        context.assertEquals(saved.getInt("sale_mode"), TradingStallBlockEntity.SaleMode.ONE_SALE_PER_SIGNAL.getId(), "mode saved");
        stall.setSaleMode(TradingStallBlockEntity.SaleMode.FREE);
        context.assertTrue(offer.isSaleAllowed(), "FREE mode never locks");
        context.complete();
    }

    /** Full trader flow: FREE mode never exhausts the offer; ONE_SALE_PER_SIGNAL sells once per pulse. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void traderSellsFreelyAndOncePerSignal(TestContext context) {
        BlockPos stallPos = new BlockPos(1, 1, 1);
        BlockPos chestPos = new BlockPos(3, 1, 1);
        BlockPos registerPos = new BlockPos(1, 1, 3);
        context.setBlockState(stallPos, ModBlocks.TRADING_STALL);
        context.setBlockState(chestPos, Blocks.CHEST);
        context.setBlockState(registerPos, ModBlocks.CASH_REGISTER);
        TradingStallBlockEntity stall = context.getBlockEntity(stallPos);
        stall.setStack(0, new ItemStack(Items.EMERALD));
        stall.setStack(18, new ItemStack(Items.DIAMOND));
        Inventory chest = context.getBlockEntity(chestPos);
        chest.setStack(0, new ItemStack(Items.DIAMOND, 10));
        CashRegisterBlockEntity register = context.getBlockEntity(registerPos);

        HidingTraderEntity trader = context.spawnEntity(ModEntities.HIDING_TRADER_ENTITY, new BlockPos(2, 1, 2));
        VendorLinkPersistentState links = VendorLinkPersistentState.get(context.getWorld().getServer());
        for (BlockPos pos : List.of(stallPos, chestPos, registerPos)) {
            links.linkBlock(trader.getUuid(), GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(pos)));
        }

        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.setPosition(trader.getPos().add(0, 0, 1));
        trader.interact(player, Hand.MAIN_HAND);
        context.assertTrue(player.currentScreenHandler instanceof MerchantScreenHandler, "merchant screen opened");
        MerchantScreenHandler handler = (MerchantScreenHandler) player.currentScreenHandler;

        for (int i = 0; i < 3; i++) {
            context.assertTrue(buyOnce(handler, player), "FREE mode purchase " + (i + 1));
        }
        context.assertEquals(chest.count(Items.DIAMOND), 7, "stock consumed");
        context.assertEquals(register.count(Items.EMERALD), 3, "payments in the cash register");
        context.assertFalse(trader.getOffers().getFirst().isDisabled(), "offer not exhausted");

        stall.setSaleMode(TradingStallBlockEntity.SaleMode.ONE_SALE_PER_SIGNAL);
        context.waitAndRun(12, () -> {
            context.assertFalse(buyOnce(handler, player), "locked stall: nothing to buy");
            context.setBlockState(stallPos.west(), Blocks.REDSTONE_BLOCK);
            context.waitAndRun(12, () -> {
                context.assertTrue(buyOnce(handler, player), "one sale after the pulse");
                context.assertFalse(buyOnce(handler, player), "locked again after the sale");
                context.assertEquals(register.count(Items.EMERALD), 4, "one more payment");
                player.closeHandledScreen();
                context.complete();
            });
        });
    }

    /** Puts one emerald in the merchant input and takes the result; false if no result was offered. */
    private static boolean buyOnce(MerchantScreenHandler handler, ServerPlayerEntity player) {
        handler.getSlot(0).setStack(new ItemStack(Items.EMERALD));
        if (handler.getSlot(2).getStack().isEmpty()) {
            handler.getSlot(0).setStack(ItemStack.EMPTY);
            return false;
        }
        handler.onSlotClick(2, 0, SlotActionType.PICKUP, player);
        boolean bought = handler.getCursorStack().isOf(Items.DIAMOND);
        handler.setCursorStack(ItemStack.EMPTY);
        return bought;
    }

    private static ItemStack linkedKey(UUID vendor) {
        ItemStack key = new ItemStack(ModItems.SHOPKEEPER_KEY);
        key.set(ModComponents.SHOPKEEPER_UUID, vendor);
        return key;
    }

    private static RegistryEntry<Enchantment> sharpness(TestContext context) {
        return context.getWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
    }
}
