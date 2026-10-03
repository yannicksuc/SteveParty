package fr.lordfinn.steveparty.items.custom.cartridges.menu;

import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

/**
 * The containers of an Inventory Cartridge in their order, two per row (index, coordinates, red when it is not there:
 * gone or not loaded), each with a remove button and one to put it earlier in the order (the order matters: taken from and given to in this
 * order, the bank pays in this order). A change is sent as {@code index * OPS + op} and checked on the server.
 */
public final class ContainersModule extends CartridgeModule {
    public static final int ROW_H = 10;
    /** Two containers per row, then the row of the hint (what a click does). */
    public static final int PER_ROW = 2;
    public static final int OPS = 4;
    public static final int REMOVE = 0, UP = 1, DOWN = 2;

    public ContainersModule(String id, @org.jetbrains.annotations.Nullable String labelKey) {
        super(id, labelKey);
    }

    public static int action(int index, int op) {
        return index * OPS + op;
    }

    @Override
    public int height() {
        // Its most rows: the full list, two per row, and the hint
        return labelHeight() + (CartridgeContainers.MAX / PER_ROW + 1) * ROW_H;
    }

    @Override
    public boolean editable() {
        return true;
    }

    /** A value that changes with the list (a change is shown at once, without waiting for it). */
    @Override
    public int get(ItemStack stack) {
        return CartridgeContainers.of(stack, World.OVERWORLD).hashCode();
    }

    @Override
    public boolean accepts(ItemStack stack, int value) {
        if (value < 0) return false;
        int index = value / OPS, op = value % OPS;
        int size = CartridgeContainers.of(stack, World.OVERWORLD).size();
        if (index >= size) return false;
        return switch (op) {
            case REMOVE -> true;
            case UP -> index > 0;
            case DOWN -> index < size - 1;
            default -> false;
        };
    }

    @Override
    public void set(CartridgeEdit edit, int value) {
        RegistryKey<World> fallback = edit.holder() != null && edit.holder().getWorld() != null
                ? edit.holder().getWorld().getRegistryKey() : World.OVERWORLD;
        int index = value / OPS, op = value % OPS;
        switch (op) {
            case REMOVE -> CartridgeContainers.remove(edit.stack(), index, fallback);
            case UP -> CartridgeContainers.move(edit.stack(), index, true, fallback);
            case DOWN -> CartridgeContainers.move(edit.stack(), index, false, fallback);
            default -> {
            }
        }
    }
}
