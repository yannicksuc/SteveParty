package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeLayout;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenuHost;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenus;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeRef;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.GhostSlotsModule;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.Property;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;

/**
 * A board space's interface: its cartridge slot(s) (16 in an Advanced Tile), the player's inventory and, on the right,
 * the menu of the selected slot's cartridge (see {@link CartridgeMenuHost}). The selected slot is the active one when
 * the interface opens; a right click on another slot of an Advanced Tile selects it ({@link #onButtonClick}).
 * The Inventory Cartridge's ghost slots are real slots here too, shown while such a cartridge is selected.
 */
public class BoardSpaceScreenHandler extends CartridgeContainerScreenHandler implements CartridgeMenuHost, GhostSlotHost {
    /** The tile part's width; the cartridge's menu starts after it, with a gap. */
    public static final int TILE_W = 176, MENU_GAP = 4, MENU_X = TILE_W + MENU_GAP;

    private final Property selected = Property.create();
    private final BoardSpaceBlockEntity boardSpace;
    private int ghostStart = -1;

    //Main constructor (Called on the server and the client)
    public BoardSpaceScreenHandler(int syncId, PlayerInventory playerInventory, BoardSpaceBlockEntity blockEntity) {
        super(ModScreensHandlers.TILE_SCREEN_HANDLER, syncId);
        this.inventory = blockEntity;
        this.boardSpace = blockEntity;
        init(playerInventory, 101);
        selected.set(blockEntity == null ? 0 : Math.clamp(blockEntity.getActiveSlot(), 0, Math.max(0, blockEntity.size() - 1)));
        addProperty(selected);
        addGhostSlots(playerInventory.player);
    }

    //Client constructor
    public BoardSpaceScreenHandler(int syncId, PlayerInventory playerInventory, BlockPosPayload blockPosPayload) {
        this(syncId, playerInventory, (BoardSpaceBlockEntity) playerInventory.player.getWorld().getBlockEntity(blockPosPayload.pos()));
    }

    @Override
    public void setupScreen() {
        int m, l;
        if (this.inventory.size() == 16) {
            for (m = 0; m < 4; ++m) {
                for (l = 0; l < 4; ++l) {
                    this.addSlot(new CartridgeCustomSlot(this.inventory, l + m * 4, 53 + l * 18, 12 + m * 18));
                }
            }
        }

        if (this.inventory.size() == 1) {
            this.addSlot(new CartridgeCustomSlot(this.inventory, 0, 80, 39));
        }
    }

    /**
     * The Inventory Cartridge's ghost slots, after the player's slots (the cartridge slots and the player's keep their
     * indexes for shift-clicks): its ghost module comes first in its menu, so they are always at the same place.
     */
    private void addGhostSlots(PlayerEntity player) {
        if (boardSpace == null) return;
        // The Inventory Cartridge's first module: at the top left of its menu
        GhostSlotsModule module = (GhostSlotsModule) ((CartridgeItem) ModItems.INVENTORY_CARTRIDGE).modules().getFirst();
        Inventory ghosts = player.getWorld().isClient ? new SimpleInventory(GhostSlotsModule.COUNT)
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

    /** Whether slot {@code index} holds a real item: the tile's slots and the player's, not the ghost slots. */
    public boolean isItemSlot(int index) {
        return index >= 0 && index < (ghostStart < 0 ? slots.size() : ghostStart);
    }

    public int getActiveSlot() {
        if (inventory == null) return -1;
        return ((BoardSpaceBlockEntity)inventory).getActiveSlot();
    }

    /** The slot whose cartridge's menu is shown. */
    public int getSelectedSlot() {
        return Math.clamp(selected.get(), 0, Math.max(0, inventory.size() - 1));
    }

    public ItemStack selectedStack() {
        return inventory == null ? ItemStack.EMPTY : inventory.getStack(getSelectedSlot());
    }

    public @Nullable BoardSpaceBlockEntity boardSpace() {
        return boardSpace;
    }

    /** Button {@code id}: selects slot {@code id} (a right click on it). */
    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id < 0 || id >= inventory.size()) return false;
        selected.set(id);
        return true;
    }

    @Override
    public @Nullable CartridgeRef editedCartridge(PlayerEntity player) {
        if (boardSpace == null || CartridgeMenus.cartridge(selectedStack()) == null) return null;
        return CartridgeRef.slot(boardSpace.getPos(), getSelectedSlot());
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
    public void handleGhostScroll(PlayerEntity player, int slotIndex, int direction) {
        CartridgeRef ref = editedCartridge(player);
        if (!canUse(player) || !isGhost(slotIndex) || direction == 0 || ref == null || !ref.mayEdit(player)
                || !slots.get(slotIndex).isEnabled()) return;
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

    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        if (isGhost(invSlot)) return ItemStack.EMPTY;
        return super.quickMove(player, invSlot);
    }

    /** Shift-clicks never reach the ghost slots (they would take the real item). */
    @Override
    protected int realSlotsEnd() {
        return ghostStart >= 0 ? ghostStart : slots.size();
    }
}
