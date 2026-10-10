package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.utils.InventoryChain;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a party's things come from and go to: the one door every consumer goes through (the mini-game gains, a star
 * bought, a Shop space, a Trichaudron, an Inventory space without a chest of its own, the Coin and Debt faces, the
 * Double Coins power-up, a Common pot's base, a Mistigri's fines...). Which source it is gets decided in one place,
 * {@link #of(PartyControllerEntity)}: the real bank ({@link PartyBank}: the Party Controller's own inventory, then its
 * linked chests) or, the controller's « Infinite bank » on, a bank that never runs out (every request served, every
 * deposit absorbed). No consumer looks at the mode itself. The containers of a cartridge of their own keep their
 * priority over it ({@link #of(List)}: they really give and take).
 * <p>
 * Items are matched by item and components; the count of a template is ignored.
 */
public interface PartyResources {
    /** No source at all (outside a party): nothing to take, nothing goes in. */
    PartyResources NONE = new PartyResources() {
        @Override
        public int available(ItemStack template) {
            return 0;
        }

        @Override
        public int take(ItemStack template, int count) {
            return 0;
        }

        @Override
        public int give(ItemStack stack) {
            return 0;
        }

        @Override
        public List<ItemStack> contents() {
            return List.of();
        }

        @Override
        public boolean isNone() {
            return true;
        }
    };

    /** A bank that never runs out: everything asked is there, everything given is absorbed. */
    PartyResources INFINITE = new PartyResources() {
        @Override
        public int available(ItemStack template) {
            return template.isEmpty() ? 0 : Integer.MAX_VALUE;
        }

        @Override
        public int take(ItemStack template, int count) {
            return template.isEmpty() ? 0 : Math.max(0, count);
        }

        @Override
        public int give(ItemStack stack) {
            int count = stack.getCount();
            stack.setCount(0);
            return count;
        }

        @Override
        public List<ItemStack> contents() {
            return List.of();
        }

        @Override
        public boolean isUnlimited() {
            return true;
        }
    };

    /** How many items matching {@code template} it can give now ({@link Integer#MAX_VALUE}: no end). */
    int available(ItemStack template);

    /** Whether it can give {@code count} items matching {@code template} now. */
    default boolean canTake(ItemStack template, int count) {
        return count <= 0 || available(template) >= count;
    }

    /** Takes up to {@code count} items matching {@code template} out of it. @return how many came out */
    int take(ItemStack template, int count);

    /** Puts as much of {@code stack} as it takes in; {@code stack} is decremented by that. @return how many went in */
    int give(ItemStack stack);

    /** One stack of each kind of item it holds, as many as it holds, in order (a bank without end lists nothing). */
    List<ItemStack> contents();

    /** For the screens only (the dashboard, the tile info): a bank without end. */
    default boolean isUnlimited() {
        return false;
    }

    /** For the screens only: no source at all. */
    default boolean isNone() {
        return false;
    }

    // ---------------------------------------------------------------- the one place a source is chosen

    /** The party's source: its bank, or the bank without end when the controller says so; {@link #NONE} for no party. */
    static PartyResources of(@Nullable PartyControllerEntity controller) {
        if (controller == null) return NONE;
        if (controller.isInfiniteBank()) return INFINITE;
        return new Containers(PartyBank.places(controller));
    }

    /** Real containers (a cartridge's own, in their order): taken from the first ones first, filled the first first. */
    static PartyResources of(List<Inventory> containers) {
        return containers.isEmpty() ? NONE : new Containers(containers);
    }

    /**
     * {@code stack} (emptied) goes into the party's source; what does not fit falls by its Party Controller: nothing is
     * ever lost. Nothing happens outside a party ({@code controller} null: {@code stack} is left as it is).
     */
    static void deposit(@Nullable PartyControllerEntity controller, ItemStack stack) {
        if (controller == null || stack.isEmpty()) return;
        of(controller).give(stack);
        if (stack.isEmpty() || controller.getWorld() == null) return;
        BlockPos pos = controller.getPos();
        ItemScatterer.spawn(controller.getWorld(), pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, stack.copyAndEmpty());
    }

    /** Real containers, in order. */
    final class Containers implements PartyResources {
        private final List<Inventory> containers;

        Containers(List<Inventory> containers) {
            this.containers = List.copyOf(containers);
        }

        /** The containers, end to end. */
        public Inventory inventory() {
            return containers.size() == 1 ? containers.getFirst() : new InventoryChain(containers);
        }

        @Override
        public int available(ItemStack template) {
            if (template.isEmpty()) return 0;
            int count = 0;
            for (Inventory inventory : containers) {
                for (int slot = 0; slot < inventory.size(); slot++) {
                    ItemStack stack = inventory.getStack(slot);
                    if (!stack.isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, template)) count += stack.getCount();
                }
            }
            return count;
        }

        @Override
        public int take(ItemStack template, int count) {
            if (template.isEmpty() || count <= 0) return 0;
            int taken = 0;
            for (Inventory inventory : containers) {
                boolean changed = false;
                for (int slot = 0; slot < inventory.size() && taken < count; slot++) {
                    ItemStack stack = inventory.getStack(slot);
                    if (stack.isEmpty() || !ItemStack.areItemsAndComponentsEqual(stack, template)) continue;
                    int part = Math.min(stack.getCount(), count - taken);
                    stack.decrement(part);
                    if (stack.isEmpty()) inventory.setStack(slot, ItemStack.EMPTY);
                    taken += part;
                    changed = true;
                }
                if (changed) inventory.markDirty();
                if (taken >= count) break;
            }
            return taken;
        }

        @Override
        public int give(ItemStack stack) {
            return CartridgeContainers.insertInOrder(stack, containers);
        }

        @Override
        public List<ItemStack> contents() {
            List<ItemStack> kinds = new ArrayList<>();
            for (Inventory inventory : containers) {
                for (int slot = 0; slot < inventory.size(); slot++) {
                    ItemStack stack = inventory.getStack(slot);
                    if (stack.isEmpty()) continue;
                    ItemStack known = kinds.stream().filter(kind -> ItemStack.areItemsAndComponentsEqual(kind, stack)).findFirst().orElse(null);
                    if (known != null) known.increment(stack.getCount());
                    else kinds.add(stack.copy());
                }
            }
            return kinds;
        }
    }
}
