package fr.lordfinn.steveparty.gametest.kit;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/** Checks and counts shared by the tests. */
public final class TestAsserts {
    private TestAsserts() {
    }

    /** The token is on the board space at the relative position {@code at}. */
    public static void assertOn(TestContext context, MobEntity token, BlockPos at, String what) {
        BoardSpaceBlockEntity on = BoardSpaces.boardSpaceOf(token);
        context.assertTrue(on != null && on.getPos().equals(context.getAbsolutePos(at)),
                what + ": on " + (on == null ? "nothing" : on.getPos()) + ", expected " + context.getAbsolutePos(at));
    }

    /** How many items of exactly that kind (item and components) are in an inventory. */
    public static int count(Inventory inventory, ItemStack template) {
        return InventoryUtils.count(inventory, template);
    }

    /** How many items of exactly that kind (item and components) the player carries. */
    public static int count(ServerPlayerEntity player, ItemStack template) {
        return count(player.getInventory(), template);
    }

    /** How many plain {@code item}s (default components) the player carries. */
    public static int count(ServerPlayerEntity player, Item item) {
        return count(player, new ItemStack(item));
    }
}
