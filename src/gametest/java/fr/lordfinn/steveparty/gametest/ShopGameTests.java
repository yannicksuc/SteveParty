package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.CashRegisterBlock;
import fr.lordfinn.steveparty.blocks.custom.CashRegisterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlock;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.ShopkeeperKeyItem;
import fr.lordfinn.steveparty.persistent_state.ShopProtection;
import fr.lordfinn.steveparty.screen_handlers.custom.CashRegisterScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.TradingStallScreenHandler;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.enums.ChestType;
import fr.lordfinn.steveparty.persistent_state.VendorLinkPersistentState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ShopGameTests implements SteveGameTest {

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

    /**
     * The first player linking a key owns the trader: another player can't link keys to it nor link blocks with
     * it; the owner opens the trader's shop blocks with any of their keys.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void traderBelongsToTheFirstPlayerLinkingAKey(TestContext context) {
        BlockPos stallRelative = new BlockPos(1, 1, 1);
        context.setBlockState(stallRelative, ModBlocks.TRADING_STALL);
        BlockPos stallPos = context.getAbsolutePos(stallRelative);
        GlobalPos stallGlobalPos = GlobalPos.create(context.getWorld().getRegistryKey(), stallPos);
        BoxedTraderEntity trader = context.spawnEntity(ModEntities.BOXED_TRADER_ENTITY, new BlockPos(3, 1, 3));
        VendorLinkPersistentState state = VendorLinkPersistentState.get(context.getWorld().getServer());
        ShopkeeperKeyItem keyItem = (ShopkeeperKeyItem) ModItems.SHOPKEEPER_KEY;

        PlayerEntity owner = context.createMockPlayer(GameMode.SURVIVAL);
        PlayerEntity other = context.createMockPlayer(GameMode.SURVIVAL);
        PlayerEntity creative = context.createMockPlayer(GameMode.CREATIVE);
        try {
            context.assertTrue(trader.getOwnerUuid() == null, "new trader has no owner");

            ItemStack ownerKey = new ItemStack(ModItems.SHOPKEEPER_KEY);
            owner.setStackInHand(Hand.MAIN_HAND, ownerKey);
            context.assertEquals(keyItem.useOnEntity(ownerKey, owner, trader, Hand.MAIN_HAND), ActionResult.SUCCESS, "first link");
            context.assertEquals(trader.getOwnerUuid(), owner.getUuid(), "first linker owns the trader");
            context.assertEquals(state.getOwner(trader.getUuid()), owner.getUuid(), "owner in the persistent state");

            ItemStack otherKey = new ItemStack(ModItems.SHOPKEEPER_KEY);
            other.setStackInHand(Hand.MAIN_HAND, otherKey);
            context.assertEquals(keyItem.useOnEntity(otherKey, other, trader, Hand.MAIN_HAND), ActionResult.FAIL, "other player refused");
            context.assertFalse(otherKey.contains(ModComponents.SHOPKEEPER_UUID), "other player's key not linked");
            context.assertEquals(trader.getOwnerUuid(), owner.getUuid(), "owner unchanged");

            ItemStack secondOwnerKey = new ItemStack(ModItems.SHOPKEEPER_KEY);
            owner.setStackInHand(Hand.MAIN_HAND, secondOwnerKey);
            context.assertEquals(keyItem.useOnEntity(secondOwnerKey, owner, trader, Hand.MAIN_HAND), ActionResult.SUCCESS, "owner links a second key");
            context.assertEquals(secondOwnerKey.get(ModComponents.SHOPKEEPER_UUID), trader.getUuid(), "second key linked");

            // Linking blocks with a key of the trader is reserved to the owner too
            ItemStack strayKey = linkedKey(trader.getUuid());
            other.setStackInHand(Hand.MAIN_HAND, strayKey);
            context.assertEquals(keyItem.useOnBlock(useOn(other, stallPos)), ActionResult.FAIL, "other player can't link blocks");
            context.assertFalse(state.isBlockLinkedToVendor(trader.getUuid(), stallGlobalPos), "stall not linked by the other player");
            owner.setStackInHand(Hand.MAIN_HAND, ownerKey);
            context.assertEquals(keyItem.useOnBlock(useOn(owner, stallPos)), ActionResult.SUCCESS, "owner links the stall");
            context.assertTrue(state.isBlockLinkedToVendor(trader.getUuid(), stallGlobalPos), "stall linked");

            // Access: the owner with any Shopkeeper Key (even unlinked), never another player
            owner.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            context.assertFalse(ShopkeeperKeyItem.canOpenShopBlock(owner, context.getWorld(), stallPos), "owner without key refused");
            owner.setStackInHand(Hand.OFF_HAND, new ItemStack(ModItems.SHOPKEEPER_KEY));
            context.assertTrue(ShopkeeperKeyItem.canOpenShopBlock(owner, context.getWorld(), stallPos), "owner with any key (off hand)");
            owner.setStackInHand(Hand.OFF_HAND, secondOwnerKey);
            context.assertTrue(ShopkeeperKeyItem.canOpenShopBlock(owner, context.getWorld(), stallPos), "owner with another of their keys");
            context.assertFalse(ShopkeeperKeyItem.canOpenShopBlock(other, context.getWorld(), stallPos), "other player with a key of the trader refused");
            context.assertTrue(ShopkeeperKeyItem.canOpenShopBlock(creative, context.getWorld(), stallPos), "creative override kept");

            // Persistence: on the trader entity and in the persistent state
            NbtCompound traderNbt = trader.writeNbt(new NbtCompound());
            context.assertEquals(traderNbt.getUuid("ShopOwner"), owner.getUuid(), "owner saved on the trader");
            VendorLinkPersistentState reloaded = VendorLinkPersistentState.fromNbt(state.writeNbt(new NbtCompound(), context.getWorld().getRegistryManager()));
            context.assertEquals(reloaded.getOwner(trader.getUuid()), owner.getUuid(), "owner saved in the persistent state");
        } finally {
            state.unlinkPosition(stallGlobalPos);
        }
        context.complete();
    }

    /** A trader created before ownership existed is claimed by the next player linking a key (migration). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void unownedTraderIsClaimedByTheNextLinker(TestContext context) {
        VendorLinkPersistentState state = VendorLinkPersistentState.fromNbt(new NbtCompound());
        UUID legacyTrader = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        context.assertTrue(state.getOwner(legacyTrader) == null, "legacy trader has no owner");
        context.assertTrue(state.claimOrCheckOwner(legacyTrader, first), "next linker claims it");
        context.assertFalse(state.claimOrCheckOwner(legacyTrader, second), "then the others are refused");
        context.assertTrue(state.claimOrCheckOwner(legacyTrader, first), "the owner is still accepted");
        context.complete();
    }

    /** Breaking a stall or a register forgets its links; a state change of the same block keeps them. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void breakingShopBlocksRemovesTheirLinks(TestContext context) {
        BlockPos stallRelative = new BlockPos(1, 1, 1);
        BlockPos registerRelative = new BlockPos(3, 1, 1);
        context.setBlockState(stallRelative, ModBlocks.TRADING_STALL);
        context.setBlockState(registerRelative, ModBlocks.CASH_REGISTER);
        BlockPos stallPos = context.getAbsolutePos(stallRelative);
        BlockPos registerPos = context.getAbsolutePos(registerRelative);
        GlobalPos stallGlobalPos = GlobalPos.create(context.getWorld().getRegistryKey(), stallPos);
        GlobalPos registerGlobalPos = GlobalPos.create(context.getWorld().getRegistryKey(), registerPos);
        VendorLinkPersistentState state = VendorLinkPersistentState.get(context.getWorld().getServer());
        UUID vendor = UUID.randomUUID();
        state.linkBlock(vendor, stallGlobalPos);
        state.linkBlock(vendor, registerGlobalPos);
        try {
            // Same block, other state: links kept
            context.setBlockState(stallRelative, context.getBlockState(stallRelative).with(TradingStallBlock.COLOR1, 5));
            ((CashRegisterBlock) ModBlocks.CASH_REGISTER).setPowered(context.getWorld(), registerPos, true);
            context.assertTrue(state.isBlockLinkedToVendor(vendor, stallGlobalPos), "stall recolored: still linked");
            context.assertTrue(state.isBlockLinkedToVendor(vendor, registerGlobalPos), "register powered: still linked");

            // Broken: links removed
            context.setBlockState(stallRelative, Blocks.AIR);
            context.setBlockState(registerRelative, Blocks.AIR);
            context.assertTrue(state.getVendorsLinkedTo(stallGlobalPos).isEmpty(), "broken stall unlinked");
            context.assertTrue(state.getVendorsLinkedTo(registerGlobalPos).isEmpty(), "broken register unlinked");

            // A new stall at the same place starts unlinked (opens with any key, for setup)
            context.setBlockState(stallRelative, ModBlocks.TRADING_STALL);
            PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.SHOPKEEPER_KEY));
            context.assertTrue(ShopkeeperKeyItem.canOpenShopBlock(player, context.getWorld(), stallPos), "new stall has no inherited owner");
        } finally {
            state.unlinkPosition(stallGlobalPos);
            state.unlinkPosition(registerGlobalPos);
        }
        context.complete();
    }

    /** A stall is only its 27 trade slots: no disguise block slot, a merchant is tied to it with the Shopkeeper Key only. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aStallHasNoDisguiseSlotAndAttachesNoMerchantByItself(TestContext context) {
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
        BlockPos stallRelative = new BlockPos(1, 1, 1);
        context.setBlockState(stallRelative, ModBlocks.TRADING_STALL);
        TradingStallBlockEntity stall = (TradingStallBlockEntity) context.getBlockEntity(stallRelative);
        context.assertEquals(stall.size(), 27, "stall slots");
        // A stall saved with an item in the old 28th slot loads without it
        NbtCompound saved = stall.createNbt(context.getWorld().getRegistryManager());
        NbtList items = new NbtList();
        for (int slot : new int[]{0, 27}) {
            NbtCompound entry = (NbtCompound) new ItemStack(Items.GOLD_BLOCK).encode(context.getWorld().getRegistryManager());
            entry.putByte("Slot", (byte) slot);
            items.add(entry);
        }
        saved.put("Items", items);
        stall.read(saved, context.getWorld().getRegistryManager());
        context.assertTrue(stall.getStack(0).isOf(Items.GOLD_BLOCK) && stall.size() == 27, "old save loaded, 28th slot ignored");
        // A merchant in a gold box right next to it stays free: only a Shopkeeper Key ties him to a shop
        BoxedTraderEntity trader = context.spawnEntity(ModEntities.BOXED_TRADER_ENTITY, new BlockPos(2, 1, 2));
        stall.markDirty();
        context.waitAndRun(45, () -> {
            context.assertFalse(trader.isAssigned(), "not attached by the stall");
            VendorLinkPersistentState state = VendorLinkPersistentState.get(context.getWorld().getServer());
            GlobalPos stallPos = GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(stallRelative));
            state.linkBlock(trader.getUuid(), stallPos);
            context.waitAndRun(45, () -> {
                try {
                    context.assertTrue(trader.isAssigned(), "linked with the key: assigned, he stays by his shop");
                } finally {
                    state.unlinkPosition(stallPos);
                }
                context.complete();
            });
        });
    }

    /** Links the blocks (relative positions) to a new trader, owned by the given player (null: no owner). */
    private static UUID shopOf(TestContext context, UUID owner, BlockPos... relatives) {
        VendorLinkPersistentState state = VendorLinkPersistentState.get(context.getWorld().getServer());
        UUID vendor = UUID.randomUUID();
        for (BlockPos relative : relatives) {
            state.linkBlock(vendor, GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(relative)));
        }
        if (owner != null) state.setOwner(vendor, owner);
        return vendor;
    }

    private static void forgetShops(TestContext context, UUID... vendors) {
        VendorLinkPersistentState state = VendorLinkPersistentState.get(context.getWorld().getServer());
        for (UUID vendor : vendors) state.forgetVendor(vendor);
    }

    /**
     * Stall, register and stock chest of an owned shop: another player (even in creative, without operator rights)
     * can't break them, the owner can; the blocks of an unowned shop stay breakable by anyone.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ownedShopBlocksCanOnlyBeBrokenByTheOwner(TestContext context) {
        BlockPos stall = new BlockPos(1, 1, 1);
        BlockPos register = new BlockPos(3, 1, 1);
        BlockPos chest = new BlockPos(5, 1, 1);
        BlockPos unownedChest = new BlockPos(1, 1, 3);
        context.setBlockState(stall, ModBlocks.TRADING_STALL);
        context.setBlockState(register, ModBlocks.CASH_REGISTER);
        context.setBlockState(chest, Blocks.CHEST);
        context.setBlockState(unownedChest, Blocks.CHEST);
        ServerPlayerEntity owner = TestPlayers.mock(context);
        ServerPlayerEntity stranger = TestPlayers.mock(context);
        UUID shop = shopOf(context, owner.getUuid(), stall, register, chest);
        UUID unownedShop = shopOf(context, null, unownedChest);
        try {
            PlayerEntity survivalStranger = context.createMockPlayer(GameMode.SURVIVAL);
            for (BlockPos pos : List.of(stall, register, chest)) {
                BlockPos absolute = context.getAbsolutePos(pos);
                context.assertFalse(ShopProtection.canBreak(survivalStranger, context.getWorld(), absolute), "survival stranger can't break " + pos);
                context.assertFalse(stranger.interactionManager.tryBreakBlock(absolute), "creative non-op stranger can't break " + pos);
                context.assertFalse(context.getBlockState(pos).isAir(), "block kept: " + pos);
            }

            for (BlockPos pos : List.of(stall, register, chest)) {
                context.assertTrue(owner.interactionManager.tryBreakBlock(context.getAbsolutePos(pos)), "owner breaks " + pos);
                context.assertTrue(context.getBlockState(pos).isAir(), "broken by the owner: " + pos);
            }

            context.assertTrue(stranger.interactionManager.tryBreakBlock(context.getAbsolutePos(unownedChest)), "unowned shop: unchanged");
            context.assertTrue(context.getBlockState(unownedChest).isAir(), "unowned chest broken");
        } finally {
            forgetShops(context, shop, unownedShop);
        }
        context.complete();
    }

    /** A stock container of an owned shop only opens for its owner (and creative/operators), also through a double chest. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stockContainersOfAnOwnedShopOpenOnlyForTheOwner(TestContext context) {
        BlockPos chest = new BlockPos(1, 1, 1);
        BlockPos doubleLeft = new BlockPos(1, 1, 3);
        BlockPos doubleRight = new BlockPos(2, 1, 3);
        BlockPos unownedChest = new BlockPos(4, 1, 1);
        context.setBlockState(chest, Blocks.CHEST);
        context.setBlockState(doubleLeft, Blocks.CHEST.getDefaultState()
                .with(ChestBlock.FACING, Direction.NORTH).with(ChestBlock.CHEST_TYPE, ChestType.LEFT));
        context.setBlockState(doubleRight, Blocks.CHEST.getDefaultState()
                .with(ChestBlock.FACING, Direction.NORTH).with(ChestBlock.CHEST_TYPE, ChestType.RIGHT));
        context.setBlockState(unownedChest, Blocks.CHEST);

        PlayerEntity owner = context.createMockPlayer(GameMode.SURVIVAL);
        PlayerEntity stranger = context.createMockPlayer(GameMode.SURVIVAL);
        PlayerEntity creative = context.createMockPlayer(GameMode.CREATIVE);
        // Only the left half of the double chest is linked
        UUID shop = shopOf(context, owner.getUuid(), chest, doubleLeft);
        UUID unownedShop = shopOf(context, null, unownedChest);
        try {
            context.assertEquals(use(context, stranger, chest), ActionResult.FAIL, "stranger can't open the stock chest");
            context.assertEquals(use(context, stranger, doubleRight), ActionResult.FAIL, "nor its unlinked double-chest half");
            context.assertEquals(use(context, owner, chest), ActionResult.PASS, "owner opens the stock chest");
            context.assertEquals(use(context, owner, doubleRight), ActionResult.PASS, "owner opens the double chest");
            context.assertEquals(use(context, creative, chest), ActionResult.PASS, "creative opens the stock chest");
            context.assertEquals(use(context, stranger, unownedChest), ActionResult.PASS, "unowned shop: unchanged");

            // Sneaking with an item doesn't use the chest (placing a block against it, a key...): not blocked
            stranger.setSneaking(true);
            stranger.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE));
            context.assertEquals(use(context, stranger, chest), ActionResult.PASS, "sneak + item passes through");
        } finally {
            forgetShops(context, shop, unownedShop);
        }
        context.complete();
    }

    private static ActionResult use(TestContext context, PlayerEntity player, BlockPos relative) {
        BlockPos pos = context.getAbsolutePos(relative);
        return UseBlockCallback.EVENT.invoker().interact(player, context.getWorld(), Hand.MAIN_HAND,
                new BlockHitResult(pos.toCenterPos(), Direction.UP, pos, false));
    }

    /**
     * A shop works with machines, owned or not: a hopper fills its stock chest and the merchant sells from it, a
     * hopper under its cash register empties the till. The trading stall is its offers, not stock: hoppers neither
     * fill nor empty it. And no dispenser reaches the merchant itself (no armour put on it).
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void aShopWorksWithHoppersButItsStallAndMerchantDont(TestContext context) {
        BlockPos stallPos = new BlockPos(1, 2, 1), chestPos = new BlockPos(3, 1, 1), registerPos = new BlockPos(1, 2, 4);
        context.setBlockState(stallPos, ModBlocks.TRADING_STALL);
        context.setBlockState(stallPos.down(), Blocks.HOPPER);
        context.setBlockState(stallPos.up(), Blocks.HOPPER);
        context.setBlockState(chestPos, Blocks.CHEST);
        context.setBlockState(chestPos.up(), Blocks.HOPPER);
        context.setBlockState(registerPos, ModBlocks.CASH_REGISTER);
        context.setBlockState(registerPos.down(), Blocks.HOPPER);
        TradingStallBlockEntity stall = context.getBlockEntity(stallPos);
        stall.setStack(0, new ItemStack(Items.EMERALD));
        stall.setStack(18, new ItemStack(Items.DIAMOND));
        inventoryAt(context, stallPos.up()).setStack(0, new ItemStack(Items.GOLD_INGOT, 3));
        inventoryAt(context, chestPos.up()).setStack(0, new ItemStack(Items.DIAMOND, 5));
        CashRegisterBlockEntity register = context.getBlockEntity(registerPos);

        BoxedTraderEntity trader = context.spawnEntity(ModEntities.BOXED_TRADER_ENTITY, new BlockPos(2, 1, 3));
        UUID shop = trader.getUuid();
        VendorLinkPersistentState links = VendorLinkPersistentState.get(context.getWorld().getServer());
        for (BlockPos pos : List.of(stallPos, chestPos, registerPos)) {
            links.linkBlock(shop, GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(pos)));
        }
        links.setOwner(shop, UUID.randomUUID());
        // A dispenser of armour facing the merchant
        BlockPos dispenser = new BlockPos(3, 1, 3);
        context.setBlockState(dispenser, Blocks.DISPENSER.getDefaultState().with(DispenserBlock.FACING, Direction.WEST));
        inventoryAt(context, dispenser).setStack(0, new ItemStack(Items.IRON_HELMET));
        context.setBlockState(dispenser.east(), Blocks.REDSTONE_BLOCK);

        context.waitAndRun(60, () -> {
            ServerPlayerEntity player = TestPlayers.mock(context);
            try {
                context.assertEquals(inventoryAt(context, chestPos).count(Items.DIAMOND), 5, "the hopper filled the stock");
                player.setPosition(trader.getPos().add(0, 0, 1));
                trader.interact(player, Hand.MAIN_HAND);
                context.assertTrue(player.currentScreenHandler instanceof MerchantScreenHandler, "merchant screen opened");
                MerchantScreenHandler handler = (MerchantScreenHandler) player.currentScreenHandler;
                context.assertTrue(buyOnce(handler, player) && buyOnce(handler, player), "the merchant sells what the hopper brought");
                context.assertEquals(inventoryAt(context, chestPos).count(Items.DIAMOND), 3, "taken from the stock");
                player.closeHandledScreen();
                context.assertEquals(stall.count(Items.GOLD_INGOT), 0, "nothing pushed into the stall");
                context.assertEquals(inventoryAt(context, stallPos.up()).count(Items.GOLD_INGOT), 3, "the hopper above keeps its gold");
                context.assertTrue(stall.getStack(0).isOf(Items.EMERALD) && stall.getStack(18).isOf(Items.DIAMOND)
                        && inventoryAt(context, stallPos.down()).isEmpty(), "nothing pulled from the stall: its offer is whole");
                context.assertTrue(trader.getEquippedStack(EquipmentSlot.HEAD).isEmpty(), "no armour put on the merchant");
            } finally {
                TestPlayers.remove(context, player);
            }
            context.waitAndRun(40, () -> {
                try {
                    context.assertEquals(register.count(Items.EMERALD), 0, "the till is emptied by the hopper under it");
                    context.assertEquals(inventoryAt(context, registerPos.down()).count(Items.EMERALD), 2, "into the hopper");
                } finally {
                    forgetShops(context, shop);
                }
                context.complete();
            });
        });
    }

    /**
     * A hopper fills a shop but never empties it: hoppers under its stock (both halves of a double chest, only one
     * linked, of a shop with no owner) pull nothing, while the same hopper under a chest of no shop empties it.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
    public void hoppersNeverPullFromAShopStock(TestContext context) {
        BlockPos left = new BlockPos(1, 2, 1), right = new BlockPos(2, 2, 1), plain = new BlockPos(4, 2, 1);
        context.setBlockState(left, Blocks.CHEST.getDefaultState().with(ChestBlock.CHEST_TYPE, ChestType.LEFT));
        context.setBlockState(right, Blocks.CHEST.getDefaultState().with(ChestBlock.CHEST_TYPE, ChestType.RIGHT));
        context.setBlockState(plain, Blocks.CHEST);
        for (BlockPos chest : List.of(left, right, plain)) {
            context.setBlockState(chest.down(), Blocks.HOPPER);
            inventoryAt(context, chest).setStack(0, new ItemStack(Items.DIAMOND, 4));
        }
        UUID shop = shopOf(context, null, left);
        context.assertTrue(ShopProtection.isShopStock(context.getWorld(), context.getAbsolutePos(right)),
                "the other half of a linked double chest is stock too");
        context.waitAndRun(60, () -> {
            try {
                context.assertEquals(inventoryAt(context, left).count(Items.DIAMOND), 4, "nothing pulled from the linked half");
                context.assertEquals(inventoryAt(context, right).count(Items.DIAMOND), 4, "nothing pulled from the other half");
                context.assertTrue(inventoryAt(context, left.down()).isEmpty() && inventoryAt(context, right.down()).isEmpty(),
                        "the hoppers under the stock stay empty");
                context.assertEquals(inventoryAt(context, plain).count(Items.DIAMOND), 0, "a chest of no shop is emptied");
            } finally {
                forgetShops(context, shop);
            }
            context.complete();
        });
    }

    /** The links' reverse index follows every change: a link, an unlink, a merchant forgotten. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theMerchantOfABlockIsKnownAtOnce(TestContext context) {
        VendorLinkPersistentState state = VendorLinkPersistentState.get(context.getWorld().getServer());
        GlobalPos pos = GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(new BlockPos(1, 1, 1)));
        UUID vendor = UUID.randomUUID(), other = UUID.randomUUID();
        context.assertTrue(!state.isLinked(pos), "nothing linked there");
        state.linkBlock(vendor, pos);
        state.linkBlock(other, pos);
        context.assertTrue(state.isLinked(pos) && state.getVendorsLinkedTo(pos).equals(Set.of(vendor, other)), "two merchants there");
        state.unlinkBlock(vendor, pos);
        context.assertTrue(state.getVendorsLinkedTo(pos).equals(Set.of(other)), "one left");
        state.forgetVendor(other);
        context.assertTrue(!state.isLinked(pos) && state.getVendorsLinkedTo(pos).isEmpty(), "none any more");
        context.complete();
    }

    private static Inventory inventoryAt(TestContext context, BlockPos relative) {
        return (Inventory) context.getWorld().getBlockEntity(context.getAbsolutePos(relative));
    }

    /**
     * An explosion spares the stock of an owned shop and still destroys the others; stalls and registers are never
     * broken by one, owned or not (they are of the board blocks, see BoardExplosionGameTests).
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void explosionsSpareOwnedShopBlocks(TestContext context) {
        BlockPos stock = new BlockPos(2, 1, 1);
        BlockPos unownedStock = new BlockPos(2, 1, 3);
        BlockPos unownedStall = new BlockPos(3, 1, 3);
        context.setBlockState(stock, Blocks.CHEST);
        context.setBlockState(unownedStock, Blocks.CHEST);
        context.setBlockState(unownedStall, ModBlocks.TRADING_STALL);
        UUID shop = shopOf(context, UUID.randomUUID(), stock);
        UUID unownedShop = shopOf(context, null, unownedStock, unownedStall);
        try {
            BlockPos center = context.getAbsolutePos(new BlockPos(2, 1, 2));
            context.getWorld().createExplosion(null, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                    4.0f, World.ExplosionSourceType.TNT);
            context.assertTrue(context.getBlockState(stock).isOf(Blocks.CHEST), "owned stock kept");
            context.assertTrue(context.getBlockState(unownedStock).isAir(), "unowned stock destroyed as usual");
            context.assertTrue(context.getBlockState(unownedStall).isOf(ModBlocks.TRADING_STALL), "a stall, even unowned, is never blown up");
        } finally {
            forgetShops(context, shop, unownedShop);
        }
        context.complete();
    }

    private static ItemUsageContext useOn(PlayerEntity player, BlockPos pos) {
        return new ItemUsageContext(player, Hand.MAIN_HAND, new BlockHitResult(pos.toCenterPos(), Direction.UP, pos, false));
    }

    /** Full trader flow: the offer is never exhausted, and redstone next to the stall changes nothing. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void traderSellsFreelyWithoutRedstone(TestContext context) {
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

        BoxedTraderEntity trader = context.spawnEntity(ModEntities.BOXED_TRADER_ENTITY, new BlockPos(2, 1, 2));
        VendorLinkPersistentState links = VendorLinkPersistentState.get(context.getWorld().getServer());
        for (BlockPos pos : List.of(stallPos, chestPos, registerPos)) {
            links.linkBlock(trader.getUuid(), GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(pos)));
        }

        ServerPlayerEntity player = TestPlayers.mock(context);
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

        // The stall has no redstone control any more (the shop stops limit the purchases)
        context.setBlockState(stallPos.west(), Blocks.REDSTONE_BLOCK);
        context.waitAndRun(12, () -> {
            context.assertTrue(buyOnce(handler, player), "a powered stall still sells");
            context.assertTrue(buyOnce(handler, player), "and again");
            context.assertEquals(register.count(Items.EMERALD), 5, "every payment in the cash register");
            player.closeHandledScreen();
            TestPlayers.remove(context, player);
            context.complete();
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

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void boxedTraderBandanaColorIsRandomSavedAndNeverRerolled(TestContext context) {
        ServerWorld world = context.getWorld();
        // Summoned (initialize): a colour is picked
        BoxedTraderEntity summoned = ModEntities.BOXED_TRADER_ENTITY.create(world);
        summoned.initialize(world, world.getLocalDifficulty(summoned.getBlockPos()), SpawnReason.COMMAND, null);
        int color = summoned.getBandanaColor();
        context.assertTrue(color >= 0 && color < BoxedTraderEntity.BANDANA_COLORS, "colour 0-4 after spawn, got " + color);
        // Saved, then loaded many times: always the same colour
        NbtCompound saved = summoned.writeNbt(new NbtCompound());
        context.assertEquals(saved.getInt(BoxedTraderEntity.BANDANA_COLOR_NBT), color, "colour saved");
        for (int i = 0; i < 20; i++) {
            BoxedTraderEntity loaded = ModEntities.BOXED_TRADER_ENTITY.create(world);
            loaded.readNbt(saved);
            context.assertEquals(loaded.getBandanaColor(), color, "colour kept on reload " + i);
            loaded.initialize(world, world.getLocalDifficulty(loaded.getBlockPos()), SpawnReason.CHUNK_GENERATION, null);
            context.assertEquals(loaded.getBandanaColor(), color, "colour not re-rolled by initialize " + i);
        }
        // Summon with NBT: the given colour is used
        NbtCompound given = new NbtCompound();
        given.putInt(BoxedTraderEntity.BANDANA_COLOR_NBT, 3);
        BoxedTraderEntity fromNbt = ModEntities.BOXED_TRADER_ENTITY.create(world);
        fromNbt.readNbt(given);
        context.assertEquals(fromNbt.getBandanaColor(), 3, "BandanaColor from NBT");
        // Trader saved before bandanas existed: gets one when loaded
        NbtCompound legacy = summoned.writeNbt(new NbtCompound());
        legacy.remove(BoxedTraderEntity.BANDANA_COLOR_NBT);
        BoxedTraderEntity old = ModEntities.BOXED_TRADER_ENTITY.create(world);
        old.readNbt(legacy);
        context.assertTrue(old.getBandanaColor() >= 0 && old.getBandanaColor() < BoxedTraderEntity.BANDANA_COLORS, "legacy trader gets a colour");
        // Spawned from code without initialize() nor NBT (villager block fall): picked on its first tick
        BoxedTraderEntity spawned = context.spawnEntity(ModEntities.BOXED_TRADER_ENTITY, new BlockPos(1, 1, 1));
        context.waitAndRun(2, () -> {
            int c = spawned.getBandanaColor();
            context.assertTrue(c >= 0 && c < BoxedTraderEntity.BANDANA_COLORS, "colour picked on the first tick, got " + c);
            context.complete();
        });
    }

    /**
     * The screens of a trading stall and of a cash register close once the block is gone or the player walked away
     * (nothing put in a removed stall is lost with it); an emptied slot is empty again on the clients too.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shopScreensCloseWhenTheBlockIsGone(TestContext context) {
        BlockPos stallRelative = new BlockPos(1, 1, 1), registerRelative = new BlockPos(3, 1, 1);
        context.setBlockState(stallRelative, ModBlocks.TRADING_STALL);
        context.setBlockState(registerRelative, ModBlocks.CASH_REGISTER);
        TradingStallBlockEntity stall = context.getBlockEntity(stallRelative);
        CashRegisterBlockEntity register = context.getBlockEntity(registerRelative);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        BlockPos stallPos = context.getAbsolutePos(stallRelative);
        player.refreshPositionAndAngles(stallPos.getX() + 1.5, stallPos.getY(), stallPos.getZ() + 0.5, 0, 0);
        var stallScreen = new TradingStallScreenHandler(1, player.getInventory(), stall);
        var registerScreen = new CashRegisterScreenHandler(2, player.getInventory(), register);
        context.assertTrue(stallScreen.canUse(player) && registerScreen.canUse(player), "next to them: open");
        player.refreshPositionAndAngles(stallPos.getX() + 30, stallPos.getY(), stallPos.getZ(), 0, 0);
        context.assertTrue(!stallScreen.canUse(player) && !registerScreen.canUse(player), "far away: closed");
        player.refreshPositionAndAngles(stallPos.getX() + 1.5, stallPos.getY(), stallPos.getZ() + 0.5, 0, 0);
        context.setBlockState(stallRelative, Blocks.AIR);
        context.setBlockState(registerRelative, Blocks.AIR);
        context.assertTrue(!stallScreen.canUse(player) && !registerScreen.canUse(player), "the blocks gone: closed");

        // What the clients read: a slot emptied since the last update is empty
        context.setBlockState(stallRelative, ModBlocks.TRADING_STALL);
        TradingStallBlockEntity again = context.getBlockEntity(stallRelative);
        var registries = context.getWorld().getRegistryManager();
        again.setStack(0, new ItemStack(Items.EMERALD));
        NbtCompound before = again.toInitialChunkDataNbt(registries);
        again.setStack(0, ItemStack.EMPTY);
        NbtCompound after = again.toInitialChunkDataNbt(registries);
        again.read(before, registries);
        again.read(after, registries);
        context.assertTrue(again.getStack(0).isEmpty(), "an emptied slot is read as empty");
        context.complete();
    }

    private static RegistryEntry<Enchantment> sharpness(TestContext context) {
        return context.getWorld().getRegistryManager().getWrapperOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
    }
}
