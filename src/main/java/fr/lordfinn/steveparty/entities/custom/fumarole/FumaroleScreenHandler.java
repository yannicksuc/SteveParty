package fr.lordfinn.steveparty.entities.custom.fumarole;

import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
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

/** A tamed Fumarole's screen, like a horse's: its saddle slot, then the player's inventory. */
public class FumaroleScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final @Nullable FumaroleEntity fumarole;

    /** Client side: opened for the Fumarole with this id. */
    public FumaroleScreenHandler(int syncId, PlayerInventory playerInventory, Integer entityId) {
        this(syncId, playerInventory, inventoryOf(playerInventory.player.getWorld().getEntityById(entityId)),
                playerInventory.player.getWorld().getEntityById(entityId) instanceof FumaroleEntity f ? f : null);
    }

    private static Inventory inventoryOf(@Nullable Entity entity) {
        return entity instanceof FumaroleEntity fumarole ? fumarole.inventory : new SimpleInventory(1);
    }

    public FumaroleScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, @Nullable FumaroleEntity fumarole) {
        super(ModScreensHandlers.FUMAROLE_SCREEN_HANDLER, syncId);
        this.inventory = inventory;
        this.fumarole = fumarole;
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
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
    }

    public @Nullable FumaroleEntity fumarole() {
        return fumarole;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return fumarole == null || (fumarole.isAlive() && fumarole.squaredDistanceTo(player) < 64);
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
