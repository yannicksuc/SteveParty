package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyController;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData.Blocker;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData.Board;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData.Issue;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData.Phase;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.*;

/**
 * The Party Controller's dashboard: its settings (the Star and Coin items, the rounds), who may change them, when a
 * party may be started from it, and the state it shows (sent to the player only when it changed).
 */
public class PartyControllerDashboardGameTests implements FabricGameTest {
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

    private static boolean isOf(ItemStack stack, net.minecraft.item.Item item) {
        return stack.isOf(item) && stack.getCount() == 1;
    }

    /** Defaults (nether star, emerald), one item of the kind picked is kept, saved and loaded; the two differ. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void currenciesHaveDefaultsAndAreSaved(TestContext context) {
        PartyControllerEntity controller = place(context);
        context.assertTrue(isOf(controller.getCurrency(PartyCurrency.STAR), Items.NETHER_STAR), "default star: a nether star");
        context.assertTrue(isOf(controller.getCurrency(PartyCurrency.COIN), Items.EMERALD), "default coin: an emerald");

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
        context.assertTrue(isOf(old.getCurrency(PartyCurrency.STAR), Items.NETHER_STAR)
                && isOf(old.getCurrency(PartyCurrency.COIN), Items.EMERALD), "nothing saved: the defaults");

        controller.setCurrency(PartyCurrency.STAR, ItemStack.EMPTY);
        context.assertTrue(isOf(controller.getCurrency(PartyCurrency.STAR), Items.NETHER_STAR), "nothing picked: back to the default");
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
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            PartyControllerEntity controller = place(context);
            PartyControllerScreenHandler handler = new PartyControllerScreenHandler(1, player.getInventory(), controller);

            player.changeGameMode(GameMode.ADVENTURE);
            context.assertTrue(!controller.canEdit(player), "Adventure mode: read only");
            handler.setCursorStack(new ItemStack(Items.DIAMOND, 4));
            handler.onSlotClick(SLOT_STAR, 0, SlotActionType.PICKUP, player);
            context.assertTrue(isOf(controller.getCurrency(PartyCurrency.STAR), Items.NETHER_STAR), "Adventure: the star is unchanged");
            handler.onButtonClick(player, BUTTON_ROUNDS_UP);
            context.assertEquals(controller.getPartyData().getNbTurn(), 10, "Adventure: the rounds are unchanged");
            handler.onButtonClick(player, BUTTON_LAUNCH);
            context.assertTrue(!controller.getPartyData().isStarted(), "Adventure: no party started");

            player.changeGameMode(GameMode.CREATIVE);
            context.assertTrue(controller.canEdit(player), "a builder may edit");
            handler.onSlotClick(SLOT_STAR, 0, SlotActionType.PICKUP, player);
            context.assertTrue(isOf(controller.getCurrency(PartyCurrency.STAR), Items.DIAMOND), "the diamond clicked is the star");
            context.assertTrue(handler.getCursorStack().isOf(Items.DIAMOND) && handler.getCursorStack().getCount() == 4, "nothing taken from the cursor");
            handler.setCursorStack(new ItemStack(Items.EMERALD));
            handler.onSlotClick(SLOT_STAR, 0, SlotActionType.PICKUP, player);
            context.assertTrue(isOf(controller.getCurrency(PartyCurrency.STAR), Items.DIAMOND), "the coin's item refused as star");
            handler.setCursorStack(ItemStack.EMPTY);
            handler.onSlotClick(SLOT_STAR, 0, SlotActionType.PICKUP, player);
            context.assertTrue(isOf(controller.getCurrency(PartyCurrency.STAR), Items.NETHER_STAR), "empty hand: the default again");
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
                context.assertTrue(isOf(controller.getCurrency(PartyCurrency.COIN), Items.EMERALD), "during a party: the coin is unchanged");
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
            context.getWorld().getServer().getPlayerManager().remove(player);
        }
    }

    /**
     * What the dashboard shows: the phase, the rounds, the pages of the catalogue with how many times the roulette
     * chose each; sent when it changed only.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_dashboard_sync")
    public void dashboardShowsThePartyAndIsSentWhenItChanged(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
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
            context.getWorld().getServer().getPlayerManager().remove(player);
        }
    }
}
