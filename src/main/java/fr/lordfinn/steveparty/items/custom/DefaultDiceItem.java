package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import static fr.lordfinn.steveparty.entities.ModEntities.DICE_ENTITY;

public class DefaultDiceItem extends Item {

    protected static final float VELOCITY_MULTIPLIER = 0.6F;
    private static final float SOUND_VOLUME_1 = 0.2F;
    private static final float SOUND_PITCH_1 = 1.5F;
    private static final float SOUND_VOLUME_2 = 0.4F;
    private static final float SOUND_PITCH_2 = 1F;

    public DefaultDiceItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        if (isServerWorld(world)) {
            Vec3d spawnPosition = calculateSpawnPosition(player);
            DiceEntity diceEntity = spawnDiceEntity(world, spawnPosition);
            if (diceEntity != null) {
                configureDiceEntity(diceEntity, player, hand);
                playSounds(world, diceEntity);
                decrementDiceInHand(player, hand);
            }
        }
        return ActionResult.SUCCESS;
    }

    protected void decrementDiceInHand(PlayerEntity player, Hand hand) {
        player.getStackInHand(hand).decrement(1);
    }

    protected boolean isServerWorld(World world) {
        return !world.isClient && world instanceof ServerWorld;
    }

    protected Vec3d calculateSpawnPosition(PlayerEntity player) {
        Vec3d playerPos = player.getPos();
        Vec3d lookVec = player.getRotationVec(1.0F).multiply(2);
        return playerPos.add(lookVec);
    }

    protected DiceEntity spawnDiceEntity(World world, Vec3d spawnPosition) {
        DiceEntity diceEntity = DICE_ENTITY.create(world, SpawnReason.TRIGGERED);
        if (diceEntity != null) {
            diceEntity.setPosition(spawnPosition.x, spawnPosition.y + 0.5, spawnPosition.z);
            diceEntity.setNoGravity(true);
            world.spawnEntity(diceEntity);
        }
        return diceEntity;
    }

    protected void configureDiceEntity(DiceEntity diceEntity, PlayerEntity player, Hand hand) {
        Vec3d velocity = player.getRotationVec(1.0F).multiply(VELOCITY_MULTIPLIER);
        diceEntity.setVelocity(velocity);
        diceEntity.setOwner(player.getUuid());
        diceEntity.findTarget(player.isSneaking() ? PlayerEntity.class : MobEntity.class);
        diceEntity.setRolling(true);
        diceEntity.setItemReference(getDiceItem(diceEntity, player, hand));
    }

    private ItemStack getDiceItem(DiceEntity diceEntity, PlayerEntity player, Hand hand) {
        return player.getStackInHand(hand).copyWithCount(1);
    }

    protected void playSounds(World world, DiceEntity diceEntity) {
        world.playSound(null, diceEntity.getBlockPos(), SoundEvents.ENTITY_BREEZE_SHOOT, SoundCategory.AMBIENT, SOUND_VOLUME_1, SOUND_PITCH_1);
        world.playSound(null, diceEntity.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.AMBIENT, SOUND_VOLUME_2, SOUND_PITCH_2);
    }

    @Override
    public boolean canBeEnchantedWith(ItemStack stack, RegistryEntry<Enchantment> enchantment, EnchantingContext context) {
        if (enchantment.matchesKey(net.minecraft.enchantment.Enchantments.INFINITY)) {
            return true;
        }
        return super.canBeEnchantedWith(stack, enchantment, context);
    }
}
