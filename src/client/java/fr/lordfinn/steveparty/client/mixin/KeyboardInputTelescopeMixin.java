package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.telescope.TelescopeClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Looking through a telescope, the player stands still: the movement keys and jump do nothing (sneak still works: it
 * is how he steps back from the telescope).
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputTelescopeMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void steveparty$standStillAtTheTelescope(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (!TelescopeClient.isWatching()) return;
        this.pressingForward = this.pressingBack = this.pressingLeft = this.pressingRight = false;
        this.movementForward = this.movementSideways = 0.0F;
        this.jumping = false;
    }
}
