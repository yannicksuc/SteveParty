package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.items.tooltip.Tooltips;
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

    @Override
    protected void appendState(ItemStack stack, Tooltips tips) {
        TrapSetupComponent setup = stack.get(ModComponents.TRAP_SETUP);
        // An unsigned Trap does the default (it steals coins): only a signed one says what it does, and who signed it
        if (setup != null) tips.state(Text.translatable("tooltip.steveparty.trap.signed", Tooltips.value(setup.signer()),
                Tooltips.bad(TrapSetupComponent.effectOf(stack).describe())));
    }

    @Override
    protected void appendMore(ItemStack stack, Tooltips.More more) {
        if (!TrapSetupComponent.isSigned(stack)) more.use(Tooltips.Keys.sneakUse(), "tooltip.steveparty.trap.unsigned");
    }
}
