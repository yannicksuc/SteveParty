package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopStock;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenus;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeRef;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import fr.lordfinn.steveparty.screen_handlers.PlayerSlots;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * The offers of a Shop Cartridge, edited on a Trading Stall: the stall block's own screen (its texture, its slots at the
 * same places), bound to the cartridge (in hand or in a block's slot, {@link CartridgeRef}). Its
 * {@value ShopCartridgeItem#SLOTS} slots come first, in the stall's order (0-8 the prices, 9-17 the second prices, 18-26
 * the items sold over the awning), and are ghost slots: an item put there is a copy, its quantity set with the mouse
 * wheel, nothing real is ever taken or given. The player's inventory follows, to pick the items from. The stock of each
 * offer ({@link ShopStock}) is sent with it; button {@link #BACK} goes back to the menu it was opened from.
 */
public class ShopStallScreenHandler extends ScreenHandler implements GhostSlotHost {
    /** Button: back to the cartridge's menu (or the tile's interface) it was opened from. */
    public static final int BACK = 0;
    /** The stall's slots, as on the Trading Stall's screen: the columns, the sold row, the two price rows. */
    public static final int SLOTS_X = 12, SOLD_Y = 0, PRICE_Y = 53, SLOT = 18;
    /** The player's inventory, as on the Trading Stall's screen. */
    public static final int INVENTORY_X = 12, INVENTORY_Y = 105;

    private final CartridgeRef ref;
    private final PlayerEntity player;
    /** The cartridge when the screen opened: it closes when it is gone (dropped, taken out of the block). */
    private final ItemStack holder;
    private final Item holderItem;
    private final ShopStock.Synced stock = new ShopStock.Synced(this::addProperty);
    /** Server side: where {@link #BACK} goes. */
    private @Nullable Runnable back;

    public ShopStallScreenHandler(int syncId, PlayerInventory playerInventory, CartridgeRef ref) {
        super(ModScreensHandlers.SHOP_STALL_SCREEN_HANDLER, syncId);
        this.ref = ref;
        this.player = playerInventory.player;
        this.holder = ref.resolve(player);
        this.holderItem = holder.getItem();
        Inventory offers = player.getWorld().isClient ? new SimpleInventory(ShopCartridgeItem.SLOTS)
                : new ShopOffersInventory(() -> ref.resolve(player), () -> ref.commit(player.getWorld()));
        for (int i = 0; i < ShopCartridgeItem.SLOTS; i++) {
            int column = i % ShopCartridgeItem.OFFERS, row = i / ShopCartridgeItem.OFFERS;
            // Rows 0 and 1: the prices under the awning; row 2: the items sold over it
            int y = row == 2 ? SOLD_Y : PRICE_Y + row * SLOT;
            addSlot(new GhostSlot(offers, i, SLOTS_X + column * SLOT, y, () -> true, () -> false));
        }
        PlayerSlots.add(this::addSlot, playerInventory, INVENTORY_X, INVENTORY_Y);
        updateStock();
    }

    /**
     * Opens the stall of the Shop Cartridge at {@code ref} for {@code player}, if allowed there; {@link #BACK} then
     * reopens what was open (a tile's interface, its slot selected again, or the cartridge's own menu).
     *
     * @return true if opened
     */
    public static boolean open(ServerPlayerEntity player, CartridgeRef ref) {
        ItemStack stack = ref.resolve(player);
        if (!(stack.getItem() instanceof ShopCartridgeItem) || !ref.inReach(player) || !ref.mayEdit(player)) return false;
        Runnable back = backTo(player, ref);
        Text title = stack.getName();
        player.openHandledScreen(new ExtendedScreenHandlerFactory<CartridgeRef>() {
            @Override
            public CartridgeRef getScreenOpeningData(ServerPlayerEntity serverPlayer) {
                return ref;
            }

            @Override
            public Text getDisplayName() {
                return title;
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity playerEntity) {
                ShopStallScreenHandler handler = new ShopStallScreenHandler(syncId, playerInventory, ref);
                handler.back = back;
                return handler;
            }
        });
        player.getWorld().playSound(null, player.getBlockPos(), ModSounds.OPEN_TILE_GUI_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /** What {@link #BACK} reopens: the tile's interface open now (its slot selected again), else the cartridge's menu. */
    private static Runnable backTo(ServerPlayerEntity player, CartridgeRef ref) {
        if (player.currentScreenHandler instanceof BoardSpaceScreenHandler tile && tile.boardSpace() != null) {
            BoardSpaceBlockEntity space = tile.boardSpace();
            return () -> {
                if (space.isRemoved() || !ref.inReach(player)) {
                    player.closeHandledScreen();
                    return;
                }
                player.openHandledScreen(space);
                if (player.currentScreenHandler instanceof BoardSpaceScreenHandler reopened) reopened.onButtonClick(player, ref.index());
                player.getWorld().playSound(null, player.getBlockPos(), ModSounds.OPEN_TILE_GUI_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
            };
        }
        return () -> {
            if (!CartridgeMenus.open(player, ref)) player.closeHandledScreen();
        };
    }

    public CartridgeRef ref() {
        return ref;
    }

    /** Offer {@code column}'s stock, as the server last sent it ({@link ShopStock}). */
    public int stock(int column) {
        return stock.get(column);
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id != BACK || back == null) return false;
        back.run();
        return true;
    }

    @Override
    public void sendContentUpdates() {
        updateStock();
        super.sendContentUpdates();
    }

    private void updateStock() {
        stock.update(ref.resolve(player), player.getWorld(), ref.pos().orElse(null));
    }

    /** The cartridge is still where it was (the same stack: not dropped, split nor taken out) and in reach. */
    @Override
    public boolean canUse(PlayerEntity player) {
        if (holder.isEmpty() || holder.getItem() != holderItem || !ref.inReach(player)) return false;
        ItemStack now = ref.resolve(player);
        if (now == holder) return true;
        // In a hand: still in the inventory (e.g. the hotbar slot selected changed with a number key)
        if (ref.inHand()) {
            PlayerInventory inventory = player.getInventory();
            for (int i = 0; i < inventory.size(); i++) if (inventory.getStack(i) == holder) return true;
        }
        return false;
    }

    private static boolean isOffer(int slotIndex) {
        return slotIndex >= 0 && slotIndex < ShopCartridgeItem.SLOTS;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (isOffer(slotIndex)) {
            if (!ref.mayEdit(player)) return;
            GhostSlot.click((GhostSlot) slots.get(slotIndex), button, actionType, player, getCursorStack(), this::setCursorStack);
            return;
        }
        // The edited cartridge itself can't be moved while its stall is open
        if (slotIndex >= 0 && slotIndex < slots.size() && slots.get(slotIndex).getStack() == holder) return;
        if (actionType == SlotActionType.SWAP && ref.inHand() && player.getInventory().getStack(button) == holder) return;
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public void handleGhostScroll(PlayerEntity player, int slotIndex, int direction) {
        if (!canUse(player) || !isOffer(slotIndex) || direction == 0 || !ref.mayEdit(player)) return;
        ((GhostSlot) slots.get(slotIndex)).onScroll(direction);
    }

    @Override
    public boolean canInsertIntoSlot(Slot slot) {
        return !(slot instanceof GhostSlot) && super.canInsertIntoSlot(slot);
    }

    @Override
    public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
        return !(slot instanceof GhostSlot) && super.canInsertIntoSlot(stack, slot);
    }

    /** Shift-click: nothing moves (the offers are copies, the player's items stay where they are). */
    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (!player.getWorld().isClient) {
            player.getWorld().playSound(null, player.getBlockPos(), ModSounds.CLOSE_TILE_GUI_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
    }
}
