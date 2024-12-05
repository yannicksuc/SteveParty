package fr.lordfinn.steveparty.items;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.TokenizedEntityInterface;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.particle.ParticleTypes;
import org.slf4j.Logger;

public class TokenizerWand extends Item {
    private static final Logger LOGGER = Steveparty.LOGGER; // Adjust this as necessary

    public TokenizerWand(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand)  {
        LOGGER.info("TokenizerWand used on entity");

        if (entity instanceof MobEntity mob && !((TokenizedEntityInterface)mob).isTokenized()) {
            LOGGER.info("Entity hit");

            // Remove the mob's AI to make it "brainless"
            if (!entity.getWorld().isClient) {
                //nbtData.putBoolean(TOKENIZED_FLAG, true);
                //mob.readNbt(nbtData);
                ((TokenizedEntityInterface)mob).setTokenized(true);
                mob.setAiDisabled(true);

                // Adjust the scale based on the mob's height
                double height = mob.getHeight();
                double maxHeight = 1.5; // Set this to the maximum size you want to allow

                if (height > maxHeight) {
                    LOGGER.info("Resizing mob");
                    double scaleFactor = maxHeight / height;
                    EntityAttributeInstance scaleAttribute = mob.getAttributeInstance(EntityAttributes.SCALE);

                    if (scaleAttribute != null) {
                        scaleAttribute.setBaseValue(scaleFactor);
                    }
                }

                // Set the mob to glow
                mob.setGlowing(true);
                mob.getWorld().playSound(mob, mob.getBlockPos(),
                        SoundEvent.of(Identifier.ofVanilla("entity.illusioner.cast_spell")),
                        SoundCategory.PLAYERS, 1.0F, 1.0F);
                ((ServerWorld) mob.getWorld()).getServer().execute(() -> {
                    mob.setGlowing(false);
                });

                mob.setInvulnerable(true);
                mob.clearGoalsAndTasks();
                // Mark the mob as tokenized

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
