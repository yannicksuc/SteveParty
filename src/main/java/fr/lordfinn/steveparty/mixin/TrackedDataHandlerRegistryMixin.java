package fr.lordfinn.steveparty.mixin;

import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TrackedDataHandlerRegistry.class)
public class TrackedDataHandlerRegistryMixin {
    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void registerCustomHandlers(CallbackInfo ci) {
        // Call the registration method to register your handlers
    }
}