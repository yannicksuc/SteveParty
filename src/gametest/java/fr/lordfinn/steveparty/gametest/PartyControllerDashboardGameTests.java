package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import com.mojang.serialization.JsonOps;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.StartRollsStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.custom.PartyCardItem;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.MiniGameGains;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyController;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyResources;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData.Blocker;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData.Board;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData.Issue;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData.Phase;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.BasicGameGeneratorStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.*;

/**
 * The Party Controller's dashboard: its settings (the Star and Coin items, the rounds), who may change them, when a
 * party may be started from it, and the state it shows (sent to the player only when it changed).
 */
public class PartyControllerDashboardGameTests implements SteveGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(1, 1, 1);

    private static PartyControllerEntity place(TestContext context) {
        context.setBlockState(CONTROLLER.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        return context.getBlockEntity(CONTROLLER);
    }

    /** A running party of one (absent) token, on a plain step: nothing it does can interfere. */
    private static void startParty(PartyControllerEntity controller) {
        UUID token = UUID.randomUUID();
        PartyData data = new PartyData();
        data.setNbTurn(controller.getPartyData().getNbTurn());
        data.addToken(token);
        data.addStep(new PartyStep());
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(token))));
        controller.setPartyData(data);
        data.setStepIndex(0);
    }

    private static boolean isOf(ItemStack stack, Item item) {
        return stack.isOf(item) && stack.getCount() == 1;
    }

    /** Defaults (the mod's Party Star and Coin), one item of the kind picked is kept, saved and loaded; the two differ. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void currenciesHaveDefaultsAndAreSaved(TestContext context) {
        PartyControllerEntity controller = place(context);
        context.assertTrue(isOf(controller.getCurrency(PartyCurrency.STAR), ModItems.PARTY_STAR), "default star: the mod's Party Star");
        context.assertTrue(isOf(controller.getCurrency(PartyCurrency.COIN), ModItems.COIN), "default coin: the mod's coin");

        ItemStack coin = new ItemStack(Items.GOLD_NUGGET, 12);
        coin.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Pièce"));
        context.assertTrue(controller.setCurrency(PartyCurrency.COIN, coin), "an item picked as coin");
        context.assertTrue(!controller.setCurrency(PartyCurrency.STAR, coin.copyWithCount(1)), "the coin's very item can't be the star");
        context.assertTrue(controller.setCurrency(PartyCurrency.STAR, new ItemStack(Items.DIAMOND, 3)), "a diamond as star");

        RegistryWrapper.WrapperLookup registries = context.getWorld().getRegistryManager();
        NbtCompound nbt = controller.createNbt(registries);
        PartyControllerEntity loaded = new PartyControllerEntity(controller.getPos(), controller.getCachedState());
        loaded.read(nbt, registries);
        context.assertTrue(isOf(loaded.getCurrency(PartyCurrency.STAR), Items.DIAMOND), "the star is saved (one item)");
        ItemStack loadedCoin = loaded.getCurrency(PartyCurrency.COIN);
        context.assertTrue(ItemStack.areItemsAndComponentsEqual(loadedCoin, coin) && loadedCoin.getCount() == 1, "the coin is saved with its name");

        PartyControllerEntity old = new PartyControllerEntity(controller.getPos(), controller.getCachedState());
        old.read(new NbtCompound(), registries);
        context.assertTrue(isOf(old.getCurrency(PartyCurrency.STAR), ModItems.PARTY_STAR)
                && isOf(old.getCurrency(PartyCurrency.COIN), ModItems.COIN), "nothing saved: the defaults");

        controller.setCurrency(PartyCurrency.STAR, ItemStack.EMPTY);
        context.assertTrue(isOf(controller.getCurrency(PartyCurrency.STAR), ModItems.PARTY_STAR), "nothing picked: back to the default");
        context.complete();
    }

    /**
     * The gains of the mini-games: 10, 5, 3 and 1 coins for the four places by default (nothing for the participants,
     * no star), changed one by one from the Gains page by who may edit the controller, saved and loaded, and shown by
     * the dashboard.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_dashboard_gains")
    public void gainsAreSetFromTheDashboardAndSaved(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            PartyControllerEntity controller = place(context);
            MiniGameGains gains = controller.getGains();
            context.assertEquals(gains, MiniGameGains.DEFAULT, "the default gains");
            int[] coins = new int[MiniGameGains.ROWS], stars = new int[MiniGameGains.ROWS];
            for (int row = 0; row < MiniGameGains.ROWS; row++) {
                coins[row] = gains.amount(PartyCurrency.COIN, row);
                stars[row] = gains.amount(PartyCurrency.STAR, row);
            }
            context.assertTrue(Arrays.equals(coins, new int[]{10, 5, 3, 1, 0}), "10, 5, 3, 1 coins; nothing for the participants");
            context.assertTrue(Arrays.equals(stars, new int[]{0, 0, 0, 0, 0}), "no star");
            context.assertEquals(MiniGameGains.rowOf(0), MiniGameGains.PARTICIPANTS, "no place: a participant");
            context.assertEquals(MiniGameGains.rowOf(7), MiniGameGains.PARTICIPANTS, "beyond the 4th place: a participant");

            PartyControllerScreenHandler handler = new PartyControllerScreenHandler(1, player.getInventory(), controller);
            // Adventure: read only
            player.changeGameMode(GameMode.ADVENTURE);
            context.assertTrue(!handler.onButtonClick(player, gainButton(0, PartyCurrency.COIN, true)), "Adventure: refused");
            context.assertEquals(controller.getGains(), MiniGameGains.DEFAULT, "Adventure: the gains are unchanged");
            // A builder: one more, one less, per row and per currency
            player.changeGameMode(GameMode.CREATIVE);
            context.assertTrue(handler.onButtonClick(player, gainButton(0, PartyCurrency.COIN, true)), "one more coin for the 1st");
            context.assertTrue(handler.onButtonClick(player, gainButton(1, PartyCurrency.COIN, false)), "one coin less for the 2nd");
            context.assertTrue(handler.onButtonClick(player, gainButton(0, PartyCurrency.STAR, true)), "a star for the 1st");
            context.assertTrue(handler.onButtonClick(player, gainButton(MiniGameGains.PARTICIPANTS, PartyCurrency.COIN, true)), "a coin for the participants");
            context.assertTrue(!handler.onButtonClick(player, gainButton(3, PartyCurrency.STAR, false)), "never below 0");
            MiniGameGains set = controller.getGains();
            context.assertEquals(set.amount(PartyCurrency.COIN, 0), 11, "1st: 11 coins");
            context.assertEquals(set.amount(PartyCurrency.COIN, 1), 4, "2nd: 4 coins");
            context.assertEquals(set.amount(PartyCurrency.STAR, 0), 1, "1st: a star");
            context.assertEquals(set.amount(PartyCurrency.COIN, MiniGameGains.PARTICIPANTS), 1, "participants: a coin");
            context.assertEquals(set.amount(PartyCurrency.STAR, 3), 0, "4th: no star");
            context.assertEquals(MiniGameGains.DEFAULT.with(PartyCurrency.COIN, 0, 500).amount(PartyCurrency.COIN, 0), MiniGameGains.MAX, "capped");

            // Paid as items of the party's currencies, taken from the bank
            SimpleInventory bank = new SimpleInventory(
                    controller.getCurrency(PartyCurrency.COIN).copyWithCount(20), controller.getCurrency(PartyCurrency.STAR).copyWithCount(3));
            PartyControllerEntity.Paid paid = controller.payGains(player, 1, PartyResources.of(List.of(bank)));
            context.assertTrue(paid.coins() == 11 && paid.stars() == 1 && paid.full(), "the whole gain paid");
            context.assertEquals(InventoryUtils.count(player.getInventory(), controller.getCurrency(PartyCurrency.COIN)), 11, "11 coins paid");
            context.assertEquals(InventoryUtils.count(player.getInventory(), controller.getCurrency(PartyCurrency.STAR)), 1, "a star paid");
            context.assertTrue(bank.getStack(0).getCount() == 9 && bank.getStack(1).getCount() == 2, "taken from the bank");
            context.assertTrue(!controller.payGains(player, 1, PartyResources.NONE).full(), "no bank: nothing paid");
            context.assertEquals(InventoryUtils.count(player.getInventory(), controller.getCurrency(PartyCurrency.COIN)), 11, "nothing created");

            // The dashboard shows them, and they travel to the client as they are
            Board board = new Board(0, 0, List.of(), List.of());
            PartyDashboardData data = PartyDashboardData.capture(controller, context.getWorld(), player, board);
            context.assertEquals(data.gains(), set, "captured by the dashboard");
            RegistryByteBuf buf = new RegistryByteBuf(io.netty.buffer.Unpooled.buffer(), context.getWorld().getRegistryManager());
            PartyDashboardData.PACKET_CODEC.encode(buf, data);
            context.assertEquals(PartyDashboardData.PACKET_CODEC.decode(buf).gains(), set, "sent and read back");
            buf.release();

            // Saved with the controller
            RegistryWrapper.WrapperLookup registries = context.getWorld().getRegistryManager();
            NbtCompound saved = controller.createNbt(registries);
            controller.setGains(MiniGameGains.DEFAULT);
            controller.read(saved, registries);
            context.assertEquals(controller.getGains(), set, "saved and loaded");
            saved.remove("MiniGameGains");
            controller.read(saved, registries);
            context.assertEquals(controller.getGains(), MiniGameGains.DEFAULT, "a controller saved without gains has the default ones");

            // During a party: operators and Game Masters only
            startParty(controller);
            context.assertEquals(handler.onButtonClick(player, gainButton(2, PartyCurrency.COIN, true)), player.hasPermissionLevel(2),
                    "during a party: operators only");
            context.setBlockState(CONTROLLER, Blocks.AIR);
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    /** The rounds stay between 1 and 50, and only change while no party runs. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void roundsAreClampedAndSetBeforeTheParty(TestContext context) {
        PartyControllerEntity controller = place(context);
        context.assertEquals(controller.getPartyData().getNbTurn(), 10, "10 rounds by default");
        controller.setRounds(0);
        context.assertEquals(controller.getPartyData().getNbTurn(), PartyControllerEntity.MIN_ROUNDS, "at least one round");
        controller.setRounds(80);
        context.assertEquals(controller.getPartyData().getNbTurn(), PartyControllerEntity.MAX_ROUNDS, "at most 50");
        controller.setRounds(7);
        startParty(controller);
        context.assertTrue(!controller.setRounds(3), "refused while a party runs");
        context.assertEquals(controller.getPartyData().getNbTurn(), 7, "unchanged");
        context.setBlockState(CONTROLLER, Blocks.AIR);
        context.complete();
    }

    /** A party starts from the dashboard only with a board, a start tile, a token on it, and a player allowed to. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aPartyStartsOnlyWhenReady(TestContext context) {
        List<UUID> token = List.of(UUID.randomUUID());
        context.assertEquals(PartyDashboardData.launchBlocker(false, new Board(0, 0, List.of(), List.of()), true), Blocker.NO_BOARD, "no board");
        context.assertEquals(PartyDashboardData.launchBlocker(false, new Board(12, 0, List.of(), List.of(new Issue("no_start", 0, true))), true),
                Blocker.NO_START, "no start tile");
        context.assertEquals(PartyDashboardData.launchBlocker(false, new Board(12, 2, List.of(), List.of()), true), Blocker.NO_TOKEN, "no token");
        context.assertEquals(PartyDashboardData.launchBlocker(false, new Board(12, 2, token, List.of()), false), Blocker.NOT_ALLOWED, "not allowed");
        context.assertEquals(PartyDashboardData.launchBlocker(false, new Board(12, 2, token, List.of(new Issue("dead_ends", 3, false))), true),
                Blocker.NONE, "ready: the warnings don't block");
        context.assertEquals(PartyDashboardData.launchBlocker(true, new Board(12, 2, token, List.of()), true), Blocker.RUNNING, "already running");
        context.complete();
    }

    /**
     * Server-checked edits: an Adventure player changes nothing, a builder picks the items (nothing is taken from the
     * cursor), can't give both currencies the same item, goes back to the default with an empty hand; during a party
     * only an operator may; following the party is open to anyone. The catalogue slot only takes a catalogue.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_dashboard_edit")
    public void onlyWhoMayEditChangesTheSettings(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            PartyControllerEntity controller = place(context);
            PartyControllerScreenHandler handler = new PartyControllerScreenHandler(1, player.getInventory(), controller);

            player.changeGameMode(GameMode.ADVENTURE);
            context.assertTrue(!controller.canEdit(player), "Adventure mode: read only");
            handler.setCursorStack(new ItemStack(Items.DIAMOND, 4));
            handler.onSlotClick(SLOT_STAR, 0, SlotActionType.PICKUP, player);
            context.assertTrue(isOf(controller.getCurrency(PartyCurrency.STAR), ModItems.PARTY_STAR), "Adventure: the star is unchanged");
            handler.onButtonClick(player, BUTTON_ROUNDS_UP);
            context.assertEquals(controller.getPartyData().getNbTurn(), 10, "Adventure: the rounds are unchanged");
            handler.onButtonClick(player, BUTTON_LAUNCH);
            context.assertTrue(!controller.getPartyData().isStarted(), "Adventure: no party started");

            player.changeGameMode(GameMode.CREATIVE);
            context.assertTrue(controller.canEdit(player), "a builder may edit");
            handler.onSlotClick(SLOT_STAR, 0, SlotActionType.PICKUP, player);
            context.assertTrue(isOf(controller.getCurrency(PartyCurrency.STAR), Items.DIAMOND), "the diamond clicked is the star");
            context.assertTrue(handler.getCursorStack().isOf(Items.DIAMOND) && handler.getCursorStack().getCount() == 4, "nothing taken from the cursor");
            handler.setCursorStack(new ItemStack(ModItems.COIN));
            handler.onSlotClick(SLOT_STAR, 0, SlotActionType.PICKUP, player);
            context.assertTrue(isOf(controller.getCurrency(PartyCurrency.STAR), Items.DIAMOND), "the coin's item refused as star");
            handler.setCursorStack(ItemStack.EMPTY);
            handler.onSlotClick(SLOT_STAR, 0, SlotActionType.PICKUP, player);
            context.assertTrue(isOf(controller.getCurrency(PartyCurrency.STAR), ModItems.PARTY_STAR), "empty hand: the default again");
            context.assertTrue(!handler.getSlot(SLOT_COIN).canTakeItems(player) && !handler.getSlot(SLOT_COIN).canInsert(new ItemStack(Items.DIAMOND)),
                    "a setting slot is never filled or emptied");
            handler.onButtonClick(player, BUTTON_ROUNDS_UP);
            context.assertEquals(controller.getPartyData().getNbTurn(), 11, "one more round");
            handler.onButtonClick(player, BUTTON_ROUNDS_DOWN);
            context.assertEquals(controller.getPartyData().getNbTurn(), 10, "one round less");

            // The catalogue slot
            handler.setCursorStack(new ItemStack(Items.DIAMOND));
            handler.onSlotClick(SLOT_CATALOGUE, 0, SlotActionType.PICKUP, player);
            context.assertTrue(controller.getCatalogue().isEmpty(), "no diamond in the catalogue slot");
            handler.setCursorStack(new ItemStack(ModItems.MINI_GAMES_CATALOGUE));
            handler.onSlotClick(SLOT_CATALOGUE, 0, SlotActionType.PICKUP, player);
            context.assertTrue(controller.getCatalogue().isOf(ModItems.MINI_GAMES_CATALOGUE) && handler.getCursorStack().isEmpty(), "the catalogue is in");
            context.assertTrue(context.getBlockState(CONTROLLER).get(PartyController.CATALOGUED), "the block shows its catalogue");
            handler.onSlotClick(SLOT_CATALOGUE, 0, SlotActionType.PICKUP, player);
            context.assertTrue(controller.getCatalogue().isEmpty() && handler.getCursorStack().isOf(ModItems.MINI_GAMES_CATALOGUE), "and out again");
            handler.setCursorStack(ItemStack.EMPTY);

            // During a party: operators and Game Masters only
            startParty(controller);
            context.assertEquals(controller.canEdit(player), player.hasPermissionLevel(2), "during a party: operators only");
            if (!player.hasPermissionLevel(2)) {
                handler.setCursorStack(new ItemStack(Items.GOLD_NUGGET));
                handler.onSlotClick(SLOT_COIN, 0, SlotActionType.PICKUP, player);
                context.assertTrue(isOf(controller.getCurrency(PartyCurrency.COIN), ModItems.COIN), "during a party: the coin is unchanged");
            }

            // Following the party: anyone
            player.changeGameMode(GameMode.ADVENTURE);
            handler.onButtonClick(player, BUTTON_FOLLOW);
            context.assertTrue(controller.getInterestedPlayers().contains(player.getUuid()), "follows the party");
            handler.onButtonClick(player, BUTTON_FOLLOW);
            context.assertTrue(!controller.getInterestedPlayers().contains(player.getUuid()), "no longer follows it");
            context.setBlockState(CONTROLLER, Blocks.AIR);
            context.complete();
        } finally {
            TestPlayers.remove(context, player);
        }
    }

    /**
     * What the dashboard shows: the phase, the rounds, the pages of the catalogue with how many times the roulette
     * chose each; sent when it changed only.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_dashboard_sync")
    public void dashboardShowsThePartyAndIsSentWhenItChanged(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            PartyControllerEntity controller = place(context);
            PartyControllerScreenHandler handler = new PartyControllerScreenHandler(1, player.getInventory(), controller);
            handler.sendContentUpdates();
            PartyDashboardData data = handler.getData();
            context.assertTrue(data != null && data.phase() == Phase.SETUP, "no party: setup");
            context.assertEquals(data.roundsSetting(), 10, "the rounds setting");
            context.assertTrue(!data.hasCatalogue() && data.pages().isEmpty(), "no catalogue");
            context.assertTrue(data.canEdit() && !data.following(), "may edit, doesn't follow");
            context.assertEquals(handler.getSentCount(), 1, "sent at once");
            for (int i = 0; i < 15; i++) handler.sendContentUpdates();
            context.assertEquals(handler.getSentCount(), 1, "nothing changed: nothing more sent");

            // A catalogue of two pages (and an empty slot); the roulette chose the second one in this party
            ItemStack catalogue = new ItemStack(ModItems.MINI_GAMES_CATALOGUE);
            ItemStack race = new ItemStack(ModItems.MINI_GAME_PAGE);
            race.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Course"));
            ItemStack battle = new ItemStack(ModItems.MINI_GAME_PAGE);
            battle.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Bataille"));
            catalogue.set(ModComponents.INVENTORY_COMPONENT, new InventoryComponent(List.of(race, ItemStack.EMPTY, battle)));
            controller.putCatalogue(catalogue);

            NbtCompound played = new NbtCompound();
            played.putString("Type", "MINI_GAME");
            played.putString("Status", "FINISHED");
            played.putInt("ChosenPage", 2);
            MiniGamePartyStep miniGame = new MiniGamePartyStep(played);
            context.assertEquals(new MiniGamePartyStep(miniGame.toNbt()).getChosenPageSlot(), 2, "the page chosen is saved");
            UUID token = UUID.randomUUID();
            PartyData party = new PartyData();
            party.addToken(token);
            party.addStep(new PartyStep());
            party.addStep(miniGame);
            party.addStep(new PartyStep());
            party.addStep(new EndPartyStep(new ArrayList<>(List.of(token))));
            controller.setPartyData(party);
            party.setStepIndex(2);

            for (int i = 0; i < SYNC_INTERVAL && handler.getSentCount() < 2; i++) handler.sendContentUpdates();
            context.assertEquals(handler.getSentCount(), 2, "a change: sent");
            data = handler.getData();
            context.assertTrue(data.phase() == Phase.RUNNING, "a party runs");
            context.assertEquals(data.players().size(), 1, "its token");
            context.assertTrue(data.hasCatalogue() && data.pages().size() == 2, "two pages (the empty slot skipped)");
            PartyDashboardData.Page first = data.pages().getFirst(), second = data.pages().get(1);
            context.assertTrue(first.slot() == 0 && first.played() == 0, "the race was not played");
            context.assertTrue(second.slot() == 2 && second.played() == 1, "the battle was played once");
            context.assertTrue(second.pipes() == 0 && second.playable() == 0, "no pipe linked: it can't be drawn");

            party.setStepIndex(3);
            for (int i = 0; i < SYNC_INTERVAL && handler.getSentCount() < 3; i++) handler.sendContentUpdates();
            context.assertTrue(handler.getData().phase() == Phase.ENDED, "on its END step: over");
            context.setBlockState(CONTROLLER, Blocks.AIR);
            context.complete();
        } finally {
            TestPlayers.remove(context, player);
        }
    }

    /**
     * The dashboard is compact: with its tabs and the player's inventory it fits a 427 x 240 screen. Each tab shows
     * its own slots (the catalogue and the cards on Program, the Star and Coin items on Gains, the inventory on both),
     * none overlapping, all inside their panel; the slots keep their indices.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theDashboardIsCompactAndItsSlotsFollowItsTabs(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            context.assertTrue(SLOT_CATALOGUE == 0 && SLOT_STAR == 1 && SLOT_COIN == 2 && PROGRAM_FIRST_SLOT == 39, "the slots keep their indices");
            context.assertTrue(WIDTH <= 427 && TABS_HEIGHT + INVENTORY_Y + INVENTORY_PANEL_HEIGHT <= 240, "tabs, page and inventory fit a 427 x 240 screen");
            context.assertTrue(PartyControllerEntity.PROGRAM_SLOTS == 2 * PROGRAM_COLUMNS && DICE_FIRST_SLOT == PROGRAM_FIRST_SLOT + 24,
                    "the program: 2 rows of 12 cards, the allowed dice after them");
            // The client's handler: the page shown decides which slots are there
            PartyControllerScreenHandler handler = new PartyControllerScreenHandler(1, player.getInventory(),
                    new BlockPosPayload(BlockPos.ORIGIN));
            for (Page page : Page.values()) {
                handler.setPage(page);
                boolean program = page == Page.PROGRAM, gains = page == Page.GAINS;
                context.assertTrue(handler.getSlot(SLOT_CATALOGUE).isEnabled() == program, page + ": the catalogue slot is on the Program tab");
                context.assertTrue(handler.getSlot(SLOT_STAR).isEnabled() == gains && handler.getSlot(SLOT_COIN).isEnabled() == gains,
                        page + ": the Star and Coin items are on the Gains tab");
                context.assertTrue(handler.getSlot(PROGRAM_FIRST_SLOT).isEnabled() == program, page + ": the cards are on the Program tab");
                context.assertTrue(handler.getSlot(PLAYER_SLOTS).isEnabled(), page + ": the inventory, on every tab");
                List<Slot> shown = handler.slots.stream().filter(Slot::isEnabled).toList();
                for (Slot slot : shown) {
                    boolean inventory = slot.inventory == player.getInventory();
                    // The player's inventory is centred in its panel: as much room on its left as on its right
                    if (inventory) context.assertTrue(slot.x - 1 >= (WIDTH - 9 * 18) / 2 && slot.x - 1 + 18 <= WIDTH - (WIDTH - 9 * 18) / 2,
                            page + ": the inventory is centred in its panel");
                    int top = inventory ? INVENTORY_Y : 0, bottom = inventory ? INVENTORY_Y + INVENTORY_PANEL_HEIGHT : PANEL_HEIGHT;
                    context.assertTrue(slot.x >= 4 && slot.x + 16 <= WIDTH - 4 && slot.y >= top + 4 && slot.y + 16 <= bottom - 4,
                            page + ": slot " + slot.id + " is inside its panel");
                    for (Slot other : shown) {
                        context.assertTrue(other == slot || Math.abs(other.x - slot.x) >= 18 || Math.abs(other.y - slot.y) >= 18,
                                page + ": slots " + slot.id + " and " + other.id + " don't overlap");
                    }
                }
            }
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    /**
     * The ghost cards shown while the program is empty are exactly the default party: written as real cards, they
     * would be played the same.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theGhostCardsAreTheDefaultParty(TestContext context) {
        for (int rounds : new int[]{1, 2, 10, 50}) {
            List<ItemStack> cards = new ArrayList<>();
            for (BasicGameGeneratorStep.ExpandedCard card : BasicGameGeneratorStep.defaultProgram(rounds)) {
                cards.add(new ItemStack(switch (card.type()) {
                    case TURNS -> ModItems.PARTY_CARD_TURNS;
                    case MINIGAME -> ModItems.PARTY_CARD_MINIGAME;
                    case EVENT -> ModItems.PARTY_CARD_EVENT;
                    case REPEAT -> ModItems.PARTY_CARD_REPEAT;
                    case SEQUENCE_START -> ModItems.PARTY_CARD_SEQUENCE_START;
                    case CUSTOM -> throw new IllegalArgumentException("not in the default program");
                }, card.count()));
            }
            context.assertTrue(cards.size() <= PartyControllerEntity.PROGRAM_SLOTS, "the ghost cards fit the program's slots");
            context.assertEquals(BasicGameGeneratorStep.expand(cards, 7), BasicGameGeneratorStep.expand(List.of(), rounds),
                    rounds + " rounds: the ghost cards are what an empty program plays");
        }
        context.assertEquals(BasicGameGeneratorStep.defaultProgram(10).stream().map(BasicGameGeneratorStep.ExpandedCard::type).toList(),
                List.of(PartyCardItem.CardType.TURNS, PartyCardItem.CardType.MINIGAME,
                        PartyCardItem.CardType.REPEAT), "the players' turn, a mini-game, repeated");
        context.complete();
    }

    private static List<PartyDashboardData.StepKind> kinds(PartyDashboardData.Timeline timeline) {
        return timeline.steps().stream().map(PartyDashboardData.TimelineStep::kind).toList();
    }

    private static List<Integer> rounds(PartyDashboardData.Timeline timeline) {
        return timeline.steps().stream().map(PartyDashboardData.TimelineStep::round).toList();
    }

    /**
     * The timeline of a program, before the party: the start rolls, the cards expanded (loops and sequences
     * unrolled), the end; a round ends with its mini-game. Long programs are cut, with how many steps are left out.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theProgramTimelineIsWhatWillBePlayed(TestContext context) {
        PartyDashboardData.StepKind rolls = PartyDashboardData.StepKind.START_ROLLS, turns = PartyDashboardData.StepKind.TURNS,
                game = PartyDashboardData.StepKind.MINI_GAME, event = PartyDashboardData.StepKind.EVENT, end = PartyDashboardData.StepKind.END;
        // No card: the default party
        PartyDashboardData.Timeline byDefault = PartyDashboardData.programTimeline(List.of(), 2);
        context.assertEquals(kinds(byDefault), List.of(rolls, turns, game, turns, game, end), "the default party of two rounds");
        context.assertEquals(rounds(byDefault), List.of(0, 1, 1, 2, 2, 0), "a round is the turns up to its mini-game");
        context.assertTrue(byDefault.current() == -1 && byDefault.more() == 0 && byDefault.offset() == 0, "nothing is being played, nothing is left out");

        // Cards with a sequence: the event once, then (turns, mini-game) three times
        List<ItemStack> cards = List.of(new ItemStack(ModItems.PARTY_CARD_EVENT, 4), new ItemStack(ModItems.PARTY_CARD_SEQUENCE_START),
                new ItemStack(ModItems.PARTY_CARD_TURNS), new ItemStack(ModItems.PARTY_CARD_MINIGAME), new ItemStack(ModItems.PARTY_CARD_REPEAT, 3));
        PartyDashboardData.Timeline program = PartyDashboardData.programTimeline(cards, 10);
        context.assertEquals(kinds(program), List.of(rolls, event, turns, game, turns, game, turns, game, end), "the loop of the sequence, unrolled");
        context.assertEquals(rounds(program), List.of(0, 1, 1, 1, 2, 2, 3, 3, 0), "three rounds");
        context.assertEquals(program.steps().get(1).value(), 4, "the channel of the event card");

        // A long program is cut
        PartyDashboardData.Timeline longOne = PartyDashboardData.programTimeline(List.of(), 50);
        context.assertEquals(longOne.steps().size(), PartyDashboardData.MAX_TIMELINE_STEPS, "at most 64 steps are sent");
        context.assertEquals(longOne.more(), 102 - PartyDashboardData.MAX_TIMELINE_STEPS, "and how many are left out (rolls + 50 x 2 + end)");
        context.complete();
    }

    /**
     * The timeline of a running party: its real steps, whose turn each is, the step being played, the rounds; a window
     * around the current step when the party is long. The dashboard sends both timelines.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_dashboard_timeline")
    public void thePartyTimelineFollowsTheCurrentStep(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            PartyControllerEntity controller = place(context);
            UUID a = UUID.randomUUID(), b = UUID.randomUUID();
            List<UUID> tokens = List.of(a, b);
            List<PartyStep> steps = new ArrayList<>();
            steps.add(new StartRollsStep());
            steps.add(new BasicGameGeneratorStep());
            for (int round = 0; round < 2; round++) {
                steps.add(new TokenTurnPartyStep(a, null));
                steps.add(new TokenTurnPartyStep(b, null));
                steps.add(new MiniGamePartyStep(new ArrayList<>(tokens)));
            }
            steps.add(new EndPartyStep(new ArrayList<>(tokens)));
            PartyDashboardData.Timeline timeline = PartyDashboardData.timelineOf(steps, 3, tokens);
            PartyDashboardData.StepKind turn = PartyDashboardData.StepKind.TURN, game = PartyDashboardData.StepKind.MINI_GAME;
            context.assertEquals(kinds(timeline), List.of(PartyDashboardData.StepKind.START_ROLLS, PartyDashboardData.StepKind.PREPARING,
                    turn, turn, game, turn, turn, game, PartyDashboardData.StepKind.END), "the steps of the party, in order");
            context.assertEquals(rounds(timeline), List.of(0, 0, 1, 1, 1, 2, 2, 2, 0), "the rounds: a round ends with its mini-game");
            context.assertEquals(timeline.steps().stream().map(PartyDashboardData.TimelineStep::player).toList(), List.of(-1, -1, 0, 1, -1, 0, 1, -1, -1),
                    "whose turn each turn is");
            context.assertTrue(timeline.current() == 3 && timeline.offset() == 0 && timeline.more() == 0, "the step being played");

            // A long party: a window around the current step
            List<PartyStep> many = new ArrayList<>();
            for (int i = 0; i < 200; i++) many.add(new TokenTurnPartyStep(a, null));
            PartyDashboardData.Timeline window = PartyDashboardData.timelineOf(many, 100, tokens);
            context.assertEquals(window.offset(), 100 - PartyDashboardData.TIMELINE_PAST_STEPS, "a few steps before the current one");
            context.assertEquals(window.current(), PartyDashboardData.TIMELINE_PAST_STEPS, "the current step in the window");
            context.assertTrue(window.steps().size() == PartyDashboardData.MAX_TIMELINE_STEPS
                    && window.more() == 200 - window.offset() - PartyDashboardData.MAX_TIMELINE_STEPS, "the steps after the window are counted");

            // What the dashboard sends: the program before the party, both during it
            PartyControllerScreenHandler handler = new PartyControllerScreenHandler(1, player.getInventory(), controller);
            handler.sendContentUpdates();
            context.assertTrue(handler.getData().steps().steps().isEmpty() && handler.getData().program().steps().size() == 22,
                    "before the party: no steps, the default program of 10 rounds");
            controller.getProgram().setStack(0, new ItemStack(ModItems.PARTY_CARD_TURNS));
            PartyData party = new PartyData();
            party.addToken(a);
            for (PartyStep step : steps) party.addStep(step);
            controller.setPartyData(party);
            party.setStepIndex(4);
            PartyDashboardData sent = PartyDashboardData.capture(controller, context.getWorld(), player, PartyDashboardData.Board.UNKNOWN);
            context.assertTrue(sent.steps().current() == 4 && sent.steps().steps().size() == 9, "during the party: its steps, the current one");
            context.assertEquals(kinds(sent.program()), List.of(PartyDashboardData.StepKind.START_ROLLS, PartyDashboardData.StepKind.TURNS,
                    PartyDashboardData.StepKind.END), "and the program of the next one");
            // It travels whole
            RegistryByteBuf buf = new RegistryByteBuf(io.netty.buffer.Unpooled.buffer(), context.getWorld().getRegistryManager());
            PartyDashboardData.PACKET_CODEC.encode(buf, sent);
            PartyDashboardData received = PartyDashboardData.PACKET_CODEC.decode(buf);
            buf.release();
            context.assertTrue(received.steps().equals(sent.steps()) && received.program().equals(sent.program()), "the timelines are sent as they are");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    /**
     * A controller copied as an item (pick block with its data) gives an item that can be saved and sent, with or
     * without a catalogue in it: the catalogue component never holds an empty stack.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aCopiedControllerIsAValidItem(TestContext context) {
        PartyControllerEntity controller = place(context);
        var ops = context.getWorld().getRegistryManager().getOps(JsonOps.INSTANCE);
        ItemStack empty = new ItemStack(ModBlocks.PARTY_CONTROLLER);
        empty.applyComponentsFrom(controller.createComponentMap());
        context.assertTrue(ItemStack.CODEC.encodeStart(ops, empty).isSuccess(), "empty controller: a valid item");
        controller.putCatalogue(new ItemStack(ModItems.MINI_GAMES_CATALOGUE));
        ItemStack full = new ItemStack(ModBlocks.PARTY_CONTROLLER);
        full.applyComponentsFrom(controller.createComponentMap());
        context.assertTrue(ItemStack.CODEC.encodeStart(ops, full).isSuccess() && full.get(ModComponents.CATALOGUE) != null,
                "with its catalogue: a valid item that carries it");
        context.complete();
    }

    /**
     * The catalogue goes in and out by clicking the block only for a player who may edit the controller, and the
     * redstone lock holds against a swap as against taking it out.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_dashboard_catalogue")
    public void theCatalogueIsLockedAgainstSwapsAndPlayers(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            PartyControllerEntity controller = place(context);
            BlockPos pos = context.getAbsolutePos(CONTROLLER);
            ItemStack first = new ItemStack(ModItems.MINI_GAMES_CATALOGUE);
            first.set(DataComponentTypes.CUSTOM_NAME, Text.literal("first"));
            controller.putCatalogue(first);
            var hit = new BlockHitResult(pos.toCenterPos(), Direction.UP, pos, false);
            var hand = Hand.MAIN_HAND;

            // Powered: a second catalogue does not replace the first. (Marked powered first: a rising edge would boot a
            // party, with the tokens on whatever start tiles other tests left within its reach, and lock it for good.)
            context.setBlockState(CONTROLLER, context.getBlockState(CONTROLLER).with(PartyController.POWERED, true));
            context.setBlockState(CONTROLLER.east(), Blocks.REDSTONE_BLOCK);
            player.changeGameMode(GameMode.SURVIVAL);
            player.setStackInHand(hand, new ItemStack(ModItems.MINI_GAMES_CATALOGUE));
            context.getWorld().getBlockState(pos).onUseWithItem(player.getStackInHand(hand), context.getWorld(), player, hand, hit);
            context.assertTrue(controller.getCatalogue() == first && !player.getStackInHand(hand).isEmpty(),
                    "powered: the catalogue stays, the one in hand too");
            context.setBlockState(CONTROLLER.east(), Blocks.AIR);

            // Adventure: neither swapped nor taken out
            player.changeGameMode(GameMode.ADVENTURE);
            context.getWorld().getBlockState(pos).onUseWithItem(player.getStackInHand(hand), context.getWorld(), player, hand, hit);
            context.assertTrue(controller.getCatalogue() == first, "adventure: not swapped");
            player.setStackInHand(hand, ItemStack.EMPTY);
            player.setSneaking(true);
            context.getWorld().getBlockState(pos).onUse(context.getWorld(), player, hit);
            context.assertTrue(controller.getCatalogue() == first, "adventure: not taken out");
            PartyControllerScreenHandler handler = new PartyControllerScreenHandler(1, player.getInventory(), controller);
            context.assertTrue(!handler.getSlot(SLOT_CATALOGUE).canTakeItems(player), "adventure: not taken from the screen");

            // A builder takes it out
            player.changeGameMode(GameMode.SURVIVAL);
            context.getWorld().getBlockState(pos).onUse(context.getWorld(), player, hit);
            context.assertTrue(controller.getCatalogue().isEmpty(), "survival: taken out");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }
}
