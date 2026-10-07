package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.telescope.TelescopeClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Looking through a Telescope, the player can't lower the tube far below the horizon (see TelescopeClient#clampPitch). */
@Mixin(Entity.class)
public class EntityLookTelescopeMixin {
    @Inject(method = "changeLookDirection", at = @At("TAIL"))
    private void steveparty$telescopeLowestPitch(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self != MinecraftClient.getInstance().player) return;
        float pitch = TelescopeClient.clampPitch(self.getPitch());
        if (pitch == self.getPitch()) return;
        self.setPitch(pitch);
        self.prevPitch = Math.min(self.prevPitch, pitch);
    }
}
