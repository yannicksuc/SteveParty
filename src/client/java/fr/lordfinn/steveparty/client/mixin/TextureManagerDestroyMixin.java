package fr.lordfinn.steveparty.client.mixin;

import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.texture.TextureTickListener;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;

/**
 * {@code TextureManager.destroyTexture} (Mojang: {@code release}) on 1.21.1 deletes the GL texture but keeps the
 * texture registered with its stale GL name. OpenGL hands that name to the next texture made, often a player's skin;
 * binding, re-uploading or freeing the stale texture again then wipes the skin (the party turn bar's blank face).
 * Any mod freeing its textures this way (a photo cache, a HUD…) triggered it. After the vanilla delete, the texture
 * also forgets its GL name (without deleting it again) and leaves the manager, so its id loads afresh if asked again.
 */
@Mixin(TextureManager.class)
public abstract class TextureManagerDestroyMixin {
    @Shadow
    @Final
    private Map<Identifier, AbstractTexture> textures;

    @Shadow
    @Final
    private Set<TextureTickListener> tickListeners;

    @Inject(method = "destroyTexture", at = @At("TAIL"))
    private void steveparty$forgetDestroyedTexture(Identifier id, CallbackInfo ci) {
        AbstractTexture texture = textures.get(id);
        if (texture == null || texture == MissingSprite.getMissingSpriteTexture()) return;
        textures.remove(id);
        if (texture instanceof TextureTickListener listener) tickListeners.remove(listener);
        // Its GL name is already deleted (and may now be someone else's): forget it, then free the rest (its image)
        ((AbstractTextureAccessor) texture).steveparty$setGlId(-1);
        texture.close();
    }
}
