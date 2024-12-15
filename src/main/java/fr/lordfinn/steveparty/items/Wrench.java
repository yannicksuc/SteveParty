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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static fr.lordfinn.steveparty.components.ModComponents.BLOCK_POS;
import static fr.lordfinn.steveparty.service.TokenMovementService.displayDestinations;

public class Wrench extends Item implements TileOpener {
    private final List<UUID> holders = new ArrayList<>();

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
        holders.remove(player.getUuid());
        sendMessageToPlayer("Wrench not bound anymore", player);
    }

    private void bindWrench(BlockPos pos, PlayerEntity player, ItemStack stack) {
        stack.set(BLOCK_POS, pos);
        holders.remove(player.getUuid());
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
        TileEntity boundTile = Tile.getTileEntity(world, blockPos);
        if (boundTile == null || holders.contains(player.getUuid()))
            return;
        holders.add(player.getUuid());
        List<TileDestination> tileDestinations = TileEntity.getCurrentDestinations(boundTile);
        displayDestinations(tileDestinations, blockPos, List.of(player), world, () -> player.isHolding(this) && holders.contains(player.getUuid()), () -> holders.remove(player.getUuid()));
        super.inventoryTick(stack, world, entity, slot, true);
    }
}
