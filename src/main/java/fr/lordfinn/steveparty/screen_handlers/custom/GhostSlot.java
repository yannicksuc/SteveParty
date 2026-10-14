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
 * A « ghost » slot of the Inventory Cartridge: it holds a copy of an item with a quantity ({@link #edit}: the − / +
 * buttons of the menu, or the mouse wheel), given (positive) or taken (negative,
 * {@link fr.lordfinn.steveparty.components.ModComponents#IS_NEGATIVE}), and never consumes nor hands out a real item.
 */
public class GhostSlot extends Slot {
    public static final int MAX_COUNT = 1028;
    private final BooleanSupplier enabled;
    /** Whether its item may be taken (negative) as well as given. */
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

    /** A change of a ghost slot's item: its quantity, or whether it is given or taken. */
    public enum Edit {
        /** {@code amount} more (less if negative), from one to a full stack of it, given or taken alike. */
        STEP,
        /** Given (green). */
        GIVE,
        /** Taken (red), on a slot that may take. */
        TAKE
    }

    /** Whether its item may be taken (red) as well as given. */
    public boolean signed() {
        return signed.getAsBoolean();
    }

    /** Applies {@code edit} to its item (nothing on an empty slot, nor {@link Edit#TAKE} on a slot that only gives). */
    public void edit(Edit edit, int amount) {
        ItemStack stack = this.getStack();
        if (stack.isEmpty()) return;
        switch (edit) {
            case STEP -> {
                int count = Math.clamp((long) Math.min(stack.getCount(), maxQuantity(stack)) + amount, 1, maxQuantity(stack));
                if (count == stack.getCount()) return;
                stack.setCount(count);
            }
            case GIVE -> {
                if (isPositive(stack)) return;
                stack.remove(IS_NEGATIVE);
            }
            case TAKE -> {
                if (!isPositive(stack) || !signed()) return;
                stack.set(IS_NEGATIVE, true);
            }
        }
        markDirty();
    }

    /** The most of {@code stack}'s item a ghost slot gives or takes: one full stack of it. */
    public static int maxQuantity(ItemStack stack) {
        return Math.clamp(stack.getMaxCount(), 1, MAX_COUNT);
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
