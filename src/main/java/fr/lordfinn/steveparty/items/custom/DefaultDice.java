package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import static fr.lordfinn.steveparty.entities.ModEntities.DICE_ENTITY;

public class DefaultDice extends Item {
    public DefaultDice(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        if (!world.isClient && world instanceof ServerWorld) {
            // Calculate position in front of the player
            Vec3d playerPos = player.getPos();
            Vec3d lookVec = player.getRotationVec(1.0F).multiply(2); // Multiplies by 2 for distance
            Vec3d spawnPos = playerPos.add(lookVec);
            DiceEntity diceEntity = DICE_ENTITY.create(world, SpawnReason.TRIGGERED);
            if (diceEntity != null) {
                diceEntity.setPosition(spawnPos.x, spawnPos.y + 0.5, spawnPos.z);
                diceEntity.setNoGravity(true);
                diceEntity.setOwner(player.getUuid());
                world.spawnEntity(diceEntity);
                Vec3d velocity = lookVec.multiply(0.6);
                diceEntity.setVelocity(velocity);
                diceEntity.findTarget(player.isSneaking() ? PlayerEntity.class : MobEntity.class);
                diceEntity.setRolling(true);
                world.playSound(
                        null,
                        diceEntity.getBlockPos(),
                        SoundEvents.ENTITY_BREEZE_SHOOT,
                        SoundCategory.AMBIENT,
                        0.2F,
                        1.5F
                );

                world.playSound(
                        null,
                        diceEntity.getBlockPos(),
                        SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,
                        SoundCategory.AMBIENT,
                        0.4F,
                        1F
                );
            }
        }

        return ActionResult.SUCCESS;
    }
}

