package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
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
 * The Party Controller's dashboard: four pages (state, players, mini-games, settings) fed by a
 * {@link PartyDashboardData} the server sends while the screen is open, three slots (the mini-game catalogue, the
 * star item and the coin item) and the player's inventory. Everything is checked server side: the setting slots and
 * the buttons only act for a player who {@linkplain PartyControllerEntity#canEdit may edit} the controller (except
 * following the party, open to anyone), the catalogue slot follows the block's rule (locked while powered).
 * <p>
 * The setting slots are « ghost » slots: clicking one with an item picks that item (nothing is taken), clicking it
 * with an empty hand puts the default item back.
 */
public class PartyControllerScreenHandler extends ScreenHandler {
    public static final int SLOT_CATALOGUE = 0, SLOT_STAR = 1, SLOT_COIN = 2, PLAYER_SLOTS = 3;
    public static final int BUTTON_LAUNCH = 0, BUTTON_FOLLOW = 1, BUTTON_ROUNDS_DOWN = 2, BUTTON_ROUNDS_UP = 3,
            BUTTON_CHECK_BOARD = 4;
    /** Ticks between two captures of the dashboard (sent only if something changed). */
    public static final int SYNC_INTERVAL = 10;
    /** Ticks between two checks of the board while no party runs (a check walks the whole board). */
    public static final int BOARD_INTERVAL = 100;

    /** Pages of the dashboard: each shows its own slots (client side; the server always has them all). */
    public enum Page { STATE, PLAYERS, MINI_GAMES, SETTINGS }

    // Layout (shared with the screen)
    public static final int WIDTH = 248, PANEL_HEIGHT = 158;
    public static final int INVENTORY_Y = PANEL_HEIGHT + 4;
    public static final int CATALOGUE_X = 14, CATALOGUE_Y = 32;
    public static final int STAR_X = 16, STAR_Y = 34, COIN_X = 16, COIN_Y = 82;

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
        this(syncId, playerInventory, controller.getPos(), controller, catalogueInventory(controller), currencyInventory(controller));
    }

    /** Client side. */
    public PartyControllerScreenHandler(int syncId, PlayerInventory playerInventory, BlockPosPayload payload) {
        this(syncId, playerInventory, payload.pos(), null, new SimpleInventory(1), new SimpleInventory(2));
    }

    private PartyControllerScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos,
                                         @Nullable PartyControllerEntity controller, Inventory catalogue, Inventory currencies) {
        super(ModScreensHandlers.PARTY_CONTROLLER_SCREEN_HANDLER, syncId);
        this.controller = controller;
        this.pos = pos;
        this.player = playerInventory.player;
        addSlot(new CatalogueSlot(catalogue, CATALOGUE_X, CATALOGUE_Y));
        addSlot(new GhostSlot(currencies, 0, STAR_X, STAR_Y));
        addSlot(new GhostSlot(currencies, 1, COIN_X, COIN_Y));
        int invX = (WIDTH - 162) / 2 + 1;
        EnumSet<Page> withInventory = EnumSet.of(Page.MINI_GAMES, Page.SETTINGS);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++)
                addSlot(new PageSlot(playerInventory, col + row * 9 + 9, invX + col * 18, INVENTORY_Y + 14 + row * 18, withInventory));
        }
        for (int col = 0; col < 9; col++)
            addSlot(new PageSlot(playerInventory, col, invX + col * 18, INVENTORY_Y + 14 + 58, withInventory));
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
            super(inventory, 0, x, y, EnumSet.of(Page.MINI_GAMES));
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return stack.getItem() instanceof MiniGamesCatalogueItem;
        }

        @Override
        public boolean canTakeItems(PlayerEntity playerEntity) {
            return !isCatalogueLocked();
        }

        @Override
        public int getMaxItemCount() {
            return 1;
        }
    }

    private class GhostSlot extends PageSlot {
        GhostSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y, EnumSet.of(Page.SETTINGS));
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
        if (index == SLOT_CATALOGUE) {
            if (!slot.canTakeItems(player) || !insertItem(stack, PLAYER_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            Slot catalogue = slots.get(SLOT_CATALOGUE);
            if (!catalogue.canInsert(stack) || catalogue.hasStack() || !insertItem(stack, SLOT_CATALOGUE, SLOT_CATALOGUE + 1, false))
                return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
        else slot.markDirty();
        return original;
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (controller == null || !(player instanceof ServerPlayerEntity serverPlayer)
                || !(controller.getWorld() instanceof ServerWorld world)) return false;
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
            case BUTTON_CHECK_BOARD -> board = PartyDashboardData.checkBoard(controller, world);
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
