package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.gui.TitleScreenBackground;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Steve Party Maker poster (TitleScreenBackground) in place of the vanilla panorama, when its textures exist. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenBackgroundMixin extends Screen {
    protected TitleScreenBackgroundMixin(Text title) {
        super(title);
    }

    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    private void steveparty$posterBackground(DrawContext context, float delta, CallbackInfo ci) {
        if (TitleScreenBackground.isAvailable()) {
            TitleScreenBackground.render(context, width, height);
            ci.cancel();
        }
    }
}
