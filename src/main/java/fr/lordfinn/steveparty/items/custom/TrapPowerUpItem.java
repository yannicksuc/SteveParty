package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TrapSetupComponent;
import fr.lordfinn.steveparty.payloads.custom.TrapSetupPayloads;
import fr.lordfinn.steveparty.powerups.PowerUp;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Trap power-up's item, like a book and quill: unsigned, it steals coins, and a sneak right-click opens its setup
 * screen (another effect, its amount) where its player signs it ({@link TrapSetupPayloads.Sign}); signed
 * ({@link ModComponents#TRAP_SETUP}), it does that effect for good, its signer in its tooltip. Traps alike stack; a
 * signed one is copied in the crafting grid with unsigned ones (TrapCopyRecipe).
 */
public class TrapPowerUpItem extends PowerUpItem {
    public TrapPowerUpItem(PowerUp powerUp, Settings settings) {
        super(powerUp, settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!player.isSneaking() || TrapSetupComponent.isSigned(stack)) return super.use(world, player, hand);
        if (player instanceof ServerPlayerEntity serverPlayer && ServerPlayNetworking.canSend(serverPlayer, TrapSetupPayloads.Open.ID))
            ServerPlayNetworking.send(serverPlayer, new TrapSetupPayloads.Open(hand));
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return TrapSetupComponent.isSigned(stack) || super.hasGlint(stack);
    }

    /** What it does (its signed effect), who signed it, how to sign it; then the power-up's lines. */
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        TrapSetupComponent setup = stack.get(ModComponents.TRAP_SETUP);
        TrapSetupComponent.Effect effect = TrapSetupComponent.effectOf(stack);
        tooltip.add(Text.translatable("tooltip.steveparty.trap.effect", effect.describe().formatted(Formatting.RED))
                .formatted(Formatting.GRAY));
        if (setup != null) {
            tooltip.add(Text.translatable("tooltip.steveparty.trap.signed", Text.literal(setup.signer()).formatted(Formatting.WHITE))
                    .formatted(Formatting.GRAY, Formatting.ITALIC));
            tooltip.add(Text.translatable("tooltip.steveparty.trap.copy").formatted(Formatting.DARK_GRAY));
        } else {
            tooltip.add(Text.translatable("tooltip.steveparty.trap.unsigned").formatted(Formatting.DARK_GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
