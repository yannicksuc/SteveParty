package fr.lordfinn.steveparty.items;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.TokenizedEntityInterface;
import fr.lordfinn.steveparty.components.MobEntityComponent;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

import static fr.lordfinn.steveparty.components.ModComponents.MOB_ENTITY_COMPONENT;
import static fr.lordfinn.steveparty.effect.ModEffects.SQUISHED;
import static net.minecraft.entity.effect.StatusEffects.LEVITATION;

public class TokenizerWand extends Item {

    public TokenizerWand(Settings settings) {
        super(settings);
    }

    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        MobEntityComponent test = stack.get(MOB_ENTITY_COMPONENT);
        if (test != null)
            tooltip.add(Text.of(String.format("TEST : %s", test.entityUUID())).copy().formatted(Formatting.GOLD));
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (entity instanceof MobEntity mob) {
            if (!((TokenizedEntityInterface) mob).steveparty$isTokenized()) {
                tokenizeEntity(mob);
            } else {
                String uuid = entity.getUuidAsString();
                user.getMainHandStack().set(MOB_ENTITY_COMPONENT, new MobEntityComponent(uuid));
                user.getWorld().playSound(null, entity.getBlockPos(), ModSounds.SELECT_SOUND_EVENT, SoundCategory.PLAYERS, 1.0F, 1.0F);
            }
            return ActionResult.SUCCESS;
        }
        return super.useOnEntity(stack, user, entity, hand);
    }

    private void tokenizeEntity(MobEntity mob) {
        if (!mob.getWorld().isClient) {
            ((TokenizedEntityInterface) mob).steveparty$setTokenized(true);
            mob.clearGoalsAndTasks();
            mob.setAiDisabled(true);
            mob.setInvulnerable(true);
            mob.setTarget(null);

            // Add effects
            mob.addStatusEffect(new StatusEffectInstance(SQUISHED, 130, 13)); //10 will be a height of 1 block
            mob.addStatusEffect(new StatusEffectInstance(LEVITATION, 130, 1));

            // Play sounds
            playSounds(mob);
        }

        // Client-side particle effect
        if (mob.getWorld().isClient) {
            spawnParticles(mob);
        }
    }

    private void playSounds(MobEntity mob) {
        mob.getWorld().playSound(null, mob.getBlockPos(),
                SoundEvent.of(Identifier.ofVanilla("entity.illusioner.cast_spell")),
                SoundCategory.PLAYERS, 1.0F, 1.0F);

        mob.getWorld().playSound(null, mob.getBlockPos(),
                SoundEvent.of(Identifier.ofVanilla("entity.zombie_villager.cure")),
                SoundCategory.PLAYERS, 0.2F, 2.0F);
    }

    private void spawnParticles(MobEntity mob) {
        for (int i = 0; i < 20; i++) {
            mob.getWorld().addImportantParticle(ParticleTypes.WAX_OFF, true,
                    mob.getX(), mob.getY() + mob.getHeight() / 2, mob.getZ(),
                    mob.getWorld().getRandom().nextDouble() - 0.5,
                    mob.getWorld().getRandom().nextDouble() - 0.5,
                    mob.getWorld().getRandom().nextDouble() - 0.5);
        }
    }


    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        handleWandClickOnBlock(context.getStack(), context.getPlayer(), context.getBlockPos());
        return super.useOnBlock(context);
    }

    public void handleWandClickOnBlock(ItemStack stack, PlayerEntity user, BlockPos targetPos) {
        if (user.getWorld().isClient || !stack.contains(MOB_ENTITY_COMPONENT)) {
            return;
        }

        MobEntityComponent component = stack.get(MOB_ENTITY_COMPONENT);
        if (component == null || component.entityUUID() == null) {
            return;
        }

        Entity entity = ((ServerWorld) user.getWorld()).getEntity(UUID.fromString(component.entityUUID()));
        if (!(entity instanceof MobEntity mob)) {
            return;
        }

        BlockState blockState = ((ServerWorld) user.getWorld()).getBlockState(targetPos);
        double blockHeight = blockState.getCollisionShape(((ServerWorld) user.getWorld()), targetPos).getMax(Direction.Axis.Y);
        Vector3d target = new Vector3d(targetPos.getX(), targetPos.getY() + blockHeight, targetPos.getZ());
        double distance = mob.squaredDistanceTo(target.x(), target.y(), target.z());
        // Check if the distance is greater than 20 blocks (400 blocks squared)
        if (distance > 400) {
            // Teleport the entity to the target position if it's too far away
            mob.setPosition(target.x(), target.y(), target.z());

            // Play sound effect for teleportation
            user.getWorld().playSound(
                    mob,
                    targetPos, // Position to play the sound at
                    SoundEvents.ENTITY_ENDERMAN_TELEPORT, // Sound event to use
                    SoundCategory.PLAYERS, // Category for the sound
                    1.0F, // Volume (1.0 is full volume)
                    1.0F // Pitch (1.0 is normal pitch)
            );
            return;
        }

        if (mob instanceof TokenizedEntityInterface) {
            ((TokenizedEntityInterface) entity).steveparty$setTargetPosition(target, 0.5);
        }
        user.getWorld().playSound(
                mob,
                targetPos, // Position to play the sound at
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, // Example sound event for moving
                SoundCategory.PLAYERS, // Category for the sound
                1.0F, // Volume
                1.0F // Pitch
        );
    }
}
