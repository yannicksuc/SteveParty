package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeLayout;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenuHost;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenus;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeRef;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.GhostSlotsModule;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundCategory;

import java.util.List;

/**
 * A cartridge's menu on its own (the cartridge in hand, or in a block's slot, see
 * {@link fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenus}): its shell with its modules and, only
 * when a module needs it (the Inventory Cartridge's ghost slots), the player's inventory under it.
 * <p>
 * The layout is computed from the cartridge's modules on both sides, so the slots are at the same place.
 */
public class CartridgeScreenHandler extends ScreenHandler implements CartridgeMenuHost, GhostSlotHost {
    private final CartridgeRef ref;
    /** The cartridge when the screen opened: the screen closes when it is gone (dropped, taken out of the block). */
    private final ItemStack holder;
    private final Item holderItem;
    private final List<CartridgeModule> modules;
    private final CartridgeLayout layout;
    private final boolean withInventory;
    private final int backgroundWidth, backgroundHeight, shellX;
    private int ghostStart = -1;

    public CartridgeScreenHandler(int syncId, PlayerInventory playerInventory, CartridgeRef ref) {
        super(ModScreensHandlers.CARTRIDGE_SCREEN_HANDLER, syncId);
        this.ref = ref;
        PlayerEntity player = playerInventory.player;
        this.holder = ref.resolve(player);
        this.holderItem = holder.getItem();
        this.modules = CartridgeMenus.modules(holder);
        int ghost = CartridgeLayout.indexOf(modules, GhostSlotsModule.class);
        this.withInventory = ghost >= 0;
        this.layout = CartridgeLayout.of(modules, withInventory ? CartridgeLayout.MAX_CONTENT_WITH_INVENTORY : CartridgeLayout.MAX_CONTENT_ALONE);
        this.backgroundWidth = Math.max(layout.width(), withInventory ? CartridgeLayout.INVENTORY_W : 0);
        this.backgroundHeight = layout.height() + (withInventory ? CartridgeLayout.INVENTORY_GAP + CartridgeLayout.INVENTORY_H : 0);
        this.shellX = (backgroundWidth - layout.width()) / 2;

        if (withInventory) {
            GhostSlotsModule module = (GhostSlotsModule) modules.get(ghost);
            Inventory ghosts = player.getWorld().isClient ? new SimpleInventory(GhostSlotsModule.COUNT)
                    : new CartridgeGhostInventory(() -> ref.resolve(player), () -> ref.commit(player.getWorld()));
            ghostStart = slots.size();
            for (int i = 0; i < GhostSlotsModule.COUNT; i++) {
                addSlot(new GhostSlot(ghosts, i, shellX + layout.x(ghost) + module.slotX(i), layout.y(ghost) + module.slotY(i), () -> true));
            }
            int invX = (backgroundWidth - CartridgeLayout.INVENTORY_W) / 2;
            int invY = layout.height() + CartridgeLayout.INVENTORY_GAP;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    addSlot(new Slot(playerInventory, col + row * 9 + 9, invX + 8 + col * 18, invY + 17 + row * 18));
                }
            }
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col, invX + 8 + col * 18, invY + 75));
            }
        }
    }

    public CartridgeRef ref() {
        return ref;
    }

    public List<CartridgeModule> modules() {
        return modules;
    }

    public CartridgeLayout layout() {
        return layout;
    }

    public boolean withInventory() {
        return withInventory;
    }

    public int backgroundWidth() {
        return backgroundWidth;
    }

    public int backgroundHeight() {
        return backgroundHeight;
    }

    /** The shell's left edge in the screen (it is centred over the inventory). */
    public int shellX() {
        return shellX;
    }

    @Override
    public CartridgeRef editedCartridge(PlayerEntity player) {
        return ref;
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

    private boolean isGhost(int slotIndex) {
        return ghostStart >= 0 && slotIndex >= ghostStart && slotIndex < ghostStart + GhostSlotsModule.COUNT;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (isGhost(slotIndex)) {
            if (!ref.mayEdit(player)) return;
            GhostSlot.click((GhostSlot) slots.get(slotIndex), button, actionType, player, getCursorStack(), this::setCursorStack);
            return;
        }
        // The edited cartridge itself can't be moved while its menu is open
        if (slotIndex >= 0 && slotIndex < slots.size() && slots.get(slotIndex).getStack() == holder) return;
        if (actionType == SlotActionType.SWAP && ref.inHand() && player.getInventory().getStack(button) == holder) return;
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public void handleGhostScroll(PlayerEntity player, int slotIndex, int direction) {
        if (!canUse(player) || !isGhost(slotIndex) || direction == 0 || !ref.mayEdit(player)) return;
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
