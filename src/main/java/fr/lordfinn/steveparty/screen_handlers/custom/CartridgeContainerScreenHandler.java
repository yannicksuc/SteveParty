package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopStock;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeLayout;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenuHost;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenus;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeRef;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.GhostSlotsModule;
import fr.lordfinn.steveparty.screen_handlers.PlayerSlots;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The interface of a block holding cartridges: its cartridge slots, then the player's. A block whose cartridge is set
 * up in place (a board space, a Piggy Bank, a Looting Box) also shows the menu of its selected cartridge on the right
 * ({@link #addCartridgeMenu}, see {@link CartridgeMenuHost}): the edits go to the cartridge in the block's slot, on the
 * server, and come back with the slot's sync. The Inventory Cartridge's ghost slots are real slots there, after the
 * player's.
 */
public abstract class CartridgeContainerScreenHandler extends ScreenHandler implements CartridgeMenuHost, GhostSlotHost {
    /** The block's part of the interface (its texture's width); the cartridge's menu starts after it, with a gap. */
    public static final int BODY_W = 176, MENU_GAP = 4, MENU_X = BODY_W + MENU_GAP;

    protected Inventory inventory = null;

    /** The block whose cartridge's menu is shown, null without a menu. */
    private @Nullable BlockPos menuPos;
    private @Nullable World menuWorld;
    /** Where the selected cartridge keeps its items ({@link CartridgeContainers.Storage}), sent by the server. */
    private @Nullable Property storage;
    /** The selected Shop Cartridge's stock, per offer, sent by the server. */
    private @Nullable ShopStock.Synced shopStock;
    private int ghostStart = -1;

    public CartridgeContainerScreenHandler(ScreenHandlerType<?> type, int syncId) {
        super(type, syncId);
    }

    protected void setupScreen() {
    }

    public CartridgeContainerScreenHandler(ScreenHandlerType<?> type, int syncId, Inventory inventory) {
        super(type, syncId);
        this.inventory = inventory;
    }

    void init(PlayerInventory playerInventory, int playerInventoryTitleY) {
        if (inventory != null)
            inventory.onOpen(playerInventory.player);
        setupScreen();
        PlayerSlots.add(this::addSlot, playerInventory, 8, playerInventoryTitleY);
    }

    // ---------------------------------------------------------------- the cartridge's menu

    /** The block at {@code inventory}'s place, null if it is not a block (the client's copy of the slots). */
    protected static @Nullable BlockPos posOf(@Nullable Inventory inventory) {
        return inventory instanceof BlockEntity blockEntity ? blockEntity.getPos() : null;
    }

    /**
     * Shows the selected cartridge's menu beside the block's part (after {@link #init}, on both sides alike): its
     * storage and stock properties, and the ghost slots after the player's (the cartridge slots and the player's keep
     * their indexes for shift-clicks). {@code pos}: the block (sent to the client with the opening data).
     */
    protected void addCartridgeMenu(PlayerEntity player, @Nullable BlockPos pos) {
        menuPos = pos == null ? null : pos.toImmutable();
        menuWorld = player.getWorld();
        storage = Property.create();
        addProperty(storage);
        shopStock = new ShopStock.Synced(this::addProperty);
        updateMenuState();
        // The Inventory Cartridge's first module: at the top left of its menu
        GhostSlotsModule module = (GhostSlotsModule) ((CartridgeItem) ModItems.INVENTORY_CARTRIDGE).modules().getFirst();
        Inventory ghosts = player.getWorld().isClient || menuPos == null ? new SimpleInventory(GhostSlotsModule.COUNT)
                : new CartridgeGhostInventory(this::selectedStack, () -> {
                    CartridgeRef ref = editedCartridge(player);
                    if (ref != null) ref.commit(player.getWorld());
                });
        ghostStart = slots.size();
        for (int i = 0; i < GhostSlotsModule.COUNT; i++) {
            int index = i;
            // Shown for a cartridge with ghost slots (Inventory, Trichaudron): all at the same place, its first module
            addSlot(new GhostSlot(ghosts, i, MENU_X + CartridgeLayout.PAD_X + module.slotX(i), CartridgeLayout.TOP + module.slotY(i),
                    () -> index < GhostSlotsModule.countOf(selectedStack()), () -> {
                        GhostSlotsModule ghostsOf = GhostSlotsModule.of(selectedStack());
                        return ghostsOf == null || ghostsOf.signed();
                    }));
        }
    }

    /** Whether the selected cartridge's menu is shown beside the block's part. */
    public boolean hasCartridgeMenu() {
        return storage != null;
    }

    /** The block whose cartridge's menu is shown (null without a menu). */
    public @Nullable BlockPos menuPos() {
        return menuPos;
    }

    /** The slot whose cartridge's menu is shown (the only one by default). */
    public int getSelectedSlot() {
        return 0;
    }

    public ItemStack selectedStack() {
        return inventory == null || getSelectedSlot() >= inventory.size() ? ItemStack.EMPTY : inventory.getStack(getSelectedSlot());
    }

    @Override
    public @Nullable CartridgeRef editedCartridge(PlayerEntity player) {
        if (menuPos == null || CartridgeMenus.cartridge(selectedStack()) == null) return null;
        return CartridgeRef.slot(menuPos, getSelectedSlot());
    }

    @Override
    public CartridgeContainers.Storage storage() {
        return storage == null ? CartridgeContainers.Storage.NONE : CartridgeContainers.Storage.byId(storage.get());
    }

    @Override
    public int shopStock(int column) {
        return shopStock == null ? ShopStock.NO_OFFER : shopStock.get(column);
    }

    @Override
    public void sendContentUpdates() {
        updateMenuState();
        super.sendContentUpdates();
    }

    /**
     * The selected cartridge's storage and a shop's stock, before the properties are sent (a Party Controller is known
     * on the server only).
     */
    private void updateMenuState() {
        if (storage == null || shopStock == null || menuPos == null || menuWorld == null || menuWorld.isClient) return;
        ItemStack stack = selectedStack();
        if (CartridgeContainers.linksContainers(stack))
            storage.set(CartridgeContainers.storageOf(stack, menuWorld, menuPos).ordinal());
        shopStock.update(stack, menuWorld, menuPos);
    }

    /** Whether slot {@code index} holds a real item: the block's slots and the player's, not the ghost slots. */
    public boolean isItemSlot(int index) {
        return index >= 0 && index < realSlotsEnd();
    }

    private boolean isGhost(int slotIndex) {
        return ghostStart >= 0 && slotIndex >= ghostStart && slotIndex < ghostStart + GhostSlotsModule.COUNT;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (isGhost(slotIndex)) {
            CartridgeRef ref = editedCartridge(player);
            if (ref == null || !slots.get(slotIndex).isEnabled() || !ref.mayEdit(player)) return;
            GhostSlot.click((GhostSlot) slots.get(slotIndex), button, actionType, player, getCursorStack(), this::setCursorStack);
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public void handleGhostEdit(PlayerEntity player, int slotIndex, GhostSlot.Edit edit, int amount) {
        CartridgeRef ref = editedCartridge(player);
        if (!canUse(player) || !isGhost(slotIndex) || ref == null || !ref.mayEdit(player)
                || !slots.get(slotIndex).isEnabled()) return;
        ((GhostSlot) slots.get(slotIndex)).edit(edit, amount);
    }

    @Override
    public boolean canInsertIntoSlot(Slot slot) {
        return !(slot instanceof GhostSlot) && super.canInsertIntoSlot(slot);
    }

    @Override
    public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
        return !(slot instanceof GhostSlot) && super.canInsertIntoSlot(stack, slot);
    }

    // ---------------------------------------------------------------- the slots

    @Override
    public boolean canUse(PlayerEntity player) {
        return ScreenHandlerChecks.canUseInventory(this.inventory, player);
    }

    // Shift + Player Inv Slot
    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        if (isGhost(invSlot)) return ItemStack.EMPTY;
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);
        if (slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();
            if (invSlot < this.inventory.size()) {
                if (!this.insertItem(originalStack, this.inventory.size(), realSlotsEnd(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.insertItem(originalStack, 0, this.inventory.size(), false)) {
                return ItemStack.EMPTY;
            }

            if (originalStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }

        return newStack;
    }

    /** The end of the slots shift-clicks move items between (the cartridge slots, then the player's): never the ghost slots. */
    protected int realSlotsEnd() {
        return ghostStart >= 0 ? ghostStart : this.slots.size();
    }

    /** The number of cartridge slots (they come first). */
    public int getInventorySize() {
        return inventory == null ? 0 : inventory.size();
    }

    public boolean isSingle() {
        return inventory.size() == 1;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        player.getWorld().playSound(null, player.getBlockPos(), ModSounds.CLOSE_TILE_GUI_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }
}
