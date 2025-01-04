package fr.lordfinn.steveparty.mixin;

import com.mojang.authlib.GameProfile;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.HidingTraderEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static fr.lordfinn.steveparty.blocks.ModBlocks.VILLAGER_BLOCK;
import static fr.lordfinn.steveparty.entities.ModEntities.HIDING_TRADER_ENTITY;

@Mixin(ServerPlayerEntity.class)
public abstract class PlayerFallMixin extends PlayerEntity {
    public PlayerFallMixin(World world, BlockPos pos, float yaw, GameProfile gameProfile) {
        super(world, pos, yaw, gameProfile);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    public void onTick(CallbackInfo info) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        // Vérifie si le joueur est en chute libre
        if (!player.isOnGround() && player.fallDistance >= 10) {
            BlockPos blockPos = player.getBlockPos().down();
            BlockState blockState = player.getWorld().getBlockState(blockPos);

            // Vérifie si le bloc est le bon type
            if (blockState.isOf(VILLAGER_BLOCK)) {
                World world = player.getWorld();
                BlockPos belowPos = blockPos.down();
                BlockState belowState = world.getBlockState(belowPos);

                if (!belowState.isFullCube(world, belowPos))
                    return;

                // Supprime le bloc actuel et celui en dessous
                world.breakBlock(blockPos, false);
                world.breakBlock(belowPos, false);

                // Invoque l'entité personnalisée
                HidingTraderEntity trader = new HidingTraderEntity(HIDING_TRADER_ENTITY, world);
                trader.refreshPositionAndAngles(belowPos.getX() + 0.5, belowPos.getY() + 1, belowPos.getZ() + 0.5, 0, 0);
                trader.setBlockState(belowState);

                double dx = player.getX() - trader.getX();
                double dz = player.getZ() - trader.getZ();
                float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                trader.setYaw(yaw);

                // Play Illusioner spell sound
                world.playSound(null, belowPos, SoundEvents.ENTITY_ILLUSIONER_PREPARE_MIRROR, SoundCategory.BLOCKS, 1.0F, 1.0F);

                // Spawn particles around the block
                ((ServerWorld)world).spawnParticles(ParticleTypes.ENCHANTED_HIT,
                        belowPos.getX() + 0.5,
                        belowPos.getY() + 1.5,
                        belowPos.getZ() + 0.5,
                        20,0.1d, 0.0d, 0.1d, 0.5);


                world.spawnEntity(trader);
                world.playSound(null, belowPos, SoundEvents.ENTITY_VILLAGER_CELEBRATE, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
        }
    }
}
