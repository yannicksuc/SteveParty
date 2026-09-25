package fr.lordfinn.steveparty.persistent_state;

import fr.lordfinn.steveparty.blocks.custom.CashRegisterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Protection of the blocks of an owned shop (a shop whose trader was claimed by a player with a Shopkeeper Key,
 * see {@link VendorLinkPersistentState#getOwner(UUID)}): its trading stalls, cash registers and the containers
 * linked to it as stock.
 * <ul>
 *     <li>breaking: only the owner, or an operator in creative mode;</li>
 *     <li>opening a stock container: only the owner, a creative player or an operator (same bypass as the shop
 *     GUIs, see {@code ShopkeeperKeyItem#canOpenShopBlock}); stalls and registers keep their own GUI rule;</li>
 *     <li>explosions don't destroy them, hoppers and hopper minecarts don't pull from them (mixins).</li>
 * </ul>
 * Blocks of shops without owner (trader never claimed, or not linked at all) keep the vanilla behaviour.
 */
public final class ShopProtection {
    private ShopProtection() {
    }

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient || canBreak(player, world, pos)) return true;
            sendDenied(player);
            return false;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient || player.isSpectator()) return ActionResult.PASS;
            // Sneaking with an item: the block is not used (the item is: placing a block, linking a key...)
            boolean holdsItem = !player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty();
            if (player.shouldCancelInteraction() && holdsItem) return ActionResult.PASS;
            BlockPos pos = hitResult.getBlockPos();
            BlockEntity blockEntity = world.getBlockEntity(pos);
            // Stalls and registers have their own access rule (their onUse)
            if (!(blockEntity instanceof Inventory) || isShopGuiBlock(blockEntity)) return ActionResult.PASS;
            if (canOpenContainer(player, world, pos)) return ActionResult.PASS;
            if (hand == Hand.MAIN_HAND) sendDenied(player);
            return ActionResult.FAIL;
        });
    }

    private static boolean isShopGuiBlock(BlockEntity blockEntity) {
        return blockEntity instanceof TradingStallBlockEntity || blockEntity instanceof CashRegisterBlockEntity;
    }

    /** Action-bar message sent to a player denied the breaking or the opening of a protected shop block. */
    public static void sendDenied(PlayerEntity player) {
        player.sendMessage(Text.translatableWithFallback("message.steveparty.shop.protected_block",
                "This block belongs to another player's shop."), true);
    }

    /**
     * Owners of the owned traders the block at this position is linked to (persistent key links, plus the runtime
     * links of a trading stall). Empty if the block belongs to no owned shop.
     */
    public static Set<UUID> getShopOwners(World world, BlockPos pos) {
        Set<UUID> owners = new HashSet<>();
        if (world.isClient || world.getServer() == null) return owners;
        VendorLinkPersistentState state = VendorLinkPersistentState.get(world.getServer());
        if (state == null) return owners;
        Set<UUID> traders = new HashSet<>(state.getVendorsLinkedTo(GlobalPos.create(world.getRegistryKey(), pos)));
        if (world.getBlockEntity(pos) instanceof TradingStallBlockEntity) {
            traders.addAll(TraderStallRegistry.getLinkedTraders(pos));
        }
        for (UUID trader : traders) {
            UUID owner = state.getOwner(trader);
            if (owner != null) owners.add(owner);
        }
        return owners;
    }

    /** @return true if the block at this position belongs to an owned shop (stall, register or stock container). */
    public static boolean isProtected(World world, BlockPos pos) {
        if (world.isClient || !world.getBlockState(pos).hasBlockEntity()) return false;
        return !getShopOwners(world, pos).isEmpty();
    }

    /**
     * Like {@link #isProtected(World, BlockPos)}, for the whole container opened at this position: the two halves
     * of a double chest share one inventory, so a double chest is protected if either half is.
     */
    public static boolean isContainerProtected(World world, BlockPos pos) {
        if (isProtected(world, pos)) return true;
        BlockPos other = getOtherChestHalf(world.getBlockState(pos), pos);
        return other != null && isProtected(world, other);
    }

    private static BlockPos getOtherChestHalf(BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof ChestBlock) || !state.contains(ChestBlock.CHEST_TYPE)
                || state.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) {
            return null;
        }
        return pos.offset(ChestBlock.getFacing(state));
    }

    private static boolean isOwner(PlayerEntity player, World world, BlockPos pos) {
        return getShopOwners(world, pos).contains(player.getUuid());
    }

    /** Breaking rule: the owner of the shop, or an operator in creative mode. Unowned blocks: anyone. */
    public static boolean canBreak(PlayerEntity player, World world, BlockPos pos) {
        if (!isProtected(world, pos)) return true;
        if (player.isCreative() && player.hasPermissionLevel(2)) return true;
        return isOwner(player, world, pos);
    }

    /**
     * Opening rule of a stock container: the owner of the shop, a creative player or an operator (same bypass as
     * the shop GUIs). For a double chest, the player must be allowed on each protected half.
     */
    public static boolean canOpenContainer(PlayerEntity player, World world, BlockPos pos) {
        if (player.isCreative() || player.hasPermissionLevel(2)) return true;
        if (isProtected(world, pos) && !isOwner(player, world, pos)) return false;
        BlockPos other = getOtherChestHalf(world.getBlockState(pos), pos);
        return other == null || !isProtected(world, other) || isOwner(player, world, other);
    }
}
