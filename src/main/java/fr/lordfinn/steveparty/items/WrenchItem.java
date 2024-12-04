package fr.lordfinn.steveparty.items;

import fr.lordfinn.steveparty.blocks.TileEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class WrenchItem extends Item {
    private TileEntity boundTile = null;

    public WrenchItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        PlayerEntity player = context.getPlayer();
        BlockEntity blockEntity = world.getBlockEntity(pos);
        sendMessageToPlayer("Used Wrench", player);

        if (player == null)
            return ActionResult.PASS;
        return ActionResult.SUCCESS;
    }

    private void handleUnboundState(PlayerEntity player) {
        if (boundTile != null) {
            resetBoundTile();
            sendMessageToPlayer("Wrench not bound anymore", player);
        }
    }

    private void bindWrenchToTile(TileEntity tileEntity, BlockPos pos, PlayerEntity player) {
        boundTile = tileEntity;
        sendMessageToPlayer("Wrench bound to tile at " + pos, player);
    }

    private void handleRegularClick(TileEntity currentTileEntity, BlockPos pos, PlayerEntity player) {
    }

    private void sendMessageToPlayer(String message, PlayerEntity player) {
        player.sendMessage(Text.of(message), false);
    }

    public void resetBoundTile() {
        this.boundTile = null;
    }
}
