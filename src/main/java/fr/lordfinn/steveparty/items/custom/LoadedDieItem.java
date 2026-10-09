package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.dice.CursedRolls;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Loaded Die (« Dé pipé »), the Mistigri's loot: used on a player, or on the token of a player, it curses their
 * next roll ({@link CursedRolls}: whatever die they throw next only rolls the cursed 1, 2 or 3). Spent once used. Also
 * the ingredient of the Mistigri Cartridge.
 */
public class LoadedDieItem extends Item {

    public LoadedDieItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(user.getWorld() instanceof ServerWorld world)) return ActionResult.SUCCESS;
        ServerPlayerEntity victim = victimOf(world, entity);
        if (victim == null) return ActionResult.PASS;
        if (!CursedRolls.curse(victim)) {
            if (user instanceof ServerPlayerEntity player) {
                MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.loaded_die.already", victim.getDisplayName())
                        .formatted(Formatting.GRAY), MessageUtils.MessageType.ACTION_BAR);
            }
            return ActionResult.FAIL;
        }
        stack.decrementUnlessCreative(1, user);
        world.spawnParticles(ParticleTypes.WITCH, entity.getX(), entity.getBodyY(0.6), entity.getZ(), 16, 0.3, 0.4, 0.3, 0.05);
        world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), ModSounds.MISTIGRI_JINX, SoundCategory.PLAYERS, 0.8f, 0.9f);
        MessageUtils.sendToPlayer(victim, Text.translatable("message.steveparty.loaded_die.cursed")
                .formatted(Formatting.DARK_PURPLE), MessageUtils.MessageType.ACTION_BAR);
        if (user instanceof ServerPlayerEntity player && player != victim) {
            MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.loaded_die.used", victim.getDisplayName())
                    .formatted(Formatting.DARK_PURPLE), MessageUtils.MessageType.ACTION_BAR);
        }
        return ActionResult.CONSUME;
    }

    /** The player a Loaded Die used on {@code entity} curses: that player, or a token's player (online). */
    private static @Nullable ServerPlayerEntity victimOf(ServerWorld world, LivingEntity entity) {
        if (entity instanceof ServerPlayerEntity player) return player;
        if (entity instanceof TokenizedEntityInterface token && token.steveparty$isTokenized() && token.steveparty$getTokenOwner() != null) {
            return world.getServer().getPlayerManager().getPlayer(token.steveparty$getTokenOwner());
        }
        return null;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.steveparty.loaded_die").formatted(Formatting.DARK_PURPLE));
        tooltip.add(Text.translatable("tooltip.steveparty.loaded_die.use").formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
