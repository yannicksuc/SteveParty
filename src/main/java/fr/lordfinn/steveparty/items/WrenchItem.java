package fr.lordfinn.steveparty.items;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.TileDestination;
import fr.lordfinn.steveparty.blocks.TileEntity;
import fr.lordfinn.steveparty.blocks.TileService;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TileBehaviorComponent;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.util.List;

import static fr.lordfinn.steveparty.components.TileBehaviorComponent.DEFAULT_TILE_BEHAVIOR;
import static fr.lordfinn.steveparty.particles.ModParticles.ARROW_PARTICLE;
import static fr.lordfinn.steveparty.particles.ModParticles.HERE_PARTICLE;

public class WrenchItem extends Item {
    private long lastTimeItemHoldParticleUpdate = 0;
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

        if (player == null)
            return ActionResult.PASS;

        if (blockEntity instanceof TileEntity) {
            toggleWrenchState((TileEntity) blockEntity, player);
        }
        return ActionResult.SUCCESS;
    }

    private void toggleWrenchState(TileEntity blockEntity, PlayerEntity player) {
        if (boundTile == blockEntity) {
            handleUnboundState(player);
        } else {
            bindWrenchToTile(blockEntity, player.getBlockPos(), player);
        }
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

    private void sendMessageToPlayer(String message, PlayerEntity player) {
        player.sendMessage(Text.of(message), false);
    }

    public void resetBoundTile() {
        this.boundTile = null;
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!selected || boundTile == null)
            return;
        if (!(entity instanceof LivingEntity user))
            return;
        if (lastTimeItemHoldParticleUpdate + 1000 > System.currentTimeMillis())
            return;
        lastTimeItemHoldParticleUpdate = System.currentTimeMillis();
        List<TileDestination> tileDestinations = TileService.getCurrentDestinations(boundTile, 0);
        if (!tileDestinations.isEmpty()) {
            for (TileDestination destination : tileDestinations) {
                // Summon the particle at each position for the player
                BlockPos offset = destination.position().subtract(boundTile.getPos());
                Vector3f normalizedOffset = new Vector3f(offset.getX(), offset.getY(), offset.getZ()).normalize().mul(1.5f);


                world.addParticle(ARROW_PARTICLE,
                        boundTile.getPos().getX() + 0.5 + normalizedOffset.x(),
                        boundTile.getPos().getY() + 0.3 + normalizedOffset.y(),
                        boundTile.getPos().getZ() + 0.5 + normalizedOffset.z(),
                        offset.getX() - (normalizedOffset.x() * 2),
                        offset.getY() - (normalizedOffset.y() * 2),
                        offset.getZ() - (normalizedOffset.z()) * 2);
            }
        }
    }
}
