package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.jetbrains.annotations.Nullable;

import static fr.lordfinn.steveparty.entities.ModEntities.HIDING_TRADER_ENTITY;

public class VillagerBlock extends FallingBlock {
    public static final MapCodec<VillagerBlock> CODEC = Block.createCodec(VillagerBlock::new);

    public VillagerBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends FallingBlock> getCodec() {
        return CODEC;
    }

    // Helper method to send a message to the closest player within 10 blocks
    private void sendMessageToPlayer(World world, BlockPos pos, String translationKey) {
        if (world.isClient) return;
        ServerPlayerEntity closestPlayer = (ServerPlayerEntity) world.getClosestPlayer(pos.getX(), pos.getY(), pos.getZ(), 10, false);
        if (closestPlayer != null) {
            closestPlayer.sendMessage(Text.translatable(translationKey), false);
        }
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        super.onBreak(world, pos, state, player);
        sendMessageToPlayer(world, pos, "villagerblock.break");
        return state;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);

        // Handling water interaction
        if (world.getBlockState(pos.up()).isOf(Blocks.WATER)) {
            sendMessageToPlayer(world, pos, "villagerblock.underwater");
            world.playSound(null, pos, SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }

        // Handling fire interaction
        else if (world.getBlockState(pos.up()).isOf(Blocks.FIRE) || world.getBlockState(pos.down()).isOf(Blocks.FIRE)) {
            sendMessageToPlayer(world, pos, "villagerblock.onfire");
            world.playSound(null, pos, SoundEvents.ENTITY_VILLAGER_HURT, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }

        // Handling sky interaction
        else if (world.isAir(pos.down())) {
            sendMessageToPlayer(world, pos, "villagerblock.sky");
            world.playSound(null, pos, SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.BLOCKS, 1.0F, 1.0F);
        } else {
            sendMessageToPlayer(world, pos, "villagerblock.place");
        }
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, @Nullable WireOrientation wireOrientation, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, wireOrientation, notify);
        if (world.isClient) return;
        if (world.getBlockState(pos.north()).isOf(Blocks.GRASS_BLOCK) &&
                world.getBlockState(pos.south()).isOf(Blocks.GRASS_BLOCK) &&
                world.getBlockState(pos.east()).isOf(Blocks.GRASS_BLOCK) &&
                world.getBlockState(pos.west()).isOf(Blocks.GRASS_BLOCK)) {
            sendMessageToPlayer(world, pos, "villagerblock.grass");
        }

        if (world.getBlockState(pos.north()).isOf(Blocks.STONE) &&
                world.getBlockState(pos.south()).isOf(Blocks.STONE) &&
                world.getBlockState(pos.east()).isOf(Blocks.STONE) &&
                world.getBlockState(pos.west()).isOf(Blocks.STONE)) {
            sendMessageToPlayer(world, pos, "villagerblock.stone");
        }
    }

    @Override
    protected BlockSoundGroup getSoundGroup(BlockState state) {
        return new BlockSoundGroup(0.8f, 1, SoundEvents.ENTITY_VILLAGER_DEATH, SoundEvents.ENTITY_VILLAGER_AMBIENT, SoundEvents.ENTITY_VILLAGER_CELEBRATE, SoundEvents.ENTITY_VILLAGER_HURT, SoundEvents.ENTITY_VILLAGER_NO);
    }
}
