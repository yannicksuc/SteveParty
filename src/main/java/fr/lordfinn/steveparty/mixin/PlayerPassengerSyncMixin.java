package fr.lordfinn.steveparty.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * What rides a player is told to that player too: the server tells the players around, never the ridden one, so a
 * carrier did not know of the Glandouilles in his arms (no carrying pose, the stack put back in place every tick).
 */
@Mixin(Entity.class)
public abstract class PlayerPassengerSyncMixin {
    @Inject(method = "addPassenger", at = @At("TAIL"))
    private void steveparty$tellTheRiddenPlayer(Entity passenger, CallbackInfo ci) {
        steveparty$sync();
    }

    @Inject(method = "removePassenger", at = @At("TAIL"))
    private void steveparty$tellTheRiddenPlayerLeft(Entity passenger, CallbackInfo ci) {
        steveparty$sync();
    }

    private void steveparty$sync() {
        if ((Object) this instanceof ServerPlayerEntity player && player.networkHandler != null) {
            player.networkHandler.sendPacket(new EntityPassengersSetS2CPacket(player));
        }
    }
}
