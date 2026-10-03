package fr.lordfinn.steveparty.client.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The vanilla action bar message: the party's notice moves up above it while it is shown. */
@Mixin(InGameHud.class)
public interface InGameHudAccessor {
    @Accessor("overlayRemaining")
    int steveparty$getOverlayRemaining();
}
