package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Whatever a player does with its hands (use a block, use an item, start breaking, break) acts on the side of a
 * mini-game zone's border it plays on: a bucket emptied over the border, a bed laid across it, change nothing and
 * spawn nothing on the other side ({@link ZoneBorder#enterPlayer}).
 */
@Mixin(value = ServerPlayerInteractionManager.class, priority = 2000)
public abstract class ZoneBubbleInteractionManagerMixin {
    @Shadow
    @Final
    protected ServerPlayerEntity player;

    @Inject(method = {"interactBlock", "interactItem", "tryBreakBlock"}, at = @At("HEAD"))
    private void steveparty$enterPlayerAction(CallbackInfoReturnable<?> cir) {
        if (ZoneBorder.ACTIVE) ZoneBorder.enterPlayer(player, true);
    }

    @Inject(method = {"interactBlock", "interactItem", "tryBreakBlock"}, at = @At("RETURN"))
    private void steveparty$exitPlayerAction(CallbackInfoReturnable<?> cir) {
        if (ZoneBorder.ACTIVE) ZoneBorder.exit(player.getWorld());
    }

    @Inject(method = "processBlockBreakingAction", at = @At("HEAD"))
    private void steveparty$enterPlayerBreaking(CallbackInfo ci) {
        if (ZoneBorder.ACTIVE) ZoneBorder.enterPlayer(player, true);
    }

    @Inject(method = "processBlockBreakingAction", at = @At("RETURN"))
    private void steveparty$exitPlayerBreaking(CallbackInfo ci) {
        if (ZoneBorder.ACTIVE) ZoneBorder.exit(player.getWorld());
    }
}
