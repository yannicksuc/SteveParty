package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpace;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import static fr.lordfinn.steveparty.components.ModComponents.BLOCK_POS;

public class Wrench extends Item implements TileOpener {

    public Wrench(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        PlayerEntity player = context.getPlayer();
        BlockEntity blockEntity = world.getBlockEntity(pos);

        if (player == null || world.isClient)
            return ActionResult.PASS;

        if (blockEntity instanceof BoardSpaceEntity tile) {
            boolean result = tile.toggleDestinations((ServerPlayerEntity) player);
            sendMessageToPlayer(result ? "Bound to tile at " + pos : "Unbound from tile at " + pos, player);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    private void unbindWrench(PlayerEntity player, ItemStack stack, BoardSpaceEntity tile) {

        tile.hideDestinations();
        sendMessageToPlayer("Wrench not bound anymore", player);
    }

    private void bindWrench(BlockPos pos, PlayerEntity player, ItemStack stack, BoardSpaceEntity tile) {
        tile.displayDestinations((ServerPlayerEntity) player);
        sendMessageToPlayer("Wrench bound to tile at " + pos, player);
    }

    private void sendMessageToPlayer(String message, PlayerEntity player) {
        if (player.getWorld().isClient)
            player.sendMessage(Text.of(message), false);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (entity.getWorld().isClient)
            return;
        if (!selected || !(entity instanceof ServerPlayerEntity player) || !(stack.get(BLOCK_POS) instanceof BlockPos blockPos))
            return;
        BoardSpaceEntity boundTile = BoardSpace.getTileEntity(world, blockPos);
        if (boundTile == null)
            return;
        super.inventoryTick(stack, world, entity, slot, true);
    }
}
//, () -> player.isHolding(this) && holders.contains(player.getUuid()), () -> holders.remove(player.getUuid())