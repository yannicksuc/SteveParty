package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.blocks.custom.PartyController.MiniGameGains;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.items.custom.PartyCardItem;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.payloads.custom.PartyDashboardPayload;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumSet;

/**
 * The Party Controller's dashboard: five pages (state, players, program, gains, settings) fed by a
 * {@link PartyDashboardData} the server sends while the screen is open, three slots (the mini-game catalogue, the
 * star item and the coin item) and the player's inventory. Everything is checked server side: the setting slots and
 * the buttons and the program cards only act for a player who {@linkplain PartyControllerEntity#canEdit may edit} the controller (except
 * following the party, open to anyone), the catalogue slot follows the block's rule (locked while powered).
 * <p>
 * The currency slots (the Star and Coin items, shown on the Gains page) are « ghost » slots: clicking one with an item picks that item (nothing is taken), clicking it
 * with an empty hand puts the default item back.
 */
public class PartyControllerScreenHandler extends ScreenHandler {
    public static final int SLOT_CATALOGUE = 0, SLOT_STAR = 1, SLOT_COIN = 2, PLAYER_SLOTS = 3;
    public static final int BUTTON_LAUNCH = 0, BUTTON_FOLLOW = 1, BUTTON_ROUNDS_DOWN = 2, BUTTON_ROUNDS_UP = 3,
            BUTTON_PRACTICE = 5, BUTTON_MAX_POWER_UPS_DOWN = 6, BUTTON_MAX_POWER_UPS_UP = 7;
    /**
     * The steppers of the Gains page: {@code BUTTON_GAINS + row * 4 + column}, the columns being coins less, coins
     * more, stars less, stars more (see {@link #gainButton}).
     */
    public static final int BUTTON_GAINS = 100;
    /** Ticks between two captures of the dashboard (sent only if something changed). */
    public static final int SYNC_INTERVAL = 10;
    /** Ticks between two checks of the board while no party runs (a check walks the whole board). */
    public static final int BOARD_INTERVAL = 40;

    /** Pages of the dashboard: each shows its own slots (client side; the server always has them all). */
    public enum Page { STATE, PLAYERS, PROGRAM, GAINS, SETTINGS }

    // Layout (shared with the screen), the approved mock-up's (the art sources): the
    // tabs (17 px above the panel), the panel (125), 2 px, the player's inventory in its own panel (92): 236 px high,
    // it fits a 427 x 240 screen. The content of a panel is 10 px inside it (its bezel 4, a margin 6).
    public static final int WIDTH = 248, PANEL_HEIGHT = 125;
    /** The player's inventory (every tab): its own panel under the page, its grid centred, 8 px under its top. */
    public static final int INVENTORY_Y = PANEL_HEIGHT + 2, INVENTORY_PANEL_HEIGHT = 92, INVENTORY_PAD = 8;
    public static final int INVENTORY_GRID_X = (WIDTH - 162) / 2;
    /** Room taken by the tabs above the panel. */
    public static final int TABS_HEIGHT = 17;
    /** The content box of the panel. */
    public static final int CONTENT_X = 10, CONTENT_Y = 10, CONTENT_WIDTH = WIDTH - 2 * CONTENT_X;
    /** Program page: the catalogue slot, at the content's top left (its item at + 1). */
    public static final int CATALOGUE_X = CONTENT_X + 1, CATALOGUE_Y = CONTENT_Y + 1;
    /** Gains page: two columns of steppers at the right, the Coin and Star items centred above them. */
    public static final int GAINS_COLUMN = 62, GAINS_STAR_X = CONTENT_X + CONTENT_WIDTH - GAINS_COLUMN, GAINS_COIN_X = GAINS_STAR_X - 8 - GAINS_COLUMN;
    public static final int COIN_X = GAINS_COIN_X + (GAINS_COLUMN - 18) / 2 + 1, COIN_Y = CONTENT_Y + 1;
    public static final int STAR_X = GAINS_STAR_X + (GAINS_COLUMN - 18) / 2 + 1, STAR_Y = CONTENT_Y + 1;
    /** The party program: its card slots come after the player's inventory, 2 rows of 12 centred (Program page). */
    public static final int PROGRAM_FIRST_SLOT = PLAYER_SLOTS + 36;
    public static final int PROGRAM_COLUMNS = 12;
    public static final int PROGRAM_X = CONTENT_X + (CONTENT_WIDTH - PROGRAM_COLUMNS * 18) / 2 + 1, PROGRAM_Y = CONTENT_Y + 33;
    /** Gains page: the bank's Inventory Cartridge (after the program's slots), at the content's top left. */
    public static final int SLOT_BANK = PROGRAM_FIRST_SLOT + PartyControllerEntity.PROGRAM_SLOTS;
    public static final int BANK_X = CONTENT_X + 1, BANK_Y = CONTENT_Y + 1;

