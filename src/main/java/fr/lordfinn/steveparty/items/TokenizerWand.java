package fr.lordfinn.steveparty.items;

import fr.lordfinn.steveparty.TokenizedEntityInterface;
import fr.lordfinn.steveparty.components.MobEntityComponent;
import fr.lordfinn.steveparty.service.TokenMovementService;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import static fr.lordfinn.steveparty.components.ModComponents.MOB_ENTITY_COMPONENT;
import static fr.lordfinn.steveparty.effect.ModEffects.SQUISHED;
import static net.minecraft.entity.effect.StatusEffects.LEVITATION;

public class TokenizerWand extends Item {

    public TokenizerWand(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (entity instanceof MobEntity mob) {
            if (!((TokenizedEntityInterface) mob).steveparty$isTokenized()) {
                tokenizeEntity(mob, user);
            } else {
                String uuid = entity.getUuidAsString();
                user.getMainHandStack().set(MOB_ENTITY_COMPONENT, new MobEntityComponent(uuid));
                user.getWorld().playSound(null, entity.getBlockPos(), ModSounds.SELECT_SOUND_EVENT, SoundCategory.PLAYERS, 1.0F, 1.0F);
            }
            return ActionResult.SUCCESS;
        }
        return super.useOnEntity(stack, user, entity, hand);
    }

    private void tokenizeEntity(MobEntity mob, PlayerEntity user) {
        if (!mob.getWorld().isClient) {
            ((TokenizedEntityInterface) mob).steveparty$setTokenized(true);
            ((TokenizedEntityInterface) mob).steveparty$setTokenOwner(user);
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
        if (context.getPlayer() != null)
            handleWandClickOnBlock(context.getStack(), context.getPlayer(), context.getBlockPos());
        return super.useOnBlock(context);
    }

    public void handleWandClickOnBlock(ItemStack stack, PlayerEntity user, BlockPos targetPos) {
        if (isInvalidContext(stack, user)) {
            return;
        }

        MobEntityComponent component = stack.get(MOB_ENTITY_COMPONENT);
        Entity entity = getEntityFromComponent(component, ((ServerWorld) user.getWorld()));
        if (!(entity instanceof MobEntity mob)) {
            return;
        }

        TokenMovementService.moveEntity(mob, targetPos);
    }

    @Nullable
    private static Entity getEntityFromComponent(MobEntityComponent component, ServerWorld world) {
        if (component == null || component.entityUUID() == null) {
            return null;
        }
        UUID entityUUID = UUID.fromString(component.entityUUID());
        return world.getEntity(entityUUID);
    }

    private static boolean isInvalidContext(ItemStack stack, PlayerEntity user) {
        return user.getWorld().isClient || !stack.contains(MOB_ENTITY_COMPONENT);
    }
}
