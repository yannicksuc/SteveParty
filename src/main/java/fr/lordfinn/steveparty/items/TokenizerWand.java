package fr.lordfinn.steveparty.items;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.TokenizedEntityInterface;
import fr.lordfinn.steveparty.effect.ModEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.particle.ParticleTypes;
import org.slf4j.Logger;

import static fr.lordfinn.steveparty.effect.ModEffects.SQUISHED;
import static net.minecraft.entity.effect.StatusEffects.LEVITATION;

public class TokenizerWand extends Item {
    private static final Logger LOGGER = Steveparty.LOGGER; // Adjust this as necessary

    public TokenizerWand(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand)  {

        if (entity instanceof MobEntity mob && !((TokenizedEntityInterface)mob).steveparty$isTokenized()) {

            // Remove the mob's AI to make it "brainless"
            if (!entity.getWorld().isClient) {
                //nbtData.putBoolean(TOKENIZED_FLAG, true);
                //mob.readNbt(nbtData);
                ((TokenizedEntityInterface)mob).steveparty$setTokenized(true);
                mob.setAiDisabled(true);
                mob.setInvulnerable(true);
                mob.clearGoalsAndTasks();

                // Adjust the scale based on the mob's height
                double height = mob.getHeight();
                double maxHeight = 1.5; // Set this to the maximum size you want to allow

                if (height > maxHeight) {
                    double scaleFactor = maxHeight / height;
                    EntityAttributeInstance scaleAttribute = mob.getAttributeInstance(EntityAttributes.SCALE);

                    if (scaleAttribute != null) {
                        scaleAttribute.setBaseValue(scaleFactor);
                    }
                }

                mob.getWorld().playSound(mob, mob.getBlockPos(),
                        SoundEvent.of(Identifier.ofVanilla("entity.illusioner.cast_spell")),
                        SoundCategory.PLAYERS, 1.0F, 1.0F);
                mob.getWorld().playSound(mob, mob.getBlockPos(),
                        SoundEvent.of(Identifier.ofVanilla("entity.zombie_villager.cure")),
                        SoundCategory.PLAYERS, 0.2F, 2.0F);
                mob.addStatusEffect(new StatusEffectInstance(SQUISHED, 180, 10));
                mob.addStatusEffect(new StatusEffectInstance(LEVITATION, 180, 3));
                // Set the mob to glow
                mob.setGlowing(true);
            }
            // Spawn multiple "wax-on" particles around the mob
            if (entity.getWorld().isClient) {
                for (int i = 0; i < 20; i++) { // Adjust the number as needed
                    mob.getWorld().addImportantParticle(ParticleTypes.WAX_OFF, true, mob.getX(), mob.getY()+(mob.getHeight()/2), mob.getZ(),
                            10 * mob.getWorld().getRandom().nextBetween(-1,1), 10 * mob.getWorld().getRandom().nextBetween(-1,1), 10 * mob.getWorld().getRandom().nextBetween(-1,1));
                }
            }
        }

        return ActionResult.SUCCESS;
    }
}
