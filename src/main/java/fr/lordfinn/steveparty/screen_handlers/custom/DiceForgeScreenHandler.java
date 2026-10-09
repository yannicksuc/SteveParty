package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.dice.DiceModules;
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
 * Dice forge GUI. Handler slot indices match the forge inventory: 0..11 faces, 12 center (the gravity core: put in to
 * activate the forge, taken back like any item to put it to sleep, see {@link CoreSlot}), 13..17 star fragments
 * (clockwise from the top), 18 blank faces and 19 output (in the capsule under the galaxy), 20..24 dice modules (the
 * gems on the five points of the star, clockwise from the top), then the player inventory.
 * <p>
 * The positions below are the resting ones (galaxy at angle 0): the screen turns the face and fragment slots with the
 * galaxy, client side only.
 * The FORGE button uses the vanilla button click packet (syncId + canUse, i.e. same forge open and in reach).
 */
public class DiceForgeScreenHandler extends ScreenHandler {
    public static final int BUTTON_TOGGLE = 0;
    /** Center of the galaxy (GUI coordinates), and the center slot (the core) on it. */
    public static final int GALAXY_X = 118, GALAXY_Y = 126;
    public static final int CENTER_X = GALAXY_X - 8, CENTER_Y = GALAXY_Y - 8;
    /** Inner ring, 28 px from the core, one fragment every 72 degrees from the top. */
    public static final int FRAGMENT_RADIUS = 28;
    public static final int[][] FRAGMENT_POSITIONS = {{110, 90}, {137, 109}, {126, 141}, {94, 141}, {83, 109}};
    /** Outer ring, 54 px from the core, one face every 30 degrees. */
    public static final int[][] FACE_POSITIONS = {
            {110, 64}, {83, 71},   {137, 71},
            {63, 91},  {157, 91},  {56, 118},
            {164, 118}, {63, 145}, {157, 145},
            {83, 165}, {137, 165}, {110, 172}
    };
    /** The capsule under the galaxy: blank faces on its left, the forged die on its right. */
    public static final int BLANK_X = 95, BLANK_Y = 202;
    public static final int OUTPUT_X = 125, OUTPUT_Y = 202;
    /** The module slots: one gem on each point of the star, 90 px from the core, clockwise from the top. */
    public static final int[][] MODULE_POSITIONS = {{110, 28}, {196, 90}, {163, 191}, {57, 191}, {24, 90}};
    /** The player inventory panel: its slots start 8 and 7 px in, the hotbar 4 px under the rows. */
    public static final int INVENTORY_X = 30, INVENTORY_Y = 230;
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

        // --- Center slot: the gravity core ---
        this.addSlot(new CoreSlot(inventory));

        // --- 5 star fragment slots around the core ---
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

        // --- Dice modules (not consumed; a slot holds as many of a module as a die may carry) ---
        for (int i = 0; i < MODULE_SLOTS; i++) {
            this.addSlot(new ForgeSlot(inventory, FIRST_MODULE_SLOT + i, MODULE_POSITIONS[i][0], MODULE_POSITIONS[i][1]) {
                @Override
                public int getMaxItemCount(ItemStack stack) {
                    return Math.max(1, maxModuleCount(stack));
                }
            });
        }

        // --- Player inventory ---
        // Matches the slot cells painted in the texture (rows at y = 237, 255, 273, hotbar at 295)
        addPlayerSlots(playerInventory, INVENTORY_X + 8, INVENTORY_Y + 7);
        addProperties(properties);
    }

    /** Client constructor. */
    public DiceForgeScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(SIZE), new ArrayPropertyDelegate(PROPERTY_COUNT));
    }

    /**
     * The center slot. The core put in it goes into the forge itself (the forge inventory keeps it empty, see
     * {@link DiceForgeBlockEntity#setStack}): while the forge is activated the slot shows that core, and taking it
     * (click, shift-click, number keys, drop) takes it out of the forge ({@link DiceForgeBlockEntity#takeCore}), which
     * goes back to sleep. The core is a single item: it is never both in the forge and on the cursor.
     */
    private class CoreSlot extends ForgeSlot {
        CoreSlot(Inventory inventory) {
            super(inventory, CENTER_SLOT, CENTER_X, CENTER_Y);
        }

        @Override
        public int getMaxItemCount(ItemStack stack) {
            return isGravityCore(stack) ? 1 : super.getMaxItemCount(stack);
        }

        @Override
        public ItemStack getStack() {
            // A new stack each time: what reads it may change its count, the core itself stays in the forge
            return isActivated() ? new ItemStack(ModBlocks.GRAVITY_CORE) : super.getStack();
        }

        @Override
        public boolean canTakeItems(PlayerEntity player) {
            if (!isActivated()) return super.canTakeItems(player);
            // Server: not during the insertion animation (the client predicts, the server corrects)
            return !(inventory instanceof DiceForgeBlockEntity forge) || forge.canRemoveCore();
        }

        @Override
        public ItemStack takeStack(int amount) {
            if (!isActivated()) return super.takeStack(amount);
            if (amount <= 0) return ItemStack.EMPTY;
            return inventory instanceof DiceForgeBlockEntity forge ? forge.takeCore() : getStack();
        }

        @Override
        public void setStack(ItemStack stack) {
            // Emptied while activated (the core went straight to a hotbar slot, or shift-clicked to the inventory)
            if (isActivated() && stack.isEmpty()) {
                if (inventory instanceof DiceForgeBlockEntity forge) forge.takeCore();
                return;
            }
            super.setStack(stack);
        }
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

            // The core is taken only when it can leave the forge (not while it is being inserted)
            if (index == CENTER_SLOT && !slot.canTakeItems(player)) return ItemStack.EMPTY;
            if (index < PLAYER_INVENTORY_START) {
                // Forge → player
                if (!this.insertItem(stackInSlot, PLAYER_INVENTORY_START, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (isGravityCore(stackInSlot) && !isActivated()) {
                if (!this.insertItem(stackInSlot, CENTER_SLOT, CENTER_SLOT + 1, false)) return ItemStack.EMPTY;
            } else if (isBlankFace(stackInSlot)) {
                // Blank faces go to their slot only (put one on the ring by hand to make it a blank side)
                if (!insertPreferringGhosts(stackInSlot, BLANK_SLOT, BLANK_SLOT + 1)) return ItemStack.EMPTY;
            } else if (DiceFace.isFace(stackInSlot)) {
                if (!insertPreferringGhosts(stackInSlot, 0, FACE_SLOTS)) return ItemStack.EMPTY;
            } else if (DiceModules.isModuleItem(stackInSlot)) {
                if (!insertPreferringGhosts(stackInSlot, FIRST_MODULE_SLOT, FIRST_MODULE_SLOT + MODULE_SLOTS)) return ItemStack.EMPTY;
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
