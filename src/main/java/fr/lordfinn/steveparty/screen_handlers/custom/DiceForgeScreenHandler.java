package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.jetbrains.annotations.Nullable;

import static fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity.*;
import static fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers.DICE_FORGE_SCREEN_HANDLER;

/**
 * Dice forge GUI. Handler slot indices match the forge inventory: 0..11 faces, 12 center (gravity core input,
 * hidden once the forge is activated: the screen draws the core there as the FORGE button), 13..16 star fragments
 * (NW, NE, SE, SW), 17 blank faces (left of the core), 18 output (right of the core), then the player inventory.
 * The FORGE button uses the vanilla button click packet (syncId + canUse, i.e. same forge open and in reach).
 */
public class DiceForgeScreenHandler extends ScreenHandler {
    public static final int BUTTON_TOGGLE = 0;
    /** Position (GUI coordinates) of the center slot (the core) and of the slots around it. */
    public static final int CENTER_X = 80, CENTER_Y = 63;
    /** Star fragments on the 4 diagonals of the core (NW, NE, SE, SW). */
    public static final int[][] FRAGMENT_POSITIONS = {
            {CENTER_X - 19, CENTER_Y - 19}, {CENTER_X + 19, CENTER_Y - 19},
            {CENTER_X + 19, CENTER_Y + 19}, {CENTER_X - 19, CENTER_Y + 19}
    };
    public static final int[][] FACE_POSITIONS = {
            {80, 9},   {54, 19},  {106, 19},
            {36, 37},  {124, 37}, {26, 63},
            {134, 63}, {36, 89},  {124, 89},
            {54, 107}, {106, 107},{80, 117}
    };
    /** Blank faces go in on the left of the core, the forged die comes out on its right. */
    public static final int BLANK_X = CENTER_X - 24, BLANK_Y = CENTER_Y;
    public static final int OUTPUT_X = CENTER_X + 24, OUTPUT_Y = CENTER_Y;
    private static final int PLAYER_INVENTORY_START = SIZE;

    private final Inventory inventory;
    private final PropertyDelegate properties;