    private final @Nullable PartyControllerEntity controller;
    private final BlockPos pos;
    private final PlayerEntity player;
    /** Client: the page shown (which slots are enabled). */
    private Page page = Page.STATE;
    /** Client: the last data received; server: the last data sent. Null until the first one. */
    private @Nullable PartyDashboardData data;
    // server
    private int ticks;
    private @Nullable PartyDashboardData.Board board;
    private byte[] lastSent;
    private boolean refreshNow;
    /** Dashboard states sent to the player so far (only the changed ones are). */
    private int sentCount;

    /** Server side. */
    public PartyControllerScreenHandler(int syncId, PlayerInventory playerInventory, PartyControllerEntity controller) {
        this(syncId, playerInventory, controller.getPos(), controller, catalogueInventory(controller), currencyInventory(controller),
                controller.getProgram());
    }

    /** Client side. */
    public PartyControllerScreenHandler(int syncId, PlayerInventory playerInventory, BlockPosPayload payload) {
        this(syncId, playerInventory, payload.pos(), null, new SimpleInventory(1), new SimpleInventory(2),
                new SimpleInventory(PartyControllerEntity.PROGRAM_SLOTS), new SimpleInventory(1));
    }

    private PartyControllerScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos,
                                         @Nullable PartyControllerEntity controller, Inventory catalogue, Inventory currencies,
                                         Inventory program) {
        this(syncId, playerInventory, pos, controller, catalogue, currencies, program, bankInventory(controller));
    }

    private PartyControllerScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos,
                                         @Nullable PartyControllerEntity controller, Inventory catalogue, Inventory currencies,
                                         Inventory program, Inventory bank) {
        super(ModScreensHandlers.PARTY_CONTROLLER_SCREEN_HANDLER, syncId);
        this.controller = controller;
        this.pos = pos;
        this.player = playerInventory.player;
        addSlot(new CatalogueSlot(catalogue, CATALOGUE_X, CATALOGUE_Y));
        addSlot(new GhostSlot(currencies, 0, STAR_X, STAR_Y));
        addSlot(new GhostSlot(currencies, 1, COIN_X, COIN_Y));
        int invX = INVENTORY_GRID_X + 1;
        EnumSet<Page> withInventory = EnumSet.allOf(Page.class);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++)
                addSlot(new PageSlot(playerInventory, col + row * 9 + 9, invX + col * 18, INVENTORY_Y + INVENTORY_PAD + 1 + row * 18, withInventory));
        }
        for (int col = 0; col < 9; col++)
            addSlot(new PageSlot(playerInventory, col, invX + col * 18, INVENTORY_Y + INVENTORY_PAD + 1 + 58, withInventory));
        for (int i = 0; i < PartyControllerEntity.PROGRAM_SLOTS; i++)
            addSlot(new CardSlot(program, i, PROGRAM_X + (i % PROGRAM_COLUMNS) * 18, PROGRAM_Y + (i / PROGRAM_COLUMNS) * 18));
        addSlot(new BankSlot(bank));
    }

    /** The bank's cartridge slot (server: the controller's; client: filled by the slot sync). */
    private static Inventory bankInventory(@Nullable PartyControllerEntity controller) {
        if (controller == null) return new SimpleInventory(1);
        return new Inventory() {
            @Override public int size() { return 1; }
            @Override public boolean isEmpty() { return controller.getBank().isEmpty(); }
            @Override public ItemStack getStack(int slot) { return controller.getBank(); }
            @Override public ItemStack removeStack(int slot, int amount) { return removeStack(slot); }
            @Override public ItemStack removeStack(int slot) {
                ItemStack stack = controller.getBank();
                controller.setBank(ItemStack.EMPTY);
                return stack;
            }
            @Override public void setStack(int slot, ItemStack stack) { controller.setBank(stack); }
            @Override public int getMaxCountPerStack() { return 1; }
            @Override public void markDirty() { controller.markDirty(); }
            @Override public boolean canPlayerUse(PlayerEntity player) { return ScreenHandlerChecks.canUseBlockEntity(controller, player); }
            @Override public void clear() { controller.setBank(ItemStack.EMPTY); }
        };
    }

    // ------------------------------------------------------------------ inventories (server)

    private static Inventory catalogueInventory(PartyControllerEntity controller) {
        return new Inventory() {
            @Override public int size() { return 1; }
            @Override public boolean isEmpty() { return controller.getCatalogue().isEmpty(); }
            @Override public ItemStack getStack(int slot) { return controller.getCatalogue(); }
            @Override public ItemStack removeStack(int slot, int amount) { return removeStack(slot); }
            @Override public ItemStack removeStack(int slot) {
                ItemStack stack = controller.getCatalogue();
                controller.putCatalogue(ItemStack.EMPTY);
                return stack;
            }
            @Override public void setStack(int slot, ItemStack stack) { controller.putCatalogue(stack); }
            @Override public int getMaxCountPerStack() { return 1; }
            @Override public void markDirty() { controller.markDirty(); }
            @Override public boolean canPlayerUse(PlayerEntity player) { return ScreenHandlerChecks.canUseBlockEntity(controller, player); }
            @Override public void clear() { controller.putCatalogue(ItemStack.EMPTY); }
        };
    }

    /** The two currency items, read-only: they change through {@link #onSlotClick} only. */
    private static Inventory currencyInventory(PartyControllerEntity controller) {
        return new SimpleInventory(2) {
            @Override public ItemStack getStack(int slot) {
                return controller.getCurrency(slot == 0 ? PartyCurrency.STAR : PartyCurrency.COIN);
            }
            @Override public ItemStack removeStack(int slot, int amount) { return ItemStack.EMPTY; }
            @Override public ItemStack removeStack(int slot) { return ItemStack.EMPTY; }
            @Override public void setStack(int slot, ItemStack stack) {}
        };
    }

    // ------------------------------------------------------------------ slots

    private class PageSlot extends Slot {
        private final EnumSet<Page> pages;

        PageSlot(Inventory inventory, int index, int x, int y, EnumSet<Page> pages) {
            super(inventory, index, x, y);
            this.pages = pages;
        }

        @Override
        public boolean isEnabled() {
            // Server side every slot is there: the page only hides them on screen
            return controller != null || pages.contains(page);
        }
    }

    private class CatalogueSlot extends PageSlot {
        CatalogueSlot(Inventory inventory, int x, int y) {
            super(inventory, 0, x, y, EnumSet.of(Page.PROGRAM));
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return stack.getItem() instanceof MiniGamesCatalogueItem && mayEditProgram();
        }

        @Override
        public boolean canTakeItems(PlayerEntity playerEntity) {
            // Like the program: a party's players don't take its catalogue away
            return !isCatalogueLocked() && mayEditProgram();
        }

        @Override
        public int getMaxItemCount() {
            return 1;
        }
    }

    private class GhostSlot extends PageSlot {
        GhostSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y, EnumSet.of(Page.GAINS));
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeItems(PlayerEntity playerEntity) {
            return false;
        }
    }

    /** The bank's slot: an Inventory Cartridge (its container is the bank), changed only by a player who may edit the controller. */
    private class BankSlot extends PageSlot {
        BankSlot(Inventory inventory) {
            super(inventory, 0, BANK_X, BANK_Y, EnumSet.of(Page.GAINS));
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return stack.getItem() instanceof fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem && mayEditProgram();
        }

        @Override
        public boolean canTakeItems(PlayerEntity playerEntity) {
            return mayEditProgram();
        }

        @Override
        public int getMaxItemCount() {
            return 1;
        }
    }

    /** A slot of the party program: party cards only, changed only by a player who may edit the controller. */
    private class CardSlot extends PageSlot {
        CardSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y, EnumSet.of(Page.PROGRAM));
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return stack.getItem() instanceof PartyCardItem && mayEditProgram();
        }

        @Override
        public boolean canTakeItems(PlayerEntity playerEntity) {
            return mayEditProgram();
        }
    }

    /** Whether the player may change the program (server: the controller's rule, client: what the server said). */
    public boolean mayEditProgram() {
        if (controller != null) return controller.canEdit(player);
        return data != null && data.canEdit();
    }

    public static boolean isProgramSlot(int slotIndex) {
        return slotIndex >= PROGRAM_FIRST_SLOT && slotIndex < PROGRAM_FIRST_SLOT + PartyControllerEntity.PROGRAM_SLOTS;
    }

    public static boolean isGhostSlot(int slotIndex) {
        return slotIndex == SLOT_STAR || slotIndex == SLOT_COIN;
    }

    public static PartyCurrency currencyOf(int slotIndex) {
        return slotIndex == SLOT_STAR ? PartyCurrency.STAR : PartyCurrency.COIN;
    }

    /** The catalogue can't be taken out while the controller is powered (like with a sneaking click on the block). */
    public boolean isCatalogueLocked() {
        if (controller != null) return controller.isCatalogueLocked();
        return data != null && data.catalogueLocked();
    }

    // ------------------------------------------------------------------ clicks

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (isGhostSlot(slotIndex)) {
            if (controller != null && actionType == SlotActionType.PICKUP)
                pickCurrency(player, currencyOf(slotIndex), getCursorStack());
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    /** A click on a setting slot: {@code picked} becomes that currency (empty: its default item). Server side. */
    public boolean pickCurrency(PlayerEntity player, PartyCurrency currency, ItemStack picked) {
        if (controller == null) return false;
        if (!controller.canEdit(player)) {
            player.sendMessage(Text.translatable("gui.steveparty.party_controller.locked").formatted(Formatting.RED), true);
            return false;
        }
        if (!controller.setCurrency(currency, picked)) {
            player.sendMessage(Text.translatable("gui.steveparty.party_controller.settings.same_item").formatted(Formatting.RED), true);
            return false;
        }
        refreshNow = true;
        return true;
    }

    @Override
    public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
        return !isGhostSlot(slot.id) && super.canInsertIntoSlot(stack, slot);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        if (index < 0 || index >= slots.size() || isGhostSlot(index)) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        if (index == SLOT_CATALOGUE || isProgramSlot(index) || index == SLOT_BANK) {
            if (!slot.canTakeItems(player) || !insertItem(stack, PLAYER_SLOTS, PROGRAM_FIRST_SLOT, true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem) {
            Slot bank = slots.get(SLOT_BANK);
            if (!bank.canInsert(stack) || bank.hasStack() || !insertItem(stack, SLOT_BANK, SLOT_BANK + 1, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof PartyCardItem) {
            if (!mayEditProgram() || !insertItem(stack, PROGRAM_FIRST_SLOT, PROGRAM_FIRST_SLOT + PartyControllerEntity.PROGRAM_SLOTS, false))
                return ItemStack.EMPTY;
        } else {
            Slot catalogue = slots.get(SLOT_CATALOGUE);
            if (!catalogue.canInsert(stack) || catalogue.hasStack() || !insertItem(stack, SLOT_CATALOGUE, SLOT_CATALOGUE + 1, false))
                return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
        else slot.markDirty();
        return original;
    }

    /** The button of the Gains page that changes the gain of a row by one. */
    public static int gainButton(int row, PartyCurrency currency, boolean more) {
        return BUTTON_GAINS + row * 4 + (currency == PartyCurrency.STAR ? 2 : 0) + (more ? 1 : 0);
    }

    /** A stepper of the Gains page: one more or one less, for a player who may edit the controller. */
    private boolean changeGain(PlayerEntity player, int button) {
        if (controller == null) return false;
        if (!controller.canEdit(player)) {
            player.sendMessage(Text.translatable("gui.steveparty.party_controller.locked").formatted(Formatting.RED), true);
            return false;
        }
        int row = button / 4;
        PartyCurrency currency = button % 4 >= 2 ? PartyCurrency.STAR : PartyCurrency.COIN;
        int amount = controller.getGains().amount(currency, row) + (button % 2 == 1 ? 1 : -1);
        if (amount < 0 || amount > MiniGameGains.MAX) return false;
        controller.setGains(controller.getGains().with(currency, row, amount));
        return true;
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (controller == null || !(player instanceof ServerPlayerEntity serverPlayer)
                || !(controller.getWorld() instanceof ServerWorld world)) return false;
        if (id >= BUTTON_GAINS && id < BUTTON_GAINS + MiniGameGains.ROWS * 4) {
            if (!changeGain(player, id - BUTTON_GAINS)) return false;
            refreshNow = true;
            return true;
        }
        switch (id) {
            case BUTTON_FOLLOW -> {
                if (controller.getInterestedPlayers().contains(player.getUuid())) controller.removeInterestedPlayer(serverPlayer);
                else controller.addInterestedPlayer(serverPlayer);
                world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
            case BUTTON_ROUNDS_DOWN, BUTTON_ROUNDS_UP -> {
                if (!controller.canEdit(player)) return false;
                int rounds = controller.getPartyData().getNbTurn() + (id == BUTTON_ROUNDS_UP ? 1 : -1);
                if (!controller.setRounds(rounds)) return false;
            }
            case BUTTON_MAX_POWER_UPS_DOWN, BUTTON_MAX_POWER_UPS_UP -> {
                if (!controller.canEdit(player)) return false;
                controller.setMaxPowerUps(controller.getMaxPowerUps() + (id == BUTTON_MAX_POWER_UPS_UP ? 1 : -1));
            }
            case BUTTON_PRACTICE -> {
                if (!controller.canEdit(player)) return false;
                controller.setPracticeRound(!controller.hasPracticeRound());
            }
            case BUTTON_LAUNCH -> {
                board = PartyDashboardData.checkBoard(controller, world);
                if (PartyDashboardData.launchBlocker(controller.getPartyData().isStarted(), board, controller.canEdit(player))
                        != PartyDashboardData.Blocker.NONE) return false;
                controller.boot();
            }
            default -> {
                return false;
            }
        }
        refreshNow = true;
        return true;
    }

    // ------------------------------------------------------------------ sync (server)

    @Override
    public void sendContentUpdates() {
        super.sendContentUpdates();
        if (controller == null || !(player instanceof ServerPlayerEntity serverPlayer)
                || !(controller.getWorld() instanceof ServerWorld world)) return;
        ticks++;
        boolean running = controller.getPartyData().isStarted();
        if (board == null || (!running && ticks % BOARD_INTERVAL == 0)) board = PartyDashboardData.checkBoard(controller, world);
        if (!refreshNow && ticks % SYNC_INTERVAL != 1) return;
        refreshNow = false;
        PartyDashboardData captured = PartyDashboardData.capture(controller, world, serverPlayer, board);
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), world.getRegistryManager());
        byte[] bytes;
        try {
            PartyDashboardData.PACKET_CODEC.encode(buf, captured);
            bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
        } finally {
            buf.release();
        }
        data = captured;
        if (Arrays.equals(bytes, lastSent)) return;
        lastSent = bytes;
        sentCount++;
        ServerPlayNetworking.send(serverPlayer, new PartyDashboardPayload(syncId, captured));
    }

    public int getSentCount() {
        return sentCount;
    }

    // ------------------------------------------------------------------ accessors

    public @Nullable PartyDashboardData getData() {
        return data;
    }

    /** Client: a new state from the server. */
    public void setData(PartyDashboardData data) {
        this.data = data;
    }

    public Page getPage() {
        return page;
    }

    /** Client: the page shown. */
    public void setPage(Page page) {
        this.page = page;
    }

    public BlockPos getPos() {
        return pos;
    }

    public @Nullable PartyControllerEntity getController() {
        return controller;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return controller == null || ScreenHandlerChecks.canUseBlockEntity(controller, player);
    }
}
