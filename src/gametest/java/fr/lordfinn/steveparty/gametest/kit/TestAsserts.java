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
import net.minecraft.util.math.Vec3d;

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

    /** The player came out of the pipe whose mouth is the relative block {@code mouth}: off it, standing on top of it. */
    public static boolean cameOutAt(TestContext context, ServerPlayerEntity player, BlockPos mouth) {
        Vec3d at = context.getRelative(player.getPos());
        return !player.hasVehicle() && Math.abs(at.x - (mouth.getX() + 0.5)) < 1.2 && Math.abs(at.z - (mouth.getZ() + 0.5)) < 1.2
                && at.y >= mouth.getY() + 0.9;
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
