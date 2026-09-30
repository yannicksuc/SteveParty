package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.entities.custom.PipeCarrierEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No "press sneak to dismount" in a pipe: sneaking does not get one out. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class PipeMountMessageMixin {
    @Inject(method = "onEntityPassengersSet", at = @At("TAIL"))
    private void steveparty$noDismountHintInPipes(EntityPassengersSetS2CPacket packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getVehicle() instanceof PipeCarrierEntity && packet.getEntityId() == client.player.getVehicle().getId()) {
            client.inGameHud.setOverlayMessage(Text.empty(), false);
        }
    }
}
