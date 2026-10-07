package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.signs.StencilInteractions;
import fr.lordfinn.steveparty.blocks.custom.signs.StencilPaintBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hitting stencil paint outside creative, whatever is in hand: the hint of {@link StencilPaintBlock#registerBreakRule}
 * (how to get the paint off) is given here, from the very start of the attack. In adventure mode vanilla stops the
 * attack on the client before any attack callback is called; in survival the same text is only shown again.
 */
@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerStencilPaintMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "attackBlock", at = @At("HEAD"))
    private void steveparty$stencilPaintHint(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (client.player == null || client.world == null || client.player.isSpectator()) return;
        if (!client.world.getBlockState(pos).isOf(ModBlocks.STENCIL_PAINT) || StencilPaintBlock.canBreak(client.player)) return;
        StencilInteractions.hint(client.world, client.player, "message.steveparty.stencil_paint.remove");
    }
}
