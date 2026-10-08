package fr.lordfinn.steveparty.client.utils;

import fr.lordfinn.steveparty.client.mixin.TextureManagerAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.TextureTickListener;
import net.minecraft.util.Identifier;

public final class ClientTextures {
    private ClientTextures() {
    }

    /**
     * Frees a texture and forgets it, so its id loads afresh if asked again. Not {@code TextureManager.destroyTexture}:
     * on 1.21.1 it only deletes the GL texture and keeps the texture, with its stale GL name, in the manager. That
     * name goes to the next texture made (often a player's skin, downloaded on joining a server); destroying or binding
     * the stale texture again then deletes or draws over that one: the skin turns into a blank or another picture.
     */
    public static void destroy(Identifier id) {
        if (id == null) return;
        TextureManagerAccessor manager = (TextureManagerAccessor) MinecraftClient.getInstance().getTextureManager();
        AbstractTexture texture = manager.steveparty$getTextures().remove(id);
        // A texture that failed to load is the shared missing texture: forgotten, never freed
        if (texture == null || texture == MissingSprite.getMissingSpriteTexture()) return;
        if (texture instanceof TextureTickListener listener) manager.steveparty$getTickListeners().remove(listener);
        texture.close();
        texture.clearGlId();
    }
}