    public DiceForgeScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties) {
        super(DICE_FORGE_SCREEN_HANDLER, syncId);
        checkSize(inventory, SIZE);
        checkDataCount(properties, PROPERTY_COUNT);
        this.inventory = inventory;
        this.properties = properties;
        inventory.onOpen(playerInventory.player);

        // --- 12 face slots ---
        for (int i = 0; i < FACE_SLOTS; i++) {
            this.addSlot(new ForgeSlot(inventory, i, FACE_POSITIONS[i][0], FACE_POSITIONS[i][1]));
        }

        // --- Center slot: gravity core input until the forge is activated (then the FORGE button) ---
        this.addSlot(new ForgeSlot(inventory, CENTER_SLOT, CENTER_X, CENTER_Y) {
            @Override
            public int getMaxItemCount(ItemStack stack) {
                return isGravityCore(stack) ? 1 : super.getMaxItemCount(stack);
            }

            @Override
            public boolean isEnabled() {
                return !isActivated();
            }
        });

        // --- 4 star fragment slots around the core ---
        for (int i = 0; i < FRAGMENT_SLOTS; i++) {
            this.addSlot(new ForgeSlot(inventory, FIRST_FRAGMENT_SLOT + i, FRAGMENT_POSITIONS[i][0], FRAGMENT_POSITIONS[i][1]));
        }

        // --- Blank faces (consumed) and output (take only) ---
        this.addSlot(new ForgeSlot(inventory, BLANK_SLOT, BLANK_X, BLANK_Y));
        this.addSlot(new Slot(inventory, OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return false;
            }
        });

        // --- Player inventory ---
        // Matches the slot cells painted in the texture (rows at y = 143, 161, 179, hotbar at 201)
        addPlayerSlots(playerInventory, 8, 143);
        addProperties(properties);
    }

    /** Client constructor. */
    public DiceForgeScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(SIZE), new ArrayPropertyDelegate(PROPERTY_COUNT));
    }

    private class ForgeSlot extends Slot {
        ForgeSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return isValidForSlot(inventory, getIndex(), stack, isActivated());
        }
    }

    private void addPlayerSlots(PlayerInventory playerInventory, int left, int top) {
        // Player inventory (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        left + col * 18, top + row * 18));
            }
        }

        // Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col,
                    left + col * 18, top + 58));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return ScreenHandlerChecks.canUseInventory(this.inventory, player);
    }

    /** Server side: the vanilla packet handler already checked the syncId and {@link #canUse}. */
    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id != BUTTON_TOGGLE || !canUse(player)) return false;
        if (inventory instanceof DiceForgeBlockEntity forge) {
            forge.toggleProduction();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack stackInSlot = slot.getStack();
            itemStack = stackInSlot.copy();

            if (index < PLAYER_INVENTORY_START) {
                // Forge → player
                if (!this.insertItem(stackInSlot, PLAYER_INVENTORY_START, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (isGravityCore(stackInSlot) && !isActivated()) {
                if (!this.insertItem(stackInSlot, CENTER_SLOT, CENTER_SLOT + 1, false)) return ItemStack.EMPTY;
            } else if (isBlankFace(stackInSlot)) {
                // Blank faces go to the blank faces slot; only when it is full do they go to the ring (as a face)
                boolean moved = insertPreferringGhosts(stackInSlot, BLANK_SLOT, BLANK_SLOT + 1);
                if (!moved && !insertPreferringGhosts(stackInSlot, 0, FACE_SLOTS)) return ItemStack.EMPTY;
            } else if (DiceFace.isFace(stackInSlot)) {
                if (!insertPreferringGhosts(stackInSlot, 0, FACE_SLOTS)) return ItemStack.EMPTY;
            } else if (isStarFragment(stackInSlot)) {
                if (!insertPreferringGhosts(stackInSlot, FIRST_FRAGMENT_SLOT, FIRST_FRAGMENT_SLOT + FRAGMENT_SLOTS)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY;
            }

            if (stackInSlot.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
            if (stackInSlot.getCount() == itemStack.getCount()) return ItemStack.EMPTY;
        }

        return itemStack;
    }

    /** Shift-click: fill matching stacks, then empty slots whose ghost is this item, then any empty slot. */
    private boolean insertPreferringGhosts(ItemStack stack, int start, int end) {
        int before = stack.getCount();
        // 1. merge with identical stacks
        for (int i = start; i < end && !stack.isEmpty(); i++) {
            Slot slot = this.slots.get(i);
            ItemStack current = slot.getStack();
            if (!current.isEmpty() && ItemStack.areItemsAndComponentsEqual(current, stack)) {
                int max = Math.min(slot.getMaxItemCount(current), current.getMaxCount());
                int moved = Math.min(stack.getCount(), max - current.getCount());
                if (moved > 0) {
                    current.increment(moved);
                    stack.decrement(moved);
                    slot.markDirty();
                }
            }
        }
        // 2. empty slots remembering this item, 3. any empty slot
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int i = start; i < end && !stack.isEmpty(); i++) {
                Slot slot = this.slots.get(i);
                if (slot.hasStack() || !slot.canInsert(stack)) continue;
                Item ghost = getGhost(i);
                if (pass == 0 && ghost != stack.getItem()) continue;
                if (pass == 1 && ghost != null && ghost != stack.getItem()) continue; // keep other ghosts free
                slot.setStack(stack.split(Math.min(stack.getCount(), slot.getMaxItemCount(stack))));
                slot.markDirty();
            }
        }
        // Last resort: slots with a different ghost
        for (int i = start; i < end && !stack.isEmpty(); i++) {
            Slot slot = this.slots.get(i);
            if (slot.hasStack() || !slot.canInsert(stack)) continue;
            slot.setStack(stack.split(Math.min(stack.getCount(), slot.getMaxItemCount(stack))));
            slot.markDirty();
        }
        return stack.getCount() != before;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        this.inventory.onClose(player);
    }

    public Inventory getInventory() {
        return this.inventory;
    }

    // ---- synced state (valid on both sides)

    public int getFlags() {
        return properties.get(PROP_FLAGS);
    }

    public boolean isActivated() {
        return (getFlags() & FLAG_ACTIVATED) != 0;
    }

    public boolean isRunning() {
        return (getFlags() & FLAG_RUNNING) != 0;
    }

    public boolean isBlocked() {
        return (getFlags() & FLAG_BLOCKED) != 0;
    }

    public boolean isPowered() {
        return (getFlags() & FLAG_POWERED) != 0;
    }

    public DiceForgeBlockEntity.Status getStatus() {
        return DiceForgeBlockEntity.Status.byId(properties.get(PROP_STATUS));
    }

    /** @return craft progress in [0, 1] */
    public float getProgress() {
        int total = properties.get(PROP_CRAFT_TIME);
        return total <= 0 ? 0f : Math.min(1f, (float) properties.get(PROP_PROGRESS) / total);
    }

    /** @return the item remembered for this forge slot (ghost), or null. */
    public @Nullable Item getGhost(int slot) {
        int layoutIndex = layoutIndex(slot);
        if (layoutIndex < 0) return null;
        int rawId = properties.get(PROP_FIRST_GHOST + layoutIndex);
        if (rawId <= 0) return null;
        Item item = Registries.ITEM.get(rawId);
        return item == Items.AIR ? null : item;
    }
}
