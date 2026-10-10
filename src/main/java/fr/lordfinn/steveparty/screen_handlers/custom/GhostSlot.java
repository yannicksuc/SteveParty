package fr.lordfinn.steveparty.screen_handlers.custom;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static fr.lordfinn.steveparty.components.ModComponents.IS_NEGATIVE;

/**
 * A « ghost » slot of the Inventory Cartridge: it holds a copy of an item with a quantity set with the mouse wheel,
 * given (positive) or taken (negative, {@link fr.lordfinn.steveparty.components.ModComponents#IS_NEGATIVE}), and never
 * consumes nor hands out a real item.
 */
public class GhostSlot extends Slot {
    public static final int MAX_COUNT = 1028;
    private final BooleanSupplier enabled;
    /** Whether its item may be taken (negative) as well as given: otherwise the wheel stops at 1. */
    private final BooleanSupplier signed;

    public GhostSlot(Inventory inventory, int index, int x, int y, BooleanSupplier enabled) {
        this(inventory, index, x, y, enabled, () -> true);
    }

    public GhostSlot(Inventory inventory, int index, int x, int y, BooleanSupplier enabled, BooleanSupplier signed) {
        super(inventory, index, x, y);
        this.enabled = enabled;
        this.signed = signed;
    }

    @Override
    public boolean isEnabled() {
        return enabled.getAsBoolean();
    }

    /** Sets a ghost copy (count 1) of {@code stack}, or clears the slot if it is empty. Never consumes it. */
    public void setGhostStack(ItemStack stack) {
        this.setStack(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    @Override
    public Optional<ItemStack> tryTakeStackRange(int min, int max, PlayerEntity player) {
        this.setStack(ItemStack.EMPTY);
        return Optional.empty();
    }

    @Override
    public ItemStack takeStackRange(int min, int max, PlayerEntity player) {
        this.setStack(ItemStack.EMPTY);
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack takeStack(int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertStack(ItemStack stack, int count) {
        if (!stack.isEmpty()) this.setStack(stack.copyWithCount(1));
        return stack;
    }

    @Override
    public boolean canInsert(ItemStack stack) {
        return true;
    }

    @Override
    public boolean canTakeItems(PlayerEntity playerEntity) {
        return false;
    }

    @Override
    public boolean canTakePartial(PlayerEntity player) {
        return false;
    }

    @Override
    public int getMaxItemCount(ItemStack stack) {
        return MAX_COUNT;
    }

    public static boolean isPositive(ItemStack stack) {
        return !stack.contains(IS_NEGATIVE) || Boolean.FALSE.equals(stack.get(IS_NEGATIVE));
    }

    /**
     * One more / one less of the item: going under 1 turns a given item into a taken one (and back), on a slot that
     * may take; on one that only gives, it stops at 1.
     */
    public void onScroll(double amount) {
        ItemStack stack = this.getStack();
        if (stack.isEmpty()) return;
        boolean positive = isPositive(stack);
        int change = amount > 0 ? 1 : -1;
        if (!positive) change = -change;
        int count = stack.getCount() + change;
        if (count <= 0 && !signed.getAsBoolean()) return;
        if (count <= 0) stack.set(IS_NEGATIVE, positive);
        if (count > 0 && count <= MAX_COUNT) stack.setCount(count);
        markDirty();
    }

    /**
     * A click on a ghost slot (from {@link net.minecraft.screen.ScreenHandler#onSlotClick}): no real item is ever
     * created or consumed.
     */
    public static void click(GhostSlot slot, int button, SlotActionType actionType, PlayerEntity player, ItemStack cursor,
                             Consumer<ItemStack> setCursor) {
        switch (actionType) {
            // Item on cursor → a ghost copy; empty cursor → clears the slot
            case PICKUP -> slot.setGhostStack(cursor);
            // Hotbar / off hand key → a ghost copy of that stack (or clears), nothing is moved
            case SWAP -> {
                if (button >= 0 && button < player.getInventory().size()) slot.setGhostStack(player.getInventory().getStack(button));
            }
            case THROW -> {
                if (cursor.isEmpty()) slot.setGhostStack(ItemStack.EMPTY);
            }
            case CLONE -> {
                if (player.isInCreativeMode() && cursor.isEmpty() && slot.hasStack()) {
                    ItemStack clone = slot.getStack().copyWithCount(slot.getStack().getMaxCount());
                    clone.remove(IS_NEGATIVE);
                    setCursor.accept(clone);
                }
            }
            default -> {
                // Quick move, drag, pick-up-all: nothing (ghost items are not real)
            }
        }
    }
}
