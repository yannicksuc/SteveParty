package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.components.MobEntityComponent;
import fr.lordfinn.steveparty.service.TokenMovementService;
import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import static fr.lordfinn.steveparty.components.ModComponents.MOB_ENTITY_COMPONENT;
import static fr.lordfinn.steveparty.effect.ModEffects.SQUISHED;
import static net.minecraft.entity.effect.StatusEffects.LEVITATION;

public class TokenizerWandItem extends Item {
    /** Data-driven enchantment (data/steveparty/enchantment/game_master.json): control any player's token. */
    public static final RegistryKey<Enchantment> GAME_MASTER = RegistryKey.of(RegistryKeys.ENCHANTMENT, Steveparty.id("game_master"));

    public TokenizerWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (entity instanceof MobEntity mob) {
            if (!((TokenizedEntityInterface) mob).steveparty$isTokenized()) {
                // Bosses can't become tokens (exploit: shrinking/controlling them)
                if (isBoss(mob)) return ActionResult.FAIL;
                tokenizeEntity(mob, user);
            } else {
                String uuid = entity.getUuidAsString();
                ItemStack wand = user.getStackInHand(hand);
                if (wand.isEmpty()) return ActionResult.PASS;
                // The selection is decided (and synced back) by the server, which knows the token owner
                if (user.getWorld().isClient) return ActionResult.SUCCESS;
                if (!canControlToken(user, wand, mob)) {
                    sendNotYourToken(user);
                    return ActionResult.FAIL;
                }
                wand.set(MOB_ENTITY_COMPONENT, new MobEntityComponent(uuid));
                user.getWorld().playSound(null, entity.getBlockPos(), ModSounds.SELECT_SOUND_EVENT, SoundCategory.PLAYERS, 1.0F, 1.0F);
            }
            return ActionResult.SUCCESS;
        }
        return super.useOnEntity(stack, user, entity, hand);
    }

    /**
     * Another player's token can only be selected / moved by an operator, or with a Game Master wand.
     * Tokens without owner, and the user's own tokens, are always allowed.
     */
    public static boolean canControlToken(PlayerEntity user, ItemStack wand, MobEntity token) {
        UUID owner = ((TokenizedEntityInterface) token).steveparty$getTokenOwner();
        return owner == null
                || owner.equals(user.getUuid())
                || user.hasPermissionLevel(2)
                || hasGameMaster(wand, user.getWorld());
    }

    /** @return true if {@code stack} carries the data-driven {@code steveparty:game_master} enchantment. */
    public static boolean hasGameMaster(ItemStack stack, World world) {
        return world.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT)
                .flatMap(registry -> registry.getOptional(GAME_MASTER))
                .map(enchantment -> EnchantmentHelper.getLevel(enchantment, stack) > 0)
                .orElse(false);
    }

    private static void sendNotYourToken(PlayerEntity user) {
        if (user instanceof ServerPlayerEntity serverPlayer) {
            MessageUtils.sendToPlayer(serverPlayer,
                    Text.translatableWithFallback("message.steveparty.token_not_yours",
                            "This token belongs to another player (operator or Game Master wand required)."),
                    MessageUtils.MessageType.ACTION_BAR);
        }
    }

    private static boolean isBoss(MobEntity mob) {
        return mob instanceof EnderDragonEntity || mob instanceof WitherEntity;
    }

    private void tokenizeEntity(MobEntity mob, PlayerEntity user) {
        if (!mob.getWorld().isClient) {
            ((TokenizedEntityInterface) mob).steveparty$setTokenized(true);
            ((TokenizedEntityInterface) mob).steveparty$setTokenOwner(user);
            // Add effects
            // Define the target width limit (1.3 blocks)
            double targetWidth = 1;
            // Calculate the current width and height
            double width = mob.getWidth();
            double height = mob.getHeight();
            // Check if the width is larger than the height
            if (width > height) {
                // Calculate the scale factor to fit the width within 1.3 blocks
                double scaleFactor = targetWidth / width;

                // Adjust the height to maintain the same width-to-height ratio
                double adjustedHeight = height * scaleFactor;

                // Add the status effect with the adjusted height as the new value
                mob.addStatusEffect(new StatusEffectInstance(SQUISHED, 130, (int)(adjustedHeight * 10))); // Assuming 10 is a unit of scaling
            } else {
                // Use the default height scaling if width is not larger than height
                mob.addStatusEffect(new StatusEffectInstance(SQUISHED, 130, 10)); // Default height scaling
            }
            mob.addStatusEffect(new StatusEffectInstance(LEVITATION, 130, 1));
            if (mob.getCustomName() == null) {
                mob.setCustomName(user.getDisplayName());
            }
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
        // Re-checked on each move: the wand may have been handed over, disenchanted or the player de-opped
        if (!canControlToken(user, stack, mob)) {
            sendNotYourToken(user);
            return;
        }

        TokenMovementService.moveEntity(mob, targetPos);
    }

    @Nullable
    private static Entity getEntityFromComponent(MobEntityComponent component, ServerWorld world) {
        if (component == null) {
            return null;
        }
        UUID entityUUID = component.getUuid();
        return entityUUID == null ? null : world.getEntity(entityUUID);
    }

    private static boolean isInvalidContext(ItemStack stack, PlayerEntity user) {
        return user.getWorld().isClient || !stack.contains(MOB_ENTITY_COMPONENT);
    }
}
