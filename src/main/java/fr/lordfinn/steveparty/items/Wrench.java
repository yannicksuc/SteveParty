package fr.lordfinn.steveparty.items;

import fr.lordfinn.steveparty.blocks.tiles.Tile;
import fr.lordfinn.steveparty.blocks.tiles.TileDestination;
import fr.lordfinn.steveparty.blocks.tiles.TileEntity;
import fr.lordfinn.steveparty.blocks.tiles.TileService;
import fr.lordfinn.steveparty.particles.ParticleUtils;
import fr.lordfinn.steveparty.payloads.ArrowParticlesPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.awt.*;
import java.util.List;

import static fr.lordfinn.steveparty.components.ModComponents.BLOCK_POS;

public class Wrench extends Item implements TileOpener {
    private long lastTimeItemHoldParticleUpdate = 0;

    public Wrench(Settings settings) {
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
            toggleWrenchState((TileEntity) blockEntity, player, context.getStack());
        } else {
            unbindWrench(player, context.getStack());
        }
        return ActionResult.SUCCESS;
    }

    private void toggleWrenchState(TileEntity blockEntity, PlayerEntity player, ItemStack stack) {
        BlockPos boundTile = stack.get(BLOCK_POS);
        if (boundTile != null && boundTile.equals(blockEntity.getPos())) {
            unbindWrench(player, stack);
        } else {
            bindWrench(blockEntity.getPos(), player, stack);
        }
    }

    private void unbindWrench(PlayerEntity player, ItemStack stack) {
        stack.remove(BLOCK_POS);
        sendMessageToPlayer("Wrench not bound anymore", player);
    }

    private void bindWrench(BlockPos pos, PlayerEntity player, ItemStack stack) {
        stack.set(BLOCK_POS, pos);
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
        //Steveparty.LOGGER.info("TICK");
        if (!selected || !(entity instanceof ServerPlayerEntity player) || !(stack.get(BLOCK_POS) instanceof BlockPos blockPos))
            return;
        TileEntity boundTile = Tile.getTileEntity(world, blockPos);
        //Steveparty.LOGGER.info("Bound tile: {} lastTimeItemHoldParticleUpdate: {} currentTimeMillis: {}", boundTile, lastTimeItemHoldParticleUpdate, System.currentTimeMillis());
        if (boundTile == null || lastTimeItemHoldParticleUpdate + 1000 > System.currentTimeMillis())
            return;
        lastTimeItemHoldParticleUpdate = System.currentTimeMillis();
        List<TileDestination> tileDestinations = TileService.getCurrentDestinations(boundTile, 0);
        if (!tileDestinations.isEmpty()) {
            for (TileDestination destination : tileDestinations) {
                // Summon the particle at each position for the player
                BlockPos offset = destination.position().subtract(boundTile.getPos());
                Vector3f normalizedOffset = new Vector3f(offset.getX(), offset.getY(), offset.getZ()).normalize().mul(1.5f);

                Color color = destination.isTile() ? Color.GREEN : Color.RED;

                Vec3d encodedVelocity = ParticleUtils.encodeVelocity(
                        color,
                        offset.getX() - (normalizedOffset.x() * 2),
                        offset.getY() - (normalizedOffset.y() * 2),
                        offset.getZ() - (normalizedOffset.z() * 2));
                ServerPlayNetworking.send(player, new ArrowParticlesPayload(new Vec3d(boundTile.getPos().getX() + 0.5 + normalizedOffset.x(),
                        boundTile.getPos().getY() + 0.6 + normalizedOffset.y(),
                        boundTile.getPos().getZ() + 0.5 + normalizedOffset.z()), encodedVelocity));
            }
        }
        super.inventoryTick(stack, world, entity, slot, true);
    }
}
