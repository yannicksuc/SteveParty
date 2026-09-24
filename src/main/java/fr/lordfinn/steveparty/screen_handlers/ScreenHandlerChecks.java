package fr.lordfinn.steveparty.screen_handlers;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;

/**
 * Shared server-side validity checks for screen handlers and C2S payloads.
 */
public final class ScreenHandlerChecks {
    /** Extra reach tolerance, same as vanilla block interactions. */
    public static final double REACH_TOLERANCE = 1.0;

    private ScreenHandlerChecks() {}

    /** @return true if the block entity still exists at its position and the player is within reach. */
    public static boolean canUseBlockEntity(BlockEntity blockEntity, PlayerEntity player) {
        if (blockEntity == null || blockEntity.isRemoved() || blockEntity.getWorld() == null) return false;
        if (blockEntity.getWorld() != player.getWorld()) return false;
        return Inventory.canPlayerUse(blockEntity, player);
    }

    /**
     * Block-backed inventories are checked for removal and distance; other inventories keep their own rule.
     */
    public static boolean canUseInventory(Inventory inventory, PlayerEntity player) {
        if (inventory == null) return false;
        if (inventory instanceof BlockEntity blockEntity && !canUseBlockEntity(blockEntity, player)) return false;
        return inventory.canPlayerUse(player);
    }

    /** @return true if the player can interact with the block at {@code pos} (reach check). */
    public static boolean isInReach(PlayerEntity player, BlockPos pos) {
        return player.canInteractWithBlockAt(pos, REACH_TOLERANCE);
    }
}
