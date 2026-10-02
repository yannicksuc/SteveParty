package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A participant of a mini-game session is not teleported out of its zone, nor anyone else into it (ender pearl,
 * command, another dimension...), unless the mod does it itself ({@link ZoneBorder#blocksTeleport}). And what a
 * player does on its own tick (an item it finishes using) stays on its side of the border.
 */
@Mixin(value = ServerPlayerEntity.class, priority = 2000)
public abstract class ZoneBubbleServerPlayerMixin {

    @Inject(method = "teleportTo(Lnet/minecraft/world/TeleportTarget;)Lnet/minecraft/server/network/ServerPlayerEntity;",
            at = @At("HEAD"), cancellable = true)
    private void steveparty$noTeleportAcrossZoneBorder(TeleportTarget target, CallbackInfoReturnable<ServerPlayerEntity> cir) {
        if (!ZoneBorder.ACTIVE) return;
        if (ZoneBorder.blocksTeleport((ServerPlayerEntity) (Object) this, target)) cir.setReturnValue(null);
    }

    /** The drop of a server player, which does not go through the one of {@code PlayerEntity}: see {@link ZoneBubblePlayerEntityMixin}. */
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void steveparty$noSessionItemOutOfZone(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        if (!ZoneBorder.ACTIVE) return;
        if (ZoneBorder.blocksDrop((ServerPlayerEntity) (Object) this)) cir.setReturnValue(null);
    }

    @Inject(method = "playerTick()V", at = @At("HEAD"))
    private void steveparty$enterPlayerTick(CallbackInfo ci) {
        if (ZoneBorder.ACTIVE) ZoneBorder.enterPlayer((ServerPlayerEntity) (Object) this);
    }

    @Inject(method = "playerTick()V", at = @At("RETURN"))
    private void steveparty$exitPlayerTick(CallbackInfo ci) {
        if (ZoneBorder.ACTIVE) ZoneBorder.exit(((ServerPlayerEntity) (Object) this).getWorld());
    }
}
