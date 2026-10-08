package fr.lordfinn.steveparty.client.mixin;

import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.texture.TextureTickListener;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Set;

/** The registered textures: {@link fr.lordfinn.steveparty.client.utils.ClientTextures#destroy} really forgets one. */
@Mixin(TextureManager.class)
public interface TextureManagerAccessor {
    @Accessor("textures")
    Map<Identifier, AbstractTexture> steveparty$getTextures();

    @Accessor("tickListeners")
    Set<TextureTickListener> steveparty$getTickListeners();
}
