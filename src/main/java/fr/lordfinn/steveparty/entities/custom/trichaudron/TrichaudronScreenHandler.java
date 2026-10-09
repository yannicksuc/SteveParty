package fr.lordfinn.steveparty.entities.custom.trichaudron;

import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import fr.lordfinn.steveparty.screen_handlers.PlayerSlots;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.jetbrains.annotations.Nullable;

/** A tamed Trichaudron's screen, like a horse's: its saddle slot, then the player's inventory. */
public class TrichaudronScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final @Nullable TrichaudronEntity trichaudron;

    /** Client side: opened for the Trichaudron with this id. */
    public TrichaudronScreenHandler(int syncId, PlayerInventory playerInventory, Integer entityId) {
        this(syncId, playerInventory, inventoryOf(playerInventory.player.getWorld().getEntityById(entityId)),
                playerInventory.player.getWorld().getEntityById(entityId) instanceof TrichaudronEntity f ? f : null);
    }

    private static Inventory inventoryOf(@Nullable Entity entity) {
        return entity instanceof TrichaudronEntity trichaudron ? trichaudron.inventory : new SimpleInventory(1);
    }

    public TrichaudronScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, @Nullable TrichaudronEntity trichaudron) {
        super(ModScreensHandlers.TRICHAUDRON_SCREEN_HANDLER, syncId);
        this.inventory = inventory;
        this.trichaudron = trichaudron;
        addSlot(new Slot(inventory, 0, 8, 18) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.isOf(Items.SADDLE);
            }

            @Override
            public int getMaxItemCount() {
                return 1;
            }
        });
        PlayerSlots.add(this::addSlot, playerInventory, 8, 84);
    }

    public @Nullable TrichaudronEntity trichaudron() {
        return trichaudron;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return trichaudron == null || (trichaudron.isAlive() && trichaudron.squaredDistanceTo(player) < 64);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!insertItem(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.isOf(Items.SADDLE) && !slots.get(0).hasStack()) {
            if (!insertItem(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
        else slot.markDirty();
        return copy;
    }
}
