package fr.lordfinn.steveparty.entities;

import fr.lordfinn.steveparty.entities.custom.pawn.PawnPossessions;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.event.GameEvent;

/**
 * Waxed pawns, like waxed copper: a right click with a honeycomb waxes a pawn (any token: mob pawn or player pawn), and
 * from then on its pose never changes (the clicks that pose it do nothing, its look stays frozen on every client);
 * a right click with an axe scrapes the wax off. The pawn still moves on the board, is stored in a Token (the wax
 * with it) and keeps its other uses.
 * <p>
 * Only these two items, only on a token: any other item keeps its use (and a honeycomb on a waxed pawn, an axe on a
 * bare one, do nothing here).
 */
public final class TokenWax {
    private TokenWax() {
    }

    public static boolean isWaxed(Entity entity) {
        return entity instanceof TokenizedEntityInterface token && token.steveparty$isTokenized() && token.steveparty$isTokenWaxed();
    }

    /** A right click on {@code mob} with what is in {@code hand}: waxing or scraping it (both sides), else PASS. */
    public static ActionResult use(MobEntity mob, PlayerEntity player, Hand hand) {
        if (!TokenBase.isToken(mob) || player.isSpectator() || PawnPossessions.isInsideAPawn(player)) return ActionResult.PASS;
        ItemStack stack = player.getStackInHand(hand);
        boolean waxed = isWaxed(mob);
        if (stack.isOf(Items.HONEYCOMB) && !waxed) {
            if (!mob.getWorld().isClient) {
                setWaxed(mob, true, player);
                stack.decrementUnlessCreative(1, player);
                effects(mob, player, SoundEvents.ITEM_HONEYCOMB_WAX_ON, ParticleTypes.WAX_ON, "message.steveparty.token_wax.on");
            }
            return ActionResult.success(mob.getWorld().isClient);
        }
        if (stack.getItem() instanceof AxeItem && waxed) {
            if (!mob.getWorld().isClient) {
                setWaxed(mob, false, player);
                stack.damage(1, player, LivingEntity.getSlotForHand(hand));
                effects(mob, player, SoundEvents.ITEM_AXE_WAX_OFF, ParticleTypes.WAX_OFF, "message.steveparty.token_wax.off");
            }
            return ActionResult.success(mob.getWorld().isClient);
        }
        return ActionResult.PASS;
    }

    /**
     * A click that would pose a waxed pawn: nothing changes, the player reads why (server side; the client only takes
     * the click).
     */
    public static void refusePose(Entity pawn, PlayerEntity player) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            MessageUtils.sendToPlayer(serverPlayer, Text.translatable("message.steveparty.token_wax.frozen"),
                    MessageUtils.MessageType.ACTION_BAR);
        }
    }

    private static void setWaxed(MobEntity mob, boolean waxed, PlayerEntity player) {
        ((TokenizedEntityInterface) mob).steveparty$setTokenWaxed(waxed);
        mob.emitGameEvent(GameEvent.ENTITY_INTERACT, player);
    }

    private static void effects(MobEntity mob, PlayerEntity player, SoundEvent sound, ParticleEffect particle, String messageKey) {
        mob.getWorld().playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound, SoundCategory.PLAYERS, 1.0F, 1.0F);
        if (mob.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(particle, mob.getX(), mob.getBodyY(0.5), mob.getZ(), 10,
                    mob.getWidth() * 0.5 + 0.1, mob.getHeight() * 0.4, mob.getWidth() * 0.5 + 0.1, 0.5);
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            MessageUtils.sendToPlayer(serverPlayer, Text.translatable(messageKey), MessageUtils.MessageType.ACTION_BAR);
        }
    }
}
